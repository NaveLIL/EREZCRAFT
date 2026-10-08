package pro.erez.interstice.client;

import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.TideSproutBlock;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideSync;
import pro.erez.interstice.worldgen.GloomcrownTree;
import pro.erez.interstice.worldgen.IslandWorld;

/** A disposable native-renderer gallery. Never attaches to the owner's playtest world. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class FloraVisualSmoke {
    private static boolean started;
    private static int stage, ticks;
    private static long deadline;
    private static int x, z, y;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("interstice.floraSmoke")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            mc.options.renderDistance().set(5);
            mc.options.fov().set(65);
            mc.options.ambientOcclusion().set(true);
            mc.createWorldOpenFlows().createFreshLevel("flora-gallery-" + System.currentTimeMillis(),
                    new LevelSettings("Interstice flora gallery", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(20261006L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
            return;
        }
        if (!started) return;
        if (System.nanoTime() > deadline) throw new IllegalStateException("Flora gallery timed out");
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.screen != null) return;
        if (stage == 0) { mc.getConnection().sendCommand("interstice explore tall"); stage = 1; ticks = 0; return; }
        if (!mc.level.dimension().equals(IslandWorld.TALL_WORLD)) return;
        if (stage == 1 && ++ticks >= 60) {
            x = mc.player.blockPosition().getX(); z = mc.player.blockPosition().getZ();
            y = mc.player.blockPosition().getY() - 1;
            var uuid = mc.player.getUUID(); var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid); var level = player.serverLevel();
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
                var tide = TideManager.getSavedData(server); tide.setPhase(TidePhase.CALM, 20000); TideSync.broadcast(tide.snapshot());
                for (int dx = -18; dx <= 18; dx++) for (int dz = -16; dz <= 16; dz++) {
                    level.setBlock(new BlockPos(x + dx, y, z + dz), Interstice.ABYSSAL_TURF.get().defaultBlockState(), 3);
                    for (int dy = 1; dy <= 20; dy++) level.setBlock(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 3);
                }
                for (int shape = 0; shape < 3; shape++) GloomcrownTree.plan(new BlockPos(x - 9 + shape * 9, y + 1, z + 5), 6, shape != 1, shape).forEach((pos, state) -> level.setBlock(pos, state, 3));
                for (int dx : new int[]{-3, 0, 3}) level.setBlock(new BlockPos(x + dx, y + 1, z - 5), Interstice.TIDE_SPROUT.get().defaultBlockState(), 3);
                level.setBlock(new BlockPos(x + 5, y + 1, z + 3), Interstice.GLOOMCROWN_SAPLING.get().defaultBlockState(), 3);
                player.getAbilities().flying = true; player.onUpdateAbilities();
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 12000, 0, false, false));
                player.teleportTo(level, x + 10.0, y + 9.0, z - 13.0, java.util.Set.of(), 29, 18);
            });
            stage = 2; ticks = 0;
        } else if (stage == 2 && ++ticks >= 100) {
            capture(mc, "01-gloomcrown-and-buds.png");
            mc.getConnection().sendCommand("tp @s " + (x + .5) + " " + (y + 1.2) + " " + (z - 9.0) + " 0 24");
            stage = 3; ticks = 0;
        } else if (stage == 3 && ++ticks >= 60) {
            capture(mc, "02-compact-bud.png");
            mc.getConnection().sendCommand("interstice tide set surge");
            mc.getConnection().sendCommand("tp @s " + (x + 10.0) + " " + (y + 9.0) + " " + (z - 13.0) + " 29 18");
            stage = 4; ticks = 0;
        } else if (stage == 4 && ++ticks >= 180) {
            capture(mc, "03-bloom-during-surge.png");
            mc.getConnection().sendCommand("effect clear @s minecraft:night_vision");
            mc.getConnection().sendCommand("tp @s " + (x + .5) + " " + (y + 2.0) + " " + (z - 10.0) + " 0 35");
            stage = 5; ticks = 0;
        } else if (stage == 5 && ++ticks >= 80) {
            var state = Interstice.ABYSSAL_TURF.get().defaultBlockState();
            var model = mc.getBlockRenderer().getBlockModel(state);
            if (model.useAmbientOcclusion(state, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, net.minecraft.client.renderer.RenderType.solid()) != net.neoforged.neoforge.common.util.TriState.TRUE) {
                throw new IllegalStateException("Luminous turf did not opt into smooth lighting");
            }
            capture(mc, "04-smooth-light-without-night-vision.png");
            mc.options.ambientOcclusion().set(false);
            mc.levelRenderer.allChanged();
            stage = 6; ticks = 0;
        } else if (stage == 6 && ++ticks >= 80) {
            capture(mc, "05-flat-light-comparison.png");
            mc.options.ambientOcclusion().set(true); mc.levelRenderer.allChanged();
            int height = mc.level.getBlockState(new BlockPos(x, y + 1, z - 5)).getValue(TideSproutBlock.HEIGHT);
            mc.getConnection().sendCommand("tp @s " + (x + .5) + " " + (y + height + .2) + " " + (z - 10.0) + " 0 5");
            stage = 7; ticks = 0;
        } else if (stage == 7 && ++ticks >= 80) {
            capture(mc, "06-bloom-closeup-without-night-vision.png");
            BlockPos root = new BlockPos(x, y + 1, z - 5);
            int height = mc.level.getBlockState(root).getValue(TideSproutBlock.HEIGHT);
            int light = mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, root.above(height).east());
            if (light < 13) throw new IllegalStateException("Bloom did not illuminate its neighbor: " + light);
            try { Files.writeString(mc.gameDirectory.toPath().resolve("flora-validation.json"), "{\"completed\":true,\"screenshots\":6,\"bloom_neighbor_light\":" + light + ",\"smooth_lighting_opt_in\":true,\"tree_forms\":3,\"dimension\":\"interstice:islands_tall\"}"); }
            catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            System.out.println("FLORA_VALIDATION completed bloom_neighbor_light=" + light); stage = 8; ticks = 0;
        } else if (stage == 8 && ++ticks >= 30) {
            mc.stop();
        }
    }
    private static void capture(Minecraft mc, String filename) {
        Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(), message -> System.out.println("FLORA_SCREENSHOT " + message.getString()));
    }
}
