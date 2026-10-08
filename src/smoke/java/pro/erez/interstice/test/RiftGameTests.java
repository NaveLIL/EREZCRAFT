package pro.erez.interstice.test;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.rift.*;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder("interstice_rifts")
@PrefixGameTestTemplate(false)
public final class RiftGameTests {
    @GameTest(template = "empty", timeoutTicks = 260)
    public static void virtualPlayerSessionsCloseBeforeServerShutdown(GameTestHelper h) {
        // Minecraft 1.21.1 needs live ticks to finish generation leases after rapid mock-player transfers.
        h.runAtTickTime(200, () -> {
            h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayers().isEmpty(), "All virtual client sessions must close before the server saves worlds");
            h.succeed();
        });
    }
    private static final BlockPos ROOT = new BlockPos(6, 2, 6);
    private static void floor(GameTestHelper h) {
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 13; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }
    private static RiftGeometry.Frame frame(GameTestHelper h, Direction.Axis axis) {
        floor(h);
        RiftGeometry.Frame frame = new RiftGeometry.Frame(h.absolutePos(ROOT), axis);
        for (int w = -1; w <= 2; w++) for (int y = -1; y <= 3; y++) {
            if ((w == -1 || w == 2) && (y == -1 || y == 3)) continue;
            if (w == -1 || w == 2 || y == -1 || y == 3) h.getLevel().setBlock(frame.at(w, y), Interstice.RIFT_FRAME.get().defaultBlockState(), 3);
        }
        return frame;
    }
    private static ServerPlayer player(GameTestHelper h, BlockPos local) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "rift-test-player"), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        BlockPos pos = h.absolutePos(local);
        player.teleportTo(h.getLevel(), pos.getX() + .5, pos.getY() + .01, pos.getZ() + .5, java.util.Set.of(), 0, 0);
        return player;
    }
    private static void remove(ServerPlayer player) { player.server.getPlayerList().remove(player); }
    private static RandomSource winning(int bound) {
        for (long seed = 0; seed < 10000; seed++) if (RandomSource.create(seed).nextInt(bound) == 0) return RandomSource.create(seed);
        throw new AssertionError("No test roll");
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void framesActivateInBothAxesAndRejectObstructions(GameTestHelper h) {
        var frame = frame(h, Direction.Axis.X);
        h.getLevel().setBlock(frame.at(0, 1), Blocks.CHEST.defaultBlockState(), 3);
        h.assertTrue(RiftGeometry.activate(h.getLevel(), frame.at(-1, 0)) == null, "Activation must preserve blocked interiors");
        h.assertTrue(h.getLevel().getBlockState(frame.at(0, 1)).is(Blocks.CHEST), "Chest must remain intact");
        h.getLevel().removeBlock(frame.at(0, 1), false);
        h.assertTrue(RiftGeometry.activate(h.getLevel(), frame.at(-1, 0)) != null && RiftGeometry.valid(h.getLevel(), frame, true), "X frame must activate without corners");
        for (int w = -1; w <= 2; w++) for (int y = -1; y <= 3; y++) h.getLevel().removeBlock(frame.at(w, y), false);
        frame = frame(h, Direction.Axis.Z);
        h.assertTrue(RiftGeometry.activate(h.getLevel(), frame.at(-1, 0)) != null && RiftGeometry.valid(h.getLevel(), frame, true), "Z frame must activate without corners");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void breakingFrameCollapsesPortal(GameTestHelper h) {
        var frame = frame(h, Direction.Axis.X); RiftGeometry.activate(h.getLevel(), frame.at(-1, 0));
        h.getLevel().removeBlock(frame.at(-1, 1), false);
        h.runAtTickTime(8, () -> {
            for (int w = 0; w < 2; w++) for (int y = 0; y < 3; y++) h.assertTrue(h.getLevel().getBlockState(frame.at(w, y)).isAir(), "Broken frames must close all portal cells");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void linksSurviveSerializationAndIgnoreMalformedRecords(GameTestHelper h) {
        RiftLinks data = new RiftLinks();
        var source = new RiftLinks.Endpoint(Level.OVERWORLD, new BlockPos(15, 65, -32), Direction.Axis.Z);
        var target = new RiftLinks.Endpoint(IslandWorld.TALL_WORLD, new BlockPos(-4, 170, 8), Direction.Axis.X);
        var link = data.add(RiftLinks.Kind.PORTAL, source, target);
        var tag = data.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
        tag.getList("Links", 10).add(new net.minecraft.nbt.CompoundTag());
        RiftLinks reload = RiftLinks.load(tag, h.getLevel().registryAccess());
        h.assertTrue(reload.size() == 1 && reload.byId(link.id()).equals(link), "Links must retain UUID, dimensions, positions and axis across saves");
        h.assertTrue(reload.echo(target.dimension(), target.pos()).source().equals(source), "Return lookup must preserve shared connections");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void portalRoundTripUsesShelteredLandingAndCooldown(GameTestHelper h) {
        var frame = frame(h, Direction.Axis.X); RiftGeometry.activate(h.getLevel(), frame.at(-1, 0));
        ServerPlayer player = player(h, ROOT);
        try {
            h.assertTrue(RiftTravel.enter(player, frame.endpoint(h.getLevel()), RiftLinks.Kind.PORTAL), "Portal must enter without commands");
            h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD), "New entrances must use tall islands");
            h.assertTrue(ShelterDetector.isSheltered(player.level(), player), "First landing must have a roof against the tide");
            var link = RiftLinks.get(player.server).byId(player.getPersistentData().getUUID(RiftTravel.ACTIVE));
            h.assertTrue(!RiftTravel.returnThroughEcho(player, link.echo().pos()), "Immediate bounce must be blocked");
            player.getPersistentData().remove(RiftTravel.COOLDOWN);
            h.assertTrue(RiftTravel.returnThroughEcho(player, link.echo().pos()), "Echo must return through its linked entrance");
            h.assertTrue(player.level().dimension().equals(Level.OVERWORLD) && RiftSafety.standing(h.getLevel(), player.blockPosition()), "Return landing must be safe");
            h.succeed();
        } finally { remove(player); }
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void twoPlayersShareLinkButNotCooldown(GameTestHelper h) {
        var frame = frame(h, Direction.Axis.X); RiftGeometry.activate(h.getLevel(), frame.at(-1, 0));
        ServerPlayer first = player(h, ROOT), second = player(h, ROOT);
        try {
            h.assertTrue(RiftTravel.enter(first, frame.endpoint(h.getLevel()), RiftLinks.Kind.PORTAL), "First player must enter");
            h.assertTrue(!RiftTravel.coolingDown(second), "Cooldown must belong to each player");
            h.assertTrue(RiftTravel.enter(second, frame.endpoint(h.getLevel()), RiftLinks.Kind.PORTAL), "Second player must enter independently");
            h.assertTrue(first.getPersistentData().getUUID(RiftTravel.ACTIVE).equals(second.getPersistentData().getUUID(RiftTravel.ACTIVE)), "A physical entrance must keep the same shared connection");
            h.succeed();
        } finally { remove(first); remove(second); }
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void destroyedEchoRefusesEntryWithoutMovingPlayer(GameTestHelper h) {
        var frame = frame(h, Direction.Axis.X); RiftGeometry.activate(h.getLevel(), frame.at(-1, 0));
        ServerPlayer player = player(h, ROOT);
        try {
            h.assertTrue(RiftTravel.enter(player, frame.endpoint(h.getLevel()), RiftLinks.Kind.PORTAL), "Initial connection must succeed");
            var link = RiftLinks.get(player.server).byId(player.getPersistentData().getUUID(RiftTravel.ACTIVE));
            var target = player.serverLevel(); player.getPersistentData().remove(RiftTravel.COOLDOWN);
            h.assertTrue(RiftTravel.returnThroughEcho(player, link.echo().pos()), "Return before destruction must work");
            target.removeBlock(link.echo().pos(), false); player.getPersistentData().remove(RiftTravel.COOLDOWN);
            Vec3 before = player.position();
            h.assertTrue(!RiftTravel.enter(player, frame.endpoint(h.getLevel()), RiftLinks.Kind.PORTAL), "Destroyed echo must refuse transfer");
            h.assertTrue(player.serverLevel() == h.getLevel() && player.position().equals(before), "Failed transfer must leave the player in place");
            h.succeed();
        } finally { remove(player); }
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void destroyedSourceRefusesReturnUntilRepaired(GameTestHelper h) {
        var frame = frame(h, Direction.Axis.X); RiftGeometry.activate(h.getLevel(), frame.at(-1, 0));
        ServerPlayer player = player(h, ROOT);
        try {
            h.assertTrue(RiftTravel.enter(player, frame.endpoint(h.getLevel()), RiftLinks.Kind.PORTAL), "Initial connection must succeed");
            var link = RiftLinks.get(player.server).byId(player.getPersistentData().getUUID(RiftTravel.ACTIVE));
            h.getLevel().removeBlock(frame.at(-1, 1), false); player.getPersistentData().remove(RiftTravel.COOLDOWN);
            h.assertTrue(!RiftTravel.returnThroughEcho(player, link.echo().pos()) && player.level().dimension().equals(IslandWorld.TALL_WORLD), "A broken source must not produce an unsafe return");
            h.getLevel().setBlock(frame.at(-1, 1), Interstice.RIFT_FRAME.get().defaultBlockState(), 3);
            RiftGeometry.activate(h.getLevel(), frame.at(-1, 1));
            h.assertTrue(RiftTravel.returnThroughEcho(player, link.echo().pos()), "Repair must reuse the existing saved connection");
            h.succeed();
        } finally { remove(player); }
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void naturalFishingNeedsNoRealmItemsAndKeepsVanillaCatch(GameTestHelper h) {
        floor(h); ServerPlayer player = player(h, new BlockPos(3, 2, 3));
        BlockPos water = h.absolutePos(new BlockPos(3, 2, 5)); h.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        FishingHook hook = new FishingHook(player, h.getLevel(), 0, 0); hook.setPos(water.getX() + .5, water.getY() + .5, water.getZ() + .5);
        h.getLevel().setWeatherParameters(0, 1200, true, true); h.getLevel().setRainLevel(1); h.getLevel().setThunderLevel(1);
        try {
            h.assertTrue(RiftAnomalies.tryFishing(player, hook, winning(RiftAnomalies.FISHING_CHANCE)), "A storm catch must work without realm materials");
            h.assertTrue(player.serverLevel() == h.getLevel(), "Fishing callbacks must not teleport synchronously");
        } catch (Throwable error) { remove(player); throw error; }
        finally { h.getLevel().setWeatherParameters(0, 0, false, false); h.getLevel().setRainLevel(0); h.getLevel().setThunderLevel(0); }
        h.runAtTickTime(40, () -> {
            try { h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD), "Queued catch must enter the realm"); h.succeed(); }
            finally { remove(player); }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void lensCatchEventPreservesLootAndTransfersAfterCallback(GameTestHelper h) {
        floor(h); ServerPlayer player = player(h, new BlockPos(3, 2, 3));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Interstice.RIFT_LENS.get()));
        BlockPos water = h.absolutePos(new BlockPos(3, 2, 5)); h.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        FishingHook hook = new FishingHook(player, h.getLevel(), 0, 0); hook.setPos(water.getX() + .5, water.getY() + .5, water.getZ() + .5);
        var event = new ItemFishedEvent(List.of(new ItemStack(Items.COD, 2)), 1, hook); NeoForge.EVENT_BUS.post(event);
        h.assertTrue(!event.isCanceled() && event.getDrops().getFirst().is(Items.COD) && event.getDrops().getFirst().getCount() == 2 && event.getRodDamage() == 1, "The anomaly must preserve vanilla loot and rod wear");
        h.assertTrue(player.serverLevel() == h.getLevel(), "The event must finish before transfer");
        h.runAtTickTime(40, () -> { try { h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD), "Lens must make a successful catch repeatable without a storm"); h.succeed(); } finally { remove(player); } });
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void cauldronKeepsVanillaBucketPickupBeforeTransfer(GameTestHelper h) {
        floor(h); ServerPlayer player = player(h, new BlockPos(3, 2, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET)); player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Interstice.RIFT_LENS.get()));
        BlockPos pos = h.absolutePos(new BlockPos(5, 2, 3)); h.getLevel().setBlock(pos.below(), Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        var full = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3); h.getLevel().setBlock(pos, full, 3);
        h.assertTrue(RiftAnomalies.tryCauldron(player, pos, true, RandomSource.create(1)), "Conductive full cauldron must queue an anomaly");
        CauldronInteraction.WATER.map().get(Items.BUCKET).interact(full, h.getLevel(), pos, player, InteractionHand.MAIN_HAND, player.getMainHandItem());
        h.assertTrue(player.serverLevel() == h.getLevel(), "Vanilla cauldron operation must finish first");
        h.runAtTickTime(40, () -> { try {
            h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD), "Cauldron must transfer after pickup");
            h.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET) || player.getInventory().contains(new ItemStack(Items.WATER_BUCKET)), "Collected water must remain in the inventory");
            h.succeed();
        } finally { remove(player); } });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void movingAwayCancelsPendingAnomaly(GameTestHelper h) {
        floor(h); ServerPlayer player = player(h, new BlockPos(3, 2, 3));
        h.assertTrue(RiftTravel.requestAnomaly(player, RiftLinks.Kind.CAULDRON), "Anomaly must queue");
        BlockPos original = player.blockPosition(); player.teleportTo(player.getX() + 6, player.getY(), player.getZ());
        h.runAtTickTime(40, () -> { try {
            h.assertTrue(player.serverLevel() == h.getLevel(), "Leaving the effect must cancel pending transfer");
            h.assertTrue(RiftLinks.get(player.server).source(new RiftLinks.Endpoint(Level.OVERWORLD, original, Direction.Axis.X), RiftLinks.Kind.CAULDRON) == null, "Canceled anomalies must not create a link"); h.succeed();
        } finally { remove(player); } });
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void canceledDimensionTravelDoesNotClaimSuccess(GameTestHelper h) {
        var frame = frame(h, Direction.Axis.X); RiftGeometry.activate(h.getLevel(), frame.at(-1, 0)); ServerPlayer player = player(h, ROOT);
        Consumer<EntityTravelToDimensionEvent> deny = event -> { if (event.getEntity() == player) event.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(deny);
        try {
            h.assertTrue(!RiftTravel.enter(player, frame.endpoint(h.getLevel()), RiftLinks.Kind.PORTAL), "Canceled server transfer must report failure");
            h.assertTrue(player.serverLevel() == h.getLevel() && !RiftTravel.coolingDown(player), "Canceled transfer must retain the player and not consume cooldown"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(deny); remove(player); }
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void frameAndLensRecipesRequireRealmResources(GameTestHelper h) {
        var input = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, new ItemStack(Interstice.RIFTSTONE_ITEM.get()), ItemStack.EMPTY,
                new ItemStack(Interstice.RIFTSTONE_ITEM.get()), new ItemStack(Interstice.RIFTSILVER_INGOT.get()), new ItemStack(Interstice.RIFTSTONE_ITEM.get()),
                ItemStack.EMPTY, new ItemStack(Interstice.RIFTSTONE_ITEM.get()), ItemStack.EMPTY));
        var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        var result = recipe.value().assemble(input, h.getLevel().registryAccess());
        h.assertTrue(result.is(Interstice.RIFT_FRAME_ITEM.get()) && result.getCount() == 4, "Four Riftstone and one silver ingot must craft four frames");
        var lens = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, new ItemStack(Items.AMETHYST_SHARD), ItemStack.EMPTY,
                new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Interstice.RIFTSILVER_INGOT.get()), new ItemStack(Items.AMETHYST_SHARD),
                ItemStack.EMPTY, new ItemStack(Items.COMPASS), ItemStack.EMPTY));
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, lens, h.getLevel()).orElseThrow().value().assemble(lens, h.getLevel().registryAccess()).is(Interstice.RIFT_LENS.get()), "The lens must require realm silver"); h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void naturalCauldronDiscoveryNeedsNoRealmMaterials(GameTestHelper h) {
        floor(h); ServerPlayer player = player(h, new BlockPos(3, 2, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        BlockPos pos = h.absolutePos(new BlockPos(5, 2, 3));
        var full = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
        h.getLevel().setBlock(pos.below(), Blocks.COPPER_BLOCK.defaultBlockState(), 3); h.getLevel().setBlock(pos, full, 3);
        h.getLevel().setWeatherParameters(0, 1200, true, true); h.getLevel().setRainLevel(1); h.getLevel().setThunderLevel(1);
        try {
            h.assertTrue(RiftAnomalies.tryCauldron(player, pos, false, winning(RiftAnomalies.CAULDRON_CHANCE)), "The first cauldron discovery must need only Overworld materials");
            h.assertTrue(player.serverLevel() == h.getLevel(), "The bucket callback must finish before transfer"); h.succeed();
        } finally {
            h.getLevel().setWeatherParameters(0, 0, false, false); h.getLevel().setRainLevel(0); h.getLevel().setThunderLevel(0); remove(player);
        }
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void portalBuiltInsideReturnsNearOriginalExternalEntrance(GameTestHelper h) {
        floor(h); ServerPlayer player = player(h, ROOT);
        BlockPos outside = player.blockPosition();
        try {
            h.assertTrue(RiftTravel.enter(player, new RiftLinks.Endpoint(Level.OVERWORLD, outside, Direction.Axis.X), RiftLinks.Kind.FISHING), "Initial discovery must enter the realm");
            var islands = player.serverLevel();
            var inside = new RiftGeometry.Frame(player.blockPosition().east(10), Direction.Axis.Z);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                islands.setBlock(inside.origin().offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 4; dy++) islands.setBlock(inside.origin().offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
            }
            for (int w = -1; w <= 2; w++) for (int y = -1; y <= 3; y++) {
                if ((w == -1 || w == 2) && (y == -1 || y == 3)) continue;
                if (w == -1 || w == 2 || y == -1 || y == 3) islands.setBlock(inside.at(w, y), Interstice.RIFT_FRAME.get().defaultBlockState(), 3);
            }
            h.assertTrue(RiftGeometry.activate(islands, inside.at(-1, 0)) != null, "Island frame must activate");
            player.teleportTo(islands, inside.origin().getX() + .5, inside.origin().getY() + .01, inside.origin().getZ() + .5, java.util.Set.of(), 0, 0);
            player.getPersistentData().remove(RiftTravel.COOLDOWN);
            h.assertTrue(RiftTravel.enter(player, inside.endpoint(islands), RiftLinks.Kind.PORTAL), "A crafted island portal must lead outside");
            h.assertTrue(player.level().dimension().equals(Level.OVERWORLD) && player.blockPosition().distSqr(outside) < 1600, "An island portal must use the remembered external entrance region");
            h.succeed();
        } finally { remove(player); }
    }
}
