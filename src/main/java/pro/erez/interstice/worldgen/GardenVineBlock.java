package pro.erez.interstice.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pro.erez.interstice.Interstice;

/** Hanging, climbable chains. The original 16x32 artwork is shown across two 16px-high blocks. */
public final class GardenVineBlock extends Block implements BonemealableBlock {
    public static final IntegerProperty SECTION=IntegerProperty.create("section",0,3); // body upper/lower, tip upper/lower
    public GardenVineBlock(){super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollission().instabreak().sound(SoundType.WEEPING_VINES).randomTicks());registerDefaultState(stateDefinition.any().setValue(SECTION,2));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(SECTION);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return box(4,0,4,12,16,12);}
    public static boolean anchor(BlockState s){return s.is(GardenMaterials.PALEHEART_LOG.get())||s.is(GardenMaterials.PALEHEART_LEAVES.get())||s.is(Interstice.GLOOMCROWN_LOG.get())||s.is(Interstice.GLOOMCROWN_LEAVES.get());}
    @Override public boolean canSurvive(BlockState s,LevelReader l,BlockPos p){var above=l.getBlockState(p.above());return anchor(above)||above.is(this);}
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){
        if(direction==Direction.UP&&!canSurvive(state,level,pos))return Blocks.AIR.defaultBlockState();
        if(direction==Direction.UP||direction==Direction.DOWN){
            var above=level.getBlockState(pos.above());int part=above.is(this)?1-(above.getValue(SECTION)&1):0;
            var below=level.getBlockState(pos.below());boolean cap=!below.is(this)||(part==0&&below.getValue(SECTION)==3);
            return state.setValue(SECTION,part+(cap?2:0));
        }return state;
    }
    private boolean canExtend(ServerLevel l,BlockPos p){
        if(!l.getBlockState(p.below()).isAir()||!l.hasChunkAt(p.below())||l.isOutsideBuildHeight(p.below()))return false;
        int length=1;var cursor=p.above();while(l.getBlockState(cursor).is(this)&&length<9){length++;cursor=cursor.above();}
        if(length>=8)return false;
        var g=l.getChunkSource().getGenerator();var next=p.below();
        return !(g instanceof IslandChunkGenerator islands)||IslandChunkGenerator.landAllowed(islands.geometry(),next.getX(),next.getY(),next.getZ());
    }
    private void extend(ServerLevel l,BlockPos p){if(canExtend(l,p))l.setBlock(p.below(),defaultBlockState().setValue(SECTION,3-(l.getBlockState(p).getValue(SECTION)&1)),3);}
    @Override public void randomTick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){if(s.getValue(SECTION)>=2&&!l.getBlockState(p.below()).is(this)&&r.nextInt(10)==0)extend(l,p);}
    @Override public boolean isValidBonemealTarget(LevelReader l,BlockPos p,BlockState s){return s.getValue(SECTION)>=2&&l.getBlockState(p.below()).isAir();}
    @Override public boolean isBonemealSuccess(Level l,RandomSource r,BlockPos p,BlockState s){return true;}
    @Override public void performBonemeal(ServerLevel l,RandomSource r,BlockPos p,BlockState s){extend(l,p);}
}
