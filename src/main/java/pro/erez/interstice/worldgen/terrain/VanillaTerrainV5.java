package pro.erez.interstice.worldgen.terrain;

import net.minecraft.world.level.levelgen.*;
import pro.erez.interstice.geometry.GeometryProfile;

public final class VanillaTerrainV5 {
    private VanillaTerrainV5(){}
    public static TerrainColumn column(RandomState random,GeometryProfile profile,int x,int z){
        var root=random.router().finalDensity();while(root instanceof DensityFunctions.MarkerOrMarked||root instanceof DensityFunctions.HolderHolder){if(root instanceof DensityFunctions.MarkerOrMarked marker)root=marker.wrapped();else root=((DensityFunctions.HolderHolder)root).function().value();}
        if(!(root instanceof VanillaRealmDensity density))throw new IllegalStateException("V5 requires its installed native density router");
        var at=new DensityFunction.SinglePointContext(x,0,z);double island=VanillaRealmDensity.islandWeight(random.router().continents().compute(at));double erosion=random.router().erosion().compute(at);
        double vault=(1-island)*Math.max(0,Math.min(1,(-erosion+.15)/.5));var weights=new TerrainV2.Weights(island,1-island-vault,vault);
        var sampler=NativeColumnSampler.of(random);
        return new TerrainColumn(){
            public TerrainV2.Weights weights(){return weights;}public TerrainV2.Kind dominant(){return weights.dominant();}
            public double gardenHeight(){return profile.lowerSeaTop()+5;}public double vaultHeight(){return maximumSurfaceY();}public double maximumSurfaceY(){return profile.maxLand();}
            public double density(double y){return sampler.density(x,(int)Math.floor(y),z,true)*12;}
        };
    }
}
