package pro.erez.interstice.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.client.V6ShaderBridge;

@Pseudo
@Mixin(targets="net.irisshaders.iris.shaderpack.ShaderPack", remap=false)
public abstract class IrisV6ProgramSetMixin {
    @Inject(method="getProgramSet", at=@At("RETURN"), cancellable=true, remap=false)
    private void interstice$v6Programs(@Coerce Object dimension, CallbackInfoReturnable<Object> callback) {
        callback.setReturnValue(V6ShaderBridge.programSet(this, dimension, callback.getReturnValue()));
    }
}
