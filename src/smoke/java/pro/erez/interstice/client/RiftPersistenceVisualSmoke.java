package pro.erez.interstice.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.rift.*;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.worldgen.IslandWorld;

/** Two actual client JVMs and one disposable save verify link persistence, portal warmup and return. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class RiftPersistenceVisualSmoke {
    private static final String MODE = System.getProperty("interstice.riftPersistence", "");
    private static final String WORLD = "rift-persistence-check";
    private static boolean started;
    private static int stage, ticks;
    private static long deadline;
    private static volatile boolean serverReady, serverVerified;
    private static volatile Throwable failure;
    private static RiftGeometry.Frame frame;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (MODE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true; deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(7);
            mc.options.pauseOnLostFocus = false; mc.options.hideGui = true; mc.options.renderDistance().set(4); mc.options.fov().set(70);
            if (MODE.equals("create")) {
                if (Files.exists(mc.gameDirectory.toPath().resolve("saves/" + WORLD + "/level.dat"))) throw new IllegalStateException("Use a fresh rift verification profile");
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings("Interstice rift persistence", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261006L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
            } else mc.createWorldOpenFlows().openWorld(WORLD, () -> {});
            return;
        }
        if (!started) return;
        if (SmokeWorldPrompts.advance(mc)) return;
        if (failure != null) throw new IllegalStateException("Rift verification failed", failure);
        if (System.nanoTime() > deadline) throw new IllegalStateException("Rift verification timed out at " + stage);
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.screen != null) return;
        var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
        if (stage == 0) {
            stage = 1; ticks = 0;
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(uuid);
                    if (MODE.equals("create")) create(mc, player);
                    else {
                        var metadata = read(mc, "rift-fixture.json");
                        frame = new RiftGeometry.Frame(new BlockPos(metadata.get("x").getAsInt(), metadata.get("y").getAsInt(), metadata.get("z").getAsInt()), Direction.Axis.X);
                        if (!player.level().dimension().equals(IslandWorld.TALL_WORLD)) throw new IllegalStateException("Saved player did not resume in islands");
                        var link = RiftLinks.get(server).byId(UUID.fromString(metadata.get("link").getAsString()));
                        if (link == null || !link.source().equals(frame.endpoint(server.overworld())) || !player.serverLevel().getBlockState(link.echo().pos()).is(Interstice.RIFT_ECHO.get())) throw new IllegalStateException("Saved connection did not survive restart");
                        TideManager.getSavedData(server).setPhase(TidePhase.CALM, 20000);
                    }
                    serverReady = true;
                } catch (Throwable error) { failure = error; }
            });
            return;
        }
        if (!serverReady) return;
        if (MODE.equals("create")) {
            if (stage == 1 && ++ticks >= 60) {
                capture(mc, "01-created-rift-portal.png"); stage = 2; ticks = 0;
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    player.teleportTo(player.serverLevel(), frame.origin().getX() + .5, frame.origin().getY() + .01, frame.origin().getZ() + .5, java.util.Set.of(), 0, 0);
                    TideManager.getSavedData(server).setPhase(TidePhase.SURGE, 20000);
                });
            } else if (stage == 2 && mc.level.dimension().equals(IslandWorld.TALL_WORLD) && ++ticks >= 30) {
                stage = 3; ticks = 0;
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(uuid);
                        if (!player.isAlive() || !ShelterDetector.isSheltered(player.level(), player)) throw new IllegalStateException("Landing did not provide a safe tide shelter");
                        var id = player.getPersistentData().getUUID(RiftTravel.ACTIVE); var link = RiftLinks.get(server).byId(id);
                        if (link == null || !link.source().equals(frame.endpoint(server.overworld()))) throw new IllegalStateException("Portal did not preserve source frame");
                        JsonObject json = new JsonObject(); json.addProperty("x", frame.origin().getX()); json.addProperty("y", frame.origin().getY()); json.addProperty("z", frame.origin().getZ());
                        json.addProperty("link", id.toString()); json.addProperty("passed", true); json.addProperty("surge_shelter", true);
                        write(mc, "rift-fixture.json", json); write(mc, "rift-create-validation.json", json);
                        TideManager.getSavedData(server).setPhase(TidePhase.CALM, 20000);
                        player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), java.util.Set.of(), -90, 30);
                        serverVerified = true;
                    } catch (Throwable error) { failure = error; }
                });
            } else if (stage == 3 && serverVerified && ++ticks >= 40) { capture(mc, "02-return-echo-and-shelter.png"); stage = 4; ticks = 0; }
            else if (stage == 4 && ++ticks >= 30) { System.out.println("RIFT_CREATE_VALIDATION passed"); mc.stop(); }
        } else {
            if (stage == 1 && ++ticks >= 80) {
                stage = 2; ticks = 0;
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(uuid); var json = read(mc, "rift-fixture.json");
                        var link = RiftLinks.get(server).byId(UUID.fromString(json.get("link").getAsString()));
                        if (!RiftTravel.returnThroughEcho(player, link.echo().pos())) throw new IllegalStateException("Saved echo refused the return");
                    } catch (Throwable error) { failure = error; }
                });
            } else if (stage == 2 && mc.level.dimension().equals(Level.OVERWORLD) && ++ticks >= 60) {
                capture(mc, "03-return-after-jvm-restart.png"); stage = 3; ticks = 0;
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(uuid);
                        if (!RiftGeometry.load(player.serverLevel(), frame) || !RiftGeometry.valid(player.serverLevel(), frame, true)) throw new IllegalStateException("Source portal did not survive restart across its chunk boundary");
                        player.teleportTo(player.serverLevel(), frame.origin().getX() + .5, frame.origin().getY() + .01, frame.origin().getZ() + .5, java.util.Set.of(), 0, 0);
                    } catch (Throwable error) { failure = error; }
                });
            } else if (stage == 3 && mc.level.dimension().equals(IslandWorld.TALL_WORLD) && ++ticks >= 40) {
                stage = 4; ticks = 0;
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(uuid); var json = read(mc, "rift-fixture.json");
                        if (!player.getPersistentData().getUUID(RiftTravel.ACTIVE).toString().equals(json.get("link").getAsString())) throw new IllegalStateException("Repeat entry created a different connection");
                        JsonObject result = new JsonObject(); result.addProperty("passed", true); result.addProperty("real_client_roundtrip", true);
                        result.addProperty("same_link_after_restart", true); result.addProperty("chunk_boundary_portal", true);
                        write(mc, "rift-reload-validation.json", result); serverVerified = true;
                    } catch (Throwable error) { failure = error; }
                });
            } else if (stage == 4 && serverVerified && ++ticks >= 30) { capture(mc, "04-reentered-saved-rift.png"); System.out.println("RIFT_RELOAD_VALIDATION passed"); mc.stop(); }
        }
    }
    private static void create(Minecraft mc, ServerPlayer player) throws Exception {
        var level = player.serverLevel(); if (!level.dimension().equals(Level.OVERWORLD)) throw new IllegalStateException("Fresh world must start outside the Interstice");
        int x = (player.getBlockX() >> 4) * 16 + 14, y = player.getBlockY(), z = player.getBlockZ() + 8;
        frame = new RiftGeometry.Frame(new BlockPos(x, y, z), Direction.Axis.X);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            level.setBlock(frame.origin().offset(dx, -1, dz), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            for (int dy = 0; dy <= 4; dy++) level.setBlock(frame.origin().offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int w = -1; w <= 2; w++) for (int h = -1; h <= 3; h++) {
            if ((w == -1 || w == 2) && (h == -1 || h == 3)) continue;
            if (w == -1 || w == 2 || h == -1 || h == 3) level.setBlock(frame.at(w, h), Interstice.RIFT_FRAME.get().defaultBlockState(), 3);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Interstice.RIFT_LENS.get()));
        BlockPos clicked = frame.at(-1, 1);
        var use = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(clicked), Direction.EAST, clicked, false));
        if (!player.getMainHandItem().useOn(use).consumesAction() || !RiftGeometry.valid(level, frame, true)) throw new IllegalStateException("Lens item did not activate the frame");
        player.teleportTo(level, x + .5, y + .01, z - 2.5, java.util.Set.of(), 0, 0);
    }
    private static JsonObject read(Minecraft mc, String filename) throws Exception { return JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve(filename))).getAsJsonObject(); }
    private static void write(Minecraft mc, String filename, JsonObject json) throws Exception { Files.writeString(mc.gameDirectory.toPath().resolve(filename), json.toString()); }
    private static void capture(Minecraft mc, String filename) { Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(), message -> System.out.println("RIFT_SCREENSHOT " + message.getString())); }
}
