package pro.erez.interstice.worldgen.terrain;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.util.KeyDispatchDataCodec;

/** References the installed game's terrain functions, without copying its implementation or changing its registries. */
public final class VanillaField implements DensityFunction {
    public static final MapCodec<VanillaField> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(f->f.settings),Codec.STRING.fieldOf("field").forGetter(f->f.field),
        Codec.DOUBLE.optionalFieldOf("xz_scale",1.35).forGetter(f->f.scale),Codec.DOUBLE.optionalFieldOf("y_scale",1.25).forGetter(f->f.yScale),
        Codec.DOUBLE.optionalFieldOf("y_offset",20.5).forGetter(f->f.yOffset),Codec.INT.optionalFieldOf("xz_offset",0).forGetter(f->f.offset)
    ).apply(i,VanillaField::new));
    private final Holder<NoiseGeneratorSettings> settings;private final String field;private final double scale,yScale,yOffset;private final int offset;
    private final DensityFunction mapped;
    private static final KeyDispatchDataCodec<? extends DensityFunction> END_CODEC=DensityFunctions.endIslands(0).codec();
    public VanillaField(Holder<NoiseGeneratorSettings> settings,String field,double scale,double yScale,double yOffset,int offset){this(settings,field,scale,yScale,yOffset,offset,null);}
    private VanillaField(Holder<NoiseGeneratorSettings> settings,String field,double scale,double yScale,double yOffset,int offset,DensityFunction mapped){
        this.settings=settings;this.field=field;this.scale=scale;this.yScale=yScale;this.yOffset=yOffset;this.offset=offset;this.mapped=mapped;
        if(!java.util.Set.of("solid","terrain","continents","erosion","ridges","temperature","vegetation","depth").contains(field)||scale<.25||scale>4||yScale<=0||yScale>4)throw new IllegalArgumentException("Unsupported native field transform");
    }
    private DensityFunction input(){var r=settings.value().noiseRouter();return switch(field){case"solid"->r.finalDensity();case"terrain"->r.initialDensityWithoutJaggedness();case"continents"->r.continents();case"erosion"->r.erosion();case"ridges"->r.ridges();case"temperature"->r.temperature();case"vegetation"->r.vegetation();default->r.depth();};}
    public VanillaField asTerrain(){return new VanillaField(settings,"terrain",scale,yScale,yOffset,offset);}
    @Override public double compute(FunctionContext c){return (mapped==null?input():mapped).compute(new SinglePointContext((int)Math.floor(c.blockX()*scale)+offset,(int)Math.floor(c.blockY()*yScale+yOffset),(int)Math.floor(c.blockZ()*scale)+offset));}
    @Override public void fillArray(double[] values,ContextProvider provider){provider.fillAllDirectly(values,this);}
    @Override public DensityFunction mapAll(Visitor visitor){
        // An inner native cache is keyed in native coordinates. Strip its markers before applying
        // NoiseChunk's visitor, then interpolate the COMPLETE transformed field outside this node.
        var value=(mapped==null?input():mapped).mapAll(new Visitor(){
            @Override public NoiseHolder visitNoise(NoiseHolder noise){return visitor.visitNoise(noise);}
            @Override public DensityFunction apply(DensityFunction f){
                if(f instanceof DensityFunctions.MarkerOrMarked m){if(String.valueOf((Object)m.type()).equalsIgnoreCase("cache2d"))return visitor.apply(new NativeCache2D(m.wrapped()));return m.wrapped();}
                if(f instanceof DensityFunctions.HolderHolder h)return h.function().value();
                boolean end=f.codec()==END_CODEC;var result=visitor.apply(f);return end?new NativeCache2D(result):result;
            }
        });return visitor.apply(new VanillaField(settings,field,scale,yScale,yOffset,offset,value));
    }
    @Override public double minValue(){return mapped==null?-1000000:mapped.minValue();}
    @Override public double maxValue(){return mapped==null?1000000:mapped.maxValue();}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return KeyDispatchDataCodec.of(CODEC);}
}
