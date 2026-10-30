package pro.erez.interstice.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import pro.erez.interstice.vehicle.*;
import java.util.Locale;

public final class EitanClient {
    private static int lastSeat=-1;
    private static final KeyMapping PANEL=new KeyMapping("key.interstice.eitan_panel",InputConstants.KEY_V,"key.categories.interstice");
    public static void register(IEventBus bus){bus.addListener(EitanVehicleModels::geometry);bus.addListener(EitanVehicleModels::additional);bus.addListener((RegisterKeyMappingsEvent e)->e.register(PANEL));bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{e.registerBlockEntityRenderer(EitanVehicles.CONTROL_ENTITY.get(),EitanRenderer::new);e.registerEntityRenderer(EitanVehicles.SEAT_ENTITY.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);});NeoForge.EVENT_BUS.addListener(EitanClient::tick);NeoForge.EVENT_BUS.addListener(EitanClient::hud);NeoForge.EVENT_BUS.addListener(EitanCameras::frame);}
    public static EitanController vehicle(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.player.getVehicle() instanceof EitanSeatEntity s?s.controller():null;}
    public static void action(EitanController c,int action,int index){PacketDistributor.sendToServer(new EitanNetwork.Action(c.getBlockPos(),action,index));}
    public static void open(EitanNetwork.Panel packet){var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.getBlockEntity(packet.controller()) instanceof EitanController c)mc.setScreen(new EitanScreen(c,packet.monitor()));}
    public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){EitanCameras.reset();lastSeat=-1;return;}var c=vehicle();if(c==null){lastSeat=-1;return;}if(mc.player.getVehicle() instanceof EitanSeatEntity seat&&lastSeat!=seat.getId()){lastSeat=seat.getId();mc.player.setYRot(seat.initialYaw());mc.player.yRotO=mc.player.getYRot();mc.player.setYHeadRot(mc.player.getYRot());mc.player.setXRot(seat.seatIndex()==0?28:0);mc.player.xRotO=mc.player.getXRot();}EitanCameras.touch(c);while(PANEL.consumeClick())mc.setScreen(new EitanScreen(c,-1));if(!c.pilot(mc.player)||mc.level.getGameTime()%2!=0)return;int forward=0,turn=0;boolean brake=false,boost=false;
        if(mc.screen==null){forward=(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0);turn=(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0);brake=mc.options.keyJump.isDown();boost=mc.options.keySprint.isDown();}
        else if(mc.screen instanceof EitanScreen screen){forward=screen.forward();turn=screen.turn();brake=screen.braking();boost=screen.boost();}else brake=true;
        PacketDistributor.sendToServer(new EitanNetwork.Input(c.getBlockPos(),forward,turn,brake,boost));
    }
    public static void hud(RenderGuiEvent.Post event){var mc=Minecraft.getInstance();var c=vehicle();if(c==null||mc.options.hideGui||mc.screen!=null)return;var g=event.getGuiGraphics();int x=12,y=g.guiHeight()-83;g.fill(x-1,y-1,x+227,y+68,0xff9b8255);g.fill(x,y,x+226,y+67,0xe6142029);g.drawString(mc.font,"EITAN E01  ·  "+String.format(Locale.ROOT,"%.1f km/h",c.speed*3.6),x+8,y+7,0xffe3c889,false);g.drawString(mc.font,Component.translatable(c.powered()?"interstice.eitan.engine_on":"interstice.eitan.engine_off").getString()+" · "+Component.translatable("interstice.eitan.fuel").getString()+" "+c.fuelTicks/20+"s",x+8,y+21,0xffd0dad8,false);g.drawString(mc.font,Component.translatable("interstice.eitan."+c.brakeStatus()),x+8,y+35,0xffe3c889,false);g.drawString(mc.font,Component.translatable("interstice.eitan.controls"),x+8,y+50,0xffa7b7ba,false);}
    private EitanClient(){}
}
