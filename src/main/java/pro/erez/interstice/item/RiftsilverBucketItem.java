package pro.erez.interstice.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
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

    public static ItemStack filledWith(Fluid fluid) {
        if (fluid.isSame(Interstice.HEAVY.get())) return new ItemStack(Interstice.RIFTSILVER_HEAVY_BUCKET.get());
        if (fluid.isSame(Interstice.LIGHT.get())) return new ItemStack(Interstice.RIFTSILVER_INVERTED_BUCKET.get());
        if (fluid.isSame(Fluids.WATER)) return new ItemStack(Interstice.RIFTSILVER_WATER_BUCKET.get());
        if (fluid.isSame(Fluids.LAVA)) return new ItemStack(Interstice.RIFTSILVER_LAVA_BUCKET.get());
        return ItemStack.EMPTY;
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
                ItemStack filled = filledWith(fluid);

                if (!filled.isEmpty()) {
                    BlockState blockState = level.getBlockState(hitPos);
                    if (!(blockState.getBlock() instanceof BucketPickup pickup)
                            || pickup.pickupBlock(player, level, hitPos, blockState).isEmpty()) {
                        return InteractionResultHolder.fail(stack);
                    }
                    player.awardStat(Stats.ITEM_USED.get(this));
                    var sound = pickup.getPickupSound(blockState).orElse(
                            fluid.isSame(Fluids.LAVA) ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL);
                    level.playSound(player, hitPos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(player, GameEvent.FLUID_PICKUP, hitPos);
                    if (player instanceof ServerPlayer serverPlayer) {
                        CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, filled);
                    }

                    ItemStack result = ItemUtils.createFilledResult(stack, player, filled);
                    return InteractionResultHolder.sidedSuccess(result, level.isClientSide());
                }
            }
        }

        return InteractionResultHolder.pass(stack);
    }
}
