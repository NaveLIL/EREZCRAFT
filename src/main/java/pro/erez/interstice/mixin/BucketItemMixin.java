package pro.erez.interstice.mixin;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.water.WaterTransmutationManager;
import pro.erez.interstice.worldgen.IslandWorld;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
    @Shadow private Fluid content;

    @Inject(method = "emptyContents", at = @At("HEAD"), cancellable = true)
    private void interstice$interceptWaterBucketEmpty(
            @Nullable Player player, Level level, BlockPos pos,
            @Nullable BlockHitResult hit,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.content.is(FluidTags.WATER) && IslandWorld.isIsland(level.dimension())) {
            boolean handled = WaterTransmutationManager.handleBucketEmpty(player, level, pos, hit);
            if (handled) {
                cir.setReturnValue(true);
            }
        }
    }
}
