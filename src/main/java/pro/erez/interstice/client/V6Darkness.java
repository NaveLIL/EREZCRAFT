package pro.erez.interstice.client;

/** Remove the current frame's zero-light floor from the final ABGR lightmap. */
public final class V6Darkness {
    private V6Darkness() {}

    public static int removeAmbient(int color, int zeroLight, int fullLight) {
        int result = color & 0xff000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int value = color >>> shift & 255;
            int floor = zeroLight >>> shift & 255;
            int ceiling = fullLight >>> shift & 255;
            int corrected = Math.max(0, value - floor) * ceiling / Math.max(1, ceiling - floor);
            result |= Math.min(255, corrected) << shift;
        }
        return result;
    }
}
