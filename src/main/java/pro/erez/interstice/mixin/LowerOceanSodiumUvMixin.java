package pro.erez.interstice.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.client.LowerOceanUv;
import pro.erez.interstice.geometry.*;
@Pseudo @Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer",remap=false)
public abstract class LowerOceanSodiumUvMixin {
    @Unique private static final ThreadLocal<LowerOceanUv.Context> interstice$oc01=new ThreadLocal<>();
    @Inject(method="render",at=@At("HEAD"),remap=false)
    private void interstice$begin(@Coerce Object world,BlockState block,FluidState fluid,BlockPos pos,BlockPos offset,@Coerce Object collector,@Coerce Object buffers,@Coerce Object material,@Coerce Object colors,TextureAtlasSprite[] sprites,CallbackInfo ci){
        interstice$oc01.remove();if(fluid.getFluidType()==Interstice.HEAVY_TYPE.get())interstice$oc01.set(new LowerOceanUv.Context(pos.immutable(),GeometryProfiles.get((BlockAndTintGetter)world),sprites[0],LowerOceanUv.ocean((BlockAndTintGetter)world,pos,GeometryProfiles.get((BlockAndTintGetter)world))));
    }
    @Inject(method="render",at=@At("RETURN"),remap=false)
    private void interstice$end(CallbackInfo ci){interstice$oc01.remove();}
    @Inject(method="writeQuad",at=@At("HEAD"),cancellable=true,remap=false)
    private void interstice$uv(@Coerce Object buffers,@Coerce Object collector,@Coerce Object material,BlockPos offset,@Coerce Object quad,@Coerce Object face,boolean flipped,CallbackInfo ci){
        var c=interstice$oc01.get();if(c!=null&&c.ocean()&&LowerOceanUv.top(quad)){ci.cancel();return;}if(c!=null)LowerOceanUv.sodium(quad,c.pos(),c.profile(),c.sprite());
    }
}
