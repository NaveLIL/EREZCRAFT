package pro.erez.interstice.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.equipment.*;

@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class BackpackClient {
    private static final KeyMapping OPEN=new KeyMapping("key.interstice.open_backpack",InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_B,"key.categories.interstice");
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(ExpeditionEquipment.PACK_MENU.get(),BackpackScreen::new);event.register(ExpeditionEquipment.HARNESS_MENU.get(),HarnessScreen::new);}
    @SubscribeEvent public static void inventoryButton(ScreenEvent.Init.Post event){
        if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen)||!(screen instanceof InventoryScreen||screen instanceof CreativeModeInventoryScreen))return;
        var button=Button.builder(Component.translatable("menu.interstice.harness.button"),b->PacketDistributor.sendToServer(new BackpackNetworking.OpenHarness())).bounds(screen.getGuiLeft()+screen.getXSize()+4,screen.getGuiTop()+4,43,18).build();
        button.setTooltip(Tooltip.create(Component.translatable("menu.interstice.harness.button_hint")));event.addListener(button);
    }
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(OPEN);}
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event){
        for(var skin:event.getSkins()){
            net.minecraft.client.renderer.entity.player.PlayerRenderer renderer=event.getSkin(skin);
            if(renderer!=null)renderer.addLayer(new BackpackLayer(renderer));
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();while(OPEN.consumeClick())if(mc.player!=null&&mc.screen==null&&!mc.isPaused())PacketDistributor.sendToServer(new BackpackNetworking.Open());}
    private BackpackClient(){}
}
