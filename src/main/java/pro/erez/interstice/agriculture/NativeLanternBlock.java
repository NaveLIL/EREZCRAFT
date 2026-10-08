package pro.erez.interstice.agriculture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

/** Selection/collision match the actual original-bud case, including its bounded hanging support. */
public final class NativeLanternBlock extends LanternBlock {
    public NativeLanternBlock(){this(BlockBehaviour.Properties.ofFullCopy(Blocks.LANTERN).lightLevel(s->14));}
    private NativeLanternBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override public MapCodec<LanternBlock> codec(){return simpleCodec(NativeLanternBlock::new);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return state.getValue(HANGING)?Shapes.or(box(4,0,4,12,12,12),box(7,12,7,9,16,9)):box(4,0,4,12,16,12);
    }
}
