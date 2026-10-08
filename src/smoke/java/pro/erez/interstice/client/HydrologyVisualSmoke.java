package pro.erez.interstice.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.terrain.HydrologyV4;

/** Natural V4 drainage/frost gallery; no fluid, stone, snow or terrain is placed by this helper. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class HydrologyVisualSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("interstice.hydrologySmoke");
    private static final String WORLD="natural-v4-water-snow-check";
    private static boolean started,finished,closing,pendingPassed;
    private static String reason;
    private static long deadline,poseTick;
    private static int stage,ticks,index;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static JsonObject data=new JsonObject();
    private static List<Scene> scenes=List.of();
    private record Scene(String id,BlockPos focus,BlockPos support,boolean fluid,boolean fall,JsonObject details,
                         double x,double y,double z,double ty) {}
    private HydrologyVisualSmoke() {}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){var s=settling;if(ENABLED&&s!=null)s.tick(event.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED||finished)return;var mc=Minecraft.getInstance();
        try {
            if(closing){close(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen) {
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat")),"Disposable hydrology save already exists");
                mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Disposable natural water snow",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261006L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;require(System.nanoTime()<deadline,"Hydrology scene deadline stage "+stage);
            if(mc.player==null||mc.level==null||mc.screen!=null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==0){mc.getConnection().sendCommand("interstice explore living");stage=1;ticks=0;return;}
            if(stage==1&&++ticks>=40){work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);require(((IslandChunkGenerator)p.serverLevel().getChunkSource().getGenerator()).terrainRevision()==4,"Hydrology entered an archived revision");try{scenes=find(p.serverLevel());}catch(ReflectiveOperationException error){throw new IllegalStateException("Read-only watershed inspection failed",error);}pose(p,scenes.get(0));return true;});stage=2;ticks=0;}
            else if(stage==2&&done()) {
                if(!ready(mc,scenes.get(index))){ticks=0;return;}if(++ticks<100)return;
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);if(p.serverLevel().getGameTime()-poseTick<100)return false;verify(p.serverLevel(),scenes.get(index));return true;});stage=3;ticks=0;
            } else if(stage==3&&done()) {
                if(!Boolean.TRUE.equals(work.join())){stage=2;ticks=0;return;}
                shot(mc,"hydrology-"+scenes.get(index).id+"-clear.png");
                if(scenes.get(index).fall){work=server.submit(()->{server.getPlayerList().getPlayer(uuid).removeEffect(MobEffects.NIGHT_VISION);return true;});stage=4;ticks=0;}
                else next(mc);
            } else if(stage==4&&done()) {
                if(!ready(mc,scenes.get(index))){ticks=0;return;}if(++ticks<100)return;
                shot(mc,"hydrology-island-waterfall-dark.png");next(mc);
            }
        } catch(Throwable failure){failure.printStackTrace();finish(mc,false,failure.toString());}
    }
    private static void next(Minecraft mc) {
        if(++index>=scenes.size()){finish(mc,true,"Naturally generated beach river, alpine frost, mountain stream and falling island water inspected after real fluid ticks");return;}
        var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();work=server.submit(()->{pose(server.getPlayerList().getPlayer(uuid),scenes.get(index));return true;});stage=2;ticks=0;
    }

    private static List<Scene> find(ServerLevel level)throws ReflectiveOperationException {
        var g=(IslandChunkGenerator)level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        g.terrainColumn(random,0,0);
        var field=IslandChunkGenerator.class.getDeclaredField("hydrology");field.setAccessible(true);var context=(HydrologyV4.Context)field.get(g);
        var plansField=HydrologyV4.Context.class.getDeclaredField("plans");plansField.setAccessible(true);
        Scene shore=null,snow=null,stream=null,fall=null;int actualChunks=0;var seen=new java.util.HashSet<Object>();
        // Global source-cell warming is numeric only. At most11x11 queried cells; all actual
        // subjects are restricted to2048 blocks, including routes from neighboring source cells.
        for(int radius=0;radius<=5;radius++)for(int cx=-radius;cx<=radius;cx++)for(int cz=-radius;cz<=radius;cz++) {
            require(System.nanoTime()<deadline,"Bounded hydrology source discovery deadline");if(Math.max(Math.abs(cx),Math.abs(cz))!=radius)continue;
            g.terrainColumn(random,cx*256+128,cz*256+128);
            List<Object> plans; synchronized(context){plans=new ArrayList<>(((Map<?,?>)plansField.get(context)).values());}
            for(Object plan:plans) {
                if(plan==null||!seen.add(plan))continue;
                var kind=(HydrologyV4.Kind)read(plan,"kind");var points=(List<?>)read(plan,"points");
                if(stream==null&&kind==HydrologyV4.Kind.MOUNTAIN_STREAM&&points.size()>=4) {
                    for(int i=0;i<points.size();i+=Math.max(1,points.size()/5)) {
                        var p=points.get(i);int x=(int)Math.floor(number(p,"x")),z=(int)Math.floor(number(p,"z"));
                        if(Math.abs(x)>2048||Math.abs(z)>2048)continue;
                        var col=(HydrologyV4.Column)g.terrainColumn(random,x,z);var sample=col.water();
                        if(!sample.wet()||sample.kind()!=kind||sample.water()<50)continue;
                        level.getChunk(x>>4,z>>4);actualChunks++;var pos=new BlockPos(x,sample.water(),z);
                        if(!heavy(level,pos)||!openAbove(level,pos,g.geometry().maxLand()))continue;
                        stream=scene(level,"mountain-stream",pos,findSupport(level,pos),true,false,route(plan,points),sample.water()+9);if(stream!=null)break;
                    }
                }
                if(fall==null&&kind==HydrologyV4.Kind.ISLAND_FALL&&read(plan,"fall")!=null) {
                    var end=read(plan,"fall");int x=(int)Math.floor(number(end,"x")),z=(int)Math.floor(number(end,"z"));
                    if(Math.abs(x)>2048||Math.abs(z)>2048)continue;
                    var col=(HydrologyV4.Column)g.terrainColumn(random,x,z);var sample=col.water();if(!sample.falling()||!sample.wet()||sample.water()<70)continue;
                    level.getChunk(x>>4,z>>4);actualChunks++;int middle=(sample.water()+g.geometry().lowerSeaTop()+1)/2;var pos=new BlockPos(x,middle,z);
                    if(!heavy(level,pos))continue;
                    var details=route(plan,points);details.addProperty("waterfall_top",sample.water());details.addProperty("waterfall_bottom",g.geometry().lowerSeaTop()+1);
                    fall=scene(level,"island-waterfall",pos,new BlockPos(x,sample.water(),z),true,true,details,middle+3);
                }
            }
            // Coarse points choose real surface veneers and channels; detailed checks load only matches.
            for(int dx=-96;dx<=96;dx+=32)for(int dz=-96;dz<=96;dz+=32) {
                int x=cx*256+128+dx,z=cz*256+128+dz;var col=(HydrologyV4.Column)g.terrainColumn(random,x,z);
                if(shore==null&&col.water().kind()==HydrologyV4.Kind.PLAIN_RIVER&&col.water().wet()) {
                    level.getChunk(x>>4,z>>4);actualChunks++;var pos=new BlockPos(x,col.water().water(),z);BlockPos sand=null;
                    for(int sx=-8;sx<=8&&sand==null;sx+=2)for(int sz=-8;sz<=8&&sand==null;sz+=2)for(int y=30;y<=40;y++)if(level.getBlockState(new BlockPos(x+sx,y,z+sz)).is(MineralEcology.TOXIC_SAND.get())){sand=new BlockPos(x+sx,y,z+sz);break;}
                    if(sand!=null&&heavy(level,pos)){var d=new JsonObject();d.addProperty("kind","plain_river_at_natural_toxic_sand_bank");position(d,"sand",sand);shore=scene(level,"beach-river",pos,sand,true,false,d,48);}
                }
                if(snow==null&&col.groundHeight()>145&&col.weights().vaults()>.7) {
                    level.getChunk(x>>4,z>>4);actualChunks++;BlockPos cover=null;
                    for(int sx=-4;sx<=4&&cover==null;sx+=2)for(int sz=-4;sz<=4&&cover==null;sz+=2)for(int y=140;y<195;y++){var p=new BlockPos(x+sx,y,z+sz);if(level.getBlockState(p).is(MineralEcology.MINERAL_FROST.get())||level.getBlockState(p).is(MineralEcology.MINERAL_POWDER.get())){cover=p;break;}}
                    if(cover!=null){var d=new JsonObject();d.addProperty("natural_cover",BuiltInRegistries.BLOCK.getKey(level.getBlockState(cover).getBlock()).toString());snow=scene(level,"mineral-snow-peak",cover,cover.below(),false,false,d,Math.min(cover.getY()+15,198));}
                }
            }
            require(actualChunks<=72,"Actual hydrology candidate-chunk budget exceeded");
            if(shore!=null&&snow!=null&&stream!=null&&fall!=null) {
                data.addProperty("seed",level.getSeed());data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("terrain_revision",4);data.addProperty("numeric_source_cells_examined",seen.size());data.addProperty("actual_candidate_chunk_requests",actualChunks);
                data.addProperty("scope","Naturally generated V4 scenes only; helper places no terrain/fluids/snow. Creative camera positioning and night vision are used for inspection; every fluid scene waits at least100 actual server ticks.");
                return List.of(shore,snow,stream,fall);
            }
        }
        throw new IllegalStateException("Natural hydrology scenes missing: shore="+(shore!=null)+" snow="+(snow!=null)+" stream="+(stream!=null)+" waterfall="+(fall!=null));
    }
    private static Scene scene(ServerLevel level,String id,BlockPos focus,BlockPos support,boolean fluid,boolean fall,JsonObject details,double y) {
        double[] heights=id.equals("mountain-stream")?new double[]{18,30,42,54,66,78}:new double[]{0,12,24};
        for(double dy:heights)for(int distance:new int[]{24,32,40})for(int[] d:new int[][]{{1,-1},{-1,-1},{1,1},{-1,1},{1,0},{0,-1}}) {
            double x=focus.getX()+.5+d[0]*distance,z=focus.getZ()+.5+d[1]*distance,cy=y+dy;
            if(!clear(level,x,cy,z)||!visible(level,x,cy,z,focus))continue;
            return new Scene(id,focus,support,fluid,fall,details,x,cy,z,focus.getY()+.5);
        }return null;
    }
    private static boolean openAbove(ServerLevel level,BlockPos p,int maximum) {
        for(int y=p.getY()+1;y<=maximum;y++){var q=p.atY(y);if(!level.getBlockState(q).getCollisionShape(level,q).isEmpty())return false;}
        return true;
    }
    private static boolean visible(ServerLevel level,double x,double y,double z,BlockPos focus) {
        var hit=level.clip(new ClipContext(new Vec3(x,y+1.62,z),Vec3.atCenterOf(focus),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty()));
        return hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(focus);
    }
    private static boolean clear(ServerLevel level,double x,double y,double z) {
        var profile=((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry();
        if(y<profile.minY()+6||y+2>=SeaSurface.cellMinimum(profile,(int)Math.floor(x),(int)Math.floor(z),true)-2)return false;
        return level.noCollision(new AABB(x-.3,y,z-.3,x+.3,y+1.9,z+.3))&&level.getFluidState(BlockPos.containing(x,y,z)).isEmpty()&&level.getFluidState(BlockPos.containing(x,y+1,z)).isEmpty();
    }
    private static BlockPos findSupport(ServerLevel level,BlockPos p){for(int y=p.getY()-1;y>=1;y--){var q=p.atY(y);if(!level.getBlockState(q).isAir()&&level.getFluidState(q).isEmpty())return q;}return p.below();}
    private static Object read(Object value,String accessor)throws ReflectiveOperationException{Method m=value.getClass().getDeclaredMethod(accessor);m.setAccessible(true);return m.invoke(value);}
    private static double number(Object value,String accessor)throws ReflectiveOperationException{return ((Number)read(value,accessor)).doubleValue();}
    private static JsonObject route(Object plan,List<?> points)throws ReflectiveOperationException {
        var d=new JsonObject();var array=new JsonArray();double previous=Double.POSITIVE_INFINITY,length=0;boolean monotonic=true;
        for(int i=0;i<points.size();i++){var p=points.get(i);double x=number(p,"x"),z=number(p,"z"),water=number(p,"water");monotonic&=water<=previous+1e-9;previous=water;var j=new JsonObject();j.addProperty("x",x);j.addProperty("z",z);j.addProperty("water_y",water);array.add(j);if(i>0)length+=Math.hypot(x-number(points.get(i-1),"x"),z-number(points.get(i-1),"z"));}
        d.add("natural_plan_points",array);d.addProperty("route_water_monotonic",monotonic);d.addProperty("route_length_blocks",length);
        d.addProperty("route_has_actual_fall_endpoint",read(plan,"fall")!=null);d.addProperty("route_reaches_lower_sea",read(plan,"fall")!=null||previous<=34);
        d.addProperty("route_ends_in_basin",read(plan,"fall")==null&&previous>34);return d;
    }
    private static void pose(ServerPlayer p,Scene s) {
        var level=p.serverLevel();for(BlockPos center:List.of(s.focus,BlockPos.containing(s.x,s.y,s.z)))for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk((center.getX()>>4)+dx,(center.getZ()>>4)+dz);
        require(clear(level,s.x,s.y,s.z),"Natural hydrology camera obstructed");
        require(visible(level,s.x,s.y,s.z,s.focus),"Natural hydrology subject is hidden behind terrain");
        double dx=s.focus.getX()+.5-s.x,dz=s.focus.getZ()+.5-s.z,dy=s.ty-(s.y+p.getEyeHeight());
        p.teleportTo(level,s.x,s.y,s.z,Set.of(),(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));p.setDeltaMovement(0,0,0);p.getAbilities().flying=true;p.onUpdateAbilities();p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));poseTick=level.getGameTime();
    }
    private static boolean ready(Minecraft mc,Scene s) {
        if(mc.player.position().distanceToSqr(s.x,s.y,s.z)>.1)return false;
        for(BlockPos center:List.of(s.focus,BlockPos.containing(s.x,s.y,s.z)))for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!mc.level.hasChunkAt(new BlockPos((center.getX()>>4)*16+dx*16,center.getY(),(center.getZ()>>4)*16+dz*16)))return false;
        return s.fluid?!mc.level.getFluidState(s.focus).isEmpty():mc.level.getBlockState(s.focus).is(MineralEcology.MINERAL_FROST.get())||mc.level.getBlockState(s.focus).is(MineralEcology.MINERAL_POWDER.get());
    }
    private static void verify(ServerLevel level,Scene s) {
        var out=s.details.deepCopy();out.addProperty("id",s.id);position(out,"focus",s.focus);out.addProperty("native_fluid_ticks_waited",level.getGameTime()-poseTick);out.addProperty("client_camera_and_target_3x3_loaded",true);out.addProperty("naturally_generated",true);
        require(visible(level,s.x,s.y,s.z,s.focus),"Fluid ticks left the photographed subject occluded");
        out.addProperty("actual_camera_collider_line_of_sight_clear",true);
        if(s.id.equals("mountain-stream")){require(openAbove(level,s.focus,((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry().maxLand()),"Mountain stream is underneath a stone canopy");out.addProperty("stream_exposed_above_natural_water_surface",true);}
        if(s.fluid) {
            require(heavy(level,s.focus),"Natural fluid vanished after actual fluid ticks: "+s.id);var state=level.getBlockState(s.focus);var fluid=state.getFluidState();
            out.addProperty("fluid_type",BuiltInRegistries.FLUID.getKey(fluid.getType()).toString());out.addProperty("source",fluid.isSource());out.addProperty("falling",fluid.hasProperty(FlowingFluid.FALLING)&&fluid.getValue(FlowingFluid.FALLING));out.addProperty("block_level",state.hasProperty(LiquidBlock.LEVEL)?state.getValue(LiquidBlock.LEVEL):-1);
            if(s.fall){require(!fluid.isSource()&&fluid.hasProperty(FlowingFluid.FALLING)&&fluid.getValue(FlowingFluid.FALLING),"Island waterfall body is still a static source column");
                int x=s.focus.getX(),z=s.focus.getZ(),top=s.details.get("waterfall_top").getAsInt();require(heavy(level,new BlockPos(x,top,z))&&heavy(level,new BlockPos(x,35,z)),"Natural waterfall does not connect its source to the lower ocean");out.addProperty("actual_fall_top_and_sea_connection",true);}
            else require(!level.getFluidState(s.support).isEmpty()||!level.getBlockState(s.support).isAir(),"Natural channel lacks its real bed/bank support");
        } else require(level.getBlockState(s.focus).is(MineralEcology.MINERAL_FROST.get())||level.getBlockState(s.focus).is(MineralEcology.MINERAL_POWDER.get()),"Natural alpine cover disappeared");
        if(s.id.equals("beach-river"))require(level.getBlockState(s.support).is(MineralEcology.TOXIC_SAND.get()),"River shoreline has no actual toxic sand bank");
        if(!data.has("scenes"))data.add("scenes",new JsonArray());data.getAsJsonArray("scenes").add(out);
    }
    private static boolean heavy(ServerLevel level,BlockPos p){var f=level.getFluidState(p);return f.is(Interstice.HEAVY.get())||f.is(Interstice.HEAVY_FLOW.get());}
    private static void position(JsonObject j,String key,BlockPos p){j.addProperty(key+"_x",p.getX());j.addProperty(key+"_y",p.getY());j.addProperty(key+"_z",p.getZ());}
    private static boolean done(){if(!work.isDone())return false;work.join();return true;}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),msg->System.out.println("HYDROLOGY_SCREENSHOT "+name));}
    private static void require(boolean yes,String message){if(!yes)throw new IllegalStateException(message);}
    private static void finish(Minecraft mc,boolean passed,String message){if(finished)return;if(closing){if(!passed){pendingPassed=false;reason=message;write(mc,false,true);}return;}closing=true;pendingPassed=passed;reason=message;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.broadcastOptions();settling=new NativeChunkSettler.Session("hydrology_terminal_shutdown",160);if(!passed)write(mc,false,true);}
    private static void close(Minecraft mc){if(mc.getSingleplayerServer()==null){finished=true;write(mc,pendingPassed,false);mc.stop();return;}if(settling.failed()&&!data.has("quiescence_failed")){pendingPassed=false;reason=settling.failure();data.addProperty("quiescence_failed",true);write(mc,false,true);}if(!settling.ready())return;data.add("settle_terminal",settling.report());data.addProperty("clean_generation_before_mc_stop",true);finished=true;settling=null;write(mc,pendingPassed,false);mc.stop();}
    private static void write(Minecraft mc,boolean passed,boolean pending){data.addProperty("passed",passed);data.addProperty("reason",reason);data.addProperty("stage",stage);data.addProperty("shutdown_pending",pending);try{Files.writeString(mc.gameDirectory.toPath().resolve("hydrology-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception error){error.printStackTrace();}System.out.println("HYDROLOGY_VALIDATION "+data);}
}
