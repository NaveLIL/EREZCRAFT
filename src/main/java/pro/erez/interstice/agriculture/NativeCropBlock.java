package pro.erez.interstice.agriculture;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.common.CommonHooks;

/** Whole ready sprites: five grain stages or four root stages. Chemical nutrition replaces sunlight. */
public final class NativeCropBlock extends CropBlock {
    public static final IntegerProperty AGE=IntegerProperty.create("age",0,4);
    private final boolean grain;
    public NativeCropBlock(boolean grain){this(grain,BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT));}
    private NativeCropBlock(boolean grain,BlockBehaviour.Properties properties){super(properties);this.grain=grain;}
    private static final MapCodec<NativeCropBlock> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            Codec.BOOL.fieldOf("grain").forGetter(b->b.grain),propertiesCodec()).apply(i,NativeCropBlock::new));
    @Override public MapCodec<NativeCropBlock> codec(){return CODEC;}
    @Override protected IntegerProperty getAgeProperty(){return AGE;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(AGE);}
    @Override public int getMaxAge(){return grain?4:3;}
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){
        int[] heights=grain?new int[]{2,6,11,14,15}:new int[]{3,6,9,11,11};return box(0,0,0,16,heights[getAge(state)],16);
    }
    @Override protected boolean mayPlaceOn(BlockState soil,BlockGetter world,BlockPos pos){return soil.is(RealmAgriculture.FARMLAND.get());}
    @Override protected boolean canSurvive(BlockState state,LevelReader world,BlockPos pos){return world.getBlockState(pos.below()).is(RealmAgriculture.FARMLAND.get());}
    @Override protected ItemLike getBaseSeedId(){return grain?RealmAgriculture.GRAIN.get():RealmAgriculture.ROOT.get();}
    public boolean canGrow(LevelReader level,BlockPos pos,BlockState state){return !isMaxAge(state)&&canSurvive(state,level,pos)&&FarmHydration.scan(level,pos.below())==FarmHydration.State.WET;}
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        if(canGrow(level,pos,state)&&CommonHooks.canCropGrow(level,pos,state,random.nextInt(5)==0))advance(level,pos,state);
    }
    public void advance(ServerLevel level,BlockPos pos,BlockState state){level.setBlock(pos,getStateForAge(Math.min(getMaxAge(),getAge(state)+1)),2);CommonHooks.fireCropGrowPost(level,pos,state);}
    @Override public boolean isValidBonemealTarget(LevelReader level,BlockPos pos,BlockState state){return false;}
    @Override public boolean isBonemealSuccess(Level level,RandomSource random,BlockPos pos,BlockState state){return false;}
    @Override public void performBonemeal(ServerLevel level,RandomSource random,BlockPos pos,BlockState state){}
}
