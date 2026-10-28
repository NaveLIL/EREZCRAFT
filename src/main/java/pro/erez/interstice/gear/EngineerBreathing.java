package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import pro.erez.interstice.Interstice;

/** Holds the existing air budget; it never replaces it with a fresh full lungful. */
@EventBusSubscriber(modid=Interstice.ID)
public final class EngineerBreathing {
    public static final TagKey<DamageType> INHALATION=TagKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"engineer_inhalation"));
    private record Paid(long tick,ResourceLocation dimension){}
    private static final Map<LivingEntity,Paid> PAID=new WeakHashMap<>();
    private EngineerBreathing(){}
    /** Explicit integration hook for a nonrespirable atmosphere. No arbitrary gas/poison inference. */
    public static final class MediumEvent extends LivingEvent {
        private boolean nonrespirable;
        public MediumEvent(LivingEntity entity,boolean nonrespirable){super(entity);this.nonrespirable=nonrespirable;}
        public boolean nonrespirable(){return nonrespirable;}
        public void setNonrespirable(boolean value){nonrespirable=value;}
    }
    public static boolean medium(LivingEntity wearer){
        boolean water=wearer.isEyeInFluid(FluidTags.WATER)&&!wearer.hasEffect(MobEffects.WATER_BREATHING);
        return NeoForge.EVENT_BUS.post(new MediumEvent(wearer,water)).nonrespirable();
    }
    public static boolean fitted(LivingEntity wearer){return wearer.isAlive()&&EngineerArmor.worn(wearer,EquipmentSlot.HEAD)&&EngineerArmor.worn(wearer,EquipmentSlot.CHEST)&&EngineerModules.valid(wearer.getItemBySlot(EquipmentSlot.CHEST));}
    public static boolean ready(LivingEntity wearer){return fitted(wearer)&&EngineerModules.reserve(EngineerModules.get(wearer.getItemBySlot(EquipmentSlot.CHEST),0))>0;}
    public static boolean paidThisTick(LivingEntity wearer){var paid=PAID.get(wearer);return paid!=null&&paid.tick()==wearer.level().getServer().overworld().getGameTime();}
    /** At most one reserve tick for overlapping media and damage callbacks in the same server tick. */
    public static boolean breathe(LivingEntity wearer){
        if(wearer.level().isClientSide||!fitted(wearer)||wearer instanceof Player p&&(p.isCreative()||p.isSpectator())||!medium(wearer))return false;
        if(paidThisTick(wearer))return true;
        var chest=wearer.getItemBySlot(EquipmentSlot.CHEST);var cassette=EngineerModules.get(chest,0);int left=EngineerModules.reserve(cassette);if(left<=0)return false;
        EngineerModules.reserve(cassette,left-1);EngineerModules.set(chest,0,cassette);
        PAID.put(wearer,new Paid(wearer.level().getServer().overworld().getGameTime(),wearer.level().dimension().location()));
        if(wearer instanceof Player p)p.getInventory().setChanged();return true;
    }
    public static void tick(LivingEntity wearer){if(fitted(wearer)&&medium(wearer))breathe(wearer);}
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event){
        if(event.isCanceled()||event.getEntity().level().isClientSide||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var source=event.getSource();
        if((source.is(DamageTypes.DROWN)||source.is(INHALATION))&&!source.is(DamageTypeTags.IS_FIRE)&&medium(event.getEntity())&&breathe(event.getEntity()))event.setCanceled(true);
    }
}
