package pro.erez.interstice.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.client.AegisVision;

/** A virtual client-only optic effect; real potion durations are never overwritten or removed. */
@Mixin(LivingEntity.class)
public abstract class AegisNightVisionMixin {
    @Inject(method="hasEffect",at=@At("HEAD"),cancellable=true)
    private void interstice$aegisNight(Holder<MobEffect> effect,CallbackInfoReturnable<Boolean> cir){
        if(effect.equals(MobEffects.NIGHT_VISION)&&(AegisVision.nightVision((LivingEntity)(Object)this)||pro.erez.interstice.client.EngineerClient.night((LivingEntity)(Object)this)))cir.setReturnValue(true);
    }
    @Inject(method="getEffect",at=@At("HEAD"),cancellable=true)
    private void interstice$aegisEffect(Holder<MobEffect> effect,CallbackInfoReturnable<MobEffectInstance> cir){
        if(effect.equals(MobEffects.NIGHT_VISION)&&(AegisVision.nightVision((LivingEntity)(Object)this)||pro.erez.interstice.client.EngineerClient.night((LivingEntity)(Object)this)))
            cir.setReturnValue(new MobEffectInstance(MobEffects.NIGHT_VISION,-1,0,true,false,false));
    }
}
