package pro.erez.interstice.ship;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class SkatSeatBlock extends SeatBlock {
    public SkatSeatBlock(){super(Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),DyeColor.CYAN);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(SkatData.CELL);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return SkatData.shape(s.getValue(SkatData.CELL));}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return getShape(s,w,p,c);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level w,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.isShiftKeyDown())return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(player instanceof ServerPlayer server&&!isSeatOccupied(w,p)){
            if(!SkatSeatEntity.mount(w,p,player))return ItemInteractionResult.FAIL;
            var cell=SkatData.cell(s.getValue(SkatData.CELL));
            if(cell.pos().getX()<0&&w.getBlockEntity(p.subtract(cell.pos()).offset(0,4,-6)) instanceof SkatHelmEntity helm)helm.interact(server);
        }
        return ItemInteractionResult.sidedSuccess(w.isClientSide);
    }
    @Override public void updateEntityAfterFallOn(BlockGetter w,net.minecraft.world.entity.Entity entity){/* Board explicitly; Create's automatic fall path uses its original seat entity. */}
    @Override public float getDestroyProgress(net.minecraft.world.level.block.state.BlockState s,net.minecraft.world.entity.player.Player p,net.minecraft.world.level.BlockGetter w,net.minecraft.core.BlockPos pos){return pro.erez.interstice.vehicle.VehicleAccess.destroyProgress(s,p,w,pos);}
}
