package pro.erez.interstice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.EchoRiftEntity;

import java.util.List;

@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class EchoRiftClientHandler {
    private EchoRiftClientHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || player.level() == null) return;

        List<EchoRiftEntity> rifts = player.level().getEntitiesOfClass(
                EchoRiftEntity.class,
                player.getBoundingBox().inflate(6.0)
        );

        for (EchoRiftEntity rift : rifts) {
            float r = rift.getRadius();
            Vec3 center = new Vec3(rift.getX(), rift.getY() + 0.8, rift.getZ());
            double distSq = player.distanceToSqr(center);

            if (distSq <= r * r) {
                // 1. Instant zero-latency fall arrest
                player.resetFallDistance();

                // 2. Tidal Inward Pull (приливный захват к ядру)
                Vec3 motion = player.getDeltaMovement();
                Vec3 toCenter = center.subtract(player.position());
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

                player.setDeltaMovement(newX, newY, newZ);
                break;
            }
        }
    }
}
