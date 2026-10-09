package pro.erez.interstice.lift;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

/** Three cargo rows use the native 27+36 slot layout and the original owned-vehicle cargo lease. */
public final class FieldLiftMenu extends ChestMenu {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, Interstice.ID);
    public static final DeferredHolder<MenuType<?>, MenuType<FieldLiftMenu>> TYPE =
            MENUS.register("field_lift_cargo", () -> new MenuType<>(FieldLiftMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public final ContainerData data;

    public static void register(IEventBus bus) { MENUS.register(bus); }

    public FieldLiftMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(RealmLift.CARGO_SLOTS), new SimpleContainerData(1));
    }

    public FieldLiftMenu(int id, Inventory inventory, FieldLiftEntity lift) {
        this(id, inventory, lift.cargo(), new ContainerData() {
            @Override public int get(int index) { return index == 0 ? lift.quarantinedCargoRecords() : 0; }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 1; }
        });
    }

    private FieldLiftMenu(int id, Inventory inventory, Container cargo, ContainerData data) {
        super(TYPE.get(), id, inventory, cargo, 3);
        if (cargo.getContainerSize() != RealmLift.CARGO_SLOTS) throw new IllegalArgumentException("A field lift requires exactly 27 cargo slots");
        this.data = data;
        addDataSlots(data);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        return !stillValid(player) || index < 0 || index >= slots.size() ? ItemStack.EMPTY : super.quickMoveStack(player, index);
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player)) super.clicked(slot, button, type, player);
    }
}
