package pro.erez.interstice.rift;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import pro.erez.interstice.Interstice;

public final class RiftLensItem extends Item {
    public RiftLensItem() { super(new Item.Properties().stacksTo(1)); }
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
            java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("item.interstice.rift_lens.frame_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.interstice.rift_lens.anomaly_hint").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.is(Interstice.RIFT_FRAME.get())) {
            if (context.getLevel() instanceof ServerLevel level) {
                var frame = RiftGeometry.activate(level, context.getClickedPos());
                if (frame == null) {
                    if (context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.translatable("message.interstice.rift.incomplete_frame"), true);
                    return InteractionResult.FAIL;
                }
                level.playSound(null, frame.origin(), net.minecraft.sounds.SoundEvents.PORTAL_TRIGGER, net.minecraft.sounds.SoundSource.BLOCKS, .5F, .75F);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (context.getPlayer() instanceof ServerPlayer player && RiftAnomalies.chargedCauldron(player, context.getClickedPos())) return InteractionResult.SUCCESS;
        return InteractionResult.PASS;
    }
}
