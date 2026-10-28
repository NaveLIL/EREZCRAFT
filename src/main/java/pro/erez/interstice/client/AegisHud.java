package pro.erez.interstice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.*;

@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class AegisHud {
    private AegisHud(){}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null||!AegisArmor.worn(mc.player.getItemBySlot(EquipmentSlot.HEAD),EquipmentSlot.HEAD))return;
        int y=Math.max(132,mc.getWindow().getGuiScaledHeight()/2-12);
        int charge=AegisPower.charge(mc.player.getItemBySlot(EquipmentSlot.HEAD));
        Component text=AegisPower.realm(mc.player)?Component.translatable("hud.interstice.aegis.realm",charge*100/AegisPower.CAPACITY)
                :Component.translatable(charge>0?"hud.interstice.aegis.battery":"hud.interstice.aegis.empty",charge*100/AegisPower.CAPACITY);
        event.getGuiGraphics().drawString(mc.font,text,8,y,charge>0||AegisPower.realm(mc.player)?0x8ce6dd:0xff8c70,true);
        boolean sealed=AegisPower.sealed(mc.player);
        int minimum=RealmArmor.SLOTS.stream().mapToInt(s->AegisPower.charge(mc.player.getItemBySlot(s))).min().orElse(0)*100/AegisPower.CAPACITY;
        Component seal=Component.translatable(sealed?"hud.interstice.aegis.sealed":"hud.interstice.aegis.unsealed",minimum);
        event.getGuiGraphics().drawString(mc.font,seal,8,y+12,sealed?0x8ce6dd:0xff8c70,true);
    }
    @SubscribeEvent public static void fog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event){
        if(event.getType()==net.minecraft.world.level.material.FogType.LAVA&&AegisVision.active()){
            event.setNearPlaneDistance(0);event.setFarPlaneDistance(16);event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void overlay(net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent event){
        if(event.getOverlayType()==net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent.OverlayType.FIRE
                &&AegisPower.sealed(event.getPlayer()))event.setCanceled(true);
    }
}
