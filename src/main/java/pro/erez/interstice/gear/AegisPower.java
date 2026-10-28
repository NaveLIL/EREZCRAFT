package pro.erez.interstice.gear;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.IslandWorld;

/** Saved charge on each physical piece; realm power is inexhaustible, durability is not. */
public final class AegisPower {
    public static final int CAPACITY=7200;
    public static final String CHARGE="interstice_aegis_charge";
    private static final String LAST="interstice_aegis_power_second";
    private static final String LAST_SEAL_WEAR="interstice_aegis_seal_wear";
    private AegisPower(){}
    public static int charge(ItemStack stack){return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt(CHARGE),0,CAPACITY);}
    public static void charge(ItemStack stack,int amount){
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();data.putInt(CHARGE,Math.clamp(amount,0,CAPACITY));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
    public static boolean realm(LivingEntity wearer){return IslandWorld.isIsland(wearer.level().dimension());}
    public static boolean powered(LivingEntity wearer,EquipmentSlot slot){return wearer.isAlive()&&AegisArmor.worn(wearer.getItemBySlot(slot),slot)&&(realm(wearer)||charge(wearer.getItemBySlot(slot))>0);}
    public static boolean sealed(LivingEntity wearer){return RealmArmor.SLOTS.stream().allMatch(slot->powered(wearer,slot));}
    public static boolean protectsTide(LivingEntity wearer){return realm(wearer)&&sealed(wearer);}
    /** Pure energy transition: one unit per second abroad, four in calm realm, 120 in the exposed surge. */
    public static int nextCharge(int old,boolean realm,boolean exposedSurge){return Math.clamp(old+(realm?(exposedSurge?120:4):-1),0,CAPACITY);}
    public static boolean exposedSurge(LivingEntity wearer,TideState tide){
        return realm(wearer)&&tide.phase()==TidePhase.SURGE&&tide.buoyancyIntensity()>0
                &&!ShelterDetector.isSheltered(wearer.level(),wearer)&&!wearer.isInWater()&&!wearer.isInFluidType()
                &&!wearer.onClimbable()&&!wearer.isPassenger()&&!wearer.isFallFlying();
    }
    public static void tick(net.minecraft.world.entity.player.Player player){
        if(!(player instanceof ServerPlayer serverPlayer)||!player.isAlive()||player.isSpectator())return;
        long second=serverPlayer.server.overworld().getGameTime()/20;
        var data=player.getPersistentData();if(data.contains(LAST)&&data.getLong(LAST)==second)return;data.putLong(LAST,second);
        boolean here=realm(player),surge=here&&exposedSurge(player,TideManager.getState(serverPlayer.server));
        for(var slot:RealmArmor.SLOTS){var stack=player.getItemBySlot(slot);if(!AegisArmor.worn(stack,slot))continue;
            int old=charge(stack),next=nextCharge(old,here,surge);if(next!=old){charge(stack,next);player.getInventory().setChanged();}}
        if(sealed(player)){player.clearFire();player.setAirSupply(player.getMaxAirSupply());player.removeEffect(net.minecraft.world.effect.MobEffects.POISON);}
    }
    public static boolean protectToxin(LivingEntity wearer){
        if(!sealed(wearer))return false;
        wearSeal(wearer);
        return true;
    }
    /** Multiple lava/fire/toxin cells in one tick cannot multiply seal wear. */
    private static void wearSeal(LivingEntity wearer){
        long now=wearer.level().getServer().overworld().getGameTime();var data=wearer.getPersistentData();
        if(data.contains(LAST_SEAL_WEAR)){long elapsed=now-data.getLong(LAST_SEAL_WEAR);if(elapsed>=0&&elapsed<20)return;}
        data.putLong(LAST_SEAL_WEAR,now);
        for(var slot:RealmArmor.SLOTS)wearer.getItemBySlot(slot).hurtAndBreak(1,wearer,slot);
    }
    public static boolean protectEnvironment(LivingEntity wearer,net.minecraft.world.damagesource.DamageSource source){
        if(!source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)&&!source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
                &&!source.is(pro.erez.interstice.ToxicLiquidBlock.TOXIN))return false;
        return protectToxin(wearer);
    }
}
