package pro.erez.interstice.worldgen.terrain;

import java.util.ArrayList;
import java.util.List;
import pro.erez.interstice.geometry.GeometryProfile;

/** Bounded, irregular compound stone masses. The lattice scatters centers; it is not the silhouette. */
public final class FloatingLandforms {
    private static final int SPACING = 420;
    private record Slice(double top, double bottom, double side) {
        double density(double y) { return Math.min(side, Math.min(top - y, y - bottom)) / 12; }
    }
    public static final class Column {
        private final List<Slice> slices;
        private final int minY;
        private final double cap;
        private Column(List<Slice> slices, int minY, double cap) { this.slices = List.copyOf(slices); this.minY = minY; this.cap = cap; }
        public double density(double y) {
            if (y < minY || y >= cap) return -8;
            double result = -8;
            for (var slice : slices) result = Math.max(result, slice.density(y));
            return result;
        }
        public int highestSolidY() {
            return slices.stream().filter(s -> s.side > 0).mapToInt(s -> (int)Math.ceil(Math.min(cap, s.top)) - 1).max().orElse(-1);
        }
        public int lowestSolidY() {
            return slices.stream().filter(s -> s.side > 0).mapToInt(s -> Math.max(minY, (int)Math.floor(s.bottom) + 1)).min().orElse(-1);
        }
    }
    private FloatingLandforms() {}
    public static Column column(long seed, GeometryProfile profile, int x, int z) {
        int field = FreeTerraNoise.fieldSeed(seed, 0x5633464C4F41544CL);
        double wx = x + FreeTerraNoise.warpOffset(field, x, z, 560, 2, 90);
        double wz = z + FreeTerraNoise.warpOffset(field + 1, x, z, 560, 2, 90);
        int cellX = (int)Math.floor(wx / SPACING), cellZ = (int)Math.floor(wz / SPACING);
        double cap = Math.min(profile.minY() + 194, profile.maxLand() - 11);
        var slices = new ArrayList<Slice>(3);
        // Every body, including lobes and warping, stays within300 blocks on each X/Z axis. Therefore
        // only the surrounding3x3 center cells can contribute, including negative coordinates.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int cx = cellX + dx, cz = cellZ + dz;
            long key = FreeTerraNoise.mix(seed ^ ((long)cx * 0x9E3779B97F4A7C15L)
                    ^ ((long)cz * 0xC2B2AE3D27D4EB4FL) ^ 0x56334D4153534553L);
            if (unit(key, 0) > .74) continue;
            double centerX = cx * (double)SPACING + SPACING * (.2 + unit(key, 1) * .6);
            double centerZ = cz * (double)SPACING + SPACING * (.2 + unit(key, 2) * .6);
            if (Math.abs(wx - centerX) > 300 || Math.abs(wz - centerZ) > 300) continue;
            double angle = unit(key, 3) * Math.PI * 2, cosine = Math.cos(angle), sine = Math.sin(angle);
            double u = (wx - centerX) * cosine + (wz - centerZ) * sine;
            double v = -(wx - centerX) * sine + (wz - centerZ) * cosine;
            double rx = 120 + unit(key, 4) * 40, rz = 95 + unit(key, 5) * 40;
            double footprint = 1 - Math.pow(Math.abs(u / rx), 2.6) - Math.pow(Math.abs(v / rz), 2.6);
            int lobes = 2 + (int)(unit(key, 6) * 3);
            for (int lobe = 0; lobe < lobes; lobe++) {
                int salt = 20 + lobe * 5;
                double direction = unit(key, salt) * Math.PI * 2;
                double pu = Math.cos(direction) * rx * (.5 + unit(key, salt + 1) * .45);
                double pv = Math.sin(direction) * rz * (.5 + unit(key, salt + 2) * .45);
                double a = (u - pu) / (rx * (.35 + unit(key, salt + 3) * .25));
                double b = (v - pv) / (rz * (.35 + unit(key, salt + 4) * .25));
                footprint = Math.max(footprint, 1 - a * a - b * b);
            }
            // Correlated edge breakup acts on tens of blocks, not independent voxel speckles.
            double rough = FreeTerraNoise.fractal(FreeTerraNoise.fieldSeed(seed ^ key, 0x45444745L), wx, wz, 72, 2) - .5;
            footprint += rough * .34;
            if (footprint <= 0) continue;
            double half = 13 + unit(key, 7) * 10;
            double low = profile.minLand() + half + 30 + 16;
            double centerY = low + unit(key, 8) * Math.max(0, cap - half * .35 - 25 - low);
            double tilt = (unit(key, 9) - .5) * 14 * u / rx + (unit(key, 10) - .5) * 10 * v / rz;
            double relief = (FreeTerraNoise.fractal(seed ^ key, 0x504C415445544F50L, wx, wz, 110, 2) - .5) * 7
                    + (FreeTerraNoise.fractal(seed ^ key, 0x504C41544546494EL, wx, wz, 35, 1) - .5) * 2;
            double shape = FreeTerraNoise.clamp(footprint, 0, 1);
            double top = centerY + half * .35 + tilt + relief - (1 - shape) * 3;
            double bottom = centerY - half * (.45 + .55 * Math.sqrt(shape)) + tilt;
            double fangs = 0;
            for (int fang = 0; fang < 3; fang++) {
                int salt = 60 + fang * 5;
                double fu = (u - (unit(key, salt) - .5) * rx * 1.3) / (15 + unit(key, salt + 1) * 16);
                double fv = (v - (unit(key, salt + 2) - .5) * rz * 1.3) / (15 + unit(key, salt + 3) * 16);
                fangs = Math.max(fangs, Math.max(0, 1 - Math.hypot(fu, fv)) * (16 + unit(key, salt + 4) * 14));
            }
            bottom -= fangs;
            // Some plates are divided by a warped cleft; an independently placed, thinner
            // stone neck remains as a bridge, rather than a perfectly round solid oval.
            if (unit(key, 11) < .55) {
                double cleft = (unit(key, 12) - .5) * rz * .5
                        + (FreeTerraNoise.fractal(seed ^ key, 0x434C454654L, u, 0, 150, 2) - .5) * 24;
                double width = 5 + unit(key, 13) * 7;
                if (Math.abs(v - cleft) < width) {
                    double bridge = (unit(key, 14) - .5) * rx * 1.1;
                    if (Math.abs(u - bridge) > 10 + unit(key, 15) * 8) continue;
                    bottom = Math.max(bottom, top - (6 + unit(key, 16) * 5));
                }
            }
            if (top > bottom) slices.add(new Slice(Math.min(cap - .01, top), Math.max(profile.minLand() - .01, bottom), footprint * 42));
        }
        return new Column(slices, profile.minLand(), cap);
    }
    private static double unit(long key, int salt) {
        return (FreeTerraNoise.mix(key + salt * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
