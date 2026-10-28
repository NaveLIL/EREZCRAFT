package pro.erez.interstice.fauna;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.tslat.smartbrainlib.api.core.BrainActivityGroup;
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.custom.attack.AnimatableMeleeAttack;
import net.tslat.smartbrainlib.api.core.behaviour.custom.look.LookAtTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.*;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetRandomWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetRandomLookTarget;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import pro.erez.interstice.Interstice;
import static pro.erez.interstice.fauna.VaultHunter.Phase.*;

/** SmartBrainLib owns memory expiry, sensors, activities, movement and attack timing.
 * These policies only supply the realm's perception and tactical destinations. */
public final class HunterBrain {
    private static final DeferredRegister<MemoryModuleType<?>> MEMORIES=DeferredRegister.create(Registries.MEMORY_MODULE_TYPE,Interstice.ID);
    private static final DeferredRegister<SensorType<?>> SENSORS=DeferredRegister.create(Registries.SENSOR_TYPE,Interstice.ID);
    public static final DeferredHolder<MemoryModuleType<?>,MemoryModuleType<Vec3>> OBSERVATION=memory("hunter_observation"),
            AMBUSH_POINT=memory("hunter_ambush_point"),RETREAT_POINT=memory("hunter_retreat_point"),SEARCH_POINT=memory("hunter_search_point");
    private static final DeferredHolder<MemoryModuleType<?>,MemoryModuleType<Boolean>> REST=memory("hunter_rest"),
            RETREAT_LOCK=memory("hunter_retreat_lock"),ALERT_LOCK=memory("hunter_alert_lock");
    private static final DeferredHolder<MemoryModuleType<?>,MemoryModuleType<Long>> SEARCH_UNTIL=memory("hunter_search_until");
    private static final DeferredHolder<SensorType<?>,SensorType<Awareness>> AWARENESS=SENSORS.register("hunter_awareness",()->new SensorType<>(Awareness::new));
    private static <T> DeferredHolder<MemoryModuleType<?>,MemoryModuleType<T>> memory(String name){
        // No codec: observations, target identities and combat state never survive a cold load.
        return MEMORIES.register(name,()->new MemoryModuleType<T>(Optional.empty()));
    }
    public static void register(IEventBus bus){MEMORIES.register(bus);SENSORS.register(bus);}
    static List<ExtendedSensor<VaultHunter>> sensors(){return List.of(new Awareness());}
    static BrainActivityGroup<VaultHunter> core(){return BrainActivityGroup.coreTasks(
            new FloatToSurfaceOfFluid<VaultHunter>(),new LookAtTarget<VaultHunter>(),new Movement());}
    static BrainActivityGroup<VaultHunter> fight(){return BrainActivityGroup.fightTasks(new Chase(),new Strike());}
    static BrainActivityGroup<VaultHunter> idle(){return BrainActivityGroup.idleTasks(
            new Investigate(),new Retreat(),
            new SetRandomLookTarget<VaultHunter>().startCondition(e->e.phase()==IDLE),
            new Wander().setRadius(6,2).speedModifier(.6F).startCondition(e->e.phase()==IDLE).cooldownFor(e->60));}

    static Vec3 observed(VaultHunter e){return e.getBrain().getMemory(OBSERVATION.get()).orElse(null);}
    static void see(VaultHunter e,Player player){
        var brain=e.getBrain();
        if(e.phase()!=CHASE||e.getTarget()!=player){clearNavigation(e);e.cancelStrike();}
        brain.setMemory(MemoryModuleType.ATTACK_TARGET,player);
        brain.setMemoryWithExpiry(OBSERVATION.get(),player.position(),200);
        brain.eraseMemory(AMBUSH_POINT.get());brain.eraseMemory(SEARCH_UNTIL.get());brain.eraseMemory(SEARCH_POINT.get());
        e.phase(CHASE);
        if(!brain.hasMemoryValue(ALERT_LOCK.get())){
            brain.setMemoryWithExpiry(ALERT_LOCK.get(),true,200);
            e.playSound(HunterSounds.ALERT.get(),.7F,1);
            e.level().getEntitiesOfClass(VaultHunter.class,e.getBoundingBox().inflate(12),
                    other->other!=e&&other.phase()==IDLE&&e.getSensing().hasLineOfSight(other))
                    .stream().min(Comparator.comparingDouble(e::distanceToSqr)).ifPresent(other->hear(other,player.position()));
        }
    }
    static void hear(VaultHunter e,Vec3 place){
        if(e.phase()==RETREAT||e.phase()==COOLDOWN||e.phase()==CHASE)return;
        clearNavigation(e);e.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        e.getBrain().eraseMemory(AMBUSH_POINT.get());e.getBrain().eraseMemory(SEARCH_UNTIL.get());e.getBrain().eraseMemory(SEARCH_POINT.get());
        e.getBrain().setMemoryWithExpiry(OBSERVATION.get(),place,100);e.phase(INVESTIGATE);
    }
    private static void clearNavigation(VaultHunter e){
        e.getNavigation().stop();e.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        e.getBrain().eraseMemory(MemoryModuleType.PATH);e.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }
    private static void forget(VaultHunter e){
        clearNavigation(e);e.cancelStrike();
        for(var type:List.of(OBSERVATION.get(),AMBUSH_POINT.get(),RETREAT_POINT.get(),SEARCH_POINT.get(),SEARCH_UNTIL.get(),MemoryModuleType.ATTACK_TARGET))e.getBrain().eraseMemory(type);
        e.phase(IDLE);
    }

    public static final class Awareness extends ExtendedSensor<VaultHunter>{
        Awareness(){setScanRate(e->0);} // SBL's strict '<' requires a zero interval to scan every game tick.
        @Override public SensorType<? extends ExtendedSensor<?>> type(){return AWARENESS.get();}
        @Override public List<MemoryModuleType<?>> memoriesUsed(){return List.of(OBSERVATION.get(),AMBUSH_POINT.get(),RETREAT_POINT.get(),SEARCH_POINT.get(),SEARCH_UNTIL.get(),REST.get(),RETREAT_LOCK.get(),ALERT_LOCK.get(),MemoryModuleType.ATTACK_TARGET,MemoryModuleType.LOOK_TARGET,MemoryModuleType.WALK_TARGET,MemoryModuleType.PATH,MemoryModuleType.ATTACK_COOLING_DOWN);}
        @Override protected void doTick(ServerLevel level,VaultHunter e){
            var brain=e.getBrain();
            if(brain.hasMemoryValue(RETREAT_POINT.get())){brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);e.phase(RETREAT);return;}
            if(e.phase()==RETREAT){clearNavigation(e);brain.setMemoryWithExpiry(REST.get(),true,160);e.phase(COOLDOWN);}
            if(brain.hasMemoryValue(REST.get())){brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);return;}
            if(e.phase()==COOLDOWN)forget(e);
            Vec3 known=observed(e);
            if(known!=null&&e.getHealth()<=e.getMaxHealth()*.3F&&!brain.hasMemoryValue(RETREAT_LOCK.get())){
                Vec3 point=retreat(e,known);brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);e.cancelStrike();clearNavigation(e);
                brain.setMemoryWithExpiry(RETREAT_LOCK.get(),true,600);
                if(point!=null){brain.setMemoryWithExpiry(RETREAT_POINT.get(),point,100);e.phase(RETREAT);}
                else{brain.setMemoryWithExpiry(REST.get(),true,160);e.phase(COOLDOWN);}
                return;
            }
            Player seen=brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) instanceof Player p&&e.perceives(p)?p:null;
            if(seen==null&&(e.tickCount+e.getId())%5==0)
                seen=level.players().stream().filter(e::perceives).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
            if(seen!=null){see(e,seen);return;}
            if(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)){
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);clearNavigation(e);e.cancelStrike();
                Vec3 point=ambush(e,known);
                if(point!=null){brain.setMemoryWithExpiry(AMBUSH_POINT.get(),point,60);e.phase(AMBUSH);}else e.phase(SEARCH);
            }
            if(known==null){if(e.phase()!=IDLE)forget(e);return;}
            if(brain.hasMemoryValue(AMBUSH_POINT.get()))return;
            if(e.phase()==AMBUSH){clearNavigation(e);e.phase(SEARCH);}
            if(e.distanceToSqr(known)<2.25){
                e.phase(SEARCH);
                long until=brain.getMemory(SEARCH_UNTIL.get()).orElseGet(()->{long end=level.getGameTime()+80;brain.setMemory(SEARCH_UNTIL.get(),end);return end;});
                if(level.getGameTime()>=until){forget(e);return;}
                if(!brain.hasMemoryValue(SEARCH_POINT.get())){
                    double angle=((e.getId()%6)+(until-level.getGameTime())/20)*Math.PI/3;
                    Vec3 point=known.add(Math.cos(angle)*3,0,Math.sin(angle)*3);
                    if(level.hasChunkAt(BlockPos.containing(point)))brain.setMemoryWithExpiry(SEARCH_POINT.get(),point,20);
                }
            }
        }
    }

    private abstract static class Intent extends ExtendedBehaviour<VaultHunter>{
        Intent(){cooldownFor(e->10);}
        @Override protected List<Pair<MemoryModuleType<?>,MemoryStatus>> getMemoryRequirements(){return List.of();}
        final void destination(VaultHunter e,Vec3 point,float speed,int close,Vec3 look){
            if(point==null)return;
            e.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,new BlockPosTracker(BlockPos.containing(look.add(0,1,0))));
            if(e.distanceToSqr(point)<close*close){e.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);e.getNavigation().stop();return;}
            e.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(point,speed,close));
        }
    }
    private static final class Chase extends Intent{
        @Override protected boolean checkExtraStartConditions(ServerLevel level,VaultHunter e){return e.phase()==CHASE;}
        @Override protected void start(VaultHunter e){Vec3 at=observed(e);destination(e,at,1.05F,1,at);}
    }
    private static final class Investigate extends Intent{
        @Override protected boolean checkExtraStartConditions(ServerLevel level,VaultHunter e){return e.phase()==INVESTIGATE||e.phase()==SEARCH||e.phase()==AMBUSH;}
        @Override protected void start(VaultHunter e){
            Vec3 known=observed(e);if(known==null)return;
            Vec3 at=e.getBrain().getMemory(AMBUSH_POINT.get()).orElseGet(()->e.getBrain().getMemory(SEARCH_POINT.get()).orElse(known));
            destination(e,at,e.phase()==SEARCH?.75F:1.05F,1,known);
        }
    }
    private static final class Retreat extends Intent{
        @Override protected boolean checkExtraStartConditions(ServerLevel level,VaultHunter e){return e.phase()==RETREAT;}
        @Override protected void start(VaultHunter e){Vec3 at=e.getBrain().getMemory(RETREAT_POINT.get()).orElse(null);destination(e,at,1.3F,1,at);}
    }
    /** Native SBL path follower, with a short failure cooldown instead of retrying every tick. */
    private static final class Movement extends MoveToWalkTarget<VaultHunter>{
        Movement(){noTimeout();cooldownFor(e->0);}
        @Override protected boolean checkExtraStartConditions(ServerLevel level,VaultHunter e){
            boolean ready=super.checkExtraStartConditions(level,e);
            if(!ready)cooldownFinishedAt=level.getGameTime()+5;
            return ready;
        }
        @Override protected void stop(VaultHunter e){
            boolean failed=e.getNavigation().isStuck();
            super.stop(e);
            cooldownFinishedAt=failed?e.level().getGameTime()+5:0;
        }
    }
    private static final class Wander extends SetRandomWalkTarget<VaultHunter>{
        @Override protected Vec3 getTargetPos(VaultHunter e){
            return HunterNavigation.loadedAround(e.level(),e.blockPosition(),12)?super.getTargetPos(e):null;
        }
    }
    private static final class Strike extends AnimatableMeleeAttack<VaultHunter>{
        Strike(){super(VaultHunter.STRIKE_WINDUP);}
        private boolean allowed(VaultHunter e){return e.isAlive()&&!e.isNoAi()&&e.phase()==CHASE&&target instanceof Player p&&e.perceives(p)&&e.getTarget()==p;}
        @Override protected boolean checkExtraStartConditions(ServerLevel level,VaultHunter e){return e.phase()==CHASE&&super.checkExtraStartConditions(level,e)&&allowed(e);}
        @Override protected boolean shouldKeepRunning(VaultHunter e){return allowed(e)&&super.shouldKeepRunning(e);}
        @Override protected void start(VaultHunter e){
            e.beginStrike();e.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN,true,24);
        }
        @Override protected void doDelayedAction(VaultHunter e){
            if(allowed(e)&&e.isWithinMeleeAttackRange(target)){e.doHurtTarget(target);e.playSound(HunterSounds.ATTACK.get(),.7F,1);}
        }
        @Override protected void stop(VaultHunter e){if(!allowed(e))e.cancelStrike();super.stop(e);}
    }

    private static Vec3 ambush(VaultHunter e,Vec3 known){
        if(known==null)return null;
        Vec3 best=null;double distance=e.distanceToSqr(known);
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4;BlockPos pos=BlockPos.containing(e.getX()+Math.cos(angle)*3,e.getY(),e.getZ()+Math.sin(angle)*3);
            if(!e.level().hasChunkAt(pos))continue;
            Vec3 at=Vec3.atBottomCenterOf(pos);if(at.distanceToSqr(known)>=distance)continue;
            if(e.level().clip(new ClipContext(known.add(0,1,0),at.add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e)).getType()==HitResult.Type.MISS)continue;
            var path=e.getNavigation().createPath(pos,0);if(path!=null&&path.canReach()){best=at;distance=at.distanceToSqr(known);}
        }
        return best;
    }
    private static Vec3 retreat(VaultHunter e,Vec3 threat){
        Vec3 best=null;double score=Double.NEGATIVE_INFINITY;
        for(int i=0;i<12;i++){
            double angle=i*Math.PI/6;BlockPos pos=BlockPos.containing(e.getX()+Math.cos(angle)*7,e.getY(),e.getZ()+Math.sin(angle)*7);
            if(!e.level().hasChunkAt(pos))continue;
            var path=e.getNavigation().createPath(pos,0);if(path==null||!path.canReach())continue;
            Vec3 at=Vec3.atBottomCenterOf(pos);double candidate=at.distanceToSqr(threat);
            if(e.level().clip(new ClipContext(threat.add(0,1,0),at.add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e)).getType()!=HitResult.Type.MISS)candidate+=64;
            if(candidate>score){score=candidate;best=at;}
        }
        return best;
    }
    private HunterBrain(){}
}
