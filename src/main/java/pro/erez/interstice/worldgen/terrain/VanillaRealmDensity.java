package pro.erez.interstice.worldgen.terrain;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.*;

/** Vanilla continents and local outer-End islands share one continuous density, before native interpolation. */
public record VanillaRealmDensity(DensityFunction land,DensityFunction islands,DensityFunction continents) implements DensityFunction {
    public static final MapCodec<VanillaRealmDensity> CODEC=RecordCodecBuilder.mapCodec(i->i.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("land").forGetter(VanillaRealmDensity::land),DensityFunction.HOLDER_HELPER_CODEC.fieldOf("islands").forGetter(VanillaRealmDensity::islands),DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(VanillaRealmDensity::continents)).apply(i,VanillaRealmDensity::new));
    public static double islandWeight(double continent){double t=Math.max(0,Math.min(1,(-.20-continent)/.15));return t*t*(3-2*t);}
    @Override public double compute(FunctionContext c){
        if(c.blockY()<4)return 1;if(c.blockY()>205)return -1;
        double weight=islandWeight(continents.compute(c));double landValue=weight>=1?-1:land.compute(c);double islandValue=weight<=0?-1:islands.compute(c);
        return landValue*(1-weight)+islandValue*weight;
    }
    @Override public void fillArray(double[] values,ContextProvider provider){provider.fillAllDirectly(values,this);}
    @Override public DensityFunction mapAll(Visitor visitor){return visitor.apply(new VanillaRealmDensity(land.mapAll(visitor),islands.mapAll(visitor),continents.mapAll(visitor)));}
    @Override public double minValue(){return Math.min(-1,Math.min(land.minValue(),islands.minValue()));}
    @Override public double maxValue(){return Math.max(1,Math.max(land.maxValue(),islands.maxValue()));}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return KeyDispatchDataCodec.of(CODEC);}
}
