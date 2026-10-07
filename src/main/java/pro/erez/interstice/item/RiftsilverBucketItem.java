package pro.erez.interstice.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import pro.erez.interstice.Interstice;

public class RiftsilverBucketItem extends Item {
    public RiftsilverBucketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);

        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hit.getBlockPos();
            Direction direction = hit.getDirection();
            BlockPos relative = hitPos.relative(direction);

            if (!level.mayInteract(player, hitPos) || !player.mayUseItemAt(relative, direction, stack)) {
                return InteractionResultHolder.fail(stack);
            }

            FluidState fluidState = level.getFluidState(hitPos);
            if (fluidState.isSource()) {
                Fluid fluid = fluidState.getType();
                ItemStack filled = null;

                if (fluid.isSame(Interstice.HEAVY.get())) {
                    filled = new ItemStack(Interstice.RIFTSILVER_HEAVY_BUCKET.get());
                } else if (fluid.isSame(Interstice.LIGHT.get())) {
                    filled = new ItemStack(Interstice.RIFTSILVER_INVERTED_BUCKET.get());
                } else if (fluid.isSame(Fluids.WATER)) {
                    filled = new ItemStack(Items.WATER_BUCKET);
                } else if (fluid.isSame(Fluids.LAVA)) {
                    filled = new ItemStack(Items.LAVA_BUCKET);
                }

                if (filled != null) {
                    level.setBlock(hitPos, Blocks.AIR.defaultBlockState(), 11);
                    player.awardStat(Stats.ITEM_USED.get(this));
                    level.playSound(player, hitPos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(player, GameEvent.FLUID_PICKUP, hitPos);

                    ItemStack result = ItemUtils.createFilledResult(stack, player, filled);
                    return InteractionResultHolder.sidedSuccess(result, level.isClientSide());
                }
            }
        }

        return InteractionResultHolder.pass(stack);
    }
}
