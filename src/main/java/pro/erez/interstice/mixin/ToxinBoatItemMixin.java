package pro.erez.interstice.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pro.erez.interstice.ocean.ToxinBoats;
@Mixin(BoatItem.class)
public abstract class ToxinBoatItemMixin {
    @WrapOperation(method="use",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean interstice$restore(Level level,Entity boat,Operation<Boolean> original,@Local ItemStack stack){ToxinBoats.loadItem(boat,stack);return original.call(level,boat);}
}
