package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real TCP observer of a separate owned dedicated process. */
@EventBusSubscriber(modid="interstice",value=Dist.CLIENT)
public final class FaunaNetworkSmoke {
    private static boolean started,done;
    private static long deadline;
    private static int titleTicks,titleReturnTicks,warmup,samples,frames;private static String lastScreen="";private static net.minecraft.world.entity.decoration.ArmorStand camera;
    private static final Map<String,List<Double>> steps=new LinkedHashMap<>();
    private static final JsonArray series=new JsonArray();
    private static final Map<String,Vec3> previous=new HashMap<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("interstice.faunaNetworkSmoke")||done)return;
        var mc=Minecraft.getInstance();
        try{
            if(!started){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null){titleTicks=0;return;}if(++titleTicks<60)return;
                if(!mc.gameDirectory.toPath().toAbsolutePath().toString().contains(".verification"))throw new IllegalStateException("Owned client required");
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(8);
                ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25566"),new ServerData("Owned fauna TCP","127.0.0.1:25566",ServerData.Type.OTHER),false,null);return;
            }
            if(System.nanoTime()>deadline)throw new IllegalStateException("Owned TCP probe timed out");
            if(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen){throw new IllegalStateException("Dedicated connection disconnected: "+mc.screen.getNarrationMessage().getString());}
            var screen=mc.screen==null?"world":mc.screen.getClass().getSimpleName();if(!screen.equals(lastScreen)){System.out.println("FAUNA_TCP_SCREEN "+screen);lastScreen=screen;}
            if(mc.level==null||mc.player==null){if(mc.screen instanceof TitleScreen&&++titleReturnTicks>100)throw new IllegalStateException("Dedicated connect returned to title before world join");return;}titleReturnTicks=0;
            var actors=new ArrayList<Entity>();
            for(var actor:mc.level.entitiesForRendering())if(actor.hasCustomName()&&Set.of("probe-hunter","probe-spitter","probe-vanilla").contains(actor.getCustomName().getString()))actors.add(actor);
            if(actors.size()!=3)return;
            if(camera==null){camera=new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND,mc.level);mc.options.hideGui=true;mc.options.fov().set(45);}var focus=actors.stream().filter(a->a.getCustomName().getString().equals("probe-hunter")).findFirst().orElseThrow();double x=focus.getX()+4,y=71.3,z=focus.getZ()+7;camera.setPos(x,y,z);camera.xo=x;camera.yo=y;camera.zo=z;camera.setYRot(150);camera.yRotO=150;camera.setXRot(0);camera.xRotO=0;mc.setCameraEntity(camera);if(++warmup<80)return;if(frames<40&&samples%2==0){net.minecraft.client.Screenshot.grab(mc.gameDirectory,String.format(java.util.Locale.ROOT,"tcp-hunter-walk-%03d.png",frames++),mc.getMainRenderTarget(),m->{});}
            for(var actor:actors){
                var name=actor.getCustomName().getString();var before=previous.put(name,actor.position());
                if(before!=null)steps.computeIfAbsent(name,k->new ArrayList<>()).add(actor.position().subtract(before).horizontalDistance());
            }
            var point=new JsonObject();point.addProperty("client_tick",samples);for(var actor:actors){var v=new JsonArray();v.add(actor.getX());v.add(actor.getY());v.add(actor.getZ());point.add(actor.getCustomName().getString(),v);}series.add(point);
            if(++samples<360)return;
            var report=new JsonObject();report.addProperty("passed",true);report.addProperty("tcp_separate_dedicated",true);report.addProperty("dh_loaded",ModList.get().isLoaded("distanthorizons"));report.addProperty("singleplayer_server_absent",mc.getSingleplayerServer()==null);
            report.addProperty("gait_frames",frames);report.add("series",series);if(steps.size()!=3)throw new IllegalStateException("Missing TCP motion actors");
            for(var entry:steps.entrySet()){
                var row=new JsonObject();var values=entry.getValue();double mean=values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
                int idle=0,longest=0;for(double value:values){idle=value<.005?idle+1:0;longest=Math.max(longest,idle);}double travelled=values.stream().mapToDouble(Double::doubleValue).sum();if(values.size()<350||travelled<15||(!entry.getKey().equals("probe-vanilla")&&longest>10))throw new IllegalStateException("TCP motion failed "+entry.getKey()+" travelled="+travelled+" idle="+longest);row.addProperty("travelled",travelled);row.addProperty("longest_idle_ticks",longest);row.addProperty("samples",values.size());row.addProperty("mean",mean);row.addProperty("stationary",values.stream().filter(v->v<.001).count());
                row.addProperty("max",values.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
                row.addProperty("coefficient_of_variation",Math.sqrt(values.stream().mapToDouble(v->(v-mean)*(v-mean)).average().orElseThrow())/Math.max(.0001,mean));report.add(entry.getKey(),row);
            }
            finish(mc,report);
        }catch(Throwable error){var r=new JsonObject();r.addProperty("passed",false);r.addProperty("error",error.toString());finish(mc,r);}
    }
    private static void finish(Minecraft mc,JsonObject report){
        done=true;
        try{Files.writeString(mc.gameDirectory.toPath().resolve("fauna-network-client.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){throw new IllegalStateException(error);}
        if(mc.level!=null){mc.level.disconnect();mc.disconnect(new TitleScreen());}mc.stop();
    }
}
