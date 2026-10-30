package pro.erez.interstice.client;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import pro.erez.interstice.Interstice;
/** Carry the authored 90/255 opacity in the sprite, which shader packs also read.
 * Only the owned glass atlas tile is adapted, once per reload, including mipmaps.
 * Original PNG bytes and every RGB value are preserved. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class SkatGlassSprite {
    public static final int OPACITY=90;
    public static long uploads,pixels;public static int levels;
    @SubscribeEvent public static void stitched(TextureAtlasStitchedEvent event){
        var atlas=event.getAtlas();if(!atlas.location().equals(TextureAtlas.LOCATION_BLOCKS))return;
        var id=ResourceLocation.fromNamespaceAndPath(Interstice.ID,"block/skat/glass");var sprite=atlas.getSprite(id);if(!sprite.contents().name().equals(id))return;
        int count=0;for(var image:sprite.contents().byMipLevel){if(image==null)continue;count++;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int rgba=image.getPixelRGBA(x,y);image.setPixelRGBA(x,y,(rgba&0x00ffffff)|(Math.min(rgba>>>24,OPACITY)<<24));pixels++;}}
        int previous=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        try{RenderSystem.bindTexture(atlas.getId());sprite.contents().uploadFirstFrame(sprite.getX(),sprite.getY());uploads++;levels=count;}finally{RenderSystem.bindTexture(previous);}
    }
    private SkatGlassSprite(){}
}
