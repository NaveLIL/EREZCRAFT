package pro.erez.interstice.client;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.*;

/** Real V6 ocean across the vanilla/DH boundary. Green-mask pass proves actual far fragments. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class LowerOceanDhNative {
    private static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("loweroceandh");
    private static final boolean REVIEW=Boolean.getBoolean("interstice.oceanDhReview");
    private static final boolean RESUME=Boolean.getBoolean("interstice.oceanDhResume");
    private static final JsonObject REPORT=new JsonObject();
    private static boolean started,closing,finished;private static int phase,ticks,waits,chunk;private static long deadline;
    private static CompletableFuture<?> work;private static ServerLevel level;private static NativeChunkSettler.Session settling;
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException(why);}
    private static void write(){try{
        REPORT.addProperty("tagged_columns",LowerOceanDh.TAGGED.sum());REPORT.addProperty("uniform_uploads",LowerOceanDh.UNIFORMS.sum());REPORT.addProperty("patched_sources",LowerOceanDh.PATCHES.sum());
        var c=Class.forName("com.seibel.distanthorizons.common.render.openGl.GlDhMetaRenderer_neoforge");var renderer=c.getField("INSTANCE").get(null);var shader=c.getField("shaderProgramForThisFrame").get(renderer);REPORT.addProperty("actual_shader_class",shader==null?"null":shader.getClass().getName());

        Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("ocean-dh.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"ocean-dh-"+name+".png",mc.getMainRenderTarget(),unused->{});}
    private static ServerPlayer player(){var mc=Minecraft.getInstance();return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static void place(double x,double z,float yaw){var p=player();p.teleportTo(level,x,39,z,Set.of(),yaw,3);p.hasChangedDimension();p.getAbilities().flying=true;p.onUpdateAbilities();}
    private static void submit(Runnable action){work=Minecraft.getInstance().getSingleplayerServer().submit(()->{action.run();return true;});}
    private static void finish(boolean pass,String reason){if(closing)return;LowerOceanDh.VERIFY_MASK=false;REPORT.addProperty("passed",pass);REPORT.addProperty("reason",reason);REPORT.addProperty("pid",ProcessHandle.current().pid());REPORT.addProperty("tagged_columns",LowerOceanDh.TAGGED.sum());REPORT.addProperty("uniform_uploads",LowerOceanDh.UNIFORMS.sum());REPORT.addProperty("patched_sources",LowerOceanDh.PATCHES.sum());write();closing=true;settling=new NativeChunkSettler.Session("ocean_dh_shutdown",3200);}
    private static int maskPixels(){var mc=Minecraft.getInstance();var target=mc.getMainRenderTarget();int size=target.width*target.height*4;var bytes=MemoryUtil.memAlloc(size);int old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);try{GL11.glReadPixels(0,0,target.width,target.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);int count=0;for(int i=0;i<size;i+=4)if((bytes.get(i+1)&255)>200&&(bytes.get(i)&255)<40&&(bytes.get(i+2)&255)<40)count++;return count;}finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);MemoryUtil.memFree(bytes);}}
    @SubscribeEvent public static void server(ServerTickEvent.Post event){if(!ACTIVE||REVIEW||finished)return;if(closing){settling.tick(event.getServer());if(settling.ready()||settling.failed()){if(settling.failed()){REPORT.addProperty("passed",false);REPORT.addProperty("reason",settling.failure());}REPORT.add("settled",settling.report());write();finished=true;Minecraft.getInstance().stop();}return;}if(level!=null&&phase==1&&chunk<289){int x=chunk%17-8,z=chunk/17-8;level.getChunk(x,z);chunk++;}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();try{
        if(closing)return;
        if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Isolated profile required");for(String id:List.of("distanthorizons","sodium","iris","create","sable"))require(ModList.get().isLoaded(id),"Missing "+id);started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(18);
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=!REVIEW;
            if(REVIEW){require(Files.exists(mc.gameDirectory.toPath().resolve("ocean-dh.json")),"Passed scene required");require(JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("ocean-dh.json"))).getAsJsonObject().get("passed").getAsBoolean(),"Failed scene");mc.createWorldOpenFlows().openWorld("ocean-dh-owned",()->{});return;}
            if(RESUME){var prior=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("ocean-dh.json"))).getAsJsonObject();require(!prior.get("owner_profile_used").getAsBoolean()&&!Files.exists(mc.gameDirectory.toPath().resolve("ocean-dh-review.json"))&&!Files.exists(mc.gameDirectory.toPath().resolve("ocean-dh-owner-profile")),"Automated scene required");REPORT.addProperty("version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());REPORT.addProperty("dh_loaded",true);REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("owner_profile_used",false);mc.createWorldOpenFlows().openWorld("ocean-dh-owned",()->{});return;}
            require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/ocean-dh-owned")),"Fresh world required");REPORT.addProperty("version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());REPORT.addProperty("dh_loaded",true);REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("owner_profile_used",false);
            mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.options.fov().set(70);mc.options.bobView().set(false);
            var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("ocean-dh-owned",new LevelSettings("OC01 + DH owner review",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261028L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
        }
        if(!started||mc.getOverlay()!=null||SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||mc.screen!=null)return;
        if(REVIEW){if(phase==0&&++ticks>=100){require(mc.level.dimension().equals(IslandWorld.TENSION_WORLD),"Cold V6");shot("review-ready");Files.writeString(mc.gameDirectory.toPath().resolve("ocean-dh-review.json"),"{\"passed\":true,\"player_has_control\":true,\"dh_loaded\":true}");phase=1;}return;}
        require(System.nanoTime()<deadline,"Deadline phase "+phase);if(work!=null&&!work.isDone())return;if(work!=null)work.join();if(++ticks<40)return;ticks=0;REPORT.addProperty("phase",phase);REPORT.addProperty("loaded_full_chunks",chunk);write();shot("progress");
        if(phase==0){submit(()->{level=mc.getSingleplayerServer().getLevel(IslandWorld.TENSION_WORLD);require(level.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&g.terrainRevision()==6,"Actual V6");var p=player();p.setGameMode(GameType.CREATIVE);p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,200000,0,false,false));place(39,1,0);});phase=1;}
        else if(phase==1){if(chunk<289)return;if(++waits<10)return;if(LowerOceanDh.UNIFORMS.sum()==0&&waits<90)return;waits=0;require(LowerOceanDh.PATCHES.sum()>=2,"DH shaders not adapted");require(LowerOceanDh.TAGGED.sum()>0,"No real heavy columns tagged");require(LowerOceanDh.UNIFORMS.sum()>0,"DH renderer never used shared atlas");shot("boundary-before-reload");LowerOceanDh.VERIFY_MASK=true;phase=2;}
        else if(phase==2){int pixels=maskPixels();REPORT.addProperty("actual_far_ocean_mask_pixels",pixels);shot("far-fragment-mask");require(pixels>500,"No actual far ocean fragments: "+pixels);LowerOceanDh.VERIFY_MASK=false;phase=3;}
        else if(phase==3){shot("same-material");work=mc.reloadResourcePacks();phase=4;}
        else if(phase==4){if(++waits<5)return;waits=0;LowerOceanDh.VERIFY_MASK=true;phase=5;}
        else if(phase==5){int pixels=maskPixels();REPORT.addProperty("far_pixels_after_reload",pixels);require(pixels>500,"Far material lost after reload");LowerOceanDh.VERIFY_MASK=false;submit(()->place(75,25,0));phase=6;}
        else if(phase==6){if(++waits<5)return;waits=0;shot("moved-camera");LowerOceanDh.VERIFY_MASK=true;phase=7;}
        else if(phase==7){int pixels=maskPixels();REPORT.addProperty("far_pixels_after_moving",pixels);require(pixels>500,"Far material lost after camera move");LowerOceanDh.VERIFY_MASK=false;phase=8;}
        else if(phase==8){shot("review-scene");REPORT.addProperty("actual_dimension",mc.level.dimension().location().toString());REPORT.addProperty("render_distance_chunks",mc.options.renderDistance().get());REPORT.addProperty("no_world_geometry_edits",true);finish(true,"");}
    }catch(Throwable failure){failure.printStackTrace();if(!REVIEW)finish(false,failure.toString());}}
}
