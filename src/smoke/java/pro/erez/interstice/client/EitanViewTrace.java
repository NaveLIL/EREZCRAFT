package pro.erez.interstice.client;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector3d;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.mixin.EitanCameraAccess;
import pro.erez.interstice.vehicle.*;
/** Measures the real driver's camera against the same interpolated body used for drawing. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class EitanViewTrace {
    static final JsonArray SERIES=new JsonArray();static final java.util.Map<Integer,Double> PHASE_MAX=new java.util.LinkedHashMap<>();static int frames,stable;static double max,restMax;static long stableTick=-1;
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!EitanDriveNative.ACTIVE&&!EitanFieldNative.ACTIVE)return;var mc=Minecraft.getInstance();var c=EitanClient.vehicle();if(c==null||!(mc.player.getVehicle() instanceof EitanSeatEntity seat)||seat.seatIndex()!=0||!(c.body() instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel body)||!mc.options.getCameraType().isFirstPerson())return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);var pose=body.renderPose(partial);var s=EitanData.SEATS.get(0);var o=c.origin();var eye=(EitanCameraAccess)mc.gameRenderer.getMainCamera();double eyeHeight=net.minecraft.util.Mth.lerp(partial,eye.interstice$getOldEye(),eye.interstice$getEye());var expected=pose.transformPosition(new Vector3d(o.getX()+s.x(),o.getY()+s.y()-.62+eyeHeight,o.getZ()+s.z()+.16));var actual=mc.gameRenderer.getMainCamera().getPosition();double error=expected.distance(actual.x,actual.y,actual.z);max=Math.max(max,error);int phase=EitanFieldNative.ACTIVE?EitanFieldNative.phase:EitanDriveNative.phase;PHASE_MAX.merge(phase,error,Math::max);
        long tick=mc.level.getGameTime();if(tick!=stableTick){stableTick=tick;stable=c.speed<.05?stable+1:0;}if(stable>20)restMax=Math.max(restMax,error);
        if(++frames%20==0&&SERIES.size()<1500){var row=new JsonObject();row.addProperty("phase",phase);row.addProperty("frame",frames);row.addProperty("speed",c.speed);row.addProperty("eye_residual_m",error);row.addProperty("rest",stable>20);SERIES.add(row);}
    }
    public static JsonObject result(){var r=new JsonObject();r.addProperty("frames",frames);r.addProperty("max_eye_residual_m",max);r.addProperty("rest_max_eye_residual_m",restMax);var phases=new JsonObject();PHASE_MAX.forEach((p,v)->phases.addProperty(String.valueOf(p),v));r.add("phase_max_m",phases);r.add("series",SERIES);return r;}
    private EitanViewTrace(){}
}
