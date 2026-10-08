package pro.erez.interstice.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** V4-only feature budgets. Immutable global-cell selection prevents clusters and chunk-order drift. */
public final class FeatureDistribution {
    public static final int STRUCTURE_CELL_CHUNKS = 16;
    public static final int STRUCTURE_MARGIN_CHUNKS = 4;
    public static final int MIN_STRUCTURE_SEPARATION_CHUNKS = STRUCTURE_MARGIN_CHUNKS * 2 + 1;
    public enum Structure { WATCHPOST, STONE_VAULT }
    public record Slot(Structure structure, ChunkPos chunk) {}
    private FeatureDistribution() {}
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
    private static long cellSeed(long seed, int x, int z) {
        return mix(seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ 0x5634464541545552L);
    }
    private static double unit(long value) { return (mix(value) >>> 11) * 0x1.0p-53; }
    /** One shared structure slot, chosen from the original feature's valid candidate chunks. */
    public static Slot slot(long seed, int cellX, int cellZ) {
        long key = cellSeed(seed, cellX, cellZ);
        Structure structure = unit(key ^ 0x54595045L) < .4 ? Structure.WATCHPOST : Structure.STONE_VAULT;
        int startX = cellX * STRUCTURE_CELL_CHUNKS, startZ = cellZ * STRUCTURE_CELL_CHUNKS;
        ChunkPos selected = null; long best = -1L;
        for (int x = startX + STRUCTURE_MARGIN_CHUNKS; x < startX + STRUCTURE_CELL_CHUNKS - STRUCTURE_MARGIN_CHUNKS; x++)
            for (int z = startZ + STRUCTURE_MARGIN_CHUNKS; z < startZ + STRUCTURE_CELL_CHUNKS - STRUCTURE_MARGIN_CHUNKS; z++) {
                boolean legacy = structure == Structure.WATCHPOST ? WatchpostRuins.candidate(seed, x, z) : StoneVaults.candidate(seed, x, z);
                if (!legacy) continue;
                long rank = mix(key ^ ChunkPos.asLong(x, z) ^ 0x414E43484F52L);
                if (selected == null || Long.compareUnsigned(rank, best) < 0) { selected = new ChunkPos(x, z); best = rank; }
            }
        return selected == null ? null : new Slot(structure, selected);
    }
    public static boolean structureAllowed(int revision, long seed, int chunkX, int chunkZ, Structure kind, boolean garden) {
        if (revision < 4) return true;
        if (!(kind == Structure.WATCHPOST ? WatchpostRuins.candidate(seed, chunkX, chunkZ) : StoneVaults.candidate(seed, chunkX, chunkZ))) return false;
        int cellX = Math.floorDiv(chunkX, STRUCTURE_CELL_CHUNKS), cellZ = Math.floorDiv(chunkZ, STRUCTURE_CELL_CHUNKS);
        var selected = slot(seed, cellX, cellZ);
        if (selected == null || selected.structure != kind || selected.chunk.x != chunkX || selected.chunk.z != chunkZ) return false;
        // The same anchor grid for both biomes/types preserves spacing across their boundaries.
        return unit(cellSeed(seed, cellX, cellZ) ^ 0x414343455054L) < (garden ? .25 : .8);
    }
    public static boolean watchpostAllowed(int revision, long seed, int chunkX, int chunkZ, boolean garden) {
        return structureAllowed(revision, seed, chunkX, chunkZ, Structure.WATCHPOST, garden);
    }
    public static boolean stoneVaultAllowed(int revision, long seed, int chunkX, int chunkZ, boolean garden) {
        return structureAllowed(revision, seed, chunkX, chunkZ, Structure.STONE_VAULT, garden);
    }
    /** Keep original4..7 scatter attempts in retained chunks; the overall budget is1/7. */
    public static boolean tideSproutsAllowed(int revision, long seed, int chunkX, int chunkZ) {
        return revision < 4 || Math.floorMod(mix(seed ^ ChunkPos.asLong(chunkX, chunkZ) ^ 0x56345350524F5554L), 7) == 0;
    }
    /** Independent deterministic thinning of safe/stinging plants only; never used by clingweed. */
    public static boolean cavePlantAllowed(int revision, long seed, BlockPos pos, boolean hanging) {
        if (revision < 4) return true;
        long key = seed ^ pos.getX() * 0x9E3779B97F4A7C15L ^ pos.getZ() * 0xC2B2AE3D27D4EB4FL
                ^ pos.getY() * 0xD1B54A32D192ED03L ^ (hanging ? 0x50454E44414E54L : 0x464C4F4F52504CL);
        return Math.floorMod(mix(key), 4) == 0;
    }
}
