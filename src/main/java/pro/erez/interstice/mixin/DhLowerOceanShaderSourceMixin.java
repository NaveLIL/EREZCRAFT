package pro.erez.interstice.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pro.erez.interstice.client.LowerOceanDh;
@Pseudo @Mixin(targets="com.seibel.distanthorizons.common.render.openGl.glObject.shader.GlShader",remap=false)
public abstract class DhLowerOceanShaderSourceMixin {
    @Inject(method="loadFile",at=@At("RETURN"),cancellable=true,remap=false)
    private static void interstice$source(String path,boolean absolute,CallbackInfoReturnable<String> ci){ci.setReturnValue(LowerOceanDh.shader(path,ci.getReturnValue()));}
}
