package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.TideSproutBlock;
import pro.erez.interstice.tide.TideSproutTracker;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class TideSproutTrackerGameTests {
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wakingSproutsNeverLoadsMissingChunks(GameTestHelper h) {
        var world = h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD);
        BlockPos center = new BlockPos(2000008, 100, 2000008);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            h.assertTrue(world.getChunkSource().getChunkNow((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz) == null,
                    "Search fixture must be unloaded");
        }
        TideSproutBlock.wakeNearbySprouts(world, center, 32);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            h.assertTrue(world.getChunkSource().getChunkNow((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz) == null,
                    "Waking sprouts must not create or load any surrounding chunk");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void indexedRootsWakeAndAreRemovedWhenBroken(GameTestHelper h) {
        BlockPos local = new BlockPos(2, 2, 2);
        BlockPos root = h.absolutePos(local);
        h.setBlock(local.below(), Interstice.ABYSSAL_TURF.get());
        h.setBlock(local, Interstice.TIDE_SPROUT.get());
        h.getLevel().getBlockTicks().clearArea(new BoundingBox(root));
        h.assertTrue(TideSproutTracker.wakeNearby(h.getLevel(), root, 0) == 1, "Placed root must be indexed");
        h.assertTrue(h.getLevel().getBlockTicks().hasScheduledTick(root, Interstice.TIDE_SPROUT.get()),
                "Indexed root must receive a real block tick");
        h.setBlock(local, Blocks.AIR);
        h.assertTrue(TideSproutTracker.wakeNearby(h.getLevel(), root, 0) == 0, "Broken root must leave the index");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void chunkLoadRebuildsRootIndex(GameTestHelper h) {
        BlockPos local = new BlockPos(2, 2, 2);
        BlockPos root = h.absolutePos(local);
        h.setBlock(local.below(), Interstice.ABYSSAL_TURF.get());
        h.setBlock(local, Interstice.TIDE_SPROUT.get());
        var chunk = h.getLevel().getChunkAt(root);
        TideSproutTracker.onChunkUnload(new ChunkEvent.Unload(chunk));
        h.assertTrue(TideSproutTracker.wakeNearby(h.getLevel(), root, 0) == 0, "Unloaded chunk must leave the index");
        TideSproutTracker.onChunkLoad(new ChunkEvent.Load(chunk, false));
        h.getLevel().getBlockTicks().clearArea(new BoundingBox(root));
        h.assertTrue(TideSproutTracker.wakeNearby(h.getLevel(), root, 0) == 1,
                "Chunk load must discover existing roots without placement callbacks");
        h.assertTrue(h.getLevel().getBlockTicks().hasScheduledTick(root, Interstice.TIDE_SPROUT.get()),
                "Restored root must receive a real block tick");
        h.setBlock(local, Blocks.AIR);
        h.succeed();
    }
}
