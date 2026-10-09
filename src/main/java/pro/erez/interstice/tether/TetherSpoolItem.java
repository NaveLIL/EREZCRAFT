package pro.erez.interstice.tether;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

/** An explicit self-use tool. Copies of this item contain no player/anchor attachment state. */
public final class TetherSpoolItem extends Item {
    public TetherSpoolItem(){super(new Properties().durability(128));}
    @Override public InteractionResult useOn(UseOnContext context){
        if(!context.getLevel().getBlockState(context.getClickedPos()).is(RiftTethers.WINCH.get())||context.getPlayer()==null)return InteractionResult.PASS;
        if(context.getPlayer() instanceof ServerPlayer player){
            if(player.isShiftKeyDown())WinchLinks.detach(player);
            else{if(!WinchLinks.attach(player,player.serverLevel(),context.getClickedPos(),32))return InteractionResult.FAIL;
                if(!player.isCreative())context.getItemInHand().hurtAndBreak(1,player,LivingEntity.getSlotForHand(context.getHand()));}
        }return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,java.util.List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("tooltip.interstice.tether_spool"));}
}
