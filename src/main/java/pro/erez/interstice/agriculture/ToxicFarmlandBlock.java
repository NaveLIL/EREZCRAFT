package pro.erez.interstice.agriculture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.common.util.TriState;

/** Independent of FarmBlock: terrestrial crops cannot mistake this chemical bed for vanilla farmland. */
public final class ToxicFarmlandBlock extends Block {
    public static final IntegerProperty MOISTURE=BlockStateProperties.MOISTURE;
    public ToxicFarmlandBlock(){this(BlockBehaviour.Properties.ofFullCopy(Blocks.FARMLAND).randomTicks());}
    private ToxicFarmlandBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(MOISTURE,0));}
    @Override public MapCodec<ToxicFarmlandBlock> codec(){return simpleCodec(ToxicFarmlandBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(MOISTURE);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return box(0,0,0,16,15,16);}
    @Override public TriState canSustainPlant(BlockState soil,BlockGetter level,BlockPos pos,Direction face,BlockState plant){return face==Direction.UP&&plant.getBlock() instanceof NativeCropBlock?TriState.TRUE:TriState.FALSE;}
    @Override public boolean isFertile(BlockState soil,BlockGetter level,BlockPos pos){return soil.getValue(MOISTURE)>0;}
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        var hydration=FarmHydration.scan(level,pos);if(hydration==FarmHydration.State.UNKNOWN)return;
        int old=state.getValue(MOISTURE),next=hydration==FarmHydration.State.WET?7:Math.max(0,old-1);
        if(next!=old)level.setBlock(pos,state.setValue(MOISTURE,next),2);
    }
}
