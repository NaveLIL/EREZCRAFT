package pro.erez.interstice.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pro.erez.interstice.vehicle.EitanController;
import java.util.*;

/** Physical monitor interaction and an optional enlarged, drivable camera view. */
public final class EitanScreen extends Screen {
    private final EitanController vehicle;private int monitor;
    private final Set<Integer> held=new HashSet<>();
    public EitanScreen(EitanController c,int view){super(Component.literal("EITAN E01"));vehicle=c;monitor=view;}
    public int monitorView(){return monitor;}
    @Override protected void init(){int left=width/2-210,top=height-75;addRenderableWidget(Button.builder(Component.translatable("interstice.vehicle.access_title"),b->net.neoforged.neoforge.network.PacketDistributor.sendToServer(new pro.erez.interstice.vehicle.VehicleNetwork.Request(vehicle.getBlockPos(),0,""))).bounds(width-112,8,104,20).build());String[] keys={"engine","lights","ramp","door","roof","parking","exit"};for(int i=0;i<keys.length;i++){final int index=i;addRenderableWidget(Button.builder(Component.translatable("interstice.eitan."+keys[i]),b->{if(index==0)EitanClient.action(vehicle,0,0);if(index==1)EitanClient.action(vehicle,1,0);if(index==2)EitanClient.action(vehicle,2,0);if(index==3)EitanClient.action(vehicle,2,1);if(index==4){for(int k=2;k<6;k++)EitanClient.action(vehicle,2,k);}if(index==5)EitanClient.action(vehicle,4,0);if(index==6){EitanClient.action(vehicle,3,0);onClose();}}).bounds(left+i*60,top,58,20).build());}for(int i=0;i<4;i++){final int id=i;addRenderableWidget(Button.builder(Component.translatable("interstice.eitan.view_"+i),b->monitor=id==3?-2:id).bounds(width/2-144+i*72,34,70,20).build());}addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-40,height-29,80,20).build());}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){g.fill(0,0,width,height,0xf4142029);g.drawCenteredString(font,title,width/2,12,0xffe3c889);double aspect=(monitor>=0&&monitor<3?1:3)*(.215/.15);int h=Math.min(height-160,360),w=(int)Math.min(width-30,h*aspect);h=(int)(w/aspect);int x=(width-w)/2,y=65;EitanCameras.gui(g,x,y,w,h,monitor,vehicle);g.drawString(font,Component.translatable(vehicle.powered()?"interstice.eitan.engine_on":"interstice.eitan.engine_off"),14,height-114,0xffe3c889);g.drawString(font,String.format(Locale.ROOT,"%.1f km/h  ·  %s %ds",vehicle.speed*3.6,Component.translatable("interstice.eitan.fuel").getString(),vehicle.fuelTicks/20),14,height-99,0xffbdcbcc);super.render(g,mouseX,mouseY,partial);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(control(key,scan)){held.add(key);return true;}return super.keyPressed(key,scan,mods);}
    @Override public boolean keyReleased(int key,int scan,int mods){held.remove(key);return super.keyReleased(key,scan,mods);}
    private boolean control(int key,int scan){var o=minecraft.options;return o.keyUp.matches(key,scan)||o.keyDown.matches(key,scan)||o.keyLeft.matches(key,scan)||o.keyRight.matches(key,scan)||o.keyJump.matches(key,scan)||o.keySprint.matches(key,scan);}
    private boolean down(net.minecraft.client.KeyMapping key){return held.stream().anyMatch(k->key.matches(k,0));}
    public int forward(){return (down(minecraft.options.keyUp)?1:0)-(down(minecraft.options.keyDown)?1:0);}
    public int turn(){return (down(minecraft.options.keyLeft)?1:0)-(down(minecraft.options.keyRight)?1:0);}
    public boolean braking(){return down(minecraft.options.keyJump);}
    public boolean boost(){return down(minecraft.options.keySprint);}
    @Override public void removed(){held.clear();EitanCameras.front();}
}
