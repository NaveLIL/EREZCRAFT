package pro.erez.interstice.rift;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LightningRodBlock;

/** A paid deterministic cauldron anomaly; charge is consumed only by a successful native rift transfer. */
public final class RiftInitiatorItem extends Item {
    public RiftInitiatorItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var stack = context.getItemInHand();
        var state = level.getBlockState(pos);
        var support = level.getBlockState(pos.below());
        boolean apparatus = level.dimension().equals(Level.OVERWORLD)
                && state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) == 3
                && support.is(Blocks.LIGHTNING_ROD) && support.getValue(LightningRodBlock.FACING) == Direction.UP;
        if (!apparatus) {
            if (!level.isClientSide && context.getPlayer() != null)
                context.getPlayer().displayClientMessage(Component.translatable("message.interstice.initiator.apparatus"), true);
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player) || player.serverLevel() != level
                || stack.isEmpty() || stack.getItem() != this || player.getItemInHand(context.getHand()) != stack
                || !player.canInteractWithBlock(pos, 0) || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        // Reuses source/landing validation, shared links, cooldown and the existing expedition ledger events.
        // A replay is rejected after the first call by both the changed dimension and rift cooldown.
        var source = new RiftLinks.Endpoint(level.dimension(), player.blockPosition(), Direction.Axis.X);
        if (!RiftTravel.enter(player, source, RiftLinks.Kind.CAULDRON)) return InteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(1);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return InteractionResult.CONSUME;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) {
        text.add(Component.translatable("tooltip.interstice.unstable_rift_initiator.apparatus"));
        text.add(Component.translatable("tooltip.interstice.unstable_rift_initiator.cost"));
    }
}
