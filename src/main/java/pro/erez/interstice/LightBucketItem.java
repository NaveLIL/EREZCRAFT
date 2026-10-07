package pro.erez.interstice;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Pouring into the held ocean merges the contents without replacing its shaped voxel. */
public final class LightBucketItem extends BucketItem {
    public LightBucketItem(Properties properties) { super(Interstice.LIGHT.get(), properties); }

    @Override
    public boolean emptyContents(@Nullable Player player, Level level, BlockPos pos,
                                 @Nullable BlockHitResult hit, @Nullable ItemStack container) {
        if (level.getBlockState(pos).getBlock() instanceof OceanLiquidBlock
                && Interstice.LIGHT.get().isSame(level.getFluidState(pos).getType())) {
            playEmptySound(player, level, pos);
            return true;
        }
        return super.emptyContents(player, level, pos, hit, container);
    }
}
