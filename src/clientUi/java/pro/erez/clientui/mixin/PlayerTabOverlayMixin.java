package pro.erez.clientui.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.clientui.ErezcraftTab;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void erezcraft$render(GuiGraphics graphics, int width, Scoreboard scoreboard,
                                  Objective objective, CallbackInfo callback) {
        if (ErezcraftTab.render(Minecraft.getInstance(), graphics, objective)) callback.cancel();
    }
}
