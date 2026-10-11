package pro.erez.interstice.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.fauna.StrongRealmFauna;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;

/** The warning's flying hunters. A grab applies bounded physical motion, never
 * a mount, teleport, wall bypass or global change to the accepted tide/roof rules. */
public final class TidePhantomEntity extends Phantom {
    private static final EntityDataAccessor<Boolean> GRABBING=SynchedEntityData.defineId(TidePhantomEntity.class,EntityDataSerializers.BOOLEAN);
    private static final double REACH=2.4, MAX_PULL=.22;
    private UUID grabbed;
    private Vec3 escape;
    private int gripTicks,cooldown;
    private UUID huntTarget;
    private Vec3 huntEntrance;
    private TidePhase lastHuntPhase;
    private int scanTicks;
    public TidePhantomEntity(EntityType<? extends Phantom> type,Level level){
        super(type,level);setNoGravity(true);
        // Do not retain vanilla Phantom's private sweep goal/moveTargetPoint,
        // which assumes a daylight heightmap above the roof of this dimension.
        this.moveControl=new MoveControl(this){@Override public void tick(){}};
        xpReward=8;
    }
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes()
        .add(Attributes.MAX_HEALTH,26).add(Attributes.ATTACK_DAMAGE,6)
        .add(Attributes.MOVEMENT_SPEED,.24).add(Attributes.FLYING_SPEED,.5).add(Attributes.FOLLOW_RANGE,48);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(GRABBING,false);}
    @Override public boolean isSunBurnTick(){return false;}
    public static boolean spawnPhase(TidePhase phase){return phase==TidePhase.WARNING;}
    public boolean isGrabbing(){return entityData.get(GRABBING);}
    @Nullable public UUID grabbedPlayerId(){return grabbed;}
    private static boolean eligible(ServerLevel level,ServerPlayer player){return player!=null&&player.level()==level
        &&player.isAlive()&&!player.isRemoved()&&!player.isCreative()&&!player.isSpectator()&&!player.isPassenger();}
    private static boolean clearLine(ServerLevel level,Vec3 from,Vec3 to,net.minecraft.world.entity.Entity subject){
        if(!StrongRealmFauna.loadedBox(level,new AABB(from,to).inflate(.2)))return false;
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,subject)).getType()==HitResult.Type.MISS;
    }
    /** Only block/world-border geometry is considered. Every sampled cell must
     * already be loaded; neither path search nor pursuit creates world chunks. */
    private static boolean freeSweep(ServerLevel level,net.minecraft.world.entity.Entity subject,AABB box,Vec3 delta){
        int steps=Math.max(1,(int)Math.ceil(delta.length()/.35));if(steps>48)return false;
        for(int i=0;i<=steps;i++){
            AABB moved=box.move(delta.scale(i/(double)steps));
            if(!StrongRealmFauna.loadedBox(level,moved)||!level.getWorldBorder().isWithinBounds(moved)
                ||level.getBlockCollisions(subject,moved).iterator().hasNext()||level.containsAnyLiquid(moved))return false;
        }return true;
    }
    /** A nearby open column connected by a genuine player-sized straight path.
     * Fully sealed rooms have no route; broad roofs retain their physical value. */
    @Nullable public static Vec3 safeEscape(ServerLevel level,ServerPlayer player){
        if(!eligible(level,player)||!StrongRealmFauna.isV6(level))return null;
        var profile=GeometryProfiles.get(level);Vec3 origin=player.position();AABB box=player.getBoundingBox();
        for(int radius=3;radius<=12;radius+=3)for(int direction=0;direction<8;direction++){
            double angle=direction*Math.PI/4;Vec3 candidate=origin.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            AABB destination=box.move(candidate.subtract(origin));
            if(!StrongRealmFauna.loadedBox(level,destination)||!freeSweep(level,player,box,candidate.subtract(origin)))continue;
            if(destination.maxY>=SeaSurface.cellMinimum(profile,(int)Math.floor(candidate.x),(int)Math.floor(candidate.z),true)-2)continue;
            int startY=(int)Math.floor(destination.maxY);
            boolean open=true;
            for(int x=(int)Math.floor(destination.minX+.05);x<=(int)Math.floor(destination.maxX-.05)&&open;x++)
                for(int z=(int)Math.floor(destination.minZ+.05);z<=(int)Math.floor(destination.maxZ-.05);z++)
                    if(ShelterDetector.hasRoofAt(level,profile,x,startY,z)){open=false;break;}
            if(open)return candidate;
        }return null;
    }
    public boolean tryBeginGrab(ServerPlayer player){
        if(!isAlive()||isRemoved()||!(level() instanceof ServerLevel level)||!StrongRealmFauna.isV6(level)||cooldown>0||isGrabbing()
            ||TideManager.getState(level.getServer()).phase()!=TidePhase.SURGE||!eligible(level,player)
            ||distanceToSqr(player)>REACH*REACH||!ShelterDetector.isSheltered(level,player)
            ||!clearLine(level,getEyePosition(),player.getBoundingBox().getCenter(),this))return false;
        if(!level.getEntitiesOfClass(TidePhantomEntity.class,player.getBoundingBox().inflate(8),p->p!=this&&player.getUUID().equals(p.grabbedPlayerId())).isEmpty())return false;
        Vec3 destination=safeEscape(level,player);if(destination==null)return false;
        grabbed=player.getUUID();escape=destination;gripTicks=0;entityData.set(GRABBING,true);
        playSound(SoundEvents.PHANTOM_SWOOP,1.2F,.7F);return true;
    }
    private void release(){grabbed=null;escape=null;gripTicks=0;huntEntrance=null;scanTicks=0;entityData.set(GRABBING,false);cooldown=Math.max(cooldown,80);}
    @Override public boolean hurt(DamageSource source,float damage){boolean applied=super.hurt(source,damage);if(applied&&!level().isClientSide)release();return applied;}
    @Override public void die(DamageSource source){release();super.die(source);}
    @Override public void tick(){super.tick();if(isAlive()&&!isRemoved()&&!level().isClientSide)updateHunt();}
    /** Public for the integration probe; production invokes it once per native tick. */
    public void updateHunt(){
        if(!isAlive()||isRemoved()){release();return;}
        if(!(level() instanceof ServerLevel level))return;
        var phase=TideManager.getState(level.getServer()).phase();
        if(!StrongRealmFauna.isV6(level)||phase==TidePhase.CALM||phase==TidePhase.EBB){release();discard();return;}
        if(lastHuntPhase!=phase){lastHuntPhase=phase;scanTicks=0;huntTarget=null;huntEntrance=null;}
        if(cooldown>0)cooldown--;
        if(isGrabbing()){
            var entity=level.getEntity(grabbed);ServerPlayer player=entity instanceof ServerPlayer p?p:null;
            if(phase!=TidePhase.SURGE||!eligible(level,player)||distanceToSqr(player)>16
                ||!clearLine(level,getEyePosition(),player.getBoundingBox().getCenter(),this)
                ||!ShelterDetector.isSheltered(level,player)||++gripTicks>60){release();return;}
            Vec3 delta=escape.subtract(player.position());Vec3 horizontal=new Vec3(delta.x,0,delta.z);
            if(horizontal.lengthSqr()<.25||!freeSweep(level,player,player.getBoundingBox(),horizontal.normalize().scale(.5))){release();return;}
            Vec3 direction=horizontal.normalize();Vec3 previous=player.getDeltaMovement();
            // Retain vertical gravity and fall behavior. Marking ordinary velocity
            // synchronizes through vanilla packets; no flight permission is granted.
            Vec3 pulled=new Vec3(previous.x*.45+direction.x*.12,previous.y,previous.z*.45+direction.z*.12);
            double speed=pulled.horizontalDistance();if(speed>MAX_PULL)pulled=new Vec3(pulled.x/speed*MAX_PULL,pulled.y,pulled.z/speed*MAX_PULL);
            player.setDeltaMovement(pulled);player.hasImpulse=true;player.hurtMarked=true;
            steer(player.position().add(0,1.1,0),.23);return;
        }
        if(scanTicks--<=0){
            scanTicks=20;huntTarget=null;huntEntrance=null;
            var candidates=level.players().stream().filter(p->eligible(level,p)&&distanceToSqr(p)<48*48)
                .sorted(java.util.Comparator.comparingDouble(this::distanceToSqr)).limit(10).toList();
            for(var candidate:candidates){
                if(phase==TidePhase.SURGE&&cooldown==0){
                    if(!ShelterDetector.isSheltered(level,candidate))continue;
                    var entrance=safeEscape(level,candidate);if(entrance==null)continue;
                    huntEntrance=entrance;
                }
                huntTarget=candidate.getUUID();break;
            }
        }
        var target=huntTarget==null?null:level.getEntity(huntTarget);ServerPlayer player=target instanceof ServerPlayer p?p:null;
        if(!eligible(level,player)){setDeltaMovement(getDeltaMovement().scale(.8));return;}
        if(phase==TidePhase.WARNING||cooldown>0){
            double angle=(tickCount+getId()*11)*.035;
            Vec3 circle=player.position().add(Math.cos(angle)*7,5,Math.sin(angle)*7);
            steer(circle,.28);return;
        }
        if(tryBeginGrab(player))return;
        Vec3 body=player.position().add(0,.9,0);
        if(freeSweep(level,this,getBoundingBox(),body.subtract(position()))){steer(body,.38);return;}
        // Find the same physically open edge used for release, descend there,
        // then approach under the roof. Never steer through the roof itself.
        Vec3 edge=huntEntrance;if(edge==null){setDeltaMovement(getDeltaMovement().scale(.8));return;}
        Vec3 above=edge.add(0,Math.max(1,getY()-edge.y),0);
        Vec3 waypoint=position().subtract(edge).horizontalDistanceSqr()>4?above:edge.add(0,.9,0);
        steer(waypoint,.32);
    }
    private void steer(Vec3 desired,double speed){
        if(!(level() instanceof ServerLevel level))return;Vec3 delta=desired.subtract(position());
        if(delta.lengthSqr()<.1){setDeltaMovement(getDeltaMovement().scale(.8));return;}
        Vec3 step=delta.normalize().scale(speed);
        if(!freeSweep(level,this,getBoundingBox(),step)){setDeltaMovement(Vec3.ZERO);return;}
        setDeltaMovement(getDeltaMovement().scale(.3).add(step.scale(.7)));hasImpulse=true;
        setYRot((float)(Math.atan2(-step.x,step.z)*180/Math.PI));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);release();huntTarget=null;lastHuntPhase=null;cooldown=0;}
}
