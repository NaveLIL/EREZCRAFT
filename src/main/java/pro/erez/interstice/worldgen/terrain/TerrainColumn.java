package pro.erez.interstice.worldgen.terrain;

/** Read-only density contract shared by separately versioned terrain algorithms. */
public interface TerrainColumn {
    TerrainV2.Weights weights();
    TerrainV2.Kind dominant();
    double gardenHeight();
    double vaultHeight();
    double maximumSurfaceY();
    double density(double y);
    default double density(int y) { return density((double)y); }
}
