package pro.erez.interstice.entity;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
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

public final class CaveRiftSpiderEntity extends Monster implements RangedAttackMob {
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
        if (getVariant() == Variant.LURKER && !isBursting()) {
            this.burstTicks = 160; // 8 seconds burst sprint
            this.setBursting(true);
            this.playSound(SoundEvents.SPIDER_HURT, 1.3F, 1.6F);
            alertPack(this.getTarget());
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

    public void alertPack(LivingEntity target) {
        if (target == null || this.level().isClientSide) return;
        var box = new AABB(this.getX() - 20, this.getY() - 10, this.getZ() - 20,
                this.getX() + 20, this.getY() + 10, this.getZ() + 20);
        var pack = this.level().getEntitiesOfClass(CaveRiftSpiderEntity.class, box, s -> s.isAlive() && s != this);
        for (var spider : pack) {
            if (spider.getTarget() == null) {
                spider.setTarget(target);
                if (spider.getVariant() == Variant.LURKER) {
                    spider.triggerAmbushBurst();
                }
            }
        }
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbingWall() || this.isClimbingCeiling();
    }

    public boolean hasCeilingAbove() {
        AABB bb = this.getBoundingBox();
        AABB ceilingBox = new AABB(
                bb.minX + 0.05, bb.maxY - 0.05, bb.minZ + 0.05,
                bb.maxX - 0.05, bb.maxY + 0.35, bb.maxZ - 0.05
        );
        return !this.level().noCollision(this, ceilingBox);
    }

    public boolean canTargetEntity(@Nullable LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity instanceof CaveRiftSpiderEntity) return false;
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (this.getVariant() == Variant.LURKER) {
                return this.isBursting() || this.distanceToSqr(player) <= 12.25;
            }
            return true;
        }
        return entity instanceof Bat || entity instanceof IronGolem || entity instanceof Zombie || entity instanceof AbstractSkeleton;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target instanceof CaveRiftSpiderEntity) {
            return;
        }
        super.setTarget(target);
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
        if (this.isAlive() && this.isClimbingCeiling()) {
            this.resetFallDistance();

            double speed = this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.85;
            this.moveRelative((float) speed, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());

            Vec3 vel = this.getDeltaMovement();
            double stickyY = this.hasCeilingAbove() ? 0.02 : vel.y;
            this.setDeltaMovement(vel.x * 0.8, stickyY, vel.z * 0.8);
            return;
        }
        super.travel(travelVector);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.55F));
        this.goalSelector.addGoal(3, new SpitterRangedAttackGoal(this, 1.15, 38, 14.0F));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.25, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Retaliation: alert comrades, NEVER target brothers
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                if (this.mob.getLastHurtByMob() instanceof CaveRiftSpiderEntity) {
                    this.mob.setLastHurtByMob(null);
                    return false;
                }
                return super.canUse();
            }

            @Override
            public void start() {
                super.start();
                if (mob instanceof CaveRiftSpiderEntity spider) {
                    if (spider.getTarget() instanceof CaveRiftSpiderEntity) {
                        spider.setTarget(null);
                        return;
                    }
                    spider.triggerAmbushBurst();
                    spider.alertPack(spider.getTarget());
                }
            }
        }.setAlertOthers());

        // Target Players
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                this::canTargetEntity) {
            @Override
            public void start() {
                super.start();
                if (mob instanceof CaveRiftSpiderEntity spider) {
                    if (spider.getVariant() == Variant.LURKER) {
                        spider.triggerAmbushBurst();
                    }
                    spider.alertPack(spider.getTarget());
                }
            }
        });

        // Prey 1: Cave bats (frequent prey in caverns)
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Bat.class, 10, true, false,
                this::canTargetEntity));

        // Prey 2: Village protectors (Iron Golems)
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, 10, true, false,
                this::canTargetEntity));

        // Prey 3: Hostile undead invaders (Zombies)
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Zombie.class, 10, true, false,
                this::canTargetEntity));

        // Prey 4: Hostile undead invaders (Skeletons)
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, 10, true, false,
                this::canTargetEntity));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
        } else {
            boolean ceiling = this.hasCeilingAbove() && !this.onGround();
            this.setClimbingCeiling(ceiling);
            this.setClimbingWall(this.horizontalCollision && !ceiling);

            // Ceiling behavior & Ambush drops
            if (this.isClimbingCeiling()) {
                this.resetFallDistance();
                LivingEntity target = this.getTarget();

                // If no target yet, seek prey beneath within 10 blocks vertical and 7 blocks horizontal
                if (target == null && this.tickCount % 10 == 0) {
                    AABB ambushZone = this.getBoundingBox().inflate(7.0, 0, 7.0).expandTowards(0, -10.0, 0);
                    var candidates = this.level().getEntitiesOfClass(LivingEntity.class, ambushZone, this::canTargetEntity);
                    if (!candidates.isEmpty()) {
                        candidates.sort(Comparator.comparingDouble(this::distanceToSqr));
                        this.setTarget(candidates.get(0));
                        target = this.getTarget();
                    }
                }

                if (target != null && target.isAlive()) {
                    double dx = target.getX() - this.getX();
                    double dz = target.getZ() - this.getZ();
                    double horizDist = Math.sqrt(dx * dx + dz * dz);
                    double dy = target.getY() - this.getY();

                    // If target is beneath the spider
                    if (dy < -1.0) {
                        if (this.getVariant() == Variant.LURKER) {
                            if (horizDist <= 3.5) {
                                // Lethal Ambush Drop from ceiling!
                                this.setClimbingCeiling(false);
                                this.triggerAmbushBurst();
                                this.setDeltaMovement(dx * 0.35, -0.85, dz * 0.35);
                                this.hasImpulse = true;
                                this.playSound(SoundEvents.SPIDER_HURT, 1.4F, 1.8F);
                                if (this.level() instanceof ServerLevel sl) {
                                    sl.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 12, 0.4, 0.4, 0.4, 0.2);
                                }
                            } else {
                                // Stalk along ceiling towards target
                                double speed = 0.28;
                                this.setDeltaMovement(dx / horizDist * speed, 0.01, dz / horizDist * speed);
                                this.setYRot((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
                                this.yBodyRot = this.getYRot();
                            }
                        } else if (this.getVariant() == Variant.SKIRMISHER) {
                            if (horizDist <= 2.8) {
                                // Skirmisher drop pounce
                                this.setClimbingCeiling(false);
                                this.setDeltaMovement(dx * 0.25, -0.70, dz * 0.25);
                                this.hasImpulse = true;
                                this.playSound(SoundEvents.SPIDER_HURT, 1.1F, 1.4F);
                            } else {
                                // Crawl along ceiling towards target
                                double speed = 0.32;
                                this.setDeltaMovement(dx / horizDist * speed, 0.01, dz / horizDist * speed);
                                this.setYRot((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
                                this.yBodyRot = this.getYRot();
                            }
                        } else if (this.getVariant() == Variant.SPITTER) {
                            // Spitters snipe from above, or crawl closer if out of range
                            if (horizDist > 12.0) {
                                double speed = 0.22;
                                this.setDeltaMovement(dx / horizDist * speed, 0.01, dz / horizDist * speed);
                            }
                            this.setYRot((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
                            this.yBodyRot = this.getYRot();
                        }
                    }
                }
            }

            // Lurker ground proximity sensing: if player gets close, burst out!
            if (this.getVariant() == Variant.LURKER && !this.isBursting()) {
                var nearestPlayer = this.level().getNearestPlayer(this, 3.5);
                if (nearestPlayer != null && nearestPlayer.isAlive() && !nearestPlayer.isCreative() && !nearestPlayer.isSpectator()) {
                    this.setTarget(nearestPlayer);
                    this.triggerAmbushBurst();
                }
            }

            // Burst timer countdown & dynamic lunges during burst
            if (this.isBursting()) {
                if (this.getVariant() == Variant.LURKER) {
                    LivingEntity target = this.getTarget();
                    if (target != null && target.isAlive()) {
                        // Periodically pounce during burst sprint if target tries to flee (3 to 8 blocks away)
                        if (this.burstTicks % 35 == 0 && this.onGround()) {
                            double distSq = this.distanceToSqr(target);
                            if (distSq >= 9.0 && distSq <= 64.0) {
                                performAmbushLunge(target);
                            }
                        }
                    }
                }
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
        if (!(this.level() instanceof ServerLevel) || this.getVariant() != Variant.SPITTER
                || target == null || !target.isAlive() || target.isRemoved() || target.level() != this.level()
                || target instanceof CaveRiftSpiderEntity || !this.canAttack(target)
                || this.distanceToSqr(target) > 14.0 * 14.0 || !this.getSensing().hasLineOfSight(target)) return;
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() * 0.5, 0);
        Vec3 origin = this.position().add(0, isClimbingCeiling() ? 0.2 : this.getEyeHeight(), 0);
        Vec3 dir = targetPos.subtract(origin).normalize().scale(0.85);

        ToxinSpitEntity spit = new ToxinSpitEntity(this.level(), this, dir.x, dir.y, dir.z);
        this.level().addFreshEntity(spit);
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
            setClimbingCeiling(true);
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
        tag.putByte("SpiderVariant", (byte) getVariant().id);
        tag.putInt("BurstTicks", this.burstTicks);
        tag.putBoolean("SpiderBursting", this.isBursting());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
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

    private static class SpitterRangedAttackGoal extends Goal {
        private final CaveRiftSpiderEntity spider;
        private final double speedModifier;
        private final int attackInterval;
        private final float maxAttackDistance;
        private int attackTime = -1;

        public SpitterRangedAttackGoal(CaveRiftSpiderEntity spider, double speed, int interval, float maxDist) {
            this.spider = spider;
            this.speedModifier = speed;
            this.attackInterval = interval;
            this.maxAttackDistance = maxDist * maxDist;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = spider.getTarget();
            return spider.getVariant() == Variant.SPITTER && target != null && target.isAlive()
                    && !target.isRemoved() && target.level() == spider.level() && spider.canAttack(target);
        }

        @Override
        public void stop() {
            spider.getNavigation().stop();
            attackTime = -1;
        }

        @Override
        public void tick() {
            LivingEntity target = spider.getTarget();
            if (target == null) return;
            double distSq = spider.distanceToSqr(target.getX(), target.getY(), target.getZ());
            spider.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // Retreat if too close (< 5 blocks)
            if (distSq < 25.0) {
                Vec3 away = spider.position().subtract(target.position()).normalize().scale(5.0);
                spider.getNavigation().moveTo(spider.getX() + away.x, spider.getY() + away.y, spider.getZ() + away.z, speedModifier * 1.2);
            } else if (distSq > maxAttackDistance || !spider.getSensing().hasLineOfSight(target)) {
                spider.getNavigation().moveTo(target, speedModifier);
            } else {
                spider.getNavigation().stop();
            }

            if (--this.attackTime <= 0 && distSq <= maxAttackDistance
                    && spider.getSensing().hasLineOfSight(target)) {
                this.attackTime = this.attackInterval;
                spider.performRangedAttack(target, 1.0F);
            }
        }
    }
}
