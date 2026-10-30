package pro.erez.interstice.mixin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class SkatPlayerLoginMixin {
    @Inject(method="load",at=@At("TAIL"))
    private void interstice$shipLoginDimension(CompoundTag tag,CallbackInfo ci){
        if(net.neoforged.fml.ModList.get().isLoaded("sable"))pro.erez.interstice.ship.SkatPlayerLogin.restore((Entity)(Object)this,tag);
    }
}
