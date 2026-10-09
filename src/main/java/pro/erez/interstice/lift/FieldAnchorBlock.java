package pro.erez.interstice.lift;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.minerals.MineralEcology;

public final class FieldAnchorBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(2, 4, 2, 14, 12, 14), Block.box(5, 12, 5, 11, 16, 11));
    public FieldAnchorBlock() { this(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(5, 12)); }
    private FieldAnchorBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<FieldAnchorBlock> codec() { return simpleCodec(FieldAnchorBlock::new); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FieldAnchorEntity(pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity owner, ItemStack stack) {
        if (!level.isClientSide && owner instanceof Player player && level.getBlockEntity(pos) instanceof FieldAnchorEntity anchor) { anchor.setOwner(player.getUUID()); anchor.deploy(player); }
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(RealmLift.CONTROLLER.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof FieldAnchorEntity anchor) || !anchor.authorized(player)) return ItemInteractionResult.FAIL;
        int fuel = stack.is(MineralEcology.UMBRAL_COAL.get()) ? 3200 : stack.is(MineralEcology.PHOSPHORITE_CRYSTAL.get()) ? 1600 : 0;
        if (fuel > 0) {
            if (anchor.fuel() > RealmLift.MAX_FUEL - fuel) return ItemInteractionResult.FAIL;
            if (!level.isClientSide && anchor.addFuel(fuel)) { if (!player.isCreative()) stack.shrink(1); player.displayClientMessage(Component.translatable("message.interstice.lift.fuel", anchor.fuel() / 20), true); }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(RealmAgriculture.PURE_LINING.get()) && anchor.status() == FieldAnchorEntity.Status.BROKEN) {
            if (!level.isClientSide && anchor.repair(player) && !player.isCreative()) stack.shrink(1);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FieldAnchorEntity anchor) {
            boolean accepted = anchor.control(player, player.isShiftKeyDown() ? FieldAnchorEntity.Action.DOWN : FieldAnchorEntity.Action.UP);
            player.displayClientMessage(Component.translatable(accepted ? "message.interstice.lift.status" : "message.interstice.lift.unavailable",
                    Component.translatable("status.interstice.lift." + anchor.status().name().toLowerCase(java.util.Locale.ROOT))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof FieldAnchorEntity anchor) anchor.removeAnchor();
        super.onRemove(state, level, pos, next, moved);
    }
}
