package pro.erez.interstice.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pro.erez.interstice.Interstice;

public final class ToxinSpitEntity extends Projectile {
    private int ticksInAir;

    public ToxinSpitEntity(EntityType<? extends ToxinSpitEntity> type, Level level) {
        super(type, level);
    }

    public ToxinSpitEntity(Level level, LivingEntity owner, double vx, double vy, double vz) {
        super(Interstice.TOXIN_SPIT.get(), level);
        this.setOwner(owner);
        this.setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
        this.setDeltaMovement(vx, vy, vz);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected boolean canHitEntity(Entity entity) {
        return !(entity instanceof CaveRiftSpiderEntity) && super.canHitEntity(entity);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 mov = this.getDeltaMovement();
        if (this.level() instanceof ServerLevel) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) this.onHit(hit);
            if (this.isRemoved()) return;
        }

        double nextX = this.getX() + mov.x;
        double nextY = this.getY() + mov.y;
        double nextZ = this.getZ() + mov.z;
        this.setPos(nextX, nextY, nextZ);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SNEEZE, this.getX(), this.getY(), this.getZ(),
                    2, 0.05, 0.05, 0.05, 0.02);
        }

        // Slight gravity drop
        this.setDeltaMovement(mov.x * 0.98, mov.y - 0.03, mov.z * 0.98);

        if (++this.ticksInAir > 100) {
            this.discard();
        }
    }

    @Override
    public void onHitEntity(EntityHitResult result) {
        if (!(this.level() instanceof ServerLevel) || result.getEntity() instanceof CaveRiftSpiderEntity) return;
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        if (target instanceof LivingEntity living && target != owner) {
            DamageSource source = new DamageSource(
                    this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(CaveRiftSpiderEntity.SPIDER_SPIT),
                    this,
                    (owner instanceof LivingEntity l) ? l : null
            );
            living.hurt(source, 4.0F);
            // Apply slowness and poison to simulate corrosive upper toxin
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), owner);
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), owner);
            splashSoundAndParticles();
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!(this.level() instanceof ServerLevel)) return;
        super.onHitBlock(result);
        splashSoundAndParticles();
        this.discard();
    }

    private void splashSoundAndParticles() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.7F, 1.4F);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.GLOW_SQUID_INK, this.getX(), this.getY(), this.getZ(),
                    8, 0.15, 0.15, 0.15, 0.05);
        }
    }
}
