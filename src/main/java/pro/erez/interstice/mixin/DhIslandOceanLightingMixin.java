package pro.erez.interstice.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import pro.erez.interstice.OceanLiquidBlock;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;

/** Match the existing full-bright upper sea in DH's render data, not world data. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer", remap = false)
public abstract class DhIslandOceanLightingMixin {
    @Unique private static volatile Method interstice$oceanWrappedWorldMethod;
    @Unique private static volatile Method interstice$wrappedStateMethod;
    @Unique private static volatile Method interstice$blockStateMethod;
    @Unique private static volatile Method interstice$dataIdMethod;
    @Unique private static volatile Method interstice$blockLightMethod;
    @Unique private static volatile Method interstice$skyLightMethod;
    @Unique private static volatile Field interstice$mappingField;

    @Redirect(method = "setRenderColumnView",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/core/util/FullDataPointUtil;getBlockLight(J)I"),
            remap = false)
    private static int interstice$upperSeaBlockLight(long data, @Coerce Object wrapper, @Coerce Object fullSource,
            int blockX, int blockZ, @Coerce Object renderView, @Coerce Object dataColumn, @Coerce Object blockPos) {
        return interstice$readOceanRenderLight(data, wrapper, fullSource, false);
    }

    @Redirect(method = "setRenderColumnView",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/core/util/FullDataPointUtil;getSkyLight(J)I"),
            remap = false)
    private static int interstice$upperSeaSkyLight(long data, @Coerce Object wrapper, @Coerce Object fullSource,
            int blockX, int blockZ, @Coerce Object renderView, @Coerce Object dataColumn, @Coerce Object blockPos) {
        return interstice$readOceanRenderLight(data, wrapper, fullSource, true);
    }

    @Unique private static int interstice$readOceanRenderLight(long data, Object wrapper, Object fullSource, boolean sky) {
        try {
            if (interstice$skyLightMethod == null) interstice$initializeOceanLightAccess();
            if (interstice$isFullBrightUpperSea(data, wrapper, fullSource)) {
                // FluidClient.renderOcean uses LightTexture.FULL_BRIGHT (both channels 15).
                // DH saves light above a homogeneous segment; our bedrock roof has no
                // light, so that sample cannot represent the emitting sea below it.
                return 15;
            }
            return ((Number) (sky ? interstice$skyLightMethod : interstice$blockLightMethod).invoke(null, data)).intValue();
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Cannot read DH render lighting for the upper sea", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read DH render lighting for the upper sea", failure);
        }
    }

    @Unique private static boolean interstice$isFullBrightUpperSea(long data, Object wrapper, Object fullSource)
            throws ReflectiveOperationException {
        Object world = interstice$oceanWrappedWorldMethod.invoke(wrapper);
        if (!(world instanceof ClientLevel level)
                || !level.dimension().location().getNamespace().equals("interstice")
                || !GeometryProfile.TALL.equals(GeometryProfiles.get(level))) return false;

        int id = ((Number) interstice$dataIdMethod.invoke(null, data)).intValue();
        Object mapping = interstice$mappingField.get(fullSource);
        if (mapping == null) return false;
        Object block;
        try {
            block = interstice$blockStateMethod.invoke(mapping, id);
        } catch (InvocationTargetException failure) {
            // Preserve DH's later handling of a broken mapping: it logs and skips it.
            if (failure.getCause() instanceof IndexOutOfBoundsException) return false;
            throw failure;
        }
        if (block == null) return false;
        Object nativeState = interstice$wrappedStateMethod.invoke(block);
        return nativeState instanceof BlockState state && state.getBlock() instanceof OceanLiquidBlock;
    }

    @Unique private static synchronized void interstice$initializeOceanLightAccess() throws ReflectiveOperationException {
        if (interstice$skyLightMethod != null) return;
        Class<?> clientWrapper = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper");
        Class<?> blockWrapper = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper");
        Class<?> source = Class.forName("com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2");
        Class<?> mapping = Class.forName("com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap");
        Class<?> point = Class.forName("com.seibel.distanthorizons.core.util.FullDataPointUtil");
        Method world = clientWrapper.getMethod("getWrappedMcObject");
        Method state = blockWrapper.getMethod("getWrappedMcObject");
        Method lookup = mapping.getMethod("getBlockStateWrapper", int.class);
        Method id = point.getMethod("getId", long.class);
        Method blockLight = point.getMethod("getBlockLight", long.class);
        Method skyLight = point.getMethod("getSkyLight", long.class);
        Field map = source.getField("mapping");
        interstice$oceanWrappedWorldMethod = world;
        interstice$wrappedStateMethod = state;
        interstice$blockStateMethod = lookup;
        interstice$dataIdMethod = id;
        interstice$blockLightMethod = blockLight;
        interstice$mappingField = map;
        interstice$skyLightMethod = skyLight;
    }
}
