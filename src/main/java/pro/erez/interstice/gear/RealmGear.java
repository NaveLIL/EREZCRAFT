package pro.erez.interstice.gear;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.tide.BuoyancyController;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideState;
import pro.erez.interstice.worldgen.IslandWorld;

/** Optional expedition tools. Defaults preserve existing tide and toxic-hazard behaviour. */
public final class RealmGear {
    public static final int BELT_MAX_DAMAGE = 240;
    public static final int COATING_CHARGES = 12;
    public static final int COATING_WINDOW_TICKS = 20;
    public static final int BEACON_FUEL_TICKS = 2400;
    public static final int BEACON_MAX_FUEL = BEACON_FUEL_TICKS * 4;
    public enum HazardKind { LOWER_SEA, CAVE_GAS, STINGING_PLANT, SPORE_POD }
    private static final String COATING = "interstice_coating_charges";
    private static final String COATING_UNTIL = "interstice_coating_until";
    private static final String BELT_LAST_WEAR = "interstice_ballast_wear_tick";
    private static final String BELT_ACTIVE_TICKS = "interstice_ballast_active_ticks";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, Interstice.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Interstice.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Interstice.ID);
    public static final DeferredHolder<Block, RouteBeaconBlock> BEACON = BLOCKS.register("route_beacon", RouteBeaconBlock::new);
    public static final DeferredHolder<Item, Item> BEACON_ITEM = ITEMS.register("route_beacon", () -> new BlockItem(BEACON.get(), new Item.Properties()) {
        @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) {
            text.add(Component.translatable("tooltip.interstice.route_beacon"));
        }
    });
    public static final DeferredHolder<Item, BallastBelt> BALLAST_BELT = ITEMS.register("ballast_belt", BallastBelt::new);
    public static final DeferredHolder<Item, ProtectiveCoating> PROTECTIVE_COATING = ITEMS.register("protective_coating", ProtectiveCoating::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RouteBeaconEntity>> BEACON_ENTITY = ENTITIES.register("route_beacon", () ->
            BlockEntityType.Builder.of(RouteBeaconEntity::new, BEACON.get()).build(null));
    private RealmGear() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); NeoForge.EVENT_BUS.addListener(RealmGear::addTooltips); }
    public static void displayItems(CreativeModeTab.Output output) {
        output.accept(BEACON_ITEM.get()); output.accept(BALLAST_BELT.get()); output.accept(PROTECTIVE_COATING.get());
    }
    private static void addTooltips(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (chestArmor(stack) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(COATING))
            event.getToolTip().add(Component.translatable("tooltip.interstice.coating_charges", coatingCharges(stack)));
    }
    private static boolean usableBelt(ItemStack stack) {
        return stack.is(BALLAST_BELT.get()) && stack.getDamageValue() < BELT_MAX_DAMAGE;
    }
    private static InteractionHand beltHand(ServerPlayer player) {
        if (usableBelt(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        return usableBelt(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
    }
    /** Multiplies the whole optional gravity modifier; it does not change base gravity or other entities. */
    public static double buoyancyFactor(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !player.isAlive() || player.isCreative() || player.isSpectator()
                || !IslandWorld.isIsland(player.level().dimension()) || beltHand(player) == null) return 1;
        return .62;
    }
    /** Call after the normal buoyancy update: shelter, fluid, vehicles and phase eligibility remain native. */
    public static void tick(ServerPlayer player) { tick(player, TideManager.getState(player.server)); }
    public static void tick(ServerPlayer player, TideState tide) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || !IslandWorld.isIsland(player.level().dimension())
                || tide.phase() != TidePhase.SURGE && tide.phase() != TidePhase.EBB || tide.buoyancyIntensity() <= 0) return;
        var gravity = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
        if (gravity == null || !gravity.hasModifier(BuoyancyController.BUOYANCY_ID)) return;
        InteractionHand hand = beltHand(player); if (hand == null) return;
        ItemStack stack = player.getItemInHand(hand);
        long now = player.level().getGameTime();
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(BELT_LAST_WEAR) && tag.getLong(BELT_LAST_WEAR) == now) return;
        int active = Math.clamp(tag.getInt(BELT_ACTIVE_TICKS), 0, 19) + 1;
        tag.putLong(BELT_LAST_WEAR, now); tag.putInt(BELT_ACTIVE_TICKS, active == 20 ? 0 : active);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); player.getInventory().setChanged();
        if (active < 20) return;
        int wear = stack.getDamageValue() + 1;
        if (wear >= BELT_MAX_DAMAGE) {
            Item broken = stack.getItem(); stack.shrink(1); player.onEquippedItemBroken(broken, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        } else stack.setDamageValue(wear);
        player.getInventory().setChanged();
    }
    private static boolean chestArmor(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.CHEST;
    }
    public static int coatingCharges(ItemStack armor) {
        if (!chestArmor(armor)) return 0;
        return Math.clamp(armor.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(COATING), 0, COATING_CHARGES);
    }
    /** Adds metadata to one existing chestplate; count, name, damage, enchantments and unrelated data survive. */
    public static boolean applyCoating(ItemStack armor) {
        if (!chestArmor(armor) || coatingCharges(armor) > 0) return false;
        var tag = armor.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(COATING, COATING_CHARGES); tag.remove(COATING_UNTIL);
        armor.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); return true;
    }
    /** Only explicit native hazard callers may request this protection; no global vanilla effect interception. */
    public static boolean protect(ServerPlayer player, HazardKind kind) {
        if (kind == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return false;
        ItemStack armor = player.getItemBySlot(EquipmentSlot.CHEST); if (!chestArmor(armor)) return false;
        var tag = armor.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        long now = player.level().getGameTime(), until = tag.getLong(COATING_UNTIL);
        // Bound a restored timestamp: offline time or a foreign long value cannot grant permanent protection.
        if (until > now && until - now <= COATING_WINDOW_TICKS) return true;
        int charges = coatingCharges(armor); if (charges <= 0) return false;
        tag.putInt(COATING, charges - 1); tag.putLong(COATING_UNTIL, now + COATING_WINDOW_TICKS);
        armor.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); player.getInventory().setChanged();
        return true;
    }
    public static final class BallastBelt extends Item {
        public BallastBelt() { super(new Item.Properties().durability(BELT_MAX_DAMAGE)); }
        @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) {
            text.add(Component.translatable("tooltip.interstice.ballast_belt"));
            text.add(Component.translatable("tooltip.interstice.ballast_remaining", Math.max(0, BELT_MAX_DAMAGE - stack.getDamageValue())));
        }
    }
    public static final class ProtectiveCoating extends Item {
        public ProtectiveCoating() { super(new Item.Properties()); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            ItemStack target = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!chestArmor(target)) target = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!chestArmor(target) || coatingCharges(target) > 0) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.interstice.coating.target"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!level.isClientSide && applyCoating(target)) {
                if (!player.isCreative()) stack.shrink(1);
                player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
                player.displayClientMessage(Component.translatable("message.interstice.coating.applied", COATING_CHARGES), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) {
            text.add(Component.translatable("tooltip.interstice.protective_coating", COATING_CHARGES));
        }
    }
}
