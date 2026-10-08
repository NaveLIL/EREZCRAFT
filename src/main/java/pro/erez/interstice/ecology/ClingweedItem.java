package pro.erez.interstice.ecology;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public final class ClingweedItem extends BlockItem {
    public ClingweedItem(Block block) { super(block, new Item.Properties()); }
    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.interstice.clingweed.passage").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.interstice.clingweed.recovery").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.interstice.clingweed.gas").withStyle(ChatFormatting.DARK_RED));
    }
}
