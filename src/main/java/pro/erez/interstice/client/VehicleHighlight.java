package pro.erez.interstice.client;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.vehicle.VehicleAccess;
/** Whole vehicles retain interactions without a per-cell selection wireframe. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class VehicleHighlight {
    @SubscribeEvent public static void block(RenderHighlightEvent.Block e){var level=Minecraft.getInstance().level;if(level!=null&&(VehicleAccess.part(level.getBlockState(e.getTarget().getBlockPos()))||VehicleAccess.anchor(level,e.getTarget().getBlockPos())!=null))e.setCanceled(true);}
    private VehicleHighlight(){}
}
