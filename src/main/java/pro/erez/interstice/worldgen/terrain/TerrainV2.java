package pro.erez.interstice.worldgen.terrain;

import java.util.ArrayList;
import java.util.List;
import pro.erez.interstice.geometry.GeometryProfile;

/** Canonical, stateless terrain fields for the new realm; old generators never call this class. */
public final class TerrainV2 {
    public static final int REVISION=2;
    public enum Kind { ASH_ISLANDS, PALE_GARDENS, STONE_VAULTS }
    /** Climate axes and thresholds are shared with RealmBiomes, with continuous density transitions. */
    public record Weights(double ash,double gardens,double vaults) {
        public double grounded() { return gardens+vaults; }
        public Kind dominant() {
            if(vaults>=gardens && vaults>=ash)return Kind.STONE_VAULTS;
            return gardens>=ash?Kind.PALE_GARDENS:Kind.ASH_ISLANDS;
        }
    }

    private record Body(double footprint,double center,double thickness) {
        private double density(double y) {
            double dy=(y-center)/thickness;
            return 1-footprint-dy*dy;
        }
    }

    /** Immutable per-column work: evaluate expensive 2D landforms once, then sample all vertical cells. */
    public static final class Column implements TerrainColumn {
        private final Weights weights;
        private final double gardenHeight,vaultHeight,cap;
        private final int minY,minFloatingY;
        private final List<Body> bodies;
        private Column(Weights weights,double gardenHeight,double vaultHeight,double cap,
                       int minY,int minFloatingY,List<Body> bodies) {
            this.weights=weights; this.gardenHeight=gardenHeight; this.vaultHeight=vaultHeight; this.cap=cap;
            this.minY=minY; this.minFloatingY=minFloatingY; this.bodies=List.copyOf(bodies);
        }
        public Weights weights() {return weights;}
        public Kind dominant() {return weights.dominant();}
        public double gardenHeight() {return gardenHeight;}
        public double vaultHeight() {return vaultHeight;}
        public double maximumSurfaceY() {return cap;}
        /** Positive means solid. Caves can only subtract from this value; material/sea mapping is separate. */
        public double density(int y) {return density((double)y);}
        public double density(double y) {
            if(y<=minY || y>=cap)return -1-Math.max(0,y-cap);
            double islands=-8;
            if(y>=minFloatingY)for(Body body:bodies)islands=Math.max(islands,body.density(y));
            double garden=FreeTerraNoise.clamp((gardenHeight-y)/12,-8,8);
            double vault=FreeTerraNoise.clamp((vaultHeight-y)/12,-8,8);
            return weights.ash*FreeTerraNoise.clamp(islands,-8,1) + weights.gardens*garden + weights.vaults*vault;
        }
    }

    private TerrainV2() {}

    public static Weights weights(double continentalness,double humidity) {
        if(!Double.isFinite(continentalness)||!Double.isFinite(humidity))
            throw new IllegalArgumentException("Terrain climate must be finite");
        double vault=FreeTerraNoise.smoothBetween(.055,.105,continentalness);
        double garden=(1-vault)*FreeTerraNoise.smoothBetween(.11,.17,humidity);
        return new Weights(1-vault-garden,garden,vault);
    }

    public static Column column(long seed,GeometryProfile profile,int x,int z,double continentalness,double humidity) {
        if(profile.height()<256)throw new IllegalArgumentException("Terrain revision 2 requires the tall geometry profile");
        Weights weights=weights(continentalness,humidity);
        double cap=Math.min(profile.minY()+198,profile.maxLand()-7);
        double lower=profile.lowerSeaTop()+1;
        // Low rolling, fertile land: large trees retain ample headroom below the upper sea.
        double gardens=lower+17+FreeTerraNoise.plains(seed,x,z)*300
                +FreeTerraNoise.fractal(seed,0x47415244454E46L,x,z,95,2)*8;
        // FTF mountain ridges provide the terrain, with a broad independent valley modulation.
        double mountains=FreeTerraNoise.mountain(seed,x,z);
        double valley=FreeTerraNoise.fractal(seed,0x5641554C5444414CL,x,z,520,2);
        double rawVault=lower+33+mountains*155*FreeTerraNoise.lerp(.45,1, valley);
        double vaults=softCeiling(rawVault,cap-20,cap);
        return new Column(weights,gardens,vaults,cap,profile.minY(),profile.minLand(),
                weights.ash>0?bodies(seed,profile,x,z,cap):List.of());
    }

    public static double density(long seed,GeometryProfile profile,int x,int y,int z,double c,double h) {
        return column(seed,profile,x,z,c,h).density(y);
    }

    /** Smooth bounded compression; unlike a hard y cap it does not make a table-top mountain. */
    private static double softCeiling(double height,double start,double cap) {
        if(height<=start)return height;
        double range=cap-start,excess=height-start;
        return start+range*excess/(range+excess);
    }

    private static List<Body> bodies(long seed,GeometryProfile profile,int x,int z,double cap) {
        final int spacing=320;
        int cellX=Math.floorDiv(x,spacing),cellZ=Math.floorDiv(z,spacing);
        List<Body> result=new ArrayList<>(3);
        double edge=(FreeTerraNoise.fractal(seed,0x41534845444745L,x,z,55,3)-.5)*.22;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            int cx=cellX+dx,cz=cellZ+dz;
            long key=FreeTerraNoise.mix(seed^((long)cx*0x9E3779B97F4A7C15L)^((long)cz*0xC2B2AE3D27D4EB4FL)^0x415348424F4459L);
            if(unit(key,0)>.66)continue;
            double bx=(double)cx*spacing+spacing*(.25+unit(key,1)*.5);
            double bz=(double)cz*spacing+spacing*(.25+unit(key,2)*.5);
            double rx=75+unit(key,3)*65,rz=65+unit(key,4)*60;
            double qx=(x-bx)/rx,qz=(z-bz)/rz;
            double footprint=qx*qx+qz*qz+edge;
            if(footprint>2)continue;
            double thickness=16+unit(key,5)*15;
            double bottom=profile.minLand()+3;
            double center=bottom+thickness+unit(key,6)*Math.max(0,cap-bottom-thickness*2-12);
            result.add(new Body(footprint,center,thickness));
        }
        return result;
    }

    private static double unit(long key,int salt) {
        return (FreeTerraNoise.mix(key+salt*0x9E3779B97F4A7C15L)>>>11)*0x1.0p-53;
    }
}
