package pro.erez.interstice;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import pro.erez.interstice.geometry.GeometryProfiles;

/** Ceiling-attached fluid. Vanilla water/lava flow is never modified. */
public abstract class LightFluid extends BaseFlowingFluid {
    protected LightFluid(Properties properties) { super(properties); }

    private boolean openFace(BlockGetter world, BlockPos from, BlockPos to, Direction face) {
        return !Shapes.mergedFaceOccludes(world.getBlockState(from).getCollisionShape(world, from),
                world.getBlockState(to).getCollisionShape(world, to), face);
    }
    private boolean canOccupy(Level world, BlockPos pos) {
        if (world.isOutsideBuildHeight(pos) || !world.hasChunkAt(pos)) return false;
        BlockState block = world.getBlockState(pos);
        if (block.getBlock() instanceof OceanLiquidBlock) return false;
        FluidState fluid = block.getFluidState();
        if (isSame(fluid.getType())) return !fluid.isSource();
        // Do not displace other liquids or put upside-down liquid into waterlogged blocks.
        return fluid.isEmpty() && (block.isAir() || block.canBeReplaced())
                && block.getCollisionShape(world, pos).isEmpty();
    }
    private boolean mobileSupply(BlockGetter world, BlockPos pos) {
        BlockState block = world.getBlockState(pos);
        return !(block.getBlock() instanceof OceanLiquidBlock) && isSame(block.getFluidState().getType());
    }
    /** The held ocean absorbs incoming flow; it is neither a wall nor a renewable supply. */
    private boolean joinsOcean(BlockGetter world, BlockPos pos) {
        for (Direction face : Direction.values()) {
            BlockPos neighbor = pos.relative(face);
            BlockState block = world.getBlockState(neighbor);
            if (block.getBlock() instanceof OceanLiquidBlock && isSame(block.getFluidState().getType())
                    && openFace(world, pos, neighbor, face)) return true;
        }
        return false;
    }
    @Override
    protected FluidState getNewLiquid(Level world, BlockPos pos, BlockState block) {
        BlockPos below = pos.below();
        if (mobileSupply(world, below) && openFace(world, below, pos, Direction.UP)) {
            return getFlowing(8, true);
        }
        int level = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(side);
            FluidState fluid = world.getFluidState(neighbor);
            if (mobileSupply(world, neighbor) && !joinsOcean(world, neighbor)
                    && openFace(world, neighbor, pos, side.getOpposite())) {
                level = Math.max(level, fluid.getAmount());
            }
        }
        return level <= 1 ? Fluids.EMPTY.defaultFluidState() : getFlowing(level - 1, false);
    }
    @Override
    public void tick(Level world, BlockPos pos, FluidState state) {
        if (world.getBlockState(pos).getBlock() instanceof OceanLiquidBlock) return;
        super.tick(world, pos, state);
    }
    @Override
    protected void spread(Level world, BlockPos pos, FluidState state) {
        if (world.getBlockState(pos).getBlock() instanceof OceanLiquidBlock) return;
        if (state.isEmpty()) return;
        if (joinsOcean(world, pos)) return;
        BlockPos above = pos.above();
        boolean canRise = canOccupy(world, above) && openFace(world, pos, above, Direction.UP);
        if (canRise) {
            place(world, above, getFlowing(8, true));
        }
        // A source also spreads sideways; a rising stream does so when its ceiling blocks ascent.
        if (state.isSource() || !canRise) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos target = pos.relative(side);
                if (canOccupy(world, target) && openFace(world, pos, target, side)) {
                    FluidState next = getNewLiquid(world, target, world.getBlockState(target));
                    if (!next.isEmpty()) place(world, target, next);
                }
            }
        }
    }
    private void place(Level world, BlockPos pos, FluidState fluid) {
        BlockState old = world.getBlockState(pos);
        if (old.getFluidState().equals(fluid)) return;
        if (!old.isAir() && old.getFluidState().isEmpty()) beforeDestroyingBlock(world, pos, old);
        world.setBlock(pos, fluid.createLegacyBlock(), 3);
        world.scheduleTick(pos, fluid.getType(), getTickDelay(world));
    }
    @Override
    public float getHeight(FluidState state, BlockGetter world, BlockPos pos) {
        if (world.getBlockState(pos).getBlock() instanceof OceanLiquidBlock) return SeaSurface.thickness(GeometryProfiles.get(world),pos,world.getBlockState(pos).getValue(OceanLiquidBlock.CHAOTIC));
        // Thickness measured DOWN from the top of the voxel.
        return isSame(world.getFluidState(pos.below()).getType()) ? 1.0F : getOwnHeight(state);
    }
    @Override
    public VoxelShape getShape(FluidState state, BlockGetter world, BlockPos pos) {
        if (world.getBlockState(pos).getBlock() instanceof OceanLiquidBlock) return SeaSurface.pickingShape(GeometryProfiles.get(world),pos,world.getBlockState(pos).getValue(OceanLiquidBlock.CHAOTIC));
        return Shapes.box(0, 1.0 - getHeight(state, world, pos), 0, 1, 1, 1);
    }
    @Override
    public Vec3 getFlow(BlockGetter world, BlockPos pos, FluidState state) {
        double x = 0, z = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            FluidState other = world.getFluidState(pos.relative(side));
            if (other.isEmpty() || isSame(other.getType())) {
                double difference = state.getAmount() - (other.isEmpty() ? 0 : other.getAmount());
                x += side.getStepX() * difference;
                z += side.getStepZ() * difference;
            }
        }
        Vec3 horizontal = new Vec3(x, 0, z).normalize();
        return state.getValue(FALLING) ? horizontal.add(0, 6, 0).normalize() : horizontal;
    }
    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter world, BlockPos pos, Fluid fluid, Direction direction) {
        return false;
    }
    public static final class Source extends LightFluid {
        public Source(Properties properties) { super(properties); }
        @Override public boolean isSource(FluidState state) { return true; }
        @Override public int getAmount(FluidState state) { return 8; }
    }
    public static final class Flowing extends LightFluid {
        public Flowing(Properties properties) {
            super(properties);
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }
        @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder); builder.add(LEVEL);
        }
        @Override public boolean isSource(FluidState state) { return false; }
        @Override public int getAmount(FluidState state) { return state.getValue(LEVEL); }
    }
}
