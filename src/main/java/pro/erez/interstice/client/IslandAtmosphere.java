package pro.erez.interstice.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import pro.erez.interstice.FluidContact;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandWorld;

/** An empty sky and black distance fog, scoped to the procedural island world. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class IslandAtmosphere {
    public static final ResourceLocation EFFECTS = ResourceLocation.fromNamespaceAndPath(Interstice.ID, "black_void");

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EFFECTS, new BlackVoidEffects());
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        var camera = event.getCamera();
        var level = camera.getEntity().level();
        if (!IslandWorld.isIsland(level.dimension())) return;
        var point = camera.getPosition();
        if (FluidContact.pointInLight(level, point.x, point.y, point.z)) {
            // Ceiling fluid's immersion differs from vanilla bottom-anchored fluid height.
            event.setRed(0.70F); event.setGreen(0.69F); event.setBlue(0.47F);
            return;
        }
        var fluid = level.getFluidState(camera.getBlockPosition());
        if (camera.getFluidInCamera() != FogType.NONE || (!fluid.isEmpty()
                && fluid.getType().getFluidType() != Interstice.LIGHT_TYPE.get()
                && point.y < camera.getBlockPosition().getY() + fluid.getHeight(level, camera.getBlockPosition()))) return;
        event.setRed(0); event.setGreen(0); event.setBlue(0);
    }

    public static final class BlackVoidEffects extends DimensionSpecialEffects {
        public BlackVoidEffects() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }
        @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return Vec3.ZERO; }
        @Override public boolean isFoggyAt(int x, int z) { return false; }
        @Override public float[] getSunriseColor(float time, float partialTick) { return null; }
        @Override public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView,
                Camera camera, Matrix4f projection, boolean foggy, Runnable setupFog) {
            setupFog.run();
            return true;
        }
        @Override public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack pose,
                double x, double y, double z, Matrix4f modelView, Matrix4f projection) { return true; }
        @Override public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
                LightTexture light, double x, double y, double z) { return true; }
        @Override public boolean tickRain(ClientLevel level, int ticks, Camera camera) { return true; }
    }
}
