package pro.erez.interstice.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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
import pro.erez.interstice.expedition.ExpeditionLedger;
import pro.erez.interstice.expedition.WayfarerKeyItem;
import pro.erez.interstice.rift.RiftLinks;
import pro.erez.interstice.rift.RiftTravel;
import pro.erez.interstice.worldgen.IslandWorld;

/** Native item-use packets, 200 real ticks and a cold JVM reload; isolated fixture, not M12 Survival acceptance. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class KeyPersistenceVisualSmoke {
    private static final String MODE = System.getProperty("interstice.keyPersistence", "");
    private static final String WORLD = "key-persistence-check";
    private static boolean started;
    private static int stage, ticks;
    private static long deadline;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static ResourceKey<Level> destination=IslandWorld.CURRENT_WORLD;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (MODE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true; deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(7);
            mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(3);
            if (MODE.equals("create")) {
                if (Files.exists(mc.gameDirectory.toPath().resolve("saves/" + WORLD + "/level.dat"))) throw new IllegalStateException("Fresh key verification profile required");
                mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings("Wayfarer key persistence", GameType.SURVIVAL, false,
                        Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261006L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
            } else mc.createWorldOpenFlows().openWorld(WORLD, () -> {});
            return;
        }
        if (!started) return;
        if (SmokeWorldPrompts.advance(mc)) return;
        if (failure != null) throw new IllegalStateException("Key native verification failed", failure);
        if (System.nanoTime() > deadline) throw new IllegalStateException("Key verification timeout stage=" + stage);
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
        if (stage == 0) {
            stage = 1; ticks = 0;
            server.execute(() -> { try {
                var player = server.getPlayerList().getPlayer(uuid);
                if (MODE.equals("create")) {
                    var source = player.serverLevel(); BlockPos feet = player.blockPosition();
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                        source.setBlock(feet.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                        for (int y = 0; y <= 2; y++) source.setBlock(feet.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                    if (!RiftTravel.enter(player, new RiftLinks.Endpoint(Level.OVERWORLD, feet, Direction.Axis.X), RiftLinks.Kind.FISHING)) throw new IllegalStateException("Fixture entry failed");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Interstice.WAYFARER_KEY.get()));
                } else {
                    var ledger = ExpeditionLedger.get(server).journey(uuid); var meta = JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("key-fixture.json"))).getAsJsonObject();
                    if (ledger == null || !ledger.spent() || !ledger.id().toString().equals(meta.get("journey").getAsString())) throw new IllegalStateException("Spent server entitlement did not survive restart");
                    var link=player.getPersistentData().hasUUID(RiftTravel.ACTIVE)?RiftLinks.get(server).byId(player.getPersistentData().getUUID(RiftTravel.ACTIVE)):null;
                    if(link==null)throw new IllegalStateException("Saved key fixture lost its actual persisted link");
                    destination=meta.has("destination_dimension")?ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(meta.get("destination_dimension").getAsString())):link.echo().dimension();
                    if(!destination.equals(link.echo().dimension())||!destination.equals(player.level().dimension()))throw new IllegalStateException("Saved key fixture destination changed after restart");
                    ItemStack copy = player.getMainHandItem().copy();
                    if (WayfarerKeyItem.binding(copy) == null) throw new IllegalStateException("Key binding did not survive restart");
                    player.setItemInHand(InteractionHand.MAIN_HAND, copy);
                }
                ready = true;
            } catch (Throwable error) { failure = error; } });
            return;
        }
        if (!ready || !mc.level.dimension().equals(destination) && stage == 1) return;
        if (stage == 1 && ++ticks >= 60) {
            mc.options.keyUse.setDown(true); mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); stage = 2; ticks = 0;
        } else if (stage == 2) {
            ticks++;
            if (MODE.equals("create")) {
                if (ticks == 80 && !mc.level.dimension().equals(destination)) throw new IllegalStateException("Recall happened before ten seconds");
                if (ticks == 100) Screenshot.grab(mc.gameDirectory, "key-concentration.png", mc.getMainRenderTarget(), message -> {});
                if (mc.level.dimension().equals(Level.OVERWORLD)) {
                    mc.options.keyUse.setDown(false); stage = 3; ticks = 0;
                    server.execute(() -> { try {
                        var player = server.getPlayerList().getPlayer(uuid); var journey = ExpeditionLedger.get(server).journey(uuid);
                        if (journey == null || !journey.spent()) throw new IllegalStateException("Confirmed recall did not consume entitlement");
                        JsonObject meta = new JsonObject(); meta.addProperty("journey", journey.id().toString()); meta.addProperty("passed", true);
                        meta.addProperty("destination_dimension",destination.location().toString());
                        Files.writeString(mc.gameDirectory.toPath().resolve("key-fixture.json"), meta.toString());
                    } catch (Throwable error) { failure = error; } });
                }
            } else if (ticks >= 230) {
                mc.options.keyUse.setDown(false); mc.gameMode.releaseUsingItem(mc.player);
                if (!mc.level.dimension().equals(destination)) throw new IllegalStateException("Copied key bypassed spent entitlement after restart");
                JsonObject result = new JsonObject(); result.addProperty("passed", true); result.addProperty("spent_after_jvm_restart", true); result.addProperty("copied_key_refused", true);
                report(mc, "key-reload-validation.json", result); stage = 5; ticks = 0;
            }
        } else if (stage == 3 && ++ticks >= 80) {
            stage = 4; ticks = 0;
            server.execute(() -> { try {
                var player = server.getPlayerList().getPlayer(uuid); var journey = ExpeditionLedger.get(server).journey(uuid);
                if (!RiftTravel.enter(player, new RiftLinks.Endpoint(Level.OVERWORLD, journey.origin().feet(), Direction.Axis.X), RiftLinks.Kind.FISHING)) throw new IllegalStateException("Repeat entry failed");
                if (!ExpeditionLedger.get(server).journey(uuid).spent()) throw new IllegalStateException("Repeat entry restored entitlement");
            } catch (Throwable error) { failure = error; } });
        } else if (stage == 4 && mc.level.dimension().equals(destination) && ++ticks >= 50) {
            JsonObject result = new JsonObject(); result.addProperty("passed", true); result.addProperty("real_item_channel_ticks", 200); result.addProperty("spent_survives_repeat_entry", true);
            report(mc, "key-create-validation.json", result); stage = 5; ticks = 0;
        } else if (stage == 5 && ++ticks >= 40) { mc.options.keyUse.setDown(false); mc.stop(); }
    }
    private static void report(Minecraft mc, String filename, JsonObject result) {
        try { Files.writeString(mc.gameDirectory.toPath().resolve(filename), result.toString()); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        System.out.println("KEY_VALIDATION " + filename + " " + result);
    }
}
