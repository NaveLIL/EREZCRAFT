package pro.erez.interstice.fluid;

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
import pro.erez.interstice.Interstice;
import pro.erez.interstice.OceanLiquidBlock;

/**
 * Handles cross-fluid reactions:
 * 1. Heavy Toxin + Light Toxin -> Cataclysmic annihilation explosion (14.0F radius).
 * 2. Heavy Toxin + Water -> Vitriolite (dark toxic slate).
 * 3. Heavy Toxin + Lava -> Pyrolith (ultra-hard volcanic cinder).
 * 4. Light Toxin + Water -> Aerolite (pale porous ethereal tuff).
 * 5. Light Toxin + Lava -> Phosphorite (vitrified green-amber crystalline slag).
 */
public final class FluidReactions {
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
                triggerAnnihilation(level, pos, neighborPos);
                return true;
            }

            // 2. Heavy Toxin + Water -> Vitriolite
            if ((isHeavy && nWater) || (isWater && nHeavy)) {
                BlockPos target = isWater ? pos : neighborPos;
                crystallize(level, target, Interstice.VITRIOLITE.get().defaultBlockState(),
                        SoundEvents.LAVA_EXTINGUISH, 0.4F, 1.3F);
                return true;
            }

            // 3. Heavy Toxin + Lava -> Pyrolith
            if ((isHeavy && nLava) || (isLava && nHeavy)) {
                BlockPos target = isLava ? pos : neighborPos;
                crystallize(level, target, Interstice.PYROLITH.get().defaultBlockState(),
                        SoundEvents.FIRE_EXTINGUISH, 0.7F, 0.6F);
                return true;
            }

            // 4. Light Toxin + Water -> Aerolite
            if ((isLight && nWater) || (isWater && nLight)) {
                BlockPos target = isWater ? pos : neighborPos;
                crystallize(level, target, Interstice.AEROLITE.get().defaultBlockState(),
                        SoundEvents.LAVA_EXTINGUISH, 0.5F, 1.8F);
                return true;
            }

            // 5. Light Toxin + Lava -> Phosphorite
            if ((isLight && nLava) || (isLava && nLight)) {
                BlockPos target = isLava ? pos : neighborPos;
                crystallize(level, target, Interstice.PHOSPHORITE.get().defaultBlockState(),
                        SoundEvents.GLASS_BREAK, 0.8F, 1.5F);
                return true;
            }
        }

        return false;
    }

    private static void triggerAnnihilation(Level level, BlockPos pos1, BlockPos pos2) {
        // Discard both colliding liquid cells
        BlockState bs1 = level.getBlockState(pos1);
        BlockState bs2 = level.getBlockState(pos2);
        if (!(bs1.getBlock() instanceof OceanLiquidBlock)) {
            level.setBlock(pos1, Blocks.AIR.defaultBlockState(), 3);
        }
        if (!(bs2.getBlock() instanceof OceanLiquidBlock)) {
            level.setBlock(pos2, Blocks.AIR.defaultBlockState(), 3);
        }

        double cx = (pos1.getX() + pos2.getX()) / 2.0 + 0.5;
        double cy = (pos1.getY() + pos2.getY()) / 2.0 + 0.5;
        double cz = (pos1.getZ() + pos2.getZ()) / 2.0 + 0.5;

        // Cataclysmic 14.0F explosion (~10-12 TNTs!)
        level.explode(null, cx, cy, cz, 14.0F, Level.ExplosionInteraction.BLOCK);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, cx, cy, cz, 1, 0, 0, 0, 0);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 3, 0.5, 0.5, 0.5, 0.1);
            serverLevel.playSound(null, cx, cy, cz, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 2.0F, 0.6F);
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
