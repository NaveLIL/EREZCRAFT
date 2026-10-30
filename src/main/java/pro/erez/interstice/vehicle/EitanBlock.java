package pro.erez.interstice.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public class EitanBlock extends Block {
    public static final IntegerProperty CELL=IntegerProperty.create("cell",0,EitanData.CELLS.size()-1);
    public static final BooleanProperty MOTION=BooleanProperty.create("motion"),LIT=BooleanProperty.create("lit");
    public EitanBlock(){super(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(6,40).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).dynamicShape().lightLevel(s->s.getValue(LIT)?EitanData.CELLS.get(s.getValue(CELL)).light():0));registerDefaultState(stateDefinition.any().setValue(CELL,0).setValue(MOTION,false).setValue(LIT,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(CELL,MOTION,LIT);}
    public static EitanController controller(BlockGetter level,BlockPos p,EitanData.Cell cell){return level.getBlockEntity(p.subtract(cell.pos()).offset(EitanData.CONTROL)) instanceof EitanController c?c:null;}
    public static VoxelShape shape(EitanData.Cell cell,BlockGetter level,BlockPos p,CollisionContext context){var c=controller(level,p,cell);return EitanData.shape(cell,c==null?new int[EitanData.GROUPS.size()]:c.collisionStates());}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter level,BlockPos p,CollisionContext context){return shape(EitanData.CELLS.get(s.getValue(CELL)),level,p,context);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level level,BlockPos p,Player player,BlockHitResult hit){return interact(level,p,EitanData.CELLS.get(s.getValue(CELL)),player,hit);}
    public static InteractionResult interact(Level level,BlockPos p,EitanData.Cell cell,Player player,BlockHitResult hit){var c=controller(level,p,cell);if(c==null)return InteractionResult.PASS;if(level.isClientSide)return InteractionResult.SUCCESS;return c.interact(player,hit.getLocation().subtract(c.origin().getX(),c.origin().getY(),c.origin().getZ()));}
    @Override protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState s,Level level,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){var c=controller(level,p,EitanData.CELLS.get(s.getValue(CELL)));if(c!=null&&c.fuelClick(player,stack,hit.getLocation()))return ItemInteractionResult.sidedSuccess(level.isClientSide);return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    public static final class Control extends BaseEntityBlock {
        public Control(){super(Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).dynamicShape().strength(6,40).lightLevel(s->s.getValue(LIT)?EitanData.BY_POS.get(EitanData.CONTROL).light():0));registerDefaultState(stateDefinition.any().setValue(MOTION,false).setValue(LIT,false));}
        @Override public float getDestroyProgress(BlockState s,Player p,BlockGetter w,BlockPos pos){return VehicleAccess.destroyProgress(s,p,w,pos);}
        @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new Control());}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(MOTION,LIT);}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new EitanController(p,s);}
        @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
        @Override protected VoxelShape getShape(BlockState s,BlockGetter level,BlockPos p,CollisionContext context){return shape(EitanData.BY_POS.get(EitanData.CONTROL),level,p,context);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState s,BlockEntityType<T> type){return createTickerHelper(type,EitanVehicles.CONTROL_ENTITY.get(),(w,p,state,c)->c.tick());}
        @Override protected InteractionResult useWithoutItem(BlockState s,Level level,BlockPos p,Player player,BlockHitResult hit){return interact(level,p,EitanData.BY_POS.get(EitanData.CONTROL),player,hit);}
        @Override protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState s,Level level,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){if(level.getBlockEntity(p) instanceof EitanController c&&c.fuelClick(player,stack,hit.getLocation()))return ItemInteractionResult.sidedSuccess(level.isClientSide);return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    }
    @Override public float getDestroyProgress(net.minecraft.world.level.block.state.BlockState s,net.minecraft.world.entity.player.Player p,net.minecraft.world.level.BlockGetter w,net.minecraft.core.BlockPos pos){return pro.erez.interstice.vehicle.VehicleAccess.destroyProgress(s,p,w,pos);}
}
