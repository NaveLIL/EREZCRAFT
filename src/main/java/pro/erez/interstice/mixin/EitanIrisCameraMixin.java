package pro.erez.interstice.mixin;
import java.util.Optional;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.client.EitanCameras;
/** Supplemental sensors use a separate vanilla pass; the main Iris pipeline is untouched. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.pipeline.PipelineManager",remap=false)
public abstract class EitanIrisCameraMixin {
    @Inject(method={"preparePipeline","getPipelineNullable"},at=@At("HEAD"),cancellable=true) private void interstice$sensorPipeline(CallbackInfoReturnable<Object> ci){if(EitanCameras.CAPTURING)ci.setReturnValue(EitanCameras.vanillaPipeline());}
    @Inject(method="getPipeline",at=@At("HEAD"),cancellable=true) private void interstice$sensorOptional(CallbackInfoReturnable<Optional<?>> ci){if(EitanCameras.CAPTURING)ci.setReturnValue(Optional.of(EitanCameras.vanillaPipeline()));}
}
