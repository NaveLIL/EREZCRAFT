package pro.erez.interstice.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.RealmAgriculture;

@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class AgricultureClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(RealmAgriculture.RETORT_MENU.get(),RetortScreen::new);}
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(
            RealmAgriculture.RESERVOIR_ITEM.get(),net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"filled"),
            (stack,world,entity,seed)->pro.erez.interstice.agriculture.AgricultureItems.ReservoirItem.full(stack)?1:0));}
}
