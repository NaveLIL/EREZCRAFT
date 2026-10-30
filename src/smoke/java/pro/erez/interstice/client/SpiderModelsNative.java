package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.*;
import pro.erez.interstice.entity.client.*;
import pro.erez.interstice.worldgen.*;

/** Real full-pack V6 gallery, immutable mesh/UV inspection and actual wall-to-ceiling routes. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class SpiderModelsNative {
    private static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("spiders");
    private static final JsonObject REPORT=new JsonObject();private static final JsonArray SHOTS=new JsonArray();
    private static final List<CaveRiftSpiderEntity> ACTORS=new ArrayList<>();
    private static final List<Vec3> GOALS=new ArrayList<>();private static final boolean[] WALL=new boolean[3];
    private static boolean started,finished,closing,walking;private static int phase,ticks,portrait,routeStart,wallShot;
    private static long deadline;private static CompletableFuture<?> job;private static ServerLevel level;
    private static ArmorStand camera;private static NativeChunkSettler.Session settling;
    private static void require(boolean test,String why){if(!test)throw new IllegalStateException(why);}
    private static void shot(Minecraft mc,String name){var file="spider-models-"+name+".png";Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),unused->{});SHOTS.add(file);}
    private static void view(Minecraft mc,double x,double y,double z,float yaw,float pitch){
        if(camera==null)camera=new ArmorStand(EntityType.ARMOR_STAND,mc.level);
        camera.setInvisible(true);
        double feet=y-camera.getEyeHeight();camera.setPos(x,feet,z);camera.xo=x;camera.yo=feet;camera.zo=z;camera.setYRot(yaw);camera.yRotO=yaw;camera.yHeadRot=camera.yHeadRotO=yaw;camera.yBodyRot=camera.yBodyRotO=yaw;camera.setXRot(pitch);camera.xRotO=pitch;mc.setCameraEntity(camera);
    }
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("spider-models.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void finish(Minecraft mc,boolean passed,String reason){
        if(closing||finished)return;REPORT.addProperty("passed",passed);REPORT.addProperty("reason",reason);REPORT.addProperty("phase",phase);REPORT.addProperty("pid",ProcessHandle.current().pid());REPORT.addProperty("owner_profile_used",false);REPORT.addProperty("prepared_fixture_not_survival",true);REPORT.add("screenshots",SHOTS);
        write(mc);closing=true;walking=false;settling=new NativeChunkSettler.Session("spider_models_shutdown",2200);
    }
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static void submit(Minecraft mc,Runnable action){job=mc.getSingleplayerServer().submit(()->{action.run();return true;});ticks=0;}
    private static void prepare(Minecraft mc){
        var server=mc.getSingleplayerServer();level=server.getLevel(IslandWorld.TENSION_WORLD);
        require(level.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&g.terrainRevision()==6,"Actual V6 required");
        for(int x=-5;x<=24;x++)for(int z=-5;z<=27;z++)for(int y=69;y<=80;y++)level.setBlock(new BlockPos(x,y,z),(y==69?(x%4==0&&z%4==0?Blocks.SEA_LANTERN:Blocks.SMOOTH_STONE):Blocks.AIR).defaultBlockState(),3);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(var variant:CaveRiftSpiderEntity.Variant.values()){
            var s=Interstice.CAVE_RIFT_SPIDER.get().create(level);s.setVariant(variant);s.setNoAi(true);s.setNoGravity(true);s.setPersistenceRequired();s.moveTo(3.5+ACTORS.size()*7,70,4.5);s.setYRot(0);s.yBodyRot=s.yHeadRot=0;level.addFreshEntity(s);ACTORS.add(s);
        }
        var p=player(mc);p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.setInvisible(true);p.setNoGravity(true);p.teleportTo(level,10.5,73,19.5,Set.of(),180,10);p.hasChangedDimension();
    }
    private static final class Counter implements VertexConsumer {
        int count;boolean finite=true;float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        public VertexConsumer addVertex(float x,float y,float z){count++;finite&=Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z);minY=Math.min(minY,y);maxY=Math.max(maxY,y);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){finite&=Float.isFinite(u)&&Float.isFinite(v)&&u>=0&&u<=1&&v>=0&&v<=1;return this;}
        public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){finite&=Math.abs(x*x+y*y+z*z-1)<.001;return this;}
    }
    private static List<List<Float>> pose(CaveRiftSpiderModel model){return model.root().getAllParts().map(p->List.of(p.x,p.y,p.z,p.xRot,p.yRot,p.zRot,p.xScale,p.yScale,p.zScale)).toList();}
    private static void inspect(Minecraft mc,String label){
        var results=new JsonArray();
        for(var server:ACTORS){
            require(mc.level.getEntity(server.getId()) instanceof CaveRiftSpiderEntity,"Client spider arrived");var peer=(CaveRiftSpiderEntity)mc.level.getEntity(server.getId());
            var renderer=(CaveRiftSpiderRenderer)mc.getEntityRenderDispatcher().getRenderer(peer);var model=renderer.getModel();
            require(model.sourceCubes()==309&&model.sourceQuads()==1854&&model.root().getAllParts().count()==53,"All authored cubes, faces and native joints retained");
            model.setupAnim(peer,0,0,100,0,0);var first=pose(model);model.setupAnim(peer,0,0,197,0,0);require(first.equals(pose(model)),"Stationary tarantula is not pumping or fidgeting");
            model.setupAnim(peer,2.3F,1,200,0,0);var move=pose(model);require(!first.equals(move),"Native walking moves existing joints");
            require(model.root().getAllParts().allMatch(p->p.xScale==1&&p.yScale==1&&p.zScale==1),"Animation cannot scale/stretch the authored model");
            var vertices=new Counter();model.renderToBuffer(new PoseStack(),vertices,0xf000f0,0,-1);
            require(vertices.count==7416&&vertices.finite&&model.renderedFaces==1854,"Every authored quad renders with finite positions/UVs/normals");
            require(mc.getResourceManager().getResource(renderer.getTextureLocation(peer)).isPresent(),"Selected species atlas present");
            var row=new JsonObject();row.addProperty("variant",peer.getVariant().name());row.addProperty("source_sha256",model.sourceHash(peer.getVariant()));row.addProperty("mesh_sha256",model.meshHash(peer.getVariant()));row.addProperty("texture",renderer.getTextureLocation(peer).toString());row.addProperty("vertices",vertices.count);row.addProperty("stationary_pose_exactly_static",true);row.addProperty("walk_changes_joints_without_scaling",true);row.addProperty("walking_pose_min_y_model",vertices.minY);row.addProperty("walking_pose_max_y_model",vertices.maxY);results.add(row);
        }
        REPORT.add(label,results);
    }
    private static void routes(){
        for(int i=0;i<3;i++){
            int z=3+i*7;for(int y=70;y<=77;y++)for(int dz=-2;dz<=2;dz++)level.setBlock(new BlockPos(1,y,z+dz),Blocks.GLOWSTONE.defaultBlockState(),3);
            for(int x=1;x<=12;x++)for(int dz=-2;dz<=2;dz++)level.setBlock(new BlockPos(x,77,z+dz),Blocks.GLOWSTONE.defaultBlockState(),3);
            var s=ACTORS.get(i);s.moveTo(3.5,70,z+.5);s.setNoGravity(false);s.setNoAi(false);var goal=new Vec3(9.5,77-s.getBbHeight()/2,z+.5);GOALS.add(goal);require(s.crawlTo(goal),"Native route to ceiling exists");
        }
        routeStart=level.getServer().getTickCount();walking=true;
    }
    @SubscribeEvent public static void server(ServerTickEvent.Post event){
        if(!ACTIVE||finished)return;
        if(closing){settling.tick(event.getServer());if(settling.ready()||settling.failed()){if(settling.failed()){REPORT.addProperty("passed",false);REPORT.addProperty("reason",settling.failure());}REPORT.add("settled",settling.report());finished=true;var mc=Minecraft.getInstance();write(mc);mc.stop();}return;}
        if(walking)for(int i=0;i<3;i++)WALL[i]|=ACTORS.get(i).isClimbingWall()&&ACTORS.get(i).getY()>71;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing)return;
            if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned profile only");
                for(var id:List.of("distanthorizons","create","sable","smartbrainlib","azurecortex"))require(ModList.get().isLoaded(id),"Full pack required: "+id);
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/spider-models-owned")),"Fresh world required");started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(12);
                REPORT.addProperty("distant_horizons_loaded",true);REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.fov().set(60);mc.options.bobView().set(false);
                var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("spider-models-owned",new LevelSettings("Owned studio spiders",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261028L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started)return;require(System.nanoTime()<deadline,"Deadline phase "+phase);
            if(mc.getOverlay()!=null||SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;mc.getToasts().clear();
            if(job!=null&&!job.isDone())return;if(job!=null)job.join();if(++ticks<40)return;ticks=0;
            if(phase==0){submit(mc,()->prepare(mc));phase=1;}
            else if(phase==1){if(!mc.level.dimension().equals(IslandWorld.TENSION_WORLD)||ACTORS.stream().anyMatch(s->mc.level.getEntity(s.getId())==null))return;view(mc,10.5,74,22.5,180,10);mc.levelRenderer.allChanged();phase=2;}
            else if(phase==2){if(++portrait<4)return;portrait=0;inspect(mc,"initial");REPORT.addProperty("actual_dimension",mc.level.dimension().location().toString());shot(mc,"gallery");var s=ACTORS.get(0);view(mc,s.getX()+4.4,72.2,s.getZ()+5.6,142,9);phase=3;}
            else if(phase==3){shot(mc,"portrait-"+ACTORS.get(portrait).getVariant().name().toLowerCase(Locale.ROOT));if(++portrait<3){var s=ACTORS.get(portrait);view(mc,s.getX()+4.4,72.2,s.getZ()+5.6,142,9);}else{view(mc,10.5,74,22.5,180,10);job=mc.reloadResourcePacks();phase=4;}}
            else if(phase==4){inspect(mc,"after_reload");shot(mc,"after-reload");REPORT.addProperty("resource_reload_checked",true);submit(mc,SpiderModelsNative::routes);phase=5;}
            else if(phase==5){int elapsed=mc.getSingleplayerServer().getTickCount()-routeStart;var client=(CaveRiftSpiderEntity)mc.level.getEntity(ACTORS.get(1).getId());if(client!=null){view(mc,client.getX()+5,client.getY()+1.4,client.getZ()+5,140,4);if(elapsed<160&&wallShot<8){shot(mc,"crawl-"+wallShot++);}}if(elapsed<240)return;
                submit(mc,()->{var values=new JsonArray();for(int i=0;i<3;i++){var s=ACTORS.get(i);require(WALL[i]&&s.isClimbingCeiling()&&s.getBoundingBox().getCenter().distanceTo(GOALS.get(i))<.3,"Real floor-wall-ceiling route "+s.getVariant());var row=new JsonObject();row.addProperty("variant",s.getVariant().name());row.addProperty("wall",WALL[i]);row.addProperty("ceiling",s.isClimbingCeiling());values.add(row);s.setNoAi(true);}REPORT.add("actual_routes",values);walking=false;});phase=6;}
            else if(phase==6){var s=ACTORS.get(1);view(mc,s.getX()+4,74.2,s.getZ()+4,135,-20);phase=7;}
            else if(phase==7){inspect(mc,"ceiling");shot(mc,"ceiling");finish(mc,true,"");}
        }catch(Throwable error){error.printStackTrace();if(mc.level!=null)shot(mc,"failure");finish(mc,false,error.toString());}
    }
}
