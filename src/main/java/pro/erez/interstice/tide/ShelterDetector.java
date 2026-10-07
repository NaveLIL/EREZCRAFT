package pro.erez.interstice.tide;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;

/**
 * Validates genuine physical shelter roofs between entities and the upper toxic sea.
 * Does not rely on canSeeSky or the top heightmap, since the upper ocean and technical
 * bedrock shell enclose the entire dimension.
 */
public final class ShelterDetector {
    private ShelterDetector() {}

    /**
     * Determines whether a given block state acts as a shelter roof against the tide.
     * Solid blocks, transparent barriers (glass), slabs, stairs, and foliage (leaves) count.
     * Fluids, air, technical bedrock, and the upper toxic sea do NOT count as shelter.
     */
    public static boolean isShelteringBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.is(Blocks.BEDROCK)) return false;
        if (state.is(Interstice.LIGHT_SEA.get()) || state.is(Interstice.HEAVY_BLOCK.get())) return false;

        if (state.blocksMotion()) return true;
        var block = state.getBlock();
        return block instanceof TransparentBlock
                || block instanceof LeavesBlock
                || block instanceof SlabBlock
                || block instanceof StairBlock
                || state.is(BlockTags.LEAVES);
    }

    /**
     * Checks if a single vertical column at (x, z) has a sheltering roof above startY.
     * The scan terminates at the lower surface of the upper toxic sea (minus clearance).
     */
    public static boolean hasRoofAt(Level level, GeometryProfile profile, int x, int startY, int z) {
        int maxRoofY = (int) Math.floor(SeaSurface.cellMinimum(profile, x, z, true)) - profile.clearance();
        if (startY > maxRoofY) return false;

        var pos = new BlockPos.MutableBlockPos();
        for (int y = startY; y <= maxRoofY; y++) {
            pos.set(x, y, z);
            var state = level.getBlockState(pos);
            if (isShelteringBlock(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if an entity is fully sheltered from the buoyant tide draft.
     * Verifies all block columns covered by the entity's bounding box footprint.
     */
    public static boolean isSheltered(Level level, Entity entity) {
        if (level == null || entity == null) return false;
        GeometryProfile profile = GeometryProfiles.get(level);
        var box = entity.getBoundingBox();

        int minX = (int) Math.floor(box.minX + 0.05);
        int maxX = (int) Math.floor(box.maxX - 0.05);
        int minZ = (int) Math.floor(box.minZ + 0.05);
        int maxZ = (int) Math.floor(box.maxZ - 0.05);
        if (maxX < minX) {
            minX = (int) Math.floor(box.getCenter().x);
            maxX = minX;
        }
        if (maxZ < minZ) {
            minZ = (int) Math.floor(box.getCenter().z);
            maxZ = minZ;
        }
        int startY = (int) Math.floor(box.maxY);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!hasRoofAt(level, profile, x, startY, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
