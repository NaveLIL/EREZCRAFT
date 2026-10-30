package pro.erez.interstice.mixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.ocean.ToxinBoats;
@Mixin(Entity.class)
public abstract class ToxinBoatDropMixin {
    @Inject(method="spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",at=@At("HEAD"))
    private void interstice$remember(ItemStack stack,float offset,CallbackInfoReturnable<net.minecraft.world.entity.item.ItemEntity> ci){ToxinBoats.saveItem((Entity)(Object)this,stack);}
}
