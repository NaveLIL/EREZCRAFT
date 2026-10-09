package pro.erez.interstice.equipment;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class BackpackMenu extends AbstractContainerMenu {
    public static final int MODE = 0, SORT = 1, STASH = 2, REFILL = 3, TOGGLE_FEEDER = 4;
    public final int sourceSlot, capacity, columns, rows, width, moduleSlots;
    public final Container container;
    public final Container moduleContainer;
    public final ContainerData data;
    private final Inventory playerInventory;

    public BackpackMenu(int id, Inventory player, RegistryFriendlyByteBuf buffer) {
        this(id, player, buffer.readVarInt(), buffer.readVarInt(), null);
    }

    public BackpackMenu(int id, Inventory player, int slot) {
        this(id, player, slot, BackpackStorage.capacity(BackpackStorage.stack(player.player, slot)),
                new BackpackInventory(player.player, slot));
    }

    private BackpackMenu(int id, Inventory player, int slot, int capacity, BackpackInventory live) {
        super(ExpeditionEquipment.PACK_MENU.get(), id);
        if (capacity != 54 && capacity != 72 && capacity != 84) {
            throw new IllegalArgumentException("Unsupported pack size: " + capacity);
        }
        if (slot < 0 || slot >= 36 && slot != 40 && slot != BackpackHarness.SLOT) {
            throw new IllegalArgumentException("Unsupported pack source");
        }
        this.sourceSlot = slot;
        this.capacity = capacity;
        this.columns = (capacity == 54) ? 9 : 12;
        this.rows = capacity / columns;
        this.width = columns * 18 + 16;
        this.moduleSlots = BackpackStorage.moduleSlots(capacity);
        this.playerInventory = player;
        this.container = live == null ? new SimpleContainer(capacity) : live;
        this.moduleContainer = live == null ? new SimpleContainer(moduleSlots) : new BackpackModuleInventory(player.player, slot, moduleSlots);

        this.data = live == null ? new SimpleContainerData(2) : new ContainerData() {
            @Override public int get(int i) {
                if (!live.bound() || !BackpackStorage.valid(live.source())) return 0;
                return i == 0 ? BackpackStorage.mode(live.source())
                        : (int) BackpackStorage.read(live.source()).stream().filter(s -> !s.isEmpty()).count();
            }
            @Override public void set(int i, int value) {}
            @Override public int getCount() { return 2; }
        };

        // Storage slots (0 .. capacity - 1)
        for (int i = 0; i < capacity; i++) {
            addSlot(new Slot(container, i, 8 + (i % columns) * 18, 16 + (i / columns) * 18) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return BackpackStorage.allowed(stack);
                }
            });
        }

        // Player Inventory (capacity .. capacity + 35)
        int left = (width - 162) / 2;
        int invY = (rows == 7) ? 154 : 136;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addPlayerSlot(player, col + row * 9 + 9, left + col * 18, invY + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            addPlayerSlot(player, col, left + col * 18, invY + 58);
        }

        // Module Sockets (capacity + 36 .. capacity + 36 + moduleSlots - 1)
        for (int i = 0; i < moduleSlots; i++) {
            addSlot(new BackpackModuleSlot(moduleContainer, i, -20, 20 + i * 22));
        }

        addDataSlots(data);
    }

    private void addPlayerSlot(Inventory inventory, int i, int x, int y) {
        addSlot(new Slot(inventory, i, x, y) {
            @Override public boolean mayPickup(Player p) { return i != sourceSlot; }
            @Override public boolean mayPlace(ItemStack stack) { return i != sourceSlot; }
        });
    }

    @Override
    public boolean stillValid(Player player) {
        boolean liveBound = !(container instanceof BackpackInventory live) || live.bound();
        boolean modBound = !(moduleContainer instanceof BackpackModuleInventory modLive) || modLive.bound();
        return liveBound && modBound;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (type == ClickType.SWAP && (button == sourceSlot || sourceSlot == 40 && button == 40)) return;
        super.clicked(slot, button, type, player);
        if (container instanceof BackpackInventory live && live.bound()) live.setChanged();
        if (moduleContainer instanceof BackpackModuleInventory modLive && modLive.bound()) modLive.setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.mayPickup(player) || !slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var copy = stack.copy();
        int storageEnd = capacity;
        int playerEnd = capacity + 36;
        int moduleEnd = playerEnd + moduleSlots;

        if (index < storageEnd) {
            if (!moveItemStackTo(stack, storageEnd, playerEnd, true)) return ItemStack.EMPTY;
        } else if (index >= playerEnd) {
            if (!moveItemStackTo(stack, storageEnd, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            if (BackpackStorage.isModule(stack)) {
                if (!moveItemStackTo(stack, playerEnd, moduleEnd, false)) {
                    if (!BackpackStorage.allowed(stack) || !moveItemStackTo(stack, 0, storageEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else {
                if (!BackpackStorage.allowed(stack) || !moveItemStackTo(stack, 0, storageEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        container.setChanged();
        moduleContainer.setChanged();
        return copy;
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (!stillValid(player) || !(container instanceof BackpackInventory live) || !getCarried().isEmpty()) return false;
        if (button == MODE) {
            int maxMode = capacity >= 72 ? 3 : 2;
            BackpackStorage.mode(live.source(), (BackpackStorage.mode(live.source()) + 1) % maxMode);
            player.getInventory().setChanged();
            if (sourceSlot == BackpackHarness.SLOT) BackpackHarness.changed(player);
            return true;
        }
        if (button == SORT) { sort(live); return true; }
        if (button == STASH) { stash(live, player); return true; }
        if (button == REFILL) { refill(live, player); return true; }
        if (button == TOGGLE_FEEDER) {
            if (moduleContainer instanceof BackpackModuleInventory modInv && modInv.bound()) {
                for (int i = 0; i < modInv.getContainerSize(); i++) {
                    var m = modInv.getItem(i);
                    if (!m.isEmpty() && m.getItem() instanceof BackpackModuleItem mod && mod.type == ModuleType.FEEDER) {
                        int newMode = BackpackModuleItem.toggleFeederMode(m);
                        modInv.setItem(i, m);
                        modInv.setChanged();
                        String key = newMode == BackpackModuleItem.FEEDER_FAST
                                ? "message.interstice.feeder_mode.fast"
                                : "message.interstice.feeder_mode.eco";
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
    }

    public static void sort(BackpackInventory live) {
        if (!live.bound()) return;
        var nonempty = BackpackStorage.read(live.source()).stream()
                .filter(s -> !s.isEmpty()).map(ItemStack::copy)
                .sorted(Comparator.comparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                        .thenComparing(s -> s.getHoverName().getString())).toList();
        var result = new ArrayList<ItemStack>(Collections.nCopies(live.getContainerSize(), ItemStack.EMPTY));
        for (var stack : nonempty) {
            if (BackpackStorage.insert(result, stack) != stack.getCount()) {
                throw new IllegalStateException("Sorting must preserve capacity");
            }
        }
        live.replace(result);
    }

    public static void stash(BackpackInventory live, Player player) {
        if (!live.bound()) return;
        for (int i = 9; i < 36; i++) {
            var stack = player.getInventory().getItem(i);
            if (i == live.sourceSlot || stack.isEmpty()) continue;
            int accepted = live.insert(stack);
            if (accepted > 0) stack.shrink(accepted);
        }
        player.getInventory().setChanged();
    }

    public static void refill(BackpackInventory live, Player player) {
        if (!live.bound()) return;
        var items = BackpackStorage.read(live.source());
        for (int i = 0; i < 9; i++) {
            var at = player.getInventory().getItem(i);
            if (at.isEmpty() || i == live.sourceSlot || at.getCount() >= at.getMaxStackSize()) continue;
            for (var reserve : items) {
                if (ItemStack.isSameItemSameComponents(at, reserve)) {
                    int take = Math.min(reserve.getCount(), at.getMaxStackSize() - at.getCount());
                    at.grow(take);
                    reserve.shrink(take);
                    if (at.getCount() == at.getMaxStackSize()) break;
                }
            }
        }
        live.replace(items);
        player.getInventory().setChanged();
    }
}
