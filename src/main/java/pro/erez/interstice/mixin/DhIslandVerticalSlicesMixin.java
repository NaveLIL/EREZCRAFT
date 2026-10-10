package pro.erez.interstice.mixin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;

/** Preserve enough DH render slices for the tall world's seas, shell and land. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer", remap = false)
public abstract class DhIslandVerticalSlicesMixin {
    @Unique private static volatile Method interstice$slicesWrappedWorldMethod;
    @Unique private static volatile Method interstice$calculateSliceLimitMethod;

    @Redirect(method = "transformCompleteFullDataToColumnData",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/api/enums/config/EDhApiVerticalQuality;calculateMaxNumberOfVerticalSlicesAtDetailLevel(B)I"),
            remap = false)
    private static int interstice$preserveTallSeaSlices(@Coerce Object quality, byte detail,
            @Coerce Object wrapper, @Coerce Object fullSource) {
        try {
            if (interstice$calculateSliceLimitMethod == null) interstice$initializeSliceLimitAccess();
            int original = ((Number) interstice$calculateSliceLimitMethod.invoke(quality, detail)).intValue();
            Object world = interstice$slicesWrappedWorldMethod.invoke(wrapper);
            if (world instanceof ClientLevel level
                    && level.dimension().location().getNamespace().equals("interstice")
                    && GeometryProfile.TALL.equals(GeometryProfiles.get(level))) {
                // Small ordinary-world quotas can merge the one-block bedrock shell
                // into an entire upper sea and retain bedrock's material and lighting.
                // Keep the user's higher quality; the floor applies only to render data.
                return Math.max(original, 32);
            }
            return original;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Cannot read the native DH vertical slice limit", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read the native DH vertical slice limit", failure);
        }
    }

    @Unique private static synchronized void interstice$initializeSliceLimitAccess() throws ReflectiveOperationException {
        if (interstice$calculateSliceLimitMethod != null) return;
        Class<?> clientWrapper = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper");
        Class<?> quality = Class.forName("com.seibel.distanthorizons.api.enums.config.EDhApiVerticalQuality");
        Method world = clientWrapper.getMethod("getWrappedMcObject");
        Method calculate = quality.getMethod("calculateMaxNumberOfVerticalSlicesAtDetailLevel", byte.class);
        interstice$slicesWrappedWorldMethod = world;
        interstice$calculateSliceLimitMethod = calculate;
    }
}
