package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.GloomcrownTree;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class AbyssalVegetationGameTests {
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void allTreeFormsHaveConnectedTrunksAndDistinctCrowns(GameTestHelper h) {
        var silhouettes = new java.util.HashSet<java.util.Set<BlockPos>>();
        for (int form = 0; form < 3; form++) {
            BlockPos root = new BlockPos(8, 2, 8);
            var tree = GloomcrownTree.plan(root, 6, true, form);
            var logs = new java.util.HashSet<BlockPos>();
            var crown = new java.util.HashSet<BlockPos>();
            tree.forEach((pos, state) -> {
                if (state.is(Interstice.GLOOMCROWN_LOG.get())) logs.add(pos);
                else { crown.add(pos); h.assertTrue(state.getValue(LeavesBlock.DISTANCE) < 7, "Every crown lobe must connect to wood"); }
            });
            var reached = new java.util.HashSet<BlockPos>();
            var queue = new java.util.ArrayDeque<BlockPos>();
            reached.add(root); queue.add(root);
            while (!queue.isEmpty()) {
                BlockPos current = queue.remove();
                for (var direction : net.minecraft.core.Direction.values()) {
                    BlockPos neighbor = current.relative(direction);
                    if (logs.contains(neighbor) && reached.add(neighbor)) queue.add(neighbor);
                }
            }
            h.assertTrue(reached.equals(logs), "Forks must remain connected to the main trunk in form " + form);
            silhouettes.add(crown);
        }
        h.assertTrue(silhouettes.size() == 3, "Tree forms must have three distinct crown silhouettes");
        h.succeed();
    }
    private static void darkRoom(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BLACK_CONCRETE);
            h.setBlock(new BlockPos(x, 14, z), Blocks.BLACK_CONCRETE);
            if (x == 0 || x == 15 || z == 0 || z == 15) {
                for (int y = 1; y < 14; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BLACK_CONCRETE);
            }
        }
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void stackedTurfLosesOnlyBuriedCover(GameTestHelper h) {
        BlockPos base = new BlockPos(3, 2, 3);
        for (int y = 0; y < 3; y++) h.setBlock(base.above(y), Interstice.ABYSSAL_TURF.get());
        h.runAtTickTime(25, () -> {
            h.assertBlockPresent(Interstice.RIFTSTONE.get(), base);
            h.assertBlockPresent(Interstice.RIFTSTONE.get(), base.above());
            h.assertBlockPresent(Interstice.ABYSSAL_TURF.get(), base.above(2));
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void turfSurvivesGlassLeavesAndDarkness(GameTestHelper h) {
        darkRoom(h);
        BlockPos base = new BlockPos(2, 2, 2);
        h.setBlock(base, Interstice.ABYSSAL_TURF.get());
        h.setBlock(base.above(), Blocks.GLASS);
        h.setBlock(base.east(3), Interstice.ABYSSAL_TURF.get());
        h.setBlock(base.east(3).above(), Interstice.GLOOMCROWN_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        h.setBlock(base.east(6), Interstice.ABYSSAL_TURF.get());
        h.runAtTickTime(25, () -> {
            for (int offset : new int[]{0, 3, 6}) h.assertBlockPresent(Interstice.ABYSSAL_TURF.get(), base.east(offset));
            h.assertTrue(h.getLevel().getMaxLocalRawBrightness(h.absolutePos(base.above())) < 9, "Cover survival must be checked below vanilla grass light requirements");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void turfChecksSlabFaceAndSourceWater(GameTestHelper h) {
        BlockPos base = new BlockPos(2, 2, 2);
        for (int offset : new int[]{0, 3, 6}) h.setBlock(base.east(offset), Interstice.ABYSSAL_TURF.get());
        h.setBlock(base.above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        h.setBlock(base.east(3).above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        h.setBlock(base.east(6).above(), Blocks.WATER);
        h.runAtTickTime(25, () -> {
            h.assertBlockPresent(Interstice.RIFTSTONE.get(), base);
            h.assertBlockPresent(Interstice.ABYSSAL_TURF.get(), base.east(3));
            h.assertBlockPresent(Interstice.RIFTSTONE.get(), base.east(6));
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void saplingGrowsWithoutSunAndCrownStaysAlive(GameTestHelper h) {
        darkRoom(h);
        BlockPos root = new BlockPos(8, 2, 8);
        h.setBlock(root.below(), Interstice.ABYSSAL_TURF.get());
        h.setBlock(root, Interstice.GLOOMCROWN_SAPLING.get());
        BlockPos absolute = h.absolutePos(root);
        h.runAtTickTime(5, () -> {
            h.assertTrue(h.getLevel().getMaxLocalRawBrightness(absolute) < 9, "Sapling must be tested in a dark room");
            RandomSource random = RandomSource.create(32);
            for (int attempt = 0; attempt < 100 && h.getBlockState(root).is(Interstice.GLOOMCROWN_SAPLING.get()); attempt++) {
                Interstice.GLOOMCROWN_SAPLING.get().randomTick(h.getBlockState(root), h.getLevel(), absolute, random);
            }
            h.assertBlockPresent(Interstice.GLOOMCROWN_LOG.get(), root);
        });
        h.runAtTickTime(80, () -> {
            int leaves = 0;
            for (BlockPos pos : BlockPos.betweenClosed(root.offset(-3, 0, -3), root.offset(3, 9, 3))) {
                var state = h.getBlockState(pos);
                if (state.is(Interstice.GLOOMCROWN_LEAVES.get())) {
                    leaves++;
                    h.assertTrue(state.getValue(LeavesBlock.DISTANCE) < 7, "Living leaves must remain connected to branches");
                }
            }
            h.assertTrue(leaves > 20, "Tree must retain a living crown");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blockedGrowthPreservesSaplingAndObstruction(GameTestHelper h) {
        BlockPos root = new BlockPos(8, 2, 8);
        h.setBlock(root.below(), Interstice.ABYSSAL_TURF.get());
        h.setBlock(root, Interstice.GLOOMCROWN_SAPLING.get());
        h.setBlock(root.above(3), Blocks.CHEST);
        boolean grown = GloomcrownTree.grow(h.getLevel(), h.absolutePos(root), RandomSource.create(12));
        h.assertTrue(!grown, "Blocked tree growth must fail atomically");
        h.assertBlockPresent(Interstice.GLOOMCROWN_SAPLING.get(), root);
        h.assertBlockPresent(Blocks.AIR, root.above());
        h.assertBlockPresent(Blocks.CHEST, root.above(3));
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void treeLootAndStrippingAreRegistered(GameTestHelper h) {
        var log = Interstice.GLOOMCROWN_LOG.get().defaultBlockState();
        h.assertTrue(log.is(BlockTags.LOGS), "Logs must support leaf distance updates");
        var stripping = log.getBlock().builtInRegistryHolder().getData(NeoForgeDataMaps.STRIPPABLES);
        h.assertTrue(stripping != null && stripping.strippedBlock() == Interstice.STRIPPED_GLOOMCROWN_LOG.get(), "Axes must strip Gloomcrown bark");
        var drops = Block.getDrops(Interstice.GLOOMCROWN_LEAVES.get().defaultBlockState(), h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)), null, null, new ItemStack(Items.SHEARS));
        h.assertTrue(drops.size() == 1 && drops.getFirst().is(Interstice.GLOOMCROWN_LEAVES_ITEM.get()), "Shears must recover the crown block");
        h.assertTrue(new ItemStack(Interstice.GLOOMCROWN_PLANKS_ITEM.get()).getBurnTime(null) == 300, "Wood planks must work as furnace fuel");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void treesGenerateWithinBothGeometryProfiles(GameTestHelper h) {
        for (GeometryProfile profile : new GeometryProfile[]{GeometryProfile.LEGACY, GeometryProfile.TALL}) {
            int trees = 0;
            for (int cx = 0; cx < 12; cx++) {
                var height = LevelHeightAccessor.create(profile.minY(), profile.height());
                var chunk = new ProtoChunk(new ChunkPos(cx, 0), UpgradeData.EMPTY, height, h.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null);
                int groundY = profile.minLand() + 12;
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) chunk.setBlockState(new BlockPos(cx * 16 + x, groundY, z), Interstice.ABYSSAL_TURF.get().defaultBlockState(), false);
                GloomcrownTree.generate(profile, chunk, 20261006L);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = groundY + 1; y < groundY + 12; y++) {
                    BlockPos pos = new BlockPos(cx * 16 + x, y, z);
                    var state = chunk.getBlockState(pos);
                    if (state.is(Interstice.GLOOMCROWN_LOG.get())) trees++;
                    if (!state.isAir()) h.assertTrue(IslandChunkGenerator.landAllowed(profile, pos.getX(), pos.getY(), pos.getZ()), "Trees must not intersect toxic seas");
                }
            }
            h.assertTrue(trees > 0, "Trees must generate in " + profile);
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void detachedCrownDecaysButPlacedLeavesRemain(GameTestHelper h) {
        BlockPos leaf = new BlockPos(3, 3, 3);
        var leaves = Interstice.GLOOMCROWN_LEAVES.get();
        h.setBlock(leaf, leaves.defaultBlockState().setValue(LeavesBlock.DISTANCE, 7));
        h.getBlockState(leaf).randomTick(h.getLevel(), h.absolutePos(leaf), RandomSource.create(1));
        h.assertBlockPresent(Blocks.AIR, leaf);
        h.setBlock(leaf, leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        h.getBlockState(leaf).randomTick(h.getLevel(), h.absolutePos(leaf), RandomSource.create(1));
        h.assertBlockPresent(leaves, leaf);
        h.succeed();
    }
}
