package pro.erez.interstice.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.fauna.*;
import pro.erez.interstice.entity.client.LozhnokorenModel;
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class LozhnokorenClient {
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){e.registerLayerDefinition(LozhnokorenModel.LAYER_LOCATION,LozhnokorenModel::createBodyLayer);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(LozhnokorenFauna.TYPE.get(),Renderer::new);}
    public static final class Renderer extends MobRenderer<Lozhnokoren,LozhnokorenModel>{
        public Renderer(EntityRendererProvider.Context c){super(c,new LozhnokorenModel(c.bakeLayer(LozhnokorenModel.LAYER_LOCATION)),1.6f);}
        @Override public ResourceLocation getTextureLocation(Lozhnokoren e){return ResourceLocation.fromNamespaceAndPath(Interstice.ID,"textures/entity/lozhnokoren_lk01.png");}
    }
}
