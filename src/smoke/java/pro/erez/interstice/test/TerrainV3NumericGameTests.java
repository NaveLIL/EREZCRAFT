package pro.erez.interstice.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.terrain.TerrainV2;
import pro.erez.interstice.worldgen.terrain.TerrainV3;

/** Numeric morphology checks. New native screenshots and safe-entry checks remain separate. */
@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class TerrainV3NumericGameTests {
    private static final long[] SEEDS = {0, 20261006L, 76198123L};
    private static final GeometryProfile PROFILE = GeometryProfile.TALL;
    private static RandomState climate(GameTestHelper h, long seed) {
        var key = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(Interstice.ID, "living_realm_v3"));
        var settings = h.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(key);
        return RandomState.create(settings.value(), h.getLevel().registryAccess().registryOrThrow(Registries.NOISE).asLookup(), seed);
    }
    private static TerrainV3.Column column(long seed, RandomState fields, int x, int z) {
        var p = new DensityFunction.SinglePointContext(x, 0, z);
        return TerrainV3.column(seed, PROFILE, x, z, fields.router().continents().compute(p), fields.router().vegetation().compute(p));
    }
    private static int top(TerrainV3.Column column) {
        for (int y = 193; y >= 1; y--) if (column.density(y) > 0) return y;
        return -1;
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void gardenCoreIsAtTheActualSeaWithAtMostFiveBlocksOfRelief(GameTestHelper h) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (long seed : SEEDS) for (int x = -3072; x <= 3072; x += 64) for (int z = -3072; z <= 3072; z += 64) {
            var column = TerrainV3.column(seed, PROFILE, x, z, -.5, .5); int surface = top(column);
            h.assertTrue(column.zone() == TerrainV3.Zone.PLAIN && column.weights().gardens() == 1, "Pure garden climate leaked into mountain foothills");
            h.assertTrue(surface >= 34 && surface <= 39, "Garden plain is raised above its actual lower sea: " + surface);
            for (int y = 1; y <= surface; y++) h.assertTrue(column.density(y) > 0, "Low continental plain has a floating foundation");
            h.assertTrue(column.density(surface + 1) <= 0, "A suspended body contaminates the low garden core");
            min = Math.min(min, surface); max = Math.max(max, surface);
            var atEdge = TerrainV3.column(seed, PROFILE, x, z, TerrainV3.MOUNTAIN_START, TerrainV3.GARDEN_CORE);
            h.assertTrue(atEdge.groundHeight() == column.groundHeight(), "Mountain influence starts inside the plain biome");
        }
        h.assertTrue(max - min <= 5, "Total plain-core relief exceeds five blocks");
        System.out.println("TERRAIN_V3_PLAINS solidSurface=" + min + ".." + max + " lowerSeaVoxelTop=35"); h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=300)
    public static void actualClimateProfilesJoinBroadFoothillsAndTraversableValleys(GameTestHelper h) {
        for (long seed : SEEDS) {
            var fields = climate(h, seed); var slopes = new ArrayList<Double>();
            double minPlain = 999, maxPlain = -999, peak = -999, maxStep = 0;
            int plains = 0, foothills = 0, mountains = 0, lowValleys = 0;
            for (int x = -3072; x <= 3072; x += 48) for (int z = -3072; z <= 3072; z += 48) {
                var a = column(seed, fields, x, z);
                if (a.zone() == TerrainV3.Zone.PLAIN) { plains++; minPlain = Math.min(minPlain, a.groundHeight()); maxPlain = Math.max(maxPlain, a.groundHeight()); }
                if (a.zone() == TerrainV3.Zone.FOOTHILLS) foothills++;
                if (a.zone() == TerrainV3.Zone.MOUNTAINS) { mountains++; if (a.groundHeight() < 55) lowValleys++; }
                if (a.weights().grounded() > .5) {
                    double dx = Math.abs(a.groundHeight() - column(seed, fields, x + 1, z).groundHeight());
                    double dz = Math.abs(a.groundHeight() - column(seed, fields, x, z + 1).groundHeight());
                    maxStep = Math.max(maxStep, Math.max(dx, dz)); slopes.add(dx); slopes.add(dz);
                }
                peak = Math.max(peak, a.groundHeight());
            }
            Collections.sort(slopes); double p95 = slopes.get((int)(slopes.size() * .95));
            h.assertTrue(plains > 100 && foothills > 100 && mountains > 50 && lowValleys > 5, "A seed lacks real plains, foothills, mountain interiors or low passes");
            h.assertTrue(maxPlain - minPlain <= 5 && minPlain >= 35 && maxPlain < 40, "Actual registered climate creates cliffs inside garden cores");
            h.assertTrue(peak >= 185 && peak < 194, "Smooth mountain field lost its large peaks or reached a hard ceiling");
            h.assertTrue(maxStep < 2.3 && p95 < .65, "Actual climate still creates abrupt adjacent-column walls: max=" + maxStep + " p95=" + p95);
            System.out.println("TERRAIN_V3_PROFILE seed=" + seed + " plains=" + plains + " foothills=" + foothills + " mountainCore=" + mountains
                    + " lowValleys=" + lowValleys + " maxAdjacentRise=" + maxStep + " p95Rise=" + p95 + " peak=" + peak);
        }
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=300)
    public static void floatingMassesHaveIrregularSectionsLargeVoidsAndStableSeeds(GameTestHelper h) {
        final long seed = 20261006L; int occupied = 0, empty = 0; int[] anchor = null;
        for (int x = -3072; x <= 3072; x += 48) for (int z = -3072; z <= 3072; z += 48) {
            var column = TerrainV3.column(seed, PROFILE, x, z, -.5, -.5); int surface = top(column), depth = 0;
            for (int y = 1; y < 198; y++) if (column.density(y) > 0) {
                h.assertTrue(y >= 41 && y < 194, "Suspended mass breaches sea gaps"); depth++;
            }
            if (surface >= 41) { occupied++; if (anchor == null && depth >= 25) anchor = new int[]{x, z}; } else empty++;
        }
        double coverage = occupied / (double)(occupied + empty);
        h.assertTrue(coverage > .12 && coverage < .30 && anchor != null, "New floating world lost its large rare masses and open space: " + coverage);
        // Observe one actual connected footprint, rather than asserting a particular ellipse function.
        final int size = 121, step = 8, half = size / 2;
        int[][] tops = new int[size][size], depths = new int[size][size];
        for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) {
            var c = TerrainV3.column(seed, PROFILE, anchor[0] + (i - half) * step, anchor[1] + (j - half) * step, -.5, -.5);
            tops[i][j] = top(c); for (int y = 41; y < 194; y++) if (c.density(y) > 0) depths[i][j]++;
        }
        var queue = new ArrayDeque<Integer>(); var component = new HashSet<Integer>(); queue.add(half * size + half);
        while (!queue.isEmpty()) {
            int index = queue.removeFirst(), i = index / size, j = index % size;
            if (i < 0 || i >= size || j < 0 || j >= size || tops[i][j] < 41 || !component.add(index)) continue;
            if (i > 0) queue.add((i - 1) * size + j); if (i + 1 < size) queue.add((i + 1) * size + j);
            if (j > 0) queue.add(i * size + j - 1); if (j + 1 < size) queue.add(i * size + j + 1);
        }
        int rowsWithGaps = 0, low = 999, high = -999, shallow = 999, deep = -999, perimeter = 0;
        for (int j = 0; j < size; j++) {
            int runs = 0; boolean previous = false;
            for (int i = 0; i < size; i++) {
                boolean here = component.contains(i * size + j); if (here && !previous) runs++; previous = here;
                if (!here) continue;
                low = Math.min(low, tops[i][j]); high = Math.max(high, tops[i][j]); shallow = Math.min(shallow, depths[i][j]); deep = Math.max(deep, depths[i][j]);
                if (i == 0 || !component.contains((i - 1) * size + j)) perimeter++;
                if (i + 1 == size || !component.contains((i + 1) * size + j)) perimeter++;
                if (j == 0 || !component.contains(i * size + j - 1)) perimeter++;
                if (j + 1 == size || !component.contains(i * size + j + 1)) perimeter++;
            }
            if (runs > 1) rowsWithGaps++;
        }
        h.assertTrue(component.size() > 200 && high - low >= 5 && deep - shallow >= 12 && rowsWithGaps >= 2,
                "Floating footprint remains a smooth oval without compound edges / broken plate sections / heavy irregular underside; cells=" + component.size() + " gaps=" + rowsWithGaps);
        for (int x : new int[]{-29999900, -91, 0, 83, 29999900}) {
            var a = TerrainV3.column(seed, PROFILE, x, 71, -.5, .5);
            TerrainV3.column(seed + (1L << 32), PROFILE, -x, -71, .7, -.5);
            var b = TerrainV3.column(seed, PROFILE, x, 71, -.5, .5);
            for (int y = 1; y < 194; y++) h.assertTrue(Double.doubleToLongBits(a.density(y)) == Double.doubleToLongBits(b.density(y)), "V3 density depends on another world or mutable cache");
        }
        h.assertTrue(TerrainV2.column(seed, PROFILE, 0, 0, -.5, .5).gardenHeight() > 40, "V2 numeric profile was silently replaced with the new sea-level plain");
        System.out.println("TERRAIN_V3_FLOATING coverage=" + coverage + " componentCells=" + component.size() + " perimeter=" + perimeter
                + " splitSections=" + rowsWithGaps + " surfaceVariation=" + (high - low) + " depthVariation=" + (deep - shallow)); h.succeed();
    }
}
