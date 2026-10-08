package pro.erez.interstice.minerals;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension cycle locks survive unloaded roots and a cold restart. No chunks are loaded. */
public final class SproutHarvestData extends SavedData {
    private final Map<Long,Long> harvested=new HashMap<>();
    public static SproutHarvestData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(SproutHarvestData::new,SproutHarvestData::load,null),"interstice_sprout_harvests");}
    public Long cycle(BlockPos root){return harvested.get(root.asLong());}
    public void mark(BlockPos root,long cycle){harvested.put(root.asLong(),cycle);setDirty();}
    public void clear(BlockPos root){if(harvested.remove(root.asLong())!=null)setDirty();}
    public boolean locked(BlockPos root,long cycle){return Long.valueOf(cycle).equals(harvested.get(root.asLong()));}
    public boolean older(BlockPos root,long cycle){Long old=harvested.get(root.asLong());return old!=null&&old<cycle;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var entries=new ListTag();harvested.forEach((pos,cycle)->{var value=new CompoundTag();value.putLong("Pos",pos);value.putLong("Cycle",cycle);entries.add(value);});tag.put("Roots",entries);return tag;
    }
    public static SproutHarvestData load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new SproutHarvestData();var list=tag.getList("Roots",10);for(int i=0;i<list.size();i++){var entry=list.getCompound(i);data.harvested.put(entry.getLong("Pos"),Math.max(0,entry.getLong("Cycle")));}return data;
    }
}
