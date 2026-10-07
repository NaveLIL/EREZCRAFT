package pro.erez.interstice.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import pro.erez.interstice.Interstice;

public final class HeavyBucketItem extends BucketItem {
    public HeavyBucketItem(Properties properties) {
        super(Interstice.HEAVY.get(), properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        CorrosiveBucketHandler.tickCorrosion(stack, level, entity, Interstice.HEAVY_BLOCK, false);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, net.minecraft.world.entity.item.ItemEntity entity) {
        CorrosiveBucketHandler.tickDroppedCorrosion(stack, entity.level(), entity, Interstice.HEAVY_BLOCK, false);
        return false;
    }
}
