package pro.erez.interstice.equipment;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class BackpackModuleItem extends Item {
    public final ModuleType type;
    public final int tier;

    public BackpackModuleItem(ModuleType type, int tier, Properties properties) {
        super(properties.stacksTo(16));
        this.type = type;
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) {
        text.add(Component.translatable("tooltip.interstice.module.type." + type.id(), tier).withStyle(ChatFormatting.AQUA));
        text.add(Component.translatable("tooltip.interstice.module." + type.id() + ".desc").withStyle(ChatFormatting.GRAY));
        text.add(Component.translatable("tooltip.interstice.module.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
