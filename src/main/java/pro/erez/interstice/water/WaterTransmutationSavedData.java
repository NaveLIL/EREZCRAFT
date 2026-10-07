package pro.erez.interstice.water;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-side persistent storage of active water-to-toxin transmutation sites.
 * Ensures that if the world is saved/reloaded, any unstable light toxin placed
 * still accurately evaporates after its remaining duration.
 */
public final class WaterTransmutationSavedData extends SavedData {
    public static final String FILE_ID = "interstice_water_transmutation";
    private final Map<BlockPos, Long> expiringBlocks = new ConcurrentHashMap<>();

    public WaterTransmutationSavedData() {}

    public Map<BlockPos, Long> getExpiringBlocks() {
        return expiringBlocks;
    }

    public void addExpiring(BlockPos pos, long expireTime) {
        expiringBlocks.put(pos.immutable(), expireTime);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (expiringBlocks.remove(pos) != null) {
            setDirty();
        }
    }

    public boolean isEmpty() {
        return expiringBlocks.isEmpty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Long> entry : expiringBlocks.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putInt("x", entry.getKey().getX());
            item.putInt("y", entry.getKey().getY());
            item.putInt("z", entry.getKey().getZ());
            item.putLong("expire", entry.getValue());
            list.add(item);
        }
        tag.put("Expiring", list);
        return tag;
    }

    public static WaterTransmutationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WaterTransmutationSavedData data = new WaterTransmutationSavedData();
        ListTag list = tag.getList("Expiring", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            BlockPos pos = new BlockPos(item.getInt("x"), item.getInt("y"), item.getInt("z"));
            long expire = item.getLong("expire");
            data.expiringBlocks.put(pos, expire);
        }
        return data;
    }

    public static SavedData.Factory<WaterTransmutationSavedData> factory() {
        return new SavedData.Factory<>(
                WaterTransmutationSavedData::new,
                WaterTransmutationSavedData::load,
                null
        );
    }

    public static WaterTransmutationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }
}
