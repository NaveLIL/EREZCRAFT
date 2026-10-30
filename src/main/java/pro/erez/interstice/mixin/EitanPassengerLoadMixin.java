package pro.erez.interstice.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sable relocates LoginPoint and drops RootVehicle; our UUID-bound seat can follow it. */
@Mixin(Entity.class)
public abstract class EitanPassengerLoadMixin {
    @Unique private CompoundTag interstice$eitanPassenger;

    @Inject(method="load",at=@At("HEAD"))
    private void interstice$rememberEitanPassenger(CompoundTag tag,CallbackInfo ci){
        interstice$eitanPassenger=null;
        if((Object)this instanceof ServerPlayer && tag.contains("RootVehicle",10)){
            var root=tag.getCompound("RootVehicle");
            if(root.getCompound("Entity").getString("id").equals("interstice:eitan_seat"))
                interstice$eitanPassenger=root.copy();
        }
    }

    @Inject(method="load",at=@At("TAIL"))
    private void interstice$restoreEitanPassenger(CompoundTag tag,CallbackInfo ci){
        if(interstice$eitanPassenger!=null && !tag.contains("RootVehicle",10))
            tag.put("RootVehicle",interstice$eitanPassenger);
        interstice$eitanPassenger=null;
    }
}
