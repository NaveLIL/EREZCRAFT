package pro.erez.interstice.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.ecology.CaveEcology;
import pro.erez.interstice.ecology.ClingweedBlock;
import pro.erez.interstice.ecology.ClingweedBlockEntity;
import pro.erez.interstice.ecology.ClingweedGas;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.rift.RiftSafety;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.RealmBiomes;
import pro.erez.interstice.worldgen.StoneVaults;

/** Disposable native galleries, real walking theft, then component-preserving recovery after a cold restart. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class LivingRealmVisualSmoke {
    private static final String MODE=System.getProperty("interstice.livingRealmSmoke","");
    private static final String WORLD="natural-living-realm-check";
    private static final int MAX_CANDIDATE_CHUNKS=24;
    private static boolean started,finished;
    private static boolean shutdownRequested,pendingPassed,finalCheckStarted;
    private static String pendingReason;
    private static long deadline;
    private static int stage,ticks,sceneIndex;
    private static CompletableFuture<?> work;
    private static BlockState expectedFloor,expectedRoof;
    private static volatile SettleSession settleSession;
    private static Field updatingHolders,pendingUnloads,pendingGeneration;
    private static JsonObject data=new JsonObject();
    private static List<Scene> scenes=List.of();
    private static final Map<String,JsonObject> sceneEvidence=new java.util.HashMap<>();
    private static final List<LevelChunk> inspected=new ArrayList<>();
    private record Scene(String id,ResourceKey<Biome> biome,BlockPos floor,
                         double x,double y,double z,double tx,double ty,double tz,boolean cave,int roofY) {}
    private record Walk(BlockPos plant,BlockPos start) {}
    private record ChunkScan(long tick,int holders,int generationRefs,int savePending,int notReady,
                             int generationTasks,int failedSaves,JsonArray examples) {
        boolean quiet() {return generationRefs==0&&savePending==0&&notReady==0&&generationTasks==0;}
        JsonObject json() {
            var out=new JsonObject();out.addProperty("server_tick",tick);out.addProperty("holders_checked",holders);
            out.addProperty("generation_ref_count",generationRefs);out.addProperty("save_futures_pending",savePending);
            out.addProperty("holders_not_ready_for_saving",notReady);out.addProperty("pending_generation_tasks",generationTasks);
            out.addProperty("save_futures_failed",failedSaves);out.add("non_quiet_examples",examples);return out;
        }
    }
    /** Mutable counters are touched only by actual ServerTickEvent.Post, then published to the client. */
    private static final class SettleSession {
        final String purpose;
        final int limit;
        long firstTick=-1,lastTick=-1;
        volatile int stableTicks;
        volatile boolean timedOut;
        volatile String error;
        volatile ChunkScan latest;
        SettleSession(String purpose,int limit){this.purpose=purpose;this.limit=limit;}
        void accept(ChunkScan scan) {
            if(firstTick<0)firstTick=scan.tick;
            if(lastTick!=scan.tick){stableTicks=scan.quiet()?stableTicks+1:0;lastTick=scan.tick;}
            latest=scan;
            if(scan.tick-firstTick>limit)timedOut=true;
            if(stableTicks==20||(scan.tick-firstTick)%20==0)
                System.out.println("LIVING_SETTLE purpose="+purpose+" stable_ticks="+stableTicks+" "+scan.json());
        }
        boolean ready(){return latest!=null&&latest.quiet()&&stableTicks>=20;}
        JsonObject json(){var result=latest==null?new JsonObject():latest.json();result.addProperty("stable_actual_server_ticks",stableTicks);result.addProperty("purpose",purpose);result.addProperty("timed_out",timedOut);return result;}
    }
    private LivingRealmVisualSmoke() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(MODE.isEmpty()||finished)return;
        var mc=Minecraft.getInstance();
        try {
            if(shutdownRequested){processShutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen) {
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
                mc.options.renderDistance().set(MODE.equals("create")?6:2);
                mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
                if(MODE.equals("create")) {
                    require(!Files.exists(world(mc).resolve("level.dat")),"Disposable living-realm save already exists");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Disposable living realm",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261006L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                } else {
                    require(MODE.equals("reload"),"Unknown living-realm smoke mode");
                    data=JsonParser.parseString(Files.readString(report(mc,"create"))).getAsJsonObject();
                    require(data.get("passed").getAsBoolean(),"Creation validation did not pass");
                    require(data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Reload requires another JVM");
                    mc.options.hideGui=false;mc.createWorldOpenFlows().openWorld(WORLD,()->{});
                }
                return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;
            require(System.nanoTime()<deadline,"Living-realm smoke deadline, stage "+stage);
            if(mc.player==null||mc.level==null||mc.screen!=null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==0) {
                if(MODE.equals("create")){mc.getConnection().sendCommand("interstice explore living");stage=1;ticks=0;}
                else {
                    work=server.submit(()->{checkReload(server.getPlayerList().getPlayer(uuid));return true;});stage=10;ticks=0;
                }
                return;
            }
            if(!mc.level.dimension().equals(IslandWorld.LIVING_WORLD))return;
            if(stage==1&&++ticks>=40) {
                work=server.submit(()->{scenes=findScenes(server.getLevel(IslandWorld.LIVING_WORLD));return true;});stage=2;
            } else if(stage==2&&done()) {
                sceneIndex=0;work=server.submit(()->{placeScene(server.getPlayerList().getPlayer(uuid),scenes.get(sceneIndex));return true;});stage=3;ticks=0;
            } else if(stage==3&&done()) {
                if(!sceneReady(mc,scenes.get(sceneIndex))){ticks=0;return;}
                if(++ticks<100)return;
                markReady(false);
                shot(mc,"living-"+scenes.get(sceneIndex).id()+"-dark.png");
                work=server.submit(()->{server.getPlayerList().getPlayer(uuid).addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));return true;});stage=4;ticks=0;
            } else if(stage==4&&done()) {
                if(!sceneReady(mc,scenes.get(sceneIndex))||!mc.player.hasEffect(MobEffects.NIGHT_VISION)){ticks=0;return;}
                if(++ticks<100)return;
                markReady(true);
                shot(mc,"living-"+scenes.get(sceneIndex).id()+"-clear.png");
                if(++sceneIndex<scenes.size()) {
                    work=server.submit(()->{placeScene(server.getPlayerList().getPlayer(uuid),scenes.get(sceneIndex));return true;});stage=3;ticks=0;
                } else {
                    work=server.submit(()->{preparePassage(server.getPlayerList().getPlayer(uuid));return true;});stage=5;ticks=0;
                }
            } else if(stage==5&&done()) {
                lowerDistances(mc);settleSession=new SettleSession("before_native_theft",2000);stage=19;ticks=0;
            } else if(stage==19) {
                var session=settleSession;
                require(session.error==null,"Pre-theft chunk inspection failed: "+session.error);
                require(!session.timedOut,"Pre-theft generation did not quiesce: "+session.json());
                if(!session.ready())return;
                data.add("settle_before_theft",session.json());settleSession=null;
                mc.options.hideGui=false;mc.options.keyUp.setDown(true);stage=6;ticks=0;
            } else if(stage==6) {
                require(++ticks<90,"Actual forward walking did not trigger plant theft");
                if(mc.player.getInventory().countItem(Items.IRON_PICKAXE)==3) {
                    mc.options.keyUp.setDown(false);
                    work=server.submit(()->{provePassage(server.getPlayerList().getPlayer(uuid));return true;});stage=7;ticks=0;
                }
            } else if(stage==7&&done()&&++ticks>=20) {
                shot(mc,"living-clingweed-passage.png");
                finish(mc,true,"Six naturally generated scenes captured; real survival passage stored one component item and saved the cooldown");
            } else if(stage==10&&done()&&++ticks>=15) {
                work=server.submit(()->{destroyPlant(server.getPlayerList().getPlayer(uuid));return true;});stage=11;ticks=0;
            } else if(stage==11&&done()&&++ticks>=10) {
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);
                    if(p.serverLevel().getGameTime()-data.get("gas_emitted_world_tick").getAsLong()<10)return false;
                    proveGas(p);return true;});stage=18;ticks=0;
            } else if(stage==18&&done()) {
                stage=Boolean.TRUE.equals(work.join())?12:11;ticks=0;
            } else if(stage==12&&done()) {
                shot(mc,"living-clingweed-defensive-gas.png");stage=13;ticks=0;
            } else if(stage==13&&++ticks>=20) {
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);
                    if(p.serverLevel().getGameTime()-data.get("gas_emitted_world_tick").getAsLong()<ClingweedGas.DURATION+5)return false;
                    require(clouds(p.serverLevel(),plant())==0,"Defensive gas did not expire");
                    require(totalTools(p)==4,"A saved tool was lost or duplicated during gas expiry");data.addProperty("gas_expired_naturally",true);return true;});stage=17;ticks=0;
            } else if(stage==17&&done()) {
                stage=Boolean.TRUE.equals(work.join())?14:13;ticks=0;
            } else if(stage==14&&done()) {
                if(mc.player.getInventory().countItem(Items.IRON_PICKAXE)<4)mc.options.keyUp.setDown(true);
                stage=15;ticks=0;
            } else if(stage==15) {
                require(++ticks<90,"Actual item pickup did not recover the stored component item");
                if(mc.player.getInventory().countItem(Items.IRON_PICKAXE)==4) {
                    mc.options.keyUp.setDown(false);
                    work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);require(inventoryTools(p)==4&&drops(p.serverLevel(),plant())==0,"Recovery did not return exactly four original tools");
                        require(p.serverLevel().getBlockState(plant()).isAir(),"Destroyed clingweed reappeared");data.addProperty("ordinary_item_entity_pickup",true);data.addProperty("stored_item_recovered_once_with_components",true);return true;});stage=16;ticks=0;
                }
            } else if(stage==16&&done()&&++ticks>=20) {
                shot(mc,"living-clingweed-recovered.png");finish(mc,true,"Cold restart retained item components and player cooldown; survival destruction emitted finite gas and the exact stored item was picked up once");
            }
        } catch(Throwable error) {error.printStackTrace();finish(mc,false,error.toString());}
    }

    /** No waiting on the server thread: a bounded extra pump, followed by read-only holder inspection. */
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        var session=settleSession;
        if(MODE.isEmpty()||session==null)return;
        try {
            for(var level:event.getServer().getAllLevels()) {
                long budget=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2);
                for(int calls=0;calls<32&&System.nanoTime()<budget;calls++)if(!level.getChunkSource().pollTask())break;
            }
            session.accept(inspectChunks(event.getServer()));
        } catch(Throwable error) {session.error=error.toString();error.printStackTrace();}
    }

    private static ChunkScan inspectChunks(MinecraftServer server) throws ReflectiveOperationException {
        if(updatingHolders==null) {
            updatingHolders=field("updatingChunkMap");pendingUnloads=field("pendingUnloads");pendingGeneration=field("pendingGenerationTasks");
        }
        int holders=0,refs=0,saves=0,notReady=0,tasks=0,failed=0;var examples=new JsonArray();
        for(var level:server.getAllLevels()) {
            var map=level.getChunkSource().chunkMap;
            Set<ChunkHolder> all=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Object value:((Map<?,?>)updatingHolders.get(map)).values())all.add((ChunkHolder)value);
            for(Object value:((Map<?,?>)pendingUnloads.get(map)).values())all.add((ChunkHolder)value);
            tasks+=((List<?>)pendingGeneration.get(map)).size();holders+=all.size();
            for(var holder:all) {
                int generation=holder.getGenerationRefCount();boolean saveDone=holder.getSaveSyncFuture().isDone();
                refs+=generation;if(!saveDone)saves++;if(holder.getSaveSyncFuture().isCompletedExceptionally())failed++;
                if(!holder.isReadyForSaving()) {
                    notReady++;
                    if(examples.size()<10) {
                        var item=new JsonObject();item.addProperty("dimension",level.dimension().location().toString());
                        item.addProperty("chunk",holder.getPos().toString());item.addProperty("generation_refs",generation);item.addProperty("save_done",saveDone);
                        if(holder.getLatestChunk()!=null)item.addProperty("status",holder.getLatestChunk().getPersistedStatus().toString());examples.add(item);
                    }
                }
            }
        }
        return new ChunkScan(server.overworld().getGameTime(),holders,refs,saves,notReady,tasks,failed,examples);
    }

    private static Field field(String name) throws ReflectiveOperationException {
        var field=ChunkMap.class.getDeclaredField(name);field.setAccessible(true);return field;
    }

    private static void lowerDistances(Minecraft mc) {
        mc.options.keyUp.setDown(false);mc.options.keyUse.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyJump.setDown(false);
        // 1.21.1's public simulation option has a minimum of five; invalid four resets it to twelve.
        mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.broadcastOptions();
        data.addProperty("settling_render_distance_chunks",mc.options.renderDistance().get());
        data.addProperty("settling_simulation_distance_chunks",mc.options.simulationDistance().get());
    }

    private static List<Scene> findScenes(ServerLevel level) {
        require(level!=null,"Living dimension missing");
        var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();
        require(generator.terrainRevision()==4,"Smoke entered the historical/draft terrain revision");
        var profile=generator.geometry();var random=level.getChunkSource().randomState();var sampler=random.sampler();
        List<ResourceKey<Biome>> biomes=List.of(RealmBiomes.ASH_ISLANDS,RealmBiomes.PALE_GARDENS,RealmBiomes.STONE_VAULTS);
        String[] names={"ash","garden","vault"};Scene[] surface=new Scene[3],caves=new Scene[3];int[] generated=new int[3];
        Set<Long> attempted=new HashSet<>();
        for(int radius=0;radius<=32;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            require(System.nanoTime()<deadline,"Native biome gallery search deadline");
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int x=dx*64,z=dz*64;
            var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(profile.minLand()+8),QuartPos.fromBlock(z),sampler);
            int kind=-1;for(int i=0;i<3;i++)if(biome.is(biomes.get(i)))kind=i;
            if(kind<0||(surface[kind]!=null&&caves[kind]!=null)||generated[kind]>=MAX_CANDIDATE_CHUNKS)continue;
            var terrain=generator.terrainColumn(random,x,z);var weights=terrain.weights();
            if(kind==0) {
                int highest=-1,depth=0;
                for(int y=profile.maxLand();y>=profile.minLand();y--)if(terrain.density(y)>0){highest=y;break;}
                if(highest>=0)for(int y=highest;y>=profile.minLand()&&terrain.density(y)>0;y--)depth++;
                // A coast intentionally lowers/thins suspended stone. The ash showcase must instead
                // use an interior mass, not the first small coastal remnant in the search ring.
                if(depth<28||weights.ash()<.9)continue;
            } else if(kind==1&&weights.gardens()<.9)continue;
            else if(kind==2&&(weights.vaults()<.9||terrain.density(160)<=0))continue;
            int cx=x>>4,cz=z>>4;long packed=net.minecraft.world.level.ChunkPos.asLong(cx,cz);
            if(!attempted.add(packed))continue;
            System.out.println("LIVING_GALLERY_CANDIDATE biome="+names[kind]+" chunk="+cx+","+cz);
            var chunk=level.getChunk(cx,cz);inspected.add(chunk);generated[kind]++;
            if(surface[kind]==null)surface[kind]=findSurface(level,chunk,profile,names[kind],biomes.get(kind));
            if(caves[kind]==null)caves[kind]=findCave(level,chunk,profile,names[kind],biomes.get(kind));
            boolean all=true;for(int i=0;i<3;i++)all&=surface[i]!=null&&caves[i]!=null;
            if(all) {
                List<Scene> found=new ArrayList<>();JsonArray manifest=new JsonArray();
                for(int i=0;i<3;i++) {found.add(surface[i]);found.add(caves[i]);manifest.add(sceneJson(surface[i]));manifest.add(sceneJson(caves[i]));}
                data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("seed",level.getSeed());data.addProperty("terrain_revision",generator.terrainRevision());
                data.addProperty("natural_scene_count",6);data.addProperty("search_radius_limit_blocks",2048);data.addProperty("candidate_chunks_generated",inspected.size());data.add("scenes",manifest);
                data.addProperty("surface_camera_render_distance_chunks",6);data.addProperty("stable_client_ticks_required_per_image",100);
                data.addProperty("scene_scope","Natural Creative terrain/cave inspection in a disposable world; night vision only for clear comparison images. Prepared Survival inventory for native plant interactions.");
                return List.copyOf(found);
            }
        }
        throw new IllegalStateException("Missing native natural gallery scenes: ash="+(surface[0]!=null)+"/"+(caves[0]!=null)+", garden="+(surface[1]!=null)+"/"+(caves[1]!=null)+", vault="+(surface[2]!=null)+"/"+(caves[2]!=null));
    }

    private static Scene findSurface(ServerLevel level,LevelChunk chunk,GeometryProfile profile,String name,ResourceKey<Biome> biome) {
        int baseX=chunk.getPos().getMinBlockX(),baseZ=chunk.getPos().getMinBlockZ();
        for(int[] offset:new int[][]{{7,7},{3,3},{12,12},{3,12},{12,3}}) {
            int x=baseX+offset[0],z=baseZ+offset[1];
            for(int y=profile.maxLand();y>=profile.lowerSeaTop();y--) {
                var ground=new BlockPos(x,y,z);
                if(!StoneVaults.isGround(chunk.getBlockState(ground)))continue;
                if(!level.getBiome(ground).is(biome))break;
                if(name.equals("vault")&&y<160)break;
                if(name.equals("ash")) {
                    var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
                    var column=generator.terrainColumn(random,x,z);int depth=0;
                    for(int at=y;at>=profile.minLand()&&column.density(at)>0;at--)depth++;
                    if(depth<30)break;
                    int middle=y-depth/2;boolean broad=true;
                    for(int[] sample:new int[][]{{-20,0},{20,0},{0,-20},{0,20}})
                        broad&=generator.terrainColumn(random,x+sample[0],z+sample[1]).density(middle)>0;
                    if(!broad)break;
                    var evidence=new JsonObject();evidence.addProperty("uncarved_mass_depth_blocks",depth);evidence.addProperty("solid_cross_section_width_at_least_blocks",40);
                    evidence.addProperty("ash_core_weight",column.weights().ash());sceneEvidence.put(name+"-surface",evidence);
                }
                // Diagonal 72-block offsets exceeded the 96-block fog end at render distance six.
                // Keep the actual subject within 80 blocks, and show several real surrounding chunks.
                // Oblique view above the plate exposes its area and edge thickness. A low side-on
                // view can make a genuine wide mass look like a thin diagonal strip.
                double normalY=name.equals("ash")?Math.min(y+24,profile.upperMinimum()-18):Math.min(y+13,profile.upperMinimum()-22);
                double[] heights=new double[]{normalY};
                for(double cameraY:heights)for(int distance:new int[]{40,48,56})for(int[] direction:new int[][]{{1,-1},{-1,-1},{1,1},{-1,1},{1,0},{0,-1}}) {
                    double px=x+.5+direction[0]*distance,pz=z+.5+direction[1]*distance;
                    if(!cameraClear(level,profile,px,cameraY,pz))continue;
                    return new Scene(name+"-surface",biome,ground,px,cameraY,pz,x+.5,y+(name.equals("ash")?-3:3),z+.5,false,-1);
                }
                break;
            }
        }
        return null;
    }

    private static Scene findCave(ServerLevel level,LevelChunk chunk,GeometryProfile profile,String name,ResourceKey<Biome> biome) {
        Scene best=null;int bestView=0;
        for(int ox=2;ox<14;ox+=2)for(int oz=2;oz<14;oz+=2)for(int y=profile.minY()+6;y<profile.maxLand()-5;y++) {
            var feet=new BlockPos(chunk.getPos().getMinBlockX()+ox,y,chunk.getPos().getMinBlockZ()+oz);
            if(!chunk.getBlockState(feet).isAir()||!chunk.getBlockState(feet.above()).isAir()||!StoneVaults.isGround(chunk.getBlockState(feet.below()))||!level.getBiome(feet).is(biome))continue;
            if(chunk.getBlockState(feet.below()).is(Interstice.ABYSSAL_TURF.get()))continue;
            int roof=roof(chunk,feet,profile);if(roof<y+3)continue;
            int cover=0,walls=0;
            for(int rx=-1;rx<=1;rx++)for(int rz=-1;rz<=1;rz++)if(levelRoof(level,profile,feet.offset(rx,0,rz))>=0)cover++;
            if(cover<7)continue;
            for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}) {
                for(int distance=1;distance<=24;distance++) {
                    var p=feet.offset(direction[0]*distance,1,direction[1]*distance);var state=level.getBlockState(p);
                    if(!state.getFluidState().isEmpty())break;
                    if(StoneVaults.isGround(state)){walls++;break;}
                }
            }
            if(walls<5)continue;
            for(Direction direction:Direction.Plane.HORIZONTAL) {
                int view=0;boolean backdrop=false;for(int distance=1;distance<=24;distance++) {
                    var p=feet.relative(direction,distance).above();
                    var state=level.getBlockState(p);
                    if(!state.getCollisionShape(level,p).isEmpty()||!state.getFluidState().isEmpty()) {backdrop=StoneVaults.isGround(state);break;}
                    view=distance;
                }
                if(!backdrop||view<4||view<=bestView)continue;
                double px=feet.getX()+.5,py=feet.getY()+.01,pz=feet.getZ()+.5;
                if(!cameraClear(level,profile,px,py,pz))continue;
                var target=feet.relative(direction,view);
                best=new Scene(name+"-cave",biome,feet.below(),px,py,pz,target.getX()+.5,y+Math.min(3,(roof-y)*.4),target.getZ()+.5,true,roof);bestView=view;
                var evidence=new JsonObject();evidence.addProperty("roof_columns_covered_of_nine",cover);evidence.addProperty("rock_bounded_horizontal_rays_of_eight",walls);evidence.addProperty("rock_backdrop_distance_blocks",view+1);
                evidence.addProperty("floor_is_exterior_turf",false);sceneEvidence.put(name+"-cave",evidence);
            }
        }
        return best;
    }

    private static int roof(LevelChunk chunk,BlockPos feet,GeometryProfile profile) {
        for(int y=feet.getY()+2;y<Math.min(profile.maxLand(),feet.getY()+65);y++) {
            var state=chunk.getBlockState(feet.atY(y));
            if(StoneVaults.isGround(state))return y;
            if(!state.getFluidState().isEmpty())return -1;
        }
        return -1;
    }
    private static int levelRoof(ServerLevel level,GeometryProfile profile,BlockPos feet) {
        for(int y=feet.getY()+2;y<Math.min(profile.maxLand(),feet.getY()+25);y++) {
            var state=level.getBlockState(feet.atY(y));if(StoneVaults.isGround(state))return y;
            if(!state.getFluidState().isEmpty())return -1;
        }
        return -1;
    }

    private static boolean cameraClear(ServerLevel level,GeometryProfile profile,double x,double y,double z) {
        // Enclosed dry cave cameras may sit below the lower ocean's surface.
        // Actual block/fluid/collision checks distinguish a cave from submerged space.
        if(y<profile.minY()+6||y+2>=SeaSurface.cellMinimum(profile,(int)Math.floor(x),(int)Math.floor(z),true)-2)return false;
        var box=new AABB(x-.3,y,z-.3,x+.3,y+1.9,z+.3);
        if(!level.noCollision(box))return false;
        for(int dy=0;dy<2;dy++)if(!level.getFluidState(BlockPos.containing(x,y+dy,z)).isEmpty())return false;
        return true;
    }

    private static void placeScene(ServerPlayer player,Scene scene) {
        var level=player.server.getLevel(IslandWorld.LIVING_WORLD);
        player.setGameMode(GameType.CREATIVE);player.removeEffect(MobEffects.NIGHT_VISION);
        prefetch(level,scene.floor.getX()>>4,scene.floor.getZ()>>4);
        prefetch(level,((int)Math.floor(scene.x))>>4,((int)Math.floor(scene.z))>>4);
        expectedFloor=level.getBlockState(scene.floor);
        expectedRoof=scene.cave?level.getBlockState(scene.floor.atY(scene.roofY)):null;
        require(StoneVaults.isGround(expectedFloor),"Natural subject floor disappeared before capture: "+scene.id);
        require(cameraClear(level,((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry(),scene.x,scene.y,scene.z),"Scene camera became obstructed: "+scene.id);
        camera(player,level,scene.x,scene.y,scene.z,scene.tx,scene.ty,scene.tz,true);
    }

    /** Full real chunks around both ends, bounded to two 3x3 neighborhoods per scene. */
    private static void prefetch(ServerLevel level,int cx,int cz) {
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            require(System.nanoTime()<deadline,"Scene neighborhood prefetch deadline");level.getChunk(cx+dx,cz+dz);
        }
    }

    private static boolean sceneReady(Minecraft mc,Scene scene) {
        double dx=mc.player.getX()-scene.x,dy=mc.player.getY()-scene.y,dz=mc.player.getZ()-scene.z;
        if(dx*dx+dy*dy+dz*dz>.1)return false;
        int cameraX=((int)Math.floor(scene.x))>>4,cameraZ=((int)Math.floor(scene.z))>>4;
        int targetX=scene.floor.getX()>>4,targetZ=scene.floor.getZ()>>4;
        for(int ox=-1;ox<=1;ox++)for(int oz=-1;oz<=1;oz++) {
            if(!mc.level.hasChunkAt(new BlockPos((cameraX+ox)*16,scene.floor.getY(),(cameraZ+oz)*16))
                    ||!mc.level.hasChunkAt(new BlockPos((targetX+ox)*16,scene.floor.getY(),(targetZ+oz)*16)))return false;
        }
        if(!mc.level.getBlockState(scene.floor).equals(expectedFloor))return false;
        return !scene.cave||mc.level.getBlockState(scene.floor.atY(scene.roofY)).equals(expectedRoof);
    }

    private static void markReady(boolean clear) {
        var scene=data.getAsJsonArray("scenes").get(sceneIndex).getAsJsonObject();
        scene.addProperty(clear?"clear_capture_client_ready":"dark_capture_client_ready",true);
        scene.addProperty("target_and_camera_3x3_chunks_present",true);scene.addProperty("client_target_floor_matches_server",true);
        if(scenes.get(sceneIndex).cave)scene.addProperty("client_roof_matches_server",true);
    }

    private static JsonObject sceneJson(Scene scene) {
        JsonObject o=new JsonObject();o.addProperty("id",scene.id);o.addProperty("biome",scene.biome.location().toString());o.addProperty("naturally_generated",true);
        o.addProperty("cave",scene.cave);o.addProperty("floor_x",scene.floor.getX());o.addProperty("floor_y",scene.floor.getY());o.addProperty("floor_z",scene.floor.getZ());o.addProperty("roof_y",scene.roofY);
        o.addProperty("camera_x",scene.x);o.addProperty("camera_y",scene.y);o.addProperty("camera_z",scene.z);
        var evidence=sceneEvidence.get(scene.id);if(evidence!=null)o.add("selection_evidence",evidence.deepCopy());return o;
    }

    private static Walk walk(ServerLevel level,BlockPos plant) {
        if(!StoneVaults.isGround(level.getBlockState(plant.below()))||!RiftSafety.standing(level,plant)
                ||!level.getBlockState(plant.above()).isAir())return null;
        for(Direction direction:Direction.Plane.HORIZONTAL) {
            var start=plant.relative(direction,2);var middle=plant.relative(direction);
            if(!RiftSafety.standing(level,start)||!RiftSafety.standing(level,middle))continue;
            if(!level.getBlockState(start).isAir()||!level.getBlockState(start.above()).isAir()||!level.getBlockState(middle).isAir()||!level.getBlockState(middle.above()).isAir())continue;
            return new Walk(plant,start);
        }
        return null;
    }

    private static void preparePassage(ServerPlayer player) {
        var level=player.serverLevel();Walk route=null;
        int minimum=((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry().minY()+6;
        outer:for(var chunk:inspected)for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)for(int y=minimum;y<198;y++) {
            var pos=new BlockPos(x,y,z);
            if(chunk.getBlockState(pos).is(CaveEcology.CLINGWEED.get())&&level.getBlockEntity(pos) instanceof ClingweedBlockEntity be&&be.storedItems().isEmpty()) {
                route=walk(level,pos);if(route!=null){data.addProperty("clingweed_naturally_generated",true);break outer;}
            }
        }
        if(route==null) {
            outer:for(var chunk:inspected)for(int x=chunk.getPos().getMinBlockX()+1;x<chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ()+1;z<chunk.getPos().getMaxBlockZ();z++)for(int y=minimum;y<198;y++) {
                var pos=new BlockPos(x,y,z);if(!chunk.getBlockState(pos).isAir()||roof(chunk,pos,((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry())<0)continue;
                boolean support=false;for(Direction d:Direction.Plane.HORIZONTAL)support|=StoneVaults.isGround(level.getBlockState(pos.relative(d)));
                if(!support)continue;route=walk(level,pos);
                if(route!=null) {level.setBlock(pos,CaveEcology.CLINGWEED.get().defaultBlockState(),3);data.addProperty("clingweed_naturally_generated",false);data.addProperty("prepared_plant_fixture","One clingweed cell placed beside actual cave rock; natural terrain unchanged; inventory prepared for deterministic passage proof");break outer;}
            }
        }
        require(route!=null,"No naturally supported and walkable clingweed fixture");
        position(data,"plant",route.plant);position(data,"walk_start",route.start);
        require(clouds(level,route.plant)==0,"Plant fixture already contains gas");
        player.server.setDifficulty(Difficulty.NORMAL,true);player.setGameMode(GameType.SURVIVAL);player.removeAllEffects();player.getInventory().clearContent();
        for(int slot=0;slot<4;slot++)player.getInventory().items.set(slot,tool());
        // The later hand break must not legitimately damage one of the component-comparison tools.
        player.getInventory().selected=8;
        player.getInventory().setChanged();player.containerMenu.broadcastChanges();player.setHealth(20);player.getFoodData().setFoodLevel(20);
        camera(player,level,route.start.getX()+.5,route.start.getY()+.01,route.start.getZ()+.5,route.plant.getX()+.5,route.plant.getY()+player.getEyeHeight(),route.plant.getZ()+.5,false);
        data.addProperty("fixture_tool_count_before",4);data.addProperty("health_before_passage",player.getHealth());data.addProperty("fixture_player_positioned_at_cave_approach",true);
    }

    private static void provePassage(ServerPlayer player) {
        var level=player.serverLevel();BlockPos captured=null;
        for(int dy=0;dy<=1;dy++) {
            var pos=plant().above(dy);
            if(level.getBlockEntity(pos) instanceof ClingweedBlockEntity be&&!be.storedItems().isEmpty()) {
                require(captured==null,"Passage stole into multiple plant cells");
                require(be.storedItems().size()==1&&be.storedItems().get(0).getCount()==1&&ItemStack.isSameItemSameComponents(tool(),be.storedItems().get(0)),"Stash did not preserve exactly one damaged named tool");captured=pos;
            }
        }
        require(captured!=null&&inventoryTools(player)==3,"Native entityInside passage did not steal one tool");position(data,"plant",captured);
        require(player.getHealth()==data.get("health_before_passage").getAsFloat()&&player.getActiveEffects().isEmpty()&&clouds(level,captured)==0,"Walking through clingweed damaged or poisoned the visitor");
        long until=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong(ClingweedBlock.COOLDOWN_TAG),now=player.server.overworld().getGameTime();
        require(until>now&&until-now<=ClingweedBlock.COOLDOWN_TICKS,"Native passage did not set a bounded persistent cooldown");
        data.addProperty("ordinary_forward_key_passage",true);data.addProperty("theft_preserved_name_damage_custom_data",true);data.addProperty("passage_health_unchanged",true);data.addProperty("passage_no_gas",true);
        data.addProperty("cooldown_saved_until",until);data.addProperty("cooldown_remaining_at_save",until-now);
        var start=position("walk_start");camera(player,level,start.getX()+.5,start.getY()+.01,start.getZ()+.5,captured.getX()+.5,captured.getY()+player.getEyeHeight(),captured.getZ()+.5,false);
    }

    private static void checkReload(ServerPlayer player) {
        require(player!=null&&!player.isCreative()&&player.serverLevel().dimension().equals(IslandWorld.LIVING_WORLD),"Reload did not restore the Survival player in the living realm");
        require(player.server.getWorldData().getDifficulty()==Difficulty.NORMAL,"Normal difficulty was not preserved");
        var level=player.serverLevel();level.getChunk(plant().getX()>>4,plant().getZ()>>4);
        require(level.getBlockEntity(plant()) instanceof ClingweedBlockEntity,"Stored plant block entity was not saved");
        var stored=((ClingweedBlockEntity)level.getBlockEntity(plant())).storedItems();
        require(stored.size()==1&&stored.get(0).getCount()==1&&ItemStack.isSameItemSameComponents(tool(),stored.get(0)),"Cold restart lost stored tool components");
        long until=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong(ClingweedBlock.COOLDOWN_TAG);
        require(until==data.get("cooldown_saved_until").getAsLong()&&until>player.server.overworld().getGameTime(),"Player's cooldown did not survive the cold restart");
        require(inventoryTools(player)==3&&clouds(level,plant())==0,"Reload duplicated inventory or persisted unscheduled gas");
        data.addProperty("reload_pid",ProcessHandle.current().pid());data.addProperty("cold_restart_stash_components_preserved",true);data.addProperty("cold_restart_player_cooldown_preserved",true);data.addProperty("cooldown_remaining_after_reload",until-player.server.overworld().getGameTime());
    }

    private static void destroyPlant(ServerPlayer player) {
        var level=player.serverLevel();
        require(player.gameMode.destroyBlock(plant()),"Actual Survival game mode refused to destroy the plant");
        require(level.getBlockState(plant()).isAir()&&clouds(level,plant())==1,"Actual destruction did not create one defensive cloud");
        require(drops(level,plant())==1&&totalTools(player)==4,"Destruction lost or duplicated the stored component item");
        data.addProperty("native_survival_destroy_block",true);data.addProperty("one_defensive_cloud_created",true);data.addProperty("one_exact_saved_tool_dropped",true);data.addProperty("gas_emitted_world_tick",level.getGameTime());
        // Look towards the broken wall while remaining in the actual cloud's radius.
        var start=position("walk_start");camera(player,level,start.getX()+.5,start.getY()+.01,start.getZ()+.5,plant().getX()+.5,plant().getY()+player.getEyeHeight(),plant().getZ()+.5,false);
    }

    private static void proveGas(ServerPlayer player) {
        require(player.hasEffect(MobEffects.POISON)&&player.hasEffect(MobEffects.WEAKNESS)&&player.hasEffect(MobEffects.CONFUSION),"Actual local gas did not apply poison, weakness and nausea");
        require(totalTools(player)==4,"Cloud exposure duplicated or erased the recovered stash item");data.addProperty("native_defensive_gas_effects",true);
    }

    private static ItemStack tool() {
        var tool=new ItemStack(Items.IRON_PICKAXE);tool.setDamageValue(27);tool.set(DataComponents.CUSTOM_NAME,Component.literal("Living realm recovered tool"));
        var tag=new CompoundTag();tag.putInt("living_smoke_payload",20261006);tool.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return tool;
    }
    private static int inventoryTools(ServerPlayer player) {return player.getInventory().items.stream().filter(s->ItemStack.isSameItemSameComponents(tool(),s)).mapToInt(ItemStack::getCount).sum();}
    private static int drops(ServerLevel level,BlockPos plant) {return level.getEntitiesOfClass(ItemEntity.class,new AABB(plant).inflate(6),e->ItemStack.isSameItemSameComponents(tool(),e.getItem())).stream().mapToInt(e->e.getItem().getCount()).sum();}
    private static int totalTools(ServerPlayer player) {return inventoryTools(player)+drops(player.serverLevel(),plant());}
    private static int clouds(ServerLevel level,BlockPos plant) {return level.getEntitiesOfClass(AreaEffectCloud.class,new AABB(plant).inflate(8),c->c.isAlive()&&c.getTags().contains(ClingweedGas.ENTITY_TAG)).size();}
    private static void position(JsonObject object,String key,BlockPos p) {object.addProperty(key+"_x",p.getX());object.addProperty(key+"_y",p.getY());object.addProperty(key+"_z",p.getZ());}
    private static BlockPos position(String key) {return new BlockPos(data.get(key+"_x").getAsInt(),data.get(key+"_y").getAsInt(),data.get(key+"_z").getAsInt());}
    private static BlockPos plant() {return position("plant");}
    private static void camera(ServerPlayer p,ServerLevel level,double x,double y,double z,double tx,double ty,double tz,boolean fly) {
        double dx=tx-x,dz=tz-z,dy=ty-(y+p.getEyeHeight());
        p.teleportTo(level,x,y,z,Set.of(),(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));p.setDeltaMovement(0,0,0);p.resetFallDistance();p.getAbilities().flying=fly;p.onUpdateAbilities();
    }
    private static boolean done() {if(!work.isDone())return false;work.join();return true;}
    private static java.nio.file.Path world(Minecraft mc) {return mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);}
    private static java.nio.file.Path report(Minecraft mc,String mode) {return mc.gameDirectory.toPath().resolve("living-"+mode+"-validation.json");}
    private static void shot(Minecraft mc,String name) {Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),msg->System.out.println("LIVING_SCREENSHOT "+name));}
    private static void require(boolean yes,String reason) {if(!yes)throw new IllegalStateException(reason);}
    private static void finish(Minecraft mc,boolean passed,String why) {
        if(finished)return;
        if(shutdownRequested) {
            if(!passed){pendingPassed=false;pendingReason=why;writeResult(mc,false,why,true);}
            return;
        }
        shutdownRequested=true;pendingPassed=passed;pendingReason=why;finalCheckStarted=false;
        lowerDistances(mc);
        if(mc.getSingleplayerServer()==null) {finished=true;writeResult(mc,passed,why,false);mc.stop();return;}
        settleSession=new SettleSession("before_terminal_shutdown",160);
        data.addProperty("terminal_stage",stage);
        if(!passed)writeResult(mc,false,why,true);
    }

    private static void processShutdown(Minecraft mc) {
        var server=mc.getSingleplayerServer();var session=settleSession;
        if(server==null){finished=true;writeResult(mc,pendingPassed,pendingReason,false);mc.stop();return;}
        if(session.error!=null||session.timedOut) {
            if(pendingPassed||!data.has("quiescence_failure")) {
                pendingPassed=false;
                pendingReason=session.error!=null?"Read-only shutdown inspection failed: "+session.error
                        :"Chunk generation did not quiesce within 160 server ticks; normal pumping continues until safe shutdown";
                data.addProperty("quiescence_failure",pendingReason);data.add("settle_terminal",session.json());
                writeResult(mc,false,pendingReason,true);
            }
            // Never enter the known vanilla shutdown spin with unfinished generation references.
            // A failed bounded check remains failed even if later normal ticks make shutdown safe.
        }
        if(!session.ready())return;
        if(!finalCheckStarted) {
            finalCheckStarted=true;
            work=server.submit(()->{
                data.add("settle_terminal",session.json());
                require(session.latest.failedSaves==0,"A chunk save dependency completed exceptionally");
                if(MODE.equals("create")&&pendingPassed) {
                    var player=server.getPlayerList().getPlayer(mc.player.getUUID());
                    long until=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong(ClingweedBlock.COOLDOWN_TAG);
                    long remaining=until-server.overworld().getGameTime();
                    require(remaining>0,"The theft cooldown expired before safe final saving");
                    data.addProperty("cooldown_remaining_at_final_shutdown_check",remaining);
                    require(inventoryTools(player)==3,"Terminal settling changed the passage inventory");
                }
                return true;
            });return;
        }
        if(!work.isDone())return;
        try {work.join();}catch(Throwable error){pendingPassed=false;pendingReason=error.toString();}
        data.addProperty("clean_generation_before_mc_stop",true);finished=true;settleSession=null;
        writeResult(mc,pendingPassed,pendingReason,false);mc.stop();
    }

    private static void writeResult(Minecraft mc,boolean passed,String why,boolean shutdownPending) {
        data.addProperty("passed",passed);data.addProperty("mode",MODE);data.addProperty("stage",stage);data.addProperty("reason",why);data.addProperty("shutdown_pending",shutdownPending);
        try {Files.writeString(report(mc,MODE),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception error){error.printStackTrace();}
        System.out.println("LIVING_VALIDATION "+data);
    }
}
