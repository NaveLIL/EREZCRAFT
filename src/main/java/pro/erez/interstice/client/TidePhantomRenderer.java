package pro.erez.interstice.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PhantomRenderer;
import net.minecraft.world.entity.monster.Phantom;

/** Keeps the vanilla bitmap/animation; vertex palette follows the upper sea. */
public final class TidePhantomRenderer extends PhantomRenderer {
    public static final int UPPER_SEA_TINT=0xffd9d6a3;
    public TidePhantomRenderer(EntityRendererProvider.Context context){
        super(context);
        model=new PhantomModel<Phantom>(context.bakeLayer(ModelLayers.PHANTOM)){
            @Override public void renderToBuffer(PoseStack pose,VertexConsumer vertices,int light,int overlay,int color){
                int tint=(color&0xff000000)|(((color>>16)&255)*217/255<<16)|(((color>>8)&255)*214/255<<8)|((color&255)*163/255);
                super.renderToBuffer(pose,vertices,light,overlay,tint);
            }
        };
    }
}
