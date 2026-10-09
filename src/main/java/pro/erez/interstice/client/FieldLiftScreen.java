package pro.erez.interstice.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.lift.FieldLiftMenu;
import pro.erez.interstice.lift.RealmLift;

/** Compact three-row cargo panel reuses the accepted metal tiles without changing their pixels. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class FieldLiftScreen extends AbstractContainerScreen<FieldLiftMenu> {
    private static final int TEXT = 0xffe3ebed, MUTED = 0xffb3c2c9, WARNING = 0xffe2b775;

    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(FieldLiftMenu.TYPE.get(), FieldLiftScreen::new);
    }

    public FieldLiftScreen(FieldLiftMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 184;
        inventoryLabelX = 8;
        inventoryLabelY = 74;
        titleLabelY = 5;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        GuiMaterials.panel(graphics, leftPos, topPos, imageWidth, imageHeight, false);
        for (var slot : menu.slots)
            GuiMaterials.slot(graphics, leftPos + slot.x, topPos + slot.y, slot.index < RealmLift.CARGO_SLOTS ? 1 : 0);
        graphics.fill(leftPos + 5, topPos + 71, leftPos + imageWidth - 5, topPos + 72, 0xff61786f);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int occupied = 0;
        for (int slot = 0; slot < RealmLift.CARGO_SLOTS; slot++) if (!menu.getContainer().getItem(slot).isEmpty()) occupied++;
        String counter = occupied + "/" + RealmLift.CARGO_SLOTS;
        int counterWidth = font.width(counter);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), imageWidth - counterWidth - 22), 8, 5, TEXT, false);
        graphics.drawString(font, counter, imageWidth - counterWidth - 8, 5, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        if (menu.data.get(0) > 0) graphics.drawString(font,
                font.plainSubstrByWidth(Component.translatable("menu.interstice.field_lift.overflow", menu.data.get(0)).getString(), imageWidth - 16),
                8, 166, WARNING, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.data.get(0) > 0 && mouseX >= leftPos + 8 && mouseX < leftPos + imageWidth - 8
                && mouseY >= topPos + 164 && mouseY < topPos + 178)
            graphics.renderTooltip(font, Component.translatable("tooltip.interstice.field_lift.overflow", menu.data.get(0)), mouseX, mouseY);
    }
}
