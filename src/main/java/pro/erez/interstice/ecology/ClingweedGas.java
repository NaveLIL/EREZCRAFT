package pro.erez.interstice.ecology;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.phys.AABB;

/** Local defensive burst. Nearby destruction shares a finite cloud rather than renewing it. */
public final class ClingweedGas {
    public static final String ENTITY_TAG = "interstice_clingweed_gas";
    public static final int DURATION = 120;
    public static final float RADIUS = 2.5F;
    private ClingweedGas() {}
    public static boolean emit(ServerLevel level, BlockPos pos) {
        if (!level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(pos).inflate(8), cloud -> cloud.isAlive() && cloud.getTags().contains(ENTITY_TAG)).isEmpty()) return false;
        var cloud = new AreaEffectCloud(level, pos.getX() + .5, pos.getY() + .1, pos.getZ() + .5);
        cloud.addTag(ENTITY_TAG);
        cloud.setRadius(RADIUS);
        cloud.setWaitTime(0);
        cloud.setDuration(DURATION);
        cloud.setRadiusPerTick(-RADIUS / (DURATION + 1F));
        cloud.setParticle(ParticleTypes.SPORE_BLOSSOM_AIR);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 160, 1));
        cloud.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 240, 1));
        cloud.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200));
        return level.addFreshEntity(cloud);
    }
}
