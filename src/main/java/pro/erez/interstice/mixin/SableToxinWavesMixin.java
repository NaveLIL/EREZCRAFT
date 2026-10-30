package pro.erez.interstice.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="dev.ryanhcode.sable.sublevel.ServerSubLevel",remap=false)
public abstract class SableToxinWavesMixin {
    @Inject(method="prePhysicsTick",at=@At("TAIL"),remap=false)
    private void interstice$ocean(@Coerce Object system,@Coerce Object handle,double dt,CallbackInfo ci){pro.erez.interstice.ocean.SableOceanBridge.step(this,system,handle,dt);}
}
