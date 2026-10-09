package pro.erez.interstice.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.RealmBiomes;
import pro.erez.interstice.worldgen.StoneVaults;

/** Natural FULL-chunk gallery in an explicitly disposable Creative save, followed by a separate JVM restart. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class VanillaTerrainVisualSmoke {
    private static final String MODE=System.getProperty("interstice.vanillaTerrainSmoke","");
    private static final String WORLD="natural-vanilla-v5-gallery";
    private static final long SEED=20261006L;
    private static final GeometryProfile PROFILE=GeometryProfile.TALL;
    private static boolean started,finished,closing,passed;
    private static int stage,ticks,index;
    private static long deadline,poseTick;
    private static String reason;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static JsonObject data=new JsonObject();
    private static List<Scene> scenes=List.of();
    private record Candidate(String id,ResourceKey<Biome> biome,BlockPos ground,int score) {}
    private record ForestMeasure(int dryColumns,int canopyColumns,int flora,int hazards,int leaves,int biomeColumns){
        double coverage(){return dryColumns==0?0:canopyColumns/(double)dryColumns;}
        JsonObject json(){var o=new JsonObject();o.addProperty("dry_soil_columns",dryColumns);o.addProperty("canopy_columns",canopyColumns);o.addProperty("canopy_fraction",coverage());o.addProperty("ground_flora_blocks",flora);o.addProperty("poisonous_understory_blocks",hazards);o.addProperty("leaf_blocks",leaves);o.addProperty("expected_biome_columns",biomeColumns);return o;}
    }
    private record Scene(String id,BlockPos target,double x,double y,double z,double tx,double ty,double tz) {}
    private VanillaTerrainVisualSmoke() {}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){var s=settling;if(!MODE.isEmpty()&&s!=null)s.tick(event.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){shutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen){
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(20);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(5);
                mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
                if(MODE.equals("create")){
                    require(!Files.exists(save(mc).resolve("level.dat")),"Disposable V5 gallery already exists");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Natural V5 terrain gallery",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(SEED,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                }else{
                    require(MODE.equals("reload"),"Unknown V5 gallery mode");
                    data=JsonParser.parseString(Files.readString(report(mc,"create"))).getAsJsonObject();
                    require(data.get("passed").getAsBoolean(),"Creation gallery did not pass");
                    require(data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Persistence requires a separate JVM");
                    mc.options.renderDistance().set(2);mc.createWorldOpenFlows().openWorld(WORLD,()->{});
                }
                return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;
            require(System.nanoTime()<deadline,"V5 native gallery deadline at stage "+stage);
            if(mc.player==null||mc.level==null||mc.screen!=null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            if(stage==0){
                if(MODE.equals("create"))work=server.submit(()->{
                    var level=server.getLevel(IslandWorld.VANILLA_WORLD);scenes=findScenes(level);index=0;
                    pose(server.getPlayerList().getPlayer(id),level,scenes.get(0),false);return true;
                });
                else work=server.submit(()->{verifyReload(server.getPlayerList().getPlayer(id));return true;});
                stage=MODE.equals("create")?1:8;ticks=0;return;
            }
            if(stage==1&&done()){
                if(!ready(mc,scenes.get(index),false)){ticks=0;return;}if(++ticks<80)return;
                work=server.submit(()->server.overworld().getGameTime()-poseTick>=80);stage=2;ticks=0;
            }else if(stage==2&&done()){
                if(!Boolean.TRUE.equals(work.join())){stage=1;ticks=0;return;}
                shot(mc,"v5-"+scenes.get(index).id+"-dark.png");
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));poseTick=server.overworld().getGameTime();return true;});stage=3;ticks=0;
            }else if(stage==3&&done()){
                if(!ready(mc,scenes.get(index),true)){ticks=0;return;}if(++ticks<80)return;
                work=server.submit(()->server.overworld().getGameTime()-poseTick>=80);stage=4;ticks=0;
            }else if(stage==4&&done()){
                if(!Boolean.TRUE.equals(work.join())){stage=3;ticks=0;return;}
                shot(mc,"v5-"+scenes.get(index).id+"-clear.png");
                if(++index<scenes.size()){
                    work=server.submit(()->{pose(server.getPlayerList().getPlayer(id),server.getLevel(IslandWorld.VANILLA_WORLD),scenes.get(index),false);return true;});stage=1;ticks=0;
                }else{
                    work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);p.removeEffect(MobEffects.NIGHT_VISION);snapshot(p.serverLevel());return true;});stage=5;ticks=0;
                }
            }else if(stage==5&&done())finish(mc,true,"Natural V5 FULL terrain and layered forest captured without terrain edits; explicit Creative camera moves and optional comparison night vision");
            else if(stage==8&&done()&&++ticks>=30){shot(mc,"v5-cold-reload-dark.png");finish(mc,true,"A separate JVM retained the V5 generator, selected subsea block hashes, registered seas and player dimension");}
        }catch(Throwable failure){failure.printStackTrace();finish(mc,false,failure.toString());}
    }

    private static List<Scene> findScenes(ServerLevel level){
        require(level!=null,"V5 test world is missing");var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();
        require(generator.terrainRevision()==5&&generator.geometry().equals(PROFILE),"Gallery entered an old or mismatched generator");
        var random=level.getChunkSource().randomState();var sampler=random.sampler();
        var keys=List.of(RealmBiomes.ASH_ISLANDS,RealmBiomes.PALE_GARDENS,RealmBiomes.STONE_VAULTS,RealmBiomes.CRIMSON_THICKETS);
        String[] names={"floating-archipelago","dangerous-forest","stone-mountains","crimson-thickets"};
        var candidates=new HashMap<String,List<Candidate>>();for(String name:names)candidates.put(name,new ArrayList<>());int checked=0,columns=0;
        // Bounded numeric screening seeks biome interiors and actual mountain relief, not the
        // first climate-labelled beach. FULL generation below then measures the real forest.
        for(int radius=0;radius<=64;radius++){
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                require(System.nanoTime()<deadline,"Gallery numeric search exhausted its deadline");
                int x=dx*64+7,z=dz*64+7;checked++;
                var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),12,QuartPos.fromBlock(z),sampler);
                int kind=-1;for(int i=0;i<keys.size();i++)if(biome.is(keys.get(i)))kind=i;
                if(kind<0||candidates.get(names[kind]).size()>=10)continue;
                var column=generator.getBaseColumn(x,z,level,random);columns++;int top=-1,bottom=-1,thickness=0;
                for(int y=205;y>PROFILE.lowerSeaTop()+1;y--)if(StoneVaults.isGround(column.getBlock(y))){top=y;break;}
                if(top<PROFILE.lowerSeaTop()+2)continue;
                for(int y=top;y>PROFILE.lowerSeaTop()&&StoneVaults.isGround(column.getBlock(y));y--){bottom=y;thickness++;}
                if(kind==0&&(top<55||thickness<12||bottom<=PROFILE.lowerSeaTop()+2))continue;
                // An interior forest sample, rather than the first climate-labelled coastal sand shelf.
                int context=0;
                for(int ox:new int[]{-32,0,32})for(int oz:new int[]{-32,0,32})if(generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x+ox),12,QuartPos.fromBlock(z+oz),sampler).is(keys.get(kind)))context++;
                if((kind==1||kind==3)&&(top<PROFILE.lowerSeaTop()+10||top>120||context<8))continue;
                int grade=0;
                if(kind==2){
                    if(top<110||context<6)continue;
                    for(int[] offset:new int[][]{{16,0},{-16,0},{0,16},{0,-16}}){
                        var side=generator.getBaseColumn(x+offset[0],z+offset[1],level,random);columns++;
                        int height=34;for(int y=205;y>34;y--)if(StoneVaults.isGround(side.getBlock(y))){height=y;break;}
                        grade=Math.max(grade,Math.abs(top-height));
                    }
                    if(grade<10)continue;
                }
                candidates.get(names[kind]).add(new Candidate(names[kind],keys.get(kind),new BlockPos(x,top,z),context*100+top+grade*10));
            }
            if(candidates.values().stream().allMatch(list->list.size()>=10))break;
        }
        require(candidates.values().stream().allMatch(list->!list.isEmpty()),"Native numeric screening missed an interior landscape: "+candidates);
        var result=new ArrayList<Scene>();var manifest=new JsonArray();var chunks=new JsonArray();var evaluations=new JsonArray();long fullStart=System.nanoTime();int fullCandidates=0;
        for(int i=0;i<names.length;i++){
            candidates.get(names[i]).sort((a,b)->Integer.compare(b.score,a.score));
            Candidate candidate=null;LevelChunk chunk=null;BlockPos actual=null;ForestMeasure measure=null;
            for(var probe:candidates.get(names[i])){
                require(System.nanoTime()<deadline,"Bounded FULL scene selection exhausted its deadline");fullCandidates++;
                var current=level.getChunk(probe.ground.getX()>>4,probe.ground.getZ()>>4);
                var ground=actualGround(current,probe.ground.getX(),probe.ground.getZ());
                if(ground==null||!current.getNoiseBiome(QuartPos.fromBlock(ground.getX()),QuartPos.fromBlock(ground.getY()),QuartPos.fromBlock(ground.getZ())).is(probe.biome))ground=nearbyNaturalGround(current,probe.ground,probe.biome);
                var attempt=new JsonObject();attempt.addProperty("scene",names[i]);attempt.addProperty("chunk_x",current.getPos().x);attempt.addProperty("chunk_z",current.getPos().z);attempt.addProperty("numeric_score",probe.score);
                if(ground==null){attempt.addProperty("accepted",false);attempt.addProperty("reason","No dry native soil in requested biome");evaluations.add(attempt);continue;}
                if(i==1||i==3){
                    var found=measureForest(level,current,probe.biome);attempt.add("actual_full_3x3_forest",found.json());
                    boolean dense=found.dryColumns>=1500&&found.coverage()>=.55&&found.flora>=30&&found.hazards>=1;
                    attempt.addProperty("accepted",dense);evaluations.add(attempt);System.out.println("V5_NATIVE_FOREST_CANDIDATE "+attempt);
                    if(!dense)continue;measure=found;
                }else{attempt.addProperty("accepted",true);evaluations.add(attempt);}
                candidate=probe;chunk=current;actual=ground;break;
            }
            data.add("full_candidate_evaluations",evaluations.deepCopy());
            require(candidate!=null&&actual!=null,"No representative FULL "+names[i]+" within ten candidates; see full_candidate_evaluations");
            require(chunk.getNoiseBiome(QuartPos.fromBlock(actual.getX()),QuartPos.fromBlock(actual.getY()),QuartPos.fromBlock(actual.getZ())).is(candidate.biome),
                    "FULL native biome differs from the intended gallery landscape at "+actual);
            validateSeas(chunk);var stat=new JsonObject();stat.addProperty("scene",names[i]);stat.addProperty("chunk_x",chunk.getPos().x);stat.addProperty("chunk_z",chunk.getPos().z);
            stat.addProperty("numeric_probe_y",candidate.ground.getY());stat.addProperty("actual_surface_y",actual.getY());
            stat.addProperty("actual_surface_block",BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(actual).getBlock()).toString());
            stat.addProperty("actual_biome",candidate.biome.location().toString());stat.addProperty("natural_leaf_cells",countLeaves(chunk));chunks.add(stat);
            if(measure!=null)stat.add("actual_full_3x3_forest",measure.json());
            if(i==2)stat.addProperty("numeric_height_and_16_block_grade_score",candidate.score);
            System.out.println("V5_NATIVE_FULL_SURFACE scene="+candidate.id+" numeric="+candidate.ground+" actual="+actual+" block="+stat.get("actual_surface_block")+" leaves="+stat.get("natural_leaf_cells"));
            var scene=surfaceCamera(level,names[i],actual);result.add(scene);manifest.add(sceneJson(scene));
            if(i==1){
                var inside=forestInside(level,chunk,actual);
                int neighbours=0;
                if(inside==null)for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1},{1,-1},{-1,1}}){
                    var other=level.getChunk(chunk.getPos().x+offset[0],chunk.getPos().z+offset[1]);neighbours++;
                    inside=forestInside(level,other,actual);if(inside!=null)break;
                }
                data.addProperty("forest_canopy_neighbor_chunks_checked",neighbours);
                require(inside!=null,"Selected natural forest and its nearest FULL neighbours contain no clear camera under real leaves");
                result.add(inside);manifest.add(sceneJson(inside));
            }
            if(i==2){var cave=findCave(level,chunk);if(cave!=null){result.add(cave);manifest.add(sceneJson(cave));}}
        }
        data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("seed",level.getSeed());data.addProperty("terrain_revision",5);
        data.addProperty("scope","Naturally generated V5 FULL chunks in a disposable Creative profile. Server camera teleports and optional night vision are prepared inspection, not autonomous Survival.");
        data.addProperty("numeric_climate_points_checked",checked);data.addProperty("numeric_columns_checked",columns);data.addProperty("selected_full_chunks",4);data.addProperty("full_candidates_evaluated",fullCandidates);
        data.addProperty("camera_selection_scope","At most ten naturally generated FULL candidate centres per biome; forest interiors require >=55% actual canopy above dry soil, real undergrowth/hazards in a 3x3 chunk area. Mountain numeric screening requires Y>=110 and >=10 block height change over16 blocks. Selection changes no terrain or flora.");
        data.addProperty("full_selection_elapsed_ms",(System.nanoTime()-fullStart)/1000000);data.addProperty("render_distance_chunks",5);
        data.addProperty("stable_client_and_server_ticks_per_image",80);data.add("scenes",manifest);data.add("natural_chunk_counts",chunks);
        System.out.println("V5_NATIVE_GALLERY_SCENES "+data);return List.copyOf(result);
    }
    private static boolean naturalGround(BlockState state){
        return StoneVaults.isGround(state)||state.is(MineralEcology.ROOT_LOAM.get())||state.is(MineralEcology.TOXIC_SAND.get())||state.is(MineralEcology.MINERAL_POWDER.get())||state.is(MineralEcology.MINERAL_FROST.get());
    }
    private static ForestMeasure measureForest(ServerLevel level,LevelChunk centre,ResourceKey<Biome> expected){
        int dry=0,canopy=0,flora=0,hazards=0,leaves=0,biome=0;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var chunk=level.getChunk(centre.getPos().x+dx,centre.getPos().z+dz);
            for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++){
                var floor=actualGround(chunk,x,z);if(floor==null)continue;
                if(!chunk.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(floor.getY()),QuartPos.fromBlock(z)).is(expected))continue;
                biome++;if(floor.getY()<PROFILE.lowerSeaTop()+10||!chunk.getBlockState(floor).getFluidState().isEmpty())continue;
                dry++;boolean covered=false;
                for(int y=floor.getY()+1;y<=Math.min(PROFILE.maxLand(),floor.getY()+40);y++){
                    var state=chunk.getBlockState(new BlockPos(x,y,z));
                    if(state.getBlock() instanceof LeavesBlock){covered=true;leaves++;}
                    if(y<=floor.getY()+2){
                        var path=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                        if(path.equals("pale_fern")||path.equals("pale_litter")||path.equals("venom_reed")||path.equals("spore_pod"))flora++;
                        if(path.equals("venom_reed")||path.equals("spore_pod"))hazards++;
                    }
                }
                if(covered)canopy++;
            }
        }
        return new ForestMeasure(dry,canopy,flora,hazards,leaves,biome);
    }
    private static BlockPos actualGround(LevelChunk chunk,int x,int z){
        for(int y=PROFILE.maxLand();y>PROFILE.lowerSeaTop();y--){var p=new BlockPos(x,y,z);var state=chunk.getBlockState(p);if(naturalGround(state)&&state.getFluidState().isEmpty())return p;}
        return null;
    }
    private static BlockPos nearbyNaturalGround(LevelChunk chunk,BlockPos selected,ResourceKey<Biome> expected){
        BlockPos best=null;int distance=Integer.MAX_VALUE;
        for(int x=chunk.getPos().getMinBlockX()+1;x<chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ()+1;z<chunk.getPos().getMaxBlockZ();z++){
            var p=actualGround(chunk,x,z);if(p==null||!chunk.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(p.getY()),QuartPos.fromBlock(z)).is(expected))continue;
            int d=Math.abs(x-selected.getX())+Math.abs(z-selected.getZ());if(d<distance){distance=d;best=p;}
        }return best;
    }
    private static Scene surfaceCamera(ServerLevel level,String id,BlockPos target){
        for(int[] offset:new int[][]{{32,-32},{-32,-32},{32,32},{-32,32},{0,-44},{44,0}}){
            double x=target.getX()+.5+offset[0],z=target.getZ()+.5+offset[1];
            for(double y=Math.min(190,target.getY()+22);y<=198;y+=8)if(clear(level,x,y,z))
                return new Scene(id,target,x,y,z,target.getX()+.5,target.getY()+4,target.getZ()+.5);
        }
        throw new IllegalStateException("No natural gallery camera around "+target);
    }
    private static Scene forestInside(ServerLevel level,LevelChunk chunk,BlockPos fallback){
        for(int x=chunk.getPos().getMinBlockX()+2;x<=chunk.getPos().getMaxBlockX()-2;x++)for(int z=chunk.getPos().getMinBlockZ()+2;z<=chunk.getPos().getMaxBlockZ()-2;z++){
            var floor=actualGround(chunk,x,z);if(floor==null||!clear(level,x+.5,floor.getY()+1,z+.5))continue;
            if(!chunk.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(floor.getY()),QuartPos.fromBlock(z)).is(RealmBiomes.PALE_GARDENS))continue;
            boolean canopy=false;for(int y=floor.getY()+4;y<Math.min(PROFILE.maxLand(),floor.getY()+40);y++)if(chunk.getBlockState(new BlockPos(x,y,z)).getBlock() instanceof LeavesBlock)canopy=true;
            if(!canopy)continue;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int distance=4;distance<=8;distance+=2){
                var target=floor.offset(d[0]*distance,5,d[1]*distance);return new Scene("forest-under-canopy",floor,x+.5,floor.getY()+1,z+.5,target.getX()+.5,target.getY(),target.getZ()+.5);
            }
        }
        return null;
    }
    private static Scene findCave(ServerLevel level,LevelChunk chunk){
        for(int x=chunk.getPos().getMinBlockX()+2;x<=chunk.getPos().getMaxBlockX()-2;x+=2)for(int z=chunk.getPos().getMinBlockZ()+2;z<=chunk.getPos().getMaxBlockZ()-2;z+=2)for(int y=7;y<120;y++){
            var feet=new BlockPos(x,y,z);if(!chunk.getBlockState(feet).isAir()||!chunk.getBlockState(feet.above()).isAir()||!StoneVaults.isGround(chunk.getBlockState(feet.below())))continue;
            int roof=-1;for(int above=y+3;above<Math.min(y+18,205);above++)if(StoneVaults.isGround(chunk.getBlockState(new BlockPos(x,above,z)))){roof=above;break;}
            if(roof<0)continue;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int view=0;
                for(int distance=1;distance<=9;distance++){var p=feet.offset(d[0]*distance,1,d[1]*distance);if(!level.getBlockState(p).isAir())break;view=distance;}
                if(view>=6)return new Scene("native-mountain-cave",feet.below(),x+.5,y,z+.5,x+.5+d[0]*view,y+1.2,z+.5+d[1]*view);
            }
        }
        return null;
    }
    private static boolean clear(ServerLevel level,double x,double y,double z){var p=BlockPos.containing(x,y,z);return level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir()&&y<205;}
    private static int countLeaves(LevelChunk chunk){int count=0;var p=new BlockPos.MutableBlockPos();for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=35;y<205;y++){p.set(chunk.getPos().getMinBlockX()+x,y,chunk.getPos().getMinBlockZ()+z);if(chunk.getBlockState(p).getBlock() instanceof LeavesBlock)count++;}return count;}
    private static void validateSeas(LevelChunk chunk){
        for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x+=3)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z+=3){
            require(chunk.getBlockState(new BlockPos(x,0,z)).is(Blocks.BEDROCK)&&chunk.getBlockState(new BlockPos(x,255,z)).is(Blocks.BEDROCK),"Natural V5 shell is missing");
            double upper=SeaSurface.cellMinimum(PROFILE,x,z,true);
            for(int y=1;y<255;y++){var state=chunk.getBlockState(new BlockPos(x,y,z));require(!state.is(Blocks.WATER)&&!state.is(Blocks.LAVA),"Vanilla fluid leaked into the realm");if(y>=Math.floor(upper))require(state.is(Interstice.LIGHT_SEA.get()),"V5 changed the accepted upper sea");}
        }
    }
    private static void pose(ServerPlayer p,ServerLevel level,Scene scene,boolean vision){
        for(BlockPos at:new BlockPos[]{scene.target,BlockPos.containing(scene.x,scene.y,scene.z)})for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk((at.getX()>>4)+dx,(at.getZ()>>4)+dz);
        require(clear(level,scene.x,scene.y,scene.z),"Scene camera became blocked");double dx=scene.tx-scene.x,dz=scene.tz-scene.z,dy=scene.ty-scene.y-p.getEyeHeight();
        p.teleportTo(level,scene.x,scene.y,scene.z,Set.of(),(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
        p.setDeltaMovement(0,0,0);p.getAbilities().flying=true;p.onUpdateAbilities();if(vision)p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));else p.removeEffect(MobEffects.NIGHT_VISION);
        poseTick=level.getServer().overworld().getGameTime();
    }
    private static boolean ready(Minecraft mc,Scene scene,boolean vision){
        if(!mc.level.dimension().equals(IslandWorld.VANILLA_WORLD)||mc.player.position().distanceToSqr(scene.x,scene.y,scene.z)>.15||mc.player.hasEffect(MobEffects.NIGHT_VISION)!=vision)return false;
        for(var at:new BlockPos[]{scene.target,mc.player.blockPosition()})for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!mc.level.hasChunkAt(new BlockPos(((at.getX()>>4)+dx)*16,at.getY(),((at.getZ()>>4)+dz)*16)))return false;
        if(!mc.levelRenderer.isSectionCompiled(scene.target))return false;
        if(!scene.id.contains("cave")&&!scene.id.contains("under-canopy"))
            for(int height:new int[]{16,32})if(!mc.levelRenderer.isSectionCompiled(scene.target.above(height)))return false;
        return true;
    }
    private static JsonObject sceneJson(Scene s){var o=new JsonObject();o.addProperty("id",s.id);o.addProperty("target_x",s.target.getX());o.addProperty("target_y",s.target.getY());o.addProperty("target_z",s.target.getZ());o.addProperty("camera_x",s.x);o.addProperty("camera_y",s.y);o.addProperty("camera_z",s.z);return o;}
    private static String stableSubseaHash(LevelChunk chunk){
        try{var digest=MessageDigest.getInstance("SHA-256");var p=new BlockPos.MutableBlockPos();
            for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)for(int y=0;y<=30;y++){
                p.set(x,y,z);digest.update(chunk.getBlockState(p).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update((byte)0);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void snapshot(ServerLevel level){
        var array=new JsonArray();var unique=new java.util.HashSet<Long>();
        for(var s:scenes){int cx=s.target.getX()>>4,cz=s.target.getZ()>>4;long key=((long)cx<<32)^(cz&0xffffffffL);if(!unique.add(key))continue;
            var o=new JsonObject();o.addProperty("chunk_x",cx);o.addProperty("chunk_z",cz);o.addProperty("sha256_y0_30",stableSubseaHash(level.getChunk(cx,cz)));array.add(o);
        }
        data.add("saved_subsea_chunk_hashes",array);data.addProperty("cold_snapshot_scope","Exact naturally generated block states at Y0..30 in each selected FULL chunk; changing above-sea flora intentionally excluded.");
    }
    private static void verifyReload(ServerPlayer p){
        require(p.serverLevel().dimension().equals(IslandWorld.VANILLA_WORLD),"Player dimension did not survive restart");var level=p.serverLevel();
        require(((IslandChunkGenerator)level.getChunkSource().getGenerator()).terrainRevision()==5,"Saved generator decoded a different terrain revision");
        int checked=0;for(var element:data.getAsJsonArray("saved_subsea_chunk_hashes")){var o=element.getAsJsonObject();var c=level.getChunk(o.get("chunk_x").getAsInt(),o.get("chunk_z").getAsInt());validateSeas(c);
            require(stableSubseaHash(c).equals(o.get("sha256_y0_30").getAsString()),"Natural subsea terrain changed after a cold restart at "+c.getPos());checked++;
        }
        require(checked>=4&&!p.hasEffect(MobEffects.NIGHT_VISION),"Cold validation missed a landscape or retained diagnostic night vision");
        data.addProperty("reload_pid",ProcessHandle.current().pid());data.addProperty("cold_subsea_chunks_checked",checked);data.addProperty("cold_v5_generator_and_player_dimension_preserved",true);
    }
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException(why);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("V5_NATIVE_SCREENSHOT "+name));}
    private static Path save(Minecraft mc){return mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);}
    private static Path report(Minecraft mc,String mode){return mc.gameDirectory.toPath().resolve("v5-"+mode+"-validation.json");}
    private static void finish(Minecraft mc,boolean success,String why){if(closing)return;closing=true;passed=success;reason=why;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.broadcastOptions();settling=new NativeChunkSettler.Session("v5_native_terminal_shutdown",3000);if(!success)write(mc,true);}
    private static void shutdown(Minecraft mc){
        if(mc.getSingleplayerServer()==null){finished=true;write(mc,false);mc.stop();return;}
        if(settling.failed()&&!data.has("quiescence_failed")){passed=false;reason=settling.failure();data.addProperty("quiescence_failed",true);write(mc,true);}
        if(!settling.ready())return;data.add("settle_terminal",settling.report());data.addProperty("clean_generation_before_mc_stop",true);finished=true;settling=null;write(mc,false);mc.stop();
    }
    private static void write(Minecraft mc,boolean pending){data.addProperty("passed",passed);data.addProperty("reason",reason);data.addProperty("stage",stage);data.addProperty("shutdown_pending",pending);
        try{Files.writeString(report(mc,MODE),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception failure){failure.printStackTrace();}System.out.println("V5_NATIVE_VALIDATION "+data);
    }
}
