package pro.erez.interstice.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.client.AegisVision;

@Mixin(Minecraft.class)
public abstract class AegisOutlineMixin {
    @Inject(method="shouldEntityAppearGlowing",at=@At("RETURN"),cancellable=true)
    private void interstice$aegisScan(Entity entity,CallbackInfoReturnable<Boolean> cir){if((AegisVision.scanned(entity)||pro.erez.interstice.client.EngineerClient.scanned(entity)))cir.setReturnValue(true);}
}
