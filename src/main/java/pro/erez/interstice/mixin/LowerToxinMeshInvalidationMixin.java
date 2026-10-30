package pro.erez.interstice.mixin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientLevel.class)
public abstract class LowerToxinMeshInvalidationMixin {
    @Inject(method="sendBlockUpdated",at=@At("TAIL"))
    private void interstice$dirty(BlockPos p,BlockState old,BlockState state,int flags,CallbackInfo ci){pro.erez.interstice.client.LowerToxinRenderer.invalidate(p);pro.erez.interstice.ocean.LowerToxinShore.block((ClientLevel)(Object)this,p);pro.erez.interstice.ocean.ToxinHullOcclusion.invalidate((ClientLevel)(Object)this,p);}
}
