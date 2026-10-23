package pro.erez.interstice.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import net.tslat.smartbrainlib.api.core.*;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import com.azure.azurecortex.navigation.crawl.CrawlCapability;
import com.azure.azurecortex.navigation.movement.NavigationQueries;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

public final class CaveRiftSpiderEntity extends Monster implements RangedAttackMob, SmartBrainOwner<CaveRiftSpiderEntity>, CrawlCapability {
    public static final ResourceKey<DamageType> SPIDER_BITE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Interstice.ID, "spider_bite"));
    public static final ResourceKey<DamageType> SPIDER_SPIT =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Interstice.ID, "spider_spit"));

    private static final EntityDataAccessor<Byte> DATA_FLAGS =
            SynchedEntityData.defineId(CaveRiftSpiderEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_VARIANT =
            SynchedEntityData.defineId(CaveRiftSpiderEntity.class, EntityDataSerializers.BYTE);

    public final AnimationState idleAnimationState = new AnimationState();
    private int burstTicks = 0;
    private static final EntityDataAccessor<Byte> DATA_SURFACE =
            SynchedEntityData.defineId(CaveRiftSpiderEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> CRAWL_FORWARD_X=SynchedEntityData.defineId(CaveRiftSpiderEntity.class,EntityDataSerializers.FLOAT),
            CRAWL_FORWARD_Y=SynchedEntityData.defineId(CaveRiftSpiderEntity.class,EntityDataSerializers.FLOAT),
            CRAWL_FORWARD_Z=SynchedEntityData.defineId(CaveRiftSpiderEntity.class,EntityDataSerializers.FLOAT);
    private java.util.List<SpiderSurfacePath.Step> surfacePath = java.util.List.of();
    private int surfaceStep, detachTicks, nextSurfacePlan, nextSurfaceSpitAttempt, nextSpitTick;
    private Vec3 surfaceGoal;
    private boolean needsBodyFit = true;
    private boolean brainPaused;
    private int crawlGrace;
    private Vec3 oldCrawlForward=new Vec3(0,0,1);
    private Direction previousSurface = Direction.DOWN;
    private float surfaceTransition = 1, oldSurfaceTransition = 1;

    @Override public EntityDimensions getDefaultDimensions(Pose pose) {
        // A stable volume in every orientation; the visual body is ~1.5*scale tall.
        float size = 2.0F * getVariant().scale;
        return EntityDimensions.scalable(size, size).withEyeHeight(size * .7F);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_VARIANT.equals(key)) refreshDimensions();
        if (DATA_SURFACE.equals(key)) {
            previousSurface = visualSurface;
            visualSurface = getSurface();
            surfaceTransition = oldSurfaceTransition = 0;
        }
    }
    private Direction visualSurface = Direction.DOWN;
    public Direction getSurface() { return Direction.from3DDataValue(entityData.get(DATA_SURFACE)); }
    public Direction previousSurface() { return previousSurface; }
    public float surfaceTransition(float partial) { return Mth.lerp(partial, oldSurfaceTransition, surfaceTransition); }
    private void attach(Direction surface) {
        entityData.set(DATA_SURFACE, (byte)surface.get3DDataValue());
        setClimbingCeiling(surface == Direction.UP);
        setClimbingWall(surface.getAxis().isHorizontal());
    }
    public void detachFromSurface() {
        detachTicks = 16;
        surfacePath = java.util.List.of(); surfaceGoal = null;
        attach(Direction.DOWN);
    }
    private boolean support(Direction face) {
        AABB bb = getBoundingBox().deflate(.08);
        double reach = .18;
        AABB probe = switch (face) {
            case DOWN -> new AABB(bb.minX, getBoundingBox().minY - reach, bb.minZ, bb.maxX, getBoundingBox().minY + .01, bb.maxZ);
            case UP -> new AABB(bb.minX, getBoundingBox().maxY - .01, bb.minZ, bb.maxX, getBoundingBox().maxY + reach, bb.maxZ);
            case EAST -> new AABB(getBoundingBox().maxX - .01, bb.minY, bb.minZ, getBoundingBox().maxX + reach, bb.maxY, bb.maxZ);
            case WEST -> new AABB(getBoundingBox().minX - reach, bb.minY, bb.minZ, getBoundingBox().minX + .01, bb.maxY, bb.maxZ);
            case SOUTH -> new AABB(bb.minX, bb.minY, getBoundingBox().maxZ - .01, bb.maxX, bb.maxY, getBoundingBox().maxZ + reach);
            case NORTH -> new AABB(bb.minX, bb.minY, getBoundingBox().minZ - reach, bb.maxX, bb.maxY, getBoundingBox().minZ + .01);
        };
        if (!level().hasChunksAt(BlockPos.containing(probe.minX, probe.minY, probe.minZ), BlockPos.containing(probe.maxX, probe.maxY, probe.maxZ))) return false;
        return !level().noCollision(this, probe);
    }
    /** A real route request, also used by isolated traversal checks. It never teleports the spider. */
    public boolean crawlTo(Vec3 bodyCenter) {
        var path = new AzureSpiderNavigation(this).find(bodyCenter);
        if (path.isEmpty()) return false;
        surfaceGoal = bodyCenter; surfacePath = path; surfaceStep = 0;
        getNavigation().stop();
        return true;
    }
    public boolean hasSurfaceRoute() { return surfaceStep < surfacePath.size(); }
    @Override public void calculateEntityAnimation(boolean includeY) {
        super.calculateEntityAnimation(includeY || isClimbingWall() || isClimbingCeiling());
    }
    private static final java.util.Map<Level, SurfacePlanBudget> SURFACE_PLAN_TICK = new java.util.WeakHashMap<>();
    public boolean surfaceControlNeeded() {
            if (detachTicks > 0 || isInWater() || isInLava()) return false;
            if (hasSurfaceRoute() || isClimbingCeiling() || isClimbingWall()) return true;
            return tickCount >= nextSurfacePlan && (hasCeilingAbove() && !onGround() || horizontalCollision
                    || getTarget() != null && getTarget().isAlive() && distanceToSqr(getTarget()) > 16 && getTarget().getY() > getY()+1.5 && getNavigation().isDone()
                    || getVariant()==Variant.LURKER && !isBursting() && getTarget()==null);
        }
    public void tickSurfaceControl() {
            getNavigation().stop();
            if (tickCount < nextSurfacePlan || hasSurfaceRoute()) return;
            if (!SURFACE_PLAN_TICK.computeIfAbsent(level(), ignored -> new SurfacePlanBudget()).tryAcquire(getId(), level().getGameTime())) return;
            nextSurfacePlan = tickCount + 35 + getId() % 10;
            LivingEntity target = getTarget();
            Vec3 goal = SpiderBrain.observed(this);
            if(goal==null)goal=surfaceGoal;
            if (target != null && getSensing().hasLineOfSight(target))
                goal = new Vec3(target.getX(), getY() + getBbHeight()/2, target.getZ());
            // Reach the underside via a wall, instead of pushing vertically into the roof forever.
            if (!isClimbingCeiling()) {
                BlockPos base = blockPosition();
                for (int dy = 1; dy <= 12; dy++) {
                    BlockPos pos = base.above(dy);
                    if (!level().hasChunkAt(pos)) break;
                    if (level().getBlockState(pos).isFaceSturdy(level(), pos, Direction.DOWN)) {
                        Vec3 roof = new Vec3(goal == null ? getX() : goal.x, pos.getY()-getBbHeight()/2, goal == null ? getZ() : goal.z);
                        if (!crawlTo(roof)) crawlTo(new Vec3(getX(), roof.y, getZ()));
                        return;
                    }
                }
            } else if (goal != null) crawlTo(goal);
    }

    public enum Variant {
        SKIRMISHER(0, "skirmisher", 16.0, 0.36, 5.0, 0.60F),
        LURKER(1, "lurker", 30.0, 0.20, 8.0, 0.85F),
        SPITTER(2, "spitter", 14.0, 0.28, 3.0, 0.55F);

        public final int id;
        public final String name;
        public final double maxHealth;
        public final double speed;
        public final double attackDamage;
        public final float scale;

        Variant(int id, String name, double maxHealth, double speed, double attackDamage, float scale) {
            this.id = id;
            this.name = name;
            this.maxHealth = maxHealth;
            this.speed = speed;
            this.attackDamage = attackDamage;
            this.scale = scale;
        }

        public static Variant byId(int id) {
            for (Variant v : values()) {
                if (v.id == id) return v;
            }
            return SKIRMISHER;
        }
    }

    public CaveRiftSpiderEntity(EntityType<? extends CaveRiftSpiderEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS, (byte) 0);
        builder.define(DATA_VARIANT, (byte) Variant.SKIRMISHER.id);
        builder.define(DATA_SURFACE, (byte) Direction.DOWN.get3DDataValue());
        builder.define(CRAWL_FORWARD_X,0F);builder.define(CRAWL_FORWARD_Y,0F);builder.define(CRAWL_FORWARD_Z,1F);
    }

    public Variant getVariant() {
        return Variant.byId(this.entityData.get(DATA_VARIANT));
    }

    public void setVariant(Variant variant) {
        applyVariant(variant, true);
    }

    private void applyVariant(Variant variant, boolean initializeHealth) {
        this.entityData.set(DATA_VARIANT, (byte) variant.id);
        this.reapplyAttributes(initializeHealth);
        if (initializeHealth) needsBodyFit = true;
    }

    private void reapplyAttributes(boolean initializeHealth) {
        Variant v = getVariant();
        var healthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(v.maxHealth);
        var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(isBursting() ? v.speed * 2.7 : v.speed);
        var damageAttr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) damageAttr.setBaseValue(v.attackDamage);
        // New spawn variants start healthy; loading a wounded saved entity must not heal it.
        this.setHealth(initializeHealth ? (float) v.maxHealth : Math.min(this.getHealth(), (float) v.maxHealth));
    }

    public boolean isClimbingWall() {
        return (this.entityData.get(DATA_FLAGS) & 1) != 0;
    }

    public void setClimbingWall(boolean climbing) {
        byte b = this.entityData.get(DATA_FLAGS);
        this.entityData.set(DATA_FLAGS, climbing ? (byte) (b | 1) : (byte) (b & ~1));
    }

    public boolean isClimbingCeiling() {
        return (this.entityData.get(DATA_FLAGS) & 2) != 0;
    }

    public void setClimbingCeiling(boolean climbing) {
        byte b = this.entityData.get(DATA_FLAGS);
        this.entityData.set(DATA_FLAGS, climbing ? (byte) (b | 2) : (byte) (b & ~2));
    }

    public boolean isBursting() {
        return (this.entityData.get(DATA_FLAGS) & 4) != 0;
    }

    public void setBursting(boolean burst) {
        byte b = this.entityData.get(DATA_FLAGS);
        this.entityData.set(DATA_FLAGS, burst ? (byte) (b | 4) : (byte) (b & ~4));
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(burst ? getVariant().speed * 2.7 : getVariant().speed);
        }
    }

    public void triggerAmbushBurst() {
        if (getVariant() == Variant.LURKER && !isBursting() && SpiderBrain.canBurst(this)) {
            SpiderBrain.burstStarted(this);
            this.burstTicks = 160; // 8 seconds burst sprint
            this.setBursting(true);
            this.playSound(SoundEvents.SPIDER_HURT, 1.3F, 1.6F);
            if (this.getTarget() != null) {
                performAmbushLunge(this.getTarget());
            }
        }
    }

    public void performAmbushLunge(LivingEntity target) {
        if (target == null) return;
        Vec3 toTarget = target.position().subtract(this.position());
        double dist = toTarget.horizontalDistance();
        Vec3 dir = dist > 0.001 ? new Vec3(toTarget.x / dist, 0, toTarget.z / dist) : this.getLookAngle();

        // Explosive forward and slight upward impulse
        double power = Math.min(1.30, Math.max(0.85, dist * 0.28));
        double up = this.onGround() ? 0.36 : 0.15;
        this.setDeltaMovement(dir.x * power, up, dir.z * power);
        this.hasImpulse = true;

        this.getNavigation().moveTo(target, 1.35);
        this.playSound(SoundEvents.SPIDER_HURT, 1.1F, 1.4F);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    this.getX() + dir.x * 0.5, this.getY() + 0.3, this.getZ() + dir.z * 0.5,
                    1, 0, 0, 0, 0);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    this.getX(), this.getY() + 0.2, this.getZ(),
                    8, 0.3, 0.2, 0.3, 0.15);
        }
    }

    public void alertPack(LivingEntity target) { SpiderBrain.alert(this,target); }

    @Override protected Brain.Provider<CaveRiftSpiderEntity> brainProvider(){return new SmartBrainProvider<>(this);}
    @Override protected Brain<?> makeBrain(Dynamic<?> data){return brainProvider().makeBrain(data);}
    @Override @SuppressWarnings("unchecked") public Brain<CaveRiftSpiderEntity> getBrain(){return (Brain<CaveRiftSpiderEntity>)super.getBrain();}
    @Override public java.util.List<? extends ExtendedSensor<? extends CaveRiftSpiderEntity>> getSensors(){return SpiderBrain.sensors();}
    @Override public BrainActivityGroup<CaveRiftSpiderEntity> getCoreTasks(){return SpiderBrain.core();}
    @Override public BrainActivityGroup<CaveRiftSpiderEntity> getIdleTasks(){return SpiderBrain.idle();}
    @Override public BrainActivityGroup<CaveRiftSpiderEntity> getFightTasks(){return SpiderBrain.fight();}
    @Override protected void customServerAiStep(){super.customServerAiStep();tickBrain(this);}
    @Override public LivingEntity getTarget(){return getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);}
    public String combatTactic(){return SpiderBrain.tactic(this);}
    public int spitCooldownRemaining(){return Math.max(0,nextSpitTick-tickCount);}
    @Override public boolean isWallCrawling(){return isClimbingWall()||isClimbingCeiling();}
    @Override public void setWallCrawling(boolean value){if(!value)attach(Direction.DOWN);else crawlGrace=4;}
    @Override public int getWallCrawlGraceTicks(){return crawlGrace;}
    @Override public void setWallCrawlGraceTicks(int value){crawlGrace=value;}
    @Override public Vec3 getCrawlForward(){return new Vec3(entityData.get(CRAWL_FORWARD_X),entityData.get(CRAWL_FORWARD_Y),entityData.get(CRAWL_FORWARD_Z));}
    @Override public Vec3 getOldCrawlForward(){return oldCrawlForward;}
    @Override public Vec3 getCrawlUp(){return Vec3.atLowerCornerOf(getSurface().getOpposite().getNormal());}
    @Override public Vec3 getOldCrawlUp(){return Vec3.atLowerCornerOf(previousSurface().getOpposite().getNormal());}
    @Override public double getCrawlDistFromBlock(){return 0;}
    @Override public double getOldCrawlDistFromBlock(){return 0;}
    @Override public void setCrawlOrientation(Vec3 forward,Vec3 up,double distance){
        if(forward.lengthSqr()>.00001){forward=forward.normalize();entityData.set(CRAWL_FORWARD_X,(float)forward.x);entityData.set(CRAWL_FORWARD_Y,(float)forward.y);entityData.set(CRAWL_FORWARD_Z,(float)forward.z);}
        attach(Direction.getNearest(up.x,up.y,up.z).getOpposite());
    }
    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbingWall() || this.isClimbingCeiling();
    }

    public boolean hasCeilingAbove() { return support(Direction.UP); }

    public boolean canTargetEntity(@Nullable LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity instanceof CaveRiftSpiderEntity) return false;
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (this.getVariant() == Variant.LURKER) {
                return this.isBursting() || this.distanceToSqr(player) <= 12.25
                        || isClimbingCeiling() && player.getY()<getY() && player.getY()>getY()-10
                        && player.position().subtract(position()).horizontalDistanceSqr()<=12.25;
            }
            return true;
        }
        return entity instanceof Bat || entity instanceof IronGolem || entity instanceof Zombie || entity instanceof AbstractSkeleton;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if(target instanceof CaveRiftSpiderEntity)return;
        if(target==null){getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);return;}
        if(SpiderBrain.eligible(this,target))SpiderBrain.acquire(this,target);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof CaveRiftSpiderEntity) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (!isAlive()) { surfacePath = java.util.List.of(); attach(Direction.DOWN); }
        if (!level().isClientSide && isAlive() && detachTicks == 0) {
            if (hasSurfaceRoute()) {
                var next = surfacePath.get(surfaceStep);
                if (!level().hasChunkAt(next.support()) || !level().getBlockState(next.support()).isFaceSturdy(level(), next.support(), next.face().getOpposite())) {
                    detachFromSurface();
                } else {
                    Vec3 center = getBoundingBox().getCenter(), diff = next.center().subtract(center);
                    // Any unexpected displacement cancels the route instead of flying back to it.
                    if (diff.lengthSqr() > 2.3) { detachFromSurface(); super.travel(travelVector); return; }
                    double speed = Math.min(.26, getAttributeValue(Attributes.MOVEMENT_SPEED)*.8);
                    Vec3 step = NavigationQueries.computeWallCrawlVelocity(this,next.center(),speed);
                    if (new SpiderSurfacePath(this).clearSegment(center, center.add(step))) {
                        attach(next.face());
                        resetFallDistance();
                        move(MoverType.SELF, step);
                        Vec3 normal=Vec3.atLowerCornerOf(next.face().getOpposite().getNormal());
                        Vec3 tangent=step.subtract(normal.scale(step.dot(normal)));
                        if(tangent.lengthSqr()>.00001)setCrawlOrientation(tangent,normal,0);
                        setDeltaMovement(Vec3.ZERO);
                        if (getBoundingBox().getCenter().distanceToSqr(next.center()) < .015) surfaceStep++;
                        if (step.horizontalDistanceSqr() > .0001) {
                            setYRot((float)(Mth.atan2(step.z, step.x)*180/Math.PI)-90);
                            yBodyRot = getYRot();
                        }
                        return;
                    }
                    detachFromSurface();
                }
            } else if (getSurface() != Direction.DOWN && support(getSurface())) {
                resetFallDistance(); setDeltaMovement(Vec3.ZERO); return;
            } else if (hasCeilingAbove() && !onGround()) {
                attach(Direction.UP); resetFallDistance(); setDeltaMovement(Vec3.ZERO); return;
            } else attach(Direction.DOWN);
        }
        super.travel(travelVector);
    }

    @Override protected void registerGoals() {} // SmartBrain is the sole action scheduler.

    private void fitResizedBody() {
        if (!needsBodyFit || level().isClientSide) return;
        AABB region = getBoundingBox().inflate(1.5);
        if (!level().hasChunksAt(BlockPos.containing(region.minX, region.minY, region.minZ),
                BlockPos.containing(region.maxX, region.maxY, region.maxZ))) return;
        needsBodyFit = false;
        if (level().noCollision(this, getBoundingBox().deflate(.001))) return;
        var probe = new SpiderSurfacePath(this);
        Vec3 origin = position();
        java.util.List<Vec3> offsets = new java.util.ArrayList<>();
        // Pre-update ceiling saves used a 0.7 block body. Preserve their old top face first.
        offsets.add(new Vec3(0, Mth.clamp(.7-getBbHeight(), -1.5, 1.5), 0));
        java.util.List<Vec3> nearby = new java.util.ArrayList<>();
        for (int x=-3;x<=3;x++) for (int y=-3;y<=3;y++) for (int z=-3;z<=3;z++) {
            Vec3 offset = new Vec3(x*.5,y*.5,z*.5);
            if (offset.lengthSqr()<=2.25) nearby.add(offset);
        }
        nearby.sort(Comparator.comparingDouble(Vec3::lengthSqr)); offsets.addAll(nearby);
        for (Vec3 offset : offsets) {
            Vec3 feet = origin.add(offset);
            if (!probe.clear(feet.add(0,getBbHeight()/2,0))) continue;
            setPos(feet); setDeltaMovement(Vec3.ZERO);
            attach(hasCeilingAbove() ? Direction.UP : Direction.DOWN);
            return;
        }
    }

    @Override
    public void tick() {
        oldCrawlForward=getCrawlForward();
        if(crawlGrace>0)crawlGrace--;
        fitResizedBody();
        oldSurfaceTransition = surfaceTransition;
        surfaceTransition = Math.min(1, surfaceTransition + .2F);
        if (detachTicks > 0) detachTicks--;
        super.tick();

        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
        } else {
            if(!isAlive()||isNoAi()){
                if(!brainPaused){getBrain().stopAll((ServerLevel)level(),this);getNavigation().stop();surfacePath=java.util.List.of();brainPaused=true;}
            }else brainPaused=false;
            // Burst timer countdown & dynamic lunges during burst
            if (this.isBursting()) {
                if (--this.burstTicks <= 0) {
                    this.setBursting(false);
                }
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (this.level() instanceof ServerLevel serverLevel) {
            DamageSource source = new DamageSource(
                    serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(SPIDER_BITE),
                    this,
                    this
            );
            boolean hurt = target.hurt(source, damage);
            if (hurt) {
                if (target instanceof LivingEntity living) {
                    if (this.getVariant() == Variant.SKIRMISHER) {
                        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                    } else if (this.getVariant() == Variant.LURKER) {
                        living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
                        living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 80, 0), this);
                    } else if (this.getVariant() == Variant.SPITTER) {
                        living.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), this);
                    }
                }
                EnchantmentHelper.doPostAttackEffects(serverLevel, target, source);
                this.setLastHurtMob(target);
                this.playSound(SoundEvents.SPIDER_HURT, 1.0F, 1.2F);
            }
            return hurt;
        }
        return super.doHurtTarget(target);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (tickCount < nextSpitTick || !(this.level() instanceof ServerLevel) || this.getVariant() != Variant.SPITTER
                || target == null || !target.isAlive() || target.isRemoved() || target.level() != this.level()
                || target instanceof CaveRiftSpiderEntity || !this.canAttack(target)
                || this.distanceToSqr(target) > 14.0 * 14.0 || !this.getSensing().hasLineOfSight(target)) return;
        ToxinSpitEntity spit = new ToxinSpitEntity(this.level(), this, 0, 0, 0);
        if (!spit.aimAt(target.getBoundingBox().getCenter())) return;
        this.level().addFreshEntity(spit);
        nextSpitTick = tickCount + 38;
        this.playSound(SoundEvents.LLAMA_SPIT, 1.0F, 1.4F);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                       MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        RandomSource r = level.getRandom();
        float roll = r.nextFloat();
        if (roll < 0.50F) {
            setVariant(Variant.SKIRMISHER);
        } else if (roll < 0.75F) {
            setVariant(Variant.SPITTER);
        } else {
            setVariant(Variant.LURKER);
        }

        if (hasCeilingAbove()) {
            attach(Direction.UP);
        }

        return spawnGroupData;
    }

    public static boolean checkCaveSpiderSpawnRules(
            EntityType<? extends Monster> entityType,
            ServerLevelAccessor level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random) {
        // Shared biome IDs also exist in archived realms. Do not add a new natural population there.
        if ((spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)
                && !(level.getLevel().getChunkSource().getGenerator() instanceof IslandChunkGenerator generator
                && generator.terrainRevision() == 6)) return false;
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return false;
        if (level.canSeeSky(pos)) return false;
        if (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) > 0) return false;
        if (level.getRawBrightness(pos, 0) > 7) return false;

        // Ensure we are inside a genuine underground cave/cavern, NOT under surface trees/canopies
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.getX(), pos.getY() + 1, pos.getZ());
        boolean hasRockCeiling = false;
        for (int dy = 1; dy <= 24; dy++) {
            cursor.setY(pos.getY() + dy);
            var state = level.getBlockState(cursor);
            if (state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS)) {
                return false; // Under a tree crown on the surface
            }
            if (state.isSolidRender(level, cursor)) {
                hasRockCeiling = true;
                break;
            }
        }
        if (!hasRockCeiling) return false;

        return isDarkEnoughToSpawn(level, pos, random) && checkMobSpawnRules(entityType, level, spawnType, pos, random);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SpiderBodyRevision", needsBodyFit ? 0 : 1);
        tag.putByte("SpiderVariant", (byte) getVariant().id);
        tag.putInt("BurstTicks", this.burstTicks);
        tag.putBoolean("SpiderBursting", this.isBursting());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        needsBodyFit = tag.getInt("SpiderBodyRevision") < 1;
        if (tag.contains("SpiderVariant")) {
            applyVariant(Variant.byId(tag.getByte("SpiderVariant")), false);
        }
        this.burstTicks = Mth.clamp(tag.getInt("BurstTicks"), 0, 160);
        // Older saves stored only the positive timer. Restore that remaining burst without replaying its lunge.
        boolean active = getVariant() == Variant.LURKER && burstTicks > 0
                && (!tag.contains("SpiderBursting") || tag.getBoolean("SpiderBursting"));
        if (!active) burstTicks = 0;
        this.setBursting(active);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        this.playSound(SoundEvents.SPIDER_STEP, 0.15F, 1.0F);
    }

}
