package pro.erez.interstice.gear;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ecology.ClingweedGas;

/** Only the existing defensive cloud's explicit effect source can spend a coating charge. */
@EventBusSubscriber(modid=Interstice.ID)
public final class GearHazards {
    @SubscribeEvent public static void effect(MobEffectEvent.Applicable event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!(event.getEffectSource() instanceof AreaEffectCloud cloud)||!cloud.getTags().contains(ClingweedGas.ENTITY_TAG))return;
        var effect=event.getEffectInstance().getEffect();if(effect!=MobEffects.POISON&&effect!=MobEffects.WEAKNESS&&effect!=MobEffects.CONFUSION)return;
        if(RealmGear.protect(player,RealmGear.HazardKind.CAVE_GAS))event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
    private GearHazards(){}
}
