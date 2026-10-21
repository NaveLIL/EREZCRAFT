package pro.erez.clientui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only branding; no networking, player identity or world state. */
@Mod(value = ClientUi.ID, dist = Dist.CLIENT)
public final class ClientUi {
    public static final String ID = "erezcraft_ui";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue COMPACT_TAB;
    static {
        var builder = new ModConfigSpec.Builder();
        COMPACT_TAB = builder.comment("Use the compact EREZCRAFT player list. Disable to restore vanilla Tab.",
                "Servers using a player-list scoreboard objective always keep the vanilla scoreboard display.")
                .translation("config.erezcraft_ui.compactPlayerList")
                .define("compactPlayerList", true);
        SPEC = builder.build();
    }
    public ClientUi(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
