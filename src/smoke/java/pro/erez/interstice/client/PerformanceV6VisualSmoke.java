package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import pro.erez.interstice.*;
import pro.erez.interstice.equipment.*;
import pro.erez.interstice.geometry.*;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.*;

/** One native integrated client; extra players are explicitly CPU-only NeoForge mocks. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class PerformanceV6VisualSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("interstice.v6PerformanceSmoke");
    private static final long SEED=20261006L;
    private static final String SAVE="v6-performance-20261006";
    private static final ChunkPos[] DISTRICTS={new ChunkPos(48,-32),new ChunkPos(-48,31),new ChunkPos(96,64),new ChunkPos(-96,-64)};
    private static final int[] POPULATIONS={1,4,8};
    private static final TicketType<ChunkPos> PROBE=TicketType.create("v6_performance_probe",Comparator.comparingLong(ChunkPos::toLong));
    private static final ThreadMXBean CPU=ManagementFactory.getThreadMXBean();
    private static final JsonObject report=new JsonObject();
    private static final JsonArray dimensions=new JsonArray(),captures=new JsonArray();
    private static final List<Mock> mocks=new ArrayList<>();
    private static final List<ServerPlayer> actors=new ArrayList<>();
    private static final List<ItemEntity> drops=new ArrayList<>();
    private static boolean started,closing,finished,disconnecting,passed;
    private static volatile boolean captured;
    private static int stage,dimensionIndex,districtIndex,populationIndex,tideIndex,clientTicks,waitTicks;
    private static long deadline,requestBegan,preWall,preCpu;
    private static volatile long requestFinished;
    private static CompletableFuture<?> work;
    private static volatile Sample sample;
    private static volatile Throwable serverFailure;
    private static volatile NativeChunkSettler.Session settling;
    private static ServerLevel level;
    private static JsonObject dimension,cold;
    private static BlockPos probe;
    private static int probeEnd;
    private static long warmupUntil;
    private static UUID realUuid;
    private record Mock(ServerPlayer player,EmbeddedChannel channel) {}
    private static final class Sample {
        final int population;final TidePhase tide;final long began=System.nanoTime(),gcCount=gcCount(),gcMs=gcMs();
        final List<Long> wall=new ArrayList<>(),cpu=new ArrayList<>(),intervals=new ArrayList<>();
        final Map<UUID,Integer> priorPlayerTick=new HashMap<>();
        final Map<UUID,Integer> playerPostsThisTick=new HashMap<>();
        final Map<UUID,Long> manualTicks=new HashMap<>(),nativeTicks=new HashMap<>();
        long heapPeak,previousPre,probeCalls,packInitially,vanillaInitially,outboundDrained;
        int nativePostsMin=Integer.MAX_VALUE,nativePostsMax;
        volatile int fpsCount,fpsMin=Integer.MAX_VALUE,fpsMax;volatile long fpsSum;
        volatile boolean done;boolean armed;
        Sample(int population,TidePhase tide){this.population=population;this.tide=tide;for(var player:actors)priorPlayerTick.put(player.getUUID(),player.tickCount);}
    }
    static {if(ENABLED&&CPU.isThreadCpuTimeSupported()&&!CPU.isThreadCpuTimeEnabled())CPU.setThreadCpuTimeEnabled(true);}
    private PerformanceV6VisualSmoke(){}

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pre(ServerTickEvent.Pre event){
        if(!ENABLED)return;preWall=System.nanoTime();preCpu=CPU.isCurrentThreadCpuTimeSupported()?CPU.getCurrentThreadCpuTime():-1;
        var at=sample;if(at!=null&&!at.done){at.armed=true;at.playerPostsThisTick.clear();if(at.previousPre>0)at.intervals.add(preWall-at.previousPre);at.previousPre=preWall;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void playerPost(PlayerTickEvent.Post event){if(!ENABLED||!(event.getEntity() instanceof ServerPlayer player))return;var at=sample;if(at!=null&&at.armed&&!at.done)at.playerPostsThisTick.merge(player.getUUID(),1,Integer::sum);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(ServerTickEvent.Post event){
        if(!ENABLED)return;
        var at=sample;
        try { if(at!=null&&at.armed&&!at.done){
            var real=actors.getFirst();int nativePosts=at.playerPostsThisTick.getOrDefault(real.getUUID(),0);require(nativePosts>=1&&nativePosts<=4,"Actual native client playerPost workload missing/unbounded");at.nativePostsMin=Math.min(at.nativePostsMin,nativePosts);at.nativePostsMax=Math.max(at.nativePostsMax,nativePosts);at.nativeTicks.merge(real.getUUID(),1L,Long::sum);
            for(var mock:mocks){var p=mock.player;int before=at.playerPostsThisTick.getOrDefault(p.getUUID(),0);if(before==nativePosts)at.nativeTicks.merge(p.getUUID(),1L,Long::sum);else{for(int calls=before;calls<nativePosts;calls++){int previous=at.playerPostsThisTick.getOrDefault(p.getUUID(),0);p.doTick();require(at.playerPostsThisTick.getOrDefault(p.getUUID(),0)>previous,"Public mock doTick failed to post actual player workload");at.manualTicks.merge(p.getUUID(),1L,Long::sum);}}require(at.playerPostsThisTick.getOrDefault(p.getUUID(),0)==nativePosts,"Mock CPU player did not match the observed real native playerPost workload");}
            for(var actor:actors){require(actor.isAlive()&&actor.gameMode.getGameModeForPlayer()==GameType.SURVIVAL,"Measured prepared player died/left Survival");require(ShelterDetector.isSheltered(level,actor),"Measured player's prepared roof ceased to shelter it");require(!ShelterDetector.hasRoofAt(level,GeometryProfiles.get(level),probe.getX(),probe.getY(),probe.getZ()),"Actual prepared maximum-height probe gained a shelter roof");at.probeCalls++;}
            for(var mock:mocks){mock.channel.runPendingTasks();for(Object packet;(packet=mock.channel.readOutbound())!=null;){ReferenceCountUtil.release(packet);at.outboundDrained++;}}
            Runtime runtime=Runtime.getRuntime();at.heapPeak=Math.max(at.heapPeak,runtime.totalMemory()-runtime.freeMemory());
            at.wall.add(System.nanoTime()-preWall);if(preCpu>=0)at.cpu.add(CPU.getCurrentThreadCpuTime()-preCpu);
            if(at.wall.size()==200){at.done=true;System.out.println("V6_PERFORMANCE_SAMPLES dimension="+level.dimension().location()+" population="+at.population+" tide="+at.tide+" actual_samples=200");}
        } }catch(Throwable error){serverFailure=error;sample=null;error.printStackTrace();}
        var pending=settling;if(pending!=null)pending.tick(event.getServer());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||finished||disconnecting)return;var mc=Minecraft.getInstance();clientTicks++;
        try{
            var measuring=sample;if(measuring!=null&&!measuring.done){int fps=mc.getFps();measuring.fpsMin=Math.min(measuring.fpsMin,fps);measuring.fpsMax=Math.max(measuring.fpsMax,fps);measuring.fpsSum+=fps;measuring.fpsCount++;}
            if(started&&clientTicks%200==0)System.out.println("V6_PERFORMANCE_HEARTBEAT stage="+stage+" dimension="+dimensionIndex+" district="+districtIndex+" population="+populationIndex+" tide="+tideIndex+" samples="+(sample==null?0:sample.wall.size()));
            if(!started){if(!(mc.screen instanceof TitleScreen))return;started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(20);require(mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').contains("/.verification/"),"Performance requires an isolated profile");require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/"+SAVE+"/level.dat")),"Performance requires an actually fresh seed save");configure(mc);
                report.addProperty("creator_pid",ProcessHandle.current().pid());report.addProperty("logical_processors_available_to_jvm",Runtime.getRuntime().availableProcessors());report.addProperty("java_version",System.getProperty("java.version"));report.addProperty("os_name",System.getProperty("os.name"));report.addProperty("requested_seed",SEED);report.addProperty("scope","One actual fresh native integrated client; 4/8 populations are prepared CPU equivalents with supported in-memory mock connections/public doTick, not independent TCP clients or human Survival routes");report.addProperty("render_distance",4);report.addProperty("simulation_distance",5);report.addProperty("fps_cap",60);report.addProperty("dedicated_multiplayer_proof",false);report.add("dimensions",dimensions);report.add("captures",captures);
                var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);mc.createWorldOpenFlows().createFreshLevel(SAVE,new LevelSettings("V6 performance comparison",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(SEED,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);write(mc);return;
            }
            if(closing){shutdown(mc);return;}if(serverFailure!=null)throw new IllegalStateException("Actual server benchmark failed",serverFailure);require(System.nanoTime()<deadline,"Performance20-minute deadline");if(SmokeWorldPrompts.advance(mc))return;
            if(mc.getSingleplayerServer()==null||mc.player==null||mc.level==null||mc.getConnection()==null)return;var server=mc.getSingleplayerServer();realUuid=mc.player.getUUID();
            if(stage==0){work=server.submit(()->{server.getPlayerList().setViewDistance(4);server.getPlayerList().setSimulationDistance(5);return true;});stage=1;return;}
            if(stage==1&&done()){beginDimension(server);districtIndex=0;requestCold(server);stage=2;return;}
            if(stage==2&&done()){
                boolean wasLoaded=(Boolean)work.join();cold=new JsonObject();var p=DISTRICTS[districtIndex];cold.addProperty("chunk_x",p.x);cold.addProperty("chunk_z",p.z);cold.addProperty("actual_full_loaded_before_request",wasLoaded);require(!wasLoaded,"Fixed cold district was already FULL-loaded; no silent coordinate change");requestBegan=System.nanoTime();requestFinished=0;work=level.getChunkSource().getChunkFuture(p.x,p.z,ChunkStatus.FULL,true).whenComplete((value,error)->requestFinished=System.nanoTime());stage=3;return;
            }
            if(stage==3&&done()){
                long nanos=requestFinished-requestBegan;var result=(ChunkResult<?>)work.join();require(result.orElse(null) instanceof LevelChunk,"Actual FULL request did not return a real LevelChunk");var chunk=(LevelChunk)result.orElse(null);cold.addProperty("cold_full_wall_ms",nanos/1e6);cold.addProperty("cold_client_poll_delay_ms",(System.nanoTime()-requestFinished)/1e6);cold.addProperty("actual_full_class",chunk.getClass().getName());cold.addProperty("actual_level_seed",level.getSeed());
                work=server.submit(()->{var p=DISTRICTS[districtIndex];level.getChunkSource().addRegionTicket(PROBE,p,0,p);cold.addProperty("voxel_fingerprint_before_fixtures",fingerprint(chunk));cold.addProperty("actual_biome",level.getBiome(chunk.getPos().getMiddleBlockPosition(64)).unwrapKey().orElseThrow().location().toString());return true;});stage=4;return;
            }
            if(stage==4&&done()){work=server.submit(()->level.getChunkSource().getChunkNow(DISTRICTS[districtIndex].x,DISTRICTS[districtIndex].z)!=null);stage=5;return;}
            if(stage==5&&done()){require((Boolean)work.join(),"Warm holder not actually FULL-loaded after bounded own probe ticket");cold.addProperty("actual_full_loaded_before_warm",true);requestBegan=System.nanoTime();requestFinished=0;var p=DISTRICTS[districtIndex];work=level.getChunkSource().getChunkFuture(p.x,p.z,ChunkStatus.FULL,true).whenComplete((value,error)->requestFinished=System.nanoTime());stage=6;return;}
            if(stage==6&&done()){
                require(((ChunkResult<?>)work.join()).orElse(null) instanceof LevelChunk,"Warm access lost its actual FULL chunk");cold.addProperty("warm_full_wall_ms",(requestFinished-requestBegan)/1e6);cold.addProperty("warm_client_poll_delay_ms",(System.nanoTime()-requestFinished)/1e6);dimension.getAsJsonArray("fixed_cold_and_immediate_warm").add(cold);
                work=server.submit(()->{var p=DISTRICTS[districtIndex];var player=server.getPlayerList().getPlayer(realUuid);player.setGameMode(GameType.CREATIVE);player.teleportTo(level,p.getMiddleBlockX()+.5,128,p.getMiddleBlockZ()+.5,Set.of(),0,0);player.getAbilities().flying=true;player.onUpdateAbilities();return true;});stage=7;waitTicks=0;write(mc);return;
            }
            if(stage==7&&done()){if(++waitTicks<40)return;var p=DISTRICTS[districtIndex];require(mc.level.dimension().equals(level.dimension()),"Native client never received the explored dimension");cold.addProperty("native_client_dimension_received",true);cold.addProperty("actual_client_x_after_fixture_teleport",mc.player.getX());cold.addProperty("actual_client_y_after_fixture_teleport",mc.player.getY());cold.addProperty("actual_client_z_after_fixture_teleport",mc.player.getZ());if(++districtIndex<4){requestCold(server);stage=2;}else{work=server.submit(()->{prepareArenaAndProbe();return true;});stage=8;}return;}
            if(stage==8&&done()){populationIndex=0;tideIndex=0;work=server.submit(()->{configurePopulation(server,POPULATIONS[populationIndex]);return server.getTickCount()+80;});stage=9;return;}
            if(stage==9&&done()){warmupUntil=(Integer)work.join();if(server.getTickCount()<warmupUntil)return;settling=new NativeChunkSettler.Session("v6_performance_population_warmup",6000);stage=10;return;}
            if(stage==10){require(!settling.failed(),settling.failure());if(!settling.ready())return;dimension.add("last_population_generation_settle",settling.report());settling=null;work=server.submit(()->startSample(POPULATIONS[populationIndex],tideIndex==0?TidePhase.CALM:TidePhase.SURGE));stage=11;return;}
            if(stage==11&&done()){var at=sample;if(at==null||!at.done)return;work=server.submit(()->{var outcome=summarize(at);sample=null;dimension.getAsJsonArray("player_loads").add(outcome);return outcome;});stage=12;return;}
            if(stage==12&&done()){
                JsonObject outcome=(JsonObject)work.join();if(!outcome.get("pickup_conservation").getAsBoolean()||!outcome.get("actual_auto_pickup_72_items_verified").getAsBoolean())throw new IllegalStateException("Actual packed72/pickup conservation or native auto-pickup failed");if(tideIndex==0){tideIndex=1;work=server.submit(()->startSample(POPULATIONS[populationIndex],TidePhase.SURGE));stage=11;write(mc);return;}
                if(++populationIndex<3){tideIndex=0;work=server.submit(()->{configurePopulation(server,POPULATIONS[populationIndex]);return server.getTickCount()+80;});stage=9;write(mc);return;}
                work=server.submit(()->BackpackItem.open(server.getPlayerList().getPlayer(realUuid),BackpackHarness.SLOT));stage=13;waitTicks=0;write(mc);return;
            }
            if(stage==13&&done()){require((Boolean)work.join(),"Native packed72 menu failed to open");if(mc.screen==null||!mc.screen.getClass().getSimpleName().equals("BackpackScreen"))return;if(++waitTicks<30)return;String name="performance-v"+(dimensionIndex==0?5:6)+"-packed72-eight-surge.png";captured=false;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->captured=true);var shot=new JsonObject();shot.addProperty("filename",name);shot.addProperty("dimension",level.dimension().location().toString());shot.addProperty("caption","Actual native packed72 menu after8-actor CPU-equivalent SURGE; only one real client, prepared arena");captures.add(shot);stage=14;return;}
            if(stage==14){if(!captured)return;require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(captures.get(captures.size()-1).getAsJsonObject().get("filename").getAsString())),"Actual native benchmark PNG missing");mc.player.closeContainer();work=server.submit(()->{clearMocks();clearDrops();level.getChunkSource().removeRegionTicket(PROBE,new ChunkPos(probe),0,new ChunkPos(probe));return true;});stage=15;return;}
            if(stage==15&&done()){if(++dimensionIndex<2){stage=1;work=CompletableFuture.completedFuture(true);write(mc);}else{passed=dimensions.size()==2&&captures.size()==2;for(var dim:dimensions){passed&=dim.getAsJsonObject().getAsJsonArray("fixed_cold_and_immediate_warm").size()==4&&dim.getAsJsonObject().getAsJsonArray("player_loads").size()==6;for(var load:dim.getAsJsonObject().getAsJsonArray("player_loads"))passed&=load.getAsJsonObject().get("p95_cpu_under50ms").getAsBoolean()&&load.getAsJsonObject().get("pickup_conservation").getAsBoolean()&&load.getAsJsonObject().get("actual_auto_pickup_72_items_verified").getAsBoolean();}finish(mc,passed,passed?"Actual V5/V6 fixed FULL and prepared1/4/8 CPU samples completed":"One or more real measured CPU budgets failed; see phase results");}return;}
        }catch(Throwable error){error.printStackTrace();finish(mc,false,error.toString());}
    }

    private static void configure(Minecraft mc){mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);mc.options.fov().set(70);mc.options.gamma().set(.5);mc.options.graphicsMode().set(GraphicsStatus.FANCY);mc.options.ambientOcclusion().set(true);mc.options.bobView().set(false);mc.getWindow().setWindowed(1280,720);mc.resizeDisplay();}
    private static void beginDimension(MinecraftServer server){level=server.getLevel(dimensionIndex==0?IslandWorld.VANILLA_WORLD:IslandWorld.TENSION_WORLD);require(level!=null&&level.getSeed()==SEED,"Actual comparison level/seed missing");require(level.getChunkSource().getGenerator() instanceof IslandChunkGenerator generator&&generator.terrainRevision()==(dimensionIndex==0?5:6),"Comparison generator revision changed");dimension=new JsonObject();dimension.addProperty("dimension",level.dimension().location().toString());dimension.addProperty("actual_seed",level.getSeed());dimension.addProperty("terrain_revision",dimensionIndex==0?5:6);dimension.addProperty("exploration_scope","Four fixed nativeFULL centres; immediateRAM-warm repeats; scriptedCreative128Y teleports40clientticks per district, not a human route");dimension.add("fixed_cold_and_immediate_warm",new JsonArray());dimension.add("player_loads",new JsonArray());dimensions.add(dimension);}
    private static void requestCold(MinecraftServer server){var p=DISTRICTS[districtIndex];work=server.submit(()->level.getChunkSource().getChunkNow(p.x,p.z)!=null);System.out.println("V6_PERFORMANCE_COLD dimension="+level.dimension().location()+" district="+districtIndex+" chunk="+p);}
    private static BlockPos arena(int index){var p=DISTRICTS[index];return new BlockPos(p.getMiddleBlockX(),200,p.getMiddleBlockZ());}
    private static void prepareArenaAndProbe(){
        for(int index=0;index<4;index++){var base=arena(index);require(level.getChunkSource().getChunkNow(base.getX()>>4,base.getZ()>>4)!=null,"Actual cold/warm FULL arena chunk unloaded before fixture preparation");for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=0;y<=4;y++){var pos=base.offset(x,y,z);var original=level.getBlockState(pos);require(!original.is(Interstice.LIGHT_SEA.get())&&!original.is(Interstice.HEAVY_BLOCK.get())&&!original.is(Blocks.BEDROCK),"Prepared arena would alter a protected ocean/shell cell");boolean solid=y==0||y==4||Math.abs(x)==4||Math.abs(z)==4;level.setBlock(pos,(solid?Blocks.STONE_BRICKS:Blocks.AIR).defaultBlockState(),3);}}
        var profile=GeometryProfiles.get(level);var p=DISTRICTS[0];int x=p.getMinBlockX()+1,z=p.getMinBlockZ()+1;int start=profile.lowerSeaTop()+2;probeEnd=(int)Math.floor(SeaSurface.cellMinimum(profile,x,z,true))-profile.clearance();probe=new BlockPos(x,start,z);int replaced=0,protectedCells=0;
        for(int y=start;y<=probeEnd;y++){var pos=probe.atY(y);var state=level.getBlockState(pos);if(!state.getFluidState().isEmpty()||state.is(Blocks.BEDROCK)){protectedCells++;continue;}if(!state.isAir()){replaced++;level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}}
        for(var position:DISTRICTS)if(!position.equals(new ChunkPos(probe)))level.getChunkSource().removeRegionTicket(PROBE,position,0,position);var fixture=new JsonObject();fixture.addProperty("scope","Prepared enclosed9x9x5 Survival arenas at200Y and one maximum-height actualFULL shelter airshaft; disposable terrain may be replaced. Arenas protect both oceans/bedrock; the probe preserves all fluid/bedrock cells");fixture.addProperty("retained_full_only_probe_tickets",1);fixture.addProperty("probe_x",x);fixture.addProperty("probe_z",z);fixture.addProperty("probe_start_y",start);fixture.addProperty("probe_end_y",probeEnd);fixture.addProperty("maximum_actual_scan_cells",probeEnd-start+1);fixture.addProperty("replaced_nonfluid_nonbedrock_probe_cells",replaced);fixture.addProperty("protected_fluid_or_bedrock_probe_cells",protectedCells);dimension.add("prepared_load_fixture",fixture);
    }
    private static void configurePopulation(MinecraftServer server,int count){clearMocks();clearDrops();actors.clear();var real=server.getPlayerList().getPlayer(realUuid);require(real!=null,"Actual native client player missing");actors.add(real);
        for(int id=1;id<count;id++){
            // Same supported public connection pattern as TestPlayers; no fake GameTestHelper or TCP claim.
            var uuid=UUID.randomUUID();var cookie=CommonListenerCookie.createInitial(new GameProfile(uuid,"perf-"+uuid.toString().substring(0,8)),false);var p=new ServerPlayer(server,level,cookie.gameProfile(),new ClientInformation("en_us",4,net.minecraft.world.entity.player.ChatVisiblity.FULL,true,0,net.minecraft.world.entity.player.Player.DEFAULT_MAIN_HAND,false,false));var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);mocks.add(new Mock(p,channel));actors.add(p);
        }
        for(int id=0;id<actors.size();id++){var p=actors.get(id);p.closeContainer();p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);var base=arena(id%4);p.teleportTo(level,base.getX()+.5+(id>=4?1.5:0),201,base.getZ()+.5,Set.of(),0,0);p.hasChangedDimension();equipPacked72(p);}
        require(server.getPlayerList().getPlayerCount()==count,"Population includes an unrelated actual player");System.out.println("V6_PERFORMANCE_POPULATION count="+count+" real=1 mock="+(count-1)+" dimension="+level.dimension().location());
    }
    private static void equipPacked72(ServerPlayer player){var pack=new ItemStack(ExpeditionEquipment.EXPEDITION_BACKPACK.get());var items=NonNullList.withSize(72,ItemStack.EMPTY);for(int slot=0;slot<72;slot++)items.set(slot,new ItemStack(Items.STONE,63));BackpackStorage.write(pack,items);BackpackStorage.mode(pack,BackpackStorage.MATCHING);var modules=BackpackStorage.readModules(pack);modules.set(0,new ItemStack(ExpeditionEquipment.MAGNET_MODULE_TIER1.get()));BackpackStorage.writeModules(pack,modules);BackpackHarness.set(player,pack);require(BackpackStorage.valid(pack)&&BackpackStorage.capacity(pack)==72,"Prepared packed72 invalid");}
    private static boolean startSample(int count,TidePhase tide){clearDrops();for(var actor:actors){actor.getInventory().clearContent();equipPacked72(actor);}TideManager.getSavedData(level.getServer()).setPhase(tide,24000);TideSync.broadcast(TideManager.getState(level.getServer()));var at=new Sample(count,tide);at.packInitially=packStoneCount();at.vanillaInitially=vanillaStoneCount();var player=actors.getFirst();
        for(int item=0;item<100;item++){double x=player.getX()+((item%10)-4.5)*.25,z=player.getZ()+((item/10)-4.5)*.25;var entity=new ItemEntity(level,x,player.getY()+.25,z,new ItemStack(Items.STONE));entity.setNoPickUpDelay();entity.setTarget(player.getUUID());require(level.addFreshEntity(entity),"Actual pickup ItemEntity failed to spawn");drops.add(entity);}sample=at;System.out.println("V6_PERFORMANCE_BEGIN dimension="+level.dimension().location()+" count="+count+" tide="+tide+" actualItemEntities=100");return true;}
    private static JsonObject summarize(Sample at){var out=new JsonObject();out.addProperty("population",at.population);out.addProperty("real_native_clients",1);out.addProperty("mock_cpu_players",at.population-1);out.addProperty("tide",at.tide.name());out.addProperty("actual_server_tick_samples",at.wall.size());out.addProperty("mock_tick_path","Public doTick at serverPost only to match the actual real client PlayerTick.Post count observed in that same tick; measured CPU cost, not network/latency equivalence");out.add("pre_post_wall_ms",distribution(at.wall));out.add("server_thread_cpu_ms",distribution(at.cpu));out.add("pre_pre_interval_ms",distribution(at.intervals));out.addProperty("p95_cpu_under50ms",at.cpu.size()==200&&percentile(at.cpu,.95)/1e6<=50);out.addProperty("p95_wall_under50ms",percentile(at.wall,.95)/1e6<=50);out.addProperty("real_player_post_events_per_tick_min",at.nativePostsMin);out.addProperty("real_player_post_events_per_tick_max",at.nativePostsMax);out.addProperty("native_fps_observations",at.fpsCount);if(at.fpsCount>0){out.addProperty("native_fps_min_observed",at.fpsMin);out.addProperty("native_fps_max_observed",at.fpsMax);out.addProperty("native_fps_mean_observed",at.fpsSum/(double)at.fpsCount);}out.addProperty("observed_jvm_heap_peak_bytes",at.heapPeak);out.addProperty("configured_heap_max_bytes",Runtime.getRuntime().maxMemory());out.addProperty("gc_collection_count_delta",gcCount()-at.gcCount);out.addProperty("gc_collection_time_ms_delta",gcMs()-at.gcMs);out.addProperty("wall_elapsed_ms",(System.nanoTime()-at.began)/1e6);out.addProperty("actual_high_column_probe_calls",at.probeCalls);out.addProperty("mock_outbound_objects_drained",at.outboundDrained);
        long finalPack=packStoneCount(),finalVanilla=vanillaStoneCount(),remaining=drops.stream().filter(e->!e.isRemoved()).mapToLong(e->e.getItem().is(Items.STONE)?e.getItem().getCount():0).sum();out.addProperty("actual_spawned_items",100);out.addProperty("actual_spawned_item_entities",100);out.addProperty("initial_pack_stones",at.packInitially);out.addProperty("final_pack_stones",finalPack);out.addProperty("final_vanilla_stones",finalVanilla);out.addProperty("remaining_fixture_item_stones",remaining);out.addProperty("pickup_conservation",at.packInitially+at.vanillaInitially+100==finalPack+finalVanilla+remaining);out.addProperty("actual_auto_pickup_into_pack",finalPack-at.packInitially);out.addProperty("actual_auto_pickup_72_items_verified",finalPack-at.packInitially==72&&remaining==0&&finalVanilla-at.vanillaInitially==28);var loaded=new JsonArray();for(var world:level.getServer().getAllLevels()){var stats=new JsonObject();stats.addProperty("dimension",world.dimension().location().toString());stats.addProperty("loaded_chunk_holders",world.getChunkSource().getLoadedChunksCount());stats.addProperty("players_in_level",world.players().size());loaded.add(stats);}out.add("all_actual_levels_at_sample_end",loaded);var playerRows=new JsonArray();for(var actor:actors){var row=new JsonObject();row.addProperty("uuid",actor.getUUID().toString());row.addProperty("native_client",actor.getUUID().equals(realUuid));row.addProperty("player_tick_count",actor.tickCount);row.addProperty("manual_do_tick_samples",at.manualTicks.getOrDefault(actor.getUUID(),0L));row.addProperty("native_advanced_samples",at.nativeTicks.getOrDefault(actor.getUUID(),0L));row.addProperty("requested_view_distance",actor.requestedViewDistance());row.addProperty("pack_capacity",BackpackStorage.capacity(BackpackHarness.get(actor)));row.addProperty("sheltered",ShelterDetector.isSheltered(level,actor));playerRows.add(row);}out.add("actual_players",playerRows);return out;}
    private static long packStoneCount(){long count=0;for(var actor:actors)for(var stack:BackpackStorage.read(BackpackHarness.get(actor)))if(stack.is(Items.STONE))count+=stack.getCount();return count;}
    private static long vanillaStoneCount(){long count=0;for(var actor:actors)count+=actor.getInventory().countItem(Items.STONE);return count;}
    private static void clearDrops(){for(var drop:drops)if(!drop.isRemoved())drop.discard();drops.clear();}
    private static void clearMocks(){for(var mock:mocks){mock.player.server.getPlayerList().remove(mock.player);mock.channel.finishAndReleaseAll();}mocks.clear();actors.clear();}
    private static String fingerprint(LevelChunk chunk){long hash=0xcbf29ce484222325L;for(int y=chunk.getMinBuildHeight();y<chunk.getMaxBuildHeight();y++)for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++){hash^=Block.getId(chunk.getBlockState(new BlockPos(x,y,z)));hash*=0x100000001b3L;}return Long.toUnsignedString(hash,16);}
    private static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionCount())).sum();}
    private static long gcMs(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionTime())).sum();}
    private static long percentile(List<Long> values,double q){if(values.isEmpty())return Long.MAX_VALUE;var sorted=new ArrayList<>(values);Collections.sort(sorted);return sorted.get(Math.max(0,(int)Math.ceil(sorted.size()*q)-1));}
    private static JsonObject distribution(List<Long> values){var out=new JsonObject();out.addProperty("samples",values.size());if(!values.isEmpty()){out.addProperty("p50",percentile(values,.50)/1e6);out.addProperty("p95",percentile(values,.95)/1e6);out.addProperty("p99",percentile(values,.99)/1e6);out.addProperty("max",Collections.max(values)/1e6);}return out;}
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void write(Minecraft mc){try{report.addProperty("stage",stage);report.addProperty("finished",finished);report.addProperty("passed",passed&&finished);report.addProperty("native_fps_last_observed",mc.getFps());Files.writeString(mc.gameDirectory.toPath().resolve("v6-performance-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){error.printStackTrace();}}
    private static void finish(Minecraft mc,boolean success,String reason){if(closing)return;closing=true;passed=success;sample=null;report.addProperty("reason",reason);var server=mc.getSingleplayerServer();if(server!=null){work=server.submit(()->{clearMocks();clearDrops();if(level!=null&&probe!=null)level.getChunkSource().removeRegionTicket(PROBE,new ChunkPos(probe),0,new ChunkPos(probe));for(var world:server.getAllLevels())for(var p:DISTRICTS)world.getChunkSource().removeRegionTicket(PROBE,p,0,p);return true;});settling=new NativeChunkSettler.Session("v6_performance_terminal_shutdown",12000);}write(mc);}
    private static void shutdown(Minecraft mc){if(mc.getSingleplayerServer()==null){finished=true;write(mc);mc.stop();return;}if(!done())return;if(settling.failed()){passed=false;report.addProperty("shutdown_error",settling.failure());write(mc);}if(!settling.ready())return;report.add("generation_quiescence_before_disconnect",settling.report());settling=null;var old=mc.getSingleplayerServer();disconnecting=true;try{if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());}finally{disconnecting=false;}require(old.isStopped(),"Own integrated server did not stop after vanilla disconnect");report.addProperty("own_integrated_server_stopped",true);finished=true;write(mc);mc.stop();}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
