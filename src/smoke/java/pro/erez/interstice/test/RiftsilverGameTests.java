package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class RiftsilverGameTests {

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void ironBucketCorrodesInInventory(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bucket = new ItemStack(Interstice.HEAVY_BUCKET.get());
        player.getInventory().setItem(0, bucket);

        // Tick 105 times to trigger complete corrosion
        for (int i = 0; i < 105; i++) {
            ItemStack stack = player.getInventory().getItem(0);
            if (!stack.isEmpty()) {
                stack.inventoryTick(h.getLevel(), player, 0, true);
            }
        }

        h.assertTrue(player.getInventory().getItem(0).isEmpty(), "Iron bucket must be consumed by acid corrosion");
        BlockPos playerPos = player.blockPosition();
        boolean fluidFound = h.getLevel().getBlockState(playerPos).is(Interstice.HEAVY_BLOCK.get())
                || h.getLevel().getBlockState(playerPos.above()).is(Interstice.HEAVY_BLOCK.get());
        h.assertTrue(fluidFound, "Corroded heavy toxin must spill at player position");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void riftsilverBucketsAreImmuneToCorrosion(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack heavyRift = new ItemStack(Interstice.RIFTSILVER_HEAVY_BUCKET.get());
        ItemStack lightRift = new ItemStack(Interstice.RIFTSILVER_INVERTED_BUCKET.get());
        player.getInventory().setItem(0, heavyRift);
        player.getInventory().setItem(1, lightRift);

        for (int i = 0; i < 110; i++) {
            player.getInventory().getItem(0).inventoryTick(h.getLevel(), player, 0, true);
            player.getInventory().getItem(1).inventoryTick(h.getLevel(), player, 1, false);
        }

        h.assertTrue(!player.getInventory().getItem(0).isEmpty(), "Riftsilver heavy bucket must remain intact");
        h.assertTrue(!player.getInventory().getItem(1).isEmpty(), "Riftsilver inverted bucket must remain intact");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void droppedRiftsilverBucketsAreImmuneToCorrosion(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        ItemEntity heavy = new ItemEntity(h.getLevel(), pos.getX(), pos.getY(), pos.getZ(),
                new ItemStack(Interstice.RIFTSILVER_HEAVY_BUCKET.get()));
        ItemEntity light = new ItemEntity(h.getLevel(), pos.getX(), pos.getY(), pos.getZ(),
                new ItemStack(Interstice.RIFTSILVER_INVERTED_BUCKET.get()));
        try {
            for (int i = 0; i < 110; i++) {
                for (ItemEntity item : new ItemEntity[]{heavy, light}) {
                    if (!item.isRemoved()) {
                        item.getItem().getItem().onEntityItemUpdate(item.getItem(), item);
                    }
                }
            }
            h.assertTrue(!heavy.isRemoved(), "Dropped riftsilver heavy bucket must remain intact");
            h.assertTrue(!light.isRemoved(), "Dropped riftsilver inverted bucket must remain intact");
        } finally {
            heavy.discard();
            light.discard();
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void riftsilverBucketPreservesWaterloggedBlocksAndChestContents(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos local = new BlockPos(2, 2, 2);
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.OAK_SLAB, Blocks.CHEST}) {
            BlockPos pos = h.absolutePos(local);
            h.setBlock(local, block.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
            if (h.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.DIAMOND));
            }
            player.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
            player.setXRot(90.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Interstice.RIFTSILVER_BUCKET.get()));

            var result = Interstice.RIFTSILVER_BUCKET.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            h.assertTrue(result.getObject().is(Interstice.RIFTSILVER_WATER_BUCKET.get()), "Collecting water must retain riftsilver");
            var after = h.getLevel().getBlockState(pos);
            h.assertTrue(after.is(block), "Collecting water must preserve the waterlogged block");
            h.assertTrue(!after.getValue(BlockStateProperties.WATERLOGGED), "Waterlogged property must be cleared");
            if (block == Blocks.CHEST) {
                h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity,
                        "Collecting water must preserve the chest block entity");
                ChestBlockEntity chest = (ChestBlockEntity) h.getLevel().getBlockEntity(pos);
                h.assertTrue(chest.getItem(0).is(Items.DIAMOND), "Chest contents must survive fluid pickup");
                chest.clearContent();
            }
            h.setBlock(local, Blocks.AIR);
            local = local.offset(3, 0, 0);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void riftsilverBucketScoopsHeavyToxin(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, Interstice.HEAVY_BLOCK.get());

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos absolute = h.absolutePos(pos);
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 1.5, absolute.getZ() + 0.5);
        player.setXRot(90.0F); // Look directly down at the liquid

        ItemStack bucketStack = new ItemStack(Interstice.RIFTSILVER_BUCKET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucketStack);

        var result = Interstice.RIFTSILVER_BUCKET.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult().consumesAction(), "Riftsilver bucket must scoop liquid source");
        h.assertTrue(result.getObject().is(Interstice.RIFTSILVER_HEAVY_BUCKET.get()), "Scooping heavy toxin must yield riftsilver heavy bucket");
        h.assertTrue(h.getLevel().getBlockState(absolute).isAir(), "Scooped source must become air");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void riftsilverBucketScoopsLightToxin(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, Interstice.LIGHT_BLOCK.get());

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos absolute = h.absolutePos(pos);
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 1.5, absolute.getZ() + 0.5);
        player.setXRot(90.0F);

        ItemStack bucketStack = new ItemStack(Interstice.RIFTSILVER_BUCKET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucketStack);

        var result = Interstice.RIFTSILVER_BUCKET.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult().consumesAction(), "Riftsilver bucket must scoop light liquid source");
        h.assertTrue(result.getObject().is(Interstice.RIFTSILVER_INVERTED_BUCKET.get()), "Scooping light toxin must yield riftsilver inverted bucket");
        h.assertTrue(h.getLevel().getBlockState(absolute).isAir(), "Scooped light source must become air");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oreGeneratesInsideIslandStone(GameTestHelper h) {
        LevelHeightAccessor height = LevelHeightAccessor.create(0, 128);
        var biome = h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getHolderOrThrow(Biomes.THE_END);
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, height, h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME), null);

        // Pre-fill land region with stone
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 45; y <= 75; y++) {
                    mpos.set(x, y, z);
                    chunk.setBlockState(mpos, Blocks.STONE.defaultBlockState(), false);
                }
            }
        }

        IslandChunkGenerator.generateOres(GeometryProfile.LEGACY, chunk, 987654321L);

        int oreCount = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 128; y++) {
                    mpos.set(x, y, z);
                    if (chunk.getBlockState(mpos).is(Interstice.RIFTSILVER_ORE.get())) {
                        oreCount++;
                        h.assertTrue(y >= GeometryProfile.LEGACY.minLand() && y <= GeometryProfile.LEGACY.maxLand(),
                                "Ore must only generate inside land bounds");
                    }
                }
            }
        }

        h.assertTrue(oreCount > 0, "Chunk ore generator must produce riftsilver ore veins");
        h.assertTrue(oreCount <= 40, "Ore generation must stay reasonably rare");
        h.succeed();
    }
}
