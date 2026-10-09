package pro.erez.interstice.fauna;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.food.CrownFruitBlock;
import pro.erez.interstice.worldgen.GardenMaterials;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

/** Dormant until explicitly registered by the accepted V6 integration. No legacy-world spawning. */
public final class RealmFauna {
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE,Interstice.ID);
    public static final DeferredHolder<EntityType<?>,EntityType<CanopySentinel>> CANOPY_SENTINEL=ENTITIES.register("canopy_sentinel",()->
        EntityType.Builder.<CanopySentinel>of(CanopySentinel::new,MobCategory.CREATURE).sized(.58F,.72F).clientTrackingRange(8).updateInterval(2).build("interstice:canopy_sentinel"));
    public static final int MAX_QUEUED_CHUNKS=64,LOCAL_CAP=2,LOCAL_RADIUS=48;
    private static final Map<ServerLevel,LinkedHashMap<Long,PendingChunk>> QUEUE=new WeakHashMap<>();
    private record PendingChunk(long firstTick,long dueTick) {}
    private RealmFauna() {}
    /** Root calls this once only after geography and the isolated fauna checks are accepted. */
    public static void register(IEventBus modBus){
        ENTITIES.register(modBus);modBus.addListener(RealmFauna::createAttributes);
        NeoForge.EVENT_BUS.addListener(RealmFauna::onChunkLoad);NeoForge.EVENT_BUS.addListener(RealmFauna::serverTick);
    }
    public static void createAttributes(EntityAttributeCreationEvent event){event.put(CANOPY_SENTINEL.get(),CanopySentinel.createAttributes().build());}
    public static boolean isV6(ServerLevel level){return level.getChunkSource().getGenerator() instanceof IslandChunkGenerator generator&&generator.terrainRevision()==6;}
    private static long mix(long x){x=(x^(x>>>30))*0xBF58476D1CE4E5B9L;x=(x^(x>>>27))*0x94D049BB133111EBL;return x^(x>>>31);}
    private static boolean eligibleChunk(ServerLevel level,ChunkPos chunk){return Math.floorMod(mix(level.getSeed()^chunk.toLong()^0x43414E4F50594CL),4)==0;}
    /** Called for a completed V6 forest chunk; queues at most64 loaded candidates, never loads terrain. */
    public static void scheduleNaturalSpawn(ServerLevel level,ChunkPos chunk){
        if(!level.getServer().isSameThread()){level.getServer().execute(()->scheduleNaturalSpawn(level,chunk));return;}
        if(!isV6(level)||!eligibleChunk(level,chunk)||PopulationData.get(level).inhabited(chunk.toLong()))return;
        var queue=QUEUE.computeIfAbsent(level,key->new LinkedHashMap<>());
        if(queue.size()>=MAX_QUEUED_CHUNKS||queue.containsKey(chunk.toLong()))return;
        long now=level.getGameTime();queue.put(chunk.toLong(),new PendingChunk(now,now+40));
    }
    public static void onChunkLoad(ChunkEvent.Load event){
        if(event.getLevel() instanceof ServerLevel level&&isV6(level))scheduleNaturalSpawn(level,event.getChunk().getPos());
    }
    /** A bounded, real FULL/loaded-chunk inspection; no getChunk() call can generate new terrain. */
    public static boolean spawnInLoadedCrown(ServerLevel level,ChunkPos position){
        if(!isV6(level)||!eligibleChunk(level,position)||PopulationData.get(level).inhabited(position.toLong()))return false;
        LevelChunk chunk=level.getChunkSource().getChunkNow(position.x,position.z);if(chunk==null)return false;
        var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();
        BlockPos selected=null;int leaves=0,fruits=0;
        // One complete16x16 column volume per eligible work item; one item each20 server ticks.
        for(int y=generator.geometry().lowerSeaTop()+1;y<=generator.geometry().maxLand();y++)for(int x=position.getMinBlockX();x<=position.getMaxBlockX();x++)for(int z=position.getMinBlockZ();z<=position.getMaxBlockZ();z++){
            var p=new BlockPos(x,y,z);var state=chunk.getBlockState(p);
            if(state.is(GardenMaterials.CROWN_LEAVES.get())&&state.getValue(LeavesBlock.DISTANCE)<7)leaves++;
            if(!state.is(GardenMaterials.CROWN_FRUIT.get()))continue;fruits++;
            if(selected!=null)continue;
            for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                var air=p.offset(direction[0],0,direction[1]);
                if((air.getX()>>4)!=position.x||(air.getZ()>>4)!=position.z)continue;
                var support=chunk.getBlockState(air.below());
                if(!support.is(GardenMaterials.CROWN_LEAVES.get())||support.getValue(LeavesBlock.DISTANCE)>=7||!chunk.getBlockState(air).isAir()||!chunk.getBlockState(air.above()).isAir())continue;
                selected=air.immutable();break;
            }
        }
        if(selected==null||leaves<16||fruits<1)return false;
        var area=new AABB(selected).inflate(LOCAL_RADIUS);
        if(level.getEntitiesOfClass(CanopySentinel.class,area,e->e.isAlive()&&!e.isRemoved()).size()>=LOCAL_CAP)return false;
        var entity=CANOPY_SENTINEL.get().create(level);if(entity==null)return false;
        entity.moveTo(selected.getX()+.5,selected.getY(),selected.getZ()+.5,(float)Math.floorMod(mix(position.toLong()),360),0);
        entity.initializeHome(selected);
        if(!entity.isCanopyResident()||!EventHooks.checkSpawnPosition(entity,level,MobSpawnType.NATURAL))return false;
        EventHooks.finalizeMobSpawn(entity,level,level.getCurrentDifficultyAt(selected),MobSpawnType.NATURAL,null);
        if(!level.noCollision(entity)||!level.addFreshEntity(entity))return false;
        PopulationData.get(level).mark(position.toLong());return true;
    }
    public static void serverTick(ServerTickEvent.Post event){
        var server=event.getServer();
        for(var level:server.getAllLevels()){
            var queue=QUEUE.get(level);if(queue==null||queue.isEmpty()||level.getGameTime()%20!=0)continue;
            var iterator=queue.entrySet().iterator();var entry=iterator.next();long now=level.getGameTime();
            if(now<entry.getValue().dueTick)continue;
            var position=new ChunkPos(entry.getKey());
            if(!isV6(level)||PopulationData.get(level).inhabited(entry.getKey())||now-entry.getValue().firstTick>240){iterator.remove();continue;}
            if(level.getChunkSource().getChunkNow(position.x,position.z)==null)continue;
            iterator.remove();spawnInLoadedCrown(level,position);
        }
    }
    /** Root places this exact call after successful AGE3 -> AGE0 use and fruit delivery.
     * Pre-interaction events cannot attribute simultaneous harvests reliably. */
    public static void notifyFruitHarvest(Player actor,BlockPos fruit){
        if(!(actor instanceof ServerPlayer player)||!isV6(player.serverLevel())||player.isCreative()||player.isSpectator()||!player.serverLevel().hasChunkAt(fruit))return;
        var state=player.serverLevel().getBlockState(fruit);
        if(!state.is(GardenMaterials.CROWN_FRUIT.get())||state.getValue(CrownFruitBlock.AGE)!=0)return;
        for(var resident:player.serverLevel().getEntitiesOfClass(CanopySentinel.class,new AABB(fruit).inflate(20)))resident.observeFruitHarvest(player,fruit);
    }
    /** Marks only successful natural additions. Reloading/felling/killing never repopulates a marked chunk. */
    public static final class PopulationData extends SavedData {
        private final Set<Long> inhabited=new HashSet<>();
        public boolean inhabited(long chunk){return inhabited.contains(chunk);}
        public void mark(long chunk){if(inhabited.add(chunk))setDirty();}
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){tag.putInt("Version",1);tag.putLongArray("InhabitedCrownChunks",inhabited.stream().mapToLong(Long::longValue).sorted().toArray());return tag;}
        public static PopulationData load(CompoundTag tag,HolderLookup.Provider registries){var data=new PopulationData();for(long chunk:tag.getLongArray("InhabitedCrownChunks"))data.inhabited.add(chunk);return data;}
        public static PopulationData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PopulationData::new,PopulationData::load,null),"interstice_canopy_population_v6");}
    }
}
