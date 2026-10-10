package pro.erez.interstice.client;

import com.google.gson.*;
import com.seibel.distanthorizons.api.DhApi;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.test.ForestCaveInspection;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.terrain.*;

/** Six fresh natural FULL worlds, native eye-height captures and a second-JVM cold read. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class ForestCaveV6VisualSmoke {
    private static final String MODE=System.getProperty("interstice.forestCaveSmoke","");
    private static final long[] SEEDS={0,1,-1,20261006,76198123,4294967297L};
    private static boolean started,finished,failed,shotSaved,worldClosing;
    private static int seedIndex,stage,ticks,capture;
    private static long deadline;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static V6ForestCavePlanner.Network network;
    private static JsonObject data=new JsonObject(),row;
    private static JsonArray worlds=new JsonArray();
    @SubscribeEvent public static void server(ServerTickEvent.Post event){
        if(MODE.isEmpty())return;
        TideManager.getSavedData(event.getServer()).setPhase(TidePhase.CALM,24000);
        var session=settling;if(session!=null)session.tick(event.getServer());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(!started&&mc.screen instanceof TitleScreen){
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(40);
                require(mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').contains("/.verification/"),"Native cave test needs an isolated profile");
                mc.getWindow().setTitle("EREZCRAFT - isolated V6 cave verification");
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);mc.options.gamma().set(.5);mc.options.fov().set(70);mc.options.bobView().set(false);
                require(DhApi.Delayed.configs!=null,"Distant Horizons public configuration API did not initialize");
                require(DhApi.Delayed.configs.graphics().chunkRenderDistance().setValue(32),"Cannot set declared32-chunk diagnostic LOD radius");
                require(DhApi.Delayed.configs.graphics().renderingEnabled().setValue(true),"Cannot enable upstream DH renderer");
                require(DhApi.Delayed.configs.worldGenerator().enableDistantWorldGeneration().setValue(true),"Cannot enable actual DH distant generation");
                DhApi.Delayed.configs.multiThreading().threadCount().setValue(1);
                data.addProperty("diagnostic_vanilla_distance",3);data.addProperty("diagnostic_dh_lod_radius",32);
                data.addProperty("diagnostic_dh_worker_threads",DhApi.Delayed.configs.multiThreading().threadCount().getValue());
                var collectors=new JsonArray();for(var gc:java.lang.management.ManagementFactory.getGarbageCollectorMXBeans())collectors.add(gc.getName());data.add("garbage_collectors",collectors);
                if(MODE.equals("reload")){
                    data=JsonParser.parseString(Files.readString(report(mc,"create"))).getAsJsonObject();require(data.get("passed").getAsBoolean(),"Creation did not pass");require(data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Cold verification reused creator JVM");worlds=data.getAsJsonArray("worlds");
                }else{require(MODE.equals("create"),"Unknown cave test mode");data.addProperty("creator_pid",ProcessHandle.current().pid());data.add("worlds",worlds);}
                open(mc);return;
            }
            if(!started)return;
            if(stage==90){
                if(settling.ready()){if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());finished=true;write(mc);mc.stop();}return;
            }
            require(System.nanoTime()<deadline,"Cave native deadline stage="+stage+" seed="+SEEDS[seedIndex]);
            if(stage==10){if(mc.getSingleplayerServer()==null&&mc.level==null&&mc.screen instanceof TitleScreen){open(mc);stage=0;}return;}
            if(SmokeWorldPrompts.advance(mc))return;
            if(mc.player==null||mc.level==null||mc.screen!=null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            if(stage==0){
                work=server.submit(()->{
                    var level=server.getLevel(IslandWorld.TENSION_WORLD);require(level!=null&&level.getSeed()==SEEDS[seedIndex],"Actual V6 seed mismatch");
                    var sampler=NativeColumnSamplerV6.of(level.getChunkSource().randomState());network=first(sampler);var metrics=ForestCaveInspection.inspect(level,network);
                    if(MODE.equals("reload")){require(server.getPlayerList().getPlayer(id).serverLevel().dimension().equals(IslandWorld.TENSION_WORLD),"Saved player left V6");require(snapshot(level,network).equals(row.getAsJsonObject("snapshot")),"Cold FULL cave/sea/solid geometry changed");row.add("cold_full",metrics);row.addProperty("reload_pid",ProcessHandle.current().pid());}
                    else{row.add("natural_full",metrics);row.add("snapshot",snapshot(level,network));}
                    pose(server.getPlayerList().getPlayer(id),level,0);return true;
                });stage=1;ticks=0;return;
            }
            if(stage==1&&done()){
                var p=position(capture);if(!mc.level.dimension().equals(IslandWorld.TENSION_WORLD)||mc.player.position().distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5)>.15||!mc.levelRenderer.isSectionCompiled(p)){ticks=0;return;}
                if(++ticks<60)return;
                require(DhApi.Delayed.worldProxy!=null&&DhApi.Delayed.worldProxy.worldLoaded(),"DH did not attach to the actual client world");
                var depth=DhApi.Delayed.renderProxy.getDhDepthTextureGlId();
                if(!depth.success||depth.payload==null||depth.payload<=0)return;
                row.addProperty("dh_world_attached",true);row.addProperty("dh_depth_texture_gl_id",depth.payload);
                shotSaved=false;String name="forest-cave-seed-"+SEEDS[seedIndex]+"-"+MODE+"-"+capture+".png";
                var frame=new JsonObject();frame.addProperty("file",name);frame.addProperty("dimension",mc.level.dimension().location().toString());frame.addProperty("camera",p.toShortString());frame.addProperty("night_vision",capture==2);row.getAsJsonArray("captures").add(frame);
                Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{shotSaved=true;System.out.println("V6_FOREST_CAVE_SHOT "+name);});stage=2;return;
            }
            if(stage==2&&shotSaved){
                if(MODE.equals("create")&&++capture<4){work=server.submit(()->{pose(server.getPlayerList().getPlayer(id),server.getLevel(IslandWorld.TENSION_WORLD),capture);return true;});stage=1;ticks=0;return;}
                // End the bounded observation normally; stop submitting new distant work
                // while pending real vanilla/DH generation and saving complete.
                DhApi.Delayed.configs.worldGenerator().enableDistantWorldGeneration().setValue(false);
                row.addProperty(MODE+"_passed",true);worldClosing=true;settling=new NativeChunkSettler.Session("forest_cave_"+MODE+"_"+SEEDS[seedIndex]+"_shutdown",6000);stage=3;write(mc);return;
            }
            if(stage==3){
                if(settling.failed())throw new IllegalStateException(settling.failure());if(!settling.ready())return;
                row.add(MODE+"_settle",settling.report());settling=null;
                if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());require(server.isStopped(),"World disconnect did not stop integrated server");worldClosing=false;write(mc);
                if(++seedIndex<SEEDS.length){stage=10;return;}
                data.addProperty("passed",!failed);data.addProperty("finished",true);data.addProperty("cold_restart_performed",MODE.equals("reload"));data.addProperty("scope","Six real fresh FULL V6 worlds, Creative eye-height cameras, independent walking graphs. No terrain edits, no human Survival or dedicated multiplayer claim.");finished=true;write(mc);mc.stop();
            }
        }catch(Throwable error){error.printStackTrace();failed=true;data.addProperty("passed",false);data.addProperty("error",error.toString());write(mc);
            if(!worldClosing&&mc.getSingleplayerServer()!=null){worldClosing=true;settling=new NativeChunkSettler.Session("forest_cave_failure_shutdown",6000);stage=90;}
            if(stage==90&&settling.ready()){if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());finished=true;write(mc);mc.stop();}
            else if(mc.getSingleplayerServer()==null){finished=true;mc.stop();}
        }
    }
    private static void open(Minecraft mc){
        capture=0;work=null;network=null;ticks=0;
        DhApi.Delayed.configs.worldGenerator().enableDistantWorldGeneration().setValue(true);
        if(MODE.equals("create")){
            require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/"+name()+"/level.dat")),"Refusing to replace an existing cave world");
            row=new JsonObject();row.addProperty("seed",SEEDS[seedIndex]);row.addProperty("save_name",name());row.add("captures",new JsonArray());worlds.add(row);
            var rules=new GameRules();rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel(name(),new LevelSettings("Natural forest caves V6 "+SEEDS[seedIndex],GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(SEEDS[seedIndex],false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
        }else{row=worlds.get(seedIndex).getAsJsonObject();mc.createWorldOpenFlows().openWorld(name(),()->{});}
        System.out.println("V6_FOREST_CAVE_OPEN mode="+MODE+" seed="+SEEDS[seedIndex]);write(mc);
    }
    private static V6ForestCavePlanner.Network first(NativeColumnSamplerV6 sampler){
        for(int cx=-4;cx<=3;cx++)for(int cz=-4;cz<=3;cz++){var plans=sampler.forestCaves().plansInCell(cx,cz);if(!plans.isEmpty())return plans.getFirst();}
        throw new IllegalStateException("No network in declared64-cell radius512 window");
    }
    private static BlockPos position(int frame){return frame==0||frame==3?network.entrance().getLast():network.hub();}
    private static void pose(ServerPlayer player,ServerLevel level,int frame){
        var p=position(frame);var target=frame==0?network.entrance().get(Math.max(0,network.entrance().size()-6)):network.branches().getFirst().get(Math.min(8,network.branches().getFirst().size()-1));
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk((p.getX()>>4)+dx,(p.getZ()>>4)+dz);
        double x=target.getX()-p.getX(),z=target.getZ()-p.getZ();
        player.teleportTo(level,p.getX()+.5,p.getY(),p.getZ()+.5,Set.of(),frame==3?0:(float)(Math.toDegrees(Math.atan2(z,x))-90),frame==0?18:frame==3?-5:0);
        player.setDeltaMovement(0,0,0);player.getAbilities().flying=true;player.onUpdateAbilities();
        if(frame==2)player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));else player.removeEffect(MobEffects.NIGHT_VISION);
    }
    private static JsonObject snapshot(ServerLevel level,V6ForestCavePlanner.Network n){
        try{var result=new JsonObject();var manifest=new JsonArray();
            for(var pos:ForestCaveInspection.chunks(n)){var chunk=level.getChunk(pos.x,pos.z);var digest=MessageDigest.getInstance("SHA-256");
                for(int x=pos.getMinBlockX();x<=pos.getMaxBlockX();x++)for(int z=pos.getMinBlockZ();z<=pos.getMaxBlockZ();z++)for(int y=0;y<=80;y++){
                    var state=chunk.getBlockState(new BlockPos(x,y,z));
                    // Stable collision/fluid geometry, excluding random flora state and actors.
                    digest.update((byte)(state.getFluidState().isEmpty()?0:1));digest.update((byte)(state.isCollisionShapeFullBlock(level,new BlockPos(x,y,z))?1:0));
                }
                var row=new JsonObject();row.addProperty("x",pos.x);row.addProperty("z",pos.z);row.addProperty("stable_geometry_y0_80",java.util.HexFormat.of().formatHex(digest.digest()));manifest.add(row);
            }result.add("chunks",manifest);return result;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void require(boolean ok,String why){ForestCaveInspection.require(ok,why);}
    private static String name(){return "forest-caves-v6-seed-"+SEEDS[seedIndex];}
    private static Path report(Minecraft mc,String mode){return mc.gameDirectory.toPath().resolve("forest-caves-"+mode+"-validation.json");}
    private static void write(Minecraft mc){try{Files.writeString(report(mc,MODE),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception e){throw new IllegalStateException(e);}}
}
