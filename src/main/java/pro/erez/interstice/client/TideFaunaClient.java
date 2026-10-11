package pro.erez.interstice.client;

import net.minecraft.client.renderer.entity.PhantomRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.fauna.TideFauna;

/** Reuses the native phantom's model, animations and eye layer without copying textures. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class TideFaunaClient {
    private TideFaunaClient(){}
    @SuppressWarnings({"rawtypes","unchecked"})
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(TideFauna.TIDE_PHANTOM.get(),(net.minecraft.client.renderer.entity.EntityRendererProvider)PhantomRenderer::new);
    }
}
