package pro.erez.interstice.worldgen.terrain;

import pro.erez.interstice.geometry.GeometryProfile;

/** Shorter-scale valleys, cusp ridges and angular crags; separate saved revision, never a V3 rewrite. */
public final class TerrainV4 {
    public static final int REVISION=4;
    public static final double MOUNTAIN_START=.08,MOUNTAIN_CORE=.36,GARDEN_CORE=.14,ASH_CORE=-.18;
    public enum Zone { ASH,COAST,PLAIN,FOOTHILLS,MOUNTAINS }
    private TerrainV4() {}
    public static final class Column implements TerrainColumn {
        private final TerrainV2.Weights weights;
        private final TerrainV2.Kind kind;
        private final Zone zone;
        private final double plain,mountain,ground,cap,grounded;
        private final int minimum,sea;
        private final FloatingLandformsV4.Column floating;
        private Column(TerrainV2.Weights weights,TerrainV2.Kind kind,Zone zone,double plain,double mountain,
                       double ground,double cap,double grounded,int minimum,int sea,FloatingLandformsV4.Column floating) {
            this.weights=weights;this.kind=kind;this.zone=zone;this.plain=plain;this.mountain=mountain;this.ground=ground;
            this.cap=cap;this.grounded=grounded;this.minimum=minimum;this.sea=sea;this.floating=floating;
        }
        @Override public TerrainV2.Weights weights(){return weights;}
        @Override public TerrainV2.Kind dominant(){return kind;}
        public Zone zone(){return zone;}
        @Override public double gardenHeight(){return plain;}
        @Override public double vaultHeight(){return mountain;}
        public double groundHeight(){return ground;}
        @Override public double maximumSurfaceY(){return cap;}
        public double floatingDensity(double y) {
            if(floating==null||y<=minimum||y>=cap)return -8;
            double stretch=Math.max(.01,(1-grounded)*(1-grounded));
            return floating.density(sea+(y-sea)/stretch)-grounded*2/Math.max(.01,1-grounded);
        }
        public int highestFloatingSolidY(){for(int y=(int)Math.ceil(cap)-1;y>minimum;y--)if(floatingDensity(y)>0)return y;return -1;}
        public int lowestFloatingSolidY(){for(int y=minimum+1;y<cap;y++)if(floatingDensity(y)>0)return y;return -1;}
        /** Shared hydrology adjusts this surface once; neither caves nor chunk consumers recalculate it. */
        public Column withGroundHeight(double height) {
            if(!Double.isFinite(height)||height>=cap)throw new IllegalArgumentException("Hydrology surface must be finite and below the upper terrain limit");
            return new Column(weights,kind,zone,plain,mountain,height,cap,grounded,minimum,sea,floating);
        }
        @Override public double density(double y) {
            if(y<=minimum||y>=cap)return -1-Math.max(0,y-cap);
            double continental=(ground-y)/12;
            if(floating==null)return continental;
            return Math.max(continental,floatingDensity(y));
        }
    }
    public static TerrainV2.Weights weights(double c,double h) {
        if(!Double.isFinite(c)||!Double.isFinite(h))throw new IllegalArgumentException("Terrain climate must be finite");
        double vault=FreeTerraNoise.smoothBetween(MOUNTAIN_START,MOUNTAIN_CORE,c);
        double garden=(1-vault)*FreeTerraNoise.smoothBetween(ASH_CORE,GARDEN_CORE,h);
        return new TerrainV2.Weights(1-vault-garden,garden,vault);
    }
    public static Column column(long seed,GeometryProfile profile,int x,int z,double c,double h) {
        if(!profile.equals(GeometryProfile.TALL))throw new IllegalArgumentException("Terrain revision4 requires unchanged tall geometry");
        var weights=weights(c,h);int sea=profile.lowerSeaTop()+1;
        double broad=FreeTerraNoise.fractal(seed,0x5634504C41494E53L,x,z,290,2);
        double fine=FreeTerraNoise.fractal(seed,0x5634504C41494E46L,x,z,24,2);
        double plain=sea+(broad*.77+fine*.23)*4.95;
        double cap=Math.min(profile.minY()+190,profile.maxLand()-15);
        double mountain=mountainHeight(seed,x,z,plain,cap);
        double grounded=weights.grounded(),shore=FreeTerraNoise.lerp(profile.minY()-12,plain,grounded);
        double ground=FreeTerraNoise.lerp(shore,mountain,weights.vaults());
        var kind=c>MOUNTAIN_START?TerrainV2.Kind.STONE_VAULTS:h>=GARDEN_CORE?TerrainV2.Kind.PALE_GARDENS:TerrainV2.Kind.ASH_ISLANDS;
        var zone=c>=MOUNTAIN_CORE?Zone.MOUNTAINS:c>MOUNTAIN_START?Zone.FOOTHILLS:h>=GARDEN_CORE?Zone.PLAIN:h<=ASH_CORE?Zone.ASH:Zone.COAST;
        var floating=weights.ash()>.001?FloatingLandformsV4.column(seed,profile,x,z):null;
        return new Column(weights,kind,zone,plain,mountain,ground,cap,grounded,profile.minY(),sea,floating);
    }
    private static double mountainHeight(long seed,double x,double z,double plain,double cap) {
        int s=FreeTerraNoise.fieldSeed(seed,0x56344352414753L);
        double wx=x+FreeTerraNoise.warpOffset(s+2,x,z,330,2,72),wz=z+FreeTerraNoise.warpOffset(s+3,x,z,330,2,72);
        double valley=valleyDistance(seed,wx,wz);
        double shoulder=FreeTerraNoise.smoothBetween(8,88,valley);
        double ridge=FreeTerraNoise.ridge(s,wx,wz,150,4,2.25,1.05);
        // Absolute-value cusps give real knife edges; local detail articulates rocky faces rather
        // than making every summit the same broad, rounded hump.
        double knife=1-Math.abs(FreeTerraNoise.perlin(wx/210,wz/210,s+4));
        double crags=Math.pow(ridge,3.1)*.62+Math.pow(knife,7)*.38;
        double region=FreeTerraNoise.fractal(s+5,wx,wz,370,1);
        double detail=(FreeTerraNoise.fractal(s+6,wx,wz,28,2)-.5)*10;
        double floor=plain+FreeTerraNoise.fractal(s+7,wx,wz,180,1)*5;
        double rise=shoulder*(27+crags*185*FreeTerraNoise.lerp(.64,1,region)+detail);
        return softCeiling(floor+rise,cap-10,cap);
    }
    public static double valleyDistance(long seed,double x,double z) {
        final int spacing=320;int cellX=(int)Math.floor(x/spacing),cellZ=(int)Math.floor(z/spacing);
        double first=Double.POSITIVE_INFINITY,second=Double.POSITIVE_INFINITY;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            int cx=cellX+dx,cz=cellZ+dz;long key=FreeTerraNoise.mix(seed^cx*0x9E3779B97F4A7C15L^cz*0xC2B2AE3D27D4EB4FL^0x563456414C4C4559L);
            double px=(cx+.5)*spacing+(unit(key)-.5)*100,pz=(cz+.5)*spacing+(unit(key+1)-.5)*100;
            double distance=Math.hypot(x-px,z-pz);if(distance<first){second=first;first=distance;}else if(distance<second)second=distance;
        }
        return Math.max(0,(second-first)*.5);
    }
    private static double unit(long value){return (FreeTerraNoise.mix(value)>>>11)*0x1.0p-53;}
    private static double softCeiling(double height,double start,double cap){if(height<=start)return height;double span=cap-start,excess=height-start;return start+span*excess/(span+excess);}
}
