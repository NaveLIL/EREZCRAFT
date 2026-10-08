package pro.erez.interstice.expedition;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.rift.RiftSafety;
import pro.erez.interstice.rift.RiftTravel;
import pro.erez.interstice.worldgen.IslandWorld;

@EventBusSubscriber(modid = Interstice.ID)
public final class WayfarerRecall {
    public static final int CHANNEL_TICKS = 200;
    private record Channel(UUID journey, Vec3 start, InteractionHand hand, ItemStack key, long began, float vitality) {}
    private static final Map<MinecraftServer, Map<UUID, Channel>> CHANNELS = new WeakHashMap<>();
    private WayfarerRecall() {}
    public static ExpeditionLedger.Origin remembered(ServerPlayer player) {
        var point = player.getPersistentData().getCompound("interstice:return"); ResourceLocation location = ResourceLocation.tryParse(point.getString("dimension"));
        if (location == null) return null;
        var dimension = ResourceKey.create(Registries.DIMENSION, location);
        if (IslandWorld.isIsland(dimension)) return null;
        return new ExpeditionLedger.Origin(dimension, BlockPos.containing(point.getDouble("x"), point.getDouble("y"), point.getDouble("z")));
    }
    @SubscribeEvent public static void arrived(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !IslandWorld.isIsland(event.getFrom()) && IslandWorld.isIsland(event.getTo())) {
            var origin = remembered(player); if (origin != null && origin.dimension().equals(event.getFrom())) ExpeditionLedger.get(player.server).arrive(player.getUUID(), origin);
        }
        if (event.getEntity() instanceof ServerPlayer player) cancel(player, "dimension_changed");
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && IslandWorld.isIsland(player.level().dimension()) && ExpeditionLedger.get(player.server).journey(player.getUUID()) == null) {
            var origin = remembered(player); if (origin != null) ExpeditionLedger.get(player.server).arrive(player.getUUID(), origin);
        }
    }
    public static boolean prepareOrBind(ServerPlayer player, ItemStack key, boolean fresh) {
        if (IslandWorld.isIsland(player.level().dimension()) || !key.is(Interstice.WAYFARER_KEY.get())) return false;
        var binding = WayfarerKeyItem.binding(key);
        if (binding != null && !binding.owner().equals(player.getUUID())) return message(player, "foreign");
        var ledger = ExpeditionLedger.get(player.server); var current = ledger.journey(player.getUUID());
        if (!fresh) {
            if (current == null) return message(player, "prepare_first");
            WayfarerKeyItem.bind(key, player.getUUID(), current.id());
            if (current.spent()) return message(player, "spent");
            player.displayClientMessage(Component.translatable("message.interstice.key.bound"), true); return true;
        }
        BlockPos feet = RiftSafety.near(player.serverLevel(), player.blockPosition(), false);
        if (feet == null) return message(player, "no_origin");
        ItemStack shard = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) if (stack.is(Items.AMETHYST_SHARD)) { shard = stack; break; }
        if (shard.isEmpty()) return message(player, "need_shard");
        shard.shrink(1); var next = ledger.prepare(player.getUUID(), new ExpeditionLedger.Origin(player.level().dimension(), feet));
        WayfarerKeyItem.bind(key, player.getUUID(), next.id()); player.displayClientMessage(Component.translatable("message.interstice.key.prepared"), true); return true;
    }
    public static boolean begin(ServerPlayer player, InteractionHand hand, ItemStack key) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || !key.is(Interstice.WAYFARER_KEY.get()) || !IslandWorld.isIsland(player.level().dimension())) return false;
        var ledger = ExpeditionLedger.get(player.server); var journey = ledger.journey(player.getUUID());
        if (journey == null || !journey.entered()) return message(player, "no_origin");
        var binding = WayfarerKeyItem.binding(key);
        if (binding == null) { WayfarerKeyItem.bind(key, player.getUUID(), journey.id()); binding = WayfarerKeyItem.binding(key); }
        if (!binding.owner().equals(player.getUUID())) return message(player, "foreign");
        if (!binding.journey().equals(journey.id())) return message(player, "old");
        if (journey.spent()) return message(player, "spent");
        CHANNELS.computeIfAbsent(player.server, ignored -> new HashMap<>()).put(player.getUUID(), new Channel(journey.id(), player.position(), hand, key, RiftTravel.now(player), player.getHealth() + player.getAbsorptionAmount()));
        player.displayClientMessage(Component.translatable("message.interstice.key.concentrating", 10), true); return true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Map<UUID, Channel> channels = CHANNELS.get(event.getServer()); if (channels == null) return;
        // Copy entries because a successful dimension change invokes cancellation synchronously.
        for (var entry : new HashMap<>(channels).entrySet()) {
            var player = event.getServer().getPlayerList().getPlayer(entry.getKey()); Channel channel = entry.getValue();
            if (player == null) { channels.remove(entry.getKey()); continue; }
            if (!player.isAlive() || player.isPassenger() || !IslandWorld.isIsland(player.level().dimension())) { cancel(player, "interrupted"); continue; }
            if (player.position().distanceToSqr(channel.start()) > .04) { cancel(player, "moved"); continue; }
            if (player.getHealth() + player.getAbsorptionAmount() < channel.vitality()) { cancel(player, "hurt"); continue; }
            if (!player.isUsingItem() || player.getUsedItemHand() != channel.hand() || player.getItemInHand(channel.hand()) != channel.key()) { cancel(player, "released"); continue; }
            long elapsed = RiftTravel.now(player) - channel.began();
            if (elapsed >= CHANNEL_TICKS) { complete(player, channel); continue; }
            if (elapsed % 20 == 0) player.displayClientMessage(Component.translatable("message.interstice.key.concentrating", (CHANNEL_TICKS - elapsed + 19) / 20), true);
            if (elapsed % 10 == 0) player.serverLevel().sendParticles(ParticleTypes.GLOW, player.getX(), player.getY() + .8, player.getZ(), 3, .2, .4, .2, .01);
        }
        if (channels.isEmpty()) CHANNELS.remove(event.getServer());
    }
    private static void complete(ServerPlayer player, Channel channel) {
        var ledger = ExpeditionLedger.get(player.server); var journey = ledger.journey(player.getUUID());
        if (journey == null || !journey.id().equals(channel.journey()) || journey.spent()) { cancel(player, "spent"); return; }
        var destination = player.server.getLevel(journey.origin().dimension());
        BlockPos landing = destination == null ? null : RiftSafety.near(destination, journey.origin().feet(), false);
        if (landing == null) { cancel(player, "unsafe"); return; }
        CHANNELS.get(player.server).remove(player.getUUID()); player.stopUsingItem();
        if (!player.teleportTo(destination, landing.getX() + .5, landing.getY() + .01, landing.getZ() + .5, java.util.Set.of(), player.getYRot(), 0) || player.serverLevel() != destination) { message(player, "failed"); return; }
        if (!ledger.spend(player.getUUID(), channel.journey())) throw new IllegalStateException("Emergency entitlement changed during confirmed transfer");
        player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance(); player.invulnerableTime = 60;
        player.getPersistentData().putLong(RiftTravel.COOLDOWN, RiftTravel.now(player) + RiftTravel.COOLDOWN_TICKS);
        player.displayClientMessage(Component.translatable("message.interstice.key.returned"), false);
    }
    public static void cancel(ServerPlayer player, String reason) {
        var channels = CHANNELS.get(player.server);
        if (channels == null || channels.remove(player.getUUID()) == null) return;
        player.stopUsingItem(); message(player, reason);
    }
    private static boolean message(ServerPlayer player, String name) { player.displayClientMessage(Component.translatable("message.interstice.key." + name), true); return false; }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) { if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0) cancel(player, "hurt"); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) { cancel(player, "death"); ExpeditionLedger.get(player.server).abandon(player.getUUID()); } }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) cancel(player, "logout"); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { CHANNELS.remove(event.getServer()); }
}
