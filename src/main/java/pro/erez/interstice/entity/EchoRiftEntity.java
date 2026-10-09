package pro.erez.interstice.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pro.erez.interstice.Interstice;

import java.util.*;

public class EchoRiftEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(EchoRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STABILITY =
            SynchedEntityData.defineId(EchoRiftEntity.class, EntityDataSerializers.FLOAT);

    public static final int BUFFER_SIZE = 50; // 2.5 seconds at 20 TPS
    public static final int IMMUNITY_TICKS = 40; // 2.0 seconds immunity after rewind

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
            setStability(Math.min(1.0F, stability + 0.004F)); // Slowly regenerate
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
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.35F * stab, 1.7F, false);
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 0.45F * stab, 1.9F, false);
        }

        // 2. Dynamic gravitational vortex: particles spawn on the perimeter and spiral INWARD toward singularity
        for (int i = 0; i < 4; i++) {
            double spawnDist = 1.5 + this.random.nextDouble() * (r * 0.85);
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double heightOffset = (this.random.nextDouble() - 0.5) * (r * 0.7);

            double px = this.getX() + Math.cos(angle) * spawnDist;
            double py = this.getY() + 0.8 + heightOffset;
            double pz = this.getZ() + Math.sin(angle) * spawnDist;

            // Spiral inwards: inward radial velocity + tangential spiral velocity
            double toCenterX = this.getX() - px;
            double toCenterY = (this.getY() + 0.8) - py;
            double toCenterZ = this.getZ() - pz;
            double dist = Math.sqrt(toCenterX * toCenterX + toCenterZ * toCenterZ);

            if (dist > 0.1) {
                double speed = 0.12 * stab;
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
        AABB aabb = this.getBoundingBox().inflate(r);
        List<Entity> entities = serverLevel.getEntities(this, aabb, e -> e.isAlive() && e != this);

        Set<UUID> presentUuids = new HashSet<>();

        for (Entity entity : entities) {
            double distSq = entity.distanceToSqr(this.getX(), this.getY() + 0.8, this.getZ());
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
                Vec3 toCore = new Vec3(this.getX(), this.getY() + 0.8, this.getZ()).subtract(item.position());
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

                // 1. INSTANT TEMPORAL ARREST: Softly catch falling entities and reset fall distance!
                living.resetFallDistance();
                Vec3 motion = living.getDeltaMovement();
                boolean wasFalling = motion.y < -0.12 || living.fallDistance > 1.5F;

                if (wasFalling) {
                    // Brake vertical descent into gentle zero-g hover
                    double dampedY = Math.max(motion.y * 0.18, -0.03);
                    living.setDeltaMovement(motion.x * 0.45, dampedY, motion.z * 0.45);
                    living.hasImpulse = true;

                    // If jumping from cliff, store the highest entry point as return anchor
                    cliffEntryPoints.putIfAbsent(id, living.position());

                    if (serverLevel.random.nextFloat() < 0.3F) {
                        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, living.getX(), living.getY() + 0.5, living.getZ(), 6, 0.25, 0.25, 0.25, 0.05);
                        serverLevel.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.9F);
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
                // Case A: Full loop spent inside (2.5 seconds)
                boolean fullLoop = history.size() >= BUFFER_SIZE;
                // Case B: Entity caught in fall who spent at least 25 ticks (1.25s) hovering
                boolean cliffFallRescue = cliffEntryPoints.containsKey(id) && history.size() >= 25;
                // Case C: Sudden breach out of the anomaly boundary at high speed
                boolean edgeBreach = history.size() >= 20 && distSq > (r - 0.8) * (r - 0.8) && living.getDeltaMovement().lengthSqr() > 0.12;

                if (fullLoop || cliffFallRescue || edgeBreach) {
                    // If rescued from cliff fall, prioritize teleporting back to cliff edge
                    Vec3 returnTarget = cliffEntryPoints.remove(id);
                    TrajectoryPoint targetPoint = history.peekFirst();

                    if (returnTarget != null && targetPoint != null) {
                        targetPoint = new TrajectoryPoint(returnTarget, new Vec3(0, 0.1, 0), targetPoint.yRot(), targetPoint.xRot(), 0.0F, targetPoint.tickTime());
                    }

                    performRewind(living, targetPoint, serverLevel);
                    history.clear();
                    immunityCooldownMap.put(id, IMMUNITY_TICKS);
                }
            }
        }

        // Clean up entities that left the rift
        trajectoryMap.keySet().removeIf(id -> !presentUuids.contains(id) && !immunityCooldownMap.containsKey(id));
        cliffEntryPoints.keySet().removeIf(id -> !presentUuids.contains(id));
        deflectedProjectiles.removeIf(id -> !presentUuids.contains(id));
    }

    public void performRewind(LivingEntity entity, TrajectoryPoint target, ServerLevel level) {
        if (target == null) return;

        Vec3 from = entity.position();

        // 1. Teleport entity back to the start of the loop / cliff ledge
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.teleport(target.pos.x, target.pos.y, target.pos.z, target.yRot, target.xRot);
        } else {
            entity.moveTo(target.pos.x, target.pos.y, target.pos.z, target.yRot, target.xRot);
        }

        // 2. Restore motion and save completely from fall damage
        entity.setDeltaMovement(target.deltaMovement);
        entity.resetFallDistance();

        // 3. Audio & Visuals: Spatial implosion and sonic boom
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + entity.getBbHeight() * 0.5, from.z,
                30, 0.4, 0.4, 0.4, 0.15);
        level.sendParticles(ParticleTypes.SONIC_BOOM, target.pos.x, target.pos.y + entity.getBbHeight() * 0.5, target.pos.z,
                1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, target.pos.x, target.pos.y + entity.getBbHeight() * 0.5, target.pos.z,
                20, 0.35, 0.35, 0.35, 0.1);

        level.playSound(null, from.x, from.y, from.z, SoundEvents.PORTAL_TRAVEL, SoundSource.NEUTRAL, 0.8F, 1.8F);
        level.playSound(null, target.pos.x, target.pos.y, target.pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.3F, 1.6F);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // Harvest with glass bottle
        if (held.is(Items.GLASS_BOTTLE) && getStability() >= 0.65F) {
            if (!player.level().isClientSide) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }

                ItemStack shard = new ItemStack(Interstice.ECHO_SHARD.get());
                if (!player.addItem(shard)) {
                    player.drop(shard, false);
                }

                setStability(0.2F); // Depletes stability, recovers slowly

                if (player.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.8, this.getZ(), 35, 0.25, 0.25, 0.25, 0.2);
                    sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.PLAYERS, 1.0F, 1.4F);
                    sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.8F);
                }
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
