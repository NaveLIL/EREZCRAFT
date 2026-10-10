package pro.erez.interstice.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

/** DH3.3.3's rough surface samples density directly and fills a single solid column.
 * That approximation cannot preserve our floating land, two seas and world shell.
 * Advertise only the detail that DH can obtain from the real chunk generator;
 * DH's existing queue splits coarser requests and aggregates the generated chunks.
 * Global configuration, ordinary dimensions and explicit surface-only mode remain intact.
 */
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.generation.DhWorldGenerator",remap=false)
public abstract class DhIslandGeneratorMixin {
    @Unique private boolean interstice$islandGenerator;
    @Unique private static volatile Object interstice$planEntry;
    @Unique private static volatile Field interstice$chunkGenerationEnabled;
    @Unique private static volatile Method interstice$readPlan;

    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void interstice$bindNativeGenerator(@Coerce Object dhServerLevel,CallbackInfo ci) {
        try {
            Object wrapper=dhServerLevel.getClass().getMethod("getServerLevelWrapper").invoke(dhServerLevel);
            Object nativeLevel=wrapper.getClass().getMethod("getWrappedMcObject").invoke(wrapper);
            interstice$islandGenerator=nativeLevel instanceof ServerLevel level
                    && level.getChunkSource().getGenerator() instanceof IslandChunkGenerator;
            if(interstice$islandGenerator) interstice$initializePlanAccess();
        } catch(ReflectiveOperationException | IllegalArgumentException incompatible) {
            throw new IllegalStateException("Incompatible Distant Horizons native-level API; Interstice DH compatibility targets 3.3.3",incompatible);
        }
    }

    @Unique private static synchronized void interstice$initializePlanAccess() throws ReflectiveOperationException {
        if(interstice$readPlan!=null) return;
        Class<?> settings=Class.forName("com.seibel.distanthorizons.core.config.Config$Common$WorldGenerator");
        Object entry=settings.getField("generatorPlan").get(null);
        Method read=entry.getClass().getMethod("get");
        Class<?> plan=Class.forName("com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiGeneratorPlan");
        Field enabled=plan.getField("chunkGenEnabled");
        if(enabled.getType()!=boolean.class)
            throw new NoSuchFieldException("Distant Horizons generator-plan chunkGenEnabled must be boolean");
        interstice$planEntry=entry;
        interstice$chunkGenerationEnabled=enabled;
        interstice$readPlan=read;
    }

    @Inject(method="getLargestDataDetailLevel()B",at=@At("HEAD"),cancellable=true,remap=false)
    private void interstice$useActualIslandChunks(CallbackInfoReturnable<Byte> ci) {
        if(!interstice$islandGenerator) return;
        try {
            // Read the live entry: changing the user's plan must still take effect.
            Object plan=interstice$readPlan.invoke(interstice$planEntry);
            if(interstice$chunkGenerationEnabled.getBoolean(plan)) ci.setReturnValue((byte)0);
        } catch(ReflectiveOperationException | IllegalArgumentException incompatible) {
            throw new IllegalStateException("Incompatible Distant Horizons generation-plan API; Interstice DH compatibility targets 3.3.3",incompatible);
        }
    }
}
