package pro.erez.interstice.client;

import com.google.gson.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.*;
import pro.erez.interstice.agriculture.NativeCropBlock;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.minerals.*;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.*;
import pro.erez.interstice.worldgen.terrain.TensionTerrainV6;

/** Six real integrated worlds in one native client JVM. No seed mutation or synthetic FULL claims. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class TensionRealmVisualSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("interstice.tensionRealmSmoke");
    private static final long[] SEEDS={0,1,-1,20261006L,76198123L,4294967297L};
    private static final String[] BIOMES={"ash_islands","pale_gardens","stone_vaults","crimson_thickets"};
    private static boolean started,finished,disconnecting,closing,pinned,passed,abortAfterCurrent;
    private static volatile boolean screenshotSaved;
    private static int seedIndex,stage,ticks,imageIndex,clientTicks;
    private static long deadline;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static Prepared prepared;
    private static JsonObject row;
    private static final JsonObject data=new JsonObject();
    private static final JsonArray worlds=new JsonArray();
    private static volatile int perfMode;
    private static volatile int actualServerTick;
    private static long preWall,preCpu,previousPre;
    private static long observedHeapMax;
    private static final java.lang.management.ThreadMXBean THREADS=ManagementFactory.getThreadMXBean();
    static {if(ENABLED&&THREADS.isThreadCpuTimeSupported()&&!THREADS.isThreadCpuTimeEnabled())THREADS.setThreadCpuTimeEnabled(true);}
    private static final List<Long> allWall=new ArrayList<>(),allCpu=new ArrayList<>(),intervals=new ArrayList<>(),calmWall=new ArrayList<>(),calmCpu=new ArrayList<>(),surgeWall=new ArrayList<>(),surgeCpu=new ArrayList<>();
    private record Scene(String id,BlockPos target,double x,double y,double z,double tx,double ty,double tz,String scope) {}
    private record Prepared(JsonObject metrics,List<Scene> scenes,Set<Long> inspectedChunks) {}
    private record Candidate(String biome,BlockPos point) {}
    private TensionRealmVisualSmoke() {}

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pre(ServerTickEvent.Pre event){
        if(!ENABLED)return;preWall=System.nanoTime();preCpu=THREADS.isCurrentThreadCpuTimeSupported()?THREADS.getCurrentThreadCpuTime():-1;
        if(previousPre>0&&perfMode>0)intervals.add(preWall-previousPre);previousPre=preWall;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(ServerTickEvent.Post event){
        if(!ENABLED)return;
        actualServerTick=event.getServer().getTickCount();
        if(perfMode>0){var runtime=Runtime.getRuntime();observedHeapMax=Math.max(observedHeapMax,runtime.totalMemory()-runtime.freeMemory());}
        if(perfMode>0&&preWall>0){long wall=System.nanoTime()-preWall,cpu=preCpu<0?-1:THREADS.getCurrentThreadCpuTime()-preCpu;
            allWall.add(wall);if(cpu>=0)allCpu.add(cpu);
            if(perfMode==2){calmWall.add(wall);if(cpu>=0)calmCpu.add(cpu);}if(perfMode==3){surgeWall.add(wall);if(cpu>=0)surgeCpu.add(cpu);}
        }
        if(pinned)TideManager.getSavedData(event.getServer()).setPhase(TidePhase.CALM,24000);
        var s=settling;if(s!=null)s.tick(event.getServer());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||finished||disconnecting)return;clientTicks++;var mc=Minecraft.getInstance();
        try{
            if(started&&clientTicks%200==0)System.out.println("V6_FULL_MATRIX_HEARTBEAT seed_index="+seedIndex+" stage="+stage+" image="+imageIndex+" player="+(mc.player==null?"not_joined":mc.player.position()));
            if(!started&&mc.screen instanceof TitleScreen){
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(90);
                require(mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').contains("/.verification/"),"FULL matrix needs an isolated .verification profile");
                configure(mc);data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("scope","Six real fresh integrated worlds and one native Creative client. Fixed bounded FULL/natural inspection, not human Survival or dedicated multiplayer.");
                data.addProperty("cold_restart_performed",false);data.addProperty("four_eight_player_cpu_equivalents_performed",false);data.add("worlds",worlds);startWorld(mc);return;
            }
            if(!started)return;if(!closing&&stage!=7)require(System.nanoTime()<deadline,"Six-world FULL matrix90-minute deadline");
            if(closing){shutdown(mc);return;}if(SmokeWorldPrompts.advance(mc))return;
            if(stage==10){if(mc.getSingleplayerServer()==null&&mc.level==null&&mc.screen instanceof TitleScreen){startWorld(mc);stage=0;}return;}
            if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.screen!=null)return;
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==0){
                var actualLevel=server.getLevel(IslandWorld.TENSION_WORLD);require(actualLevel!=null,"V6 dimension missing after world join");row.addProperty("actual_server_seed",actualLevel.getSeed());
                work=server.submit(()->{try{return prepare(server.getLevel(IslandWorld.TENSION_WORLD),mc.gameDirectory.toPath());}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}});
                settling=new NativeChunkSettler.Session("v6_seed_"+SEEDS[seedIndex]+"_full_preparation",12000);stage=1;ticks=0;write(mc);return;
            }
            if(stage==1&&done()){
                require(!settling.failed(),settling.failure());if(!settling.ready())return;prepared=(Prepared)work.join();row.add("natural_full",prepared.metrics);row.add("settle_before_benchmark",settling.report());settling=null;
                work=server.submit(()->{pose(server.getPlayerList().getPlayer(uuid),server.getLevel(IslandWorld.TENSION_WORLD),prepared.scenes.get(5),false);TideManager.getSavedData(server).setPhase(TidePhase.CALM,24000);TideSync.broadcast(TideManager.getState(server));perfMode=2;return server.getTickCount();});
                stage=2;ticks=0;write(mc);return;
            }
            if(stage==2&&done()){
                int began=(Integer)work.join();if(actualServerTick-began<200)return;
                work=server.submit(()->{perfMode=3;TideManager.getSavedData(server).setPhase(TidePhase.SURGE,12000);TideSync.broadcast(TideManager.getState(server));return server.getTickCount();});stage=3;write(mc);return;
            }
            if(stage==3&&done()){
                int began=(Integer)work.join();if(actualServerTick-began<200)return;
                work=server.submit(()->{var metrics=performance();perfMode=0;pinned=true;TideManager.getSavedData(server).setPhase(TidePhase.CALM,24000);TideSync.broadcast(TideManager.getState(server));
                    var level=server.getLevel(IslandWorld.TENSION_WORLD);level.setWeatherParameters(0,0,false,false);level.setDayTime(18000);server.tickRateManager().setFrozen(true);
                    pose(server.getPlayerList().getPlayer(uuid),level,prepared.scenes.getFirst(),false);return metrics;});stage=4;imageIndex=0;ticks=0;return;
            }
            if(stage==4&&done()){
                if(!row.has("one_player_performance")){row.add("one_player_performance",(JsonObject)work.join());write(mc);}
                var scene=prepared.scenes.get(imageIndex/2);boolean nv=(imageIndex&1)==1;
                if(!ready(mc,scene,nv)){ticks=0;return;}if(++ticks<60)return;capture(mc,scene,nv);stage=5;return;
            }
            if(stage==5){
                if(!screenshotSaved)return;var scene=prepared.scenes.get(imageIndex/2);Path shot=mc.gameDirectory.toPath().resolve("screenshots").resolve(filename(scene,(imageIndex&1)==1));require(Files.isRegularFile(shot),"Native shot missing "+shot);
                row.getAsJsonArray("captures").get(row.getAsJsonArray("captures").size()-1).getAsJsonObject().addProperty("png_sha256",sha(shot));
                if(++imageIndex<prepared.scenes.size()*2){work=server.submit(()->{pose(server.getPlayerList().getPlayer(uuid),server.getLevel(IslandWorld.TENSION_WORLD),prepared.scenes.get(imageIndex/2),(imageIndex&1)==1);return true;});stage=4;ticks=0;}
                else{work=server.submit(()->finalSnapshot(server.getLevel(IslandWorld.TENSION_WORLD)));stage=6;}write(mc);return;
            }
            if(stage==6&&done()){
                row.add("final_static_snapshot",(JsonObject)work.join());row.addProperty("native_capture_count",row.getAsJsonArray("captures").size());
                boolean criteria=prepared.metrics.getAsJsonArray("missing_in_declared_windows").isEmpty()&&row.getAsJsonObject("one_player_performance").get("p95_cpu_under50ms").getAsBoolean();row.addProperty("criteria_met",criteria);row.addProperty("passed",criteria);closeWorld(mc);return;
            }
            if(stage==7){
                if(settling.failed()){row.addProperty("passed",false);row.addProperty("shutdown_error",settling.failure());write(mc);}
                if(!settling.ready())return;row.add("settle_before_world_disconnect",settling.report());row.addProperty("clean_generation_before_world_disconnect",true);settling=null;write(mc);
                var old=server;disconnecting=true;if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());disconnecting=false;require(old.isStopped(),"Vanilla disconnect returned before the old integrated server stopped");
                row.addProperty("old_integrated_server_stopped",true);write(mc);seedIndex++;
                if(abortAfterCurrent){data.addProperty("passed",false);data.addProperty("reason","Matrix deadline/error aborted after clean world shutdown");finished=true;write(mc);mc.stop();return;}
                if(seedIndex<SEEDS.length){stage=10;return;}
                passed=worlds.size()==6;for(var w:worlds)passed&=w.getAsJsonObject().get("passed").getAsBoolean();
                data.addProperty("six_distinct_real_worlds_created",worlds.size()==6);data.addProperty("passed",passed);data.addProperty("finished",true);data.addProperty("reason",passed?"Six actual FULL seed worlds, bounded resources/routes, natural native captures and clean vanilla transitions":"One or more explicit bounded acceptance windows failed; see per-world missing criteria");
                verifySeedDistinction();finished=true;write(mc);mc.stop();return;
            }
        }catch(Throwable e){e.printStackTrace();if(System.nanoTime()>=deadline||e instanceof OutOfMemoryError)abortAfterCurrent=true;if(row!=null){row.addProperty("passed",false);row.addProperty("error",e.toString());}write(mc);if(mc.getSingleplayerServer()!=null&&!closing&&stage!=7){closeWorld(mc);}else if(stage!=7){passed=false;closing=true;settling=new NativeChunkSettler.Session("v6_matrix_failure_shutdown",12000);}}
    }
    private static void configure(Minecraft mc){mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);mc.options.fov().set(70);mc.options.gamma().set(.5);mc.options.graphicsMode().set(GraphicsStatus.FANCY);mc.options.ambientOcclusion().set(true);mc.options.bobView().set(false);mc.getWindow().setWindowed(1280,720);mc.resizeDisplay();}
    private static String worldName(){return "v6-full-seed-"+SEEDS[seedIndex];}
    private static void startWorld(Minecraft mc){
        configure(mc);
        require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/"+worldName()+"/level.dat")),"A fixed seed save already exists; no seed mutation permitted");
        row=new JsonObject();row.addProperty("requested_seed",SEEDS[seedIndex]);row.addProperty("save_name",worldName());row.addProperty("passed",false);row.add("captures",new JsonArray());worlds.add(row);
        prepared=null;work=null;pinned=false;perfMode=1;actualServerTick=0;observedHeapMax=0;allWall.clear();allCpu.clear();intervals.clear();calmWall.clear();calmCpu.clear();surgeWall.clear();surgeCpu.clear();previousPre=0;
        var rules=new GameRules();rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
        System.out.println("V6_FULL_MATRIX_CREATE actual_new_world seed="+SEEDS[seedIndex]+" index="+seedIndex);
        mc.createWorldOpenFlows().createFreshLevel(worldName(),new LevelSettings("Natural V6 FULL seed "+SEEDS[seedIndex],GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(SEEDS[seedIndex],false,false),WorldPresets::createNormalWorldDimensions,mc.screen);write(mc);
    }
    private static Prepared prepare(ServerLevel level,Path profile)throws Exception{
        require(level!=null,"V6 dimension missing");var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();require(generator.terrainRevision()==6,"Inspected archived generator");require(level.getSeed()==SEEDS[seedIndex],"Actual ServerLevel seed differs from requested seed");
        var metrics=new JsonObject();metrics.addProperty("actual_server_seed",level.getSeed());metrics.addProperty("terrain_revision",6);metrics.addProperty("dimension",level.dimension().location().toString());metrics.addProperty("numeric_search_radius",2048);metrics.addProperty("numeric_search_spacing",64);
        var random=level.getChunkSource().randomState();var candidates=new TreeMap<String,List<Candidate>>();for(String id:BIOMES)candidates.put(id,new ArrayList<>());int screened=0,columns=0;
        for(int radius=0;radius<=32;radius++){
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;int x=dx*64+7,z=dz*64+7;screened++;
                if(Math.abs(x)>2048||Math.abs(z)>2048)continue;String biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),12,QuartPos.fromBlock(z),random.sampler()).unwrapKey().orElseThrow().location().getPath();
                var list=candidates.get(biome);if(list==null||list.size()>=8)continue;var column=generator.getBaseColumn(x,z,level,random);columns++;int top=-1;
                for(int y=generator.geometry().maxLand();y>generator.geometry().lowerSeaTop();y--)if(V6CaveSurvey.mass(column.getBlock(y))){top=y;break;}
                if(top>=generator.geometry().lowerSeaTop()+1)list.add(new Candidate(biome,new BlockPos(x,top,z)));
            }
            System.out.println("V6_FULL_MATRIX_NUMERIC seed="+level.getSeed()+" radius="+(radius*64)+" candidates="+candidates.entrySet().stream().map(e->e.getKey()+":"+e.getValue().size()).toList());
            if(candidates.values().stream().allMatch(c->c.size()>=8))break;
        }
        metrics.addProperty("numeric_biome_points_checked",screened);metrics.addProperty("numeric_columns_checked",columns);var plots=new JsonArray();var chosen=new LinkedHashMap<String,BlockPos>();var inspected=new TreeSet<Long>();var pairRows=new JsonArray();var originRows=new JsonArray();var missing=new JsonArray();
        for(int cx=-1;cx<=0;cx++)for(int cz=-1;cz<=0;cz++){var chunk=full(level,cx,cz,inspected);originRows.add(chunkStats(level,chunk,true));}metrics.add("fixed_origin_full_2x2",originRows);
        var distant=new JsonArray();for(var p:List.of(new ChunkPos(47,-32),new ChunkPos(-48,31)))for(int dx=0;dx<=1;dx++)distant.add(chunkStats(level,full(level,p.x+dx,p.z,inspected),true));metrics.add("fixed_distant_full_neighbor_pairs",distant);
        for(String id:BIOMES){
            BlockPos actual=null;var evaluations=new JsonArray();
            for(var candidate:candidates.get(id)){
                var chunk=full(level,candidate.point.getX()>>4,candidate.point.getZ()>>4,inspected);actual=surface(level,chunk,id);var attempt=V6CaveSurvey.json(candidate.point);attempt.addProperty("actual_dry_ground_found",actual!=null);evaluations.add(attempt);if(actual!=null)break;
            }
            require(actual!=null,"No actual dry FULL "+id+" among eight bounded candidates on seed "+level.getSeed());chosen.put(id,actual);loadPlot(level,actual,25,inspected);
            var plot=new JsonObject();plot.addProperty("biome",id);plot.add("actual_ground",V6CaveSurvey.json(actual));plot.addProperty("radius_blocks",24);plot.add("candidate_evaluations",evaluations);
            plot.add("actual_loaded_statistics",plotStats(level,actual,24));plots.add(plot);
            for(int dx=0;dx<=1;dx++){var chunk=full(level,(actual.getX()>>4)+dx,actual.getZ()>>4,inspected);pairRows.add(chunkStats(level,chunk,false));}
            System.out.println("V6_FULL_MATRIX_BIOME seed="+level.getSeed()+" id="+id+" actual="+actual+" inspected_FULL="+inspected.size());
        }
        metrics.add("four_natural_plots",plots);metrics.add("natural_full_neighbor_pairs",pairRows);
        try{var landing=IslandWorld.findLanding(level);metrics.add("actual_safe_entry_candidate",V6CaveSurvey.json(landing));metrics.addProperty("safe_entry_search_scope","Existing read-only V6 landing API, bounded by its64 FULL budget; no echo or terrain edits performed");}
        catch(RuntimeException unavailable){metrics.addProperty("safe_entry_search_error",unavailable.toString());missing.add("Existing bounded landing API could not find a real safe entry");}
        var cave=V6CaveSurvey.survey(level,chosen.get("stone_vaults"),24,generator.geometry().lowerSeaTop()+1,generator.geometry().maxLand());metrics.add("standing_cave_graph",cave);
        if(!cave.get("accessible_cave_found").getAsBoolean())missing.add("No accessible subtractive cave in the declared stone-vault plot; exterior arches are reported separately");
        else if(!cave.get("camera_internal_tunnel_view_found").getAsBoolean())missing.add("Accessible subtractive cave nodes exist, but no3-node interior standing viewing run with actual3..5-block ceiling in the declared plot");
        BlockPos coast=findCoast(level,generator,chosen.get("pale_gardens"),inspected,metrics);if(coast==null){missing.add("No dry lower-sea coast in fixed512-block garden window");coast=chosen.get("pale_gardens");}else loadPlot(level,coast,25,inspected);
        var structureCandidates=new ArrayList<RealmStructuresV5.Slot>();for(int rx=-8;rx<=7;rx++)for(int rz=-8;rz<=7;rz++){var slot=RealmStructuresV5.slot(level.getSeed(),rx,rz);if(slot!=null&&Math.abs(slot.chunk().getMinBlockX())<=2048&&Math.abs(slot.chunk().getMinBlockZ())<=2048)structureCandidates.add(slot);}
        structureCandidates.sort(Comparator.comparingLong(s->(long)s.chunk().x*s.chunk().x+(long)s.chunk().z*s.chunk().z));var structureRows=new JsonArray();int structureInspected=0;
        for(var slot:structureCandidates){if(structureInspected>=32)break;full(level,slot.chunk().x,slot.chunk().z,inspected);var sr=new JsonObject();sr.addProperty("chunk_x",slot.chunk().x);sr.addProperty("chunk_z",slot.chunk().z);sr.addProperty("family",slot.family().name());sr.addProperty("template",slot.template().toString());structureRows.add(sr);structureInspected++;}
        metrics.addProperty("structure_proposals_in_radius2048",structureCandidates.size());metrics.addProperty("structure_full_budget",32);metrics.add("structure_candidates_inspected",structureRows);
        var resources=resources(level,inspected);metrics.add("initial_resource_window",resources);
        if(resources.get("silver_ore_blocks").getAsInt()==0)missing.add("No silver ore in the declared FULL plots/candidate chunks");
        if(resources.get("native_crop_blocks").getAsInt()==0)missing.add("No natural crop in the declared FULL plots/coast/candidate chunks");
        if(resources.get("assigned_observation_post_loot_sources").getAsInt()==0)missing.add("No actual naturally placed coupler-source container within the32 structure proposal budget");
        var scenes=new ArrayList<Scene>();for(String id:BIOMES)scenes.add(eye(level,"biome-"+id,chosen.get(id)));
        scenes.add(side(level,"ash-side",chosen.get("ash_islands"),metrics));scenes.add(forest(level,chosen.get("pale_gardens")));
        if(scenes.get(4).id.contains("unavailable"))missing.add("No verified free-air underside view of actual separated ash mass intervals in the fixed49x49 plot");
        if(scenes.get(5).id.contains("unavailable"))missing.add("No clear natural player-height under-canopy route in the fixed49x49 garden plot");
        scenes.add(cave.get("accessible_cave_found").getAsBoolean()&&cave.get("camera_internal_tunnel_view_found").getAsBoolean()?caveScene(cave):eye(level,"tunnel-interior-view-unavailable-vault-surface",chosen.get("stone_vaults")));
        scenes.add(coast.getY()<=40?coastScene(level,coast):eye(level,"coast-unavailable-garden-surface",coast));
        for(var scene:scenes){loadPlot(level,scene.target,18,inspected);loadPlot(level,BlockPos.containing(scene.x,scene.y,scene.z),18,inspected);require(clear(level,scene.x,scene.y,scene.z),"Natural camera blocked "+scene.id);}
        var cameras=new JsonArray();for(var scene:scenes)cameras.add(sceneJson(scene));metrics.add("natural_cameras",cameras);metrics.add("missing_in_declared_windows",missing);metrics.addProperty("requested_actual_full_chunks",inspected.size());
        metrics.addProperty("geometry_edits",0);metrics.addProperty("inspection_world_random_ticks",0);metrics.addProperty("selected_plots_are_not_unbiased_global_distribution",true);
        var sections=new JsonArray();sections.add(exportSection(level,chosen.get("ash_islands"),profile,"ash"));sections.add(exportSection(level,chosen.get("stone_vaults"),profile,"vault"));metrics.add("actual_FULL_voxel_sections",sections);
        return new Prepared(metrics,List.copyOf(scenes),Set.copyOf(inspected));
    }
    private static LevelChunk full(ServerLevel level,int x,int z,Set<Long> inspected){long began=System.nanoTime();var chunk=level.getChunk(x,z);require(chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL),"Earlier than FULL chunk");if(inspected.add(chunk.getPos().toLong()))System.out.println("V6_FULL_MATRIX_CHUNK seed="+level.getSeed()+" x="+x+" z="+z+" elapsed_ms="+(System.nanoTime()-began)/1_000_000);return chunk;}
    private static void loadPlot(ServerLevel level,BlockPos centre,int radius,Set<Long> inspected){for(int x=(centre.getX()-radius)>>4;x<=(centre.getX()+radius)>>4;x++)for(int z=(centre.getZ()-radius)>>4;z<=(centre.getZ()+radius)>>4;z++)full(level,x,z,inspected);}
    private static BlockPos surface(ServerLevel level,LevelChunk chunk,String expected){
        var profile=((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry();
        for(int x=chunk.getPos().getMinBlockX()+2;x<=chunk.getPos().getMaxBlockX()-2;x++)for(int z=chunk.getPos().getMinBlockZ()+2;z<=chunk.getPos().getMaxBlockZ()-2;z++)for(int y=profile.maxLand();y>=profile.lowerSeaTop();y--){
            var p=new BlockPos(x,y,z);if(!V6CaveSurvey.mass(chunk.getBlockState(p)))continue;
            if(clear(level,x+.5,y+1,z+.5)&&chunk.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(y),QuartPos.fromBlock(z)).unwrapKey().orElseThrow().location().getPath().equals(expected))return p;break;
        }return null;
    }
    private static JsonObject chunkStats(ServerLevel level,LevelChunk chunk,boolean fixed){
        var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();var profile=generator.geometry();var digest=digest();var stat=digest();long rock=0,heavy=0,upper=0,baseRockLost=0,baseAirFilled=0,compared=0;var pos=new BlockPos.MutableBlockPos();
        for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++){
            var column=generator.getBaseColumn(x,z,level,level.getChunkSource().randomState());double ceiling=SeaSurface.cellMinimum(profile,x,z,true);
            for(int y=0;y<256;y++){
                var s=chunk.getBlockState(pos.set(x,y,z));var expected=column.getBlock(y);put(digest,s.toString());put(stat,V6CaveSurvey.mass(s)||!s.getFluidState().isEmpty()||s.is(Blocks.BEDROCK)?s.toString():"nonstatic");compared++;
                if(y==0||y==255)require(s.is(Blocks.BEDROCK),"FULL shell mismatch");else{require(!s.is(Blocks.WATER)&&!s.is(Blocks.LAVA),"Vanilla fluid in V6");if(y>=Math.floor(ceiling)){require(s.is(Interstice.LIGHT_SEA.get()),"Upper sea changed");upper++;}else if(y>profile.maxLand())require(s.isAir(),"Natural upper clearance breached");}
                if(V6CaveSurvey.mass(s))rock++;if(s.is(Interstice.HEAVY_BLOCK.get()))heavy++;
                if(expected.is(Interstice.RIFTSTONE.get())&&!V6CaveSurvey.mass(s))baseRockLost++;if(expected.isAir()&&V6CaveSurvey.mass(s))baseAirFilled++;
            }
            put(digest,chunk.getNoiseBiome(QuartPos.fromBlock(x),12,QuartPos.fromBlock(z)).unwrapKey().orElseThrow().location().toString());
        }
        var o=new JsonObject();o.addProperty("chunk_x",chunk.getPos().x);o.addProperty("chunk_z",chunk.getPos().z);o.addProperty("fixed_before_selection",fixed);o.addProperty("actual_FULL_state_and_biome_sha256",hex(digest));o.addProperty("static_geology_fluid_sha256",hex(stat));o.addProperty("natural_mass",rock);o.addProperty("heavy_fluid",heavy);o.addProperty("upper_fluid",upper);o.addProperty("canonical_cells_compared_after_surface",compared);o.addProperty("canonical_base_rock_replaced_by_nonmass_after_surface",baseRockLost);o.addProperty("canonical_air_with_surface_or_structure_mass",baseAirFilled);o.addProperty("comparison_scope","Actual FULL after geology/flora/structures versus canonical base material column. Stage changes counted; raw NOISE equality is the prior independent144-chunk GT.");return o;
    }
    private static JsonObject plotStats(ServerLevel level,BlockPos centre,int radius){
        var profile=((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry();int columns=0,dry=0,canopy=0,multi=0,overhang=0,leaves=0;var morph=new TreeMap<String,Integer>();
        for(int x=centre.getX()-radius;x<=centre.getX()+radius;x++)for(int z=centre.getZ()-radius;z<=centre.getZ()+radius;z++){
            int runs=0;boolean last=false,leaf=false,solid=false,hang=false;
            for(int y=profile.lowerSeaTop()+1;y<=profile.maxLand();y++){var s=level.getBlockState(new BlockPos(x,y,z));boolean mass=V6CaveSurvey.mass(s);if(mass&&!last){runs++;if(y>profile.lowerSeaTop()+1)hang=true;}last=mass;solid|=mass;if(s.getBlock() instanceof LeavesBlock){leaf=true;leaves++;}}
            columns++;if(solid)dry++;if(solid&&leaf)canopy++;if(runs>=2)multi++;if(hang)overhang++;morph.merge(TensionTerrainV6.root(level.getChunkSource().randomState()).morphology(x,z).dominant(),1,Integer::sum);
        }
        var o=new JsonObject();o.addProperty("columns",columns);o.addProperty("columns_with_dry_mass",dry);o.addProperty("canopy_columns",canopy);o.addProperty("canopy_fraction",dry==0?0:canopy/(double)dry);o.addProperty("leaf_blocks",leaves);o.addProperty("multi_interval_mass_columns",multi);o.addProperty("columns_with_air_under_mass_above_sea",overhang);var m=new JsonObject();morph.forEach(m::addProperty);o.add("numeric_morphology_labels_at_actual_FULL_columns",m);return o;
    }
    private static BlockPos findCoast(ServerLevel level,IslandChunkGenerator generator,BlockPos garden,Set<Long> inspected,JsonObject metrics){
        int checked=0,fullCount=0;var random=level.getChunkSource().randomState();metrics.addProperty("coast_candidate_halo_radius",18);
        search:for(int radius=0;radius<=32;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;int x=garden.getX()+dx*16,z=garden.getZ()+dz*16;checked++;
            if(!generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),12,QuartPos.fromBlock(z),random.sampler()).is(RealmBiomes.PALE_GARDENS))continue;
            var column=generator.getBaseColumn(x,z,level,random);int top=-1;
            for(int y=generator.geometry().maxLand();y>=34;y--)if(V6CaveSurvey.mass(column.getBlock(y))){top=y;break;}if(top<34||top>38)continue;
            var chunk=full(level,x>>4,z>>4,inspected);fullCount++;loadPlot(level,new BlockPos(x,top,z),18,inspected);var ground=coastalGround(level,chunk);
            if(ground!=null){metrics.addProperty("coast_numeric_columns",checked);metrics.addProperty("coast_full_candidates",fullCount);metrics.addProperty("coast_radius_blocks",512);return ground;}
            if(fullCount>=24)break search;
        }
        metrics.addProperty("coast_numeric_columns",checked);metrics.addProperty("coast_full_candidates",fullCount);metrics.addProperty("coast_radius_blocks",512);metrics.addProperty("coast_full_candidate_budget",24);return null;
    }
    private static JsonObject resources(ServerLevel level,Set<Long> chunks){
        int silver=0,crops=0,fruits=0,containers=0,assigned=0;var locations=new JsonArray();
        for(long key:chunks){var p=new ChunkPos(key);var chunk=level.getChunkSource().getChunkNow(p.x,p.z);require(chunk!=null,"Inspected FULL chunk unexpectedly unloaded");
            for(int x=p.getMinBlockX();x<=p.getMaxBlockX();x++)for(int z=p.getMinBlockZ();z<=p.getMaxBlockZ();z++)for(int y=1;y<=205;y++){var at=new BlockPos(x,y,z);var s=chunk.getBlockState(at);if(s.is(MineralEcology.RIFTSILVER_SEAM.get())||s.is(Interstice.RIFTSILVER_ORE.get()))silver++;if(s.getBlock() instanceof NativeCropBlock)crops++;if(s.is(GardenMaterials.CROWN_FRUIT.get()))fruits++;}
            // FULL chunks can still carry worldgen block entities as pending NBT. The union
            // includes live and packed positions; saving metadata never calls unpackLootTable.
            for(var at:chunk.getBlockEntitiesPos()){
                var pending=chunk.getBlockEntityNbt(at);var tag=pending!=null?pending:chunk.getBlockEntityNbtForSaving(at,level.registryAccess());
                if(tag==null||!tag.contains("LootTable"))continue;containers++;
                if(tag.getString("LootTable").equals("interstice:chests/v5/observation_post")){assigned++;var r=V6CaveSurvey.json(at);r.addProperty("loot_table",tag.getString("LootTable"));r.addProperty("pending_worldgen_nbt",pending!=null);r.addProperty("loot_opened",false);locations.add(r);}
            }
        }
        var o=new JsonObject();o.addProperty("scope","Counts in explicitly requested origin/biome/coast FULL plots and first32 distance-sorted natural structure proposals; not the entire radius2048 world. Unopened assigned observation-post table guarantees its coupler entry; no physical inventory claim.");o.addProperty("full_chunks",chunks.size());o.addProperty("silver_ore_blocks",silver);o.addProperty("native_crop_blocks",crops);o.addProperty("natural_crown_fruit_blocks",fruits);o.addProperty("natural_assigned_loot_containers",containers);o.addProperty("assigned_observation_post_loot_sources",assigned);o.add("coupler_source_locations",locations);return o;
    }
    private static Scene eye(ServerLevel level,String id,BlockPos ground){return new Scene(id,ground,ground.getX()+.5,ground.getY()+1,ground.getZ()+.5,ground.getX()+5.5,ground.getY()+2.6,ground.getZ()+.5,"Natural FULL surface; standard player eye height. No terrain clearing or issued materials.");}
    private static BlockPos coastalGround(ServerLevel level,LevelChunk chunk){
        var profile=((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry();
        for(int x=chunk.getPos().getMinBlockX()+2;x<=chunk.getPos().getMaxBlockX()-2;x++)for(int z=chunk.getPos().getMinBlockZ()+2;z<=chunk.getPos().getMaxBlockZ()-2;z++)for(int y=profile.maxLand();y>=profile.lowerSeaTop();y--){
            var p=new BlockPos(x,y,z);if(!V6CaveSurvey.mass(chunk.getBlockState(p)))continue;
            if(y<=profile.lowerSeaTop()+4&&level.getBiome(p).is(RealmBiomes.PALE_GARDENS)&&clear(level,x+.5,y+1,z+.5)&&seaNeighbor(level,p)!=null)return p;break;
        }return null;
    }
    private static Scene side(ServerLevel level,String id,BlockPos centre,JsonObject metrics){
        var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();var profile=generator.geometry();var sampler=pro.erez.interstice.worldgen.terrain.NativeColumnSamplerV6.of(level.getChunkSource().randomState());int columns=0,cameras=0;
        for(int x=centre.getX()-24;x<=centre.getX()+24;x+=2)for(int z=centre.getZ()-24;z<=centre.getZ()+24;z+=2){
            if(!level.getBiome(new BlockPos(x,64,z)).is(RealmBiomes.ASH_ISLANDS))continue;columns++;var intervals=new ArrayList<int[]>();int start=-1;
            for(int y=profile.minLand();y<=profile.maxLand();y++){boolean mass=V6CaveSurvey.mass(level.getBlockState(new BlockPos(x,y,z)));if(mass&&start<0)start=y;if(start>=0&&(!mass||y==profile.maxLand())){intervals.add(new int[]{start,mass?y:y-1});start=-1;}}
            for(int band=1;band<intervals.size();band++){var lower=intervals.get(band-1);var upper=intervals.get(band);if(lower[1]-lower[0]<3||upper[1]-upper[0]<3||upper[0]-lower[1]<5)continue;
                int gapY=(upper[0]+lower[1])/2;if(sampler.density(x,gapY,z,true)>0)continue;var target=new BlockPos(x,upper[0],z);
                for(int distance:new int[]{6,10,14})for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int drop:new int[]{4,6,8}){
                    int cx=x+d[0]*distance,cz=z+d[1]*distance,cy=upper[0]-drop;
                    if(Math.abs(cx-centre.getX())>24||Math.abs(cz-centre.getZ())>24||cy<profile.minLand()||cy<lower[1]+1)continue;cameras++;
                    if(!clear(level,cx+.5,cy,cz+.5)||sampler.density(cx,cy+1,cz,true)>0)continue;
                    var eye=new Vec3(cx+.5,cy+1.62,cz+.5);var aim=new Vec3(x+.5,upper[0]+.15,z+.5);boolean visible=true;int steps=(int)Math.ceil(eye.distanceTo(aim)*4);
                    for(int n=1;n<steps;n++){var at=BlockPos.containing(eye.lerp(aim,n/(double)steps));if(at.equals(target))break;if(!level.getBlockState(at).isAir()){visible=false;break;}}
                    if(!visible)continue;var proof=new JsonObject();proof.addProperty("bounded_plot_radius",24);proof.addProperty("mass_columns_checked",columns);proof.addProperty("camera_candidates_checked",cameras);proof.add("underside_target",V6CaveSurvey.json(target));proof.addProperty("lower_interval_min_y",lower[0]);proof.addProperty("lower_interval_max_y",lower[1]);proof.addProperty("upper_interval_min_y",upper[0]);proof.addProperty("upper_interval_max_y",upper[1]);proof.addProperty("geographic_uncarved_air_gap",true);proof.addProperty("camera_in_exterior_uncarved_air",true);proof.addProperty("actual_unobstructed_view_to_mass",true);metrics.add("ash_underside_voxel_proof",proof);
                    return new Scene("ash-separated-plates-underside",target,cx+.5,cy,cz+.5,aim.x,aim.y,aim.z,"Natural side/underside of two actual separated ash mass intervals; exterior uncarved-air camera below upper plate, verified ray to its lower face. No terrain edits.");
                }
            }
        }
        var failure=new JsonObject();failure.addProperty("mass_columns_checked",columns);failure.addProperty("camera_candidates_checked",cameras);failure.addProperty("bounded_plot_radius",24);metrics.add("ash_underside_voxel_proof",failure);return eye(level,id+"-underside-view-unavailable",centre);
    }
    private static Scene forest(ServerLevel level,BlockPos fallback){
        for(int x=fallback.getX()-24;x<=fallback.getX()+24;x++)for(int z=fallback.getZ()-24;z<=fallback.getZ()+24;z++)for(int y=35;y<=180;y++){
            var p=new BlockPos(x,y,z);if(!V6CaveSurvey.mass(level.getBlockState(p))||!clear(level,x+.5,y+1,z+.5))continue;boolean covered=false;
            if(!level.getBiome(p).is(RealmBiomes.PALE_GARDENS))continue;
            for(int a=y+2;a<=Math.min(y+36,205);a++){var state=level.getBlockState(new BlockPos(x,a,z));if(V6CaveSurvey.mass(state))break;if(a>=y+4&&state.getBlock() instanceof LeavesBlock){covered=true;break;}}
            if(!covered)continue;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                if(Math.abs(x+d[0]*6-fallback.getX())>24||Math.abs(z+d[1]*6-fallback.getZ())>24)continue;
                boolean view=true;for(int distance=1;distance<=6;distance++){
                    var ray=new BlockPos(x+d[0]*distance,y+2,z+d[1]*distance);if(!level.getBlockState(ray).isAir()){view=false;break;}
                }
                if(!view)continue;int walkingY=y+1;
                for(int distance=1;distance<=2;distance++){
                    boolean forward=false;for(int step:new int[]{0,-1,1}){
                        var feet=new BlockPos(x+d[0]*distance,walkingY+step,z+d[1]*distance);var floor=level.getBlockState(feet.below());
                        if(floor.getFluidState().isEmpty()&&floor.isFaceSturdy(level,feet.below(),net.minecraft.core.Direction.UP)
                            &&level.getBlockState(feet).isAir()&&level.getBlockState(feet.above()).isAir()){
                            walkingY=feet.getY();forward=true;break;
                        }
                    }
                    if(!forward){view=false;break;}
                }
                if(view)return new Scene("forest-eye-under-canopy",p,x+.5,y+1,z+.5,x+.5+d[0]*6,y+2.62,z+.5+d[1]*6,"Natural forest route at horizontal standing eye height; actual6m AIR viewing ray and two dry walkable forward cells, canopy observed in FULL blocks.");
            }
        }return eye(level,"forest-canopy-unavailable",fallback);
    }
    private static BlockPos seaNeighbor(ServerLevel level,BlockPos ground){for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int distance=2;distance<=8;distance+=2){var p=new BlockPos(ground.getX()+d[0]*distance,((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry().lowerSeaTop(),ground.getZ()+d[1]*distance);if(level.getBlockState(p).is(Interstice.HEAVY_BLOCK.get()))return p;}return null;}
    private static Scene coastScene(ServerLevel level,BlockPos ground){var sea=seaNeighbor(level,ground);if(sea==null)return eye(level,"coast-view-unavailable",ground);return new Scene("lower-coast",ground,ground.getX()+.5,ground.getY()+1,ground.getZ()+.5,sea.getX()+.5,sea.getY()+.8,sea.getZ()+.5,"Natural dry garden coast looking at actually observed lower heavy sea, standard player eye height.");}
    private static Scene caveScene(JsonObject report){var p=V6CaveSurvey.point(report.getAsJsonObject("camera_feet"));var target=V6CaveSurvey.point(report.getAsJsonObject("camera_target"));return new Scene("accessible-compact-tunnel-interior",p,p.getX()+.5,p.getY(),p.getZ()+.5,target.getX()+.5,report.get("camera_look_y").getAsDouble(),target.getZ()+.5,"Actual FULL interior view along at least3 consecutive dry standing subtractive nodes inside uncarved rock, actual ceiling3..5 blocks; recorded surface walking route, no exterior arch caption.");}
    private static boolean clear(ServerLevel level,double x,double y,double z){var p=BlockPos.containing(x,y,z);return level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir();}
    private static void pose(ServerPlayer player,ServerLevel level,Scene s,boolean nv){require(clear(level,s.x,s.y,s.z),"Natural camera changed "+s.id);double dx=s.tx-s.x,dz=s.tz-s.z,dy=s.ty-s.y-player.getEyeHeight();player.teleportTo(level,s.x,s.y,s.z,Set.of(),(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));player.setDeltaMovement(0,0,0);player.getAbilities().flying=true;player.onUpdateAbilities();player.removeAllEffects();if(nv)player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));}
    private static boolean ready(Minecraft mc,Scene scene,boolean nv){return mc.level.dimension().equals(IslandWorld.TENSION_WORLD)&&mc.player.position().distanceToSqr(scene.x,scene.y,scene.z)<.1&&mc.player.hasEffect(MobEffects.NIGHT_VISION)==nv&&mc.level.hasChunkAt(scene.target)&&mc.levelRenderer.isSectionCompiled(scene.target)&&mc.getWindow().getWidth()==1280&&mc.getWindow().getHeight()==720;}
    private static JsonObject sceneJson(Scene s){var o=V6CaveSurvey.json(s.target);o.addProperty("id",s.id);o.addProperty("camera_x",s.x);o.addProperty("camera_y",s.y);o.addProperty("camera_z",s.z);o.addProperty("look_at_x",s.tx);o.addProperty("look_at_y",s.ty);o.addProperty("look_at_z",s.tz);o.addProperty("scope",s.scope);o.addProperty("natural_blocks_edited",0);return o;}
    private static String filename(Scene scene,boolean nv){return "v6-seed-"+SEEDS[seedIndex]+"-"+scene.id+"-"+(nv?"nv":"dark")+".png";}
    private static void capture(Minecraft mc,Scene scene,boolean nv){var frame=sceneJson(scene);frame.addProperty("file",filename(scene,nv));frame.addProperty("night_vision",nv);frame.addProperty("seed",SEEDS[seedIndex]);frame.addProperty("actual_yaw",mc.player.getYRot());frame.addProperty("actual_pitch",mc.player.getXRot());row.getAsJsonArray("captures").add(frame);screenshotSaved=false;Screenshot.grab(mc.gameDirectory,filename(scene,nv),mc.getMainRenderTarget(),message->{System.out.println("V6_FULL_MATRIX_SCREENSHOT "+frame);screenshotSaved=true;});}
    private static JsonObject exportSection(ServerLevel level,BlockPos centre,Path profile,String label)throws Exception{
        int width=49,height=((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry().height();var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var columns=new JsonArray();
        for(int ix=0;ix<width;ix++){int x=centre.getX()+ix-24,z=centre.getZ();require(level.getChunkSource().getChunkNow(x>>4,z>>4)!=null,"Section left its declared loaded FULL plot");var intervals=new JsonArray();int start=-1;
            for(int y=0;y<height;y++){var s=level.getBlockState(new BlockPos(x,y,z));boolean mass=V6CaveSurvey.mass(s);image.setRGB(ix,height-1-y,mass?0x858995:s.is(Blocks.BEDROCK)?0x353b43:s.is(Interstice.HEAVY_BLOCK.get())?0x772c40:s.is(Interstice.LIGHT_SEA.get())?0xbeb783:s.getBlock() instanceof LeavesBlock?0x627e77:0x10151e);if(mass&&start<0)start=y;if(start>=0&&(!mass||y==height-1)){var run=new JsonArray();run.add(start);run.add(mass?y:y-1);intervals.add(run);start=-1;}}
            var c=new JsonObject();c.addProperty("x",x);c.addProperty("z",z);c.add("actual_mass_vertical_intervals",intervals);columns.add(c);
        }
        Path dir=profile.resolve("voxel-sections/seed-"+SEEDS[seedIndex]);Files.createDirectories(dir);Path png=dir.resolve(label+"-actual-FULL.png"),json=dir.resolve(label+"-actual-FULL.json");ImageIO.write(image,"PNG",png.toFile());Files.writeString(json,new GsonBuilder().setPrettyPrinting().create().toJson(columns));
        var section=new JsonObject();section.addProperty("scope","Cross-section plot from actual loaded FULL voxel states, not a native client cutaway or raw density preview");section.addProperty("png",profile.relativize(png).toString().replace('\\','/'));section.addProperty("png_sha256",sha(png));section.addProperty("interval_json",profile.relativize(json).toString().replace('\\','/'));section.addProperty("json_sha256",sha(json));section.addProperty("width_blocks",width);section.addProperty("height_blocks",height);section.addProperty("legend","grey=natural mass; dark grey=bedrock shell; crimson=lower sea; pale yellow=upper sea; green=leaves; black=other/air");return section;
    }
    private static JsonObject finalSnapshot(ServerLevel level){var o=new JsonObject();var chunks=new JsonArray();int unloaded=0;for(long key:new TreeSet<>(prepared.inspectedChunks)){var p=new ChunkPos(key);var chunk=level.getChunkSource().getChunkNow(p.x,p.z);if(chunk==null){unloaded++;continue;}var digest=digest();var pos=new BlockPos.MutableBlockPos();for(int x=p.getMinBlockX();x<=p.getMaxBlockX();x++)for(int z=p.getMinBlockZ();z<=p.getMaxBlockZ();z++)for(int y=0;y<256;y++){var s=chunk.getBlockState(pos.set(x,y,z));put(digest,V6CaveSurvey.mass(s)||!s.getFluidState().isEmpty()||s.is(Blocks.BEDROCK)?s.toString():"nonstatic");}var r=new JsonObject();r.addProperty("chunk_x",p.x);r.addProperty("chunk_z",p.z);r.addProperty("static_geology_fluid_sha256",hex(digest));chunks.add(r);}o.add("loaded_requested_chunks",chunks);o.addProperty("already_unloaded_requested_chunks",unloaded);o.addProperty("cold_restart_checked",false);return o;}
    private static JsonObject performance(){var o=new JsonObject();o.addProperty("scope","One actual native integrated Creative client,200 stationary CALM and200 actual SURGE server ticks. Not dedicated multiplayer, exploration timing or4/8-player equivalence.");o.add("server_highest_pre_to_lowest_post_wall",percentiles(allWall));o.add("server_thread_cpu",percentiles(allCpu));o.add("pre_to_pre_intervals_including_between_tick_tasks",percentiles(intervals));o.add("calm_wall",percentiles(calmWall));o.add("calm_cpu",percentiles(calmCpu));o.add("surge_wall",percentiles(surgeWall));o.add("surge_cpu",percentiles(surgeCpu));o.addProperty("server_cpu_time_supported",THREADS.isCurrentThreadCpuTimeSupported());o.addProperty("p95_cpu_under50ms",!allCpu.isEmpty()&&percentile(allCpu,.95)/1_000_000.0<=50);var memory=Runtime.getRuntime();o.addProperty("used_heap_bytes",memory.totalMemory()-memory.freeMemory());o.addProperty("heap_max_observed_on_server_post_bytes",observedHeapMax);long count=0,time=0;for(var gc:ManagementFactory.getGarbageCollectorMXBeans()){count+=Math.max(0,gc.getCollectionCount());time+=Math.max(0,gc.getCollectionTime());}o.addProperty("jvm_gc_count_cumulative",count);o.addProperty("jvm_gc_time_ms_cumulative",time);return o;}
    private static JsonObject percentiles(List<Long> values){var o=new JsonObject();o.addProperty("samples",values.size());for(double p:new double[]{.5,.95,.99,1})o.addProperty(p==1?"max_ms":"p"+(int)(p*100)+"_ms",percentile(values,p)/1_000_000.0);return o;}
    private static long percentile(List<Long> source,double q){if(source.isEmpty())return 0;var sorted=new ArrayList<>(source);Collections.sort(sorted);return sorted.get((int)Math.ceil((sorted.size()-1)*q));}
    private static void closeWorld(Minecraft mc){var server=mc.getSingleplayerServer();var id=mc.player==null?null:mc.player.getUUID();pinned=false;perfMode=0;if(server!=null)server.execute(()->{server.tickRateManager().setFrozen(false);var p=id==null?null:server.getPlayerList().getPlayer(id);if(p!=null)p.removeAllEffects();});mc.options.renderDistance().set(2);mc.options.broadcastOptions();settling=new NativeChunkSettler.Session("v6_seed_"+SEEDS[seedIndex]+"_before_disconnect",12000);stage=7;ticks=0;write(mc);}
    private static void shutdown(Minecraft mc){if(mc.getSingleplayerServer()!=null){if(settling!=null&&settling.ready()){disconnecting=true;if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());disconnecting=false;}else return;}passed=false;data.addProperty("passed",false);data.addProperty("finished",true);finished=true;write(mc);mc.stop();}
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void verifySeedDistinction(){var hashes=new HashMap<Long,String>();for(var item:worlds){var world=item.getAsJsonObject();if(!world.has("natural_full"))continue;var first=world.getAsJsonObject("natural_full").getAsJsonArray("fixed_origin_full_2x2").get(0).getAsJsonObject();hashes.put(world.get("requested_seed").getAsLong(),first.get("actual_FULL_state_and_biome_sha256").getAsString());}boolean distinct=hashes.size()==6&&new HashSet<>(hashes.values()).size()==6;data.addProperty("fixed_origin_full_hashes_distinguish_six_seeds",distinct);if(!distinct){passed=false;data.addProperty("passed",false);}}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new IllegalStateException(e);}}
    private static void put(MessageDigest d,String s){d.update(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));d.update((byte)0);}
    private static String hex(MessageDigest d){return HexFormat.of().formatHex(d.digest());}
    private static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void write(Minecraft mc){try{data.addProperty("seed_index",seedIndex);data.addProperty("stage",stage);data.addProperty("finished",finished);Files.writeString(mc.gameDirectory.toPath().resolve("v6-full-matrix-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception e){e.printStackTrace();}}
}
