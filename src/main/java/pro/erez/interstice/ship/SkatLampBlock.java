package pro.erez.interstice.ship;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.Block;
/** Only lamps and the two combined lamp/boarding cells carry this additional state. */
public final class SkatLampBlock extends SkatBlock {
    public static final BooleanProperty LIT=BooleanProperty.create("lit");
    public SkatLampBlock(){registerDefaultState(defaultBlockState().setValue(LIT,true));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(LIT);}
    @Override protected int getLightBlock(BlockState s,net.minecraft.world.level.BlockGetter w,net.minecraft.core.BlockPos p){return 0;}
}
