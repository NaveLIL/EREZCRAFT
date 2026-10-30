package pro.erez.interstice.mixin;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CompletableFuture;

/** Sable initializes/updates virtual plot lighting in its own LevelLightEngine.
 * Vanilla's asynchronous LIGHT stage must not illuminate those same chunks in
 * the parent engine, whose section map intentionally has no plot data layers.
 */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class SkatPlotLightMixin {
    @Shadow @Final private ChunkMap chunkMap;
    @Unique private static final java.util.concurrent.atomic.AtomicLong interstice$virtualStages=new java.util.concurrent.atomic.AtomicLong();
    @Inject(method={"initializeLight","lightChunk"},at=@At("HEAD"),cancellable=true)
    private void interstice$plotOwnsLight(ChunkAccess chunk,boolean alreadyLit,CallbackInfoReturnable<CompletableFuture<ChunkAccess>> ci){
        if(!net.neoforged.fml.ModList.get().isLoaded("sable"))return;
        if(pro.erez.interstice.ship.SkatPlotLighting.engine(((SkatChunkMapAccessor)chunkMap).interstice$level(),chunk.getPos())==null)return;
        long count=interstice$virtualStages.incrementAndGet();
        if(count<=4&&System.getProperty("interstice.studioSmoke","").equals("skat"))System.out.println("SKAT_PLOT_LIGHT_STAGE "+chunk.getPos()+" parent="+getClass().getSimpleName());
        ci.setReturnValue(CompletableFuture.completedFuture(chunk));
    }
    @Inject(method="checkBlock",at=@At("HEAD"),cancellable=true)
    private void interstice$plotBlock(BlockPos pos,CallbackInfo ci){
        if(!net.neoforged.fml.ModList.get().isLoaded("sable"))return;
        var engine=pro.erez.interstice.ship.SkatPlotLighting.engine(((SkatChunkMapAccessor)chunkMap).interstice$level(),new ChunkPos(pos));
        if(engine!=null){engine.checkBlock(pos);ci.cancel();}
    }
}
