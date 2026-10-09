package pro.erez.interstice.food;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pro.erez.interstice.worldgen.GardenMaterials;

/** A living crown pod. Food comes from close hand harvesting, never from breaking/felling/explosions. */
public final class CrownFruitBlock extends Block {
    public static final IntegerProperty AGE=IntegerProperty.create("age",0,3);
    private static final VoxelShape SHAPE=box(3,0,3,13,13,13);
    public CrownFruitBlock(){super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).noCollission().strength(.3F).sound(SoundType.FUNGUS).randomTicks().lightLevel(s->s.getValue(AGE)==3?14:1));registerDefaultState(stateDefinition.any().setValue(AGE,3));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(AGE);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        var support=level.getBlockState(pos.below());return (support.is(GardenMaterials.PALEHEART_LEAVES.get())||support.is(GardenMaterials.CROWN_LEAVES.get()))&&support.getValue(LeavesBlock.DISTANCE)<7;
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){
        if(direction==Direction.DOWN&&!canSurvive(state,level,pos))return Blocks.AIR.defaultBlockState();
        return state;
    }
    @Override public void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        if(!canSurvive(state,level,pos)){level.removeBlock(pos,false);return;}
        if(state.getValue(AGE)<3&&random.nextInt(5)==0)level.setBlock(pos,state.setValue(AGE,state.getValue(AGE)+1),3);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.canInteractWithBlock(pos,1.0)||!canSurvive(state,level,pos))return InteractionResult.FAIL;
        if(state.getValue(AGE)<3){if(!level.isClientSide)player.displayClientMessage(Component.translatable("message.interstice.tide_heart.unripe"),true);return InteractionResult.SUCCESS;}
        if(!level.isClientSide){
            level.setBlock(pos,state.setValue(AGE,0),3);
            var fruit=new ItemStack(TideHeart.FRUIT.get());if(!player.addItem(fruit))player.drop(fruit,false);
            pro.erez.interstice.fauna.RealmFauna.notifyFruitHarvest(player,pos);
        }return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
