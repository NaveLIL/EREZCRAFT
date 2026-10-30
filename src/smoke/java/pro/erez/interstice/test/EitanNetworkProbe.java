package pro.erez.interstice.test;
import com.google.gson.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.vehicle.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** Disposable dedicated process. Movement commands only arrive through normal client payloads. */
@EventBusSubscriber(modid=Interstice.ID)
public final class EitanNetworkProbe {
    static final boolean ACTIVE=Boolean.getBoolean("interstice.eitanNetworkProbe");
    static final JsonObject REPORT=new JsonObject();static final JsonArray SAMPLES=new JsonArray();
    static ServerLevel world;static EitanController c;static ServerSubLevel body;static CompletableFuture<ServerSubLevel> assembly;
    static boolean mounted,done;static int stable,ticks,absent;static long deadline;static Vec3 first;static double peak,distance;static int poweredTicks;static boolean priorLights=true,lightsChanged;
    static void need(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    static Vec3 point(){return c.world(0,1,0);}
    static void write(String name,JsonObject data)throws Exception{Files.writeString(Path.of(name),new GsonBuilder().setPrettyPrinting().create().toJson(data));}
    @SubscribeEvent public static void start(ServerStartedEvent event){if(!ACTIVE)return;try{need(Files.readString(Path.of("eitan-automated-network-profile")).trim().equals("disposable-eitan-loopback"),"Owned dedicated marker required");REPORT.addProperty("dh_loaded",net.neoforged.fml.ModList.get().isLoaded("distanthorizons"));need(REPORT.get("dh_loaded").getAsBoolean(),"DH required");REPORT.addProperty("dedicated",event.getServer().isDedicatedServer());REPORT.addProperty("owner_profile_used",false);deadline=System.nanoTime()+1_200_000_000_000L;world=event.getServer().overworld();world.setDayTime(6000);world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());for(int x=-40;x<=40;x++)for(int z=-65;z<=30;z++){world.getChunkAt(new BlockPos(x,199,z));world.setBlock(new BlockPos(x,199,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);}assembly=EitanAssembly.create(world,new BlockPos(0,200,0));}catch(Throwable error){finish(event.getServer(),false,error.toString());}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!ACTIVE||done||world==null)return;try{if(Files.exists(Path.of("eitan-probe-stop"))){finish(event.getServer(),false,"Stopped by owned verification runner");return;}need(System.nanoTime()<deadline,"Dedicated Eitan deadline");if(assembly!=null){if(!assembly.isDone())return;body=assembly.join();assembly=null;var b=body.getPlot().getBoundingBox();for(int x=b.minX();x<=b.maxX();x++)for(int y=b.minY();y<=b.maxY();y++)for(int z=b.minZ();z<=b.maxZ();z++)if(world.getBlockEntity(new BlockPos(x,y,z)) instanceof EitanController found)c=found;need(c!=null,"Dedicated controller not assembled");REPORT.addProperty("body_uuid",body.getUniqueId().toString());}
        if(c==null)return;if(++stable==80){var ready=new JsonObject();ready.addProperty("ready",true);ready.addProperty("dedicated",true);write("eitan-network-ready.json",ready);}if(stable<80)return;
        var players=event.getServer().getPlayerList().getPlayers();if(players.isEmpty()){if(mounted&&++absent>40){need(c.brake&&Math.abs(c.driveEffort)<.001,"Disconnect did not brake chassis");need(distance>12&&peak>4.3&&poweredTicks>40,"Dedicated vehicle did not actually drive");need(lightsChanged,"No interactive light command reached dedicated controller");REPORT.addProperty("disconnect_brake_checked",true);finish(event.getServer(),true,"");}return;}absent=0;var player=players.getFirst();if(!mounted){player.setGameMode(GameType.SURVIVAL);REPORT.addProperty("survival_driver",true);var p=c.world(-.68,.55,-2.21);player.teleportTo(world,p.x,p.y,p.z,Set.of(),180,28);need(EitanSeatEntity.mount(c,0,player),"Dedicated driver mount failed");first=point();mounted=true;}
        ticks++;peak=Math.max(peak,c.speed);distance=Math.max(distance,point().distanceTo(first));if(Math.abs(c.driveEffort)>1)poweredTicks++;lightsChanged|=c.lights!=priorLights;priorLights=c.lights;need(Math.abs(player.getX())<1000&&Math.abs(player.getZ())<1000,"Virtual plot coordinates leaked to real player");if(ticks%10==0){var s=new JsonObject();s.addProperty("tick",ticks);s.addProperty("speed",c.speed);s.addProperty("distance",point().distanceTo(first));s.addProperty("driver",c.pilot(player));s.addProperty("brake",c.brake);s.addProperty("effort",c.driveEffort);SAMPLES.add(s);}
    }catch(Throwable error){finish(event.getServer(),false,error.toString());}}
    static void finish(net.minecraft.server.MinecraftServer server,boolean pass,String error){if(done)return;done=true;REPORT.addProperty("passed",pass);REPORT.addProperty("error",error);REPORT.addProperty("peak_m_s",peak);REPORT.addProperty("max_distance_m",distance);REPORT.addProperty("powered_ticks",poweredTicks);REPORT.addProperty("lights_changed",lightsChanged);REPORT.add("samples",SAMPLES);try{write("eitan-network-server.json",REPORT);server.saveEverything(false,true,true);}catch(Exception e){e.printStackTrace();}server.halt(false);}
    private EitanNetworkProbe(){}
}
