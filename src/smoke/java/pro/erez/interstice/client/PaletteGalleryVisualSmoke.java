package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.ecology.RealmEcology;
import pro.erez.interstice.ecology.SporePodBlock;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.*;

/** One disposable native JVM, one V5 geometry, real A/B/C pack reloads. Inspection, never Survival. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class PaletteGalleryVisualSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("interstice.paletteGallerySmoke");
    private static final String WORLD="v6-palette-comparison-v5";
    private static final Path CANDIDATES=Path.of(System.getProperty("interstice.paletteCandidates",".verification/v6-palette-candidates")).toAbsolutePath().normalize();
    private static final String[] PALETTES={"A","B","C"};
    private static boolean started,finished,closing,passed,pinned;
    private static volatile boolean screenshotSaved;
    private static int stage,ticks,palette,index,clientTicks,atlasEpoch;
    private static long deadline;
    private static String reason;
    private static CompletableFuture<?> work,reload;
    private static volatile NativeChunkSettler.Session settling;
    private static List<Scene> scenes=List.of();
    private static Map<String,String> baselineHashes=Map.of();
    private static final JsonObject data=new JsonObject();
    private static final JsonArray captures=new JsonArray(),reloads=new JsonArray();
    private static List<String> originalPacks=List.of();
    private record Scene(String id,BlockPos target,double x,double y,double z,double tx,double ty,double tz,boolean prepared,String scope) {}

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){
        if(!ENABLED)return;
        // Tide advances on its own Post event even while vanilla world simulation is frozen.
        // This explicitly prepared colour inspection holds CALM; gameplay tests do not use it.
        if(pinned)TideManager.getSavedData(event.getServer()).setPhase(TidePhase.CALM,24000);
        var s=settling;if(s!=null)s.tick(event.getServer());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||finished)return;clientTicks++;var mc=Minecraft.getInstance();
        try{
            if(started&&clientTicks%200==0)System.out.println("V6_PALETTE_HEARTBEAT stage="+stage+" palette="+PALETTES[Math.min(palette,2)]+" image="+index+"/16 stable_client_ticks="+ticks+" player="+(mc.player==null?"not_joined":mc.player.position()));
            if(closing){shutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen){
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(35);
                Path profile=mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(profile.toString().replace('\\','/').contains("/.verification/"),"Palette gallery requires a disposable .verification profile");
                require(!Files.exists(profile.resolve("saves/"+WORLD+"/level.dat")),"Palette gallery save already exists");
                installPacks(profile);originalPacks=new ArrayList<>(mc.getResourcePackRepository().getSelectedIds());
                mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);
                mc.options.framerateLimit().set(60);mc.options.fov().set(70);mc.options.gamma().set(.5);mc.options.graphicsMode().set(GraphicsStatus.FANCY);
                mc.options.ambientOcclusion().set(true);mc.options.bobView().set(false);mc.options.fovEffectScale().set(0.0);mc.options.hideLightningFlash().set(true);
                mc.getWindow().setWindowed(1280,720);mc.resizeDisplay();
                var rules=new GameRules();rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("V6 A B C palette inspection",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261006L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;require(System.nanoTime()<deadline,"Palette gallery deadline at stage "+stage);
            if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.screen!=null)return;
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==1&&settling!=null)require(!settling.failed(),settling.failure());
            if(stage==0){
                work=server.submit(()->{try{var level=server.getLevel(IslandWorld.VANILLA_WORLD);scenes=prepare(level);return true;}catch(Exception failure){throw new java.util.concurrent.CompletionException(failure);}});
                System.out.println("V6_PALETTE_PROGRESS preparing eight bounded V5 scenes; seed20261006, revision5");
                settling=new NativeChunkSettler.Session("palette_geometry_before_freeze",6000);stage=1;return;
            }
            if(stage==1&&done()&&settling.ready()){
                System.out.println("V6_PALETTE_PROGRESS FULL geometry quiescent; freezing one comparison snapshot");
                data.add("settle_before_freeze",settling.report());settling=null;
                work=server.submit(()->{
                    var level=server.getLevel(IslandWorld.VANILLA_WORLD);baselineHashes=hashes(level);server.tickRateManager().setFrozen(true);
                    pinned=true;TideManager.getSavedData(server).setPhase(TidePhase.CALM,24000);TideSync.broadcast(TideManager.getState(server));
                    level.setDayTime(18000);level.setWeatherParameters(0,0,false,false);pose(server.getPlayerList().getPlayer(uuid),level,scenes.getFirst(),false);return true;
                });stage=2;return;
            }
            if(stage==2&&done()){reloadPack(mc);stage=3;return;}
            if(stage==3&&reload.isDone()){
                reload.join();atlasEpoch=clientTicks;verifyActivePack(mc);index=0;
                work=server.submit(()->{pose(server.getPlayerList().getPlayer(uuid),server.getLevel(IslandWorld.VANILLA_WORLD),scenes.getFirst(),false);return true;});
                stage=4;ticks=0;return;
            }
            if(stage==4&&done()){
                var scene=scenes.get(index/2);boolean nv=(index&1)==1;
                if(!ready(mc,scene,nv)){ticks=0;return;}ticks++;
                // The two unchanged sea animations have periods128 and96 ticks. Each matching
                // image uses the same phase modulo384 after its pack reload, including interpolation.
                int desired=((index+1)*96)%384;
                if(ticks<80||Math.floorMod(clientTicks-atlasEpoch,384)!=desired)return;
                capture(mc,scene,nv,desired);stage=5;ticks=0;return;
            }
            if(stage==5){
                if(!screenshotSaved)return;
                Path shot=mc.gameDirectory.toPath().resolve("screenshots").resolve(filename(scenes.get(index/2),(index&1)==1));
                require(Files.isRegularFile(shot)&&Files.size(shot)>0,"Native screenshot did not finish writing: "+shot);
                var row=captures.get(captures.size()-1).getAsJsonObject();row.addProperty("png_sha256",sha(shot));
                if(++index<scenes.size()*2){
                    work=server.submit(()->{pose(server.getPlayerList().getPlayer(uuid),server.getLevel(IslandWorld.VANILLA_WORLD),scenes.get(index/2),(index&1)==1);return true;});stage=4;ticks=0;
                }else{work=server.submit(()->{verifyGeometry(server.getLevel(IslandWorld.VANILLA_WORLD));return true;});stage=6;}
                return;
            }
            if(stage==6&&done()){
                if(++palette<PALETTES.length){reloadPack(mc);stage=3;}
                else{
                    require(captures.size()==48&&reloads.size()==3,"Missing native A/B/C captures or actual reloads");
                    data.addProperty("same_geometry_all_palettes",true);data.addProperty("actual_resource_reload_count",reloads.size());
                    finish(mc,true,"Three actual native palette packs, eight scenes and48 dark/NV frames on one unchanged inspection snapshot");
                }
            }
        }catch(Throwable failure){failure.printStackTrace();finish(mc,false,failure.toString());}
    }
    private static List<Scene> prepare(ServerLevel level)throws Exception{
        require(level!=null,"V5 realm missing");var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();
        require(generator.terrainRevision()==5&&generator.geometry().equals(GeometryProfile.TALL),"Colour gallery must use unmodified V5 geometry");
        var result=new ArrayList<Scene>();
        addScene(result,new Scene("open-surface",new BlockPos(-121,129,-57),-88.5,151,-88.5,-120.5,133,-56.5,false,"Natural V5 ash-island mass; accepted baseline overview camera"));
        addScene(result,new Scene("forest-eye",new BlockPos(258,71,-574),258.5,72,-573.5,262.5,76,-573.5,false,"Natural forest at standard player eye height; accepted baseline coordinates"));
        addScene(result,shore(level));
        addScene(result,new Scene("cave",new BlockPos(3394,60,2306),3394.5,61,2306.5,3400.5,62.2,2306.5,false,"Natural V5 cavity; camera inspection does not prove an accessible entrance"));
        addScene(result,oreFixture(level));
        addScene(result,hazardFixture(level));
        addScene(result,fruit(level));
        addScene(result,retortFixture(level));
        var manifest=new JsonArray();
        for(var s:result){
            load(level,s.target);load(level,BlockPos.containing(s.x,s.y,s.z));require(clear(level,s.x,s.y,s.z),"Fixed camera obstructed: "+s.id);
            var item=sceneJson(s);item.addProperty("actual_target_state",level.getBlockState(s.target).toString());manifest.add(item);
            System.out.println("V6_PALETTE_SCENE_READY "+item);
        }
        data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("seed",level.getSeed());data.addProperty("terrain_revision",5);data.add("scenes",manifest);
        data.addProperty("scope","Disposable Creative native colour inspection: five natural scenes and three explicitly prepared material comparisons. No Survival, natural ore availability, ruin placement, cave route or V6 geography acceptance is claimed.");
        data.addProperty("simulation_control","Random ticks disabled; vanilla world simulation frozen after FULL quiescence; CALM tide pinned only in this disposable smoke; same geometry/state hashes verified after every palette");
        data.addProperty("width",1280);data.addProperty("height",720);data.addProperty("fov",70);data.addProperty("gamma",.5);data.addProperty("render_distance",5);data.addProperty("simulation_distance",5);data.addProperty("ambient_occlusion",true);data.addProperty("shaders","none");
        return List.copyOf(result);
    }
    private static Scene shore(ServerLevel level){
        var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();int checked=0;
        for(int radius=0;radius<=32;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;int x=dx*8,z=dz*8;checked++;
            if(dx==-radius&&dz==-radius)System.out.println("V6_PALETTE_SHORE_SURVEY radius_blocks="+(radius*8)+"/256 numeric_columns="+checked);
            var column=generator.getBaseColumn(x,z,level,random);int top=-1;
            for(int y=100;y>=35;y--)if(ground(column.getBlock(y))){top=y;break;}
            if(top<35||top>40)continue;load(level,new BlockPos(x,top,z));
            for(int y=Math.min(top+2,42);y>=35;y--){var p=new BlockPos(x,y,z);if(!ground(level.getBlockState(p))||!clear(level,x+.5,y+1,z+.5))continue;
                for(int[] direction:List.of(new int[]{1,0},new int[]{-1,0},new int[]{0,1},new int[]{0,-1}))for(int distance=2;distance<=8;distance+=2){
                    var sea=new BlockPos(x+direction[0]*distance,33,z+direction[1]*distance);if(!level.getBlockState(sea).is(Interstice.HEAVY_BLOCK.get()))continue;
                    data.addProperty("shore_numeric_columns_checked",checked);return new Scene("lower-shore",p,x+.5,y+1,z+.5,sea.getX()+.5,34,sea.getZ()+.5,false,"Natural dry shore and unchanged crimson lower sea; bounded search within256 blocks of origin");
                }
            }
        }
        throw new IllegalStateException("No natural dry lower-sea shore in the fixed256-block search");
    }
    private static Scene fruit(ServerLevel level){
        int checked=0;
        for(int cx=14;cx<=18;cx++)for(int cz=-38;cz<=-34;cz++){
            var chunk=level.getChunk(cx,cz);checked++;
            System.out.println("V6_PALETTE_FRUIT_SURVEY full_chunk="+cx+","+cz+" bounded_count="+checked+"/25");
            for(int y=36;y<=160;y++)for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
                var p=new BlockPos(x,y,z);if(!chunk.getBlockState(p).is(GardenMaterials.CROWN_FRUIT.get()))continue;
                require(level.getBlockState(p.below()).is(GardenMaterials.CROWN_LEAVES.get()),"Natural crown pod lacks crown support");
                for(int distance:List.of(3,5,2))for(int[] d:List.of(new int[]{1,0},new int[]{-1,0},new int[]{0,1},new int[]{0,-1}))for(int dy:List.of(-1,1,-3)){
                    double px=x+.5+d[0]*distance,py=y+dy,pz=z+.5+d[1]*distance;if(!clear(level,px,py,pz))continue;
                    boolean open=true;for(int step=1;step<distance;step++){var s=level.getBlockState(new BlockPos(x+d[0]*step,y,z+d[1]*step));if(!s.isAir()&&!(s.getBlock() instanceof LeavesBlock)){open=false;break;}}
                    if(!open)continue;data.addProperty("fruit_full_chunks_checked",checked);return new Scene("crown-fruit",p,px,py,pz,x+.5,y+.6,z+.5,false,"Naturally generated ripe crown pod; free-air inspection camera, no scaffold or tree edits");
                }
            }
        }
        throw new IllegalStateException("No visible natural crown fruit within the fixed25 FULL forest chunks");
    }
    private static BlockPos platform(ServerLevel level,int originalX,String id){
        var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();var profile=generator.geometry();var random=level.getChunkSource().randomState();
        var columns=new HashMap<Long,NoiseColumn>();var attempts=new JsonArray();var search=new JsonObject();
        search.addProperty("scene",id);search.addProperty("original_x",originalX);search.addProperty("original_z",2384);
        search.addProperty("centre_radius_blocks",32);search.addProperty("centre_grid_step",8);search.addProperty("min_base_y",150);search.addProperty("max_base_y",195);search.addProperty("y_step",3);
        search.addProperty("air_cube_width",9);search.addProperty("air_cube_height",6);search.addProperty("required_upper_clearance",profile.clearance());search.add("candidates",attempts);
        if(!data.has("fixture_searches"))data.add("fixture_searches",new JsonArray());data.getAsJsonArray("fixture_searches").add(search);
        System.out.println("V6_PALETTE_FIXTURE_SEARCH "+search);
        int fullCandidates=0,fullAirCells=0;var fullChunks=new HashSet<Long>();
        // Fixed centre bounds; the nine-block footprint extends at most four blocks farther.
        // Prefer the original X/Z and the lowest legal Y, then bounded nearby centres.
        for(int radius=0;radius<=32;radius+=8)for(int ox=-radius;ox<=radius;ox+=8)for(int oz=-radius;oz<=radius;oz+=8){
            if(Math.max(Math.abs(ox),Math.abs(oz))!=radius)continue;int x=originalX+ox,z=2384+oz;
            for(int y=150;y<=195;y+=3){
                var base=new BlockPos(x,y,z);var attempt=new JsonObject();attempt.addProperty("x",x);attempt.addProperty("y",y);attempt.addProperty("z",z);attempts.add(attempt);
                boolean numeric=true;int minimumClearance=Integer.MAX_VALUE;
                screening:for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
                    int wx=x+dx,wz=z+dz;int clearance=(int)Math.floor(SeaSurface.cellMinimum(profile,wx,wz,true))-(y+5)-1;
                    minimumClearance=Math.min(minimumClearance,clearance);if(clearance<profile.clearance()){numeric=false;break screening;}
                    long key=BlockPos.asLong(wx,0,wz);var column=columns.get(key);if(column==null){column=generator.getBaseColumn(wx,wz,level,random);columns.put(key,column);}
                    for(int dy=0;dy<=5;dy++)if(!column.getBlock(y+dy).isAir()){numeric=false;break screening;}
                }
                attempt.addProperty("numeric_air_and_sea_clearance",numeric);attempt.addProperty("minimum_upper_clearance_checked",minimumClearance);
                search.addProperty("numeric_columns_checked",columns.size());search.addProperty("candidate_positions_checked",attempts.size());
                if(!numeric){if(attempts.size()%128==0)System.out.println("V6_PALETTE_FIXTURE_PROGRESS scene="+id+" candidates="+attempts.size()+" numeric_columns="+columns.size());continue;}
                load(level,base);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)fullChunks.add(ChunkPos.asLong((x>>4)+dx,(z>>4)+dz));fullCandidates++;
                boolean actual=true;
                actualCheck:for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=0;dy<=5;dy++){
                    var point=base.offset(dx,dy,dz);fullAirCells++;var state=level.getBlockState(point);
                    if(!state.isAir()){actual=false;attempt.addProperty("first_obstruction",point.toShortString());attempt.addProperty("obstruction_state",state.toString());break actualCheck;}
                }
                attempt.addProperty("actual_full_air_cube",actual);search.addProperty("full_candidates_checked",fullCandidates);search.addProperty("full_chunks_inspected",fullChunks.size());search.addProperty("actual_air_cells_checked",fullAirCells);
                System.out.println("V6_PALETTE_FIXTURE_CANDIDATE scene="+id+" "+attempt);
                if(!actual)continue;
                // Validate all486 cells before writing any prepared support; failure is atomic.
                for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)level.setBlock(base.offset(dx,0,dz),Interstice.RIFTSTONE.get().defaultBlockState(),3);
                search.addProperty("selected",true);search.addProperty("selected_x",x);search.addProperty("selected_y",y);search.addProperty("selected_z",z);search.addProperty("natural_blocks_replaced",0);
                return base;
            }
        }
        search.addProperty("selected",false);throw new IllegalStateException("No untouched9x9x6 fixture air cube for "+id+" in fixed centre radius32 / Y150..195");
    }
    private static Scene oreFixture(ServerLevel level){
        var base=platform(level,3440,"ore-host-panel");
        for(int x=-2;x<=2;x++)for(int y=1;y<=3;y++)level.setBlock(base.offset(x,y,1),Interstice.RIFTSTONE.get().defaultBlockState(),3);
        level.setBlock(base.offset(-1,2,1),MineralEcology.RIFTSILVER_SEAM.get().defaultBlockState(),3);
        level.setBlock(base.offset(1,2,1),MineralEcology.PHOSPHORITE_ORE.get().defaultBlockState(),3);
        return new Scene("ore-host-panel",base.offset(0,2,1),base.getX()+.5,base.getY()+1,base.getZ()-3.5,base.getX()+.5,base.getY()+2.3,base.getZ()+1.5,true,"Prepared ore/host colour panel in a bounded verified air cube above untouched natural V5 terrain; does not prove naturally exposed ore");
    }
    private static Scene hazardFixture(ServerLevel level){
        var base=platform(level,3456,"hazard-and-safe");level.setBlock(base.offset(-1,1,0),RealmEcology.SPORE_POD.get().defaultBlockState().setValue(SporePodBlock.PHASE,SporePodBlock.Phase.ARMED),3);
        level.setBlock(base.offset(1,1,0),block("glow_bloom").defaultBlockState(),3);
        return new Scene("hazard-and-safe",base.above(),base.getX()+.5,base.getY()+1,base.getZ()-3.5,base.getX()+.5,base.getY()+1.3,base.getZ()+.5,true,"Prepared armed capsule and safe glow bloom side by side in a bounded verified air cube; native states/models/light, no behavioural claim");
    }
    private static Scene retortFixture(ServerLevel level){
        var base=platform(level,3488,"retort-and-ruin-materials");
        for(int x:new int[]{-3,3})for(int z:new int[]{0,3})for(int y=1;y<=4;y++)level.setBlock(base.offset(x,y,z),VaultMaterials.VAULTSTONE_BRICKS.get().defaultBlockState(),3);
        for(int x=-3;x<=3;x++)for(int z=0;z<=3;z++)level.setBlock(base.offset(x,5,z),VaultMaterials.POLISHED_VAULTSTONE.get().defaultBlockState(),3);
        level.setBlock(base.offset(0,1,2),RealmAgriculture.RETORT.get().defaultBlockState(),3);
        return new Scene("retort-and-ruin-materials",base.offset(0,1,2),base.getX()+.5,base.getY()+1,base.getZ()-3.5,base.getX()+.5,base.getY()+2,base.getZ()+2.5,true,"Prepared retort and roofed ruin-material segment in a bounded verified air cube against natural mountains; not a naturally placed structure");
    }
    private static Block block(String id){var key=ResourceLocation.fromNamespaceAndPath(Interstice.ID,id);require(BuiltInRegistries.BLOCK.containsKey(key),"Missing native block "+id);return BuiltInRegistries.BLOCK.get(key);}
    private static void addScene(List<Scene> output,Scene scene){output.add(scene);System.out.println("V6_PALETTE_SCENE_SELECTED "+sceneJson(scene));}
    private static boolean ground(BlockState s){return StoneVaults.isGround(s)||s.is(MineralEcology.ROOT_LOAM.get())||s.is(MineralEcology.TOXIC_SAND.get())||s.is(MineralEcology.MINERAL_POWDER.get())||s.is(MineralEcology.MINERAL_FROST.get());}
    private static boolean clear(ServerLevel level,double x,double y,double z){var p=BlockPos.containing(x,y,z);return level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir();}
    private static void load(ServerLevel level,BlockPos p){for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk((p.getX()>>4)+dx,(p.getZ()>>4)+dz);}
    private static void pose(ServerPlayer player,ServerLevel level,Scene s,boolean nv){
        require(clear(level,s.x,s.y,s.z),"Camera geometry changed "+s.id);double dx=s.tx-s.x,dz=s.tz-s.z,dy=s.ty-s.y-player.getEyeHeight();
        player.teleportTo(level,s.x,s.y,s.z,Set.of(),(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
        player.setDeltaMovement(0,0,0);player.getAbilities().flying=true;player.onUpdateAbilities();player.removeAllEffects();if(nv)player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));
    }
    private static boolean ready(Minecraft mc,Scene s,boolean nv){
        if(!mc.level.dimension().equals(IslandWorld.VANILLA_WORLD)||mc.player.position().distanceToSqr(s.x,s.y,s.z)>.05||mc.player.hasEffect(MobEffects.NIGHT_VISION)!=nv)return false;
        if(!mc.level.hasChunkAt(s.target)||!mc.levelRenderer.isSectionCompiled(s.target))return false;
        return mc.getWindow().getWidth()==1280&&mc.getWindow().getHeight()==720;
    }
    private static Map<String,String> hashes(ServerLevel level){
        var result=new TreeMap<String,String>();try{
            for(var s:scenes){var digest=MessageDigest.getInstance("SHA-256");var pos=new BlockPos.MutableBlockPos();
                for(int x=s.target.getX()-8;x<=s.target.getX()+8;x++)for(int z=s.target.getZ()-8;z<=s.target.getZ()+8;z++)for(int y=0;y<=205;y++){
                    pos.set(x,y,z);digest.update(level.getBlockState(pos).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update((byte)0);
                }
                result.put(s.id,HexFormat.of().formatHex(digest.digest()));
            }
        }catch(Exception e){throw new IllegalStateException(e);}return Map.copyOf(result);
    }
    private static void verifyGeometry(ServerLevel level){require(hashes(level).equals(baselineHashes),"Geometry or plant state changed during palette "+PALETTES[palette]);}
    private static JsonObject sceneJson(Scene s){var o=new JsonObject();o.addProperty("id",s.id);o.addProperty("prepared",s.prepared);o.addProperty("scope",s.scope);o.addProperty("target_x",s.target.getX());o.addProperty("target_y",s.target.getY());o.addProperty("target_z",s.target.getZ());o.addProperty("camera_x",s.x);o.addProperty("camera_y",s.y);o.addProperty("camera_z",s.z);o.addProperty("look_at_x",s.tx);o.addProperty("look_at_y",s.ty);o.addProperty("look_at_z",s.tz);return o;}
    private static void installPacks(Path profile)throws Exception{
        var validation=JsonParser.parseString(Files.readString(CANDIDATES.resolve("validation.json"))).getAsJsonObject();require(validation.get("passed").getAsBoolean(),"Candidate PNG audit failed");
        for(String p:PALETTES){Path source=CANDIDATES.resolve("v6-palette-"+p),destination=profile.resolve("resourcepacks/v6-palette-"+p);
            try(var files=Files.walk(source)){for(var input:files.toList()){Path target=destination.resolve(source.relativize(input));require(target.normalize().startsWith(profile),"Pack copy escaped profile");if(Files.isDirectory(input))Files.createDirectories(target);else Files.copy(input,target,StandardCopyOption.REPLACE_EXISTING);}}
        }
        data.addProperty("candidate_validation_sha256",sha(CANDIDATES.resolve("validation.json")));
    }
    private static void reloadPack(Minecraft mc){
        var repo=mc.getResourcePackRepository();repo.reload();String selected="file/v6-palette-"+PALETTES[palette];require(repo.isAvailable(selected),"Native pack repository cannot find "+selected);
        var ids=new ArrayList<>(originalPacks);ids.removeIf(id->id.startsWith("file/v6-palette-"));ids.add(selected);repo.setSelected(ids);reload=mc.reloadResourcePacks();
        System.out.println("V6_PALETTE_PROGRESS native resource reload requested "+selected);
    }
    private static void verifyActivePack(Minecraft mc)throws Exception{
        String selected="file/v6-palette-"+PALETTES[palette];var resource=mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"textures/block/riftstone.png")).orElseThrow();
        require(resource.sourcePackId().equals(selected),"Active native texture came from another pack");
        String expected=sha(CANDIDATES.resolve("v6-palette-"+PALETTES[palette]+"/assets/interstice/textures/block/riftstone.png"));
        String actual;try(var in=resource.open()){actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));}
        require(actual.equals(expected),"Active resource bytes differ from audited candidate");
        var row=new JsonObject();row.addProperty("palette",PALETTES[palette]);row.addProperty("pack_id",selected);row.addProperty("active_riftstone_sha256",actual);row.addProperty("reload_completed",true);reloads.add(row);
        System.out.println("V6_PALETTE_PACK_ACTIVE "+row);
    }
    private static String filename(Scene s,boolean nv){return "v6-palette-"+PALETTES[palette]+"-"+s.id+"-"+(nv?"nv":"dark")+".png";}
    private static void capture(Minecraft mc,Scene s,boolean nv,int phase){
        var row=sceneJson(s);row.addProperty("palette",PALETTES[palette]);row.addProperty("night_vision",nv);row.addProperty("file",filename(s,nv));row.addProperty("geometry_state_sha256",baselineHashes.get(s.id));row.addProperty("atlas_phase_mod384",phase);row.addProperty("stable_client_ticks",ticks);row.addProperty("actual_yaw",mc.player.getYRot());row.addProperty("actual_pitch",mc.player.getXRot());row.addProperty("eye_y",mc.player.getEyeY());captures.add(row);
        screenshotSaved=false;
        Screenshot.grab(mc.gameDirectory,filename(s,nv),mc.getMainRenderTarget(),message->{System.out.println("V6_PALETTE_SCREENSHOT "+row);screenshotSaved=true;});
    }
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static String sha(Path file)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void finish(Minecraft mc,boolean success,String why){
        if(closing)return;closing=true;passed=success;reason=why;pinned=false;
        var server=mc.getSingleplayerServer();if(server!=null)server.execute(()->server.tickRateManager().setFrozen(false));
        mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.broadcastOptions();settling=new NativeChunkSettler.Session("palette_gallery_terminal_shutdown",6000);write(mc,true);
    }
    private static void shutdown(Minecraft mc){
        if(mc.getSingleplayerServer()==null){finished=true;write(mc,false);mc.stop();return;}
        if(settling.failed()&&!data.has("quiescence_failed")){passed=false;reason=settling.failure();data.addProperty("quiescence_failed",true);write(mc,true);}
        if(!settling.ready())return;data.add("settle_terminal",settling.report());data.addProperty("clean_generation_before_mc_stop",true);settling=null;finished=true;write(mc,false);mc.stop();
    }
    private static void write(Minecraft mc,boolean pending){
        data.addProperty("passed",passed);data.addProperty("reason",reason);data.addProperty("stage",stage);data.addProperty("shutdown_pending",pending);data.add("captures",captures);data.add("pack_reloads",reloads);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("palette-gallery-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception e){e.printStackTrace();}System.out.println("V6_PALETTE_VALIDATION "+data);
    }
}
