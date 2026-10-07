package pro.erez.interstice.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.water.WaterTransmutationManager;
import pro.erez.interstice.worldgen.IslandWorld;

@Mixin(IceBlock.class)
public abstract class IceBlockMixin {
    @Inject(method = "melt", at = @At("HEAD"), cancellable = true)
    private void interstice$interceptIceMelt(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (IslandWorld.isIsland(level.dimension())) {
            WaterTransmutationManager.transmuteAt(level, pos, null);
            ci.cancel();
        }
    }
}
