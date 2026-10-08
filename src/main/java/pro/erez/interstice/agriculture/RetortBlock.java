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

public final class RetortBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    public RetortBlock(){this(BlockBehaviour.Properties.ofFullCopy(Blocks.FURNACE).strength(4,8).lightLevel(s->s.getValue(LIT)?4:0));}
    private RetortBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(LIT,false));}
    @Override protected MapCodec<RetortBlock> codec(){return simpleCodec(RetortBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(LIT);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new RetortBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,RealmAgriculture.RETORT_ENTITY.get(),(world,pos,block,entity)->entity.process());}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof RetortBlockEntity entity)server.openMenu(entity,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof RetortBlockEntity entity)entity.release();super.onRemove(state,level,pos,next,moved);}
}
