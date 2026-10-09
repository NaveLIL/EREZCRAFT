package pro.erez.interstice.gear;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.minerals.MineralEcology;

public final class RouteBeaconBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(4, 3, 4, 12, 12, 12), Block.box(2, 12, 2, 14, 16, 14));
    public RouteBeaconBlock() { this(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(4, 9).lightLevel(state -> state.getValue(LIT) ? 14 : 0)); }
    private RouteBeaconBlock(BlockBehaviour.Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(LIT, false)); }
    @Override protected MapCodec<RouteBeaconBlock> codec() { return simpleCodec(RouteBeaconBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LIT); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RouteBeaconEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, RealmGear.BEACON_ENTITY.get(), (world, pos, block, beacon) -> beacon.tick());
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Interstice.TIDE_INDICATOR.get()) && player.isShiftKeyDown()
                && level.getBlockEntity(pos) instanceof RouteBeaconEntity beacon) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            return player instanceof ServerPlayer server && RouteMarkers.bind(server, stack, beacon)
                    ? ItemInteractionResult.CONSUME : ItemInteractionResult.FAIL;
        }
        if (!stack.is(MineralEcology.PHOSPHORITE_CRYSTAL.get()) || !(level.getBlockEntity(pos) instanceof RouteBeaconEntity beacon))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (beacon.fuelTicks() > RealmGear.BEACON_MAX_FUEL - RealmGear.BEACON_FUEL_TICKS) return ItemInteractionResult.FAIL;
        if (!level.isClientSide && beacon.addFuel()) {
            if (!player.isCreative()) stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.interstice.beacon.fuel", beacon.fuelTicks() / 20), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RouteBeaconEntity beacon)
            player.displayClientMessage(Component.translatable("message.interstice.beacon.position", pos.getX(), pos.getY(), pos.getZ(), beacon.fuelTicks() / 20), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity owner, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RouteBeaconEntity beacon) {
            beacon.setOwner(owner == null ? null : owner.getUUID());
            beacon.setMarkerName(stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : "");
        }
    }
}
