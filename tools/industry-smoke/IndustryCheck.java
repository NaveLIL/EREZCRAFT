package pro.erez.industrycheck;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual production binaries plus candidates; a fresh menu-only native acceptance check. */
@Mod("erez_industry_check")
@EventBusSubscriber(modid="erez_industry_check", value=Dist.CLIENT)
public final class IndustryCheck {
    private static int ticks;
    private static boolean finished;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(finished || !(mc.screen instanceof TitleScreen) || mc.getOverlay()!=null || ++ticks<30)return;
        finished=true;
        var report=new JsonObject();
        var root=mc.gameDirectory.toPath();
        try {
            if(!root.toRealPath().toString().replace('\\','/').contains("/.local/verification/industry-"))throw new IllegalStateException("Refuse player profile");
            var expected=JsonParser.parseString(Files.readString(root.resolve("industry-expected.json"))).getAsJsonObject();
            var versions=new JsonObject();var failures=new JsonArray();
            for(var entry:expected.getAsJsonObject("mods").entrySet()){
                var mod=ModList.get().getModContainerById(entry.getKey()).orElseThrow().getModInfo();
                versions.addProperty(entry.getKey(),mod.getVersion().toString());
                if(!entry.getValue().getAsString().equals(mod.getVersion().toString()))failures.add("version:"+entry.getKey());
            }
            var translations=expected.getAsJsonObject("translations");
            for(var entry:translations.entrySet()){
                var text=entry.getValue().getAsString().replaceAll("%(\\d+\\$)?[\\d\\.]*[df]", "%$1s");
                if(!Language.getInstance().getOrDefault(entry.getKey()).equals(text))failures.add("translation:"+entry.getKey());
            }
            int books=0;
            for(var entry:expected.getAsJsonArray("books")){
                var parts=entry.getAsString().split("/",3);
                var resource=mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(parts[1],parts[2])).orElseThrow();
                if(!resource.sourcePackId().equals("file/erezcraft-ru-mc1.21.1.zip"))throw new IllegalStateException("Book from wrong resource pack");
                try(var stream=resource.open()){if(stream.readAllBytes().length==0)throw new IllegalStateException("Empty book");}
                books++;
            }
            report.add("versions",versions);report.add("failures",failures);
            report.addProperty("loaded_mod_count",ModList.get().getMods().size());
            report.addProperty("translations_checked",translations.size());report.addProperty("book_resources_checked",books);
            report.addProperty("language",mc.options.languageCode);
            report.addProperty("distant_horizons_loaded",ModList.get().isLoaded("distanthorizons"));
            report.addProperty("no_world_opened",mc.level==null && mc.getSingleplayerServer()==null);
            report.addProperty("passed",failures.isEmpty() && mc.options.languageCode.equals("ru_ru") && ModList.get().isLoaded("distanthorizons") && mc.level==null);
        }catch(Throwable error){report.addProperty("passed",false);report.addProperty("error_class",error.getClass().getName());}
        try{Files.writeString(root.resolve("industry-native.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception error){throw new IllegalStateException("Could not persist native report",error);}
        mc.stop();
    }
}
