package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** A wool-and-fish utility set. It grants cat abilities, not a sealed environmental suit. */
@EventBusSubscriber(modid=Interstice.ID)
public final class KotomyafArmor {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,Interstice.ID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS=DeferredRegister.create(Registries.ARMOR_MATERIAL,Interstice.ID);
    public static final Map<String,DeferredHolder<Item,Item>> PARTS=new LinkedHashMap<>();
    public static final Holder<ArmorMaterial> MATERIAL=MATERIALS.register("kotomyaf",()->new ArmorMaterial(
            Map.of(ArmorItem.Type.HELMET,2,ArmorItem.Type.CHESTPLATE,5,ArmorItem.Type.LEGGINGS,3,ArmorItem.Type.BOOTS,2),
            18,SoundEvents.ARMOR_EQUIP_LEATHER,()->Ingredient.of(ItemTags.WOOL),RealmArmor.layers(),0,0));
    static {for(var type:List.of(ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS,ArmorItem.Type.BOOTS))
        PARTS.put("kotomyaf_"+type.getName(),ITEMS.register("kotomyaf_"+type.getName(),()->new CatArmor(type)));}
    private static final Map<Player,Motion> MOTION=new WeakHashMap<>();
    private static final class Motion {Vec3 previous;int purrCooldown;Motion(Vec3 p){previous=p;}}
    private KotomyafArmor(){}
    public static void register(IEventBus bus){MATERIALS.register(bus);ITEMS.register(bus);}
    public static Item item(String name){return Objects.requireNonNull(PARTS.get(name),name).get();}
    public static void displayItems(CreativeModeTab.Output out){PARTS.values().forEach(p->out.accept(p.get()));}
    public static boolean worn(ItemStack stack,EquipmentSlot slot){return stack.getItem() instanceof CatArmor item&&item.getEquipmentSlot()==slot&&stack.getDamageValue()<stack.getMaxDamage();}
    public static boolean full(LivingEntity wearer){return wearer.isAlive()&&RealmArmor.SLOTS.stream().allMatch(slot->worn(wearer.getItemBySlot(slot),slot));}
    public static boolean afraid(LivingEntity mob){return mob instanceof Creeper||mob instanceof Zombie zombie&&zombie.isBaby();}
    private static boolean protectedPlayer(LivingEntity entity){return entity instanceof Player p&&!p.isSpectator()&&!p.isCreative()&&full(p);}
    public static final class CatArmor extends ArmorItem {
        CatArmor(Type type){super(MATERIAL,type,new Item.Properties().durability(type.getDurability(16)));}
        @Override public net.minecraft.resources.ResourceLocation getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner){return layer.texture(false);}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){
            text.add(Component.translatable("tooltip.interstice.kotomyaf.material"));
            text.add(Component.translatable("tooltip.interstice.kotomyaf.set"));
            text.add(Component.translatable("tooltip.interstice.kotomyaf.sounds"));
        }
    }
    /** Cancel only the normal fall damage tag. Void, lava, poison and melee are unchanged. */
    @SubscribeEvent public static void fall(LivingIncomingDamageEvent event){
        if(!event.getEntity().level().isClientSide&&event.getEntity() instanceof Player p&&full(p)
                &&event.getSource().is(DamageTypeTags.IS_FALL)&&!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))event.setCanceled(true);
    }
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event){
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p&&full(p)&&!p.isSpectator())KotomyafSounds.send(p,true);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        var p=event.getEntity();if(p.level().isClientSide)return;
        if(!full(p)||p.isSpectator()){MOTION.remove(p);return;}
        var state=MOTION.computeIfAbsent(p,k->new Motion(p.position()));
        double move=p.position().subtract(state.previous).horizontalDistanceSqr();state.previous=p.position();
        if(state.purrCooldown>0)state.purrCooldown--;
        if(p instanceof net.minecraft.server.level.ServerPlayer server&&p.isCrouching()&&!p.isPassenger()&&move>0.00001&&move<.25&&state.purrCooldown==0){
            KotomyafSounds.send(server,false);state.purrCooldown=10;
        }
        if(p.tickCount%5!=0)return;
        for(var mob:p.level().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(8),m->afraid(m)&&m.distanceToSqr(p)<=64)){
            if(mob.getTarget()==p)mob.setTarget(null);
            if(mob instanceof Creeper creeper&&!creeper.isIgnited())creeper.setSwellDir(-1);
        }
    }
    /** Clear the proposed protected target rather than cancelling, which would retain an old target. */
    @SubscribeEvent public static void target(LivingChangeTargetEvent event){
        var target=event.getNewAboutToBeSetTarget();
        if(!event.getEntity().level().isClientSide&&afraid(event.getEntity())&&target!=null&&protectedPlayer(target)
                &&event.getEntity().distanceToSqr(target)<=64)event.setNewAboutToBeSetTarget(null);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide||!(event.getEntity() instanceof PathfinderMob mob)||!(mob instanceof Creeper||mob instanceof Zombie))return;
        // EntityJoin fires before FULL chunks: install a goal only, without reading or loading the world.
        if(mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof CatAvoidGoal))mob.goalSelector.addGoal(1,new CatAvoidGoal(mob));
    }
    public static final class CatAvoidGoal extends AvoidEntityGoal<Player> {
        CatAvoidGoal(PathfinderMob mob){super(mob,Player.class,KotomyafArmor::protectedPlayer,8,1.1,1.45,EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);}
        @Override public boolean canUse(){return afraid(mob)&&super.canUse();}
        @Override public boolean canContinueToUse(){return afraid(mob)&&toAvoid!=null&&protectedPlayer(toAvoid)&&mob.distanceToSqr(toAvoid)<=144&&super.canContinueToUse();}
        @Override public void start(){mob.setTarget(null);if(mob instanceof Creeper c&&!c.isIgnited())c.setSwellDir(-1);super.start();}
        @Override public void tick(){if(mob instanceof Creeper c&&!c.isIgnited())c.setSwellDir(-1);super.tick();}
        @Override public void stop(){super.stop();mob.getNavigation().stop();}
    }
}
