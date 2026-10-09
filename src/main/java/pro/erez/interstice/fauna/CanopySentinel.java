package pro.erez.interstice.fauna;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;
import pro.erez.interstice.worldgen.GardenMaterials;

/** Territorial crown resident, with a visible warning and a peaceful crouching alternative.
 * Uses ordinary gravity/ground navigation. It does not promise navigation along every branch. */
public final class CanopySentinel extends PathfinderMob {
    public enum Phase { CALM, WARNING, CHASE, RETURN, COOLDOWN }
    public static final int HOME_RADIUS=20,CHASE_RADIUS=24,WARNING_TICKS=24,MIN_TARGET_HOLD=40;
    public static final int MAX_CHASE_TICKS=160,COOLDOWN_TICKS=200;
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(CanopySentinel.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WARNING=SynchedEntityData.defineId(CanopySentinel.class,EntityDataSerializers.INT);
    private BlockPos home;
    private String homeDimension="";
    private UUID selectedTarget;
    private boolean canopyResident;
    private int targetHold,chaseElapsed,returnElapsed,cooldownRemaining,attackCooldown,noPathTicks;

    public CanopySentinel(EntityType<? extends CanopySentinel> type,Level level){
        super(type,level);setPersistenceRequired();xpReward=2;
    }
    public static AttributeSupplier.Builder createAttributes(){
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH,12).add(Attributes.MOVEMENT_SPEED,.27)
            .add(Attributes.ATTACK_DAMAGE,2).add(Attributes.FOLLOW_RANGE,24).add(Attributes.STEP_HEIGHT,1);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(PHASE,Phase.CALM.ordinal());builder.define(WARNING,0);}
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(8,new RandomLookAroundGoal(this));
    }
    public Phase phase(){return Phase.values()[Math.clamp(entityData.get(PHASE),0,Phase.values().length-1)];}
    public BlockPos home(){return home==null?blockPosition():home;}
    public int warningTicksRemaining(){return entityData.get(WARNING);}
    public int cooldownTicksRemaining(){return cooldownRemaining;}
    public float warningProgress(float partialTick){return phase()==Phase.WARNING?Math.clamp(1-(warningTicksRemaining()-partialTick)/WARNING_TICKS,0,1):0;}
    public boolean isCanopyResident(){return canopyResident;}
    @Override public boolean checkSpawnRules(LevelAccessor accessor,MobSpawnType kind){
        if(!(accessor instanceof ServerLevel server)||!RealmFauna.isV6(server)||!validCrownSupport())return false;
        return server.getBlockState(blockPosition()).isAir()&&server.getBlockState(blockPosition().above()).isAir();
    }
    /** The home is the feet position immediately above the real crown leaf, not the leaf itself. */
    public void initializeHome(BlockPos position){
        home=position.immutable();homeDimension=level().dimension().location().toString();canopyResident=validCrownSupport();restrictTo(home,HOME_RADIUS);
        selectedTarget=null;setTarget(null);targetHold=chaseElapsed=returnElapsed=cooldownRemaining=noPathTicks=0;setPhase(Phase.CALM);setPersistenceRequired();
    }
    private void setPhase(Phase next){entityData.set(PHASE,next.ordinal());if(next!=Phase.WARNING)entityData.set(WARNING,0);}
    private boolean validCrownSupport(){
        if(home==null||!level().hasChunkAt(home.below()))return false;
        var support=level().getBlockState(home.below());return support.is(GardenMaterials.CROWN_LEAVES.get())&&support.getValue(LeavesBlock.DISTANCE)<7;
    }
    private boolean eligible(Player player){
        return player!=null&&player.isAlive()&&!player.isRemoved()&&!player.isCreative()&&!player.isSpectator()&&!player.isShiftKeyDown()
            &&player.level()==level()&&player.distanceToSqr(Vec3.atBottomCenterOf(home()))<=CHASE_RADIUS*CHASE_RADIUS;
    }
    private boolean intruder(Player player){
        return eligible(player)&&Math.abs(player.getY()-home().getY())<=8
            &&player.distanceToSqr(Vec3.atBottomCenterOf(home()))<=HOME_RADIUS*HOME_RADIUS&&getSensing().hasLineOfSight(player);
    }
    private Player candidate(){
        return level().players().stream().filter(this::intruder)
            .min(Comparator.comparingDouble((Player p)->distanceToSqr(p)).thenComparing(p->p.getUUID().toString())).orElse(null);
    }
    private void warn(Player player){
        selectedTarget=player.getUUID();setTarget(player);targetHold=chaseElapsed=noPathTicks=0;navigation.stop();
        setPhase(Phase.WARNING);entityData.set(WARNING,WARNING_TICKS);getLookControl().setLookAt(player,40,40);
        level().playSound(null,blockPosition(),SoundEvents.FOX_SCREECH,SoundSource.NEUTRAL,.65F,1.35F);
    }
    /** Called only after a real ripe pod has become an unripe pod; it never changes fruit/loot. */
    public void observeFruitHarvest(Player player,BlockPos fruit){
        if(level().isClientSide||!canopyResident||!intruder(player)||home==null||fruit.distSqr(home)>12*12
            ||player.distanceToSqr(Vec3.atCenterOf(fruit))>5*5||!level().hasChunkAt(fruit)
            ||!level().getBlockState(fruit).is(GardenMaterials.CROWN_FRUIT.get())||phase()==Phase.COOLDOWN||phase()==Phase.RETURN)return;
        if(phase()==Phase.CALM)warn(player);
        // A second harvest cannot switch a valid active target or restart its warning.
    }
    private void returnHome(){
        selectedTarget=null;setTarget(null);navigation.stop();setPhase(Phase.RETURN);returnElapsed=noPathTicks=0;cooldownRemaining=COOLDOWN_TICKS;
        var speed=getDeltaMovement();setDeltaMovement(speed.x*.65,speed.y,speed.z*.65);
    }
    private void rest(){selectedTarget=null;setTarget(null);navigation.stop();setPhase(Phase.COOLDOWN);cooldownRemaining=COOLDOWN_TICKS;}
    @Override protected void customServerAiStep(){
        super.customServerAiStep();if(!(level() instanceof ServerLevel server))return;if(home==null)initializeHome(blockPosition());
        if(!homeDimension.equals(level().dimension().location().toString())){
            // Transport never causes navigation towards unrelated coordinates in another realm.
            initializeHome(blockPosition());canopyResident=false;rest();return;
        }
        if(attackCooldown>0)attackCooldown--;
        if(canopyResident&&tickCount%10==0&&!validCrownSupport()){
            canopyResident=false;var ground=lowerGround(server);if(ground!=null){home=ground;restrictTo(home,HOME_RADIUS);}returnHome();
        }
        if(phase()==Phase.WARNING||phase()==Phase.CHASE){
            var player=selectedTarget==null?null:server.getPlayerByUUID(selectedTarget);
            if(!eligible(player)||distanceToSqr(Vec3.atBottomCenterOf(home))>CHASE_RADIUS*CHASE_RADIUS){returnHome();return;}
            setTarget(player);targetHold++;getLookControl().setLookAt(player,40,40);
            if(targetHold>=MIN_TARGET_HOLD&&tickCount%20==0){
                var challenger=candidate();if(challenger!=null&&challenger!=player&&distanceToSqr(challenger)<distanceToSqr(player)*.4){warn(challenger);return;}
            }
            if(phase()==Phase.WARNING){
                navigation.stop();int left=warningTicksRemaining();entityData.set(WARNING,Math.max(0,left-1));
                if(left<=1){setPhase(Phase.CHASE);chaseElapsed=0;}return;
            }
            if(++chaseElapsed>MAX_CHASE_TICKS){returnHome();return;}
            if(distanceToSqr(player)<=2.25&&getSensing().hasLineOfSight(player)){
                navigation.stop();noPathTicks=0;if(attackCooldown==0){doHurtTarget(player);attackCooldown=20;}
            }else if(tickCount%10==0){
                boolean moving=navigation.moveTo(player,1.15);noPathTicks=moving?0:noPathTicks+10;
                if(noPathTicks>=40)returnHome();
            }
            return;
        }
        if(phase()==Phase.RETURN){
            returnElapsed++;
            if(distanceToSqr(Vec3.atBottomCenterOf(home))<4&&onGround()){rest();return;}
            if(tickCount%10==0)navigation.moveTo(home.getX()+.5,home.getY(),home.getZ()+.5,.9);
            // When an inaccessible crown has been cut or no branch path exists, land naturally
            // and settle into a quiet ground resident. No teleport, flying flag or gravity change.
            if(returnElapsed>=120&&onGround()){
                var below=level().getBlockState(blockPosition().below());
                if(below.getFluidState().isEmpty()&&!below.is(Blocks.BEDROCK)){
                    home=blockPosition();restrictTo(home,HOME_RADIUS);canopyResident=validCrownSupport();rest();
                }
            }
            return;
        }
        if(phase()==Phase.COOLDOWN){if(cooldownRemaining>0)cooldownRemaining--;else setPhase(Phase.CALM);return;}
        if(canopyResident&&tickCount%10==0){var player=candidate();if(player!=null){warn(player);return;}}
        if(tickCount%100==0&&navigation.isDone()){
            int x=home.getX()+random.nextInt(9)-4,z=home.getZ()+random.nextInt(9)-4;
            var point=new BlockPos(x,home.getY(),z);
            if(server.getChunkSource().getChunkNow(x>>4,z>>4)!=null&&server.getBlockState(point).isAir()
                &&server.getBlockState(point.above()).isAir()&&server.getBlockState(point.below()).isFaceSturdy(server,point.below(),Direction.UP))navigation.moveTo(x+.5,home.getY(),z+.5,.65);
        }
    }
    private BlockPos lowerGround(ServerLevel level){
        for(int[] offset:new int[][]{{0,0},{4,0},{-4,0},{0,4},{0,-4}}){
            int x=home.getX()+offset[0],z=home.getZ()+offset[1];if(level.getChunkSource().getChunkNow(x>>4,z>>4)==null)continue;
            for(int y=home.getY()-2;y>=Math.max(level.getMinBuildHeight()+1,home.getY()-64);y--){
                var p=new BlockPos(x,y,z);var s=level.getBlockState(p);
                if(s.getBlock() instanceof LeavesBlock||s.is(Blocks.BEDROCK)||!s.getFluidState().isEmpty()||!s.isFaceSturdy(level,p,Direction.UP))continue;
                if(level.getBlockState(p.above()).isAir()&&level.getBlockState(p.above(2)).isAir())return p.above();
            }
        }
        return null;
    }
    @Override public boolean hurt(DamageSource source,float amount){
        boolean result=super.hurt(source,amount);
        if(result&&!level().isClientSide&&source.getEntity() instanceof Player player&&intruder(player)&&phase()==Phase.CALM)warn(player);
        return result;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);tag.putBoolean("HasCanopyHome",home!=null);if(home!=null)tag.putLong("CanopyHome",home.asLong());
        tag.putString("CanopyHomeDimension",homeDimension);tag.putBoolean("CanopyResident",canopyResident);tag.putInt("CanopyPhase",phase().ordinal());
        if(selectedTarget!=null)tag.putUUID("CanopyTarget",selectedTarget);tag.putInt("WarningRemaining",warningTicksRemaining());tag.putInt("TargetHold",targetHold);
        tag.putInt("ChaseElapsed",chaseElapsed);tag.putInt("ReturnElapsed",returnElapsed);tag.putInt("CooldownRemaining",cooldownRemaining);tag.putInt("AttackCooldown",attackCooldown);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);home=tag.getBoolean("HasCanopyHome")?BlockPos.of(tag.getLong("CanopyHome")):blockPosition();
        homeDimension=tag.getString("CanopyHomeDimension");if(homeDimension.isEmpty())homeDimension=level().dimension().location().toString();
        canopyResident=tag.getBoolean("CanopyResident");int ordinal=tag.getInt("CanopyPhase");setPhase(ordinal>=0&&ordinal<Phase.values().length?Phase.values()[ordinal]:Phase.CALM);
        selectedTarget=tag.hasUUID("CanopyTarget")?tag.getUUID("CanopyTarget"):null;setTarget(null);
        entityData.set(WARNING,phase()==Phase.WARNING?Math.clamp(tag.getInt("WarningRemaining"),0,WARNING_TICKS):0);targetHold=Math.clamp(tag.getInt("TargetHold"),0,MIN_TARGET_HOLD);
        chaseElapsed=Math.clamp(tag.getInt("ChaseElapsed"),0,MAX_CHASE_TICKS);returnElapsed=Math.clamp(tag.getInt("ReturnElapsed"),0,120);
        cooldownRemaining=Math.clamp(tag.getInt("CooldownRemaining"),0,COOLDOWN_TICKS);attackCooldown=Math.clamp(tag.getInt("AttackCooldown"),0,20);restrictTo(home,HOME_RADIUS);
        if(((phase()==Phase.WARNING||phase()==Phase.CHASE)&&selectedTarget==null)||(phase()==Phase.WARNING&&warningTicksRemaining()==0))returnHome();
    }
    @Override protected SoundEvent getAmbientSound(){return SoundEvents.FOX_AMBIENT;}
    @Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.FOX_HURT;}
    @Override protected SoundEvent getDeathSound(){return SoundEvents.FOX_DEATH;}
}
