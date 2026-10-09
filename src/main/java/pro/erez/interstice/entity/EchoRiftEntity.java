package pro.erez.interstice.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pro.erez.interstice.Interstice;

import java.util.*;

public class EchoRiftEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(EchoRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STABILITY =
            SynchedEntityData.defineId(EchoRiftEntity.class, EntityDataSerializers.FLOAT);

    public static final int BUFFER_SIZE = 50; // 2.5 seconds loop duration
    public static final int RESCUE_HOVER_TICKS = 25; // 1.25 seconds weightless hover before cliff rewind
    public static final int IMMUNITY_TICKS = 50; // 2.5 seconds immunity cooldown after rewind

    public record TrajectoryPoint(Vec3 pos, Vec3 deltaMovement, float yRot, float xRot, float fallDistance, int tickTime) {}

    private final Map<UUID, Deque<TrajectoryPoint>> trajectoryMap = new HashMap<>();
    private final Map<UUID, Integer> immunityCooldownMap = new HashMap<>();
    private final Set<UUID> deflectedProjectiles = new HashSet<>();
    private final Map<UUID, Vec3> cliffEntryPoints = new HashMap<>();

    public EchoRiftEntity(EntityType<? extends EchoRiftEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 4.5F);
        builder.define(DATA_STABILITY, 1.0F);
    }

    public float getRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public void setRadius(float radius) {
        this.entityData.set(DATA_RADIUS, radius);
    }

    public float getStability() {
        return this.entityData.get(DATA_STABILITY);
    }

    public void setStability(float stability) {
        this.entityData.set(DATA_STABILITY, stability);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        float stability = getStability();
        if (stability < 1.0F) {
            // Recharges to full stability over 2 full minutes (2400 ticks)
            setStability(Math.min(1.0F, stability + (1.0F / 2400.0F)));
        }

        if (this.level().isClientSide) {
            tickClientVisuals();
        } else {
            tickServerLogic();
        }
    }

    private void tickClientVisuals() {
        float r = getRadius();
        float stab = getStability();

        // 1. Ambient resonant hum
        if (this.tickCount % 35 == 0) {
            this.level().playLocalSound(this.getX(), this.getY() + 0.8, this.getZ(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.35F * stab, 1.7F, false);
            this.level().playLocalSound(this.getX(), this.getY() + 0.8, this.getZ(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 0.45F * stab, 1.9F, false);
        }

        // 2. Gravitational vortex: particles spawn on perimeter and spiral inward toward singularity
        for (int i = 0; i < 4; i++) {
            double spawnDist = 1.4 + this.random.nextDouble() * (r * 0.85);
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double heightOffset = (this.random.nextDouble() - 0.5) * (r * 0.7);

            double px = this.getX() + Math.cos(angle) * spawnDist;
            double py = this.getY() + 0.8 + heightOffset;
            double pz = this.getZ() + Math.sin(angle) * spawnDist;

            double toCenterX = this.getX() - px;
            double toCenterY = (this.getY() + 0.8) - py;
            double toCenterZ = this.getZ() - pz;
            double dist = Math.sqrt(toCenterX * toCenterX + toCenterZ * toCenterZ);

            if (dist > 0.1) {
                double speed = 0.14 * (0.4 + 0.6 * stab);
                double inwardX = (toCenterX / dist) * speed;
                double inwardZ = (toCenterZ / dist) * speed;
                double tangentX = -Math.sin(angle) * (speed * 0.8);
                double tangentZ = Math.cos(angle) * (speed * 0.8);

                this.level().addParticle(ParticleTypes.REVERSE_PORTAL,
                        px, py, pz,
                        inwardX + tangentX, toCenterY * 0.08, inwardZ + tangentZ);
            }
        }

        // 3. Dense core spatial sparkles
        for (int j = 0; j < 2; j++) {
            double ox = (this.random.nextDouble() - 0.5) * 0.5;
            double oy = (this.random.nextDouble() - 0.5) * 0.5 + 0.8;
            double oz = (this.random.nextDouble() - 0.5) * 0.5;
            this.level().addParticle(ParticleTypes.PORTAL,
                    this.getX() + ox, this.getY() + oy, this.getZ() + oz,
                    -ox * 0.2, -oy * 0.2, -oz * 0.2);
        }
    }

    private void tickServerLogic() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Decrement immunity cooldowns
        var iterator = immunityCooldownMap.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }

        float r = getRadius();
        double rSq = r * r;
        Vec3 center = new Vec3(this.getX(), this.getY() + 0.8, this.getZ());
        AABB aabb = this.getBoundingBox().inflate(r);
        List<Entity> entities = serverLevel.getEntities(this, aabb, e -> e.isAlive() && e != this);

        Set<UUID> presentUuids = new HashSet<>();

        for (Entity entity : entities) {
            double distSq = entity.distanceToSqr(center);
            if (distSq > rSq) continue;

            UUID id = entity.getUUID();
            presentUuids.add(id);

            // Projectile deflection (temporal reflection)
            if (entity instanceof Projectile projectile) {
                if (!deflectedProjectiles.contains(id)) {
                    deflectedProjectiles.add(id);
                    Vec3 vel = projectile.getDeltaMovement();
                    projectile.setDeltaMovement(vel.scale(-0.85));
                    serverLevel.sendParticles(ParticleTypes.FLASH, projectile.getX(), projectile.getY(), projectile.getZ(), 1, 0, 0, 0, 0);
                    serverLevel.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                            SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.9F);
                }
                continue;
            }

            // Item drift toward core
            if (entity instanceof ItemEntity item) {
                Vec3 toCore = center.subtract(item.position());
                double dist = toCore.length();
                if (dist > 0.1) {
                    item.setDeltaMovement(item.getDeltaMovement().scale(0.85).add(toCore.normalize().scale(0.04)));
                }
                continue;
            }

            // Living entities: temporal capture & stasis
            if (entity instanceof LivingEntity living) {
                // If entity is currently immune after a rewind, allow free movement
                if (immunityCooldownMap.containsKey(id)) {
                    continue;
                }

                // 1. INSTANT TEMPORAL ARREST & TIDAL INWARD PULL (приливный захват)
                living.resetFallDistance();

                Vec3 motion = living.getDeltaMovement();
                boolean wasFalling = motion.y < -0.15 || living.fallDistance > 1.0F || cliffEntryPoints.containsKey(id);

                Vec3 toCenter = center.subtract(living.position());
                double dist = toCenter.length();

                double pullFactor = Math.min(dist * 0.06, 0.16);
                Vec3 pull = toCenter.normalize().scale(pullFactor);

                // Heavy vertical braking into gentle zero-g hover stasis
                double newY;
                if (motion.y < 0) {
                    newY = Math.max(motion.y * 0.05, -0.03) + Math.max(0.0, pull.y);
                } else {
                    newY = motion.y * 0.6 + pull.y;
                }
                double newX = motion.x * 0.55 + pull.x;
                double newZ = motion.z * 0.55 + pull.z;

                living.setDeltaMovement(newX, newY, newZ);

                // Sync motion with ServerPlayer connection to avoid client prediction overshoot
                if (living instanceof ServerPlayer serverPlayer) {
                    serverPlayer.resetFallDistance();
                    serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
                }

                if (wasFalling) {
                    cliffEntryPoints.putIfAbsent(id, living.position());

                    if (serverLevel.random.nextFloat() < 0.25F) {
                        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, living.getX(), living.getY() + 0.5, living.getZ(),
                                6, 0.25, 0.25, 0.25, 0.05);
                        serverLevel.playSound(null, living.getX(), living.getY(), living.getZ(),
                                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.9F);
                    }
                }

                Deque<TrajectoryPoint> history = trajectoryMap.computeIfAbsent(id, k -> new ArrayDeque<>());
                history.addLast(new TrajectoryPoint(
                        living.position(),
                        living.getDeltaMovement(),
                        living.getYRot(),
                        living.getXRot(),
                        0.0F,
                        living.tickCount
                ));

                if (history.size() > BUFFER_SIZE) {
                    history.removeFirst();
                }

                // 2. Rewind Triggers:
                // Case A: Full loop completed inside the anomaly (2.5 seconds)
                boolean fullLoop = history.size() >= BUFFER_SIZE;
                // Case B: Entity caught in cliff fall who spent 1.5 seconds in weightless suspension
                boolean cliffFallRescue = cliffEntryPoints.containsKey(id) && history.size() >= RESCUE_HOVER_TICKS;
                // Case C: Emergency save: about to breach downwards out of the bottom of the anomaly
                boolean downwardBreach = cliffEntryPoints.containsKey(id) && living.getY() < this.getY() - (r * 0.45) && motion.y < 0;
                // Case D: Player explicitly sneaks to trigger immediate rewind return
                boolean playerCrouch = (living instanceof Player p && p.isShiftKeyDown() && history.size() >= 15);

                if (fullLoop || cliffFallRescue || downwardBreach || playerCrouch) {
                    Vec3 returnTarget = cliffEntryPoints.remove(id);
                    if (returnTarget == null && !history.isEmpty()) {
                        returnTarget = history.peekFirst().pos();
                    }

                    if (returnTarget != null) {
                        performRewind(living, returnTarget, serverLevel);
                        history.clear();
                        immunityCooldownMap.put(id, IMMUNITY_TICKS);
                    }
                }
            }
        }

        // Clean up entities that left the rift
        trajectoryMap.keySet().removeIf(id -> !presentUuids.contains(id) && !immunityCooldownMap.containsKey(id));
        cliffEntryPoints.keySet().removeIf(id -> !presentUuids.contains(id));
        deflectedProjectiles.removeIf(id -> !presentUuids.contains(id));
    }

    public void performRewind(LivingEntity entity, Vec3 targetPos, ServerLevel level) {
        if (targetPos == null) return;

        Vec3 from = entity.position();

        // 1. Teleport entity back to safe ledge
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.teleport(targetPos.x, targetPos.y, targetPos.z, serverPlayer.getYRot(), serverPlayer.getXRot());
            serverPlayer.setDeltaMovement(0, 0.08, 0);
            serverPlayer.resetFallDistance();
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        } else {
            entity.moveTo(targetPos.x, targetPos.y, targetPos.z, entity.getYRot(), entity.getXRot());
            entity.setDeltaMovement(0, 0.08, 0);
            entity.resetFallDistance();
        }

        // 2. Audio & Visuals: Spatial implosion and sonic boom
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + entity.getBbHeight() * 0.5, from.z,
                35, 0.4, 0.4, 0.4, 0.15);
        level.sendParticles(ParticleTypes.SONIC_BOOM, targetPos.x, targetPos.y + entity.getBbHeight() * 0.5, targetPos.z,
                1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, targetPos.x, targetPos.y + entity.getBbHeight() * 0.5, targetPos.z,
                20, 0.35, 0.35, 0.35, 0.1);

        level.playSound(null, from.x, from.y, from.z, SoundEvents.PORTAL_TRAVEL, SoundSource.NEUTRAL, 0.9F, 1.8F);
        level.playSound(null, targetPos.x, targetPos.y, targetPos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.2F, 1.6F);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // Harvest with glass bottle
        if (held.is(Items.GLASS_BOTTLE)) {
            // Must be fully stabilized to yield an Echo Shard
            if (getStability() < 0.95F) {
                if (player.level().isClientSide) {
                    player.displayClientMessage(Component.translatable("message.interstice.echo_rift_unstable"), true);
                    player.level().playLocalSound(getX(), getY() + 0.8, getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.6F, false);
                }
                return InteractionResult.sidedSuccess(player.level().isClientSide);
            }

            if (!player.level().isClientSide && player.level() instanceof ServerLevel sl) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }

                ItemStack shard = new ItemStack(Interstice.ECHO_SHARD.get());
                if (!player.addItem(shard)) {
                    player.drop(shard, false);
                }

                // 1. Collapse stability to 0.0 (requires 2 full minutes to slowly reform)
                setStability(0.0F);

                // 2. Violent Temporal Shockwave (Хроно-всплеск)
                sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 0.8, getZ(), 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 0.8, getZ(), 2, 0.4, 0.4, 0.4, 0);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.8, getZ(), 60, 0.6, 0.6, 0.6, 0.25);

                sl.playSound(null, getX(), getY() + 0.8, getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 1.3F, 1.7F);
                sl.playSound(null, getX(), getY() + 0.8, getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);

                // 3. Shockwave damage and radial repulsion
                player.hurt(sl.damageSources().magic(), 6.0F); // 3 hearts of void/temporal damage

                Vec3 away = player.position().subtract(getX(), getY() + 0.8, getZ());
                if (away.lengthSqr() < 0.01) {
                    away = new Vec3(1, 0, 0);
                }
                away = away.normalize();
                player.setDeltaMovement(away.x * 1.3, 0.48, away.z * 1.3);

                if (player instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                }

                // 4. Temporal disorientation effects
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
            }

            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }

        return super.interact(player, hand);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Radius")) {
            setRadius(tag.getFloat("Radius"));
        }
        if (tag.contains("Stability")) {
            setStability(tag.getFloat("Stability"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Radius", getRadius());
        tag.putFloat("Stability", getStability());
    }
}
