package pro.erez.interstice.client;
import java.util.*;
import com.google.gson.JsonObject;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.opengl.*;
/** Whole-frame timestamp pairs include DH and do not conflict with other mods' elapsed queries. */
@EventBusSubscriber(modid="interstice",value=Dist.CLIENT)
public final class WaveRenderTelemetry {
    private static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("toxinwaves")||System.getProperty("interstice.studioSmoke","").equals("skat");
    private static final List<Double> CPU=new ArrayList<>(),GPU=new ArrayList<>(),FRAMES=new ArrayList<>();
    private static final ArrayDeque<int[]> pending=new ArrayDeque<>();private static int current;private static long start,last;private static boolean recording;
    public static void begin(){CPU.clear();GPU.clear();FRAMES.clear();last=0;recording=true;}
    public static int frames(){return GPU.size();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void before(RenderFrameEvent.Pre e){if(!ACTIVE||!recording)return;long now=System.nanoTime();if(last!=0)FRAMES.add((now-last)/1e6);last=now;
        while(!pending.isEmpty()&&GL15.glGetQueryObjecti(pending.peek()[1],GL15.GL_QUERY_RESULT_AVAILABLE)!=0){int[] q=pending.remove();GPU.add((GL33.glGetQueryObjectui64(q[1],GL15.GL_QUERY_RESULT)-GL33.glGetQueryObjectui64(q[0],GL15.GL_QUERY_RESULT))/1e6);GL15.glDeleteQueries(q[0]);GL15.glDeleteQueries(q[1]);}if(pending.size()>8)return;start=now;current=GL15.glGenQueries();GL33.glQueryCounter(current,GL33.GL_TIMESTAMP);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(RenderFrameEvent.Post e){if(!ACTIVE||current==0)return;int end=GL15.glGenQueries();GL33.glQueryCounter(end,GL33.GL_TIMESTAMP);pending.add(new int[]{current,end});current=0;CPU.add((System.nanoTime()-start)/1e6);}
    private static double p(List<Double> list,double percentile){var a=new ArrayList<>(list);Collections.sort(a);return a.isEmpty()?0:a.get(Math.min(a.size()-1,(int)Math.floor(a.size()*percentile)));}
    public static JsonObject end(){recording=false;var j=new JsonObject();j.addProperty("scope","whole_frame_including_DH");j.addProperty("gpu_samples",GPU.size());j.addProperty("cpu_samples",CPU.size());j.addProperty("gpu_p50_ms",p(GPU,.5));j.addProperty("gpu_p95_ms",p(GPU,.95));j.addProperty("gpu_p99_ms",p(GPU,.99));j.addProperty("cpu_p50_ms",p(CPU,.5));j.addProperty("cpu_p95_ms",p(CPU,.95));j.addProperty("frame_p50_ms",p(FRAMES,.5));j.addProperty("frame_p95_ms",p(FRAMES,.95));j.addProperty("frame_p99_ms",p(FRAMES,.99));for(int[] q:pending){GL15.glDeleteQueries(q[0]);GL15.glDeleteQueries(q[1]);}pending.clear();return j;}
}
