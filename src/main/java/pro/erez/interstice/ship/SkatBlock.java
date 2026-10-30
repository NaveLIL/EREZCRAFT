package pro.erez.interstice.ship;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Static mesh cells have no ticking block entities. Only actual cargo/controls own state. */
public class SkatBlock extends Block {
    public SkatBlock(){super(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(5,25).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).lightLevel(SkatLighting::emission));registerDefaultState(stateDefinition.any().setValue(SkatData.CELL,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(SkatData.CELL);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter w,BlockPos pos,CollisionContext context){return SkatData.shape(state.getValue(SkatData.CELL));}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level w,BlockPos p,Player player,BlockHitResult hit){
        if(player.isShiftKeyDown())return InteractionResult.PASS;
        var localCell=SkatData.cell(s.getValue(SkatData.CELL));var localOrigin=p.subtract(localCell.pos());var localHit=hit.getLocation().subtract(localOrigin.getX(),localOrigin.getY(),localOrigin.getZ());
        var drawer=SkatData.cargoAt(localHit.x,localHit.y,localHit.z);
        if(player instanceof ServerPlayer server){
            if(SkatLighting.panelHit(localHit.x,localHit.y,localHit.z)&&w.getBlockEntity(localOrigin.offset(0,4,-6)) instanceof SkatHelmEntity h){h.openLighting(server);return InteractionResult.CONSUME;}
            if(w.getBlockEntity(p) instanceof SkatHelmEntity h){h.interact(server);return InteractionResult.CONSUME;}
            var cell=SkatData.cell(s.getValue(SkatData.CELL));var origin=p.subtract(cell.pos());
            // All visible faces of a drawer lead to its single authoritative inventory.
            var point=hit.getLocation().subtract(origin.getX(),origin.getY(),origin.getZ());var c=SkatData.cargoAt(point.x,point.y,point.z);
            if(c!=null&&w.getBlockEntity(origin.offset(c.anchor())) instanceof SkatCargoEntity inventory){server.openMenu(inventory);return InteractionResult.CONSUME;}
        }
        return drawer!=null||w.getBlockEntity(p) instanceof SkatHelmEntity||SkatLighting.panelHit(localHit.x,localHit.y,localHit.z)?InteractionResult.sidedSuccess(w.isClientSide):InteractionResult.PASS;
    }
    @Override protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState s,Level w,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        var c=SkatData.cell(s.getValue(SkatData.CELL));var origin=p.subtract(c.pos());var point=hit.getLocation().subtract(origin.getX(),origin.getY(),origin.getZ());if(SkatData.cargoAt(point.x,point.y,point.z)!=null)return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(c.pos().getY()>=4&&c.pos().getZ()>=8)for(int x:new int[]{-3,3})if(Math.abs(c.pos().getX()-x)<=2&&w.getBlockEntity(origin.offset(x,6,9)) instanceof SkatEngineEntity engine&&engine.addFuel(stack,player.isCreative(),player.isShiftKeyDown()?stack.getCount():1))return ItemInteractionResult.sidedSuccess(w.isClientSide);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected void onRemove(BlockState state,Level w,BlockPos pos,BlockState next,boolean moving){
        if(!moving&&!state.is(next.getBlock())&&w.getBlockEntity(pos) instanceof SkatCargoEntity cargo)Containers.dropContents(w,pos,cargo);
        super.onRemove(state,w,pos,next,moving);
    }
    @Override public float getDestroyProgress(net.minecraft.world.level.block.state.BlockState s,net.minecraft.world.entity.player.Player p,net.minecraft.world.level.BlockGetter w,net.minecraft.core.BlockPos pos){return pro.erez.interstice.vehicle.VehicleAccess.destroyProgress(s,p,w,pos);}
}
