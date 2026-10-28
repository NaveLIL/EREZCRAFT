package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.inventory.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** D02 expedition armor. E12 geometry is independent of the gameplay material. */
public final class EngineerArmor {
    public static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath(Interstice.ID,"textures/models/armor/engineer.png");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,Interstice.ID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS=DeferredRegister.create(Registries.ARMOR_MATERIAL,Interstice.ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,Interstice.ID);
    private static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPES=DeferredRegister.create(Registries.RECIPE_SERIALIZER,Interstice.ID);
    public static final Holder<ArmorMaterial> MATERIAL=MATERIALS.register("engineer",()->new ArmorMaterial(
        Map.of(ArmorItem.Type.HELMET,4,ArmorItem.Type.CHESTPLATE,8,ArmorItem.Type.LEGGINGS,7,ArmorItem.Type.BOOTS,3),22,
        SoundEvents.ARMOR_EQUIP_IRON,()->Ingredient.of(pro.erez.interstice.agriculture.RealmAgriculture.PURE_LINING.get()),
        List.of(new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace("iron"))),3.5F,.08F));
    public static final DeferredHolder<Item,Item> ASSEMBLY=ITEMS.register("engineer_assembly_pack",()->new Item(new Item.Properties()));
    public static final DeferredHolder<Item,Item> ADAPTER=ITEMS.register("engineer_neuroadapter",()->new Item(new Item.Properties()));
    public static final DeferredHolder<Item,Item> CELL=ITEMS.register("engineer_power_cell",()->new EngineerModules.ModuleItem(true));
    public static final DeferredHolder<Item,Item> CASSETTE=ITEMS.register("engineer_breathing_cassette",()->new EngineerModules.ModuleItem(false));
    public static final DeferredHolder<MenuType<?>,MenuType<EngineerMenu>> MENU=MENUS.register("engineer",()->new MenuType<>(EngineerMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>,EngineerRefillRecipe.Serializer> REFILL=RECIPES.register("engineer_refill",EngineerRefillRecipe.Serializer::new);
    public static final Map<String,DeferredHolder<Item,Piece>> PIECES=new LinkedHashMap<>();
    static{for(var type:List.of(ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS,ArmorItem.Type.BOOTS))
        PIECES.put(type.getName(),ITEMS.register("engineer_"+type.getName(),()->new Piece(type)));}
    private EngineerArmor(){}
    public static void register(IEventBus bus){MATERIALS.register(bus);ITEMS.register(bus);MENUS.register(bus);RECIPES.register(bus);bus.addListener(EngineerNetworking::register);}
    public static Item item(String kind){return PIECES.get(kind).get();}
    public static void displayItems(CreativeModeTab.Output output){PIECES.values().forEach(p->output.accept(p.get()));for(var item:List.of(ASSEMBLY,ADAPTER,CELL,CASSETTE))output.accept(item.get());}
    public static boolean worn(net.minecraft.world.entity.LivingEntity entity,EquipmentSlot slot){
        return worn(entity.getItemBySlot(slot),slot);
    }
    public static boolean worn(ItemStack stack,EquipmentSlot slot){return stack.getItem() instanceof Piece p&&p.getEquipmentSlot()==slot&&stack.getDamageValue()<stack.getMaxDamage();}
    public static int pieces(net.minecraft.world.entity.LivingEntity wearer){return (int)RealmArmor.SLOTS.stream().filter(s->worn(wearer,s)).count();}
    private static final ThreadLocal<net.minecraft.world.entity.LivingEntity> LOWER_CONTACT=new ThreadLocal<>();
    public static boolean lowerContact(net.minecraft.world.entity.LivingEntity entity){return LOWER_CONTACT.get()==entity;}
    public static void lowerSeaDamage(net.minecraft.world.entity.LivingEntity entity,net.minecraft.world.damagesource.DamageSource source,float amount){
        var previous=LOWER_CONTACT.get();LOWER_CONTACT.set(entity);try{entity.hurt(source,amount);}finally{if(previous==null)LOWER_CONTACT.remove();else LOWER_CONTACT.set(previous);}
    }
    public static final class Piece extends ArmorItem{
        Piece(Type type){super(MATERIAL,type,new Item.Properties().durability(type.getDurability(45)));}
        @Override public ItemAttributeModifiers getDefaultAttributeModifiers(){
            var base=super.getDefaultAttributeModifiers();return switch(getEquipmentSlot()){
                case HEAD->add(base,Attributes.OXYGEN_BONUS,1,AttributeModifier.Operation.ADD_VALUE);
                case LEGS->add(base,Attributes.MOVEMENT_SPEED,.08,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                case FEET->add(add(base,Attributes.STEP_HEIGHT,.5,AttributeModifier.Operation.ADD_VALUE),Attributes.FALL_DAMAGE_MULTIPLIER,-.35,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                default->base;};
        }
        private ItemAttributeModifiers add(ItemAttributeModifiers base,Holder<Attribute> attribute,double value,AttributeModifier.Operation operation){
            return base.withModifierAdded(attribute,new AttributeModifier(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"engineer_"+getEquipmentSlot().getName()+"_"+attribute.unwrapKey().orElseThrow().location().getPath().replace('.','_')),value,operation),EquipmentSlotGroup.bySlot(getEquipmentSlot()));
        }
        @Override public ResourceLocation getArmorTexture(ItemStack stack,net.minecraft.world.entity.Entity entity,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner){return TEXTURE;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flag){
            text.add(Component.translatable("tooltip.interstice.engineer.role"));
            if(getEquipmentSlot()==EquipmentSlot.HEAD)text.add(Component.translatable("tooltip.interstice.engineer.cell",EngineerModules.energy(EngineerModules.get(stack,0))/EngineerModules.UNIT));
            if(getEquipmentSlot()==EquipmentSlot.CHEST){text.add(Component.translatable("tooltip.interstice.engineer.air",EngineerModules.reserve(EngineerModules.get(stack,0))/20));var belt=EngineerModules.get(stack,1);if(!belt.isEmpty())text.add(Component.translatable("tooltip.interstice.engineer.belt",Math.max(0,belt.getMaxDamage()-belt.getDamageValue())));}
        }
    }
}
