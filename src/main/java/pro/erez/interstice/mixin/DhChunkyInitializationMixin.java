package pro.erez.interstice.mixin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DH3.3.3 sees initial FULL chunks before Chunky registers its server provider.
 * Defer only listener binding until that real provider exists; leave DH/Chunky work intact.
 * No external compile dependency, provider mutation, fake events or world edits.
 */
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractChunkyAccessor",remap=false)
public abstract class DhChunkyInitializationMixin {
    @Shadow(remap=false) private boolean listenerBound;
    @Unique private static volatile Method interstice$chunkyProvider;
    @Inject(method="tryRunFirstTimeSetup",at=@At("HEAD"),cancellable=true,remap=false)
    private void interstice$waitForChunkyServer(CallbackInfo ci){
        if(listenerBound||!ModList.get().isLoaded("chunky"))return;
        try{
            Method getter=interstice$chunkyProvider;
            if(getter==null){getter=Class.forName("org.popcraft.chunky.ChunkyProvider").getMethod("get");interstice$chunkyProvider=getter;}
            getter.invoke(null);
        }catch(InvocationTargetException notReady){
            if(notReady.getCause() instanceof IllegalStateException cause&&"Chunky is not loaded.".equals(cause.getMessage())){ci.cancel();return;}
            throw new IllegalStateException("Unexpected Chunky provider failure",notReady.getCause());
        }catch(ReflectiveOperationException incompatible){throw new IllegalStateException("Incompatible Chunky provider API",incompatible);}
    }
}
