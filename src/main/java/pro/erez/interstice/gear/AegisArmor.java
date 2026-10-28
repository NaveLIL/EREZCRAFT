package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** Endgame composite armor and its first local electronics manufacturing chain. */
public final class AegisArmor {
    private static final DeferredRegister<net.minecraft.world.item.Item> ITEMS=DeferredRegister.create(Registries.ITEM,Interstice.ID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS=DeferredRegister.create(Registries.ARMOR_MATERIAL,Interstice.ID);
    public static final Map<String,DeferredHolder<net.minecraft.world.item.Item,? extends net.minecraft.world.item.Item>> PARTS=new LinkedHashMap<>();
    public static final DeferredHolder<net.minecraft.world.item.Item,net.minecraft.world.item.Item> ALLOY=component("abyssal_alloy"),PLATE=component("abyssal_plate"),
            SUBSTRATE=component("ceramic_substrate"),BOARD=component("etched_circuit_board"),PROCESSOR=component("resonant_processor"),OPTICS=component("aegis_optics"),CONTROLLER=component("aegis_control_unit");
    public static final Holder<ArmorMaterial> MATERIAL=MATERIALS.register("aegis",()->new ArmorMaterial(
            Map.of(ArmorItem.Type.HELMET,5,ArmorItem.Type.CHESTPLATE,11,ArmorItem.Type.LEGGINGS,9,ArmorItem.Type.BOOTS,5),
            24,SoundEvents.ARMOR_EQUIP_NETHERITE,()->Ingredient.of(ALLOY.get()),RealmArmor.layers(),5,.2F));
    static{for(var type:List.of(ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS,ArmorItem.Type.BOOTS))
        PARTS.put("aegis_"+type.getName(),ITEMS.register("aegis_"+type.getName(),()->new Armor(type)));}
    private AegisArmor(){}
    private static DeferredHolder<net.minecraft.world.item.Item,net.minecraft.world.item.Item> component(String name){
        var part=ITEMS.<net.minecraft.world.item.Item>register(name,()->new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties().fireResistant()){
            @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){text.add(Component.translatable("tooltip.interstice."+name));}
        });PARTS.put(name,part);return part;
    }
    public static void register(IEventBus bus){MATERIALS.register(bus);ITEMS.register(bus);}
    public static void displayItems(CreativeModeTab.Output out){PARTS.values().forEach(p->out.accept(p.get()));}
    public static net.minecraft.world.item.Item item(String name){return Objects.requireNonNull(PARTS.get(name),name).get();}
    public static boolean worn(ItemStack stack,EquipmentSlot slot){return stack.getItem() instanceof Armor armor&&armor.getEquipmentSlot()==slot&&stack.getDamageValue()<stack.getMaxDamage();}
    public static int pieces(LivingEntity entity){return (int)RealmArmor.SLOTS.stream().filter(slot->worn(entity.getItemBySlot(slot),slot)).count();}
    public static final class Armor extends RealmArmor.Item {
        public Armor(Type type){super(MATERIAL,type,true,new net.minecraft.world.item.Item.Properties().durability(type.getDurability(75)).fireResistant());}
        @Override public boolean advanced(){return true;}
        @Override public ItemAttributeModifiers getDefaultAttributeModifiers(){
            var base=super.getDefaultAttributeModifiers();
            return switch(getEquipmentSlot()){
                case HEAD->add(base,Attributes.OXYGEN_BONUS,3,AttributeModifier.Operation.ADD_VALUE);
                case CHEST->add(base,Attributes.MAX_HEALTH,8,AttributeModifier.Operation.ADD_VALUE);
                case LEGS->add(base,Attributes.MOVEMENT_SPEED,.10,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                case FEET->add(base,Attributes.FALL_DAMAGE_MULTIPLIER,-.6,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                default->base;
            };
        }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){
            text.add(Component.translatable("tooltip.interstice.aegis_armor"));
            text.add(Component.translatable("tooltip.interstice.armor.aegis_"+getEquipmentSlot().getName()));
            text.add(Component.translatable("tooltip.interstice.armor.aegis_set"));
            text.add(Component.translatable("tooltip.interstice.aegis_charge",AegisPower.charge(stack)*100/AegisPower.CAPACITY));
            text.add(Component.translatable("tooltip.interstice.aegis_harvest"));
        }
    }
}
