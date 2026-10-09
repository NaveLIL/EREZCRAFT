package pro.erez.interstice.ecology;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import pro.erez.interstice.Interstice;

/** Tagged vanilla entity, with saved absolute timing to prevent lifetime reset on cold load. */
@EventBusSubscriber(modid=Interstice.ID)
public final class SporePodGas {
    public static final String ENTITY_TAG="interstice_forest_pod_gas",EXPIRES="interstice_forest_gas_expires",READY="interstice_forest_gas_ready",CONTACT="interstice_forest_gas_until";
    public static final int DURATION=100;public static final float RADIUS=2.25F;
    private SporePodGas(){}
    public static boolean emit(ServerLevel level,BlockPos pos,int wait){
        if(!level.getEntitiesOfClass(AreaEffectCloud.class,new AABB(pos).inflate(6),e->e.isAlive()&&e.getTags().contains(ENTITY_TAG)).isEmpty())return false;
        long now=level.getServer().overworld().getGameTime();var cloud=new AreaEffectCloud(level,pos.getX()+.5,pos.getY()+.1,pos.getZ()+.5);
        cloud.addTag(ENTITY_TAG);cloud.setRadius(RADIUS);cloud.setWaitTime(wait);cloud.setDuration(DURATION);cloud.setRadiusPerTick(-RADIUS/(DURATION+1F));cloud.setParticle(ParticleTypes.SPORE_BLOSSOM_AIR);
        cloud.getPersistentData().putLong(READY,now+wait);cloud.getPersistentData().putLong(EXPIRES,now+wait+DURATION);return level.addFreshEntity(cloud);
    }
    public static void process(AreaEffectCloud cloud){
        if(!(cloud.level() instanceof ServerLevel level)||!cloud.getTags().contains(ENTITY_TAG))return;
        long now=level.getServer().overworld().getGameTime();var saved=cloud.getPersistentData();
        if(!saved.contains(EXPIRES)||now>=saved.getLong(EXPIRES)){cloud.discard();return;}
        if(now<saved.getLong(READY))return;cloud.setWaitTime(0);
        cloud.setDuration((int)Math.min(Integer.MAX_VALUE,(long)cloud.tickCount+saved.getLong(EXPIRES)-now));
        if(Math.floorMod(now,5)!=0)return;float radius=cloud.getRadius();
        for(var victim:level.getEntitiesOfClass(LivingEntity.class,cloud.getBoundingBox().inflate(0,1.5,0))){
            if(!victim.isAlive()||victim instanceof Player p&&(p.isCreative()||p.isSpectator())||victim.getPersistentData().getLong(CONTACT)>now)continue;
            double dx=victim.getX()-cloud.getX(),dz=victim.getZ()-cloud.getZ();if(dx*dx+dz*dz>radius*radius)continue;
            victim.getPersistentData().putLong(CONTACT,now+20);if(victim instanceof net.minecraft.server.level.ServerPlayer player&&pro.erez.interstice.gear.RealmGear.protect(player,pro.erez.interstice.gear.RealmGear.HazardKind.SPORE_POD))continue;victim.addEffect(new MobEffectInstance(MobEffects.POISON,160,1));victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,240));victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,80));
        }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event){if(event.getEntity() instanceof AreaEffectCloud cloud)process(cloud);}
}
