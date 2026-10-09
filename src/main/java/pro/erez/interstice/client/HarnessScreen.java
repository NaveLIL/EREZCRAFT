package pro.erez.interstice.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import pro.erez.interstice.equipment.BackpackNetworking;
import pro.erez.interstice.equipment.HarnessMenu;

public final class HarnessScreen extends AbstractContainerScreen<HarnessMenu> {
    private Button openPackButton;

    public HarnessScreen(HarnessMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 70;
    }

    @Override
    protected void init() {
        super.init();
        openPackButton = addRenderableWidget(new OpenButton(leftPos + 40, topPos + 53, 96, 15,
                Component.translatable("menu.interstice.harness.open_pack"),
                b -> PacketDistributor.sendToServer(new BackpackNetworking.Open())));
        openPackButton.setTooltip(Tooltip.create(Component.translatable("menu.interstice.harness.open_pack_hint")));
        updateOpenButton();
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
        updateOpenButton();
    }

    private void updateOpenButton() {
        if (openPackButton != null) {
            boolean hasPack = menu.slots.get(0).hasItem();
            openPackButton.visible = hasPack;
            openPackButton.active = hasPack && menu.getCarried().isEmpty();
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int x, int y) {
        GuiMaterials.panel(g, leftPos, topPos, imageWidth, imageHeight, true, 0xff7bbaa6);
        for (var slot : menu.slots) {
            GuiMaterials.slot(g, leftPos + slot.x, topPos + slot.y, slot.index == 0 ? 2 : 0);
        }
        g.fill(leftPos + 77, topPos + 31, leftPos + 99, topPos + 53, 0xff7bb9a5);
        g.fill(leftPos + 79, topPos + 33, leftPos + 97, topPos + 51, 0xff202727);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int x, int y) {
        g.drawString(font, title, 8, 6, 0xffedf0ed, false);
        g.drawString(font, playerInventoryTitle, 8, 70, 0xffc7d0cb, false);
    }

    @Override
    public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        renderTooltip(g, x, y);
    }

    private final class OpenButton extends Button {
        OpenButton(int x, int y, int width, int height, Component title, OnPress onPress) {
            super(x, y, width, height, title, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hovered = isHoveredOrFocused();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();

            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xff0d1418);
            GuiMaterials.tiles(g, GuiMaterials.METAL, x, y, w, h);
            g.fill(x, y, x + w, y + h, hovered ? 0xb024343a : 0xd8182226);

            int border = !active ? 0xff3a484c : hovered ? 0xff91d0bd : 0xff546870;
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y, x + 1, y + h, border);
            g.fill(x + w - 1, y, x + w, y + h, border);
            g.fill(x, y + h - 1, x + w, y + h, border);

            int textColor = !active ? 0xff6b7b7f : hovered ? 0xffffffff : 0xff91d0bd;
            int textX = x + (w - font.width(getMessage())) / 2;
            int textY = y + (h - 8) / 2;
            g.drawString(font, getMessage(), textX, textY, textColor, false);
        }
    }
}
