package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.TideSproutBlock;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class TerrainAndFloraGameTests {

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void materialAtReplacesStoneWithRiftstoneAndGrassWithTurf(GameTestHelper h) {
        GeometryProfile profile = GeometryProfile.LEGACY;
        int midLandY = 55; // well inside land bounds (41..70)

        BlockState stoneResult = IslandChunkGenerator.materialAt(profile, 0, midLandY, 0, Blocks.STONE.defaultBlockState());
        h.assertTrue(stoneResult.is(Interstice.RIFTSTONE.get()), "Stone in land zone must become Riftstone");

        BlockState dirtResult = IslandChunkGenerator.materialAt(profile, 0, midLandY, 0, Blocks.DIRT.defaultBlockState());
        h.assertTrue(dirtResult.is(Interstice.RIFTSTONE.get()), "Dirt in land zone must become Riftstone");

        BlockState grassResult = IslandChunkGenerator.materialAt(profile, 0, midLandY, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        h.assertTrue(grassResult.is(Interstice.ABYSSAL_TURF.get()), "Grass block on surface must become Abyssal Turf");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void oreGeneratesInsideRiftstone(GameTestHelper h) {
        LevelHeightAccessor height = LevelHeightAccessor.create(0, 128);
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, height,
                h.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null);

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 45; y <= 70; y++) {
                    mpos.set(x, y, z);
                    chunk.setBlockState(mpos, Interstice.RIFTSTONE.get().defaultBlockState(), false);
                }
            }
        }

        IslandChunkGenerator.generateOres(GeometryProfile.LEGACY, chunk, 123456789L);

        int oreCount = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 128; y++) {
                    mpos.set(x, y, z);
                    if (chunk.getBlockState(mpos).is(Interstice.RIFTSILVER_ORE.get())) {
                        oreCount++;
                    }
                }
            }
        }

        h.assertTrue(oreCount > 0, "Riftsilver ore must generate inside Riftstone rock");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tideSproutGeneratesOnAbyssalTurf(GameTestHelper h) {
        LevelHeightAccessor height = LevelHeightAccessor.create(0, 128);
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, height,
                h.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null);

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                mpos.set(x, 50, z);
                chunk.setBlockState(mpos, Interstice.ABYSSAL_TURF.get().defaultBlockState(), false);
            }
        }

        // Generate tide sprouts using seed
        IslandChunkGenerator.generateTideSprouts(GeometryProfile.LEGACY, chunk, 555666777L);

        int sproutCount = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                mpos.set(x, 51, z);
                if (chunk.getBlockState(mpos).is(Interstice.TIDE_SPROUT.get())) {
                    sproutCount++;
                }
            }
        }

        h.assertTrue(sproutCount > 0, "Tide sprouts must generate on top of Abyssal Turf surface");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tideSproutGrowthAndRetraction(GameTestHelper h) {
        BlockPos origin = new BlockPos(2, 2, 2);
        BlockPos below = origin.below();

        // Place abyssal turf floor
        h.setBlock(below, Interstice.ABYSSAL_TURF.get().defaultBlockState());

        // Place initial tide sprout root (compact, height=0)
        BlockState root = Interstice.TIDE_SPROUT.get().defaultBlockState()
                .setValue(TideSproutBlock.SECTION, 0)
                .setValue(TideSproutBlock.BLOOMED, false)
                .setValue(TideSproutBlock.HEIGHT, 0);
        h.setBlock(origin, root);

        // Force SURGE phase
        var data = TideManager.getSavedData(h.getLevel().getServer());
        data.setPhase(TidePhase.SURGE, 2000);

        // Trigger growth tick on root
        BlockPos worldOrigin = h.absolutePos(origin);
        h.getLevel().getBlockState(worldOrigin).randomTick(h.getLevel(), worldOrigin, RandomSource.create());

        // Verify root height increased to 1
        BlockState updatedRoot = h.getLevel().getBlockState(worldOrigin);
        h.assertTrue(updatedRoot.getValue(TideSproutBlock.HEIGHT) == 1,
                "Root height must advance to 1 during SURGE");
        h.assertTrue(updatedRoot.getValue(TideSproutBlock.BLOOMED),
                "Sprout must enter BLOOMED state during SURGE");

        // Verify top block was placed above root
        BlockPos worldTop = worldOrigin.above(1);
        BlockState topState = h.getLevel().getBlockState(worldTop);
        h.assertTrue(topState.is(Interstice.TIDE_SPROUT.get()),
                "A TideSprout top section must be created above root");
        h.assertTrue(topState.getValue(TideSproutBlock.SECTION) == 3,
                "Top section must have SECTION=3 (bud/flower)");

        // Now change phase to CALM and tick to verify retraction
        data.setPhase(TidePhase.CALM, 10000);
        h.getLevel().getBlockState(worldOrigin).randomTick(h.getLevel(), worldOrigin, RandomSource.create());

        // Verify root height shrank back to 0
        BlockState retractedRoot = h.getLevel().getBlockState(worldOrigin);
        h.assertTrue(retractedRoot.getValue(TideSproutBlock.HEIGHT) == 0,
                "Root height must shrink back to 0 in CALM");
        h.assertTrue(h.getLevel().getBlockState(worldTop).isAir(),
                "Top section block must be removed on retraction");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tideSproutBreaksWhenSupportIsRemoved(GameTestHelper h) {
        BlockPos root = new BlockPos(2, 2, 2);
        h.setBlock(root.below(), Interstice.ABYSSAL_TURF.get());
        h.setBlock(root, Interstice.TIDE_SPROUT.get().defaultBlockState()
                .setValue(TideSproutBlock.HEIGHT, 1));
        h.setBlock(root.above(), Interstice.TIDE_SPROUT.get().defaultBlockState()
                .setValue(TideSproutBlock.SECTION, 3));

        h.setBlock(root.below(), Blocks.AIR);
        h.runAtTickTime(5, () -> {
            h.assertTrue(h.getBlockState(root).isAir(), "Unsupported sprout root must break");
            h.assertTrue(h.getBlockState(root.above()).isAir(), "Unsupported sprout top must break with its root");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tideSproutCascadeBreaking(GameTestHelper h) {
        BlockPos origin = new BlockPos(2, 2, 2);
        h.setBlock(origin.below(), Interstice.ABYSSAL_TURF.get().defaultBlockState());

        // Setup 2-block plant manually
        BlockState rootState = Interstice.TIDE_SPROUT.get().defaultBlockState()
                .setValue(TideSproutBlock.SECTION, 0)
                .setValue(TideSproutBlock.HEIGHT, 1)
                .setValue(TideSproutBlock.BLOOMED, true);
        BlockState topState = Interstice.TIDE_SPROUT.get().defaultBlockState()
                .setValue(TideSproutBlock.SECTION, 3)
                .setValue(TideSproutBlock.HEIGHT, 1)
                .setValue(TideSproutBlock.BLOOMED, true);

        h.setBlock(origin, rootState);
        h.setBlock(origin.above(), topState);

        // Break root block
        BlockPos worldOrigin = h.absolutePos(origin);
        h.getLevel().destroyBlock(worldOrigin, false);

        // Verify top block is also destroyed
        BlockPos worldTop = worldOrigin.above();
        h.assertTrue(h.getLevel().getBlockState(worldTop).isAir(),
                "Breaking root must cascade-destroy upper stem/flower blocks");

        h.succeed();
    }
}
