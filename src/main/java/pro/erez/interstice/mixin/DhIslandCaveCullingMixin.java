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

/** Optional Distant Horizons 3.3.3 compatibility for the two seas and their air gap. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer", remap = false)
public abstract class DhIslandCaveCullingMixin {
    @Unique private static volatile Method interstice$wrappedWorldMethod;
    @Unique private static volatile Method interstice$hasCeilingMethod;

    @Redirect(
            method = "setRenderColumnView",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/IClientLevelWrapper;hasCeiling()Z"),
            remap = false)
    private static boolean interstice$preserveTallIslandAirGap(@Coerce Object wrapper) {
        try {
            if (interstice$hasCeilingMethod == null) interstice$initializeClientLevelAccess();
            Object world = interstice$wrappedWorldMethod.invoke(wrapper);
            if (world instanceof ClientLevel level
                    && level.dimension().location().getNamespace().equals("interstice")
                    && GeometryProfile.TALL.equals(GeometryProfiles.get(level))) {
                // This answer is local to DH's cave-culling heuristic. Its zero-skylight
                // approximation can extend the last upper-sea LOD down to a low plant,
                // across an air gap which was already skipped. Preserve that gap as DH
                // does for ceiling dimensions, without changing dimension flags or data.
                return true;
            }
            return (Boolean) interstice$hasCeilingMethod.invoke(wrapper);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Cannot read the native DH level for island cave culling", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read the native DH level for island cave culling", failure);
        }
    }

    // Public interface methods avoid coupling to a particular wrapper implementation.
    // Lazy initialization also keeps the optional DH dependency out of normal loading.
    @Unique private static synchronized void interstice$initializeClientLevelAccess() throws ReflectiveOperationException {
        if (interstice$hasCeilingMethod != null) return;
        Class<?> clientWrapper = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper");
        Method world = clientWrapper.getMethod("getWrappedMcObject");
        Method ceiling = clientWrapper.getMethod("hasCeiling");
        interstice$wrappedWorldMethod = world;
        interstice$hasCeilingMethod = ceiling;
    }
}
