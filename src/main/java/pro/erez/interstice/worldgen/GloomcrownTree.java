package pro.erez.interstice.worldgen;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;

/** A forked trunk and a low, broad crown. Placement is checked in full before any block changes. */
public final class GloomcrownTree {
    private GloomcrownTree() {}

    public static Map<BlockPos, BlockState> plan(BlockPos root, int height, boolean alongX) {
        return plan(root, height, alongX, 0);
    }
    public static Map<BlockPos, BlockState> plan(BlockPos root, int height, boolean alongX, int shape) {
        if (height < 5 || height > 7 || shape < 0 || shape > 2) throw new IllegalArgumentException("Invalid Gloomcrown form");
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        BlockState log = Interstice.GLOOMCROWN_LOG.get().defaultBlockState();
        for (int y = 0; y < height - (shape == 1 ? 1 : 0); y++) result.put(root.above(y), log);
        Direction arm = alongX ? Direction.EAST : Direction.SOUTH;
        BlockState branch = log.setValue(RotatedPillarBlock.AXIS, arm.getAxis());
        for (int step = 1; step <= 2; step++) {
            result.put(root.above(height - 2).relative(arm.getOpposite(), step), branch);
            result.put(root.above(height - 1).relative(arm, step), branch);
        }
        BlockState leaves = Interstice.GLOOMCROWN_LEAVES.get().defaultBlockState();
        if (shape == 1) {
            // Connected woody forks hold two separate raised crown lobes.
            for (int sign : new int[]{-1, 1}) {
                for (int step = 1; step <= 2; step++) result.put(root.above(height - 2).relative(arm, sign * step), branch);
                BlockPos fork = root.above(height - 1).relative(arm, sign * 2);
                result.put(fork, log); result.put(fork.above(), log);
                crown(result, fork.above(), leaves, 2, false);
            }
        } else {
            crown(result, root.above(height - 1), leaves, shape == 0 ? 3 : 2, shape == 0);
            for (int sign : new int[]{-1, 1}) {
                for (int dy = height - (shape == 2 ? 4 : 3); dy <= height - 2; dy++) {
                    result.putIfAbsent(root.offset(alongX ? sign * 2 : 1, dy, alongX ? 1 : sign * 2), leaves);
                }
            }
        }
        // Correct distances from actual branches prevent the living crown from decaying.
        Map<BlockPos, Integer> distances = new LinkedHashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        result.forEach((pos, state) -> { if (state.is(Interstice.GLOOMCROWN_LOG.get())) { distances.put(pos, 0); queue.add(pos); } });
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            int distance = distances.get(current) + 1;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                if (distance <= 6 && result.containsKey(neighbor) && !distances.containsKey(neighbor)) {
                    distances.put(neighbor, distance); queue.add(neighbor);
                }
            }
        }
        result.replaceAll((pos, state) -> state.is(Interstice.GLOOMCROWN_LEAVES.get())
                ? state.setValue(LeavesBlock.DISTANCE, distances.getOrDefault(pos, 7)) : state);
        return result;
    }

    private static void crown(Map<BlockPos, BlockState> result, BlockPos center, BlockState leaves, int width, boolean umbrella) {
        for (int dy = -1; dy <= 1; dy++) {
            int radius = dy == 1 ? width - 1 : width;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) + Math.abs(z) > radius + 1) continue;
                if (umbrella && dy == -1 && Math.abs(x) + Math.abs(z) > 3) continue;
                result.putIfAbsent(center.offset(x, dy, z), leaves);
            }
        }
    }

    private static boolean fits(Map<BlockPos, BlockState> tree, Function<BlockPos, BlockState> blocks,
                                Predicate<BlockPos> allowed, BlockPos root) {
        if (!blocks.apply(root.below()).is(Interstice.ABYSSAL_TURF.get())) return false;
        for (BlockPos pos : tree.keySet()) {
            if (!allowed.test(pos)) return false;
            BlockState existing = blocks.apply(pos);
            if (!existing.isAir() && !(pos.equals(root) && existing.is(Interstice.GLOOMCROWN_SAPLING.get()))) return false;
        }
        return true;
    }

    public static boolean grow(ServerLevel level, BlockPos root, RandomSource random) {
        Map<BlockPos, BlockState> tree = plan(root, 5 + random.nextInt(3), random.nextBoolean(), random.nextInt(3));
        var generator = level.getChunkSource().getGenerator();
        if (!fits(tree, level::getBlockState, pos -> level.hasChunkAt(pos)
                && !level.isOutsideBuildHeight(pos)
                && (!(generator instanceof IslandChunkGenerator islands)
                    || IslandChunkGenerator.landAllowed(islands.geometry(), pos.getX(), pos.getY(), pos.getZ())), root)) return false;
        tree.forEach((pos, state) -> level.setBlock(pos, state, 3));
        return true;
    }

    public static void generate(GeometryProfile profile, ChunkAccess chunk, long seed) {
        RandomSource random = RandomSource.create(seed ^ chunk.getPos().toLong() ^ 0x67C0A11L);
        if (random.nextInt(3) != 0) return;
        int x = chunk.getPos().getMinBlockX() + 5 + random.nextInt(6);
        int z = chunk.getPos().getMinBlockZ() + 5 + random.nextInt(6);
        for (int y = profile.maxLand(); y >= profile.minLand(); y--) {
            BlockPos ground = new BlockPos(x, y, z);
            BlockState existing = chunk.getBlockState(ground);
            if (existing.isAir()) continue;
            if (existing.is(Interstice.ABYSSAL_TURF.get())) {
                BlockPos root = ground.above();
                Map<BlockPos, BlockState> tree = plan(root, 5 + random.nextInt(3), random.nextBoolean(), random.nextInt(3));
                if (fits(tree, chunk::getBlockState,
                        pos -> pos.getX() >= chunk.getPos().getMinBlockX() && pos.getX() <= chunk.getPos().getMaxBlockX()
                            && pos.getZ() >= chunk.getPos().getMinBlockZ() && pos.getZ() <= chunk.getPos().getMaxBlockZ()
                            && IslandChunkGenerator.landAllowed(profile, pos.getX(), pos.getY(), pos.getZ()), root)) {
                    tree.forEach((pos, state) -> chunk.setBlockState(pos, state, false));
                }
            }
            break;
        }
    }
}
