package pro.erez.interstice.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.gear.EngineerBreathing;

@Mixin(LivingEntity.class)
public abstract class EngineerBreathingMixin {
    @Inject(method="decreaseAirSupply",at=@At("HEAD"),cancellable=true)
    private void interstice$closedLoop(int current,CallbackInfoReturnable<Integer> cir){
        var entity=(LivingEntity)(Object)this;
        if(!entity.level().isClientSide&&EngineerBreathing.fitted(entity)&&EngineerBreathing.medium(entity)&&EngineerBreathing.breathe(entity))cir.setReturnValue(current);
    }
}
