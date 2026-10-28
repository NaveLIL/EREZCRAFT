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

/** Repairable expedition and heavy-combat tiers; balance lives in RealmArmor. */
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
        // A separate weapon: the original sword keeps its factory, stats and recipe.
        add("riftsteel_longsword", () -> new SwordItem(STEEL, new Item.Properties()
                .attributes(SwordItem.createAttributes(STEEL, 7, -2.4F))));
    }
    private RealmArmaments() {}
    public static void register(IEventBus bus) { MATERIALS.register(bus); ITEMS.register(bus); AegisArmor.register(bus); KotomyafArmor.register(bus); }
    public static Item item(String name) { return Objects.requireNonNull(EQUIPMENT.get(name), name).get(); }
    public static void displayItems(CreativeModeTab.Output output) {
        output.accept(RIFTSTEEL.get()); EQUIPMENT.values().forEach(i -> output.accept(i.get())); AegisArmor.displayItems(output); KotomyafArmor.displayItems(output);
    }
    private static Holder<ArmorMaterial> material(String name, boolean steel) {
        return MATERIALS.register(name, () -> new ArmorMaterial(
                Map.of(ArmorItem.Type.HELMET, steel ? 4 : 3, ArmorItem.Type.CHESTPLATE, steel ? 9 : 8,
                        ArmorItem.Type.LEGGINGS, steel ? 7 : 6, ArmorItem.Type.BOOTS, steel ? 4 : 3),
                steel ? 20 : 22, SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(steel ? RIFTSTEEL.get() : Interstice.RIFTSILVER_INGOT.get()),
                RealmArmor.layers(), steel ? 4 : 3, steel ? .125F : .05F));
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
        add(name + "_pickaxe", () -> new RealmWorktools.Pickaxe(tier, new Item.Properties().attributes(PickaxeItem.createAttributes(tier, 1, -2.8F)),steel));
        add(name + "_axe", () -> new RealmWorktools.Axe(tier, new Item.Properties().attributes(AxeItem.createAttributes(tier, 6, -3.1F)),steel));
        add(name + "_shovel", () -> new RealmWorktools.Shovel(tier, new Item.Properties().attributes(ShovelItem.createAttributes(tier, 1.5F, -3)),steel));
        add(name + "_hoe", () -> new RealmWorktools.Hoe(tier, new Item.Properties().attributes(HoeItem.createAttributes(tier, steel ? -3 : -2, steel ? 0 : -1)),steel));
        for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
            add(name + "_" + type.getName(), () -> new RealmArmor.Item(material, type, steel));
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
}
