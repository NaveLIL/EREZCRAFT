package pro.erez.interstice.worldgen.terrain;

/**
 * Numeric subset adapted from FreeTerraForged c5d322f5e44539ca6d3a6059c5de440af9abcfe6.
 * Copyright (c) 2023 ReTerraForged. MIT; see META-INF/licenses/freeterraforged-terrain.txt.
 * Registry codecs and mutable caches are deliberately absent. Coordinates retain double precision.
 */
public final class FreeTerraNoise {
    private static final double[][] GRADIENTS = {
            {-1,-1}, {1,-1}, {-1,1}, {1,1}, {0,-1}, {-1,0}, {0,1}, {1,0}
    };
    private static final double[] SIGNALS = {1, .9, .83, .75, .64, .62, .61};
    private FreeTerraNoise() {}

    /** All 64 world-seed bits contribute before FTF's 32-bit lattice hash is applied. */
    public static int fieldSeed(long worldSeed, long salt) {
        long mixed = mix(worldSeed ^ salt);
        return (int)(mixed ^ (mixed >>> 32));
    }

    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static int hash(int seed, int x, int z) {
        int value = seed ^ (1619 * x) ^ (31337 * z);
        value = value * value * value * 60493;
        return value ^ (value >> 13);
    }

    private static double gradient(int seed, int x, int z, double dx, double dz) {
        double[] g = GRADIENTS[hash(seed,x,z) & 7];
        return dx*g[0] + dz*g[1];
    }

    /** FTF Perlin.sample with CURVE3 interpolation and corrected negative-integer floor. */
    public static double perlin(double x, double z, int seed) {
        int ix = (int)Math.floor(x), iz = (int)Math.floor(z);
        double dx=x-ix, dz=z-iz;
        double xs=smooth(dx), zs=smooth(dz);
        double lower=lerp(gradient(seed,ix,iz,dx,dz), gradient(seed,ix+1,iz,dx-1,dz),xs);
        double upper=lerp(gradient(seed,ix,iz+1,dx,dz-1), gradient(seed,ix+1,iz+1,dx-1,dz-1),xs);
        return lerp(lower,upper,zs);
    }

    /** FTF Perlin.compute normalization, bounded to its advertised [0,1] range. */
    public static double fractal(int seed, double x, double z, double scale, int octaves) {
        double px=x/scale, pz=z/scale, sum=0, amplitude=.5, bound=0;
        double signal=SIGNALS[Math.min(octaves,SIGNALS.length-1)];
        for(int i=0;i<octaves;i++) {
            sum += perlin(px,pz,seed+i)*amplitude;
            bound += signal*amplitude;
            px*=2; pz*=2; amplitude*=.5;
        }
        return clamp(.5 + sum/(2*bound),0,1);
    }

    public static double fractal(long seed, long salt, double x, double z, double scale, int octaves) {
        return fractal(fieldSeed(seed,salt),x,z,scale,octaves);
    }

    /** FTF PerlinRidge.compute: octave feedback and inverse-frequency spectral weighting. */
    public static double ridge(int seed, double x, double z, double scale, int octaves,
                               double lacunarity, double gain) {
        double px=x/scale, pz=z/scale, amplitude=2, value=0, weight=1, spectral=1, bound=0;
        for(int i=0;i<octaves;i++) {
            double signal=1-Math.abs(perlin(px,pz,seed+i));
            signal=signal*signal*weight;
            weight=clamp(signal*amplitude,0,1);
            value+=signal*spectral; bound+=spectral;
            px*=lacunarity; pz*=lacunarity; spectral/=lacunarity; amplitude*=gain;
        }
        return clamp(value/bound,0,1);
    }

    /** DomainWarp maps FTF's unit noise to [-.5,.5] before multiplying the warp distance. */
    public static double warpOffset(int seed, double x, double z, double scale, int octaves, double distance) {
        return (fractal(seed,x,z,scale,octaves)-.5)*distance;
    }

    /** Numeric composition from Populators.makeMountains / makeMountainChain (fancy erosion disabled). */
    public static double mountain(long seed, double x, double z) {
        int s=fieldSeed(seed,0x4D4F554E5441494EL);
        double wx=x+warpOffset(s+2,x,z,350,1,150);
        double wz=z+warpOffset(s+3,x,z,350,1,150);
        double height=ridge(s,wx,wz,335,4,2.35,1.15);
        double scaler=fractal(s+1,wx,wz,24,4)*.075+(1-.075);
        return height*scaler*1.3;
    }

    /** The warped Perlin/erosion composition from Populators.makePlains. */
    public static double plains(long seed, double x, double z) {
        int s=fieldSeed(seed,0x504C41494E53L);
        double wx=x+warpOffset(s+4,x,z,256,1,256);
        double wz=z+warpOffset(s+5,x,z,256,1,256);
        double dx=(fractal(s+1,wx,wz,62.5,3)-.5)*62.5;
        double dz=(fractal(s+2,wx,wz,62.5,3)-.5)*62.5;
        wx+=dx; wz+=dz;
        double erosion=fractal(s,wx,wz,500,3)*.45+(1-.45);
        return fractal(s+3,wx,wz,250,1)*erosion*.15-.02;
    }

    public static double smooth(double value) { return value*value*(3-2*value); }
    public static double smoothBetween(double low,double high,double value) {
        return smooth(clamp((value-low)/(high-low),0,1));
    }
    public static double clamp(double value,double low,double high) { return Math.max(low,Math.min(high,value)); }
    public static double lerp(double low,double high,double alpha) { return low+(high-low)*alpha; }
}
