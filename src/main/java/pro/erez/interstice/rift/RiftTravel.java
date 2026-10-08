package pro.erez.interstice.rift;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.FluidLab;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandWorld;

/** Server-authoritative, shared rift links. No transfer is performed from inside a fishing/cauldron callback. */
@EventBusSubscriber(modid = Interstice.ID)
public final class RiftTravel {
    public static final String COOLDOWN = "interstice:rift_cooldown";
    public static final String ACTIVE = "interstice:active_rift";
    public static final int COOLDOWN_TICKS = 60;
    public static final int PORTAL_WARMUP = 40;
    private record Pending(RiftLinks.Endpoint source, RiftLinks.Kind kind, Vec3 start, long due) {}
    private record Touch(RiftLinks.Endpoint source, long last, int ticks) {}
    private static final class Session {
        final Map<UUID, Pending> pending = new HashMap<>();
        final Map<UUID, Touch> portals = new HashMap<>();
    }
    private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();
    private static Session session(MinecraftServer server) { return SESSIONS.computeIfAbsent(server, ignored -> new Session()); }
    private RiftTravel() {}
    public static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }
    public static boolean coolingDown(ServerPlayer player) { return player.getPersistentData().getLong(COOLDOWN) > now(player); }

    public static boolean requestAnomaly(ServerPlayer player, RiftLinks.Kind kind) {
        if (kind == RiftLinks.Kind.PORTAL || !player.isAlive() || player.isSpectator() || player.isPassenger() || coolingDown(player)) return false;
        Session session = session(player.server);
        if (session.pending.containsKey(player.getUUID())) return false;
        BlockPos returnFeet = RiftSafety.near(player.serverLevel(), player.blockPosition(), false);
        if (returnFeet == null) return false;
        var source = new RiftLinks.Endpoint(player.level().dimension(), returnFeet, Direction.Axis.X);
        session.pending.put(player.getUUID(), new Pending(source, kind, player.position(), now(player) + 30));
        player.displayClientMessage(Component.translatable(kind == RiftLinks.Kind.FISHING ? "message.interstice.rift.impossible_catch" : "message.interstice.rift.rising_water"), true);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .8F, .5F);
        return true;
    }

    public static void touch(ServerPlayer player, BlockPos cell) {
        if (coolingDown(player)) {
            // Staying inside an exit cannot start an endless bounce loop.
            player.getPersistentData().putLong(COOLDOWN, now(player) + COOLDOWN_TICKS);
            return;
        }
        var frame = RiftGeometry.atPortal(player.serverLevel(), cell);
        if (frame == null) return;
        var source = frame.endpoint(player.serverLevel());
        Session session = session(player.server);
        Touch old = session.portals.get(player.getUUID());
        int ticks = old != null && old.source().equals(source) && old.last() >= now(player) - 1 ? old.ticks() : 0;
        session.portals.put(player.getUUID(), new Touch(source, now(player), ticks));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Session session = SESSIONS.get(server); if (session == null) return;
        long time = server.overworld().getGameTime();
        var pending = session.pending.entrySet().iterator();
        while (pending.hasNext()) {
            var entry = pending.next(); var player = server.getPlayerList().getPlayer(entry.getKey()); var request = entry.getValue();
            if (player == null || !player.isAlive() || player.isSpectator() || player.isPassenger()
                    || !player.level().dimension().equals(request.source().dimension()) || player.position().distanceToSqr(request.start()) > 16) { pending.remove(); continue; }
            if (time < request.due()) {
                if (time % 3 == 0) player.serverLevel().sendParticles(ParticleTypes.GLOW, player.getX(), player.getY() + .6, player.getZ(), 8, .35, .6, .35, .03);
                continue;
            }
            pending.remove(); enter(player, request.source(), request.kind());
        }
        var portals = session.portals.entrySet().iterator();
        while (portals.hasNext()) {
            var entry = portals.next(); var player = server.getPlayerList().getPlayer(entry.getKey()); Touch touch = entry.getValue();
            if (player == null || touch.last() != time || !player.level().dimension().equals(touch.source().dimension())) { portals.remove(); continue; }
            if (touch.ticks() + 1 < PORTAL_WARMUP) { entry.setValue(new Touch(touch.source(), touch.last(), touch.ticks() + 1)); continue; }
            portals.remove(); enter(player, touch.source(), RiftLinks.Kind.PORTAL);
        }
    }

    public static boolean enter(ServerPlayer player, RiftLinks.Endpoint source, RiftLinks.Kind kind) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || coolingDown(player)
                || !player.level().dimension().equals(source.dimension()) || player.blockPosition().distSqr(source.pos()) > 64) return false;
        ServerLevel origin = player.server.getLevel(source.dimension());
        if (origin == null || sourceExit(origin, source, kind) == null) return fail(player, "unsafe_source");
        RiftLinks data = RiftLinks.get(player.server);
        RiftLinks.Link link = data.source(source, kind);
        if (link == null) {
            ServerLevel destination = player.server.getLevel(IslandWorld.isIsland(source.dimension()) ? Level.OVERWORLD : IslandWorld.TALL_WORLD);
            if (destination == null) return fail(player, "missing_dimension");
            BlockPos hint;
            try { hint = returnHint(player, destination); }
            catch (IllegalStateException exception) { return fail(player, "no_landing"); }
            BlockPos echo = RiftSafety.prepareEcho(destination, hint);
            if (echo == null) return fail(player, "no_landing");
            link = data.add(kind, source, new RiftLinks.Endpoint(destination.dimension(), echo, Direction.Axis.X));
        }
        ServerLevel destination = player.server.getLevel(link.echo().dimension());
        if (destination == null) return fail(player, "missing_dimension");
        destination.getChunk(link.echo().pos().getX() >> 4, link.echo().pos().getZ() >> 4);
        if (!destination.getBlockState(link.echo().pos()).is(Interstice.RIFT_ECHO.get())) return fail(player, "broken_echo");
        BlockPos landing = RiftSafety.near(destination, link.echo().pos().west(), IslandWorld.isIsland(destination.dimension()));
        // Recheck both endpoints after chunk generation and immediately before transfer.
        if (landing == null || !RiftSafety.standing(destination, landing) || sourceExit(origin, source, kind) == null) return fail(player, "no_landing");
        FluidLab.rememberReturn(player);
        if (!transfer(player, destination, landing, link.id())) return fail(player, "transfer_failed");
        player.displayClientMessage(Component.translatable(IslandWorld.isIsland(destination.dimension())
                ? "message.interstice.rift.entered" : "message.interstice.rift.emerged"), false);
        return true;
    }

    private static BlockPos returnHint(ServerPlayer player, ServerLevel destination) {
        if (IslandWorld.isIsland(destination.dimension())) return RiftSafety.defaultHint(destination);
        var persistent = player.getPersistentData();
        if (persistent.hasUUID(ACTIVE)) {
            var link = RiftLinks.get(player.server).byId(persistent.getUUID(ACTIVE));
            if (link != null) {
                if (link.source().dimension().equals(destination.dimension())) return link.source().pos();
                if (link.echo().dimension().equals(destination.dimension())) return link.echo().pos().west();
            }
        }
        var remembered = persistent.getCompound("interstice:return");
        ResourceLocation dimension = ResourceLocation.tryParse(remembered.getString("dimension"));
        if (dimension != null && ResourceKey.create(Registries.DIMENSION, dimension).equals(destination.dimension()))
            return BlockPos.containing(remembered.getDouble("x"), remembered.getDouble("y"), remembered.getDouble("z"));
        return RiftSafety.defaultHint(destination);
    }

    private static BlockPos sourceExit(ServerLevel level, RiftLinks.Endpoint endpoint, RiftLinks.Kind kind) {
        if (kind != RiftLinks.Kind.PORTAL) return RiftSafety.near(level, endpoint.pos(), false);
        var frame = new RiftGeometry.Frame(endpoint.pos(), endpoint.axis());
        if (!RiftGeometry.load(level, frame) || !RiftGeometry.valid(level, frame, true)) return null;
        Direction normal = endpoint.axis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        for (int sign : new int[]{1, -1}) {
            BlockPos candidate = frame.origin().relative(normal, sign * 2);
            if (RiftSafety.standing(level, candidate)) return candidate;
        }
        return RiftSafety.near(level, frame.origin(), false);
    }

    public static boolean returnThroughEcho(ServerPlayer player, BlockPos echo) {
        if (coolingDown(player)) return fail(player, "cooldown");
        if (!player.isAlive() || player.isPassenger() || player.isSpectator()) return false;
        if (!player.serverLevel().getBlockState(echo).is(Interstice.RIFT_ECHO.get())) return fail(player, "broken_echo");
        var link = RiftLinks.get(player.server).echo(player.level().dimension(), echo);
        if (link == null) return fail(player, "unlinked_echo");
        ServerLevel destination = player.server.getLevel(link.source().dimension());
        if (destination == null) return fail(player, "missing_dimension");
        BlockPos landing = sourceExit(destination, link.source(), link.kind());
        if (landing == null || !RiftSafety.standing(destination, landing)) return fail(player, "unsafe_return");
        return transfer(player, destination, landing, link.id()) || fail(player, "transfer_failed");
    }
    private static boolean transfer(ServerPlayer player, ServerLevel destination, BlockPos landing, UUID id) {
        if (!player.teleportTo(destination, landing.getX() + .5, landing.getY() + .01, landing.getZ() + .5, java.util.Set.of(), player.getYRot(), 0)
                || player.serverLevel() != destination) return false;
        player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance(); player.invulnerableTime = 60;
        player.getPersistentData().putLong(COOLDOWN, now(player) + COOLDOWN_TICKS);
        player.getPersistentData().putUUID(ACTIVE, id);
        destination.playSound(null, landing, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, .35F, .75F);
        destination.sendParticles(ParticleTypes.GLOW, landing.getX() + .5, landing.getY() + 1, landing.getZ() + .5, 30, .5, .9, .5, .02);
        return true;
    }
    private static boolean fail(ServerPlayer player, String reason) { player.displayClientMessage(Component.translatable("message.interstice.rift." + reason), true); return false; }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().contains(COOLDOWN)) event.getEntity().getPersistentData().putLong(COOLDOWN, event.getOriginal().getPersistentData().getLong(COOLDOWN));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
}
