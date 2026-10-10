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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandWorld;

/** Opens a fresh world for the owner, then relinquishes control. Not part of the release jar. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class IslandPreview {
    private static final long SEED = 20261006L;
    private static final boolean PLAYTEST = Boolean.getBoolean("interstice.playtest");
    private static final boolean V6 = Boolean.getBoolean("interstice.previewV6");
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> TARGET = V6 ? IslandWorld.TENSION_WORLD : IslandWorld.TALL_WORLD;
    private static boolean started;
    private static boolean finished;
    private static long deadline;
    private static String worldName;
    private static int stage;
    private static int ticks;

    private IslandPreview() {}

    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if ((!Boolean.getBoolean("interstice.islandPreview") && !PLAYTEST) || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
            worldName = (PLAYTEST ? "interstice-playtest-" : "island-preview-") + System.currentTimeMillis();
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(6);
            mc.options.cloudStatus().set(CloudStatus.FANCY);
            mc.options.hideGui = false;
            mc.options.fov().set(85);
            if (V6) mc.getWindow().setTitle("EREZCRAFT — V6 visual preview");
            System.out.println("ISLAND_PREVIEW_CREATING " + mc.gameDirectory.toPath().resolve("saves").resolve(worldName));
            mc.createWorldOpenFlows().createFreshLevel(worldName,
                    new LevelSettings(PLAYTEST ? "Interstice - physics playtest" : "Interstice - island preview",
                            PLAYTEST ? GameType.SURVIVAL : GameType.CREATIVE, false,
                            V6 || PLAYTEST ? Difficulty.NORMAL : Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
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
            mc.getConnection().sendCommand(V6 ? "interstice explore v6" : "interstice explore tall");
            stage = 1;
            return;
        }
        if (!mc.level.dimension().equals(TARGET) || !mc.level.hasChunkAt(mc.player.blockPosition())) return;
        if (stage == 1 && ++ticks >= 40) {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                if (player == null || !player.level().dimension().equals(TARGET)) return;
                if (PLAYTEST) {
                    player.setGameMode(GameType.SURVIVAL);
                    player.serverLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
                    player.setRespawnPosition(IslandWorld.TALL_WORLD, player.blockPosition(), -35, true, false);
                    var tide = pro.erez.interstice.tide.TideManager.getSavedData(server);
                    tide.setPhase(pro.erez.interstice.tide.TidePhase.CALM, 12000);
                    pro.erez.interstice.tide.TideSync.broadcast(tide.snapshot());
                    for (ItemStack stack : new ItemStack[]{
                            new ItemStack(Interstice.TIDE_INDICATOR.get()),
                            new ItemStack(Interstice.RIFTSILVER_BUCKET.get(), 8),
                            new ItemStack(Interstice.RIFTSILVER_HEAVY_BUCKET.get()),
                            new ItemStack(Interstice.RIFTSILVER_INVERTED_BUCKET.get()),
                            new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.LAVA_BUCKET),
                            new ItemStack(Items.BUCKET, 8), new ItemStack(Items.IRON_PICKAXE),
                            new ItemStack(Items.COOKED_BEEF, 64), new ItemStack(Items.GLASS, 64),
                            new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(Items.OAK_SLAB, 64),
                            new ItemStack(Items.LADDER, 32), new ItemStack(Items.OAK_LEAVES, 64)}) {
                        player.getInventory().add(stack);
                    }
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "/interstice tide set surge — test the tide; /interstice leave — return"), false);
                } else {
                    if (V6) player.setRespawnPosition(TARGET, player.blockPosition(), -35, true, false);
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.serverLevel(), player.getX(), player.getY() + 3.0,
                            player.getZ(), Set.of(), -35, 12);
                }
            });
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && ++ticks >= 60
                && (PLAYTEST ? !mc.player.isCreative() : mc.player.getAbilities().flying)) {
            finished = true;
            Screenshot.grab(mc.gameDirectory, PLAYTEST ? "playtest-ready.png" : "island-preview.png", mc.getMainRenderTarget(),
                    message -> System.out.println("ISLAND_PREVIEW_SCREENSHOT " + message.getString()));
            report(mc, true, "ready-for-owner");
        }
    }

    /** Only this explicit preview profile redirects the bare re-entry command. */
    @SubscribeEvent
    public static void command(net.neoforged.neoforge.event.CommandEvent event) {
        if (!V6) return;
        var parse = event.getParseResults();
        var text = parse.getReader().getString().strip();
        if (!text.equals("interstice explore")) return;
        var source = parse.getContext().getSource();
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer)) return;
        event.setParseResults(source.getServer().getCommands().getDispatcher().parse("interstice explore v6", source));
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
            Files.writeString(mc.gameDirectory.toPath().resolve(PLAYTEST ? "playtest-ready.json" : "preview-ready.json"), json.toString());
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        System.out.println("ISLAND_PREVIEW " + json);
    }
}
