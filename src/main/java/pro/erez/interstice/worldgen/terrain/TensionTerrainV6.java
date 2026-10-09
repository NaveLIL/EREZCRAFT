package pro.erez.interstice.worldgen.terrain;

import net.minecraft.world.level.levelgen.RandomState;

/** Revision-six uncarved canonical columns for sea mapping, geology, forests and structure probes. */
public final class TensionTerrainV6 {
    private TensionTerrainV6() {}
    public static TensionRealmDensity root(RandomState state){
        var density=NativeColumnSamplerV6.unwrap(state.router().finalDensity());
        if(!(density instanceof TensionRealmDensity tension))throw new IllegalArgumentException("V6 requires its installed tension density router");
        return tension;
    }
    public static TerrainColumn at(int x,int z,RandomState state,long seed){
        var root=root(state);var shape=root.shape(x,z);var sampler=NativeColumnSamplerV6.of(state);
        // Seed belongs to RandomState; the argument preserves the generator's terrain API.
        return new TerrainColumn(){
            @Override public TerrainV2.Weights weights(){return shape.morphology().terrainWeights();}
            @Override public TerrainV2.Kind dominant(){return weights().dominant();}
            @Override public double gardenHeight(){return shape.gardenHeight();}
            @Override public double vaultHeight(){return shape.vaultHeight();}
            @Override public double maximumSurfaceY(){return root.geometry().maxLand();}
            @Override public double density(double y){return sampler.density(x,(int)Math.floor(y),z,true)*12;}
        };
    }
}
