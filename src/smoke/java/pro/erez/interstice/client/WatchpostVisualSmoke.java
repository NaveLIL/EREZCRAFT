package pro.erez.interstice.client;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.WatchpostRuins;

/** Inspects actual naturally generated ruins, rather than placing an artificial gallery structure. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class WatchpostVisualSmoke {
    private static final String MODE = System.getProperty("interstice.watchpostPersistence", "");
    private static final String WORLD = "natural-watchpost-check";
    private static boolean started;
    private static int stage, ticks;
    private static long deadline;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile BlockPos barrelPos;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("interstice.watchpostSmoke") && MODE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true; deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(7);
            mc.options.pauseOnLostFocus = false; mc.options.hideGui = true; mc.options.renderDistance().set(5);
            if (MODE.equals("reload")) mc.createWorldOpenFlows().openWorld(WORLD, () -> {});
            else mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings("Natural watchpost check", GameType.CREATIVE,
                    false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261006L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
            return;
        }
        if (!started) return;
        if (SmokeWorldPrompts.advance(mc)) return;
        if (failure != null) throw new IllegalStateException("Watchpost verification failed", failure);
        if (System.nanoTime() > deadline) throw new IllegalStateException("Watchpost verification timed out stage=" + stage);
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
        if (stage == 0 && MODE.equals("reload")) {
            stage = 4; ticks = 0;
            server.execute(() -> { try {
                var data = com.google.gson.JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("watchpost-create-validation.json"))).getAsJsonObject();
                barrelPos = new BlockPos(data.get("barrel_x").getAsInt(), data.get("barrel_y").getAsInt(), data.get("barrel_z").getAsInt());
                var level = server.getLevel(IslandWorld.TALL_WORLD);
                var barrel = (BarrelBlockEntity)level.getBlockEntity(barrelPos);
                if (barrel == null || !barrel.isEmpty()) throw new IllegalStateException("Loot restored after restart");
                if (!level.getBlockState(barrelPos.above()).is(Blocks.COBBLESTONE)) throw new IllegalStateException("Player alteration disappeared after restart");
                JsonObject result = new JsonObject(); result.addProperty("passed", true); result.addProperty("empty_barrel_after_restart", true); result.addProperty("player_alteration_preserved", true);
                Files.writeString(mc.gameDirectory.toPath().resolve("watchpost-reload-validation.json"), result.toString()); ready = true;
            } catch (Throwable error) { failure = error; } });
            return;
        }
        if (stage == 4 && ready && ++ticks >= 60) { System.out.println("WATCHPOST_RESTART_VALIDATION passed"); mc.stop(); return; }
        if (stage == 0) { mc.getConnection().sendCommand("interstice explore tall"); stage = 1; ticks = 0; return; }
        if (!mc.level.dimension().equals(IslandWorld.TALL_WORLD)) return;
        if (stage == 1 && ++ticks >= 40) {
            stage = 2; ticks = 0;
            server.execute(() -> { try {
                var player = server.getPlayerList().getPlayer(uuid); var level = player.serverLevel();
                for (int radius = 0; radius <= 16 && barrelPos == null; radius++) for (int x = -radius; x <= radius && barrelPos == null; x++) for (int z = -radius; z <= radius && barrelPos == null; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius || !WatchpostRuins.candidate(level.getSeed(), x, z)) continue;
                    var chunk = level.getChunk(x, z);
                    for (BlockPos point : chunk.getBlockEntitiesPos()) if (chunk.getBlockState(point).is(Blocks.BARREL)) { barrelPos = point.immutable(); break; }
                }
                if (barrelPos == null) throw new IllegalStateException("No natural ruin within 16 chunks");
                var barrel = (BarrelBlockEntity)level.getBlockEntity(barrelPos); int components = 0;
                for (int slot = 0; slot < barrel.getContainerSize(); slot++) if (barrel.getItem(slot).is(Interstice.PRESSURE_COUPLER.get())) components += barrel.getItem(slot).getCount();
                if (components != 1) throw new IllegalStateException("Naturally generated loot barrel did not give its component");
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 12000, 0, false, false));
                player.teleportTo(level, barrelPos.getX() + 7.0, barrelPos.getY() + 6.0, barrelPos.getZ() - 7.0, java.util.Set.of(), 45, 25);
                player.getAbilities().flying = true; player.onUpdateAbilities();
                JsonObject result = new JsonObject(); result.addProperty("passed", true); result.addProperty("naturally_generated", true); result.addProperty("component_count", components);
                result.addProperty("barrel_x", barrelPos.getX()); result.addProperty("barrel_y", barrelPos.getY()); result.addProperty("barrel_z", barrelPos.getZ());
                if (MODE.equals("create")) {
                    barrel.clearContent(); barrel.setChanged();
                    level.setBlock(barrelPos.above(), Blocks.COBBLESTONE.defaultBlockState(), 3);
                    result.addProperty("barrel_emptied", true);
                }
                Files.writeString(mc.gameDirectory.toPath().resolve(MODE.equals("create") ? "watchpost-create-validation.json" : "watchpost-validation.json"), result.toString()); ready = true;
            } catch (Throwable error) { failure = error; } });
        } else if (stage == 2 && ready) {
            if (ticks++ == 0) { mc.player.getAbilities().flying = true; mc.player.onUpdateAbilities(); }
            if (ticks < 100) return;
            // Match the real camera to the authored structure; keep pose diagnostics with the image.
            mc.player.setYRot(45); mc.player.setXRot(25);
            try {
                var path = mc.gameDirectory.toPath().resolve(MODE.equals("create") ? "watchpost-create-validation.json" : "watchpost-validation.json");
                var data = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                data.addProperty("camera_x", mc.player.getX()); data.addProperty("camera_y", mc.player.getY()); data.addProperty("camera_z", mc.player.getZ());
                data.addProperty("camera_yaw", mc.player.getYRot()); data.addProperty("camera_pitch", mc.player.getXRot()); Files.writeString(path, data.toString());
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            Screenshot.grab(mc.gameDirectory, "natural-watchpost.png", mc.getMainRenderTarget(), message -> System.out.println("WATCHPOST_SCREENSHOT " + message.getString()));
            stage = 3; ticks = 0;
        } else if (stage == 3 && ++ticks >= 40) { System.out.println("WATCHPOST_VALIDATION passed"); mc.stop(); }
    }
}
