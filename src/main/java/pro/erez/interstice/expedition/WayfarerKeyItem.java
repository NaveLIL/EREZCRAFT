package pro.erez.interstice.expedition;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import pro.erez.interstice.worldgen.IslandWorld;

public final class WayfarerKeyItem extends Item {
    public record Binding(UUID owner, UUID journey) {}
    public WayfarerKeyItem() { super(new Item.Properties().stacksTo(1)); }
    public static Binding binding(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("IntersticeKey");
        return tag.hasUUID("Owner") && tag.hasUUID("Journey") ? new Binding(tag.getUUID("Owner"), tag.getUUID("Journey")) : null;
    }
    public static void bind(ItemStack stack, UUID owner, UUID journey) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag binding = new CompoundTag(); binding.putUUID("Owner", owner); binding.putUUID("Journey", journey);
        data.put("IntersticeKey", binding); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) { if (IslandWorld.isIsland(level.dimension())) player.startUsingItem(hand); return InteractionResultHolder.consume(stack); }
        if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.fail(stack);
        if (!IslandWorld.isIsland(level.dimension())) return WayfarerRecall.prepareOrBind(server, stack, player.isShiftKeyDown()) ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
        if (!WayfarerRecall.begin(server, hand, stack)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand); return InteractionResultHolder.consume(stack);
    }
    // The server finishes after 200 uninterrupted ticks; vanilla use duration just keeps the button held.
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int ticksLeft) { if (entity instanceof ServerPlayer player) WayfarerRecall.cancel(player, "released"); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.interstice.wayfarer_key.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.interstice.wayfarer_key.prepare").withStyle(ChatFormatting.DARK_AQUA));
        if (binding(stack) != null) tooltip.add(Component.translatable("item.interstice.wayfarer_key.bound").withStyle(ChatFormatting.GOLD));
    }
}
