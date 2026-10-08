package pro.erez.interstice.worldgen.terrain;

import pro.erez.interstice.geometry.GeometryProfile;

/**
 * Sea-level garden plains and smoothly joined mountain heights. This revision never changes V2.
 * FTF Perlin/Ridge and warped cellular-region compositions supply the landforms; the vertical
 * conversion, toxic-coast shelf and fading suspended bodies are specific to Interstice.
 */
public final class TerrainV3 {
    public static final int REVISION = 3;
    public static final double MOUNTAIN_START = .08;
    public static final double MOUNTAIN_CORE = .52;
    public static final double GARDEN_CORE = .14;
    public static final double ASH_CORE = -.18;
    public enum Zone { ASH, COAST, PLAIN, FOOTHILLS, MOUNTAINS }
    private TerrainV3() {}

    /** Immutable work for one X/Z column; no random source or mutable world cache. */
    public static final class Column implements TerrainColumn {
        private final TerrainV2.Weights weights;
        private final TerrainV2.Kind kind;
        private final Zone zone;
        private final double plain, mountain, ground, cap, grounded;
        private final int minY, sea;
        private final FloatingLandforms.Column suspended;
        private Column(TerrainV2.Weights weights, TerrainV2.Kind kind, Zone zone, double plain, double mountain,
                       double ground, double cap, double grounded, GeometryProfile profile, FloatingLandforms.Column suspended) {
            this.weights = weights; this.kind = kind; this.zone = zone; this.plain = plain; this.mountain = mountain;
            this.ground = ground; this.cap = cap; this.grounded = grounded; this.minY = profile.minY();
            this.sea = profile.lowerSeaTop() + 1; this.suspended = suspended;
        }
        @Override public TerrainV2.Weights weights() { return weights; }
        @Override public TerrainV2.Kind dominant() { return kind; }
        public Zone zone() { return zone; }
        @Override public double gardenHeight() { return plain; }
        @Override public double vaultHeight() { return mountain; }
        /** The bedrock-connected surface before caves, without any upper suspended body. */
        public double groundHeight() { return ground; }
        @Override public double maximumSurfaceY() { return cap; }
        @Override public double density(double y) {
            if (y <= minY || y >= cap) return -1 - Math.max(0, y - cap);
            // Heights were blended in world units. Never clip and then mix separate signed densities:
            // that used to move the zero crossing abruptly between a plain and a high mountain.
            double continental = (ground - y) / 12;
            if (suspended == null) return continental;
            // Towards mainland the compound stone mass lowers and thins into coastal fragments.
            // Pure ash retains real suspension, independently of the grounded height field.
            double stretch = Math.max(.01, (1 - grounded) * (1 - grounded));
            double sampleY = sea + (y - sea) / stretch;
            double floating = suspended.density(sampleY) - grounded * 2 / Math.max(.01, 1 - grounded);
            return Math.max(continental, floating);
        }
    }

    public static TerrainV2.Weights weights(double c, double h) {
        if (!Double.isFinite(c) || !Double.isFinite(h)) throw new IllegalArgumentException("Terrain climate must be finite");
        double vault = FreeTerraNoise.smoothBetween(MOUNTAIN_START, MOUNTAIN_CORE, c);
        double garden = (1 - vault) * FreeTerraNoise.smoothBetween(ASH_CORE, GARDEN_CORE, h);
        return new TerrainV2.Weights(1 - vault - garden, garden, vault);
    }

    public static Column column(long seed, GeometryProfile profile, int x, int z, double c, double h) {
        if (!profile.equals(GeometryProfile.TALL)) throw new IllegalArgumentException("Terrain revision3 requires the unchanged tall geometry");
        var weights = weights(c, h);
        double sea = profile.lowerSeaTop() + 1;
        double plain = sea + FreeTerraNoise.fractal(seed, 0x5633504C41494E53L, x, z, 620, 2) * 4.95;
        double cap = Math.min(profile.minY() + 194, profile.maxLand() - 11);
        double mountain = mountainHeight(seed, x, z, plain, cap);
        double grounded = weights.grounded();
        // A submerged, gradual shore reaches the garden at the lower sea's real surface.
        // Pure ash's continental field is below the world floor, so it cannot create a hidden pillar.
        double shore = FreeTerraNoise.lerp(profile.minY() - 12, plain, grounded);
        double ground = FreeTerraNoise.lerp(shore, mountain, weights.vaults());
        TerrainV2.Kind kind = c > MOUNTAIN_START ? TerrainV2.Kind.STONE_VAULTS
                : h >= GARDEN_CORE ? TerrainV2.Kind.PALE_GARDENS : TerrainV2.Kind.ASH_ISLANDS;
        Zone zone = c >= MOUNTAIN_CORE ? Zone.MOUNTAINS : c > MOUNTAIN_START ? Zone.FOOTHILLS
                : h >= GARDEN_CORE ? Zone.PLAIN : h <= ASH_CORE ? Zone.ASH : Zone.COAST;
        var floating = weights.ash() > .001 ? FloatingLandforms.column(seed, profile, x, z) : null;
        return new Column(weights, kind, zone, plain, mountain, ground, cap, grounded, profile, floating);
    }

    private static double mountainHeight(long seed, double x, double z, double plain, double cap) {
        int s = FreeTerraNoise.fieldSeed(seed, 0x56334D4F554E544EL);
        double wx = x + FreeTerraNoise.warpOffset(s + 2, x, z, 720, 2, 160);
        double wz = z + FreeTerraNoise.warpOffset(s + 3, x, z, 720, 2, 160);
        double valley = valleyDistance(seed, wx, wz);
        // Shared Voronoi borders make a connected network of wide, low corridors. The broad
        // shoulder rises over hundreds of blocks, rather than a narrow cliff at a biome boundary.
        double shoulder = FreeTerraNoise.smoothBetween(18, 298, valley);
        double ridge = FreeTerraNoise.ridge(s, wx, wz, 740, 2, 2.0, 1.05);
        double broad = FreeTerraNoise.fractal(s + 5, wx, wz, 900, 1);
        double floor = plain + FreeTerraNoise.fractal(s + 6, wx, wz, 560, 1) * 7;
        double rise = shoulder * Math.pow(ridge, 1.7) * FreeTerraNoise.lerp(.8, 1, broad) * 220;
        return softCeiling(floor + rise, cap - 16, cap);
    }

    /** Warped cellular edge field, following the nearest/second-nearest region composition in FTF. */
    private static double valleyDistance(long seed, double x, double z) {
        final int spacing = 840;
        int cellX = (int)Math.floor(x / spacing), cellZ = (int)Math.floor(z / spacing);
        double first = Double.POSITIVE_INFINITY, second = Double.POSITIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int cx = cellX + dx, cz = cellZ + dz;
            long key = FreeTerraNoise.mix(seed ^ ((long)cx * 0x9E3779B97F4A7C15L)
                    ^ ((long)cz * 0xC2B2AE3D27D4EB4FL) ^ 0x5633524547494F4EL);
            double px = ((double)cx + .5) * spacing + (unit(key) - .5) * 210;
            double pz = ((double)cz + .5) * spacing + (unit(key + 1) - .5) * 210;
            double distance = Math.hypot(x - px, z - pz);
            if (distance < first) { second = first; first = distance; }
            else if (distance < second) second = distance;
        }
        return Math.max(0, (second - first) * .5);
    }
    private static double unit(long key) { return (FreeTerraNoise.mix(key) >>> 11) * 0x1.0p-53; }
    private static double softCeiling(double height, double start, double cap) {
        if (height <= start) return height;
        double range = cap - start, excess = height - start;
        return start + range * excess / (range + excess);
    }
}
