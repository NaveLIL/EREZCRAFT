package pro.erez.interstice.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.client.LowerOceanUv;
import pro.erez.interstice.geometry.GeometryProfiles;
@Mixin(LiquidBlockRenderer.class)
public abstract class LowerOceanVanillaUvMixin {
    @WrapMethod(method="tesselate")
    private void interstice$oc01(BlockAndTintGetter world,BlockPos pos,VertexConsumer out,BlockState block,FluidState fluid,Operation<Void> original){
        if(fluid.getFluidType()!=Interstice.HEAVY_TYPE.get()){original.call(world,pos,out,block,fluid);return;}
        var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"block/heavy_oc01"));
        var wrapped=new LowerOceanUv.Consumer(out,pos,GeometryProfiles.get(world),sprite,LowerOceanUv.ocean(world,pos,GeometryProfiles.get(world)));
        original.call(world,pos,wrapped,block,fluid);wrapped.finish();
    }
}
