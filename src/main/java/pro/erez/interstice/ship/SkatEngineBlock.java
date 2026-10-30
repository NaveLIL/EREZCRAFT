package pro.erez.interstice.ship;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Fuel-driven Create shaft source, adjacent to the native Aeronautics propeller. */
public final class SkatEngineBlock extends DirectionalKineticBlock implements IBE<SkatEngineEntity> {
    public SkatEngineBlock(){super(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(5,25).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(SkatPropellerBlock.PORT);}
    @Override public Class<SkatEngineEntity> getBlockEntityClass(){return SkatEngineEntity.class;}
    @Override public BlockEntityType<? extends SkatEngineEntity> getBlockEntityType(){return SkatShips.ENGINE_ENTITY.get();}
    @Override public Direction.Axis getRotationAxis(BlockState s){return s.getValue(FACING).getAxis();}
    @Override public boolean hasShaftTowards(LevelReader w,BlockPos p,BlockState s,Direction face){return face.getAxis()==getRotationAxis(s);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return SkatData.shape(SkatPropellerBlock.index(s,"engine"));}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level w,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(w.getBlockEntity(p) instanceof SkatEngineEntity engine&&engine.addFuel(stack,player.isCreative(),player.isShiftKeyDown()?stack.getCount():1))return ItemInteractionResult.sidedSuccess(w.isClientSide);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override public float getDestroyProgress(net.minecraft.world.level.block.state.BlockState s,net.minecraft.world.entity.player.Player p,net.minecraft.world.level.BlockGetter w,net.minecraft.core.BlockPos pos){return pro.erez.interstice.vehicle.VehicleAccess.destroyProgress(s,p,w,pos);}
}
