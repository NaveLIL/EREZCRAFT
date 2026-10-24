package pro.erez.industrycheck;

import com.google.gson.*;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Synthetic train assembly/movement/cold save on an explicit disposable dedicated world. */
@Mod("erez_industry_server_check")
@EventBusSubscriber(modid="erez_industry_server_check")
public final class IndustryServerCheck {
    private static final Path ROOT=Path.of("/data");
    private static final BlockPos STATION=new BlockPos(0,65,2);
    private static int ticks;
    private static boolean done,guarded,cold;
    private static UUID trainId;
    private static BlockPos start;
    private static final JsonObject report=new JsonObject();
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(done)return;
        var server=event.getServer();
        try{
            if(!guarded){
                require(Files.readString(ROOT.resolve(".owned-industry-check")).equals("disposable-loopback-only\n"),"Missing disposable marker");
                require(server.getWorldData().getLevelName().equals("owned-industry-check"),"Refuse production world");
                guarded=true;cold=Files.exists(ROOT.resolve("industry-train-created.json"));
                report.addProperty("owner_worlds_used",false);report.addProperty("synthetic_control",true);
                report.addProperty("cold_reload",cold);report.addProperty("dedicated",server.isDedicatedServer());
            }
            var level=server.overworld();++ticks;
            if(ticks==10){
                for(int x=-2;x<=7;x++)for(int z=-1;z<=1;z++)level.setChunkForced(x,z,true);
                require(Create.RAILWAYS.trains.isEmpty()||cold,"Unexpected train in fresh test world");
                var counts=new JsonObject();
                for(String mod:List.of("create_connected","sliceanddice","create_enchantment_industry","packagedauto","packageddraconic","pneumaticcraft","extended_industrialization","sfm","railways")){
                    long count=server.getRecipeManager().getRecipes().stream().filter(r->r.id().getNamespace().equals(mod)).count();
                    require(count>0,"No recipes for "+mod);counts.addProperty(mod,count);
                }
                report.add("recipe_counts",counts);
            }
            if(cold){
                if(ticks<120)return;
                var prior=JsonParser.parseString(Files.readString(ROOT.resolve("industry-train-created.json"))).getAsJsonObject();
                trainId=UUID.fromString(prior.get("train_id").getAsString());
                var train=Create.RAILWAYS.trains.get(trainId);require(train!=null,"Saved train missing after cold process");
                require(Create.RAILWAYS.trains.size()==1,"Train duplicated after cold process");
                require(!train.derailed && !train.invalid,"Saved train invalid");
                require(train.carriages.size()==1,"Saved carriage missing");
                var position=train.getPositionInDimension(level.dimension()).orElseThrow();
                var saved=new BlockPos(prior.get("saved_x").getAsInt(),prior.get("saved_y").getAsInt(),prior.get("saved_z").getAsInt());
                require(position.distSqr(saved)<=4,"Train moved unexpectedly across cold reload");
                report.addProperty("train_id",trainId.toString());report.addProperty("train_count",1);
                report.addProperty("position_retained",true);finish(true,null);return;
            }
            if(ticks==20){
                level.setDayTime(6000);
                for(int x=-20;x<=110;x++){
                    level.setBlockAndUpdate(new BlockPos(x,64,0),Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(x,65,0),property(block("create:track"),"shape","xo"));
                }
                level.setBlockAndUpdate(STATION,block("create:track_station"));
            }
            if(ticks==80){
                var station=(StationBlockEntity)level.getBlockEntity(STATION);
                require(station!=null,"Station block entity missing");
                var nbt=new CompoundTag();nbt.put("TargetTrack",NbtUtils.writeBlockPos(new BlockPos(0,0,-2)));
                nbt.putBoolean("TargetDirection",true);nbt.putBoolean("Ortho",true);
                station.edgePoint.read(nbt,level.registryAccess(),false);station.setChanged();
            }
            if(ticks==140){
                var station=(StationBlockEntity)level.getBlockEntity(STATION);
                require(station.edgePoint.hasValidTrack(),"Station track targeting failed");
                require(station.enterAssemblyMode(null),"Station could not enter assembly mode");
                var direction=station.getAssemblyDirection();
                var bogey=new BlockPos(0,65,0).relative(direction).above();
                level.setBlockAndUpdate(bogey,property(block("create:small_bogey"),"axis","x"));
                level.setBlockAndUpdate(bogey.above(),Blocks.OAK_PLANKS.defaultBlockState());
                var controls=bogey.above().relative(direction);
                level.setBlockAndUpdate(controls,property(block("create:controls"),"facing",direction.getName()));
                level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(bogey,controls)));
                report.addProperty("assembly_direction",direction.getName());
            }
            if(ticks==190){
                var station=(StationBlockEntity)level.getBlockEntity(STATION);
                station.assemble(UUID.fromString("12345678-1234-4234-9234-1234567890ab"));
                require(Create.RAILWAYS.trains.size()==1,"Train assembly did not produce one train");
                var train=Create.RAILWAYS.trains.values().iterator().next();trainId=train.id;
                require(train.carriages.size()==1 && !train.derailed,"Assembled train invalid");
                station.exitAssemblyMode();start=train.getPositionInDimension(level.dimension()).orElseThrow();
                report.addProperty("train_assembled",true);report.addProperty("train_id",trainId.toString());
            }
            if(ticks>=200 && ticks<340){
                var train=Create.RAILWAYS.trains.get(trainId);require(train!=null,"Moving train missing");
                train.manualTick=true;train.targetSpeed=.25;train.speed=.25;train.throttle=1;
            }
            if(ticks==341){
                var train=Create.RAILWAYS.trains.get(trainId);train.speed=0;train.targetSpeed=0;train.throttle=0;
                var position=train.getPositionInDimension(level.dimension()).orElseThrow();
                double distance=Math.sqrt(position.distSqr(start));
                require(distance>=15,"Train did not travel at least 15 blocks: "+distance);
                require(!train.derailed && !train.invalid,"Train derailed during movement");
                report.addProperty("travelled_blocks",distance);report.addProperty("carriages",train.carriages.size());
                report.addProperty("saved_x",position.getX());report.addProperty("saved_y",position.getY());report.addProperty("saved_z",position.getZ());
                require(server.saveEverything(true,true,true),"Server save failed");
                Files.writeString(ROOT.resolve("industry-train-created.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
                finish(true,null);
            }
            require(ticks<600,"Train fixture timeout");
        }catch(Throwable error){finish(false,error);}
    }
    private static BlockState block(String id){
        var key=ResourceLocation.parse(id);require(BuiltInRegistries.BLOCK.containsKey(key),"Missing block "+id);
        return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static BlockState property(BlockState state,String name,String value){
        for(var property:state.getProperties())if(property.getName().equals(name)){
            var p=(Property)property;var parsed=p.getValue(value);require(parsed.isPresent(),"Invalid property "+name+"="+value);
            return state.setValue(p,(Comparable)parsed.get());
        }
        throw new IllegalStateException("Missing property "+name);
    }
    private static void finish(boolean passed,Throwable error){
        done=true;report.addProperty("passed",passed);report.addProperty("ticks",ticks);
        if(error!=null){report.addProperty("error_class",error.getClass().getName());report.addProperty("error",error.getMessage());}
        try{Files.writeString(ROOT.resolve(cold?"industry-train-cold.json":"industry-train-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception failure){throw new IllegalStateException("Cannot write owned probe receipt",failure);}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
