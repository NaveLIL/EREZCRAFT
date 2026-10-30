package pro.erez.interstice.mixin;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pro.erez.interstice.client.LowerOceanDh;
@Pseudo @Mixin(targets="com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry",remap=false)
public abstract class DhLowerOceanTextureRegistryMixin {
    @Shadow @Final private ArrayList<byte[]> tilePixelsById;
    @Shadow @Final private ArrayList<short[]> faceTileIdsBySetId;
    @Inject(method={"<init>","clear"},at=@At("TAIL"),remap=false)
    private void interstice$reserveOceanTile(CallbackInfo ci){
        if(tilePixelsById.size()!=1||faceTileIdsBySetId.size()!=1)throw new IllegalStateException("DH initial texture registry changed");
        // A real registered transparent ratio tile: unadapted shader pipelines retain the original LOD color.
        tilePixelsById.add(new byte[16*16*4]);faceTileIdsBySetId.add(new short[]{1,1,1,1,1,1});
    }
    @Inject(method="getOrRegisterBlockStateSetId",at=@At("HEAD"),cancellable=true,remap=false)
    private void interstice$oceanTag(@Coerce Object state,CallbackInfoReturnable<Short> ci){
        if(!LowerOceanDh.active())return;
        if(LowerOceanDh.heavy(state)){LowerOceanDh.TAGGED.increment();ci.setReturnValue((short)1);}
        else if(!LowerOceanDh.ordinaryTextures())ci.setReturnValue((short)0);
    }
}
