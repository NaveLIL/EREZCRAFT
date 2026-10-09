package pro.erez.interstice.worldgen.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** The installed game's cache2d contract keyed in the supplied (transformed) coordinates, on each worker. */
public final class NativeCache2D implements DensityFunction {
    public static final MapCodec<NativeCache2D> CODEC=DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").xmap(NativeCache2D::new,f->f.input);
    private static final class Value{boolean set;long key;double value;}
    private final DensityFunction input;private final ThreadLocal<Value> last=ThreadLocal.withInitial(Value::new);
    public NativeCache2D(DensityFunction input){this.input=input;}
    @Override public double compute(FunctionContext context){long key=((long)context.blockX()<<32)^(context.blockZ()&0xffffffffL);var found=last.get();if(found.set&&found.key==key)return found.value;double result=input.compute(context);found.key=key;found.value=result;found.set=true;return result;}
    @Override public void fillArray(double[] values,ContextProvider provider){provider.fillAllDirectly(values,this);}
    @Override public DensityFunction mapAll(Visitor visitor){return visitor.apply(new NativeCache2D(input.mapAll(visitor)));}
    @Override public double minValue(){return input.minValue();}@Override public double maxValue(){return input.maxValue();}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return KeyDispatchDataCodec.of(CODEC);}
}
