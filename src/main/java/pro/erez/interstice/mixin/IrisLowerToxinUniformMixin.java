package pro.erez.interstice.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.client.LowerOceanDh;
@Pseudo @Mixin(targets="net.irisshaders.iris.compat.dh.IrisLodRenderProgram",remap=false)
public abstract class IrisLowerToxinUniformMixin {
    @Shadow @Final private int id;
    @Unique private LowerOceanDh.Binding interstice$binding;
    @Inject(method="bind",at=@At("TAIL"),remap=false)
    private void interstice$atlas(CallbackInfo ci){LowerOceanDh.restore(interstice$binding);interstice$binding=LowerOceanDh.bindIris(id);}
    @Inject(method="unbind",at=@At("TAIL"),remap=false)
    private void interstice$release(CallbackInfo ci){LowerOceanDh.restore(interstice$binding);interstice$binding=null;}
}
