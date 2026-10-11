package pro.erez.interstice.fauna;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pro.erez.interstice.ToxicLiquidBlock;

/** A straight moving globule of upper toxin; never places fluid, fire, or damaged blocks. */
public final class UpperSeaCharge extends Projectile {
    public static final double SPEED = 1.1, SPLASH_RADIUS = 2.25;
    public static final int MAX_AIR_TICKS = 90;
    private int airTicks;
    public UpperSeaCharge(EntityType<? extends UpperSeaCharge> type, Level level) { super(type, level); }
    public UpperSeaCharge(Level level, LivingEntity owner, Vec3 origin, Vec3 destination) {
        this(StrongRealmFauna.UPPER_SEA_CHARGE.get(), level);
        setOwner(owner); setPos(origin); setNoGravity(true);
        Vec3 direction = destination.subtract(origin).normalize();
        setDeltaMovement(direction.scale(SPEED));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected boolean canHitEntity(Entity entity) {
        return !(entity instanceof UpperSeaGhast) && super.canHitEntity(entity);
    }
    @Override public void tick() {
        super.tick();
        if (++airTicks > MAX_AIR_TICKS) { discard(); return; }
        Vec3 movement = getDeltaMovement();
        Vec3 next = position().add(movement);
        if (level() instanceof ServerLevel server) {
            if (!Double.isFinite(next.x) || !Double.isFinite(next.y) || !Double.isFinite(next.z)
                    || !StrongRealmFauna.loadedBox(server, getBoundingBox().expandTowards(movement))
                    || next.y < server.getMinBuildHeight() || next.y >= server.getMaxBuildHeight()) { discard(); return; }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) { onHit(hit); if (isRemoved()) return; }
            if (airTicks % 3 == 0) server.sendParticles(ParticleTypes.SNEEZE, getX(), getY(), getZ(), 1, .06, .06, .06, .005);
        }
        setPos(next); updateRotation();
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel)) return;
        if (hit.getEntity() instanceof LivingEntity living) hurtTarget(living, 6);
        splash(hit.getLocation(), hit.getEntity());
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        // No superclass block callback: this is liquid splashing, not a fireball or button arrow.
        if (level() instanceof ServerLevel) splash(hit.getLocation(), null);
    }
    private void hurtTarget(LivingEntity target, float amount) {
        if (target == getOwner() || target instanceof UpperSeaGhast
                || target instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ToxicLiquidBlock.TOXIN), this, getOwner());
        if (target.hurt(source, amount)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), getOwner());
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), getOwner());
        }
    }
    private void splash(Vec3 point, Entity directlyHit) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 outward = getDeltaMovement().normalize().scale(-.08);
        Vec3 visiblePoint = point.add(outward);
        for (var living : server.getEntitiesOfClass(LivingEntity.class, new AABB(visiblePoint, visiblePoint).inflate(SPLASH_RADIUS))) {
            if (living == directlyHit || living.distanceToSqr(visiblePoint) > SPLASH_RADIUS * SPLASH_RADIUS) continue;
            Vec3 target = living.getBoundingBox().getCenter();
            if (server.clip(new ClipContext(visiblePoint, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS)
                hurtTarget(living, 3);
        }
        server.playSound(null, BlockPos.containing(point), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, .8F, .7F);
        server.sendParticles(ParticleTypes.SNEEZE, point.x, point.y, point.z, 12, .4, .4, .4, .03);
        discard();
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("UpperChargeAge", airTicks); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        airTicks = Math.clamp(tag.getInt("UpperChargeAge"), 0, MAX_AIR_TICKS);
        setNoGravity(true);
    }
}
