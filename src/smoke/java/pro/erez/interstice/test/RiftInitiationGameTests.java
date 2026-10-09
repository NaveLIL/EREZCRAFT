package pro.erez.interstice.test;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.expedition.ExpeditionLedger;
import pro.erez.interstice.rift.RiftInitiation;
import pro.erez.interstice.rift.RiftLinks;
import pro.erez.interstice.rift.RiftTravel;
import pro.erez.interstice.worldgen.IslandWorld;

/** The first discovery aid uses native block interaction, rift validation and advancement rewards. */
@GameTestHolder("interstice_rifts")
@PrefixGameTestTemplate(false)
public final class RiftInitiationGameTests {
    private static final BlockPos CAULDRON = new BlockPos(5, 2, 5);

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Interstice.ID, path);
    }

    private static ServerPlayer apparatus(GameTestHelper h) {
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 13; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        h.setBlock(CAULDRON.below(), Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.UP));
        h.setBlock(CAULDRON, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        var player = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RiftInitiation.UNSTABLE_INITIATOR.get(), 2));
        return player;
    }

    private static BlockHitResult hit(GameTestHelper h) {
        var pos = h.absolutePos(CAULDRON);
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static boolean use(GameTestHelper h, ServerPlayer player) {
        return player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(h)).consumesAction();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeAmethystDiscoveryUnlocksAnExternalOnlyFiniteInitiatorRecipe(GameTestHelper h) {
        var player = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        try {
            var preparation = player.server.getAdvancements().get(id("rift_preparation"));
            h.assertTrue(preparation != null && !player.getAdvancements().getOrStartProgress(preparation).isDone()
                    && !player.getRecipeBook().contains(id("unstable_rift_initiator")), "A fresh player cannot start with the discovery hint or its recipe");
            player.getInventory().setItem(2, new ItemStack(Items.AMETHYST_SHARD));
            player.inventoryMenu.broadcastChanges();
            h.assertTrue(player.getAdvancements().getOrStartProgress(preparation).isDone()
                    && player.getRecipeBook().contains(id("unstable_rift_initiator")),
                    "A native inventory change must display the amethyst discovery hint and unlock its construction recipe");
            var input = CraftingInput.of(3, 3, List.of(new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.AMETHYST_SHARD),
                    new ItemStack(Items.REDSTONE), new ItemStack(Items.GLASS), new ItemStack(Items.REDSTONE),
                    ItemStack.EMPTY, new ItemStack(Items.AMETHYST_SHARD), ItemStack.EMPTY));
            var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
            var output = recipe.value().assemble(input, h.getLevel().registryAccess());
            h.assertTrue(recipe.id().equals(id("unstable_rift_initiator")) && output.is(RiftInitiation.UNSTABLE_INITIATOR.get()) && output.getCount() == 1,
                    "Three amethyst, one gold, two redstone and one glass must craft exactly one finite entry charge using the installed native recipe");
        } finally {
            TestPlayers.remove(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void nativeCauldronUseConsumesOneAfterSuccessRejectsReplayAndKeepsSpentRecallRights(GameTestHelper h) {
        var player = apparatus(h);
        var initiators = player.getMainHandItem();
        var staleContext = new UseOnContext(h.getLevel(), player, InteractionHand.MAIN_HAND, initiators, hit(h));
        try {
            h.getLevel().setWeatherParameters(1200, 0, false, false);
            h.getLevel().setRainLevel(0);
            h.getLevel().setThunderLevel(0);
            h.assertTrue(!h.getLevel().isThundering(), "The controlled discovery fixture must have no storm");
            var source = new RiftLinks.Endpoint(h.getLevel().dimension(), player.blockPosition(), Direction.Axis.X);
            int before = RiftLinks.get(player.server).size();
            h.assertTrue(use(h, player) && player.level().dimension().equals(IslandWorld.CURRENT_WORLD) && initiators.getCount() == 1,
                    "A real cauldron right-click must reach the current realm and consume exactly one paid initiator only after successful transfer");
            player.hasChangedDimension();
            var links = RiftLinks.get(player.server);
            var link = links.source(source, RiftLinks.Kind.CAULDRON);
            h.assertTrue(link != null && links.size() == before + 1
                    && h.getLevel().getBlockState(h.absolutePos(CAULDRON)).getValue(LayeredCauldronBlock.LEVEL) == 3,
                    "The paid discovery must own one normal rift link while leaving the full cauldron and outside apparatus intact");
            var guidance = player.server.getAdvancements().get(id("arrival_guidance"));
            h.assertTrue(guidance != null && player.getAdvancements().getOrStartProgress(guidance).isDone()
                    && player.getRecipeBook().contains(id("rift_frame")) && player.getRecipeBook().contains(id("rift_lens")),
                    "Actual arrival must display native shelter/return guidance and unlock the local-material permanent portal recipes");
            h.assertTrue(!RiftInitiation.UNSTABLE_INITIATOR.get().useOn(staleContext).consumesAction()
                    && initiators.getCount() == 1 && links.size() == before + 1,
                    "A repeated old source interaction after transfer cannot consume another charge or create a duplicate entrance");
            var ledger = ExpeditionLedger.get(player.server);
            var journey = ledger.journey(player.getUUID());
            h.assertTrue(journey != null && journey.entered() && !journey.spent() && ledger.spend(player.getUUID(), journey.id()),
                    "Native discovery must use the existing one-use server emergency entitlement");
            player.getPersistentData().remove(RiftTravel.COOLDOWN);
            h.assertTrue(RiftTravel.returnThroughEcho(player, link.echo().pos()), "The existing echo must return the player to the original outside apparatus");
            player.hasChangedDimension();
            player.getPersistentData().remove(RiftTravel.COOLDOWN);
            h.assertTrue(use(h, player) && initiators.isEmpty() && links.size() == before + 1
                    && ledger.journey(player.getUUID()).id().equals(journey.id()) && ledger.journey(player.getUUID()).spent(),
                    "A second paid expedition must reuse the same connection without renewing spent emergency rights or creating a free permanent portal");
        } finally {
            TestPlayers.remove(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void partialWaterWrongSupportAndCooldownRejectUseWithoutPayment(GameTestHelper h) {
        var player = apparatus(h);
        var initiators = player.getMainHandItem();
        try {
            h.setBlock(CAULDRON, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
            h.assertTrue(!use(h, player) && initiators.getCount() == 2 && player.serverLevel() == h.getLevel(), "A partial cauldron must not spend or transfer");
            h.setBlock(CAULDRON, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
            h.setBlock(CAULDRON.below(), Blocks.COPPER_BLOCK);
            h.assertTrue(!use(h, player) && initiators.getCount() == 2, "A broad conductor from the random anomaly path cannot replace the initiator's upright lightning rod");
            h.setBlock(CAULDRON.below(), Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.DOWN));
            h.assertTrue(!use(h, player) && initiators.getCount() == 2, "The apparatus hint must agree with the actual rod orientation requirement");
            h.setBlock(CAULDRON.below(), Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.UP));
            player.getPersistentData().putLong(RiftTravel.COOLDOWN, RiftTravel.now(player) + 100);
            h.assertTrue(!use(h, player) && initiators.getCount() == 2 && player.serverLevel() == h.getLevel()
                    && ExpeditionLedger.get(player.server).journey(player.getUUID()) == null,
                    "A current rift cooldown must leave the charge and unclaimed emergency entitlement untouched");
        } finally {
            TestPlayers.remove(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void deniedTransferAndBrokenDestinationLeaveThePaidChargeUnspent(GameTestHelper h) {
        var player = apparatus(h);
        var initiators = player.getMainHandItem();
        var source = new RiftLinks.Endpoint(h.getLevel().dimension(), player.blockPosition(), Direction.Axis.X);
        Consumer<EntityTravelToDimensionEvent> deny = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(deny);
        try {
            h.assertTrue(!use(h, player) && initiators.getCount() == 2 && player.serverLevel() == h.getLevel() && !RiftTravel.coolingDown(player),
                    "Server-denied native travel cannot consume the initiator or claim a completed entry");
        } catch (RuntimeException | Error failure) {
            TestPlayers.remove(player);
            throw failure;
        } finally {
            NeoForge.EVENT_BUS.unregister(deny);
        }
        try {
            var link = RiftLinks.get(player.server).source(source, RiftLinks.Kind.CAULDRON);
            h.assertTrue(link != null, "The failed-transfer fixture must have an actual validated destination echo");
            var destination = player.server.getLevel(link.echo().dimension());
            destination.removeBlock(link.echo().pos(), false);
            h.assertTrue(!use(h, player) && initiators.getCount() == 2 && player.serverLevel() == h.getLevel()
                    && ExpeditionLedger.get(player.server).journey(player.getUUID()) == null,
                    "A destroyed real landing echo must refuse another charge and preserve the original unclaimed emergency right");
        } finally {
            TestPlayers.remove(player);
        }
        h.succeed();
    }
}
