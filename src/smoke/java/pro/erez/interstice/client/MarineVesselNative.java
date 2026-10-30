package pro.erez.interstice.client;

import com.google.gson.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ship.*;
import pro.erez.interstice.worldgen.IslandWorld;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Native geometry/interaction check on a paused Sable hull, NOT sea propulsion acceptance. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class MarineVesselNative {
    static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("marineae2")&&System.getProperty("interstice.marinePhase","").equals("vessel");
    static final JsonObject REPORT=new JsonObject();static final JsonArray ROUTES=new JsonArray();
    static boolean started,closing;static int phase,ticks,quiet,route;static long deadline;
    static ServerLevel world;static ServerSubLevel ship;static BlockPos origin;
    static CompletableFuture<?> work;static CompletableFuture<ServerSubLevel> assembly;static NativeChunkSettler.Session settling;
    // x, forward coordinate, floor, rise: four independent connected stair flights.
    static final double[][] STAIRS=MarineData.metadata("stairs").asList().stream().map(v->{var s=v.getAsJsonObject();return new double[]{s.get("x").getAsDouble(),s.get("y").getAsDouble(),s.get("z").getAsDouble(),s.get("rise").getAsDouble()};}).toArray(double[][]::new);
    static Minecraft mc(){return Minecraft.getInstance();}
    static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayers().getFirst();}
    static void submit(Runnable r){work=mc().getSingleplayerServer().submit(r);}
    static void write(){try{REPORT.addProperty("phase",phase);REPORT.addProperty("layout_revision",MarineData.REVISION);REPORT.addProperty("geometry_sha256",MarineData.GEOMETRY_SHA256);REPORT.addProperty("owner_profile_used",false);REPORT.add("walked_stairs",ROUTES);Files.writeString(mc().gameDirectory.toPath().resolve("marine-vessel.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    static void finish(boolean ok,String error){KeyMapping.releaseAll();REPORT.addProperty("passed",ok);REPORT.addProperty("error",error);write();closing=true;if(mc().level!=null)mc().level.disconnect();mc().disconnect(new TitleScreen());}
    static Vector3d global(double x,double y,double z){return ship.logicalPose().transformPosition(new Vector3d(origin.getX()+x,origin.getY()+y,origin.getZ()+z),new Vector3d());}
    static Vector3d local(double x,double y,double z){var p=ship.logicalPose().transformPositionInverse(new Vector3d(x,y,z),new Vector3d());return p.sub(origin.getX(),origin.getY(),origin.getZ());}
    static void locate(){var b=ship.getPlot().getBoundingBox();for(int x=b.minX();x<=b.maxX();x++)for(int y=b.minY();y<=b.maxY();y++)for(int z=b.minZ();z<=b.maxZ();z++){var p=new BlockPos(x,y,z);var s=world.getBlockState(p);if(s.getBlock() instanceof MarineBlock){origin=p.subtract(MarineData.cell(s.getValue(MarineData.CELL)).pos());return;}}throw new IllegalStateException("Relocated NE01 missing");}
    static void at(double x,double y,double z,float yaw,float pitch,boolean fly){var p=global(x,y,z);var player=player();player.teleportTo(world,p.x,p.y,p.z,Set.of(),yaw,pitch);player.getAbilities().flying=fly;player.onUpdateAbilities();player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);player.fallDistance=0;}
    static void camera(double x,double y,double z,double tx,double ty,double tz){var a=global(x,y,z);var b=global(tx,ty,tz);double dx=b.x-a.x,dy=b.y-a.y-player().getEyeHeight(),dz=b.z-a.z;at(x,y,z,(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))),true);}
    static void shot(String id){Screenshot.grab(mc().gameDirectory,"ne01-"+id+".png",mc().getMainRenderTarget(),m->{});}
    static void allOpen(){int changed=0;for(var c:MarineData.CELLS)if(!c.mechanisms().isEmpty()){var p=origin.offset(c.pos());var s=world.getBlockState(p);int mask=(1<<c.mechanisms().size())-1;world.setBlock(p,s.setValue(MarineBlock.POSE,mask),3);need(world.getBlockState(p).getValue(MarineBlock.POSE)==mask,"Opening pose overwrite");changed++;}REPORT.addProperty("mechanism_cells_opened",changed);}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){if(ACTIVE&&!closing&&settling!=null)settling.tick(e.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!ACTIVE)return;try{
        if(closing){if(mc().level==null&&mc().screen instanceof TitleScreen&&++quiet>30)mc().stop();return;}
        if(!started&&mc().screen instanceof TitleScreen&&mc().getOverlay()==null){started=true;deadline=System.nanoTime()+900_000_000_000L;REPORT.addProperty("dh_loaded",net.neoforged.fml.ModList.get().isLoaded("distanthorizons"));need(REPORT.get("dh_loaded").getAsBoolean(),"DH required");REPORT.addProperty("started_utc",java.time.Instant.now().toString());REPORT.addProperty("physics_paused_for_geometry_test",true);REPORT.addProperty("full_vessel_me_network",false);REPORT.addProperty("sea_propulsion_tested",false);need(!Files.exists(mc().gameDirectory.toPath().resolve("saves/marine-vessel-owned")),"New disposable world required");var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc().createWorldOpenFlows().createFreshLevel("marine-vessel-owned",new LevelSettings("NE01 vessel native geometry",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261029,false,false),WorldPresets::createNormalWorldDimensions,mc().screen);return;}
        if(!started||SmokeWorldPrompts.advance(mc())||mc().player==null||mc().level==null||mc().getSingleplayerServer()==null)return;
        need(System.nanoTime()<deadline,"NE01 deadline at phase "+phase);mc().mouseHandler.releaseMouse();
        if(work!=null&&!work.isDone())return;if(work!=null){work.join();work=null;}
        if(assembly!=null){if(!assembly.isDone())return;ship=assembly.join();assembly=null;submit(()->{locate();REPORT.addProperty("ship_uuid",ship.getUniqueId().toString());camera(22,18,28,0,6,0);});return;}
        if(phase==0){if(mc().screen!=null)return;submit(()->{
            world=mc().getSingleplayerServer().getLevel(IslandWorld.TENSION_WORLD);need(world!=null,"V6 absent");
            try{WaveShipFixture.pause(world);}catch(Exception e){throw new IllegalStateException(e);}
            var p=player();p.setGameMode(GameType.CREATIVE);p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,200000,0,false,false));
            BlockPos site=null;for(int y=100;y<world.getMaxBuildHeight()-18&&site==null;y+=12){var candidate=new BlockPos(32,y,16);boolean clear=true;for(var c:MarineData.CELLS){var pos=candidate.offset(c.pos());world.getChunkAt(pos);if(!world.getBlockState(pos).isAir()){clear=false;break;}}if(clear)site=candidate;}
            need(site!=null,"No clear drydock for complete NE01");p.teleportTo(world,site.getX()+22,site.getY()+18,site.getZ()+28,Set.of(),140,20);p.getAbilities().flying=true;p.onUpdateAbilities();REPORT.addProperty("construction_origin",site.toShortString());assembly=MarineAssembly.create(world,site);
        });phase=1;ticks=0;write();return;}
        if(phase==1){if(ship==null||++ticks<120)return;shot("native-exterior-closed");submit(()->{int count=0;for(var c:MarineData.CELLS){var s=world.getBlockState(origin.offset(c.pos()));need(s.getBlock() instanceof MarineBlock&&s.getValue(MarineData.CELL)==c.id(),"Authored cell missing "+c.id());count++;}REPORT.addProperty("native_cells",count);allOpen();});phase=2;ticks=0;return;}
        if(phase==2){if(++ticks<60)return;shot("native-exterior-open");submit(()->{var s=STAIRS[route];at(s[0],s[2]+.12,s[1]-.7,0,0,false);});phase=3;ticks=0;return;}
        if(phase==3){if(++ticks<30)return;phase=4;ticks=0;return;}
        if(phase==4){var s=STAIRS[route];var here=local(mc().player.getX(),mc().player.getY(),mc().player.getZ());mc().options.keyUp.setDown(true);ticks++;
            if(here.z<s[1]+(route==2?3.75:4.30)){need(ticks<400,"Walking stuck on stair "+route+" at "+here);return;}
            KeyMapping.releaseAll();need(!mc().player.getAbilities().flying,"Stair check became flight");need(here.y>=s[2]+s[3]-.05,"Player crossed stair without climbing "+route+" at "+here);
            var r=new JsonObject();r.addProperty("index",route);r.addProperty("walk_ticks",ticks);r.addProperty("local_x",here.x);r.addProperty("local_y",here.y);r.addProperty("local_z",here.z);r.addProperty("flying",false);r.addProperty("on_ground",mc().player.onGround());ROUTES.add(r);shot("native-stair-"+route);route++;
            if(route<STAIRS.length){submit(()->{var next=STAIRS[route];at(next[0],next[2]+.12,next[1]-(route==2?.55:.7),0,0,false);});phase=3;ticks=0;}else{submit(()->camera(.55,5.85,-8.5,.55,5.6,0));phase=5;ticks=0;}write();return;
        }
        if(phase==5){if(++ticks<60)return;shot("native-main-interior");submit(()->camera(.3,8.6,.5,.3,8.2,5));phase=6;ticks=0;return;}
        if(phase==6){if(++ticks<60)return;shot("native-bridge");submit(()->{REPORT.addProperty("native_walk_routes",ROUTES.size());try{WaveShipFixture.flush(world);}catch(Exception e){throw new IllegalStateException(e);}settling=new NativeChunkSettler.Session("NE01 geometry final save",1600,true);});phase=7;return;}
        if(phase==7){need(!settling.failed(),settling.failure());if(!settling.ready())return;REPORT.add("chunk_settlement",settling.report());finish(true,"");}
    }catch(Throwable e){e.printStackTrace();finish(false,e.toString());}}
}
