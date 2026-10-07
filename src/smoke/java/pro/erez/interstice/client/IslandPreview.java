package pro.erez.interstice.client;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.Set;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandWorld;

/** Opens a fresh world for the owner, then relinquishes control. Not part of the release jar. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class IslandPreview {
    private static final long SEED = 20261006L;
    private static boolean started;
    private static boolean finished;
    private static long deadline;
    private static String worldName;
    private static int stage;
    private static int ticks;

    private IslandPreview() {}

    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("interstice.islandPreview") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
            worldName = "island-preview-" + System.currentTimeMillis();
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(6);
            mc.options.cloudStatus().set(CloudStatus.FANCY);
            mc.options.hideGui = false;
            mc.options.fov().set(85);
            System.out.println("ISLAND_PREVIEW_CREATING " + mc.gameDirectory.toPath().resolve("saves").resolve(worldName));
            mc.createWorldOpenFlows().createFreshLevel(worldName,
                    new LevelSettings("Interstice - island preview", GameType.CREATIVE, false,
                            Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(SEED, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
            return;
        }
        if (!started) return;
        if (System.nanoTime() > deadline) {
            finished = true;
            report(mc, false, "entry-timeout");
            return;
        }
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.screen != null) return;
        if (stage == 0) {
            mc.getConnection().sendCommand("interstice explore");
            stage = 1;
            return;
        }
        if (!mc.level.dimension().equals(IslandWorld.TALL_WORLD) || !mc.level.hasChunkAt(mc.player.blockPosition())) return;
        if (stage == 1 && ++ticks >= 40) {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                if (player == null || !player.level().dimension().equals(IslandWorld.TALL_WORLD)) return;
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), player.getX(), player.getY() + 3.0,
                        player.getZ(), Set.of(), -35, 12);
            });
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && ++ticks >= 60 && mc.player.getAbilities().flying) {
            finished = true;
            Screenshot.grab(mc.gameDirectory, "island-preview.png", mc.getMainRenderTarget(),
                    message -> System.out.println("ISLAND_PREVIEW_SCREENSHOT " + message.getString()));
            report(mc, true, "ready-for-owner");
        }
    }

    private static void report(Minecraft mc, boolean ready, String reason) {
        JsonObject json = new JsonObject();
        json.addProperty("ready", ready);
        json.addProperty("reason", reason);
        json.addProperty("world", worldName);
        json.addProperty("seed", SEED);
        json.addProperty("dimension", mc.level == null ? "unavailable" : mc.level.dimension().location().toString());
        if (mc.level != null) {
            var geometry = pro.erez.interstice.geometry.GeometryProfiles.get(mc.level);
            json.addProperty("height", mc.level.getHeight());
            json.addProperty("upper_sea_minimum", geometry.upperMinimum());
            json.addProperty("upper_sea_maximum", geometry.upperMaximum());
        }
        if (mc.player != null) {
            json.addProperty("creative", mc.player.isCreative());
            json.addProperty("flying", mc.player.getAbilities().flying);
            json.addProperty("x", mc.player.getX());
            json.addProperty("y", mc.player.getY());
            json.addProperty("z", mc.player.getZ());
        }
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("preview-ready.json"), json.toString());
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        System.out.println("ISLAND_PREVIEW " + json);
    }
}
