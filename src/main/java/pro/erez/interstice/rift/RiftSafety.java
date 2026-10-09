package pro.erez.interstice.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.worldgen.IslandWorld;

/** Real collision, fluid, hazard and world-border checks immediately before each teleport. */
public final class RiftSafety {
    private RiftSafety() {}
    public static boolean standing(ServerLevel level, BlockPos feet) {
        if (level.isOutsideBuildHeight(feet) || level.isOutsideBuildHeight(feet.above())
                || !level.getWorldBorder().isWithinBounds(feet)) return false;
        for(var cloud:level.getEntitiesOfClass(net.minecraft.world.entity.AreaEffectCloud.class,new AABB(feet).inflate(4),
                entity->entity.isAlive()&&(entity.getTags().contains(pro.erez.interstice.ecology.ClingweedGas.ENTITY_TAG)
                        ||entity.getTags().contains(pro.erez.interstice.ecology.SporePodGas.ENTITY_TAG))))
            if(cloud.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(feet))<Math.pow(cloud.getRadius()+1,2))return false;
        var floor = level.getBlockState(feet.below());
        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP) || !floor.getFluidState().isEmpty()
                || floor.is(pro.erez.interstice.minerals.MineralEcology.MINERAL_POWDER.get())
                || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)) return false;
        for (int y = 0; y <= 1; y++) {
            var state = level.getBlockState(feet.above(y));
            if (!state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                    || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE)
                    || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY) || state.is(Interstice.RIFT_PORTAL.get())) return false;
            if(state.is(pro.erez.interstice.ecology.RealmEcology.VENOM_REED.get())||state.is(pro.erez.interstice.ecology.RealmEcology.SPORE_POD.get())
                    ||state.is(pro.erez.interstice.ecology.CaveEcology.CLINGWEED.get())||state.is(pro.erez.interstice.ecology.CaveEcology.STING_FROND.get())
                    ||state.is(pro.erez.interstice.minerals.MineralEcology.MINERAL_POWDER.get())
                    ||state.is(pro.erez.interstice.worldgen.cave.CaveMaterials.ASH_SPIRE.get())||state.is(pro.erez.interstice.worldgen.cave.CaveMaterials.GARDEN_SPIRE.get())||state.is(pro.erez.interstice.worldgen.cave.CaveMaterials.VAULT_SPIRE.get()))return false;
        }
        return level.noCollision(new AABB(feet.getX() + .2, feet.getY() + .01, feet.getZ() + .2,
                feet.getX() + .8, feet.getY() + 1.91, feet.getZ() + .8));
    }
    public static BlockPos near(ServerLevel level, BlockPos hint, boolean sheltered) {
        level.getChunk(hint.getX() >> 4, hint.getZ() >> 4);
        for (int radius = 0; radius <= 5; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                BlockPos feet = hint.offset(dx, dy, dz);
                if (standing(level, feet) && (!sheltered || ShelterDetector.hasRoofAt(level, GeometryProfiles.get(level), feet.getX(), feet.getY() + 2, feet.getZ()))) return feet;
            }
        }
        return null;
    }
    public static BlockPos prepareEcho(ServerLevel level, BlockPos hint) {
        // Build only in empty space. Existing terrain and player constructions are preserved.
        level.getChunk(hint.getX() >> 4, hint.getZ() >> 4);
        for (int radius = 0; radius <= 24; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            int x = hint.getX() + dx, z = hint.getZ() + dz;
            for (int offset : new int[]{0, 1, -1, 2, -2}) {
                BlockPos feet = new BlockPos(x, hint.getY() + offset, z);
                if (canPrepareEcho(level, feet)) return buildEcho(level, feet);
            }
        }
        return null;
    }
    /** Read-only validation shared with V5 landing search, before any echo or terrain writes. */
    public static boolean canPrepareEcho(ServerLevel level, BlockPos feet) {
        if (level.isOutsideBuildHeight(feet.above(2)) || !level.getWorldBorder().isWithinBounds(feet.offset(1, 0, 1))
                || !level.getWorldBorder().isWithinBounds(feet.offset(-1, 0, -1))) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos column = feet.offset(dx, 0, dz);
            if (!level.hasChunkAt(column)) return false;
            if (IslandWorld.isIsland(level.dimension()) && !pro.erez.interstice.worldgen.IslandChunkGenerator.featureAllowed(level,
                    GeometryProfiles.get(level), column.getX(), column.getY() + 2, column.getZ())) return false;
            var floor = level.getBlockState(column.below());
            boolean step = floor.isAir() && standing(level, column.below());
            if (!standing(level, column) && !step) return false;
            for (int y = 0; y <= 2; y++) if (!level.getBlockState(column.above(y)).isAir()) return false;
        }
        return true;
    }
    private static BlockPos buildEcho(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos column = feet.offset(dx, 0, dz);
            if (level.getBlockState(column.below()).isAir()) level.setBlock(column.below(), Interstice.RIFTSTONE.get().defaultBlockState(), 3);
            level.setBlock(column.above(2), Interstice.RIFTSTONE.get().defaultBlockState(), 3);
        }
        for (int y = 0; y < 2; y++) level.setBlock(feet.offset(-1, y, 1), Interstice.RIFTSTONE.get().defaultBlockState(), 3);
        BlockPos echo = feet.east();
        level.setBlock(echo, Interstice.RIFT_ECHO.get().defaultBlockState(), 3);
        return echo;
    }
    public static BlockPos defaultHint(ServerLevel level) {
        if (IslandWorld.isIsland(level.dimension())) return IslandWorld.findLanding(level);
        BlockPos spawn = level.getSharedSpawnPos();
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
    }
}
