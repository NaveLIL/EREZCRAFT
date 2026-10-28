package pro.erez.interstice.fauna;

import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.*;
import net.minecraft.world.phys.*;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import net.tslat.smartbrainlib.api.core.*;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;

/** SmartBrainLib hunter. Entity owns presentation and physical perception only. */
public final class VaultHunter extends Monster implements SmartBrainOwner<VaultHunter> {
    public enum Phase { IDLE, INVESTIGATE, CHASE, SEARCH, AMBUSH, RETREAT, COOLDOWN }
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(VaultHunter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STRIKE_END = SynchedEntityData.defineId(VaultHunter.class, EntityDataSerializers.LONG);
    public static final int STRIKE_DURATION = 14, STRIKE_WINDUP = 7;
    private final DynamicGameEventListener<NoiseListener> listener = new DynamicGameEventListener<>(new NoiseListener());
    private boolean brainPaused;
    public VaultHunter(EntityType<? extends VaultHunter> type, Level level) { super(type, level); xpReward = 8; }
    public static AttributeSupplier.Builder createHunterAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 36).add(Attributes.MOVEMENT_SPEED, .30)
                .add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.STEP_HEIGHT, 1);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(PHASE, Phase.IDLE.ordinal()); builder.define(STRIKE_END, -1L);
    }
    @Override protected void registerGoals() {} // Brain is the sole movement/target owner.
    @Override protected PathNavigation createNavigation(Level level) { return new HunterNavigation(this,level); }
    @Override protected Brain.Provider<VaultHunter> brainProvider() { return new SmartBrainProvider<>(this); }
    @Override protected Brain<?> makeBrain(Dynamic<?> data) { return brainProvider().makeBrain(data); }
    @Override @SuppressWarnings("unchecked") public Brain<VaultHunter> getBrain() { return (Brain<VaultHunter>)super.getBrain(); }
    @Override public List<? extends ExtendedSensor<? extends VaultHunter>> getSensors() { return HunterBrain.sensors(); }
    @Override public BrainActivityGroup<VaultHunter> getCoreTasks() { return HunterBrain.core(); }
    @Override public BrainActivityGroup<VaultHunter> getIdleTasks() { return HunterBrain.idle(); }
    @Override public BrainActivityGroup<VaultHunter> getFightTasks() { return HunterBrain.fight(); }
    @Override protected void customServerAiStep() { super.customServerAiStep(); tickBrain(this); }
    @Override public LivingEntity getTarget() { return getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null); }
    @Override public void setTarget(LivingEntity target) {
        // External targeting requests also obey the hunter's perception rules.
        if(target instanceof Player player&&perceives(player)) HunterBrain.see(this,player);
        else getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }
    public Phase phase() { return Phase.values()[entityData.get(PHASE)]; }
    void phase(Phase phase) { entityData.set(PHASE, phase.ordinal()); }
    public Vec3 lastKnownPosition() { return HunterBrain.observed(this); }
    public int memoryRemaining() { return (int)getBrain().getTimeUntilExpiry(HunterBrain.OBSERVATION.get()); }
    public float attackProgress(float partialTick) {
        long end = entityData.get(STRIKE_END);
        double remaining = end - level().getGameTime() - partialTick;
        return end < 0 || remaining <= 0 || remaining > STRIKE_DURATION ? 0 : (float)(1-remaining/STRIKE_DURATION);
    }
    public boolean isPreparingStrike() {
        long remaining=entityData.get(STRIKE_END)-level().getGameTime();
        return remaining>STRIKE_DURATION-STRIKE_WINDUP&&remaining<=STRIKE_DURATION;
    }
    void beginStrike() {
        entityData.set(STRIKE_END,level().getGameTime()+STRIKE_DURATION);
        playSound(HunterSounds.WINDUP.get(),.45F,1);
    }
    void cancelStrike() { if(entityData.get(STRIKE_END)!=-1L)entityData.set(STRIKE_END,-1L); }
    @Override public void tick() {
        super.tick();
        if(level().isClientSide)return;
        if(!isAlive()||isNoAi()) {
            if(!brainPaused){getBrain().stopAll((ServerLevel)level(),this);getNavigation().stop();brainPaused=true;}
            cancelStrike();
        } else brainPaused=false;
    }
    boolean eligible(Player p) {
        return p != null && p.isAlive() && !p.isRemoved() && !p.isCreative() && !p.isSpectator()
                && p.level() == level() && !isAlliedTo(p) && canAttack(p) && distanceToSqr(p) <= 24*24;
    }
    boolean perceives(Player p) {
        return eligible(p) && !p.isInvisible() && distanceToSqr(p) <= (p.isShiftKeyDown() ? 8*8 : 20*20)
                && getSensing().hasLineOfSight(p);
    }
    @Override public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> consumer) {
        if (level() instanceof ServerLevel server) consumer.accept(listener, server);
    }
    private final class NoiseListener implements GameEventListener {
        private final PositionSource source = new EntityPositionSource(VaultHunter.this, getEyeHeight());
        @Override public PositionSource getListenerSource() { return source; }
        @Override public int getListenerRadius() { return 12; }
        @Override public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 place) {
            Entity cause = context.sourceEntity();
            boolean projectile = event == GameEvent.PROJECTILE_LAND && cause instanceof Projectile p && p.getOwner() instanceof Player owner && eligible(owner);
            boolean player = cause instanceof Player p && eligible(p);
            if (!projectile && !player) return false;
            if (event != GameEvent.STEP && event != GameEvent.BLOCK_DESTROY && event != GameEvent.BLOCK_PLACE
                    && event != GameEvent.PROJECTILE_LAND && event != GameEvent.EXPLODE) return false;
            if (event == GameEvent.STEP && cause.isSteppingCarefully()) return false;
            if (level.getBlockState(BlockPos.containing(place).below()).is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)) return false;
            double distance = position().distanceToSqr(place);
            if (distance > 144) return false;
            var hit = level.clip(new ClipContext(getEyePosition(), place, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, VaultHunter.this));
            if (hit.getType() != HitResult.Type.MISS && (distance > 36 || level.getBlockState(hit.getBlockPos()).is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))) return false;
            // A landed snowball can pull an investigating hunter away from the real player.
            HunterBrain.hear(VaultHunter.this,Vec3.atBottomCenterOf(BlockPos.containing(place)));
            return true;
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        boolean result=super.hurt(source,amount);
        if(result&&!level().isClientSide&&source.getEntity() instanceof Player player&&perceives(player)
                &&phase()!=Phase.RETREAT&&phase()!=Phase.COOLDOWN)HunterBrain.see(this,player);
        return result;
    }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return HunterSounds.AMBIENT.get(); }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) { return HunterSounds.HURT.get(); }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return HunterSounds.DEATH.get(); }
    @Override protected float getSoundVolume() { return .35F; }
    @Override public int getAmbientSoundInterval() { return 280; }
}
