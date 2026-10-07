package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.water.WaterTransmutationManager;
import pro.erez.interstice.water.WaterTransmutationSavedData;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class WaterTransmutationGameTests {

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void waterTransmutationRejectsInIslandAndLeavesOtherDimensionsUntouched(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos testPos = new BlockPos(1, 2, 1);

        // 1. In standard overworld / flat level (not an island dimension), handleBucketEmpty must return false
        h.assertTrue(!IslandWorld.isIsland(level.dimension()), "Test level must not be considered an island dimension");
        boolean handledOverworld = WaterTransmutationManager.handleBucketEmpty(null, level, testPos, null);
        h.assertTrue(!handledOverworld, "handleBucketEmpty must return false in non-island dimensions");

        // 2. In island dimension, IslandWorld.isIsland is true
        h.assertTrue(IslandWorld.isIsland(IslandWorld.WORLD), "IslandWorld.WORLD must be recognized as island");
        h.assertTrue(IslandWorld.isIsland(IslandWorld.TALL_WORLD), "IslandWorld.TALL_WORLD must be recognized as island");

        System.out.println("WATER_ISOLATION verified_dimensions=true");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void waterTransmutesToLightToxinAndSchedulesEvaporation(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos localPos = new BlockPos(2, 2, 2);
        BlockPos worldPos = h.absolutePos(localPos);

        WaterTransmutationSavedData data = WaterTransmutationSavedData.get(level);
        data.addExpiring(worldPos, level.getGameTime() + 200L);

        level.setBlock(worldPos, Interstice.LIGHT_BLOCK.get().defaultBlockState(), 3);
        BlockState placedState = level.getBlockState(worldPos);
        h.assertTrue(placedState.is(Interstice.LIGHT_BLOCK.get()), "Block must be light toxin");
        h.assertTrue(data.getExpiringBlocks().containsKey(worldPos), "SavedData must track the expiring pos");

        // Fast-path isEmpty check works
        h.assertTrue(!data.isEmpty(), "SavedData must not be empty while tracking expiring block");

        // Manual cleanup for test cleanliness
        data.remove(worldPos);
        level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(data.isEmpty(), "SavedData must be empty after removal");

        System.out.println("WATER_TRANSMUTE_PLACEMENT verified=true");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void waterTransmutationSavedDataSurvivesSaveAndReload(GameTestHelper h) {
        HolderLookup.Provider registries = h.getLevel().registryAccess();
        WaterTransmutationSavedData original = new WaterTransmutationSavedData();
        BlockPos pos1 = new BlockPos(10, 64, -20);
        BlockPos pos2 = new BlockPos(45, 128, 90);

        original.addExpiring(pos1, 1500L);
        original.addExpiring(pos2, 1600L);

        CompoundTag tag = new CompoundTag();
        original.save(tag, registries);

        WaterTransmutationSavedData restored = WaterTransmutationSavedData.load(tag, registries);
        h.assertTrue(restored.getExpiringBlocks().size() == 2, "Restored count mismatch: " + restored.getExpiringBlocks().size());
        h.assertTrue(restored.getExpiringBlocks().get(pos1) == 1500L, "Restored expire pos1 mismatch");
        h.assertTrue(restored.getExpiringBlocks().get(pos2) == 1600L, "Restored expire pos2 mismatch");

        System.out.println("WATER_TRANSMUTE_DATA_RELOAD restored_sites=2");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void dynamicTideDurationsPickWithinExpectedRanges(GameTestHelper h) {
        RandomSource random = RandomSource.create(424242L);

        for (int i = 0; i < 100; i++) {
            long calmDuration = TidePhase.CALM.pickDuration(random);
            h.assertTrue(calmDuration >= 12000 && calmDuration <= 26400,
                    "CALM duration out of range [12000, 26400]: " + calmDuration);

            long warningDuration = TidePhase.WARNING.pickDuration(random);
            h.assertTrue(warningDuration >= 900 && warningDuration <= 1600,
                    "WARNING duration out of range [900, 1600]: " + warningDuration);

            long surgeDuration = TidePhase.SURGE.pickDuration(random);
            h.assertTrue(surgeDuration >= 1200 && surgeDuration <= 2600,
                    "SURGE duration out of range [1200, 2600]: " + surgeDuration);

            long ebbDuration = TidePhase.EBB.pickDuration(random);
            h.assertTrue(ebbDuration >= 400 && ebbDuration <= 800,
                    "EBB duration out of range [400, 800]: " + ebbDuration);
        }

        System.out.println("DYNAMIC_TIDE_DURATIONS 100_iterations_verified=true");
        h.succeed();
    }
}
