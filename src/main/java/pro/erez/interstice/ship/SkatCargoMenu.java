package pro.erez.interstice.ship;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
/** The standard 54-slot transfer rules, with a ship-specific presentation. */
public final class SkatCargoMenu extends ChestMenu {
    public SkatCargoMenu(int id,Inventory inventory){this(id,inventory,new SimpleContainer(54));}
    public SkatCargoMenu(int id,Inventory inventory,Container cargo){super(SkatShips.CARGO_MENU.get(),id,inventory,cargo,6);}
}
