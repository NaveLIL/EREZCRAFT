package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.water.WaterTransmutationSavedData;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class RiftsilverVanillaFluidGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void waterAndLavaRoundTripsKeepTheSilverBucket(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos local = new BlockPos(3, 2, 3);
        BlockPos pos = h.absolutePos(local);
        h.setBlock(local.below(), Blocks.STONE);
        for (boolean water : new boolean[]{true, false}) {
            var fluid = water ? Blocks.WATER : Blocks.LAVA;
            var filledItem = water ? Interstice.RIFTSILVER_WATER_BUCKET.get() : Interstice.RIFTSILVER_LAVA_BUCKET.get();
            h.setBlock(local, fluid);
            player.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
            player.setXRot(90.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Interstice.RIFTSILVER_BUCKET.get()));
            var picked = Interstice.RIFTSILVER_BUCKET.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            h.assertTrue(picked.getObject().is(filledItem), "Vanilla fluid pickup must return a filled silver bucket");
            h.assertTrue(h.getBlockState(local).isAir(), "Pickup must remove the source");
            player.setItemInHand(InteractionHand.MAIN_HAND, picked.getObject());
            var poured = filledItem.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            h.assertTrue(poured.getResult().consumesAction(), "Filled silver bucket must pour normally");
            h.assertTrue(poured.getObject().is(Interstice.RIFTSILVER_BUCKET.get()), "Pouring must return an empty silver bucket");
            h.assertTrue(h.getBlockState(local).is(fluid), "Pouring must restore the correct source");
            h.setBlock(local, Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cauldronRoundTripsKeepTheSilverBucket(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos local = new BlockPos(3, 2, 3);
        BlockPos pos = h.absolutePos(local);
        for (boolean water : new boolean[]{true, false}) {
            var filledItem = water ? Interstice.RIFTSILVER_WATER_BUCKET.get() : Interstice.RIFTSILVER_LAVA_BUCKET.get();
            var fullCauldron = water ? Blocks.WATER_CAULDRON : Blocks.LAVA_CAULDRON;
            h.setBlock(local, Blocks.CAULDRON);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(filledItem));
            CauldronInteraction.EMPTY.map().get(filledItem).interact(h.getBlockState(local), h.getLevel(), pos,
                    player, InteractionHand.MAIN_HAND, player.getMainHandItem());
            h.assertTrue(h.getBlockState(local).is(fullCauldron), "Silver bucket must fill the cauldron");
            h.assertTrue(player.getMainHandItem().is(Interstice.RIFTSILVER_BUCKET.get()), "Cauldron fill must return silver");
            var interactions = water ? CauldronInteraction.WATER : CauldronInteraction.LAVA;
            interactions.map().get(Interstice.RIFTSILVER_BUCKET.get()).interact(h.getBlockState(local), h.getLevel(), pos,
                    player, InteractionHand.MAIN_HAND, player.getMainHandItem());
            h.assertTrue(h.getBlockState(local).is(Blocks.CAULDRON), "Cauldron pickup must drain it");
            h.assertTrue(player.getMainHandItem().is(filledItem), "Cauldron pickup must return the matching silver bucket");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dispenserPourAndStackedPickupKeepSilver(GameTestHelper h) {
        BlockPos local = new BlockPos(3, 2, 3);
        BlockPos pos = h.absolutePos(local);
        var state = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.UP);
        h.setBlock(local, state);
        var dispenser = (DispenserBlockEntity) h.getLevel().getBlockEntity(pos);
        var source = new BlockSource(h.getLevel(), pos, state, dispenser);
        for (boolean water : new boolean[]{true, false}) {
            var filledItem = water ? Interstice.RIFTSILVER_WATER_BUCKET.get() : Interstice.RIFTSILVER_LAVA_BUCKET.get();
            var fluid = water ? Blocks.WATER : Blocks.LAVA;
            dispenser.clearContent();
            dispenser.setItem(0, new ItemStack(filledItem));
            ItemStack empty = DispenserBlock.DISPENSER_REGISTRY.get(filledItem).dispense(source, dispenser.getItem(0));
            h.assertTrue(empty.is(Interstice.RIFTSILVER_BUCKET.get()), "Dispenser must return empty riftsilver");
            h.assertTrue(h.getLevel().getBlockState(pos.above()).is(fluid), "Dispenser must pour the selected fluid");
            dispenser.setItem(0, new ItemStack(Interstice.RIFTSILVER_BUCKET.get(), 2));
            ItemStack remaining = DispenserBlock.DISPENSER_REGISTRY.get(Interstice.RIFTSILVER_BUCKET.get())
                    .dispense(source, dispenser.getItem(0));
            h.assertTrue(remaining.is(Interstice.RIFTSILVER_BUCKET.get()) && remaining.getCount() == 1,
                    "Stacked pickup must consume exactly one silver bucket");
            h.assertTrue(dispenser.getItem(1).is(filledItem), "Filled silver bucket must enter another dispenser slot");
            h.assertTrue(h.getLevel().getBlockState(pos.above()).isAir(), "Dispenser pickup must remove the source");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void silverWaterStillTransmutesInInterstice(GameTestHelper h) {
        var world = h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD);
        BlockPos pos = new BlockPos(64008, 100, 64008);
        world.getChunkAt(pos);
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var bucket = Interstice.RIFTSILVER_WATER_BUCKET.get();
        h.assertTrue(bucket.emptyContents(null, world, pos, null), "Silver water bucket must empty in Interstice");
        h.assertTrue(world.getBlockState(pos).is(Interstice.LIGHT_BLOCK.get()), "Silver water must undergo normal transmutation");
        h.assertTrue(WaterTransmutationSavedData.get(world).getExpiringBlocks().containsKey(pos),
                "Transmuted silver-bucket water must retain the evaporation deadline");
        WaterTransmutationSavedData.get(world).remove(pos);
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void silverLavaFuelAndCraftingRemaindersKeepTheMetal(GameTestHelper h) {
        var lava = Interstice.RIFTSILVER_LAVA_BUCKET.get();
        h.assertTrue(lava.getBurnTime(new ItemStack(lava), RecipeType.SMELTING) == 20000,
                "Silver lava bucket must provide normal lava-bucket fuel duration");
        for (BucketItem item : new BucketItem[]{Interstice.RIFTSILVER_WATER_BUCKET.get(), lava,
                Interstice.RIFTSILVER_HEAVY_BUCKET.get(), Interstice.RIFTSILVER_INVERTED_BUCKET.get()}) {
            h.assertTrue(item.getCraftingRemainingItem(new ItemStack(item)).is(Interstice.RIFTSILVER_BUCKET.get()),
                    "All filled riftsilver buckets must return silver as their crafting/fuel remainder");
        }
        h.succeed();
    }
}
