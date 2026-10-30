package pro.erez.interstice.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pro.erez.interstice.client.LowerOceanDh;
@Pseudo @Mixin(targets="com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource",remap=false)
public abstract class DhLowerOceanColumnMixin {
    @Inject(method="texturedLodsEnabledAtDetailLevel",at=@At("RETURN"),cancellable=true,remap=false)
    private static void interstice$oceanMetadata(byte detail,CallbackInfoReturnable<Boolean> ci){if(LowerOceanDh.active())ci.setReturnValue(true);}
}
