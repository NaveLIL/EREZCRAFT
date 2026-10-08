package pro.erez.interstice.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;

/** Chunk-local geological arches: full validation before placement, no carving or cross-chunk writes. */
public final class StoneVaults {
    private StoneVaults() {}
    public static boolean candidate(long seed, int chunkX, int chunkZ) {
        return random(seed, chunkX, chunkZ).nextInt(4) == 0;
    }
    private static RandomSource random(long seed, int x, int z) {
        return RandomSource.create(seed ^ (x * 341873128712L) ^ (z * 132897987541L) ^ 0x57A0E13L);
    }
    public static boolean isGround(BlockState state) {
        return state.is(Interstice.RIFTSTONE.get()) || state.is(Interstice.ABYSSAL_TURF.get())
                || state.is(Interstice.RIFTSILVER_ORE.get()) || state.is(VaultMaterials.VAULTSTONE.get())
                || state.is(VaultMaterials.WEATHERED_VAULTSTONE.get()) || state.is(GardenMaterials.PALESTONE.get());
    }
    public static Map<BlockPos, BlockState> plan(BlockPos root, int height, boolean alongX, int shape) {
        if (height < 5 || height > 7 || shape < 0 || shape > 2) throw new IllegalArgumentException("Invalid stone vault form");
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (int u = -4; u <= 4; u++) for (int v = -2; v <= 2; v++) {
            // Flat central roof, stepped shoulders and two connected buttresses.
            int roof = height - Math.max(0, Math.abs(u) - 2) - (shape == 1 && u >= 2 ? 1 : 0);
            if (shape == 2 && Math.abs(v) == 2 && Math.abs(u) < 3) roof--;
            put(result, root, alongX, u, roof, v, Math.abs(u) >= 3 || Math.abs(v) == 2);
            put(result, root, alongX, u, roof - 1, v, Math.abs(v) == 2);
            if (Math.abs(u) == 4) for (int y = 0; y < roof; y++) put(result, root, alongX, u, y, v, y % 3 == 1);
            if (Math.abs(u) == 3 && Math.abs(v) == 2) for (int y = 0; y < roof; y++) put(result, root, alongX, u, y, v, true);
            if (shape == 2 && Math.abs(u) <= 2 && v == 0) put(result, root, alongX, u, roof + 1, v, false);
        }
        return result;
    }
    private static void put(Map<BlockPos, BlockState> plan, BlockPos root, boolean x, int u, int y, int v, boolean weathered) {
        plan.put(root.offset(x ? u : v, y, x ? v : u), (weathered ? VaultMaterials.WEATHERED_VAULTSTONE : VaultMaterials.VAULTSTONE).get().defaultBlockState());
    }
    public static boolean place(GeometryProfile profile, ChunkAccess chunk, BlockPos root, int height, boolean alongX, int shape) {
        Map<BlockPos, BlockState> cells = plan(root, height, alongX, shape);
        // The whole walkable footprint must be supported, including the opening.
        for (int u = -4; u <= 4; u++) for (int v = -2; v <= 2; v++) {
            BlockPos foot = root.offset(alongX ? u : v, 0, alongX ? v : u);
            if (!inside(chunk, foot)) return false;
            int ground = -1;
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos p = foot.below(dy);
                if (!IslandChunkGenerator.landAllowed(profile, p.getX(), p.getY(), p.getZ())) return false;
                var existing = chunk.getBlockState(p);
                if (isGround(existing)) { ground = dy; break; }
                if (!existing.isAir()) return false;
            }
            if (ground == -1) return false;
            // Extend only actual supports, never fill the open passage.
            if (cells.containsKey(foot)) for (int dy = 1; dy < ground; dy++) cells.put(foot.below(dy), VaultMaterials.VAULTSTONE.get().defaultBlockState());
            for (int y = 0; y <= height + 1; y++) {
                BlockPos p = foot.above(y);
                if (!chunk.getBlockState(p).isAir()) return false;
            }
        }
        for (BlockPos p : cells.keySet()) if (!inside(chunk, p)
                || !IslandChunkGenerator.landAllowed(profile, p.getX(), p.getY(), p.getZ())
                || !chunk.getBlockState(p).isAir()) return false;
        cells.forEach((p, state) -> chunk.setBlockState(p, state, false));
        return true;
    }
    private static boolean inside(ChunkAccess chunk, BlockPos p) {
        return p.getX() >= chunk.getPos().getMinBlockX() && p.getX() <= chunk.getPos().getMaxBlockX()
                && p.getZ() >= chunk.getPos().getMinBlockZ() && p.getZ() <= chunk.getPos().getMaxBlockZ();
    }
    public static boolean generate(GeometryProfile profile, ChunkAccess chunk, long seed) {
        var random = random(seed, chunk.getPos().x, chunk.getPos().z);
        if (random.nextInt(4) != 0) return false;
        int height = 5 + random.nextInt(3), shape = random.nextInt(3);
        boolean alongX = random.nextBoolean();
        BlockPos center = new BlockPos(chunk.getPos().getMinBlockX() + 7, 0, chunk.getPos().getMinBlockZ() + 7);
        // Work top-down, but try every exposed tier: high islands can have insufficient headroom.
        for (int y = profile.maxLand(); y >= profile.minLand(); y--) {
            BlockPos ground = center.atY(y);
            if (!isGround(chunk.getBlockState(ground)) || !chunk.getBlockState(ground.above()).isAir()
                    || !RealmBiomes.isVault(chunk, ground)) continue;
            if (place(profile, chunk, ground.above(), height, alongX, shape)) return true;
        }
        return false;
    }
    public static void geologicalSurface(GeometryProfile profile, ChunkAccess chunk, long seed) {
        var pos = new BlockPos.MutableBlockPos();
        int startX = chunk.getPos().getMinBlockX(), startZ = chunk.getPos().getMinBlockZ();
        for (int x = startX; x < startX + 16; x++) for (int z = startZ; z < startZ + 16; z++) {
            int depth = 0;
            for (int y = profile.maxLand(); y >= profile.minLand(); y--) {
                pos.set(x, y, z);
                var state = chunk.getBlockState(pos);
                if (state.isAir()) { depth = 0; continue; }
                if (!isGround(state)) { depth = 99; continue; }
                if (depth < 6 && !state.is(Interstice.RIFTSILVER_ORE.get()) && RealmBiomes.isVault(chunk, pos)) {
                    // Connected pockets retain the existing flora rather than replacing its identity.
                    double pocket = Math.sin(x * .15 + (seed & 255)) + Math.cos(z * .17 - (seed & 127));
                    if (!(state.is(Interstice.ABYSSAL_TURF.get()) && pocket > 1.35)) {
                        boolean weathered = depth < 2 || Math.floorMod(y + (x / 6) - (z / 7), 7) == 0;
                        chunk.setBlockState(pos, (weathered ? VaultMaterials.WEATHERED_VAULTSTONE : VaultMaterials.VAULTSTONE).get().defaultBlockState(), false);
                    }
                }
                depth++;
            }
        }
    }
}
