package pro.erez.interstice.client;
import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.vehicle.*;
/** One real client connected over loopback TCP to the separate dedicated JVM. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class EitanNetworkNative {
    static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("eitannetwork");
    static final JsonObject REPORT=new JsonObject();static boolean started,done;static int phase,ticks,quiet;static long deadline;static double peak;static net.minecraft.world.phys.Vec3 first;
    static void need(boolean value,String why){if(!value)throw new IllegalStateException(why);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!ACTIVE)return;var mc=Minecraft.getInstance();try{
        if(done){if(mc.level==null&&++quiet>30)mc.stop();return;}
        if(!started){if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;need(mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().contains(".verification")&&Files.readString(mc.gameDirectory.toPath().resolve("eitan-automated-network-profile")).trim().equals("disposable-eitan-loopback"),"Owned client required");started=true;deadline=System.nanoTime()+900_000_000_000L;ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25576"),new ServerData("Eitan owned TCP","127.0.0.1:25576",ServerData.Type.OTHER),false,null);return;}
        need(System.nanoTime()<deadline,"Dedicated client deadline at phase "+phase);if(mc.screen instanceof DisconnectedScreen)throw new IllegalStateException("Dedicated disconnected: "+mc.screen.getNarrationMessage().getString());if(mc.level==null||mc.player==null)return;need(mc.getSingleplayerServer()==null,"Dedicated test accidentally integrated");mc.mouseHandler.releaseMouse();mc.options.hideGui=true;
        var c=EitanClient.vehicle();if(c==null){need(++ticks<400,"Dedicated passenger not tracked");return;}peak=Math.max(peak,c.speed);
        if(phase==0){if(++ticks<60)return;need(c.pilot(mc.player),"Dedicated driver identity mismatch");first=mc.player.position();REPORT.addProperty("dedicated_driver_spawn",true);phase=1;ticks=0;return;}
        if(phase==1){mc.options.keyUp.setDown(true);if(++ticks<120)return;KeyMapping.releaseAll();mc.options.keyJump.setDown(true);double distance=mc.player.position().distanceTo(first);REPORT.addProperty("forward_distance_m",distance);need(distance>12&&peak>4.3,"TCP drive failed: "+distance+" peak="+peak);phase=2;ticks=0;return;}
        if(phase==2){mc.options.keyJump.setDown(true);if(++ticks<70)return;need(c.speed<.35,"TCP brake failed");KeyMapping.releaseAll();EitanClient.action(c,1,0);phase=3;ticks=0;return;}
        if(phase==3){if(++ticks<25)return;need(!c.lights,"TCP lights action not synchronized");EitanClient.action(c,1,0);phase=4;ticks=0;return;}
        if(phase==4){need(EitanCameras.failure.isEmpty(),"Dedicated camera: "+EitanCameras.failure);if(++ticks<25||EitanCameras.captures<3){need(ticks<300,"Dedicated camera not updating");return;}need(c.lights,"TCP light restore failed");Screenshot.grab(mc.gameDirectory,"eitan-network-driver.png",mc.getMainRenderTarget(),m->{});REPORT.addProperty("camera_captures",EitanCameras.captures);var udp=((dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension)mc.getConnection().getConnection()).sable$getUDPChannel();REPORT.addProperty("sable_udp_channel_active",udp!=null&&udp.isActive());REPORT.addProperty("lights_roundtrip",true);finish(mc,true,"");}
    }catch(Throwable error){error.printStackTrace();finish(mc,false,error.toString());}}
    static void finish(Minecraft mc,boolean pass,String error){KeyMapping.releaseAll();done=true;REPORT.addProperty("passed",pass);REPORT.addProperty("error",error);REPORT.addProperty("peak_m_s",peak);REPORT.addProperty("real_tcp_dedicated",mc.getSingleplayerServer()==null);REPORT.addProperty("dh_loaded",net.neoforged.fml.ModList.get().isLoaded("distanthorizons"));REPORT.addProperty("owner_profile_used",false);try{Files.writeString(mc.gameDirectory.toPath().resolve("eitan-network-client.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());}
    private EitanNetworkNative(){}
}
