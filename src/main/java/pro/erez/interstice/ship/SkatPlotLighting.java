package pro.erez.interstice.ship;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
/** Optional Sable linkage stays outside vanilla mixin bytecode preprocessing. */
public final class SkatPlotLighting {
    public static LevelLightEngine engine(ServerLevel level,ChunkPos pos){
        var container=SubLevelContainer.getContainer(level);if(container==null||!container.inBounds(pos))return null;
        var plot=container.getPlot(pos);return plot==null||plot.getSubLevel().isRemoved()?null:plot.getLightEngine();
    }
    private SkatPlotLighting(){}
}
