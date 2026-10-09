package pro.erez.interstice.worldgen.terrain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import pro.erez.interstice.Interstice;

/** V6 native corner sampler: actual settings, floorDiv origin, and NoiseChunk's CacheAllInCell lerp3 order. */
public final class NativeColumnSamplerV6 {
    private record Point(int x,int y,int z,boolean uncarved) {}
    private final DensityFunction carved,uncarved;
    private final NoiseSettings settings;
    private final LinkedHashMap<Point,Double> corners=new LinkedHashMap<>(1024,.75F,true);
    private final LinkedHashMap<Long,NoiseColumn> columns=new LinkedHashMap<>(128,.75F,true);
    private static final Map<RandomState,NativeColumnSamplerV6> SAMPLERS=new WeakHashMap<>();
    private NativeColumnSamplerV6(RandomState state){
        var density=TensionTerrainV6.root(state);carved=density;uncarved=unwrap(state.router().initialDensityWithoutJaggedness());
        if(!(uncarved instanceof TensionRealmDensity initial)||initial.carved()||!density.carved()
                ||!density.samplingSettings().equals(initial.samplingSettings()))
            throw new IllegalArgumentException("V6 requires its separate carved/uncarved native density fields");
        settings=density.samplingSettings();
    }
    static DensityFunction unwrap(DensityFunction density){
        while(density instanceof DensityFunctions.MarkerOrMarked||density instanceof DensityFunctions.HolderHolder){
            if(density instanceof DensityFunctions.MarkerOrMarked marker)density=marker.wrapped();
            else density=((DensityFunctions.HolderHolder)density).function().value();
        }return density;
    }
    public static synchronized NativeColumnSamplerV6 of(RandomState state){return SAMPLERS.computeIfAbsent(state,NativeColumnSamplerV6::new);}
    public static NativeColumnSamplerV6 of(RandomState state,NoiseSettings actual){
        var result=of(state);if(!result.settings.equals(actual))throw new IllegalArgumentException("V6 sampler and generator noise cells differ");return result;
    }
    public NoiseSettings samplingSettings(){return settings;}
    private synchronized double corner(int x,int y,int z,boolean terrain){
        var key=new Point(x,y,z,terrain);var found=corners.get(key);if(found!=null)return found;
        double value=(terrain?uncarved:carved).compute(new DensityFunction.SinglePointContext(x,y,z));
        corners.put(key,value);if(corners.size()>65536)corners.remove(corners.keySet().iterator().next());return value;
    }
    public double density(int x,int y,int z){return density(x,y,z,false);}
    public double density(int x,int y,int z,boolean terrain){
        int width=settings.getCellWidth(),height=settings.getCellHeight();
        int xx=Math.floorDiv(x,width)*width,yy=Math.floorDiv(y,height)*height,zz=Math.floorDiv(z,width)*width;
        double tx=(x-xx)/(double)width,ty=(y-yy)/(double)height,tz=(z-zz)/(double)width;
        return Mth.lerp3(tx,ty,tz,
                corner(xx,yy,zz,terrain),corner(xx+width,yy,zz,terrain),
                corner(xx,yy+height,zz,terrain),corner(xx+width,yy+height,zz,terrain),
                corner(xx,yy,zz+width,terrain),corner(xx+width,yy,zz+width,terrain),
                corner(xx,yy+height,zz+width,terrain),corner(xx+width,yy+height,zz+width,terrain));
    }
    public NoiseColumn column(int x,int z){
        long key=((long)x<<32)^(z&0xffffffffL);
        synchronized(this){var found=columns.get(key);if(found!=null)return found;}
        var values=new BlockState[settings.height()];var rock=Interstice.RIFTSTONE.get().defaultBlockState();var air=Blocks.AIR.defaultBlockState();
        for(int i=0;i<values.length;i++)values[i]=density(x,settings.minY()+i,z)>0?rock:air;
        var result=new NoiseColumn(settings.minY(),values);
        synchronized(this){var old=columns.putIfAbsent(key,result);if(columns.size()>512)columns.remove(columns.keySet().iterator().next());return old==null?result:old;}
    }
}
