package pro.erez.interstice.fluid;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.OceanLiquidBlock;

/**
 * Handles cross-fluid reactions:
 * 1. Heavy Toxin + Light Toxin -> Cataclysmic annihilation explosion (14.0F radius, vaporizes local liquids).
 * 2. Heavy Toxin + Water -> Vitriolite (dark toxic slate).
 * 3. Heavy Toxin + Lava -> Pyrolith (ultra-hard volcanic cinder).
 * 4. Light Toxin + Water -> Aerolite (pale porous ethereal tuff).
 * 5. Light Toxin + Lava -> Phosphorite (vitrified green-amber crystalline slag).
 */
@EventBusSubscriber(modid = Interstice.ID)
public final class FluidReactions {
    private static final int ANNIHILATION_COOLDOWN_TICKS = 40;
    private static final Map<Level, Map<BlockPos, Long>> ANNIHILATION_COOLDOWNS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FluidReactions() {}

    public static boolean handleFluidContact(Level level, BlockPos pos, FluidState state) {
        if (state.isEmpty() || level.isClientSide()) return false;

        boolean isHeavy = state.is(Interstice.HEAVY.get()) || state.is(Interstice.HEAVY_FLOW.get());
        boolean isLight = state.is(Interstice.LIGHT.get()) || state.is(Interstice.LIGHT_FLOW.get());
        boolean isWater = state.is(FluidTags.WATER);
        boolean isLava = state.is(FluidTags.LAVA);

        if (!isHeavy && !isLight && !isWater && !isLava) return false;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            FluidState neighborFluid = level.getFluidState(neighborPos);
            if (neighborFluid.isEmpty()) continue;

            boolean nHeavy = neighborFluid.is(Interstice.HEAVY.get()) || neighborFluid.is(Interstice.HEAVY_FLOW.get());
            boolean nLight = neighborFluid.is(Interstice.LIGHT.get()) || neighborFluid.is(Interstice.LIGHT_FLOW.get());
            boolean nWater = neighborFluid.is(FluidTags.WATER);
            boolean nLava = neighborFluid.is(FluidTags.LAVA);

            // 1. Heavy Toxin + Light Toxin: Matter-antimatter annihilation!
            if ((isHeavy && nLight) || (isLight && nHeavy)) {
                if (triggerAnnihilation(level, pos, neighborPos)) {
                    return true;
                }
            }

            // 2. Heavy Toxin + Water -> Vitriolite
            if ((isHeavy && nWater) || (isWater && nHeavy)) {
                BlockPos target = pickSolidificationTarget(pos, state, neighborPos, neighborFluid, isWater);
                crystallize(level, target, Interstice.VITRIOLITE.get().defaultBlockState(),
                        SoundEvents.LAVA_EXTINGUISH, 0.4F, 1.3F);
                return true;
            }

            // 3. Heavy Toxin + Lava -> Pyrolith
            if ((isHeavy && nLava) || (isLava && nHeavy)) {
                BlockPos target = pickSolidificationTarget(pos, state, neighborPos, neighborFluid, isLava);
                crystallize(level, target, Interstice.PYROLITH.get().defaultBlockState(),
                        SoundEvents.FIRE_EXTINGUISH, 0.7F, 0.6F);
                return true;
            }

            // 4. Light Toxin + Water -> Aerolite
            if ((isLight && nWater) || (isWater && nLight)) {
                BlockPos target = pickSolidificationTarget(pos, state, neighborPos, neighborFluid, isWater);
                crystallize(level, target, Interstice.AEROLITE.get().defaultBlockState(),
                        SoundEvents.LAVA_EXTINGUISH, 0.5F, 1.8F);
                return true;
            }

            // 5. Light Toxin + Lava -> Phosphorite
            if ((isLight && nLava) || (isLava && nLight)) {
                BlockPos target = pickSolidificationTarget(pos, state, neighborPos, neighborFluid, isLava);
                crystallize(level, target, Interstice.PHOSPHORITE.get().defaultBlockState(),
                        SoundEvents.GLASS_BREAK, 0.8F, 1.5F);
                return true;
            }
        }

        return false;
    }

    /**
     * Chooses which block to solidify. If one is flowing and one is source,
     * the flowing block solidifies (just like vanilla cobblestone/basalt generators).
     */
    private static BlockPos pickSolidificationTarget(BlockPos pos1, FluidState state1,
                                                    BlockPos pos2, FluidState state2,
                                                    boolean pos1IsVanilla) {
        if (!state1.isSource() && state2.isSource()) return pos1;
        if (!state2.isSource() && state1.isSource()) return pos2;
        return pos1IsVanilla ? pos1 : pos2;
    }

    private static boolean triggerAnnihilation(Level level, BlockPos pos1, BlockPos pos2) {
        long now = level.getGameTime();
        Map<BlockPos, Long> cooldowns = ANNIHILATION_COOLDOWNS.computeIfAbsent(level, key -> new HashMap<>());
        pruneCooldowns(cooldowns, now);

        // Check if an explosion already occurred nearby within 40 ticks (2 seconds)
        for (Map.Entry<BlockPos, Long> entry : cooldowns.entrySet()) {
            if (entry.getKey().closerThan(pos1, 8.0)) {
                // Already exploded recently at this site — clear local fluids to prevent infinite loop
                clearFluidCell(level, pos1);
                clearFluidCell(level, pos2);
                return true;
            }
        }

        cooldowns.put(pos1.immutable(), now);

        double cx = (pos1.getX() + pos2.getX()) / 2.0 + 0.5;
        double cy = (pos1.getY() + pos2.getY()) / 2.0 + 0.5;
        double cz = (pos1.getZ() + pos2.getZ()) / 2.0 + 0.5;

        // Vaporize all adjacent reacting fluids in radius 3 to consume the colliding matter
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int originX = pos1.getX();
        int originY = pos1.getY();
        int originZ = pos1.getZ();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    cursor.set(originX + dx, originY + dy, originZ + dz);
                    if (!level.isLoaded(cursor)) continue;
                    BlockState bs = level.getBlockState(cursor);
                    if (bs.getBlock() instanceof OceanLiquidBlock) continue; // Never destroy monolithic ceiling ocean

                    boolean isToxin = bs.is(Interstice.HEAVY_BLOCK.get()) || bs.is(Interstice.LIGHT_BLOCK.get())
                            || bs.getFluidState().is(Interstice.HEAVY.get()) || bs.getFluidState().is(Interstice.LIGHT.get())
                            || bs.getFluidState().is(Interstice.HEAVY_FLOW.get()) || bs.getFluidState().is(Interstice.LIGHT_FLOW.get());
                    if (isToxin) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        // Cataclysmic 14.0F explosion (~10-12 TNTs!)
        level.explode(null, cx, cy, cz, 14.0F, Level.ExplosionInteraction.BLOCK);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, cx, cy, cz, 1, 0, 0, 0, 0);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 3, 0.5, 0.5, 0.5, 0.1);
            serverLevel.playSound(null, cx, cy, cz, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 2.0F, 0.6F);

            for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(cx, cy, cz) < 64.0 * 64.0) {
                    pro.erez.interstice.item.CorrosiveBucketHandler.awardAdvancement(player, "matter_annihilation");
                }
            }
        }

        return true;
    }

    private static void pruneCooldowns(Map<BlockPos, Long> cooldowns, long now) {
        cooldowns.values().removeIf(tick -> tick > now || now - tick >= ANNIHILATION_COOLDOWN_TICKS);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide() || level.getGameTime() % 20 != 0) return;
        Map<BlockPos, Long> cooldowns = ANNIHILATION_COOLDOWNS.get(level);
        if (cooldowns == null) return;
        pruneCooldowns(cooldowns, level.getGameTime());
        if (cooldowns.isEmpty()) ANNIHILATION_COOLDOWNS.remove(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        ANNIHILATION_COOLDOWNS.remove(event.getLevel());
    }

    private static void clearFluidCell(Level level, BlockPos pos) {
        BlockState bs = level.getBlockState(pos);
        if (!(bs.getBlock() instanceof OceanLiquidBlock)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void crystallize(Level level, BlockPos pos, BlockState solidState,
                                    net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        BlockState current = level.getBlockState(pos);
        if (current.getBlock() instanceof OceanLiquidBlock) return; // Protect monolithic upper ocean

        level.setBlock(pos, solidState, 3);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    6, 0.2, 0.2, 0.2, 0.03);
            serverLevel.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    4, 0.15, 0.15, 0.15, 0.02);
        }
    }
}
