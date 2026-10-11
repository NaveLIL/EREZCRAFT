package pro.erez.interstice.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.IntersticeClientConfig;
import pro.erez.interstice.client.V6Darkness;
import pro.erez.interstice.worldgen.IslandWorld;

/** Correct the finished lightmap before vanilla and DH upload the same pixels. */
@Mixin(LightTexture.class)
public abstract class V6LightTextureMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private NativeImage lightPixels;

    @Inject(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void interstice$removeV6Ambient(float partialTick, CallbackInfo callback) {
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null || !level.dimension().equals(IslandWorld.TENSION_WORLD)
                || !IntersticeClientConfig.V6_DARKNESS.get()
                || player.hasEffect(MobEffects.NIGHT_VISION)
                || (player.hasEffect(MobEffects.CONDUIT_POWER) && player.getWaterVision() > 0)) return;
        // Includes dimension ambient_light, vanilla's two gray lerps and the user's gamma.
        // Correct after those operations so they cannot reintroduce light into (0, 0).
        int zeroLight = lightPixels.getPixelRGBA(0, 0);
        int fullLight = lightPixels.getPixelRGBA(15, 15);
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                lightPixels.setPixelRGBA(block, sky,
                        V6Darkness.removeAmbient(lightPixels.getPixelRGBA(block, sky), zeroLight, fullLight));
            }
        }
    }
}
