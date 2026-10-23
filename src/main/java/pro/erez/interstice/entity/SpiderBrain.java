package pro.erez.interstice.entity;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.tslat.smartbrainlib.api.core.BrainActivityGroup;
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.custom.attack.AnimatableMeleeAttack;
import net.tslat.smartbrainlib.api.core.behaviour.custom.look.LookAtTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FloatToSurfaceOfFluid;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetRandomWalkTarget;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import pro.erez.interstice.Interstice;
import static pro.erez.interstice.entity.CaveRiftSpiderEntity.Variant.*;

/** SmartBrain decisions, Azure crawl motor. No legacy Goals compete for movement. */
public final class SpiderBrain {
    private static final DeferredRegister<MemoryModuleType<?>> MEMORIES=DeferredRegister.create(Registries.MEMORY_MODULE_TYPE,Interstice.ID);
    private static final DeferredRegister<SensorType<?>> SENSORS=DeferredRegister.create(Registries.SENSOR_TYPE,Interstice.ID);
    public static final DeferredHolder<MemoryModuleType<?>,MemoryModuleType<Vec3>> OBSERVATION=memory("spider_observation"),
            REPOSITION=memory("spider_reposition");
    private static final DeferredHolder<MemoryModuleType<?>,MemoryModuleType<Boolean>> ALERT_LOCK=memory("spider_alert_lock"),JUMP_LOCK=memory("spider_jump_lock"),BURST_LOCK=memory("spider_burst_lock");
    private static final DeferredHolder<MemoryModuleType<?>,MemoryModuleType<String>> TACTIC=memory("spider_tactic");
    private static final DeferredHolder<SensorType<?>,SensorType<Awareness>> AWARENESS=SENSORS.register("spider_awareness",()->new SensorType<>(Awareness::new));
    private static <T> DeferredHolder<MemoryModuleType<?>,MemoryModuleType<T>> memory(String name){return MEMORIES.register(name,()->new MemoryModuleType<T>(Optional.empty()));}
    public static void register(IEventBus bus){MEMORIES.register(bus);SENSORS.register(bus);}
    static List<ExtendedSensor<CaveRiftSpiderEntity>> sensors(){return List.of(new Awareness());}
    static BrainActivityGroup<CaveRiftSpiderEntity> core(){return BrainActivityGroup.coreTasks(new FloatToSurfaceOfFluid<>(),new LookAtTarget<>(),new Motor());}
    static BrainActivityGroup<CaveRiftSpiderEntity> fight(){return BrainActivityGroup.fightTasks(new Tactics(),new Melee(),new Fire());}
    static BrainActivityGroup<CaveRiftSpiderEntity> idle(){return BrainActivityGroup.idleTasks(new Search(),
            new SetRandomWalkTarget<CaveRiftSpiderEntity>().setRadius(5,2).speedModifier(.8F)
                    .startCondition(e->e.getVariant()!=LURKER&&observed(e)==null&&!e.surfaceControlNeeded()).cooldownFor(e->80));}
    public static Vec3 observed(CaveRiftSpiderEntity e){return e.getBrain().getMemory(OBSERVATION.get()).orElse(null);}
    public static String tactic(CaveRiftSpiderEntity e){return e.getBrain().getMemory(TACTIC.get()).orElse("WAIT");}
    public static boolean canBurst(CaveRiftSpiderEntity e){return !e.getBrain().hasMemoryValue(BURST_LOCK.get());}
    public static void burstStarted(CaveRiftSpiderEntity e){e.getBrain().setMemoryWithExpiry(BURST_LOCK.get(),true,240);}
    private static void tactic(CaveRiftSpiderEntity e,String action){e.getBrain().setMemory(TACTIC.get(),action);}
    public static boolean eligible(CaveRiftSpiderEntity e,LivingEntity target){
        return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==e.level()&&target!=e
                &&!(target instanceof CaveRiftSpiderEntity)&&!e.isAlliedTo(target)&&e.canAttack(target)
                &&(!(target instanceof Player p)||!p.isCreative()&&!p.isSpectator());
    }
    private static boolean visible(CaveRiftSpiderEntity e,LivingEntity target){
        return eligible(e,target)&&!target.isInvisible()&&e.distanceToSqr(target)<=24*24&&e.getSensing().hasLineOfSight(target);
    }
    public static void acquire(CaveRiftSpiderEntity e,LivingEntity target){
        if(e.getTarget()!=target)e.getBrain().eraseMemory(REPOSITION.get());
        e.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,target);
        e.getBrain().setMemoryWithExpiry(OBSERVATION.get(),target.position(),200);
    }
    public static void alert(CaveRiftSpiderEntity e,LivingEntity target){
        if(e.level().isClientSide||!visible(e,target)||e.getBrain().hasMemoryValue(ALERT_LOCK.get()))return;
        e.getBrain().setMemoryWithExpiry(ALERT_LOCK.get(),true,80);
        e.level().getEntitiesOfClass(CaveRiftSpiderEntity.class,e.getBoundingBox().inflate(20,10,20),
                other->other!=e&&other.isAlive()&&!other.isNoAi()&&other.getTarget()==null&&e.getSensing().hasLineOfSight(other))
                .stream().sorted(Comparator.comparingDouble(e::distanceToSqr)).limit(3).forEach(other->{
                    if(visible(other,target)){acquire(other,target);other.triggerAmbushBurst();}
                    else other.getBrain().setMemoryWithExpiry(OBSERVATION.get(),target.position(),100);
                });
    }
    private static void stopGround(CaveRiftSpiderEntity e){
        e.getNavigation().stop();e.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);e.getBrain().eraseMemory(MemoryModuleType.PATH);
    }
    private static int rank(LivingEntity target){return target instanceof Player?0:target instanceof net.minecraft.world.entity.ambient.Bat?1:2;}
    private static final class Awareness extends ExtendedSensor<CaveRiftSpiderEntity>{
        private int lastHurt=-1;
        Awareness(){setScanRate(e->0);}
        @Override public SensorType<? extends ExtendedSensor<?>> type(){return AWARENESS.get();}
        @Override public List<MemoryModuleType<?>> memoriesUsed(){return List.of(OBSERVATION.get(),REPOSITION.get(),ALERT_LOCK.get(),JUMP_LOCK.get(),BURST_LOCK.get(),TACTIC.get(),MemoryModuleType.ATTACK_TARGET,MemoryModuleType.ATTACK_COOLING_DOWN,MemoryModuleType.WALK_TARGET,MemoryModuleType.LOOK_TARGET,MemoryModuleType.PATH);}
        @Override protected void doTick(ServerLevel level,CaveRiftSpiderEntity e){
            LivingEntity target=e.getTarget();
            if(target!=null&&!visible(e,target)){e.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);stopGround(e);target=null;}
            LivingEntity attacker=e.getLastHurtByMob();
            if(e.getLastHurtByMobTimestamp()!=lastHurt){
                lastHurt=e.getLastHurtByMobTimestamp();
                if(visible(e,attacker)){target=attacker;e.triggerAmbushBurst();}
            }
            if(target==null&&(e.tickCount+e.getId())%5==0){
                target=level.getEntitiesOfClass(LivingEntity.class,e.getBoundingBox().inflate(24),t->e.canTargetEntity(t)&&visible(e,t))
                        .stream().min(Comparator.<LivingEntity>comparingInt(SpiderBrain::rank).thenComparingDouble(e::distanceToSqr)).orElse(null);
            }
            if(target!=null){acquire(e,target);if(e.getVariant()==LURKER)e.triggerAmbushBurst();alert(e,target);}
            else if(observed(e)==null&&!tactic(e).equals("WAIT")){stopGround(e);e.getBrain().eraseMemory(REPOSITION.get());tactic(e,"WAIT");}
        }
    }
    private abstract static class Action extends ExtendedBehaviour<CaveRiftSpiderEntity>{
        @Override protected List<Pair<MemoryModuleType<?>,MemoryStatus>> getMemoryRequirements(){return List.of();}
    }
    /** Sole ground/surface movement owner; no MoveToWalkTarget runs simultaneously. */
    private static final class Motor extends Action{
        private int groundTick;
        Motor(){noTimeout();}
        @Override protected boolean shouldKeepRunning(CaveRiftSpiderEntity e){return true;}
        @Override protected void tick(CaveRiftSpiderEntity e){
            if(e.surfaceControlNeeded()){e.tickSurfaceControl();drop(e);return;}
            var walk=e.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            if(walk!=null&&e.tickCount>=groundTick){
                groundTick=e.tickCount+10;Vec3 at=walk.getTarget().currentPosition();
                if(e.position().distanceToSqr(at)<=walk.getCloseEnoughDist()*walk.getCloseEnoughDist())stopGround(e);
                else e.getNavigation().moveTo(at.x,at.y,at.z,walk.getSpeedModifier());
            }
        }
        private void drop(CaveRiftSpiderEntity e){
            var target=e.getTarget();if(e.getVariant()==SPITTER||!e.isClimbingCeiling()||!visible(e,target))return;
            Vec3 delta=target.position().subtract(e.position());double radius=e.getVariant()==LURKER?3.5:2.8;
            if(delta.y< -1&&delta.horizontalDistance()<=radius){
                e.detachFromSurface();e.triggerAmbushBurst();Vec3 horizontal=new Vec3(delta.x,0,delta.z).normalize().scale(.55);
                e.setDeltaMovement(horizontal.x,-.85,horizontal.z);e.hasImpulse=true;tactic(e,"DROP");
            }
        }
        @Override protected void stop(CaveRiftSpiderEntity e){stopGround(e);}
    }
    private static void destination(CaveRiftSpiderEntity e,Vec3 point,float speed){
        if(point==null)return;
        e.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(point,speed,1));
        e.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,new BlockPosTracker(BlockPos.containing(point.add(0,1,0))));
    }
    private static Vec3 lateral(CaveRiftSpiderEntity e,Vec3 target,double offset,double advance){
        Vec3 toward=target.subtract(e.position());Vec3 side=new Vec3(-toward.z,0,toward.x).normalize();
        int sign=(e.getId()%2==0)?1:-1;
        for(int direction:new int[]{sign,-sign}){
            Vec3 at=e.position().add(side.scale(offset*direction)).add(new Vec3(toward.x,0,toward.z).normalize().scale(advance));
            if(!e.level().hasChunkAt(BlockPos.containing(at)))continue;
            var path=e.getNavigation().createPath(BlockPos.containing(at),0);
            if(path!=null&&path.canReach())return at;
        }
        return null;
    }
    private static final class Tactics extends Action{
        Tactics(){cooldownFor(e->10);}
        @Override protected void start(CaveRiftSpiderEntity e){
            var target=e.getTarget();if(target==null)return;Vec3 at=observed(e);if(at==null)return;
            double distance=e.distanceToSqr(target);
            if(e.getVariant()==SPITTER){
                if(distance<25){Vec3 away=e.position().subtract(at);away=new Vec3(away.x,0,away.z).normalize().scale(5);destination(e,e.position().add(away),1.38F);tactic(e,"RETREAT");}
                else if(e.getBrain().hasMemoryValue(REPOSITION.get())){destination(e,e.getBrain().getMemory(REPOSITION.get()).orElseThrow(),1.15F);tactic(e,"REPOSITION");}
                else if(distance>144){destination(e,at,1.15F);tactic(e,"APPROACH");}
                else{stopGround(e);tactic(e,"RANGED");}
            }else if(e.getVariant()==LURKER){
                destination(e,at,1.35F);tactic(e,e.isClimbingCeiling()?"AMBUSH":"POUNCE");
                if(e.onGround()&&distance>=9&&distance<=64&&!e.getBrain().hasMemoryValue(JUMP_LOCK.get())){
                    e.getBrain().setMemoryWithExpiry(JUMP_LOCK.get(),true,35);e.performAmbushLunge(target);
                }
            }else{
                if(e.onGround()&&distance>=4&&distance<=16&&!e.getBrain().hasMemoryValue(JUMP_LOCK.get())){
                    e.getBrain().setMemoryWithExpiry(JUMP_LOCK.get(),true,50);Vec3 delta=at.subtract(e.position()).normalize();
                    e.setDeltaMovement(delta.x*.55,.38,delta.z*.55);e.hasImpulse=true;tactic(e,"POUNCE");
                }else{
                    Vec3 flank=e.getBrain().getMemory(REPOSITION.get()).orElse(null);
                    if(flank==null&&distance>16&&!e.isWallCrawling()){
                        flank=lateral(e,at,3,2);if(flank!=null)e.getBrain().setMemoryWithExpiry(REPOSITION.get(),flank,40);
                    }
                    destination(e,flank==null?at:flank,1.25F);tactic(e,flank==null?"APPROACH":"FLANK");
                }
            }
        }
    }
    private static final class Search extends Action{
        Search(){cooldownFor(e->15);}
        @Override protected void start(CaveRiftSpiderEntity e){if(observed(e)!=null){destination(e,observed(e),.9F);tactic(e,"SEARCH");}}
    }
    private static final class Fire extends Action{
        Fire(){cooldownFor(e->8);}
        @Override protected boolean checkExtraStartConditions(ServerLevel level,CaveRiftSpiderEntity e){return e.getVariant()==SPITTER&&e.spitCooldownRemaining()==0&&visible(e,e.getTarget())&&e.distanceToSqr(e.getTarget())<=196;}
        @Override protected void start(CaveRiftSpiderEntity e){
            e.performRangedAttack(e.getTarget(),1);
            if(e.spitCooldownRemaining()>0&&!e.isWallCrawling()){
                Vec3 at=lateral(e,observed(e),3,0);if(at!=null)e.getBrain().setMemoryWithExpiry(REPOSITION.get(),at,38);
            }
        }
    }
    private static final class Melee extends AnimatableMeleeAttack<CaveRiftSpiderEntity>{
        Melee(){super(4);}
        private boolean valid(CaveRiftSpiderEntity e){return e.isAlive()&&!e.isNoAi()&&e.getVariant()!=SPITTER&&target!=null&&target==e.getTarget()&&visible(e,target);}
        @Override protected boolean checkExtraStartConditions(ServerLevel l,CaveRiftSpiderEntity e){return e.getVariant()!=SPITTER&&super.checkExtraStartConditions(l,e)&&valid(e);}
        @Override protected boolean shouldKeepRunning(CaveRiftSpiderEntity e){return valid(e)&&super.shouldKeepRunning(e);}
        @Override protected void start(CaveRiftSpiderEntity e){e.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN,true,20);}
        @Override protected void doDelayedAction(CaveRiftSpiderEntity e){if(valid(e)&&e.isWithinMeleeAttackRange(target))e.doHurtTarget(target);}
    }
    private SpiderBrain(){}
}
