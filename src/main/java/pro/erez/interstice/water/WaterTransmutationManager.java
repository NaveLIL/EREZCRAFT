package pro.erez.interstice.water;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.OceanLiquidBlock;
import pro.erez.interstice.worldgen.IslandWorld;

/**
 * Controller for water rejection and transmutation in the Interstice realm.
 *
 * Water cannot exist in the Interstice atmosphere.
 * When introduced (via bucket, dispenser, melting ice, or fluid flow), it violently
 * reacts with hissing steam and smoke, transmuting into an unstable source of light toxin.
 * After 10 seconds (200 ticks), this unstable toxin boils away and completely vanishes.
 *
 * Non-island dimensions (Overworld, Nether, End, FluidLab) are completely untouched.
 * Zero-overhead fast path when no water is active.
 */
@EventBusSubscriber(modid = Interstice.ID)
public final class WaterTransmutationManager {
    public static final int EVAPORATION_TICKS = 200; // 10.0 seconds

    private WaterTransmutationManager() {}

    /**
     * Intercepts emptying a water bucket in Island dimensions.
     * @return true if handled (cancels normal water placement).
     */
    public static boolean handleBucketEmpty(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult hit) {
        if (!IslandWorld.isIsland(level.dimension())) return false;

        BlockState clickedState = level.getBlockState(pos);
        BlockPos targetPos;
        if (clickedState.canBeReplaced() || clickedState.is(Blocks.WATER)) {
            targetPos = pos;
        } else if (hit != null) {
            targetPos = pos.relative(hit.getDirection());
        } else {
            targetPos = pos.above();
        }

        BlockState targetState = level.getBlockState(targetPos);
        boolean canPlace = targetState.isAir() || targetState.canBeReplaced()
                || targetState.is(Blocks.WATER) || targetState.getBlock() == Interstice.LIGHT_BLOCK.get();
        if (!canPlace) return false;

        transmuteAt(level, targetPos, player);
        return true;
    }

    /**
     * Violently transmutes water at pos into unstable light toxin with steam and sound.
     */
    public static void transmuteAt(Level level, BlockPos pos, @Nullable Player player) {
        if (!IslandWorld.isIsland(level.dimension())) return;

        playTransmuteEffects(level, pos);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            serverLevel.setBlock(pos, Interstice.LIGHT_BLOCK.get().defaultBlockState(), 3);
            WaterTransmutationSavedData.get(serverLevel).addExpiring(pos, serverLevel.getGameTime() + EVAPORATION_TICKS);
        }
    }

    /**
     * Emits violent hissing steam, smoke, and sound effects for water transmutation.
     */
    public static void playTransmuteEffects(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.9F,
                1.2F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F,
                1.8F + (level.random.nextFloat() - level.random.nextFloat()) * 0.3F);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    12, 0.25, 0.25, 0.25, 0.05);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                    6, 0.15, 0.15, 0.15, 0.03);
            serverLevel.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    4, 0.2, 0.2, 0.2, 0.02);
        } else {
            for (int i = 0; i < 8; i++) {
                level.addParticle(ParticleTypes.LARGE_SMOKE,
                        pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5,
                        pos.getY() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5,
                        pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5,
                        0, 0.02, 0);
            }
        }
    }

    /**
     * Prevents filling cauldrons with water in Island dimensions.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!IslandWorld.isIsland(level.dimension())) return;
        ItemStack stack = event.getItemStack();
        boolean riftsilver = stack.is(Interstice.RIFTSILVER_WATER_BUCKET.get());
        if (!stack.is(Items.WATER_BUCKET) && !riftsilver) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AbstractCauldronBlock) {
            event.setCanceled(true);
            Player player = event.getEntity();
            BlockPos above = pos.above();

            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                if (serverLevel.getBlockState(above).canBeReplaced()) {
                    transmuteAt(serverLevel, above, player);
                } else {
                    playTransmuteEffects(serverLevel, pos);
                }
                if (player != null && !player.isCreative()) {
                    stack.shrink(1);
                    ItemStack emptyBucket = new ItemStack(riftsilver ? Interstice.RIFTSILVER_BUCKET.get() : Items.BUCKET);
                    if (!player.getInventory().add(emptyBucket)) {
                        player.drop(emptyBucket, false);
                    }
                }
            } else {
                playTransmuteEffects(level, pos);
                if (player != null) {
                    player.swing(event.getHand());
                }
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        }
    }

    /**
     * Server tick: checks and evaporates expired transmutation sites.
     * Zero-overhead fast path: if no blocks are expiring, returns immediately.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (!IslandWorld.isIsland(level.dimension())) continue;
            WaterTransmutationSavedData data = WaterTransmutationSavedData.get(level);
            if (data.isEmpty()) continue; // Zero tick overhead!

            long now = level.getGameTime();
            var iterator = data.getExpiringBlocks().entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (now >= entry.getValue()) {
                    BlockPos pos = entry.getKey();
                    // Retain the persisted deadline until the chunk is available again.
                    // Reading or generating an unloaded chunk here would force it to stay active.
                    if (!level.isLoaded(pos)) continue;
                    evaporateAt(level, pos);
                    iterator.remove();
                    data.setDirty();
                }
            }
        }
    }

    /**
     * Evaporates the light toxin block and its vertical ascending plume into air.
     */
    private static void evaporateAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean evaporatedAny = false;

        for (int dy = 0; dy <= 8; dy++) {
            cursor.set(pos.getX(), pos.getY() + dy, pos.getZ());
            if (!level.isLoaded(cursor)) break;
            BlockState state = level.getBlockState(cursor);

            // Crucial safety check: NEVER touch the monolithic upper sea
            if (state.getBlock() instanceof OceanLiquidBlock) break;

            if (state.getBlock() == Interstice.LIGHT_BLOCK.get() || state.getFluidState().is(Interstice.LIGHT.get())) {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5,
                        4, 0.2, 0.2, 0.2, 0.03);
                evaporatedAny = true;
            } else if (!state.isAir()) {
                break; // Hit non-fluid solid ceiling
            }
        }

        if (evaporatedAny) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F,
                    2.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.3F);
            level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    6, 0.2, 0.2, 0.2, 0.02);
        }
    }
}
