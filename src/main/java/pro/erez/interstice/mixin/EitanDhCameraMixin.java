package pro.erez.interstice.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.client.EitanCameras;
/** Nearby sensors need ordinary loaded terrain only; main-view DH stays enabled. */
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.render.renderer.LodRenderer",remap=false)
public abstract class EitanDhCameraMixin {
    @Inject(method={"render","renderDeferred"},at=@At("HEAD"),cancellable=true) private void interstice$nearbySensor(CallbackInfo ci){if(EitanCameras.CAPTURING)ci.cancel();}
}
