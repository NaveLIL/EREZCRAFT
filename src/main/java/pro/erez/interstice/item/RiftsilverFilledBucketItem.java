package pro.erez.interstice.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import pro.erez.interstice.Interstice;

/** A filled riftsilver container always returns its own metal, including vanilla fluids. */
public class RiftsilverFilledBucketItem extends BucketItem {
    public RiftsilverFilledBucketItem(Fluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return keepRiftsilver(super.use(level, player, hand), player);
    }

    public static InteractionResultHolder<ItemStack> keepRiftsilver(InteractionResultHolder<ItemStack> result, Player player) {
        if (result.getResult().consumesAction() && !player.hasInfiniteMaterials() && result.getObject().is(Items.BUCKET)) {
            return new InteractionResultHolder<>(result.getResult(), new ItemStack(Interstice.RIFTSILVER_BUCKET.get()));
        }
        return result;
    }
}
