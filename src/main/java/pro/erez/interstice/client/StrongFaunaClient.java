package pro.erez.interstice.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.fauna.StrongRealmFauna;

@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class StrongFaunaClient {
    private StrongFaunaClient() {}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(UpperSeaGhastModel.LAYER, UpperSeaGhastModel::createLayer);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StrongRealmFauna.UPPER_SEA_GHAST.get(), UpperSeaGhastRenderer::new);
        event.registerEntityRenderer(StrongRealmFauna.UPPER_SEA_CHARGE.get(), UpperSeaChargeRenderer::new);
    }
}
