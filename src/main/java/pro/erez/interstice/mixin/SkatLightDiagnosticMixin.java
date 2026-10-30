package pro.erez.interstice.mixin;
import net.minecraft.core.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(LayerLightSectionStorage.class)
public abstract class SkatLightDiagnosticMixin {
    @Unique private static final boolean interstice$enabled=System.getProperty("interstice.studioSmoke","").equals("skat");
    @Unique private static final java.util.concurrent.atomic.AtomicInteger interstice$missing=new java.util.concurrent.atomic.AtomicInteger();
    @ModifyExpressionValue(method="getStoredLevel",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/lighting/LayerLightSectionStorage;getDataLayer(JZ)Lnet/minecraft/world/level/chunk/DataLayer;"))
    private DataLayer interstice$diagnose(DataLayer original,@Local(argsOnly=true)long block){
        if(original==null&&interstice$enabled&&interstice$missing.incrementAndGet()<=8)System.err.println("SKAT_MISSING_LIGHT "+BlockPos.of(block)+" section="+SectionPos.of(SectionPos.blockToSection(block))+" storage="+getClass().getSimpleName()+" thread="+Thread.currentThread().getName());
        return original;
    }
}
