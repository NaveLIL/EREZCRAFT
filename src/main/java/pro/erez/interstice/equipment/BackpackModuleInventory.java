package pro.erez.interstice.equipment;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class BackpackModuleInventory extends SimpleContainer {
    private final Player player;
    private final ItemStack source;
    private final UUID id;
    public final int sourceSlot;
    private boolean loading;

    public BackpackModuleInventory(Player player, int slot, int moduleSlots) {
        super(moduleSlots);
        this.player = player;
        this.source = BackpackStorage.stack(player, slot);
        this.sourceSlot = slot;
        this.id = BackpackStorage.ensureId(source);
        loading = true;
        var modules = BackpackStorage.readModules(source);
        for (int i = 0; i < Math.min(modules.size(), moduleSlots); i++) {
            super.setItem(i, modules.get(i));
        }
        loading = false;
    }

    public boolean bound() {
        return player.isAlive() && BackpackStorage.stack(player, sourceSlot) == source
                && BackpackStorage.isPack(source) && id.equals(BackpackStorage.id(source));
    }

    public ItemStack source() {
        return source;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return BackpackStorage.isModule(stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (!loading && player != null && bound()) {
            var items = new ArrayList<ItemStack>();
            for (int i = 0; i < getContainerSize(); i++) {
                items.add(getItem(i).copy());
            }
            BackpackStorage.writeModules(source, items);
            player.getInventory().setChanged();
            if (sourceSlot == BackpackHarness.SLOT) {
                BackpackHarness.changed(player);
            }
        }
    }
}
