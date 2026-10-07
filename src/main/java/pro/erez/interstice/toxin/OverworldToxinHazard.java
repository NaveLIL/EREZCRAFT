package pro.erez.interstice.toxin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import pro.erez.interstice.worldgen.IslandWorld;

/**
 * Governs the destructive environmental behavior of Interstice alien toxins when
 * introduced into the Overworld (or other foreign ecosystems).
 *
 * In the Interstice realm, the native stone and flora are immune.
 * In the Overworld, alien toxins are a hazardous pollutant:
 * - Foliage, flowers, crops, and saplings wither and dissolve into smoke.
 * - Soil steadily degrades: grass/farmland -> dirt -> coarse dirt -> toxic mud -> gravel.
 * - Stone and minerals corrode: stone/bricks -> cobblestone/cracked bricks -> gravel.
 * - Contact with vanilla water boils the water away in sizzling steam.
 * - Caustic fumes rise from the surface, inflicting poison on creatures breathing the air.
 */
public final class OverworldToxinHazard {
    private OverworldToxinHazard() {}

    public static boolean isHazardousDimension(Level level) {
        return !IslandWorld.isIsland(level.dimension());
    }

    public static void corrodeEnvironment(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isHazardousDimension(level)) return;

        // Corrode random adjacent neighbors
        for (int i = 0; i < 2; i++) {
            int dx = random.nextIntBetweenInclusive(-1, 1);
            int dy = random.nextIntBetweenInclusive(-1, 1);
            int dz = random.nextIntBetweenInclusive(-1, 1);
            if (dx == 0 && dy == 0 && dz == 0) continue;

            BlockPos target = pos.offset(dx, dy, dz);
            if (!level.isLoaded(target)) continue;

            BlockState state = level.getBlockState(target);
            if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
                // If it's vanilla water, alien toxin violently boils it away
                if (state.is(Blocks.WATER)) {
                    level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                    level.playSound(null, target, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.6F);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                            6, 0.2, 0.2, 0.2, 0.03);
                }
                continue;
            }

            // 1. Plant life withers and dissolves instantly
            if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.SAPLINGS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                    || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.VINE)
                    || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.MOSS_CARPET)
                    || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.DEAD_BUSH)) {
                level.destroyBlock(target, false);
                level.playSound(null, target, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 1.8F);
                level.sendParticles(ParticleTypes.SMOKE, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                        4, 0.1, 0.1, 0.1, 0.02);
                continue;
            }

            // 2. Soil degradation: grass -> dirt -> coarse dirt -> toxic mud -> gravel
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)) {
                level.setBlock(target, Blocks.DIRT.defaultBlockState(), 3);
                playCorrosionEffects(level, target);
            } else if (state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
                level.setBlock(target, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                playCorrosionEffects(level, target);
            } else if (state.is(Blocks.COARSE_DIRT)) {
                level.setBlock(target, Blocks.MUD.defaultBlockState(), 3);
                playCorrosionEffects(level, target);
            } else if (state.is(Blocks.MUD)) {
                level.setBlock(target, Blocks.GRAVEL.defaultBlockState(), 3);
                playCorrosionEffects(level, target);
            }
            // 3. Stone & Mineral corrosion: stone -> cobblestone -> gravel
            else if (state.is(Blocks.STONE)) {
                level.setBlock(target, Blocks.COBBLESTONE.defaultBlockState(), 3);
                playCorrosionEffects(level, target);
            } else if (state.is(Blocks.STONE_BRICKS)) {
                level.setBlock(target, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 3);
                playCorrosionEffects(level, target);
            } else if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.CRACKED_STONE_BRICKS) || state.is(Blocks.MOSSY_COBBLESTONE)) {
                level.setBlock(target, Blocks.GRAVEL.defaultBlockState(), 3);
                playCorrosionEffects(level, target);
            } else if (state.is(Blocks.SANDSTONE)) {
                level.setBlock(target, Blocks.SAND.defaultBlockState(), 3);
                playCorrosionEffects(level, target);
            }
        }
    }

    private static void playCorrosionEffects(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.4F);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                3, 0.1, 0.1, 0.1, 0.01);
    }

    public static void animateFumes(Level level, BlockPos pos, RandomSource random) {
        if (!isHazardousDimension(level)) return;

        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.getX() + random.nextDouble(), pos.getY() + 0.95, pos.getZ() + random.nextDouble(),
                    0, 0.02, 0);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.SNEEZE,
                    pos.getX() + random.nextDouble(), pos.getY() + 0.9, pos.getZ() + random.nextDouble(),
                    0, 0.01, 0);
        }
        if (random.nextInt(12) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.2F, 1.7F, false);
        }
    }

    public static void applyVaporHazard(LivingEntity living, Level level) {
        if (!isHazardousDimension(level)) return;
        living.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, true, true));
    }
}
