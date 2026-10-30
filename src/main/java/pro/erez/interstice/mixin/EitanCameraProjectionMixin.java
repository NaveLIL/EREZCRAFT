package pro.erez.interstice.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pro.erez.interstice.client.EitanCameras;
@Mixin(GameRenderer.class)
public abstract class EitanCameraProjectionMixin {
    @Inject(method="getProjectionMatrix",at=@At("HEAD"),cancellable=true) private void interstice$wideSensor(double fov,CallbackInfoReturnable<Matrix4f> ci){if(EitanCameras.CAPTURING)ci.setReturnValue(new Matrix4f().perspective((float)Math.toRadians(90),pro.erez.interstice.client.EitanCameraOptics.ASPECT,.05f,Math.max(64,Minecraft.getInstance().gameRenderer.getRenderDistance()*4)));}
    @Inject(method="pick",at=@At("HEAD"),cancellable=true) private void interstice$preserveInteraction(float partial,CallbackInfo ci){if(EitanCameras.CAPTURING)ci.cancel();}
}
