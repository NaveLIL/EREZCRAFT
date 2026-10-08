package pro.erez.interstice.worldgen;

import java.util.ArrayList;
import java.util.List;
import pro.erez.interstice.worldgen.terrain.FreeTerraNoise;

/** Stateless V3 groves: every chunk and species gets avalanche-mixed seeds, with coherent clearings. */
public final class ForestDistribution {
    public enum Kind { PALEHEART, CROWN, GLOOMCROWN }
    public record Candidate(long treeSeed,double anchorX,double anchorZ) {
        public int localX(int minimum,int maximum) {return anchor(minimum,maximum,anchorX);}
        public int localZ(int minimum,int maximum) {return anchor(minimum,maximum,anchorZ);}
        private static int anchor(int minimum,int maximum,double fraction) {
            if(minimum>maximum)throw new IllegalArgumentException("Tree does not fit its own chunk");
            return minimum+(int)Math.floor(fraction*(maximum-minimum+1));
        }
    }
    private ForestDistribution() {}

    public static double grove(long seed,int x,int z) {
        double broad=FreeTerraNoise.fractal(seed,0x47524F564553L,x,z,240,2);
        double clearing=FreeTerraNoise.fractal(seed,0x434C454152494E47L,x,z,95,2);
        return FreeTerraNoise.smoothBetween(.30,.62,broad)
                *(1-FreeTerraNoise.smoothBetween(.63,.78,clearing));
    }

    public static long mixedSeed(long seed,int chunkX,int chunkZ,Kind kind,int attempt) {
        return FreeTerraNoise.mix(seed^chunkX*0x9E3779B97F4A7C15L^chunkZ*0xC2B2AE3D27D4EB4FL
                ^(kind.ordinal()+1L)*0xD1B54A32D192ED03L^attempt*0x94D049BB133111EBL);
    }

    public static List<Candidate> candidates(long seed,int chunkX,int chunkZ,Kind kind,int chance,int attempts) {
        if(chance<1||attempts<0||attempts>4)throw new IllegalArgumentException("Invalid forest placement recipe");
        double grove=grove(seed,chunkX*16+8,chunkZ*16+8);
        // Medium canopy coverage supplies density; this is at most one ordinary tree with the bundled recipe.
        double acceptance=switch(kind) {
            case PALEHEART -> .16+.80*grove;
            case CROWN -> FreeTerraNoise.smoothBetween(.12,.40,grove);
            case GLOOMCROWN -> .15+.35*grove;
        };
        List<Candidate> out=new ArrayList<>(attempts);
        for(int attempt=0;attempt<attempts;attempt++) {
            long mixed=mixedSeed(seed,chunkX,chunkZ,kind,attempt);
            if(unit(mixed,0)>=acceptance/chance)continue;
            out.add(new Candidate(FreeTerraNoise.mix(mixed+0x723BA51L),unit(mixed,1),unit(mixed,2)));
        }
        return List.copyOf(out);
    }

    private static double unit(long value,int salt) {
        return (FreeTerraNoise.mix(value+salt*0x9E3779B97F4A7C15L)>>>11)*0x1.0p-53;
    }
}
