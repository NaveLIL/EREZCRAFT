package pro.erez.interstice.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.client.EitanCameras;

/** Cheap sensor backdrop from the actual biome/time; no second shader sky pass. */
@Mixin(LevelRenderer.class)
public abstract class EitanSensorSkyMixin {
    @Inject(method="renderSky",at=@At("HEAD"),cancellable=true)
    private void interstice$sensorSky(Matrix4f view,Matrix4f projection,float partial,Camera camera,boolean foggy,Runnable setupFog,CallbackInfo ci){
        var mc=Minecraft.getInstance();
        if(!EitanCameras.CAPTURING||foggy||camera.getFluidInCamera()!=FogType.NONE||mc.level==null
            ||mc.level.effects().skyType()!=DimensionSpecialEffects.SkyType.NORMAL)return;
        setupFog.run();
        var sky=mc.level.getSkyColor(camera.getPosition(),partial);
        RenderSystem.clearColor((float)sky.x,(float)sky.y,(float)sky.z,1);
        RenderSystem.clear(16384,Minecraft.ON_OSX);
        ci.cancel();
    }
}
