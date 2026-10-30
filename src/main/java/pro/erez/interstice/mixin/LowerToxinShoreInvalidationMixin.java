package pro.erez.interstice.mixin;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Level.class)
public abstract class LowerToxinShoreInvalidationMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
    private void interstice$shore(BlockPos p,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> ci){if(ci.getReturnValue()){pro.erez.interstice.ocean.LowerToxinShore.block((Level)(Object)this,p);pro.erez.interstice.ocean.ToxinHullOcclusion.invalidate((Level)(Object)this,p);}}
}
