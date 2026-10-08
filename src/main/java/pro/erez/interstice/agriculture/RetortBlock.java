package pro.erez.interstice.agriculture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.BlockGetter;

public final class RetortBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    private static final VoxelShape SHAPE=Shapes.or(Block.box(1,0,1,15,12,15),Block.box(3,12,3,13,15,13),Block.box(5,15,5,11,16,11),Block.box(3,0,.9,13,3,1.9),Block.box(5,5,.9,11,9,1.1));
    public RetortBlock(){this(BlockBehaviour.Properties.ofFullCopy(Blocks.FURNACE).noOcclusion().strength(4,8).lightLevel(s->s.getValue(LIT)?4:0));}
    private RetortBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(LIT,false));}
    @Override protected MapCodec<RetortBlock> codec(){return simpleCodec(RetortBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(LIT);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new RetortBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,RealmAgriculture.RETORT_ENTITY.get(),(world,pos,block,entity)->entity.process());}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof RetortBlockEntity entity)server.openMenu(entity,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof RetortBlockEntity entity)entity.release();super.onRemove(state,level,pos,next,moved);}
}
