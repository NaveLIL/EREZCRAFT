package pro.erez.interstice.minerals;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Deposited mineral crust: familiar layered geometry, stable beside a light source. */
public final class MineralFrostBlock extends SnowLayerBlock {
    public MineralFrostBlock(){this(BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW));}
    private MineralFrostBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override public MapCodec<SnowLayerBlock> codec(){return simpleCodec(p->new MineralFrostBlock(p));}
    @Override public void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){}
}
