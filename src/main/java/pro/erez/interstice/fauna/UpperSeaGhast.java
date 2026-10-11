package pro.erez.interstice.fauna;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfiles;

/** Realm-native floating hunter: visible windup, finite toxin charges, no fireball explosions. */
public final class UpperSeaGhast extends Ghast {
    public static final int WINDUP_TICKS = 30, SHOT_COOLDOWN_TICKS = 80;
    public static final double ATTACK_RANGE = 48;
    public UpperSeaGhast(EntityType<? extends UpperSeaGhast> type, Level level) {
        super(type, level);
        xpReward = 12;
    }
    public static AttributeSupplier.Builder createRealmAttributes() {
        return Ghast.createAttributes().add(Attributes.MAX_HEALTH, 28).add(Attributes.FOLLOW_RANGE, ATTACK_RANGE);
    }
    @Override protected void registerGoals() {
        // Do not install Ghast's vanilla fireball goal.
        goalSelector.addGoal(5, new RealmFloatGoal());
        goalSelector.addGoal(7, new ToxinAttackGoal());
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> target.isAlive() && target instanceof Player player && !player.isCreative() && !player.isSpectator()));
    }
    @Override public boolean checkSpawnRules(LevelAccessor level, MobSpawnType kind) {
        if (kind != MobSpawnType.NATURAL && kind != MobSpawnType.CHUNK_GENERATION) return super.checkSpawnRules(level, kind);
        return level instanceof net.minecraft.server.level.ServerLevel server && StrongRealmFauna.spawningEnabled(server)
                && StrongRealmFauna.airborneHabitat(server, blockPosition());
    }
    @Override protected float getSoundVolume() { return 1.2F; }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){return pro.erez.interstice.sound.ModSounds.GHAST_AMBIENT.get();}
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source){return pro.erez.interstice.sound.ModSounds.GHAST_HURT.get();}
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound(){return pro.erez.interstice.sound.ModSounds.GHAST_DEATH.get();}

    /** The same production method is exercised by projectile and native diagnostics. */
    public boolean fireAt(LivingEntity target) {
        if (level().isClientSide || !target.isAlive() || target.level() != level()
                || distanceToSqr(target) > ATTACK_RANGE * ATTACK_RANGE || !hasLineOfSight(target)
                || target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        Vec3 origin = new Vec3(getX(), getY() + getBbHeight() * .38, getZ());
        Vec3 destination = new Vec3(target.getX(), target.getY(.5), target.getZ());
        var charge = new UpperSeaCharge(level(), this, origin, destination);
        if (!level().addFreshEntity(charge)) return false;
        playSound(pro.erez.interstice.sound.ModSounds.GHAST_SHOOT.get(), 1.0F, 1F);
        return true;
    }
    private final class RealmFloatGoal extends Goal {
        RealmFloatGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            return !getMoveControl().hasWanted() || getRandom().nextInt(40) == 0;
        }
        @Override public boolean canContinueToUse() { return false; }
        @Override public void start() {
            if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) return;
            var geometry = GeometryProfiles.get(server);
            double x = getX() + (random.nextDouble() * 2 - 1) * 12;
            double z = getZ() + (random.nextDouble() * 2 - 1) * 12;
            double y = Mth.clamp(getY() + (random.nextDouble() * 2 - 1) * 8,
                    geometry.lowerSeaTop() + 28, SeaSurface.cellMinimum(geometry, (int)Math.floor(x), (int)Math.floor(z), true) - getBbHeight() - 7);
            AABB area = getBoundingBox().move(x - getX(), y - getY(), z - getZ());
            if (StrongRealmFauna.loadedBox(server, area.inflate(1)) && server.noCollision(UpperSeaGhast.this, area))
                getMoveControl().setWantedPosition(x, y, z, 1);
        }
    }
    private final class ToxinAttackGoal extends Goal {
        private int visibleTicks, cooldown;
        ToxinAttackGoal() { setFlags(EnumSet.of(Flag.LOOK)); }
        @Override public boolean canUse() { return getTarget() != null && getTarget().isAlive(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { visibleTicks = 0; setCharging(false); }
        @Override public void tick() {
            LivingEntity target = getTarget();
            if (cooldown > 0) cooldown--;
            if (target == null || target.isDeadOrDying() || distanceToSqr(target) > ATTACK_RANGE * ATTACK_RANGE
                    || !hasLineOfSight(target) || target instanceof Player p && (p.isCreative() || p.isSpectator())) {
                visibleTicks = 0; setCharging(false); return;
            }
            setYRot((float)(-Mth.atan2(target.getX() - getX(), target.getZ() - getZ()) * Mth.RAD_TO_DEG));
            yBodyRot = getYRot();
            if (cooldown > 0) { setCharging(false); return; }
            visibleTicks++;
            setCharging(visibleTicks >= 10);
            if (visibleTicks == 10) playSound(pro.erez.interstice.sound.ModSounds.GHAST_WARN.get(), .8F, 1F);
            if (visibleTicks >= WINDUP_TICKS) {
                fireAt(target); visibleTicks = 0; cooldown = SHOT_COOLDOWN_TICKS; setCharging(false);
            }
        }
    }
}
