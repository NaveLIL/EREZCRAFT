package pro.erez.interstice.gear;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import pro.erez.interstice.Interstice;

/** Server-authoritative bonuses; no recurring potion refresh or persistent player stat edits. */
@EventBusSubscriber(modid=Interstice.ID)
public final class RealmArmor {
    public static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
    public static final List<String> ROLES=List.of("plate","edge","shadow","cloth","strap","buckle","dark");
    public static final ResourceLocation EXPEDITION=id("expedition_set");
    public static final ResourceKey<Enchantment> RIFT_WARD=ResourceKey.create(Registries.ENCHANTMENT,id("rift_ward"));
    private RealmArmor(){}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(Interstice.ID,path);}
    /** One render pass per authored material. The client extension selects its geometry and color. */
    public static List<ArmorMaterial.Layer> layers(){
        return ROLES.stream().map(role->new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace(
                role.equals("strap")?"leather":"iron"),"",false)).toList();
    }
    public static class Item extends ArmorItem {
        private final boolean steel;
        public Item(Holder<ArmorMaterial> material,Type type,boolean steel){
            this(material,type,steel,new net.minecraft.world.item.Item.Properties().durability(type.getDurability(steel?50:40)));
        }
        protected Item(Holder<ArmorMaterial> material,Type type,boolean steel,net.minecraft.world.item.Item.Properties properties){
            super(material,type,properties);
            this.steel=steel;
        }
        public boolean steel(){return steel;}
        public boolean advanced(){return false;}
        @Override public ResourceLocation getArmorTexture(ItemStack stack,net.minecraft.world.entity.Entity entity,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner){
            return layer.texture(false);
        }
        @Override public ItemAttributeModifiers getDefaultAttributeModifiers(){
            var base=super.getDefaultAttributeModifiers();
            return switch(getEquipmentSlot()){
                case HEAD->add(base,Attributes.OXYGEN_BONUS,steel?2:1,AttributeModifier.Operation.ADD_VALUE);
                case CHEST->steel?add(base,Attributes.MAX_HEALTH,4,AttributeModifier.Operation.ADD_VALUE)
                        :add(base,Attributes.WATER_MOVEMENT_EFFICIENCY,.25,AttributeModifier.Operation.ADD_VALUE);
                case LEGS->add(base,Attributes.MOVEMENT_SPEED,steel?.05:.10,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                case FEET->add(add(base,Attributes.STEP_HEIGHT,.5,AttributeModifier.Operation.ADD_VALUE),
                        Attributes.FALL_DAMAGE_MULTIPLIER,steel?-.4:-.25,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                default->base;
            };
        }
        protected ItemAttributeModifiers add(ItemAttributeModifiers base,Holder<Attribute> attribute,double amount,AttributeModifier.Operation operation){
            return base.withModifierAdded(attribute,new AttributeModifier(id("armor_"+getEquipmentSlot().getName()+"_"+
                    attribute.unwrapKey().orElseThrow().location().getPath().replace('.','_')),amount,operation),EquipmentSlotGroup.bySlot(getEquipmentSlot()));
        }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){
            text.add(Component.translatable("tooltip.interstice."+(steel?"riftsteel":"riftsilver")+"_armor"));
            text.add(Component.translatable("tooltip.interstice.armor."+(steel?"steel_":"silver_")+getEquipmentSlot().getName()));
            text.add(Component.translatable("tooltip.interstice.armor."+(steel?"steel_set":"silver_set")));
        }
    }
    public static boolean worn(ItemStack stack,EquipmentSlot slot,boolean steel){
        return stack.getItem() instanceof Item item&&!item.advanced()&&item.steel()==steel&&item.getEquipmentSlot()==slot
                &&stack.getDamageValue()<stack.getMaxDamage();
    }
    public static int pieces(LivingEntity entity,boolean steel){
        return (int)SLOTS.stream().filter(slot->worn(entity.getItemBySlot(slot),slot,steel)).count();
    }
    /** Refresh one transient set modifier; removing a piece removes it on the same normal player tick. */
    public static void updateSet(LivingEntity entity){
        var mining=entity.getAttribute(Attributes.BLOCK_BREAK_SPEED);if(mining==null)return;
        boolean full=entity.isAlive()&&pieces(entity,false)==4;
        if(full&&!mining.hasModifier(EXPEDITION))mining.addTransientModifier(new AttributeModifier(EXPEDITION,.15,AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else if(!full&&mining.hasModifier(EXPEDITION))mining.removeModifier(EXPEDITION);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!event.getEntity().level().isClientSide){updateSet(event.getEntity());AegisPower.tick(event.getEntity());}
    }
    private static boolean nativeHazard(net.minecraft.world.damagesource.DamageSource source){
        var key=source.typeHolder().unwrapKey().orElse(null);
        return key!=null&&key.location().getNamespace().equals(Interstice.ID)
                &&List.of("spider_bite","spider_spit","toxin").contains(key.location().getPath());
    }
    @SubscribeEvent public static void poison(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event){
        if(!event.getEntity().level().isClientSide&&event.getEffectInstance().getEffect().equals(net.minecraft.world.effect.MobEffects.POISON)
                &&AegisPower.protectToxin(event.getEntity()))event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event){
        if(event.isCanceled()||event.getAmount()<=0||event.getEntity().level().isClientSide
                ||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var wearer=event.getEntity();int steel=pieces(wearer,true),silver=pieces(wearer,false),aegis=AegisArmor.pieces(wearer),engineer=EngineerArmor.pieces(wearer);
        if(steel+silver+aegis+engineer==0)return;
        if(AegisPower.protectEnvironment(wearer,event.getSource())){event.setCanceled(true);return;}
        float amount=event.getAmount();
        if(nativeHazard(event.getSource())){
            var chest=wearer.getItemBySlot(EquipmentSlot.CHEST);
            var registry=wearer.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            int level=chest.getItem() instanceof Item||chest.getItem() instanceof EngineerArmor.Piece?EnchantmentHelper.getItemEnchantmentLevel(registry.getHolderOrThrow(RIFT_WARD),chest):0;
            boolean toxin=event.getSource().is(pro.erez.interstice.ToxicLiquidBlock.TOXIN);
            double lining=toxin?(engineer==4&&EngineerArmor.lowerContact(wearer)?.60:0):engineer*.06;
            double reduction=Math.min(.60,aegis*.12+steel*.08+silver*.04+lining+Math.clamp(level,0,3)*.06);
            event.setAmount((float)(amount*(1-reduction)));
            // Even armor-bypassing toxin and venom consume the specialised lining.
            for(var slot:SLOTS)if(worn(wearer.getItemBySlot(slot),slot,true)||worn(wearer.getItemBySlot(slot),slot,false)||AegisArmor.worn(wearer.getItemBySlot(slot),slot)||EngineerArmor.worn(wearer,slot)&&(!toxin||lining>0))
                wearer.getItemBySlot(slot).hurtAndBreak(1,wearer,slot);
        }else if(aegis==4&&AegisPower.sealed(wearer)){
            event.setAmount(amount*.60F);
        }else if((steel==4||aegis==4)&&!event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)){
            event.setAmount(amount*(aegis==4?.75F:.85F));
        }
    }
}
