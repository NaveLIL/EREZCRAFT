package pro.erez.interstice;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;

/**
 * Dynamic tide plant ("Приливной побег").
 *
 * Behavior:
 * - During CALM / WARNING: remains a compact single-block bud (1 block high).
 * - During SURGE: dynamically grows 3 to 7 blocks high into the air, with leafy stems
 *   and a glowing, blooming bioluminescent flower at the top (light level 7).
 * - During EBB: smoothly retracts back down to 1 block high.
 * - Supports Bone Meal: right-clicking with bone meal triggers immediate growth.
 * - Section types:
 *   0 = root (when height=0 it renders as bud; when height>0 it renders as rooted base)
 *   1 = plain stem
 *   2 = leafy stem
 *   3 = top section (bloomed=false renders bud, bloomed=true renders glowing flower)
 */
public final class TideSproutBlock extends Block implements BonemealableBlock {

    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 3);
    public static final BooleanProperty BLOOMED = BooleanProperty.create("bloomed");
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 0, 6);

    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public TideSproutBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(0.0F)
                .sound(SoundType.WET_GRASS)
                .noCollission()
                .instabreak()
                .lightLevel(s -> s.getValue(BLOOMED) && s.getValue(SECTION) == 3 ? 7 : 0)
                .randomTicks());
        registerDefaultState(stateDefinition.any()
                .setValue(SECTION, 0)
                .setValue(BLOOMED, false)
                .setValue(HEIGHT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SECTION, BLOOMED, HEIGHT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(SECTION) == 0) {
            BlockState below = level.getBlockState(pos.below());
            return below.isFaceSturdy(level, pos.below(), Direction.UP);
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            level.scheduleTick(pos, this, 1);
        }
        return state;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getValue(SECTION) == 0) {
            // Schedule immediate tick upon placement so it reacts right away
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(SECTION) != 0) {
            if (!state.canSurvive(level, pos)) {
                level.destroyBlock(pos, true);
            }
            return;
        }
        processGrowth(state, level, pos, random);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(SECTION) != 0) return;
        processGrowth(state, level, pos, random);
    }

    private void processGrowth(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var server = level.getServer();
        if (server == null) return;
        TidePhase phase = TideManager.getSavedData(server).snapshot().phase();
        boolean surge = (phase == TidePhase.SURGE);
        int currentHeight = state.getValue(HEIGHT);
        int targetHeight = 2 + Math.abs((pos.getX() * 31 + pos.getZ() * 17) % 5);

        if (surge) {
            if (currentHeight < targetHeight) {
                grow(level, pos, state, currentHeight + 1, random);
                level.scheduleTick(pos, this, 8 + random.nextInt(10));
            } else {
                if (!state.getValue(BLOOMED)) {
                    level.setBlock(pos, state.setValue(BLOOMED, true), 3);
                    BlockPos topPos = pos.above(currentHeight);
                    BlockState top = level.getBlockState(topPos);
                    if (top.is(this)) {
                        level.setBlock(topPos, top.setValue(BLOOMED, true), 3);
                    }
                }
                // Keep checking periodically during surge so we know when it ends
                level.scheduleTick(pos, this, 20);
            }
        } else {
            if (currentHeight > 0) {
                shrink(level, pos, state, currentHeight);
                level.scheduleTick(pos, this, 8 + random.nextInt(10));
            } else {
                if (state.getValue(BLOOMED)) {
                    level.setBlock(pos, state.setValue(BLOOMED, false), 3);
                }
            }
        }
    }

    private void grow(ServerLevel level, BlockPos root, BlockState rootState, int newHeight, RandomSource random) {
        BlockPos topPos = root.above(newHeight);
        if (!level.getBlockState(topPos).isAir()) return;

        // Convert previous top to stem section
        if (newHeight > 1) {
            BlockPos prevTop = root.above(newHeight - 1);
            BlockState prevState = level.getBlockState(prevTop);
            if (prevState.is(this)) {
                int stemSection = (newHeight % 2 == 0) ? 2 : 1;
                level.setBlock(prevTop, prevState.setValue(SECTION, stemSection).setValue(BLOOMED, false), 3);
            }
        }

        // Place new top flower/bud
        BlockState newTop = defaultBlockState()
                .setValue(SECTION, 3)
                .setValue(BLOOMED, true)
                .setValue(HEIGHT, newHeight);
        level.setBlock(topPos, newTop, 3);

        // Update root
        level.setBlock(root, rootState.setValue(HEIGHT, newHeight).setValue(BLOOMED, true), 3);

        // Particle burst at new growth tip
        level.sendParticles(ParticleTypes.GLOW, topPos.getX() + 0.5, topPos.getY() + 0.5, topPos.getZ() + 0.5,
                4, 0.2, 0.2, 0.2, 0.02);
    }

    private void shrink(ServerLevel level, BlockPos root, BlockState rootState, int currentHeight) {
        BlockPos topPos = root.above(currentHeight);
        if (level.getBlockState(topPos).is(this)) {
            level.removeBlock(topPos, false);
        }
        int newHeight = currentHeight - 1;
        BlockState newRoot = rootState.setValue(HEIGHT, newHeight).setValue(BLOOMED, false);
        level.setBlock(root, newRoot, 3);

        if (newHeight > 0) {
            BlockPos newTop = root.above(newHeight);
            BlockState ns = level.getBlockState(newTop);
            if (ns.is(this)) {
                level.setBlock(newTop, ns.setValue(SECTION, 3).setValue(BLOOMED, false), 3);
            }
        }
    }

    // --- BonemealableBlock implementation ---

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos rootPos = findRoot(level, pos, state);
        if (rootPos == null) return false;
        BlockState rootState = level.getBlockState(rootPos);
        return rootState.getValue(HEIGHT) < 6;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos rootPos = findRoot(level, pos, state);
        if (rootPos == null) return;
        BlockState rootState = level.getBlockState(rootPos);
        int currentHeight = rootState.getValue(HEIGHT);
        if (currentHeight < 6) {
            grow(level, rootPos, rootState, currentHeight + 1, random);
        }
    }

    private BlockPos findRoot(LevelReader level, BlockPos pos, BlockState state) {
        if (state.getValue(SECTION) == 0) return pos;
        BlockPos cur = pos.below();
        while (cur.getY() >= level.getMinBuildHeight()) {
            BlockState bs = level.getBlockState(cur);
            if (bs.is(this) && bs.getValue(SECTION) == 0) return cur;
            if (!bs.is(this)) break;
            cur = cur.below();
        }
        return null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!newState.is(this) && !level.isClientSide) {
            BlockPos above = pos.above();
            while (level.getBlockState(above).is(this)) {
                level.removeBlock(above, false);
                above = above.above();
            }
            BlockPos below = pos.below();
            while (level.getBlockState(below).is(this)) {
                BlockState bs = level.getBlockState(below);
                if (bs.getValue(SECTION) == 0) {
                    level.setBlock(below, bs.setValue(HEIGHT, 0).setValue(BLOOMED, false), 3);
                    break;
                }
                below = below.below();
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Wakes up sprouts around the given position so they start growing or shrinking immediately. */
    public static void wakeNearbySprouts(ServerLevel level, BlockPos center, int radius) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - 6);
        int maxY = Math.min(level.getMaxBuildHeight(), center.getY() + 8);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = maxY; y >= minY; y--) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.is(Interstice.TIDE_SPROUT.get()) && s.getValue(SECTION) == 0) {
                        level.scheduleTick(p.immutable(), s.getBlock(), 1 + level.random.nextInt(6));
                        break;
                    }
                }
            }
        }
    }
}
