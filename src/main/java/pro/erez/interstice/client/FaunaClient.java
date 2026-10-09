package pro.erez.interstice.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import pro.erez.interstice.Interstice;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import pro.erez.interstice.fauna.RealmFauna;

/** Client-only registry hooks for the accepted canopy model and entity renderer. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class FaunaClient {
    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CanopySentinelModel.LAYER_LOCATION, CanopySentinelModel::createBodyLayer);
    }

    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RealmFauna.CANOPY_SENTINEL.get(), CanopySentinelRenderer::new);
    }

    private FaunaClient() {}
}
