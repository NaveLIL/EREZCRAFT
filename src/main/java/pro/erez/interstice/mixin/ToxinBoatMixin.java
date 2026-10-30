package pro.erez.interstice.mixin;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pro.erez.interstice.ocean.ToxinBoats;
@Mixin(Boat.class)
public abstract class ToxinBoatMixin implements pro.erez.interstice.ocean.OceanBoatState {
    @org.spongepowered.asm.mixin.Unique private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> interstice$corrosion=net.minecraft.network.syncher.SynchedEntityData.defineId(Boat.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    public int interstice$exposure(){return ((Boat)(Object)this).getEntityData().get(interstice$corrosion);}
    public void interstice$exposure(int n){((Boat)(Object)this).getEntityData().set(interstice$corrosion,n);}
    @Inject(method="defineSynchedData",at=@At("TAIL"))
    private void interstice$define(net.minecraft.network.syncher.SynchedEntityData.Builder builder,CallbackInfo ci){builder.define(interstice$corrosion,0);}
    @Inject(method="floatBoat",at=@At("HEAD"),cancellable=true)
    private void interstice$wave(CallbackInfo ci){if(ToxinBoats.floatBoat((Boat)(Object)this))ci.cancel();}
    @Inject(method="getStatus",at=@At("RETURN"),cancellable=true)
    private void interstice$status(CallbackInfoReturnable<Boat.Status> ci){var b=(Boat)(Object)this;if(pro.erez.interstice.ocean.ToxinHullOcclusion.dry(b.level(),b.getX(),b.getY()+.05,b.getZ())){ci.setReturnValue(Boat.Status.IN_AIR);return;}double h=ToxinBoats.surface(b);if(Double.isFinite(h)&&b.getY()>=h-.7)ci.setReturnValue(Boat.Status.IN_WATER);}
    @Inject(method="getPickResult",at=@At("RETURN"))
    private void interstice$item(CallbackInfoReturnable<net.minecraft.world.item.ItemStack> ci){ToxinBoats.saveItem((Boat)(Object)this,ci.getReturnValue());}
}
