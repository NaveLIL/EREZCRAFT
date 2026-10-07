package pro.erez.interstice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.tide.ClientTideState;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideState;
import pro.erez.interstice.worldgen.IslandWorld;

/**
 * Handles client-side particle effects for rising wind vortexes and advances
 * the predicted client tide timer between server calibration packets.
 */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class ClientTideEffects {
    private ClientTideEffects() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;

        ClientTideState.clientTick();

        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) return;
        if (!IslandWorld.isIsland(level.dimension())) return;

        TideState state = ClientTideState.get();
        if (state.phase() != TidePhase.WARNING && state.phase() != TidePhase.SURGE) return;

        RandomSource random = level.random;
        Vec3 pos = player.position();
        boolean isSurge = state.phase() == TidePhase.SURGE;
        int count = isSurge ? 3 : 1;

        for (int i = 0; i < count; i++) {
            if (random.nextFloat() > (isSurge ? 0.85F : 0.45F)) continue;

            double dx = (random.nextDouble() - 0.5) * 20.0;
            double dz = (random.nextDouble() - 0.5) * 20.0;
            double dy = (random.nextDouble() - 0.5) * 4.0;

            double px = pos.x + dx;
            double py = pos.y + dy;
            double pz = pos.z + dz;

            double vx = (random.nextDouble() - 0.5) * 0.06;
            double vy = isSurge ? 0.28 + 0.18 * state.buoyancyIntensity() : 0.12;
            double vz = (random.nextDouble() - 0.5) * 0.06;

            level.addParticle(ParticleTypes.CLOUD, px, py, pz, vx, vy, vz);

            if (isSurge && random.nextFloat() < 0.25F) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz,
                        (random.nextDouble() - 0.5) * 0.15,
                        0.25 + 0.15 * random.nextDouble(),
                        (random.nextDouble() - 0.5) * 0.15);
            }
        }
    }
}
