package pro.erez.interstice.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.worldgen.*;

/** Disposable Creative inspection of natural groves, real resource reload, vines and cold restart. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class GardenVisualSmoke {
    private static final String MODE=System.getProperty("interstice.gardenSmoke","");
    private static final String WORLD="natural-pale-garden-check";
    private static final com.google.gson.Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static boolean started,finished;
    private static long deadline;
    private static int stage,ticks;
    private static CompletableFuture<?> work;
    private static JsonObject result=new JsonObject();
    private static List<String> originalPacks;
    private static String woodSnapshot;
    private static java.util.Map<BlockPos,net.minecraft.world.level.block.state.BlockState> originalPlan;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(!started&&mc.screen instanceof TitleScreen){
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(7);
                mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);mc.options.framerateLimit().set(60);
                if(MODE.equals("create")){
                    require(!Files.exists(world(mc).resolve("level.dat")),"Disposable world already exists");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Disposable alien grove inspection",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261006L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                }else{
                    require(MODE.equals("reload"),"Unknown garden smoke mode");result=JsonParser.parseString(Files.readString(report(mc,"create"))).getAsJsonObject();require(result.get("passed").getAsBoolean(),"Creation failed");
                    require(result.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Cold reload must use another JVM");prepareOldBiomeFixture(mc);
                    mc.createWorldOpenFlows().openWorld(WORLD,()->{});
                }return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;
            require(System.nanoTime()<deadline,"Garden check timed out at stage "+stage);
            if(mc.player==null||mc.level==null||mc.screen!=null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==0){
                if(MODE.equals("create")){mc.getConnection().sendCommand("interstice explore");stage=1;}
                else{work=server.submit(()->{checkReload(server.getLevel(IslandWorld.TALL_WORLD));return true;});stage=12;}ticks=0;return;
            }
            if(!mc.level.dimension().equals(IslandWorld.TALL_WORLD))return;
            if(stage==1&&++ticks>=40){work=server.submit(()->{result=findGrove(server.getLevel(IslandWorld.TALL_WORLD),server.getPlayerList().getPlayer(uuid));return true;});stage=2;ticks=0;}
            else if(stage==2&&done()){stage=3;ticks=0;}
            else if(stage==3&&++ticks>=100){shot(mc,"garden-natural-darkness.png");server.execute(()->server.getPlayerList().getPlayer(uuid).addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false)));stage=4;ticks=0;}
            else if(stage==4&&++ticks>=70){
                shot(mc,"garden-natural-grove.png");recordCamera(mc,"grove_camera");
                work=server.submit(()->{var player=server.getPlayerList().getPlayer(uuid);var level=player.serverLevel();var roof=point("canopy");
                    camera(player,level,roof.getX()+.5,result.get("floor_y").getAsInt()+1.05,roof.getZ()+.5,roof.getX()+.5,roof.getY()+.5,roof.getZ()+.5,false);
                    require(ShelterDetector.isSheltered(level,player),"Natural canopy does not provide physical shelter");result.addProperty("sheltered_beneath_natural_canopy",true);return true;});stage=5;ticks=0;
            }else if(stage==5&&done()){stage=6;ticks=0;}
            else if(stage==6&&++ticks>=70){
                shot(mc,"garden-canopy-and-vines.png");recordCamera(mc,"canopy_camera");
                work=server.submit(()->{
                    var level=server.getLevel(IslandWorld.TALL_WORLD);var vine=point("vine");var below=vine.below();
                    require(level.getBlockState(vine).is(GardenMaterials.PALE_VINE.get())&&level.getBlockState(below).is(GardenMaterials.PALE_VINE.get()),"No real multi-block chain selected");
                    server.getPlayerList().getPlayer(uuid).gameMode.destroyBlock(vine);
                    require(level.getBlockState(vine).isAir()&&level.getBlockState(below).isAir(),"Broken vine left unsupported lower segments");
                    result.addProperty("broken_vine_and_lower_segment_removed",true);
                    var chunk=level.getChunk(result.get("chunk_x").getAsInt(),result.get("chunk_z").getAsInt());woodSnapshot=woodSnapshot(chunk);
                    var definition=GardenTreeDefinitions.get(GardenTreeDefinitions.PALEHEART);var root=new BlockPos(7,100,7);
                    originalPlan=GardenTrees.plan(level,p->p.equals(root.below())?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),root,definition.variants().get(0),RandomSource.create(19));
                    return true;
                });stage=7;ticks=0;
            }else if(stage==7&&done()){
                writeProbePack(mc);originalPacks=new ArrayList<>(server.getPackRepository().getSelectedIds());server.getPackRepository().reload();
                var selected=new ArrayList<>(originalPacks);selected.add("file/garden-tree-probe");
                work=server.reloadResources(selected);stage=8;
            }else if(stage==8&&done()){
                work=server.submit(()->{
                    var level=server.getLevel(IslandWorld.TALL_WORLD);var definition=GardenTreeDefinitions.get(GardenTreeDefinitions.PALEHEART);
                    require(definition.attempts()==1,"Actual datapack reload did not replace the tree catalog");var root=new BlockPos(7,100,7);
                    var changed=GardenTrees.plan(level,p->p.equals(root.below())?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),root,definition.variants().get(0),RandomSource.create(19));
                    require(!changed.isEmpty()&&!changed.equals(originalPlan),"Reloaded branch path did not change the future tree shape");
                    require(woodSnapshot(level.getChunk(result.get("chunk_x").getAsInt(),result.get("chunk_z").getAsInt())).equals(woodSnapshot),"Reload rebuilt an existing tree");
                    result.addProperty("real_datapack_reload_changes_future_shapes",true);result.addProperty("existing_tree_logs_unchanged_by_reload",true);return true;
                });stage=9;
            }else if(stage==9&&done()){work=server.reloadResources(originalPacks);stage=10;}
            else if(stage==10&&done()){
                require(GardenTreeDefinitions.get(GardenTreeDefinitions.PALEHEART).attempts()==3,"Default catalog did not restore");result.addProperty("default_catalog_restored",true);stage=11;ticks=0;
            }else if(stage==11&&++ticks>=50){finish(mc,true,"Natural grove, canopy shelter, vine break and real catalog reload passed");}
            else if(stage==12&&done()){stage=13;ticks=0;}
            else if(stage==13&&++ticks>=100){shot(mc,"garden-cold-reloaded.png");stage=14;ticks=0;}
            else if(stage==14&&++ticks>=40)finish(mc,true,"Cold restart preserved old chunks and the broken vine; the previous two-biome source upgraded");
        }catch(Throwable error){error.printStackTrace();finish(mc,false,error.toString());}
    }
    private static boolean done(){if(!work.isDone())return false;work.join();return true;}
    private static JsonObject findGrove(ServerLevel level,ServerPlayer player){
        var g=(IslandChunkGenerator)level.getChunkSource().getGenerator();var profile=g.geometry();var sampler=level.getChunkSource().randomState().sampler();
        int loaded=0;
        for(int radius=0;radius<=24;radius++)for(int cx=-radius;cx<=radius;cx++)for(int cz=-radius;cz<=radius;cz++){
            require(System.nanoTime()<deadline,"Natural grove search timeout");
            if(Math.max(Math.abs(cx),Math.abs(cz))!=radius||!g.getBiomeSource().getNoiseBiome(cx*4+2,25,cz*4+2,sampler).is(RealmBiomes.PALE_GARDENS))continue;
            var center=g.getBaseColumn(cx*16+7,cz*16+7,level,level.getChunkSource().randomState());boolean land=false;
            for(int y=profile.minLand();y<=profile.maxLand();y++)if(StoneVaults.isGround(center.getBlock(y))){land=true;break;}if(!land)continue;
            var chunk=level.getChunk(cx,cz);loaded++;var leaves=new ArrayList<BlockPos>();var vines=new ArrayList<BlockPos>();int logs=0,turf=0,rock=0;double gap=Double.POSITIVE_INFINITY;
            for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++)for(int y=profile.minLand();y<=profile.maxLand();y++){
                var p=new BlockPos(x,y,z);var s=chunk.getBlockState(p);
                if(s.is(GardenMaterials.PALEHEART_LOG.get()))logs++;
                if(s.is(GardenMaterials.PALEHEART_LEAVES.get()))leaves.add(p);
                if(s.is(GardenMaterials.PALE_VINE.get()))vines.add(p);
                if(s.is(Interstice.ABYSSAL_TURF.get()))turf++;
                if(s.is(GardenMaterials.PALESTONE.get()))rock++;
                if(s.is(GardenMaterials.PALEHEART_LOG.get())||s.is(GardenMaterials.PALEHEART_LEAVES.get())||s.is(GardenMaterials.PALE_VINE.get())){
                    require(IslandChunkGenerator.landAllowed(profile,x,y,z),"Tree/vine breaches sea clearance");gap=Math.min(gap,SeaSurface.cellMinimum(profile,x,z,true)-(y+1));
                }
            }
            if(logs<15||leaves.size()<30||turf<50)continue;
            var vine=vines.stream().filter(p->chunk.getBlockState(p.below()).is(GardenMaterials.PALE_VINE.get())).findFirst().orElse(null);if(vine==null)continue;
            BlockPos roof=null;int floor=-1;
            for(var p:leaves){
                if(!chunk.getBlockState(p.below()).isAir()||!chunk.getBlockState(p.below(2)).isAir())continue;
                for(int y=p.getY()-3;y>=Math.max(profile.minLand(),p.getY()-16);y--){var state=chunk.getBlockState(p.atY(y));if(state.isAir())continue;if(StoneVaults.isGround(state)){roof=p;floor=y;}break;}
                if(roof!=null)break;
            }
            if(roof==null)continue;
            JsonObject found=new JsonObject();found.addProperty("creator_pid",ProcessHandle.current().pid());found.addProperty("naturally_generated",true);
            found.addProperty("scene_type","disposable Creative natural generation and persistence inspection; not Survival");found.addProperty("seed",level.getSeed());
            found.addProperty("chunk_x",cx);found.addProperty("chunk_z",cz);found.addProperty("loaded_candidates",loaded);found.addProperty("pale_logs",logs);
            found.addProperty("pale_leaves",leaves.size());found.addProperty("vine_blocks",vines.size());found.addProperty("original_grey_turf",turf);found.addProperty("buried_pale_stone",rock);
            found.addProperty("minimum_upper_sea_gap",gap);found.addProperty("required_clearance",profile.clearance());found.addProperty("floor_y",floor);
            putPoint(found,"canopy",roof);putPoint(found,"vine",vine);
            found.addProperty("biome_key",level.getBiome(roof).unwrapKey().orElseThrow().location().toString());
            player.removeEffect(MobEffects.NIGHT_VISION);
            panorama(player,level,roof,floor);
            return found;
        }throw new IllegalStateException("No natural grove with live canopy and hanging vines");
    }
    private static void panorama(ServerPlayer p,ServerLevel l,BlockPos focus,int floor){
        var profile=((IslandChunkGenerator)l.getChunkSource().getGenerator()).geometry();
        for(int elevation=0;elevation<4;elevation++)for(int[] offset:new int[][]{{12,-12},{-12,-12},{12,12},{-12,12}}){
            double x=focus.getX()+offset[0]+.5,y=floor+6.5+elevation,z=focus.getZ()+offset[1]+.5;
            if(y+2>=SeaSurface.cellMinimum(profile,(int)x,(int)z,true)-1||!l.getBlockState(BlockPos.containing(x,y,z)).isAir()||!l.getBlockState(BlockPos.containing(x,y+1,z)).isAir())continue;
            camera(p,l,x,y,z,focus.getX()+.5,floor+4,focus.getZ()+.5,true);return;
        }throw new IllegalStateException("No clear natural grove camera");
    }
    private static void writeProbePack(Minecraft mc)throws Exception{
        var path=world(mc).resolve("datapacks/garden-tree-probe");Files.createDirectories(path.resolve("data/interstice/interstice_trees"));
        Files.writeString(path.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":48,\"description\":\"Disposable tree catalog reload probe\"}}");
        try(var in=GardenVisualSmoke.class.getClassLoader().getResourceAsStream("data/interstice/interstice_trees/paleheart.json")){
            var data=JsonParser.parseString(new String(java.util.Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();data.addProperty("attempts",1);
            var v=data.getAsJsonArray("variants").get(0).getAsJsonObject();v.addProperty("branch_path","U"+v.get("branch_path").getAsString());
            Files.writeString(path.resolve("data/interstice/interstice_trees/paleheart.json"),JSON.toJson(data));
        }
    }
    private static void checkReload(ServerLevel level){
        var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();require(generator.getBiomeSource().possibleBiomes().stream().anyMatch(b->b.is(RealmBiomes.PALE_GARDENS)),"Old M13 source did not upgrade to gardens");
        var vine=point("vine");level.getChunk(vine);require(level.getBlockState(vine).isAir()&&level.getBlockState(vine.below()).isAir(),"Broken vine regenerated on cold restart");
        require(level.getBiome(point("canopy")).unwrapKey().orElseThrow().location().toString().equals(result.get("biome_key").getAsString()),"Saved biome palette changed");
        result.addProperty("broken_vine_preserved_after_restart",true);result.addProperty("old_two_biome_source_upgraded",true);result.addProperty("saved_biome_palette_unchanged",true);result.addProperty("reload_pid",ProcessHandle.current().pid());
    }
    private static void prepareOldBiomeFixture(Minecraft mc)throws Exception{
        Path dat=world(mc).resolve("level.dat");require(dat.toRealPath().getParent().equals(world(mc).toRealPath()),"Wrong disposable save path");
        var saved=NbtIo.readCompressed(dat,NbtAccounter.unlimitedHeap());var before=saved.copy();
        var generator=saved.getCompound("Data").getCompound("WorldGenSettings").getCompound("dimensions").getCompound("interstice:islands_tall").getCompound("generator");
        require(generator.getString("type").equals("interstice:coupled_islands"),"Wrong saved generator");var sourceBefore=generator.getCompound("biome_source").copy();
        var source=new CompoundTag();source.putString("type","minecraft:multi_noise");var entries=new ListTag();
        for(int i=0;i<2;i++){
            var entry=new CompoundTag();entry.putString("biome",i==0?"interstice:ash_islands":"interstice:stone_vaults");var parameters=new CompoundTag();
            for(String name:List.of("temperature","humidity","erosion","depth","weirdness"))parameters.put(name,interval(-2,2));
            parameters.put("continentalness",i==0?interval(-2,.08F):interval(.08F,2));parameters.putFloat("offset",0);entry.put("parameters",parameters);entries.add(entry);
        }source.put("biomes",entries);generator.put("biome_source",source);
        var restored=saved.copy();restored.getCompound("Data").getCompound("WorldGenSettings").getCompound("dimensions").getCompound("interstice:islands_tall").getCompound("generator").put("biome_source",sourceBefore);
        require(restored.equals(before),"Old-layout fixture changed other saved fields");Files.copy(dat,world(mc).resolve("level.before-m13-source.dat"));NbtIo.writeCompressed(saved,dat);
        result.addProperty("old_layout_fixture_scope","Only the disposable world's source table was replaced by the exact M13 table; not a blanket test of all older worlds");
    }
    private static ListTag interval(float a,float b){var list=new ListTag();list.add(FloatTag.valueOf(a));list.add(FloatTag.valueOf(b));return list;}
    private static String woodSnapshot(net.minecraft.world.level.chunk.ChunkAccess chunk){
        StringBuilder s=new StringBuilder();for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)for(int y=41;y<=205;y++){
            var p=new BlockPos(x,y,z);var state=chunk.getBlockState(p);if(state.is(GardenMaterials.PALEHEART_LOG.get()))s.append(p).append(state);
        }return s.toString();
    }
    private static void camera(ServerPlayer p,ServerLevel l,double x,double y,double z,double tx,double ty,double tz,boolean flying){
        double dx=tx-x,dz=tz-z,dy=ty-(y+p.getEyeHeight());p.teleportTo(l,x,y,z,Set.of(),(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));p.setDeltaMovement(0,0,0);p.getAbilities().flying=flying;p.onUpdateAbilities();
    }
    private static void putPoint(JsonObject d,String key,BlockPos p){d.addProperty(key+"_x",p.getX());d.addProperty(key+"_y",p.getY());d.addProperty(key+"_z",p.getZ());}
    private static BlockPos point(String key){return new BlockPos(result.get(key+"_x").getAsInt(),result.get(key+"_y").getAsInt(),result.get(key+"_z").getAsInt());}
    private static Path world(Minecraft mc){return mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);}
    private static Path report(Minecraft mc,String mode){return mc.gameDirectory.toPath().resolve("garden-"+mode+"-validation.json");}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),msg->System.out.println("GARDEN_SCREENSHOT "+name));}
    private static void recordCamera(Minecraft mc,String name){var d=new JsonObject();d.addProperty("x",mc.player.getX());d.addProperty("y",mc.player.getY());d.addProperty("z",mc.player.getZ());d.addProperty("yaw",mc.player.getYRot());d.addProperty("pitch",mc.player.getXRot());result.add(name,d);}
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalStateException(reason);}
    private static void finish(Minecraft mc,boolean passed,String why){
        if(finished)return;finished=true;result.addProperty("passed",passed);result.addProperty("mode",MODE);result.addProperty("stage",stage);result.addProperty("reason",why);
        try{Files.writeString(report(mc,MODE),JSON.toJson(result));}catch(Exception error){error.printStackTrace();}System.out.println("GARDEN_VALIDATION "+JSON.toJson(result));mc.stop();
    }
}
