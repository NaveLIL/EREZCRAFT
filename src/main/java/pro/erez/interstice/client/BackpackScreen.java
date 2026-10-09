package pro.erez.interstice.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import pro.erez.interstice.equipment.BackpackMenu;

public final class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {
    private static final int TEXT = 0xffe3e9e1, MUTED = 0xffbbc6ba, ACCENT = 0xff91d0bd, AMBER = 0xffffc32d, RIFT_CYAN = 0xff54d8c6;
    private final List<ActionButton> controls = new ArrayList<>();
    private ActionButton mode;

    public BackpackScreen(BackpackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.width;
        imageHeight = (menu.rows == 7) ? 254 : 236;
        inventoryLabelX = (imageWidth - 162) / 2;
        inventoryLabelY = (menu.rows == 7) ? 144 : 126;
        titleLabelY = 5;
    }

    @Override
    protected void init() {
        super.init();
        controls.clear();
        boolean rift = menu.capacity >= 84;
        boolean reinforced = menu.capacity > 54;
        int accent = rift ? RIFT_CYAN : reinforced ? AMBER : ACCENT;

        int modeW, sortW, stashW, refillW, gap;
        if (reinforced) {
            modeW = 54; sortW = 46; stashW = 54; refillW = 54; gap = 4;
        } else {
            modeW = 42; sortW = 32; stashW = 42; refillW = 42; gap = 2;
        }
        int totalW = modeW + sortW + stashW + refillW + gap * 3;
        int startX = leftPos + (imageWidth - totalW) / 2;
        int y = topPos + ((menu.rows == 7) ? 232 : 214);

        mode = addControl("collect", BackpackMenu.MODE, startX, y, modeW, accent);
        int curX = startX + modeW + gap;
        addControl("sort", BackpackMenu.SORT, curX, y, sortW, accent);
        curX += sortW + gap;
        addControl("stash", BackpackMenu.STASH, curX, y, stashW, accent);
        curX += stashW + gap;
        addControl("refill", BackpackMenu.REFILL, curX, y, refillW, accent);

        updateControls();
    }

    private ActionButton addControl(String label, int action, int x, int y, int width, int accent) {
        var b = new ActionButton(x, y, width, 18,
                Component.translatable("menu.interstice.backpack." + label),
                action, accent,
                btn -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
                    }
                });
        b.setTooltip(Tooltip.create(Component.translatable("menu.interstice.backpack." + label + "_hint")));
        addRenderableWidget(b);
        controls.add(b);
        return b;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (BackpackClient.OPEN.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateControls();
    }

    private void updateControls() {
        boolean canAct = menu.getCarried().isEmpty();
        for (var b : controls) b.active = canAct;
        if (mode != null) {
            int currentMode = menu.data.get(0);
            mode.setMessage(Component.translatable("menu.interstice.backpack.mode." + currentMode));
            mode.setTooltip(Tooltip.create(Component.translatable("tooltip.interstice.backpack.mode." + currentMode)));
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        boolean rift = menu.capacity >= 84;
        boolean reinforced = menu.capacity > 54;
        int cornerAccent = rift ? RIFT_CYAN : reinforced ? AMBER : ACCENT;

        // Module Dock Rack (left side attached chassis)
        int moduleCount = menu.moduleSlots;
        int rackW = 28;
        int rackH = 12 + moduleCount * 22;
        int rackX = leftPos - 26;
        int rackY = topPos + 12;
        GuiMaterials.panel(g, rackX, rackY, rackW, rackH, true, cornerAccent);

        for (int i = 0; i < moduleCount; i++) {
            int slotX = leftPos - 21;
            int slotY = topPos + 19 + i * 22;
            GuiMaterials.slot(g, slotX, slotY, 0);
            var modSlot = menu.slots.get(menu.capacity + 36 + i);
            if (!modSlot.hasItem()) {
                int dotColor = (cornerAccent & 0x00ffffff) | 0x50000000;
                g.fill(slotX + 5, slotY + 7, slotX + 13, slotY + 11, dotColor);
                g.fill(slotX + 7, slotY + 5, slotX + 11, slotY + 13, dotColor);
            }
        }

        // Main panel
        GuiMaterials.panel(g, leftPos, topPos, imageWidth, imageHeight, true, cornerAccent);

        // Slots
        for (var slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            boolean locked = slot.getContainerSlot() == menu.sourceSlot && slot.container != menu.container;
            GuiMaterials.slot(g, x, y, locked ? 1 : 0);
        }

        // Section divider
        int divY = topPos + ((menu.rows == 7) ? 141 : 123);
        int divider = rift ? 0xff28555e : reinforced ? 0xffa07c32 : 0xff61786f;
        g.fill(leftPos + 5, divY, leftPos + imageWidth - 5, divY + 1, divider);
        if (rift || reinforced) {
            int glowColor = rift ? 0x3054d8c6 : 0x30d4a73b;
            g.fill(leftPos + 6, divY + 1, leftPos + imageWidth - 6, divY + 2, glowColor);
        }

        // Capacity Progress Bar
        int occupied = menu.data.get(1);
        int cap = menu.capacity;
        float ratio = Math.min(1.0F, Math.max(0.0F, (float) occupied / (float) cap));
        int barW = 36;
        int barH = 5;
        String usedText = occupied + "/" + cap;
        int usedW = font.width(usedText);
        int barX = leftPos + imageWidth - usedW - barW - 12;
        int barY = topPos + 6;

        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xff0d1418);
        g.fill(barX, barY, barX + barW, barY + barH, 0xff141c1f);

        int fillColor;
        if (ratio >= 1.0F) {
            fillColor = 0xffe06c75;
        } else if (ratio >= 0.85F) {
            fillColor = 0xffffa033;
        } else if (rift) {
            fillColor = RIFT_CYAN;
        } else if (reinforced) {
            fillColor = AMBER;
        } else {
            fillColor = ACCENT;
        }
        int filledPixels = Math.round(ratio * barW);
        if (filledPixels > 0) {
            g.fill(barX, barY, barX + filledPixels, barY + barH, fillColor);
            g.fill(barX, barY, barX + filledPixels, barY + 1, 0x40ffffff);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        boolean rift = menu.capacity >= 84;
        boolean reinforced = menu.capacity > 54;
        int occupied = menu.data.get(1);
        String used = occupied + "/" + menu.capacity;
        int counter = font.width(used);
        int titleLimit = imageWidth - counter - 54;
        g.drawString(font, font.plainSubstrByWidth(title.getString(), titleLimit), 8, 5, TEXT, false);
        int accent = rift ? RIFT_CYAN : reinforced ? AMBER : ACCENT;
        g.drawString(font, used, imageWidth - counter - 8, 5, accent, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);

        // Module empty socket tooltip
        boolean hoveredModule = false;
        for (int i = 0; i < menu.moduleSlots; i++) {
            var modSlot = menu.slots.get(menu.capacity + 36 + i);
            int slotX = leftPos + modSlot.x;
            int slotY = topPos + modSlot.y;
            if (!modSlot.hasItem() && mx >= slotX && mx <= slotX + 18 && my >= slotY && my <= slotY + 18) {
                g.renderTooltip(font, List.of(
                        Component.translatable("tooltip.interstice.backpack.socket_title", i + 1),
                        Component.translatable("tooltip.interstice.backpack.socket_hint")
                ), Optional.empty(), mx, my);
                hoveredModule = true;
                break;
            }
        }

        if (!hoveredModule) {
            // Capacity meter hover tooltip
            int occupied = menu.data.get(1);
            int cap = menu.capacity;
            String usedText = occupied + "/" + cap;
            int usedW = font.width(usedText);
            int barW = 36;
            int barX = leftPos + imageWidth - usedW - barW - 14;
            int barY = topPos + 4;
            if (mx >= barX && mx <= leftPos + imageWidth - 6 && my >= barY && my <= barY + 10) {
                int pct = Math.round(((float) occupied / (float) cap) * 100);
                g.renderTooltip(font, List.of(
                        Component.translatable("menu.interstice.backpack.capacity_tooltip"),
                        Component.translatable("menu.interstice.backpack.capacity_detail", occupied, cap, pct),
                        Component.translatable("menu.interstice.backpack.free_slots", cap - occupied)
                ), Optional.empty(), mx, my);
            } else {
                renderTooltip(g, mx, my);
            }
        }
    }

    private final class ActionButton extends Button {
        private final int actionId;
        private final int accent;

        ActionButton(int x, int y, int width, int height, Component title, int actionId, int accent, OnPress onPress) {
            super(x, y, width, height, title, onPress, DEFAULT_NARRATION);
            this.actionId = actionId;
            this.accent = accent;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hovered = isHoveredOrFocused();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();

            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xff0d1418);
            GuiMaterials.tiles(g, GuiMaterials.METAL, x, y, w, h);
            g.fill(x, y, x + w, y + h, hovered ? 0xb024343a : 0xd8182226);

            int border = !active ? 0xff3a484c : hovered ? accent : 0xff546870;
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y, x + 1, y + h, border);
            g.fill(x + w - 1, y, x + w, y + h, border);
            g.fill(x, y + h - 1, x + w, y + h, border);

            int textColor;
            if (!active) {
                textColor = 0xff6b7b7f;
            } else if (actionId == BackpackMenu.MODE) {
                int modeVal = menu.data.get(0);
                textColor = modeVal == 0 ? 0xff8b9e9b : modeVal == 1 ? ACCENT : AMBER;
            } else {
                textColor = hovered ? 0xffffffff : 0xffdce5e2;
            }
            int textX = x + (w - font.width(getMessage())) / 2;
            int textY = y + (h - 8) / 2;
            g.drawString(font, getMessage(), textX, textY, textColor, false);
        }
    }
}
