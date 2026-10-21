package pro.erez.clientui;

import java.util.Comparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.DisplaySlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/** A small immediate-mode overlay. Skin fetching, caching and signature checks remain vanilla. */
@EventBusSubscriber(modid = ClientUi.ID, value = Dist.CLIENT)
public final class ErezcraftTab {
    private static final int PANEL = 0xF2181C22, HEADER = 0xFF222A30, EDGE = 0xFF45515D;
    private static final int ACCENT = 0xFF9CBFCA, TEXT = 0xFFE8EDF1, MUTED = 0xFFADB9C3;
    private static final int ROW_HEIGHT = 18, INSET = 10, GAP = 10, TOP = 10, HEADER_HEIGHT = 40, FOOTER_HEIGHT = 23;
    private static final Comparator<PlayerInfo> ORDER = Comparator
            .comparingInt((PlayerInfo info) -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
            .thenComparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(info -> info.getProfile().getId());
    private static int firstVisible, pageSize, totalPlayers, lastRenderTick = -10;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !mc.options.keyPlayerList.isDown()) {
            firstVisible = 0;
            lastRenderTick = -10;
        }
    }

    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.level.getScoreboard().getDisplayObjective(DisplaySlot.LIST) != null
                || mc.screen != null || mc.options.hideGui || !mc.options.keyPlayerList.isDown()
                || !ClientUi.COMPACT_TAB.get() || totalPlayers <= pageSize || pageSize == 0 || event.getScrollDeltaY() == 0) return;
        int elapsed = mc.gui.getGuiTicks() - lastRenderTick;
        if (elapsed < 0 || elapsed > 2) return; // Never capture hotbar scrolling while another Tab renderer is active.
        firstVisible = Math.clamp(firstVisible + (event.getScrollDeltaY() > 0 ? -pageSize : pageSize), 0, totalPlayers - pageSize);
        event.setCanceled(true);
    }

    public static boolean render(Minecraft mc, GuiGraphics graphics, Objective objective) {
        if (!ClientUi.COMPACT_TAB.get() || objective != null || mc.getConnection() == null || mc.level == null || mc.player == null) return false;
        var listed = mc.getConnection().getListedOnlinePlayers();
        var players = listed.stream().sorted(ORDER).toList();
        if (players.isEmpty()) return false;
        int available = graphics.guiWidth() - 16;
        int columns = players.size() > 5 && available >= 344 ? 2 : 1;
        int width = Math.min(available, columns == 2 ? 344 : 224);
        int rowsAvailable = Math.max(1, (graphics.guiHeight() - TOP - 48 - HEADER_HEIGHT - FOOTER_HEIGHT) / ROW_HEIGHT);
        int rows = Math.min(rowsAvailable, (players.size() + columns - 1) / columns);
        int visible = Math.min(players.size(), rows * columns);
        int hidden = listed.size() - visible;
        pageSize = visible;
        totalPlayers = players.size();
        firstVisible = Math.clamp(firstVisible, 0, totalPlayers - pageSize);
        lastRenderTick = mc.gui.getGuiTicks();
        int height = HEADER_HEIGHT + rows * ROW_HEIGHT + FOOTER_HEIGHT;
        int left = (graphics.guiWidth() - width) / 2;
        int contentTop = TOP + HEADER_HEIGHT;
        int columnWidth = (width - 2 * INSET - (columns - 1) * GAP) / columns;

        graphics.fill(left + 2, TOP + 3, left + width + 2, TOP + height + 3, 0x50000000);
        graphics.fill(left, TOP, left + width, TOP + height, PANEL);
        graphics.fill(left, TOP, left + width, TOP + HEADER_HEIGHT - 5, HEADER);
        graphics.fill(left, TOP + HEADER_HEIGHT - 5, left + width, TOP + HEADER_HEIGHT - 4, ACCENT);
        graphics.drawCenteredString(mc.font, "EREZCRAFT", left + width / 2, TOP + 7, ACCENT);
        graphics.drawCenteredString(mc.font, Component.translatable("tab.erezcraft_ui.online", listed.size()), left + width / 2, TOP + 21, MUTED);

        for (int index = 0; index < visible; index++) {
            var info = players.get(firstVisible + index);
            int x = left + INSET + (index / rows) * (columnWidth + GAP);
            int y = contentTop + (index % rows) * ROW_HEIGHT;
            boolean own = info.getProfile().getId().equals(mc.player.getUUID());
            if (own) {
                graphics.fill(x - 3, y, x + columnWidth + 3, y + ROW_HEIGHT - 2, 0xFF29333C);
                graphics.fill(x - 3, y, x - 2, y + ROW_HEIGHT - 2, ACCENT);
            }
            var entity = mc.level.getPlayerByUUID(info.getProfile().getId());
            boolean hat = entity == null || entity.isModelPartShown(PlayerModelPart.HAT);
            boolean upsideDown = entity != null && LivingEntityRenderer.isEntityUpsideDown(entity);
            PlayerFaceRenderer.draw(graphics, info.getSkin().texture(), x, y + 2, 12, hat, upsideDown);
            String ping = info.getLatency() < 0 ? "—" : Component.translatable("tab.erezcraft_ui.ping", info.getLatency()).getString();
            int pingWidth = mc.font.width(ping);
            int nameWidth = Math.max(0, columnWidth - 18 - pingWidth - 6);
            String name = info.getProfile().getName();
            if (mc.font.width(name) > nameWidth) name = mc.font.plainSubstrByWidth(name, Math.max(0, nameWidth - mc.font.width("…"))) + "…";
            graphics.drawString(mc.font, name, x + 18, y + 4, info.getGameMode() == GameType.SPECTATOR ? MUTED : TEXT, false);
            int pingColor = info.getLatency() >= 300 ? 0xFFE2A48E : MUTED;
            graphics.drawString(mc.font, ping, x + columnWidth - pingWidth, y + 4, pingColor, false);
        }
        int footer = contentTop + rows * ROW_HEIGHT + 3;
        graphics.fill(left + INSET, footer, left + width - INSET, footer + 1, EDGE);
        Component line = hidden > 0 ? Component.translatable("tab.erezcraft_ui.page", firstVisible + 1, firstVisible + visible, listed.size()) : Component.translatable("tab.erezcraft_ui.footer");
        String footerText = mc.font.plainSubstrByWidth(line.getString(), width - 2 * INSET - mc.font.width("EREZ Dev") - 8);
        graphics.drawString(mc.font, footerText, left + INSET, footer + 6, MUTED, false);
        graphics.drawString(mc.font, "EREZ Dev", left + width - INSET - mc.font.width("EREZ Dev"), footer + 6, ACCENT, false);
        return true;
    }
    private ErezcraftTab() {}
}
