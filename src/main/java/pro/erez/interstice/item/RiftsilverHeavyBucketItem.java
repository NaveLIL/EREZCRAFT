package pro.erez.interstice.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import pro.erez.interstice.Interstice;

public final class RiftsilverHeavyBucketItem extends BucketItem {
    public RiftsilverHeavyBucketItem(Properties properties) {
        super(Interstice.HEAVY.get(), properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResultHolder<ItemStack> res = super.use(level, player, hand);
        if (res.getResult().consumesAction() && !player.hasInfiniteMaterials()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(Items.BUCKET)) {
                ItemStack riftsilver = new ItemStack(Interstice.RIFTSILVER_BUCKET.get());
                player.setItemInHand(hand, riftsilver);
                return InteractionResultHolder.sidedSuccess(riftsilver, level.isClientSide());
            } else if (res.getObject().is(Items.BUCKET)) {
                return InteractionResultHolder.sidedSuccess(new ItemStack(Interstice.RIFTSILVER_BUCKET.get()), level.isClientSide());
            }
        }
        return res;
    }
}
