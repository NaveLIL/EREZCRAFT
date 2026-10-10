package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;

/** Actual isolated client loader/resource startup. Does not claim a tested flying craft or magic progression. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class TechmagicSpineStartupSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("interstice.techmagicSpineSmoke");
    private static boolean finished;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||finished)return;var mc=Minecraft.getInstance();
        if(!(mc.screen instanceof TitleScreen))return;finished=true;var report=new JsonObject();
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').contains("/.verification/"))throw new IllegalStateException("Spine startup requires a new isolated profile");
            mc.getWindow().setTitle("EREZCRAFT - isolated Create / Aeronautics startup check");
            var expected=Map.of("interstice",System.getProperty("interstice.expectedModVersion","0.7.0-preview.2"),"create","6.0.10","flywheel","1.0.6","aeronautics","1.3.2","simulated","1.3.2","offroad","1.3.2","sable","2.0.6","distanthorizons","3.3.3");
            var mods=new JsonObject();
            for(var row:expected.entrySet()){
                var mod=ModList.get().getModContainerById(row.getKey()).orElseThrow(()->new IllegalStateException("Missing mandatory spine mod "+row.getKey()));
                String version=mod.getModInfo().getVersion().toString();mods.addProperty(row.getKey(),version);
                if(!version.equals(row.getValue()))throw new IllegalStateException("Unexpected spine version "+row.getKey()+":"+version);
            }
            String foundation=System.getProperty("interstice.techmagicFoundationLock","");
            if(!foundation.isEmpty()){
                var lock=JsonParser.parseString(Files.readString(Path.of(foundation))).getAsJsonObject();
                for(var component:lock.getAsJsonArray("mods"))for(var row:component.getAsJsonObject().getAsJsonObject("mod_ids").entrySet()){
                    var mod=ModList.get().getModContainerById(row.getKey()).orElseThrow(()->new IllegalStateException("Missing foundation mod "+row.getKey()));
                    String actual=mod.getModInfo().getVersion().toString(),wanted=row.getValue().getAsString();mods.addProperty(row.getKey(),actual);
                    if(!wanted.contains("${")&&!actual.equals(wanted))throw new IllegalStateException("Foundation version changed "+row.getKey()+":"+actual+" expected "+wanted);
                }
                report.addProperty("foundation_components",lock.getAsJsonArray("mods").size());
            }
            report.add("loaded_versions",mods);report.addProperty("passed",true);
            report.addProperty("pid",ProcessHandle.current().pid());report.addProperty("world_created",false);
            report.addProperty("max_heap_bytes",Runtime.getRuntime().maxMemory());
            report.addProperty("jvm_arguments",java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(arg->arg.startsWith("-Xmx")||arg.startsWith("-Xms")||arg.contains("UseZGC")||arg.contains("ZGenerational")).collect(java.util.stream.Collectors.joining(" ")));
            report.addProperty("scope","Actual NeoForge client reached native title screen with Create, bundled Aeronautics, Sable and DH. Ship physics, magic, recipes, dimensions and performance remain unverified.");
        }catch(Throwable failure){report.addProperty("passed",false);report.addProperty("error",failure.toString());failure.printStackTrace();}
        try{Files.writeString(mc.gameDirectory.toPath().resolve("techmagic-spine-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception failure){throw new IllegalStateException(failure);}
        System.out.println("TECHMAGIC_SPINE_STARTUP "+report);mc.stop();
    }
}
