package pro.erez.interstice.gear;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** Two repairable native tiers; specialised lining only mitigates the realm's spider damage. */
@EventBusSubscriber(modid = Interstice.ID)
public final class RealmArmaments {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Interstice.ID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Interstice.ID);
    public static final Map<String, DeferredHolder<Item, ? extends Item>> EQUIPMENT = new LinkedHashMap<>();
    public static final DeferredHolder<Item, Item> RIFTSTEEL = ITEMS.register("riftsteel_ingot", () -> new Item(new Item.Properties()));
    public static final Holder<ArmorMaterial> SILVER_ARMOR = material("riftsilver", false);
    public static final Holder<ArmorMaterial> STEEL_ARMOR = material("riftsteel", true);
    public static final Tier SILVER = tier(false), STEEL = tier(true);
    public static final double SPIDER_REDUCTION_PER_PIECE = .08;
    static {
        equipment("riftsilver", SILVER, SILVER_ARMOR, false);
        equipment("riftsteel", STEEL, STEEL_ARMOR, true);
    }
    private RealmArmaments() {}
    public static void register(IEventBus bus) { MATERIALS.register(bus); ITEMS.register(bus); }
    public static Item item(String name) { return Objects.requireNonNull(EQUIPMENT.get(name), name).get(); }
    public static void displayItems(CreativeModeTab.Output output) {
        output.accept(RIFTSTEEL.get()); EQUIPMENT.values().forEach(i -> output.accept(i.get()));
    }
    private static Holder<ArmorMaterial> material(String name, boolean steel) {
        return MATERIALS.register(name, () -> new ArmorMaterial(
                Map.of(ArmorItem.Type.HELMET, steel ? 3 : 2, ArmorItem.Type.CHESTPLATE, steel ? 8 : 6,
                        ArmorItem.Type.LEGGINGS, steel ? 6 : 5, ArmorItem.Type.BOOTS, steel ? 3 : 2),
                steel ? 16 : 18, SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(steel ? RIFTSTEEL.get() : Interstice.RIFTSILVER_INGOT.get()),
                List.of(new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace("leather"), "", true),
                        new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace("leather"), "_overlay", false)),
                steel ? 2 : 0, steel ? .05F : 0));
    }
    private static Tier tier(boolean steel) {
        return new Tier() {
            public int getUses() { return steel ? 1792 : 480; }
            public float getSpeed() { return steel ? 8.5F : 6.5F; }
            public float getAttackDamageBonus() { return steel ? 3 : 2; }
            public TagKey<Block> getIncorrectBlocksForDrops() {
                return steel ? BlockTags.INCORRECT_FOR_DIAMOND_TOOL : BlockTags.INCORRECT_FOR_IRON_TOOL;
            }
            public int getEnchantmentValue() { return steel ? 16 : 18; }
            public Ingredient getRepairIngredient() { return Ingredient.of(steel ? RIFTSTEEL.get() : Interstice.RIFTSILVER_INGOT.get()); }
        };
    }
    private static void add(String id, Supplier<? extends Item> factory) { EQUIPMENT.put(id, ITEMS.register(id, factory)); }
    private static void equipment(String name, Tier tier, Holder<ArmorMaterial> material, boolean steel) {
        add(name + "_sword", () -> new SwordItem(tier, new Item.Properties().attributes(SwordItem.createAttributes(tier, 3, -2.4F))));
        add(name + "_pickaxe", () -> new PickaxeItem(tier, new Item.Properties().attributes(PickaxeItem.createAttributes(tier, 1, -2.8F))));
        add(name + "_axe", () -> new AxeItem(tier, new Item.Properties().attributes(AxeItem.createAttributes(tier, 6, -3.1F))));
        add(name + "_shovel", () -> new ShovelItem(tier, new Item.Properties().attributes(ShovelItem.createAttributes(tier, 1.5F, -3))));
        add(name + "_hoe", () -> new HoeItem(tier, new Item.Properties().attributes(HoeItem.createAttributes(tier, steel ? -3 : -2, steel ? 0 : -1))));
        for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
            add(name + "_" + type.getName(), () -> new ArmorItem(material, type,
                    new Item.Properties().durability(type.getDurability(steel ? 32 : 20))
                            .component(DataComponents.DYED_COLOR, new DyedItemColor(steel ? 0x386369 : 0xb4bdc9, false))) {
                @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) {
                    text.add(Component.translatable(steel ? "tooltip.interstice.riftsteel_armor" : "tooltip.interstice.riftsilver_armor"));
                }
            });
        }
    }
    public static int protectivePieces(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial().equals(STEEL_ARMOR)
                    && armor.getEquipmentSlot() == slot && stack.getDamageValue() < stack.getMaxDamage()) count++;
        }
        return count;
    }
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event) {
        var key = event.getSource().typeHolder().unwrapKey().orElse(null);
        if (key == null || !key.location().getNamespace().equals(Interstice.ID)
                || !Set.of("spider_bite", "spider_spit").contains(key.location().getPath())
                || event.isCanceled() || event.getAmount() <= 0 || event.getEntity().level().isClientSide) return;
        int pieces = protectivePieces(event.getEntity());
        if (pieces == 0) return;
        event.setAmount((float)(event.getAmount() * (1 - pieces * SPIDER_REDUCTION_PER_PIECE)));
        // These sources bypass vanilla armour wear. The specialised lining must still wear out.
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            var stack = event.getEntity().getItemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial().equals(STEEL_ARMOR) && armor.getEquipmentSlot() == slot)
                stack.hurtAndBreak(1, event.getEntity(), slot);
        }
    }
}
