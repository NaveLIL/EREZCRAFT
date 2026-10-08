package pro.erez.interstice.item;

import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import pro.erez.interstice.Interstice;

/** Vanilla fluid interactions with riftsilver contents and remainders throughout. */
public final class RiftsilverBucketInteractions {
    private RiftsilverBucketInteractions() {}

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (var map : new CauldronInteraction.InteractionMap[]{CauldronInteraction.EMPTY,
                    CauldronInteraction.WATER, CauldronInteraction.LAVA, CauldronInteraction.POWDER_SNOW}) {
                map.map().put(Interstice.RIFTSILVER_WATER_BUCKET.get(), returningSilver(CauldronInteraction.FILL_WATER));
                map.map().put(Interstice.RIFTSILVER_LAVA_BUCKET.get(), returningSilver(CauldronInteraction.FILL_LAVA));
            }
            CauldronInteraction.WATER.map().put(Interstice.RIFTSILVER_BUCKET.get(), (state, level, pos, player, hand, stack) ->
                    CauldronInteraction.fillBucket(state, level, pos, player, hand, stack,
                            new ItemStack(Interstice.RIFTSILVER_WATER_BUCKET.get()),
                            block -> block.getValue(LayeredCauldronBlock.LEVEL) == 3, SoundEvents.BUCKET_FILL));
            CauldronInteraction.LAVA.map().put(Interstice.RIFTSILVER_BUCKET.get(), (state, level, pos, player, hand, stack) ->
                    CauldronInteraction.fillBucket(state, level, pos, player, hand, stack,
                            new ItemStack(Interstice.RIFTSILVER_LAVA_BUCKET.get()), block -> true, SoundEvents.BUCKET_FILL_LAVA));

            DispenseItemBehavior pour = new DefaultDispenseItemBehavior() {
                private final DefaultDispenseItemBehavior fallback = new DefaultDispenseItemBehavior();

                @Override
                protected ItemStack execute(BlockSource source, ItemStack stack) {
                    BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                    DispensibleContainerItem container = (DispensibleContainerItem) stack.getItem();
                    if (!container.emptyContents(null, source.level(), target, null, stack)) return fallback.dispense(source, stack);
                    container.checkExtraContent(null, source.level(), stack, target);
                    return consumeWithRemainder(source, stack, new ItemStack(Interstice.RIFTSILVER_BUCKET.get()));
                }
            };
            DispenserBlock.registerBehavior(Interstice.RIFTSILVER_WATER_BUCKET.get(), pour);
            DispenserBlock.registerBehavior(Interstice.RIFTSILVER_LAVA_BUCKET.get(), pour);
            DispenserBlock.registerBehavior(Interstice.RIFTSILVER_HEAVY_BUCKET.get(), pour);
            DispenserBlock.registerBehavior(Interstice.RIFTSILVER_INVERTED_BUCKET.get(), pour);
            DispenserBlock.registerBehavior(Interstice.RIFTSILVER_BUCKET.get(), new DefaultDispenseItemBehavior() {
                private final DefaultDispenseItemBehavior fallback = new DefaultDispenseItemBehavior();

                @Override
                protected ItemStack execute(BlockSource source, ItemStack stack) {
                    BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                    var level = source.level();
                    var state = level.getBlockState(target);
                    var fluid = state.getFluidState();
                    ItemStack filled = fluid.isSource() ? RiftsilverBucketItem.filledWith(fluid.getType()) : ItemStack.EMPTY;
                    if (filled.isEmpty() || !(state.getBlock() instanceof BucketPickup pickup)
                            || pickup.pickupBlock(null, level, target, state).isEmpty()) return fallback.dispense(source, stack);
                    level.gameEvent(null, GameEvent.FLUID_PICKUP, target);
                    return consumeWithRemainder(source, stack, filled);
                }
            });
        });
    }

    private static CauldronInteraction returningSilver(CauldronInteraction interaction) {
        return (state, level, pos, player, hand, stack) -> {
            var result = interaction.interact(state, level, pos, player, hand, stack);
            if (!level.isClientSide() && !player.hasInfiniteMaterials() && player.getItemInHand(hand).is(Items.BUCKET)) {
                player.setItemInHand(hand, new ItemStack(Interstice.RIFTSILVER_BUCKET.get()));
            }
            return result;
        };
    }
}
