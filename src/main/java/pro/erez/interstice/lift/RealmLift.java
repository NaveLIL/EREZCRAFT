package pro.erez.interstice.lift;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** Anchored world-owned transport. No client flight permission or mandatory slot mod. */
public final class RealmLift {
    public static final int MAX_TRAVEL = 48, CARGO_SLOTS = 9, MAX_FUEL = 9600;
    public static final double SPEED = .06;
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, Interstice.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Interstice.ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Interstice.ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Interstice.ID);
    public static final DeferredHolder<Block, FieldAnchorBlock> ANCHOR = BLOCKS.register("field_anchor", FieldAnchorBlock::new);
    public static final DeferredHolder<Item, BlockItem> ANCHOR_ITEM = ITEMS.register("field_anchor", () -> new BlockItem(ANCHOR.get(), new Item.Properties()) {
        @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) { text.add(Component.translatable("tooltip.interstice.field_anchor")); }
    });
    public static final DeferredHolder<Item, LiftControllerItem> CONTROLLER = ITEMS.register("lift_controller", LiftControllerItem::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FieldAnchorEntity>> ANCHOR_ENTITY = BLOCK_ENTITIES.register("field_anchor", () ->
            BlockEntityType.Builder.of(FieldAnchorEntity::new, ANCHOR.get()).build(null));
    public static final DeferredHolder<EntityType<?>, EntityType<FieldLiftEntity>> LIFT = ENTITIES.register("field_lift", () ->
            EntityType.Builder.<FieldLiftEntity>of(FieldLiftEntity::new, MobCategory.MISC).sized(1.5F, .35F).clientTrackingRange(8).updateInterval(1).build("interstice:field_lift"));
    private RealmLift() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); ENTITIES.register(bus); }
    public static void displayItems(CreativeModeTab.Output output) { output.accept(ANCHOR_ITEM.get()); output.accept(CONTROLLER.get()); }
    public static final class LiftControllerItem extends Item {
        public LiftControllerItem() { super(new Item.Properties().stacksTo(1)); }
        @Override public InteractionResult useOn(UseOnContext context) {
            if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown() || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FieldAnchorEntity anchor))
                return InteractionResult.PASS;
            if (!anchor.authorized(context.getPlayer())) return InteractionResult.FAIL;
            if (!context.getLevel().isClientSide) {
                var tag = context.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.putLong("LiftAnchorPos", context.getClickedPos().asLong()); tag.putUUID("LiftAnchorId", anchor.anchorId());
                tag.putString("LiftDimension", context.getLevel().dimension().location().toString());
                context.getItemInHand().set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                context.getPlayer().displayClientMessage(Component.translatable("message.interstice.lift.linked"), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
            var stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.fail(stack);
            var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            var dimension = ResourceLocation.tryParse(tag.getString("LiftDimension"));
            if (!tag.hasUUID("LiftAnchorId") || dimension == null || !level.dimension().location().equals(dimension)) return failed(server, stack);
            BlockPos pos = BlockPos.of(tag.getLong("LiftAnchorPos"));
            if (server.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64 * 64 || !level.hasChunkAt(pos)) return failed(server, stack);
            if (!(level.getBlockEntity(pos) instanceof FieldAnchorEntity anchor) || !anchor.anchorId().equals(tag.getUUID("LiftAnchorId"))
                    || !anchor.authorized(server)) return failed(server, stack);
            // Sneak is vanilla dismount for passengers. Normal use cycles both directions using the
            // saved endpoint; stopping preserves it, so the next use reverses without a sneak packet.
            var action = player.isShiftKeyDown() || anchor.target() > anchor.baseY() + .5
                    ? FieldAnchorEntity.Action.DOWN : FieldAnchorEntity.Action.UP;
            if (!anchor.control(server, action)) return failed(server, stack);
            return InteractionResultHolder.success(stack);
        }
        private InteractionResultHolder<ItemStack> failed(ServerPlayer player, ItemStack stack) {
            player.displayClientMessage(Component.translatable("message.interstice.lift.unavailable"), true); return InteractionResultHolder.fail(stack);
        }
        @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) {
            text.add(Component.translatable("tooltip.interstice.lift_controller"));
            if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().hasUUID("LiftAnchorId")) text.add(Component.translatable("tooltip.interstice.lift_linked"));
        }
    }
}
