package pro.erez.interstice.mixin;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Camera.class)
public interface EitanCameraAccess {
    @Accessor("eyeHeight") float interstice$getEye();
    @Accessor("eyeHeightOld") float interstice$getOldEye();
    @Accessor("eyeHeight") void interstice$setEye(float value);
    @Accessor("eyeHeightOld") void interstice$setOldEye(float value);
}
