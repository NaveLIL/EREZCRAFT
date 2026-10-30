package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.vehicle.*;

/** Actual full-pack geometry/crouch inspection, explicitly no driving claim. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class EitanNative {
    static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("eitan");
    static final JsonObject REPORT=new JsonObject();static final BlockPos ORIGIN=new BlockPos(0,200,0);
    static boolean started,closing;static int phase,ticks,quiet;static long deadline;static CompletableFuture<?> work;
    static ServerLevel world;static double beforeZ;
    static Minecraft mc(){return Minecraft.getInstance();}
    static ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayers().getFirst();}
    static void need(boolean ok,String error){if(!ok)throw new IllegalStateException(error);}
    static void submit(Runnable r){work=mc().getSingleplayerServer().submit(r);}
    static void shot(String id){Screenshot.grab(mc().gameDirectory,"eitan-"+id+".png",mc().getMainRenderTarget(),message->{});}
    static void camera(double x,double y,double z,double tx,double ty,double tz){var p=player();double dx=tx-x,dy=ty-y-p.getEyeHeight(),dz=tz-z;p.teleportTo(world,x,y,z,Set.of(),(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));p.getAbilities().flying=true;p.onUpdateAbilities();p.setDeltaMovement(Vec3.ZERO);}
    static void write(){try{REPORT.addProperty("phase",phase);REPORT.addProperty("owner_profile_used",false);REPORT.addProperty("driving_tested",false);REPORT.addProperty("independent_gameplay_mechanisms_tested",false);REPORT.addProperty("model_revision",EitanPreviewData.REVISION);REPORT.addProperty("geometry_sha256",EitanPreviewData.SHA256);Files.writeString(mc().gameDirectory.toPath().resolve("eitan-native.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    static void finish(boolean ok,String error){KeyMapping.releaseAll();REPORT.addProperty("passed",ok);REPORT.addProperty("error",error);write();closing=true;if(mc().level!=null)mc().level.disconnect();mc().disconnect(new TitleScreen());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!ACTIVE)return;try{
        if(closing){if(mc().level==null&&mc().screen instanceof TitleScreen&&++quiet>25)mc().stop();return;}
        if(!started&&mc().screen instanceof TitleScreen&&mc().getOverlay()==null){started=true;deadline=System.nanoTime()+720_000_000_000L;need(net.neoforged.fml.ModList.get().isLoaded("distanthorizons"),"DH required");REPORT.addProperty("dh_loaded",true);REPORT.addProperty("started_utc",java.time.Instant.now().toString());need(!Files.exists(mc().gameDirectory.toPath().resolve("saves/eitan-owned")),"New disposable world required");var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc().createWorldOpenFlows().createFreshLevel("eitan-owned",new LevelSettings("Eitan R4 geometry inspection",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261029,false,false),WorldPresets::createNormalWorldDimensions,mc().screen);return;}
        if(!started||SmokeWorldPrompts.advance(mc())||mc().player==null||mc().level==null||mc().getSingleplayerServer()==null)return;
        need(System.nanoTime()<deadline,"Eitan deadline at phase "+phase);mc().mouseHandler.releaseMouse();mc().options.hideGui=true;
        if(work!=null&&!work.isDone())return;if(work!=null){work.join();work=null;}
        if(phase==0){if(mc().screen!=null)return;submit(()->{world=mc().getSingleplayerServer().overworld();world.setDayTime(6000);for(int x=-12;x<=12;x++)for(int z=-14;z<=14;z++){world.getChunkAt(ORIGIN.offset(x,0,z));world.setBlock(ORIGIN.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);}EitanPreview.place(world,ORIGIN);REPORT.addProperty("native_cells",EitanPreviewData.CELLS.size());REPORT.addProperty("night_vision",player().hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION));camera(11,207,-13,0,201.4,0);});phase=1;ticks=0;return;}
        if(phase==1){if(++ticks<100)return;shot("exterior-closed");submit(()->{EitanPreview.pose(world,ORIGIN,1);camera(-9,206,11,0,201.4,1);});phase=2;ticks=0;return;}
        if(phase==2){if(++ticks<60)return;shot("boarding");submit(()->{var crouched=new AABB(-.3,200.94,1.7,.3,202.44,2.3);boolean fits=world.noCollision(player(),crouched);REPORT.addProperty("crouched_aisle_box_clear",fits);need(fits,"Crouch clearance blocked at aisle");REPORT.addProperty("standing_aisle_box_clear",world.noCollision(player(),new AABB(-.3,200.94,1.7,.3,202.74,2.3)));var p=player();p.teleportTo(world,0,200.96,2.6,Set.of(),180,0);p.getAbilities().flying=false;p.onUpdateAbilities();p.setShiftKeyDown(true);p.setPose(Pose.CROUCHING);p.setDeltaMovement(Vec3.ZERO);});phase=3;ticks=0;return;}
        if(phase==3){mc().options.keyShift.setDown(true);if(++ticks<25)return;beforeZ=mc().player.getZ();phase=4;ticks=0;return;}
        if(phase==4){mc().options.keyShift.setDown(true);mc().options.keyUp.setDown(true);ticks++;double distance=beforeZ-mc().player.getZ();if(distance<2.6){need(ticks<230,"Crouch walking stuck at "+mc().player.position()+"; moved "+distance);return;}KeyMapping.releaseAll();need(!mc().player.getAbilities().flying,"Aisle walk became flight");REPORT.addProperty("crouch_walk_distance_m",distance);REPORT.addProperty("crouch_walk_ticks",ticks);REPORT.addProperty("native_player_pose",mc().player.getPose().name());shot("aisle");submit(()->{EitanPreview.pose(world,ORIGIN,2);camera(-8,204,9,0,201.5,3);});phase=5;ticks=0;return;}
        if(phase==5){if(++ticks<60)return;shot("door");submit(()->{EitanPreview.pose(world,ORIGIN,3);camera(7,208,-8,0,201.8,0);});phase=6;ticks=0;return;}
        if(phase==6){if(++ticks<60)return;shot("service");submit(()->{for(var c:EitanPreviewData.CELLS){var s=world.getBlockState(ORIGIN.offset(c.pos()));need(s.getBlock()==EitanPreview.PART.get()&&s.getValue(EitanPreviewData.CELL)==c.id()&&s.getValue(EitanPreviewData.POSE)==3,"Cell pose lost");}world.getServer().saveEverything(false,true,true);REPORT.addProperty("world_save_completed",true);});phase=7;return;}
        if(phase==7)finish(true,"");
    }catch(Throwable e){e.printStackTrace();finish(false,e.toString());}}
    private EitanNative(){}
}
