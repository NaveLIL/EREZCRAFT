package pro.erez.interstice.equipment;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class BackpackModuleItem extends Item {
    public static final int FEEDER_ECO = 0;
    public static final int FEEDER_FAST = 1;
    public static final String FEEDER_MODE_KEY = "interstice_feeder_mode";

    public final ModuleType type;
    public final int tier;

    public BackpackModuleItem(ModuleType type, int tier, Properties properties) {
        super(properties.stacksTo(16));
        this.type = type;
        this.tier = tier;
    }

    public static int getFeederMode(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BackpackModuleItem mod) || mod.type != ModuleType.FEEDER) {
            return FEEDER_ECO;
        }
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getInt(FEEDER_MODE_KEY) == FEEDER_FAST ? FEEDER_FAST : FEEDER_ECO;
    }

    public static void setFeederMode(ItemStack stack, int mode) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BackpackModuleItem mod) || mod.type != ModuleType.FEEDER) {
            return;
        }
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(FEEDER_MODE_KEY, mode == FEEDER_FAST ? FEEDER_FAST : FEEDER_ECO);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int toggleFeederMode(ItemStack stack) {
        int next = getFeederMode(stack) == FEEDER_ECO ? FEEDER_FAST : FEEDER_ECO;
        setFeederMode(stack, next);
        return next;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (type == ModuleType.FEEDER) {
            int newMode = toggleFeederMode(stack);
            if (level.isClientSide()) {
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, newMode == FEEDER_FAST ? 1.4F : 1.0F);
            } else {
                String key = newMode == FEEDER_FAST ? "message.interstice.feeder_mode.fast" : "message.interstice.feeder_mode.eco";
                player.displayClientMessage(Component.translatable(key), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) {
        text.add(Component.translatable("tooltip.interstice.module.type." + type.id(), tier).withStyle(ChatFormatting.AQUA));
        if (type == ModuleType.FEEDER) {
            int mode = getFeederMode(stack);
            if (mode == FEEDER_FAST) {
                text.add(Component.translatable("tooltip.interstice.module.feeder.mode.fast").withStyle(ChatFormatting.GREEN));
            } else {
                text.add(Component.translatable("tooltip.interstice.module.feeder.mode.eco").withStyle(ChatFormatting.GOLD));
            }
        }
        text.add(Component.translatable("tooltip.interstice.module." + type.id() + ".desc").withStyle(ChatFormatting.GRAY));
        if (type == ModuleType.FEEDER) {
            text.add(Component.translatable("tooltip.interstice.module.feeder.toggle_hint").withStyle(ChatFormatting.DARK_AQUA));
        }
        text.add(Component.translatable("tooltip.interstice.module.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
