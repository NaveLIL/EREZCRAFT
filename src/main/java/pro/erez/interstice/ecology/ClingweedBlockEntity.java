package pro.erez.interstice.ecology;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Eight single-item pockets, serialized through Minecraft's component-aware ItemStack codec. */
public final class ClingweedBlockEntity extends BlockEntity {
    public static final int CAPACITY = 8;
    private NonNullList<ItemStack> stash = NonNullList.withSize(CAPACITY, ItemStack.EMPTY);
    private long destructionTick = Long.MIN_VALUE;
    public ClingweedBlockEntity(BlockPos pos, BlockState state) { super(CaveEcology.CLINGWEED_ENTITY.get(), pos, state); }
    public int freePocket() {
        for (int i = 0; i < CAPACITY; i++) if (stash.get(i).isEmpty()) return i;
        return -1;
    }
    public boolean keepOne(ItemStack source) {
        int pocket = freePocket();
        if (source.isEmpty() || source.getCount() != 1 || pocket < 0 || !(level instanceof ServerLevel)) return false;
        stash.set(pocket, source.copy());
        setChanged();
        return true;
    }
    /** Copies only: external callers cannot mutate or retrieve the stored inventory. */
    public List<ItemStack> storedItems() { return stash.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList(); }
    public void armDestruction(long tick) { destructionTick = tick; }
    public boolean gasArmed(long tick) { return destructionTick == tick; }
    public void release(ServerLevel server) {
        for (int i = 0; i < CAPACITY; i++) {
            // Remove before spawning: a second callback cannot duplicate a pocket.
            ItemStack item = stash.set(i, ItemStack.EMPTY);
            if (!item.isEmpty()) Containers.dropItemStack(server, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, item);
        }
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        ContainerHelper.saveAllItems(tag, stash, lookup);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        stash = NonNullList.withSize(CAPACITY, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, stash, lookup);
        destructionTick = Long.MIN_VALUE;
    }
}
