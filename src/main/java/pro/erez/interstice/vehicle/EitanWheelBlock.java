package pro.erez.interstice.vehicle;

import dev.ryanhcode.offroad.content.blocks.wheel_mount.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

public final class EitanWheelBlock extends WheelMountBlock {
    public static final IntegerProperty SLOT=IntegerProperty.create("slot",0,7);
    public EitanWheelBlock(){super(Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).dynamicShape().strength(6,40));registerDefaultState(defaultBlockState().setValue(SLOT,0).setValue(EitanBlock.MOTION,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(SLOT,EitanBlock.MOTION);}
    @Override public Class<WheelMountBlockEntity> getBlockEntityClass(){return WheelMountBlockEntity.class;}
    @Override public BlockEntityType<? extends WheelMountBlockEntity> getBlockEntityType(){return EitanVehicles.WHEEL_ENTITY.get();}
    @Override public VoxelShape getShape(BlockState s,BlockGetter level,BlockPos p,CollisionContext context){var cell=EitanData.BY_POS.get(EitanData.MOUNTS.get(s.getValue(SLOT)).pos());return EitanBlock.shape(cell,level,p,context);}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter level,BlockPos p,CollisionContext context){return getShape(s,level,p,context);}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos p,BlockState s,Direction side){return false;}
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState s,Level level,BlockPos p,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState s,Level level,BlockPos p,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.BlockHitResult hit){return EitanBlock.interact(level,p,EitanData.BY_POS.get(EitanData.MOUNTS.get(s.getValue(SLOT)).pos()),player,hit);}
    @Override public float getDestroyProgress(net.minecraft.world.level.block.state.BlockState s,net.minecraft.world.entity.player.Player p,net.minecraft.world.level.BlockGetter w,net.minecraft.core.BlockPos pos){return pro.erez.interstice.vehicle.VehicleAccess.destroyProgress(s,p,w,pos);}
}
