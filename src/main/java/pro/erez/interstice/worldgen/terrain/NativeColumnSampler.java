package pro.erez.interstice.worldgen.terrain;

import java.util.*;
import net.minecraft.util.Mth;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import pro.erez.interstice.Interstice;

/** Immutable native corner values for forest/landing probes; actual chunk filling still belongs to NoiseChunk. */
public final class NativeColumnSampler {
    private record Point(int x,int y,int z,boolean terrain){}
    private final DensityFunction corners,uncarved;
    private final LinkedHashMap<Point,Double> points=new LinkedHashMap<>(256,.75F,true);
    private final LinkedHashMap<Long,NoiseColumn> columns=new LinkedHashMap<>(128,.75F,true);
    private static final Map<RandomState,NativeColumnSampler> SAMPLERS=new WeakHashMap<>();
    private NativeColumnSampler(RandomState state){
        var f=state.router().finalDensity();while(f instanceof DensityFunctions.MarkerOrMarked||f instanceof DensityFunctions.HolderHolder){if(f instanceof DensityFunctions.MarkerOrMarked m)f=m.wrapped();else f=((DensityFunctions.HolderHolder)f).function().value();}
        if(!(f instanceof VanillaRealmDensity))throw new IllegalArgumentException("This sampler is only for the V5 interpolated native mixer");corners=f;uncarved=state.router().initialDensityWithoutJaggedness();
    }
    public static synchronized NativeColumnSampler of(RandomState state){return SAMPLERS.computeIfAbsent(state,NativeColumnSampler::new);}
    private synchronized double corner(int x,int y,int z,boolean terrain){var key=new Point(x,y,z,terrain);var value=points.get(key);if(value!=null)return value;double result=(terrain?uncarved:corners).compute(new DensityFunction.SinglePointContext(x,y,z));points.put(key,result);if(points.size()>32768)points.remove(points.keySet().iterator().next());return result;}
    public double density(int x,int y,int z){
        return density(x,y,z,false);
    }
    public double density(int x,int y,int z,boolean terrain){
        int xx=Math.floorDiv(x,4)*4,yy=Math.floorDiv(y,8)*8,zz=Math.floorDiv(z,4)*4;double tx=(x-xx)/4.,ty=(y-yy)/8.,tz=(z-zz)/4.;
        double a=Mth.lerp(ty,corner(xx,yy,zz,terrain),corner(xx,yy+8,zz,terrain)),b=Mth.lerp(ty,corner(xx+4,yy,zz,terrain),corner(xx+4,yy+8,zz,terrain));
        double c=Mth.lerp(ty,corner(xx,yy,zz+4,terrain),corner(xx,yy+8,zz+4,terrain)),d=Mth.lerp(ty,corner(xx+4,yy,zz+4,terrain),corner(xx+4,yy+8,zz+4,terrain));
        return Mth.lerp(tz,Mth.lerp(tx,a,b),Mth.lerp(tx,c,d));
    }
    public NoiseColumn column(int x,int z){
        long key=((long)x<<32)^(z&0xffffffffL);synchronized(this){var found=columns.get(key);if(found!=null)return found;}
        var values=new BlockState[256];var stone=Interstice.RIFTSTONE.get().defaultBlockState();var air=Blocks.AIR.defaultBlockState();for(int y=0;y<256;y++)values[y]=density(x,y,z)>0?stone:air;
        var result=new NoiseColumn(0,values);synchronized(this){var previous=columns.putIfAbsent(key,result);if(columns.size()>512)columns.remove(columns.keySet().iterator().next());return previous==null?result:previous;}
    }
}
