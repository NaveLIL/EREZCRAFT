package pro.erez.interstice.tide;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import pro.erez.interstice.FluidContact;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;

/**
 * Applies smooth, bounded upward buoyant lift during SURGE and EBB tide phases.
 * Uses native Minecraft 1.21.1 Attributes.GRAVITY modifiers, which automatically sync
 * to clients and satisfy ServerGamePacketListenerImpl flight checks without disabling
 * server security or resetting fall distance globally.
 */
@EventBusSubscriber(modid = Interstice.ID)
public final class BuoyancyController {
    public static final ResourceLocation BUOYANCY_ID = ResourceLocation.fromNamespaceAndPath(Interstice.ID, "tide_buoyancy");

    private BuoyancyController() {}

    /**
     * Updates buoyancy on a single entity according to the current tide intensity.
     */
    public static void applyEntityBuoyancy(Entity entity, float intensity) {
        if (entity == null || entity.isRemoved()) return;
        var level = entity.level();
        if (level == null || level.isClientSide()) return;

        if (entity instanceof LivingEntity living) {
            var gravityAttr = living.getAttribute(Attributes.GRAVITY);
            if (gravityAttr == null) return;

            boolean eligible = intensity > 0.0F
                    && !ShelterDetector.isSheltered(living.level(), living)
                    && !living.isInWater()
                    && !living.isInFluidType()
                    && !living.onClimbable()
                    && !living.isPassenger()
                    && !living.isFallFlying();

            if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
                eligible = false;
            }

            if (eligible) {
                // Vanilla base gravity is 0.08. We offset base gravity and add upward lift.
                // At full intensity (1.0), effective gravity = -0.028 (gentle ~0.35 blocks/tick terminal float).
                double modAmount = -0.08 - 0.028 * intensity;
                AttributeModifier modifier = new AttributeModifier(BUOYANCY_ID, modAmount, AttributeModifier.Operation.ADD_VALUE);
                gravityAttr.addOrUpdateTransientModifier(modifier);

                // When buoyant force arrests downward fall and lifts upward, reset fall distance
                if (living.getDeltaMovement().y >= 0.0) {
                    living.fallDistance = 0.0F;
                }
            } else {
                if (gravityAttr.hasModifier(BUOYANCY_ID)) {
                    gravityAttr.removeModifier(BUOYANCY_ID);
                }
            }
        } else if (entity instanceof ItemEntity item) {
            // If item reaches or touches the upper toxic sea, disintegrate it immediately!
            GeometryProfile profile = GeometryProfiles.get(item.level());
            double seaBottom = SeaSurface.cellMinimum(profile, item.getBlockX(), item.getBlockZ(), true);
            if (item.getY() >= seaBottom || FluidContact.pointInLight(item.level(), item.getX(), item.getY(), item.getZ())) {
                if (item.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.POOF, item.getX(), item.getY(), item.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                    serverLevel.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY(), item.getZ(), 5, 0.15, 0.15, 0.15, 0.01);
                    serverLevel.playSound(null, item.getX(), item.getY(), item.getZ(),
                            SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.6F);
                }
                item.discard();
                return;
            }

            // Dropped items out in the open during surge drift upward
            if (intensity > 0.0F && !ShelterDetector.isSheltered(item.level(), item)
                    && !item.isInWater()) {
                Vec3 delta = item.getDeltaMovement();
                // Overcome vanilla item gravity (0.04) and lift smoothly towards the upper sky
                double targetUpward = 0.32 * intensity;
                if (delta.y < targetUpward) {
                    item.setDeltaMovement(delta.x, Math.min(targetUpward, delta.y + 0.06 * intensity), delta.z);
                    item.hasImpulse = true;
                }
            }
        }
    }

    public static void removeBuoyancy(LivingEntity entity) {
        if (entity == null) return;
        var gravityAttr = entity.getAttribute(Attributes.GRAVITY);
        if (gravityAttr != null && gravityAttr.hasModifier(BUOYANCY_ID)) {
            gravityAttr.removeModifier(BUOYANCY_ID);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeBuoyancy(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeBuoyancy(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeBuoyancy(player);
        }
    }
}
