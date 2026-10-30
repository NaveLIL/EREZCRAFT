package pro.erez.interstice.fauna;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.entity.PartEntity;
import pro.erez.interstice.ToxicLiquidBlock;

/** Large territorial root mimic. Only the server owns perception, turning and strike timing. */
public final class Lozhnokoren extends Monster {
    public enum Phase { CAMOUFLAGED, WARNING, PURSUIT, STRIKE, RECOVER, RETURN }
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(Lozhnokoren.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SINCE=SynchedEntityData.defineId(Lozhnokoren.class,EntityDataSerializers.LONG);
    private static final java.util.List<EntityDataAccessor<Float>> BENDS=java.util.stream.IntStream.range(0,10).mapToObj(i->SynchedEntityData.defineId(Lozhnokoren.class,EntityDataSerializers.FLOAT)).toList();
    public static final int WARNING_TICKS=32,STRIKE_TICKS=20,IMPACT_TICK=8,RECOVERY_TICKS=36;
    private final Section[] sections;
    private final float[] bodyYaw=new float[11],oldBodyYaw=new float[11];
    private boolean bodyInitialized;
    private BlockPos home;private Vec3 observed;private int unseen;private boolean spent;
    public int landedStrikes,brokenVegetation;
    public Lozhnokoren(EntityType<? extends Lozhnokoren> type,Level level){super(type,level);xpReward=35;
        sections=new Section[15];
        float[] widths={31,34,35,34,32,29,26,23,20,16,12};
        for(int i=0;i<11;i++)sections[i]=new Section(this,"segment_"+i,6*Math.sin(i*.61)/16,(38-i*9.5)/16,(widths[i]+2)/16,1.9f,.15f);
        sections[11]=new Section(this,"head",0,3.45,1.35f,1.1f,.65f);
        sections[12]=new Section(this,"left_grasp",-1.5,4.3,1.2f,1.5f,.1f);
        sections[13]=new Section(this,"right_grasp",1.5,4.3,1.2f,1.5f,.1f);
        sections[14]=new Section(this,"tail",.2,-4.65,1.0f,1.6f,.65f);
        moveControl=new net.minecraft.world.entity.ai.control.MoveControl(this){@Override public void tick(){if(operation==Operation.MOVE_TO){operation=Operation.WAIT;setSpeed((float)(speedModifier*getAttributeValue(Attributes.MOVEMENT_SPEED)));Vec3 wanted=new Vec3(wantedX-getX(),0,wantedZ-getZ());setZza(wanted.lengthSqr()>.01&&forward().dot(wanted.normalize())>.85?1:0);}else{setZza(0);}}};
        setId(ENTITY_COUNTER.getAndAdd(sections.length+1)+1);refreshSections();
    }
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,240).add(Attributes.ATTACK_DAMAGE,22).add(Attributes.ARMOR,12).add(Attributes.KNOCKBACK_RESISTANCE,.9).add(Attributes.MOVEMENT_SPEED,.16).add(Attributes.FOLLOW_RANGE,18).add(Attributes.STEP_HEIGHT,.6);}
    @Override protected void registerGoals(){} // The explicit state machine is the only AI owner.
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(PHASE,0);b.define(SINCE,0L);for(var angle:BENDS)b.define(angle,0f);}
    public Phase phase(){return Phase.values()[entityData.get(PHASE)];}
    public float phaseAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(SINCE)+partial);}
    private void phase(Phase p){if(phase()!=p){entityData.set(PHASE,p.ordinal());entityData.set(SINCE,level().getGameTime());getNavigation().stop();if(p==Phase.STRIKE)spent=false;}}
    public Vec3 lastObservedPosition(){return observed;}
    public BlockPos home(){return home==null?blockPosition():home;}
    public Vec3 forward(){double a=Math.toRadians(getYRot());return new Vec3(-Math.sin(a),0,Math.cos(a));}
    public Vec3 headPosition(){return position().add(forward().scale(3.1)).add(0,1.05,0);}
    private boolean eligible(LivingEntity e){return e!=null&&e!=this&&!(e instanceof Lozhnokoren)&&e.isAlive()&&!e.isRemoved()&&e.level()==level()&&!(e instanceof Player p&&(p.isCreative()||p.isSpectator()))&&!isAlliedTo(e)&&canAttack(e);}
    public boolean sees(LivingEntity e){return eligible(e)&&!e.isInvisible()&&level().clip(new ClipContext(headPosition(),e.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    public boolean inStrikeArc(LivingEntity e){if(!eligible(e))return false;Vec3 d=e.getBoundingBox().getCenter().subtract(headPosition());double horizontal=d.horizontalDistance();return horizontal<=2.15+e.getBbWidth()/2&&Math.abs(d.y)<=2.2&&(horizontal<.01||forward().dot(new Vec3(d.x,0,d.z).normalize())>=Math.cos(Math.toRadians(35)));}
    private void warn(){phase(Phase.WARNING);playSound(SoundEvents.WOOD_HIT,.65f,.55f);}
    private void face(Vec3 point){Vec3 d=point.subtract(position());float desired=(float)Math.toDegrees(Math.atan2(-d.x,d.z));float delta=net.minecraft.util.Mth.wrapDegrees(desired-getYRot());float yaw=getYRot()+net.minecraft.util.Mth.clamp(delta,-3,3);if(canOccupy(position(),yaw))setYRot(yaw);}
    @Override protected void customServerAiStep(){
        super.customServerAiStep();if(home==null)home=blockPosition();var target=getTarget();
        if(!eligible(target)||target!=null&&target.distanceToSqr(Vec3.atBottomCenterOf(home))>32*32){setTarget(null);target=null;}
        if(target==null&&phase()!=Phase.RECOVER&&phase()!=Phase.STRIKE&&tickCount%10==0){
            Player nearest=null;double best=18*18;
            for(var p:level().players()){double distance=headPosition().distanceToSqr(p.getBoundingBox().getCenter());if(distance<best&&(!p.isShiftKeyDown()||distance<4*4)&&sees(p)){best=distance;nearest=p;}}
            if(nearest!=null){setTarget(nearest);target=nearest;unseen=0;warn();}
        }
        if(target!=null){if(sees(target)){unseen=0;observed=target.position();}else unseen++;if(unseen>120||observed==null){setTarget(null);target=null;}}
        switch(phase()){
            case CAMOUFLAGED -> {getNavigation().stop();if(target!=null)warn();}
            case WARNING -> {if(target==null){phase(Phase.RETURN);break;}face(observed);if(phaseAge(0)>=WARNING_TICKS)phase(Phase.PURSUIT);}
            case PURSUIT -> {
                if(target==null){phase(Phase.RETURN);break;}face(observed);
                if(inStrikeArc(target)&&unseen<60){phase(Phase.STRIKE);playSound(SoundEvents.WOOD_HIT,.8f,.45f);}
                else if(position().distanceToSqr(Vec3.atBottomCenterOf(home))<24*24&&phaseAge(0)<160){getMoveControl().setWantedPosition(observed.x-forward().x*4.5,observed.y,observed.z-forward().z*4.5,.65);}
                else{setTarget(null);phase(Phase.RETURN);}
            }
            case STRIKE -> {
                // Facing is locked. Sidestepping during this wind-up can evade the blow.
                getNavigation().stop();getMoveControl().strafe(0,0);
                if((int)phaseAge(0)==IMPACT_TICK){brokenVegetation+=LozhnokorenVegetation.sweep(this);if(target!=null)doHurtTarget(target);}
                if(phaseAge(0)>=STRIKE_TICKS)phase(Phase.RECOVER);
            }
            case RECOVER -> {getNavigation().stop();if(phaseAge(0)>=RECOVERY_TICKS){if(target==null)phase(Phase.RETURN);else warn();}}
            case RETURN -> {if(position().distanceToSqr(Vec3.atBottomCenterOf(home))<4){phase(Phase.CAMOUFLAGED);}else{face(Vec3.atBottomCenterOf(home));getMoveControl().setWantedPosition(home.getX()+.5,home.getY(),home.getZ()+.5,.5);}}
        }
    }
    @Override public boolean doHurtTarget(Entity e){if(level().isClientSide||phase()!=Phase.STRIKE||(int)phaseAge(0)!=IMPACT_TICK||spent||!(e instanceof LivingEntity victim)||!inStrikeArc(victim)||!sees(victim))return false;
        spent=true;boolean hit=super.doHurtTarget(e);if(hit){landedStrikes++;victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,1));}return hit;
    }
    @Override public boolean hurt(DamageSource source,float amount){boolean hit=super.hurt(source,amount);if(hit&&source.getEntity() instanceof LivingEntity attacker&&eligible(attacker)){setTarget(attacker);observed=attacker.position();unseen=0;if(phase()==Phase.CAMOUFLAGED||phase()==Phase.RETURN)warn();}return hit;}
    @Override public boolean isInvulnerableTo(DamageSource source){return source.is(ToxicLiquidBlock.TOXIN)||source.is(DamageTypes.DROWN)||super.isInvulnerableTo(source);}
    @Override public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type){return false;}
    public boolean canOccupy(Vec3 at,float yaw){
        if(sections==null)return true;for(var s:sections){var box=s.box(at,yaw).deflate(.03);if(!StrongRealmFauna.loadedBox((net.minecraft.server.level.ServerLevel)level(),box)||!level().noCollision(this,box))return false;}return true;
    }
    @Override public void move(MoverType type,Vec3 delta){if(!level().isClientSide&&sections!=null&&delta.horizontalDistanceSqr()>.000001&&!canOccupy(position().add(delta.x,0,delta.z),getYRot()))delta=new Vec3(0,delta.y,0);super.move(type,delta);}
    @Override public void tick(){super.tick();yBodyRot=getYRot();yHeadRot=getYRot();
        if(!bodyInitialized||Math.abs(net.minecraft.util.Mth.wrapDegrees(getYRot()-bodyYaw[0]))>45){java.util.Arrays.fill(bodyYaw,getYRot());bodyInitialized=true;}
        System.arraycopy(bodyYaw,0,oldBodyYaw,0,11);
        if(level().isClientSide){bodyYaw[0]=getYRot();for(int i=1;i<11;i++)bodyYaw[i]=bodyYaw[i-1]+entityData.get(BENDS.get(i-1));refreshSections();return;}
        for(int i=10;i>0;i--){float delta=net.minecraft.util.Mth.wrapDegrees(bodyYaw[i-1]-bodyYaw[i]);bodyYaw[i]+=net.minecraft.util.Mth.clamp(delta*.65f,-3,3);bodyYaw[i]=getYRot()+net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(bodyYaw[i]-getYRot()),-35,35);}
        bodyYaw[0]=getYRot();
        // A curved tail must not be allowed to swing through terrain.
        if(!level().isClientSide&&!canOccupy(position(),getYRot())){System.arraycopy(oldBodyYaw,0,bodyYaw,0,11);bodyYaw[0]=getYRot();}
        for(int i=1;i<11;i++)entityData.set(BENDS.get(i-1),net.minecraft.util.Mth.wrapDegrees(bodyYaw[i]-bodyYaw[i-1]));
        refreshSections();}
    public float bodyJointAngle(int i,float partial){
        if(!bodyInitialized)return 0;
        float before=net.minecraft.util.Mth.wrapDegrees(oldBodyYaw[i]-oldBodyYaw[i-1]);
        float now=net.minecraft.util.Mth.wrapDegrees(bodyYaw[i]-bodyYaw[i-1]);
        return before+net.minecraft.util.Mth.wrapDegrees(now-before)*partial;
    }
    private Vec3 segmentCenter(Vec3 at,float yaw,int index){
        double r=Math.toRadians(yaw);Vec3 p=at.add(-38.0/16*Math.sin(r),0,38.0/16*Math.cos(r));
        for(int i=1;i<=index;i++){
            double angle=Math.toRadians(yaw+(bodyInitialized?net.minecraft.util.Mth.wrapDegrees(bodyYaw[i-1]-bodyYaw[0]):0));
            double side=-6*(Math.sin(i*.61)-Math.sin((i-1)*.61))/16,ahead=-9.5/16;
            p=p.add(side*Math.cos(angle)-ahead*Math.sin(angle),0,side*Math.sin(angle)+ahead*Math.cos(angle));
        }return p;
    }
    public void refreshSections(){if(sections==null)return;for(var p:sections){
        double x=p.getX(),y=p.getY(),z=p.getZ();var next=p.center(position(),getYRot());
        // Parts are not independently ticked. F3+B interpolates xOld/yOld/zOld,
        // not xo/yo/zo. Seed both histories on spawn and after a teleport.
        if(!p.positionInitialized||next.distanceToSqr(p.position())>64){x=next.x;y=next.y;z=next.z;}
        p.setPos(next);p.xo=p.xOld=x;p.yo=p.yOld=y;p.zo=p.zOld=z;p.positionInitialized=true;
    }}
    @Override public boolean canCollideWith(Entity other){return !(other instanceof Section section&&section.getParent()==this)&&super.canCollideWith(other);}
    @Override public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(6,1,6);}
    @Override public boolean isMultipartEntity(){return true;}
    @Override public PartEntity<?>[] getParts(){return sections;}
    @Override public void setId(int id){super.setId(id);if(sections!=null)for(int i=0;i<sections.length;i++)sections[i].setId(id+i+1);}
    @Override public void addAdditionalSaveData(CompoundTag n){super.addAdditionalSaveData(n);if(home!=null)n.putLong("RootHome",home.asLong());}
    @Override public void readAdditionalSaveData(CompoundTag n){super.readAdditionalSaveData(n);if(n.contains("RootHome"))home=BlockPos.of(n.getLong("RootHome"));entityData.set(PHASE,Phase.CAMOUFLAGED.ordinal());entityData.set(SINCE,level().getGameTime());spent=true;observed=null;setTarget(null);}
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){return null;}
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource s){return SoundEvents.WOOD_HIT;}
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound(){return SoundEvents.WOOD_BREAK;}
    public static final class Section extends PartEntity<Lozhnokoren>{
        private boolean positionInitialized;
        public final String name;private final double side,ahead;private final float width,height,bottom;
        Section(Lozhnokoren owner,String name,double side,double ahead,float width,float height,float bottom){super(owner);this.name=name;this.side=side;this.ahead=ahead;this.width=width;this.height=height;this.bottom=bottom;refreshDimensions();}
        Vec3 center(Vec3 at,float yaw){
            if(name.startsWith("segment_"))return getParent().segmentCenter(at,yaw,Integer.parseInt(name.substring(8))).add(0,bottom,0);
            if(name.equals("tail")){
                var owner=getParent();double r=Math.toRadians(yaw+(owner.bodyInitialized?net.minecraft.util.Mth.wrapDegrees(owner.bodyYaw[10]-owner.bodyYaw[0]):0));
                double dx=-side+6*Math.sin(10*.61)/16,dz=ahead-(38-10*9.5)/16;
                return owner.segmentCenter(at,yaw,10).add(dx*Math.cos(r)-dz*Math.sin(r),bottom,dx*Math.sin(r)+dz*Math.cos(r));
            }
            double r=Math.toRadians(yaw);return at.add(side*Math.cos(r)-ahead*Math.sin(r),bottom,side*Math.sin(r)+ahead*Math.cos(r));}
        AABB box(Vec3 at,float yaw){var c=center(at,yaw);return new AABB(c.x-width/2,c.y,c.z-width/2,c.x+width/2,c.y+height,c.z+width/2);}
        @Override public EntityDimensions getDimensions(Pose pose){return EntityDimensions.scalable(width,height);}
        @Override protected void defineSynchedData(SynchedEntityData.Builder b){}
        @Override protected void readAdditionalSaveData(CompoundTag n){}
        @Override protected void addAdditionalSaveData(CompoundTag n){}
        @Override public boolean shouldBeSaved(){return false;}
        @Override public boolean isPickable(){return getParent().isAlive();}
        @Override public boolean canBeCollidedWith(){return getParent().isAlive();}
        @Override public boolean canCollideWith(Entity e){return e!=getParent()&&!(e instanceof Section p&&p.getParent()==getParent())&&super.canCollideWith(e);}
        @Override public boolean is(Entity other){return other==this||other==getParent();}
        @Override public boolean hurt(DamageSource source,float amount){return getParent().hurt(source,amount*(name.equals("head")?1.25f:1));}
    }
}
