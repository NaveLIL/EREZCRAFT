package pro.erez.interstice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A real 3x3 crafting-menu interaction, with one ingredient per cell and server round-trip delays. */
final class SurvivalMenuCraft {
    private final Item[] cells;
    private final Item output;
    private int ticks, cell, step, source;
    private boolean finished;
    SurvivalMenuCraft(Item output, Item... cells) {
        if (cells.length != 9) throw new IllegalArgumentException("Nine explicit recipe cells required");
        this.output = output; this.cells = cells;
    }
    boolean tick(Minecraft mc) {
        if (finished) return true;
        if (mc.player.containerMenu == mc.player.inventoryMenu) return false;
        if (++ticks % 5 != 0) return false;
        var menu = mc.player.containerMenu;
        while (cell < 9 && cells[cell] == null) cell++;
        if (cell == 9) {
            if (!menu.getSlot(0).getItem().is(output)) throw new IllegalStateException("Real recipe output differs from " + output + ": " + menu.getSlot(0).getItem());
            click(mc, 0, 0, ClickType.QUICK_MOVE); finished = true; return false;
        }
        if (step == 0) {
            source = -1;
            for (int slot = 10; slot < menu.slots.size(); slot++) if (matches(menu.getSlot(slot).getItem(), cells[cell])) { source = slot; break; }
            if (source < 0) throw new IllegalStateException("Naturally acquired recipe ingredient missing: " + cells[cell]);
            click(mc, source, 0, ClickType.PICKUP);
        } else if (step == 1) click(mc, cell + 1, 1, ClickType.PICKUP);
        else {
            if (!menu.getCarried().isEmpty()) click(mc, source, 0, ClickType.PICKUP);
            cell++; step = 0; return false;
        }
        step++; return false;
    }
    private static boolean matches(ItemStack stack, Item item) { return item == Items.OAK_PLANKS ? stack.is(ItemTags.PLANKS) : stack.is(item); }
    private static void click(Minecraft mc, int slot, int button, ClickType type) { mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, button, type, mc.player); }
}
