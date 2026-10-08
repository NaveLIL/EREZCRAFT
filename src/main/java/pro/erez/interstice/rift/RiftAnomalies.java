package pro.erez.interstice.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import pro.erez.interstice.Interstice;

/** Two Overworld discoveries need no Interstice resources. A post-expedition lens makes either repeatable. */
@EventBusSubscriber(modid = Interstice.ID)
public final class RiftAnomalies {
    public static final int FISHING_CHANCE = 32;
    public static final int CAULDRON_CHANCE = 8;
    public static final TagKey<Block> CONDUCTORS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Interstice.ID, "rift_conductors"));
    private RiftAnomalies() {}
    @SubscribeEvent public static void fish(ItemFishedEvent event) {
        if (!event.isCanceled() && !event.getDrops().isEmpty() && event.getEntity() instanceof ServerPlayer player)
            tryFishing(player, event.getHookEntity(), player.getRandom());
    }
    public static boolean tryFishing(ServerPlayer player, FishingHook hook, RandomSource random) {
        if (!player.level().dimension().equals(Level.OVERWORLD) || hook.getPlayerOwner() != player || hook.level() != player.level()
                || !player.level().getFluidState(hook.blockPosition()).is(FluidTags.WATER)) return false;
        boolean lens = player.getOffhandItem().is(Interstice.RIFT_LENS.get());
        if (!lens && (!player.serverLevel().isThundering() || !player.serverLevel().canSeeSky(hook.blockPosition().above()) || random.nextInt(FISHING_CHANCE) != 0)) return false;
        return RiftTravel.requestAnomaly(player, RiftLinks.Kind.FISHING);
    }
    @SubscribeEvent public static void cauldron(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) return;
        var held = player.getMainHandItem();
        if (!held.is(Items.BUCKET) && !held.is(Interstice.RIFTSILVER_BUCKET.get())) return;
        tryCauldron(player, event.getPos(), player.getOffhandItem().is(Interstice.RIFT_LENS.get()), player.getRandom());
        // Vanilla still fills the bucket; transfer waits until that operation has completed.
    }
    public static boolean chargedCauldron(ServerPlayer player, BlockPos pos) { return tryCauldron(player, pos, true, player.getRandom()); }
    public static boolean tryCauldron(ServerPlayer player, BlockPos pos, boolean charged, RandomSource random) {
        if (!player.level().dimension().equals(Level.OVERWORLD)) return false;
        var state = player.serverLevel().getBlockState(pos);
        if (!state.is(Blocks.WATER_CAULDRON) || state.getValue(LayeredCauldronBlock.LEVEL) != 3 || !player.serverLevel().getBlockState(pos.below()).is(CONDUCTORS)) return false;
        if (charged && !player.getMainHandItem().is(Interstice.RIFT_LENS.get()) && !player.getOffhandItem().is(Interstice.RIFT_LENS.get())) return false;
        if (!charged && (!player.serverLevel().isThundering() || random.nextInt(CAULDRON_CHANCE) != 0)) return false;
        return RiftTravel.requestAnomaly(player, RiftLinks.Kind.CAULDRON);
    }
}
