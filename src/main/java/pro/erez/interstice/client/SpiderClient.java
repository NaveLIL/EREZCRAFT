package pro.erez.interstice.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.client.CaveRiftSpiderModel;
import pro.erez.interstice.entity.client.CaveRiftSpiderRenderer;
import pro.erez.interstice.entity.client.ToxinSpitRenderer;

@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class SpiderClient {
    private SpiderClient() {}

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CaveRiftSpiderModel.LAYER_LOCATION, CaveRiftSpiderModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Interstice.CAVE_RIFT_SPIDER.get(), CaveRiftSpiderRenderer::new);
        event.registerEntityRenderer(Interstice.TOXIN_SPIT.get(), ToxinSpitRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(Interstice.RIFT_COBWEB.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(Interstice.SPIDER_EGG_SAC.get(), RenderType.cutout());
        });
    }
}
