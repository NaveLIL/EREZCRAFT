package pro.erez.interstice.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.client.AegisVision;

@Mixin(Entity.class)
public abstract class AegisOutlineColorMixin {
    @Inject(method="getTeamColor",at=@At("RETURN"),cancellable=true)
    private void interstice$aegisColor(CallbackInfoReturnable<Integer> cir){var entity=(Entity)(Object)this;if((AegisVision.scanned(entity)||pro.erez.interstice.client.EngineerClient.scanned(entity)))cir.setReturnValue(AegisVision.color(entity));}
}
