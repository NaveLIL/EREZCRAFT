package pro.erez.interstice.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pro.erez.interstice.client.LowerOceanDh;
@Pseudo @Mixin(targets="com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer",remap=false)
public abstract class DhLowerOceanTextureDetailMixin {
    @Inject(method="applyTextureSetIds",at=@At("HEAD"),remap=false)
    private static void interstice$detail(@Coerce Object full,@Coerce Object view,@Coerce Object data,@Coerce Object source,int x,int z,CallbackInfo ci){LowerOceanDh.detail(source);}
    @Inject(method="applyTextureSetIds",at=@At("RETURN"),remap=false)
    private static void interstice$end(@Coerce Object full,@Coerce Object view,@Coerce Object data,@Coerce Object source,int x,int z,CallbackInfo ci){LowerOceanDh.DETAIL.remove();}
}
