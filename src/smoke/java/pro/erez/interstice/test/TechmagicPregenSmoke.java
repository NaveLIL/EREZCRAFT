package pro.erez.interstice.test;

import com.google.gson.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.*;

/** Bounded functional Chunky/DH gate, never a hardware benchmark or production pregen. */
@EventBusSubscriber(modid=Interstice.ID)
public final class TechmagicPregenSmoke {
    private static final String MODE=System.getProperty("interstice.techmagicPregen","");
    private static final String[] WORLDS={"minecraft:overworld","interstice:islands_v6"};
    private static final Set<String> completed=ConcurrentHashMap.newKeySet();
    private static final JsonObject result=new JsonObject();
    private static final JsonArray samples=new JsonArray();
    private static MinecraftServer server;
    private static Object api;
    private static Method running,start;
    private static int ticks,index;
    private static boolean active,done;
    private static long began;
    private static V6DedicatedScenario.Quiescence quiet=new V6DedicatedScenario.Quiescence();

    @SubscribeEvent public static void begin(ServerStartedEvent event){
        if(MODE.isEmpty())return;
        server=event.getServer();began=System.nanoTime();
        try{
            require(server.isDedicatedServer()&&server.getServerDirectory().toAbsolutePath().toString().replace('\\','/').contains("/.verification/"),"Pregen smoke must be an isolated dedicated server");
            var v6=server.getLevel(IslandWorld.TENSION_WORLD);
            require(v6!=null&&v6.dimension().location().toString().equals(WORLDS[1])&&v6.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&g.terrainRevision()==6,"Actual V6 required");
            require(v6.getSeed()==20261006,"Unexpected isolated seed");
            result.addProperty("mode",MODE);result.addProperty("pid",ProcessHandle.current().pid());
            result.addProperty("actual_v6_dimension",v6.dimension().location().toString());result.addProperty("seed",v6.getSeed());
            result.addProperty("center_blocks",3080);result.addProperty("radius_blocks",32);
            result.addProperty("production_worlds_modified",false);result.addProperty("capacity_verified",false);result.addProperty("dh_lod_coverage_verified",false);
            result.addProperty("scope","Sequential bounded Chunky tasks with original DH; disk FULL status and stable geometry/biomes checked across separate JVMs. Not server capacity, ship gameplay or complete DH LOD coverage.");
        }catch(Throwable error){finish(error);}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(server!=event.getServer()||done)return;
        try{
            ticks++;
            require(System.nanoTime()-began<480_000_000_000L,"Bounded pregen exceeded eight minutes");
            if(ticks<40)return;
            if(MODE.equals("reload")){
                for(String world:WORLDS)samples.add(sample(level(world)));
                var previous=JsonParser.parseString(Files.readString(server.getServerDirectory().resolve("techmagic-pregen-create-validation.json"))).getAsJsonObject();
                require(previous.get("passed").getAsBoolean()&&previous.get("pid").getAsLong()!=ProcessHandle.current().pid(),"Cold check requires separate successful creator JVM");
                require(previous.getAsJsonArray("samples").equals(samples),"Saved stable geometry/biomes changed after cold start");
                result.addProperty("creator_pid",previous.get("pid").getAsLong());finish(null);return;
            }
            if(api==null){
                var chunky=Class.forName("org.popcraft.chunky.ChunkyProvider").getMethod("get").invoke(null);
                api=chunky.getClass().getMethod("getApi").invoke(chunky);
                var contract=Class.forName("org.popcraft.chunky.api.ChunkyAPI");
                running=contract.getMethod("isRunning",String.class);
                start=contract.getMethod("startTask",String.class,String.class,double.class,double.class,double.class,double.class,String.class);
                Consumer<Object> listener=e->{try{completed.add((String)e.getClass().getMethod("world").invoke(e));}catch(Exception error){throw new IllegalStateException(error);}};
                contract.getMethod("onGenerationComplete",Consumer.class).invoke(api,listener);
            }
            String world=WORLDS[index];
            if(!active){
                for(String id:WORLDS)require(!(boolean)running.invoke(api,id),"Only one world may pregen at once");
                require((boolean)start.invoke(api,world,"square",3080d,3080d,32d,32d,"concentric"),"Chunky rejected exact world "+world);
                active=true;System.out.println("TECHMAGIC_PREGEN_STARTED "+world);return;
            }
            if(!completed.contains(world)||(boolean)running.invoke(api,world))return;
            quiet.tick(server);if(!quiet.ready())return;
            server.saveEverything(true,true,true);
            samples.add(sample(level(world)));
            result.add(world+"_quiescence",quiet.json());
            index++;active=false;quiet=new V6DedicatedScenario.Quiescence();
            if(index==WORLDS.length){var names=new JsonArray();for(String id:WORLDS)names.add(id);result.add("completed_chunky_worlds",names);finish(null);}
        }catch(Throwable error){finish(error);}
    }
    private static ServerLevel level(String id){for(var level:server.getAllLevels())if(level.dimension().location().toString().equals(id))return level;throw new IllegalStateException("Missing exact world "+id);}
    private static JsonObject sample(ServerLevel level)throws Exception{
        var row=new JsonObject();row.addProperty("dimension",level.dimension().location().toString());var chunks=new JsonArray();
        for(int cx=191;cx<=193;cx++)for(int cz=191;cz<=193;cz++){
            var pos=new ChunkPos(cx,cz);
            // Inspect stored status before loading: cold load must not mask missing generation.
            var stored=level.getChunkSource().chunkMap.read(pos).join();
            require(stored.isPresent()&&stored.get().getString("Status").equals("minecraft:full"),"Missing saved FULL "+row+" "+pos);
            var chunk=level.getChunk(cx,cz);require(chunk.getPersistedStatus()==ChunkStatus.FULL,"Loaded chunk is not FULL");
            var digest=MessageDigest.getInstance("SHA-256");
            var cursor=new BlockPos.MutableBlockPos();
            for(int x=pos.getMinBlockX();x<=pos.getMaxBlockX();x++)for(int z=pos.getMinBlockZ();z<=pos.getMaxBlockZ();z++)for(int y=0;y<=80;y++){
                cursor.set(x,y,z);var state=chunk.getBlockState(cursor);
                digest.update((byte)(state.getFluidState().isEmpty()?0:1));digest.update((byte)(state.isCollisionShapeFullBlock(level,cursor)?1:0));
                if((y&15)==0)digest.update(level.getBiome(cursor).unwrapKey().orElseThrow().location().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            var entry=new JsonObject();entry.addProperty("x",cx);entry.addProperty("z",cz);entry.addProperty("saved_status","minecraft:full");entry.addProperty("stable_geometry_biomes_y0_80_sha256",HexFormat.of().formatHex(digest.digest()));chunks.add(entry);
        }
        row.add("chunks",chunks);return row;
    }
    private static void finish(Throwable error){
        if(done)return;done=true;result.add("samples",samples);result.addProperty("passed",error==null);result.addProperty("actual_server_ticks",ticks);
        if(error!=null){result.addProperty("error",error.toString());error.printStackTrace();}
        try{Files.writeString(server.getServerDirectory().resolve("techmagic-pregen-"+MODE+"-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(Exception write){throw new IllegalStateException(write);}
        System.out.println("TECHMAGIC_PREGEN_RESULT "+result);server.halt(false);
    }
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
}
