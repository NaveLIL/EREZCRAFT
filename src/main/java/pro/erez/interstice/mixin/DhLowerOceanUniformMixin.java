package pro.erez.interstice.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pro.erez.interstice.client.LowerOceanDh;
@Pseudo @Mixin(targets="com.seibel.distanthorizons.common.render.openGl.terrain.GlDhTerrainShaderProgram_neoforge",remap=false)
public abstract class DhLowerOceanUniformMixin {
    @Shadow public abstract int getId();
    @Unique private LowerOceanDh.Binding interstice$saved;
    @Inject(method="fillUniformData",at=@At("TAIL"),remap=false)
    private void interstice$uniforms(@Coerce Object params,CallbackInfo ci){LowerOceanDh.restore(interstice$saved);interstice$saved=LowerOceanDh.bind(getId());}
    @Inject(method="unbind",at=@At("TAIL"),remap=false)
    private void interstice$restore(CallbackInfo ci){LowerOceanDh.restore(interstice$saved);interstice$saved=null;}
}
