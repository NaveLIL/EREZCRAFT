package pro.erez.interstice.item;

import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import pro.erez.interstice.Interstice;

public final class CorrosiveBucketHandler {
    private CorrosiveBucketHandler() {}

    public static void tickCorrosion(ItemStack stack, Level level, Entity entity,
                                     Supplier<? extends Block> fluidBlockSupplier, boolean isLight) {
        if (level.isClientSide) return;
        if (!(entity instanceof Player player) || player.isCreative()) return;

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        int ticks = tag.getInt("CorrosionTicks") + 1;
        tag.putInt("CorrosionTicks", ticks);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        ServerLevel serverLevel = (ServerLevel) level;

        // Audio and particle warning progression
        if (ticks % 20 == 0 && ticks < 100) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.4F + (ticks / 100.0F));
            serverLevel.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.6, player.getZ(),
                    4, 0.2, 0.2, 0.2, 0.02);
        }
        if (ticks == 80) {
            // 1-second critical warning
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.9F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.6, player.getZ(),
                    8, 0.25, 0.3, 0.25, 0.04);
        }

        if (ticks >= 100) {
            // Bucket destroyed by acid corrosion!
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 0.5F, 1.6F);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.6, player.getZ(),
                    14, 0.3, 0.3, 0.3, 0.05);

            stack.shrink(1);

            // Spill liquid under player's feet
            Block fluidBlock = fluidBlockSupplier.get();
            BlockPos footPos = player.blockPosition();
            if (level.getBlockState(footPos).canBeReplaced()) {
                level.setBlock(footPos, fluidBlock.defaultBlockState(), 3);
            } else if (level.getBlockState(footPos.above()).canBeReplaced()) {
                level.setBlock(footPos.above(), fluidBlock.defaultBlockState(), 3);
            } else {
                level.setBlock(footPos, fluidBlock.defaultBlockState(), 3);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.interstice.bucket_corroded")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        true
                );
                awardAdvancement(serverPlayer, "corrosive_lesson");
            }
        }
    }

    public static void tickDroppedCorrosion(ItemStack stack, Level level, net.minecraft.world.entity.item.ItemEntity entity,
                                            Supplier<? extends Block> fluidBlockSupplier, boolean isLight) {
        if (level.isClientSide) return;
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        int ticks = tag.getInt("CorrosionTicks") + 1;
        tag.putInt("CorrosionTicks", ticks);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        ServerLevel serverLevel = (ServerLevel) level;
        if (ticks % 20 == 0 && ticks < 100) {
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F + (ticks / 100.0F));
            serverLevel.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 0.2, entity.getZ(),
                    3, 0.1, 0.1, 0.1, 0.02);
        }
        if (ticks >= 100) {
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 0.5F, 1.6F);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY() + 0.2, entity.getZ(),
                    10, 0.2, 0.2, 0.2, 0.05);

            Block fluidBlock = fluidBlockSupplier.get();
            BlockPos pos = entity.blockPosition();
            if (level.getBlockState(pos).canBeReplaced()) {
                level.setBlock(pos, fluidBlock.defaultBlockState(), 3);
            } else if (level.getBlockState(pos.above()).canBeReplaced()) {
                level.setBlock(pos.above(), fluidBlock.defaultBlockState(), 3);
            }
            entity.discard();
        }
    }

    public static void awardAdvancement(ServerPlayer player, String advancementName) {
        AdvancementHolder holder = player.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(Interstice.ID, advancementName));
        if (holder != null) {
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            if (!progress.isDone()) {
                for (String criteria : progress.getRemainingCriteria()) {
                    player.getAdvancements().award(holder, criteria);
                }
            }
        }
    }
}
