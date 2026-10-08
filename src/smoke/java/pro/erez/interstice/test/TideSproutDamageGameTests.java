package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.TideSproutBlock;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class TideSproutDamageGameTests {
    private static void plant(GameTestHelper h, BlockPos root, int height) {
        h.setBlock(root.below(), Interstice.ABYSSAL_TURF.get());
        h.setBlock(root, Interstice.TIDE_SPROUT.get().defaultBlockState().setValue(TideSproutBlock.HEIGHT, height).setValue(TideSproutBlock.BLOOMED, true));
        for (int y = 1; y <= height; y++) h.setBlock(root.above(y), Interstice.TIDE_SPROUT.get().defaultBlockState()
                .setValue(TideSproutBlock.SECTION, y == height ? 3 : 2).setValue(TideSproutBlock.BLOOMED, true).setValue(TideSproutBlock.HEIGHT, y));
    }
    private static void assertCollapsed(GameTestHelper h, BlockPos root) {
        h.assertTrue(h.getBlockState(root).getValue(TideSproutBlock.HEIGHT) == 0, "Damaged plant must reset root height");
        for (int y = 1; y <= 6; y++) h.assertTrue(h.getBlockState(root.above(y)).isAir(), "No detached stem may remain at height " + y);
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void breakingMiddleClearsLowerStemAndRegrows(GameTestHelper h) {
        BlockPos root = new BlockPos(3, 2, 3);
        plant(h, root, 4);
        TideManager.getSavedData(h.getLevel().getServer()).setPhase(TidePhase.SURGE, 2000);
        h.getLevel().destroyBlock(h.absolutePos(root.above(2)), false);
        assertCollapsed(h, root);
        h.getBlockState(root).randomTick(h.getLevel(), h.absolutePos(root), RandomSource.create(1));
        h.assertTrue(h.getBlockState(root).getValue(TideSproutBlock.HEIGHT) == 1, "Collapsed root must grow again during the same surge");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void breakingFlowerClearsEntireStem(GameTestHelper h) {
        BlockPos root = new BlockPos(3, 2, 3);
        plant(h, root, 4);
        h.getLevel().destroyBlock(h.absolutePos(root.above(4)), false);
        assertCollapsed(h, root);
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void oldOrphanStemRepairsBeforeGrowth(GameTestHelper h) {
        BlockPos root = new BlockPos(3, 2, 3);
        plant(h, root, 3);
        h.setBlock(root, h.getBlockState(root).setValue(TideSproutBlock.HEIGHT, 0).setValue(TideSproutBlock.BLOOMED, false));
        TideManager.getSavedData(h.getLevel().getServer()).setPhase(TidePhase.SURGE, 2000);
        h.getBlockState(root).randomTick(h.getLevel(), h.absolutePos(root), RandomSource.create(1));
        h.assertTrue(h.getBlockState(root).getValue(TideSproutBlock.HEIGHT) == 1, "Old orphan stems must not block new growth");
        h.assertTrue(h.getBlockState(root.above()).getValue(TideSproutBlock.SECTION) == 3, "The new section must be a flower tip");
        h.assertTrue(h.getBlockState(root.above(2)).isAir() && h.getBlockState(root.above(3)).isAir(), "Old upper fragments must be gone");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void naturalRetractionRemovesOnlyOneSection(GameTestHelper h) {
        BlockPos root = new BlockPos(3, 2, 3);
        plant(h, root, 4);
        TideManager.getSavedData(h.getLevel().getServer()).setPhase(TidePhase.CALM, 2000);
        h.getBlockState(root).randomTick(h.getLevel(), h.absolutePos(root), RandomSource.create(1));
        h.assertTrue(h.getBlockState(root).getValue(TideSproutBlock.HEIGHT) == 3, "Natural retraction must remain gradual");
        h.assertTrue(h.getBlockState(root.above(2)).is(Interstice.TIDE_SPROUT.get()), "Natural retraction must preserve the remaining stem");
        h.assertTrue(h.getBlockState(root.above(3)).getValue(TideSproutBlock.SECTION) == 3, "The remaining tip must become a closed bud");
        h.assertTrue(h.getBlockState(root.above(4)).isAir(), "The old flower section must be gone");
        h.succeed();
    }
}
