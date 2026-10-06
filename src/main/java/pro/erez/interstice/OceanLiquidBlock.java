package pro.erez.interstice;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Generated ocean is held by the dimension's field; poured liquid retains ordinary upward flow. */
public final class OceanLiquidBlock extends LiquidBlock {
    public static final BooleanProperty CHAOTIC = BooleanProperty.create("chaotic");
    public OceanLiquidBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL,0).setValue(CHAOTIC,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder);builder.add(CHAOTIC);
    }
    @Override protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState old, boolean moved) {}
    @Override protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos from, boolean moved) {}
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor world, BlockPos pos, BlockPos other) {
        return state;
    }
}
