package pro.erez.interstice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Only the known experimental-world prompt in explicitly enabled disposable smoke profiles. */
public final class SmokeWorldPrompts {
    private SmokeWorldPrompts() {}
    public static boolean advance(Minecraft mc) {
        if (!(mc.screen instanceof BackupConfirmScreen) || !(mc.screen.getTitle().getContents() instanceof TranslatableContents title)
                || !title.getKey().equals("selectWorld.backupQuestion.experimental")) return false;
        for (var child : mc.screen.children()) if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents text
                && text.getKey().equals("selectWorld.backupJoinSkipButton")) {
            System.out.println("SMOKE_WORLD_PROMPT proceeding in disposable test world"); button.onPress(); return true;
        }
        return false;
    }
}
