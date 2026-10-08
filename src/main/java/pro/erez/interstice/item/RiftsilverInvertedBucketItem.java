package pro.erez.interstice.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import pro.erez.interstice.LightBucketItem;

public final class RiftsilverInvertedBucketItem extends LightBucketItem {
    public RiftsilverInvertedBucketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return RiftsilverFilledBucketItem.keepRiftsilver(super.use(level, player, hand), player);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        // Chemically inert Riftsilver - never corrodes!
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        // Keep dropped riftsilver inert as well as buckets held in an inventory.
        return false;
    }
}
