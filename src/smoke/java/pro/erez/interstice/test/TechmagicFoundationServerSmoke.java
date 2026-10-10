package pro.erez.interstice.test;

import com.google.gson.*;
import java.nio.file.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.*;

/** Boot/dimension gate on a real isolated dedicated server, without players or production saves. */
@EventBusSubscriber(modid=Interstice.ID)
public final class TechmagicFoundationServerSmoke {
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!Boolean.getBoolean("interstice.techmagicFoundationServer"))return;
        var server=event.getServer();var result=new JsonObject();
        try{
            if(!server.isDedicatedServer()||!server.getServerDirectory().toAbsolutePath().toString().replace('\\','/').contains("/.verification/"))throw new IllegalStateException("Foundation server must be real and isolated");
            var lock=JsonParser.parseString(Files.readString(Path.of(System.getProperty("interstice.techmagicFoundationLock")))).getAsJsonObject();
            var loaded=new JsonObject();
            for(var value:lock.getAsJsonArray("mods")){
                var mod=value.getAsJsonObject();String role=mod.get("role").getAsString();if(role.equals("minimap")||role.equals("worldmap"))continue;
                for(var row:mod.getAsJsonObject("mod_ids").entrySet()){
                    var container=ModList.get().getModContainerById(row.getKey()).orElseThrow(()->new IllegalStateException("Missing server foundation mod "+row.getKey()));
                    loaded.addProperty(row.getKey(),container.getModInfo().getVersion().toString());
                }
            }
            for(String id:new String[]{"interstice","create","aeronautics","sable","distanthorizons","ae2","ars_nouveau","modern_industrialization","mekanism","yigd","chunky"})
                if(!ModList.get().isLoaded(id))throw new IllegalStateException("Missing critical server mod "+id);
            var level=server.getLevel(IslandWorld.TENSION_WORLD);
            if(level==null||!level.dimension().location().toString().equals("interstice:islands_v6")||!(level.getChunkSource().getGenerator() instanceof IslandChunkGenerator generator)||generator.terrainRevision()!=6)throw new IllegalStateException("Actual V6 generator/dimension missing");
            result.add("loaded_foundation_versions",loaded);result.addProperty("passed",true);result.addProperty("server_pid",ProcessHandle.current().pid());
            result.addProperty("actual_v6_dimension",level.dimension().location().toString());result.addProperty("actual_seed",level.getSeed());
            result.addProperty("scope","Real dedicated startup and actual dimension registration only;10-player load, machines, ship physics, graves, recipes, pregeneration and cold persistence are NOT validated by this gate.");
        }catch(Throwable failure){result.addProperty("passed",false);result.addProperty("error",failure.toString());failure.printStackTrace();}
        try{Files.writeString(server.getServerDirectory().resolve("techmagic-foundation-server-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(Exception failure){throw new IllegalStateException(failure);}
        System.out.println("TECHMAGIC_FOUNDATION_SERVER_BOOT "+result);server.halt(false);
    }
}
