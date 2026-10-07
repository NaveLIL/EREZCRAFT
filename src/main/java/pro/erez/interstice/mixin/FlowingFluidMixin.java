package pro.erez.interstice.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.water.WaterTransmutationManager;
import pro.erez.interstice.worldgen.IslandWorld;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void interstice$interceptWaterTick(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (IslandWorld.isIsland(level.dimension()) && state.is(FluidTags.WATER)) {
            WaterTransmutationManager.transmuteAt(level, pos, null);
            ci.cancel();
            return;
        }
        if (pro.erez.interstice.fluid.FluidReactions.handleFluidContact(level, pos, state)) {
            ci.cancel();
        }
    }
}
