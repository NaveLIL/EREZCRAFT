package pro.erez.interstice.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import pro.erez.interstice.vehicle.VehicleNetwork;
public final class VehicleAccessScreen extends Screen {
    private final VehicleNetwork.Panel data;private EditBox pin;
    public VehicleAccessScreen(VehicleNetwork.Panel data){super(Component.translatable("interstice.vehicle.access_title"));this.data=data;}
    private void send(int action){PacketDistributor.sendToServer(new VehicleNetwork.Request(data.anchor(),action,pin.getValue()));}
    @Override protected void init(){int x=width/2-135,y=height/2-100;pin=new EditBox(font,x+16,y+61,238,22,Component.literal("PIN"));pin.setMaxLength(6);pin.setFilter(v->v.matches("[0-9]{0,6}"));pin.setFormatter((v,i)->net.minecraft.util.FormattedCharSequence.forward("•".repeat(v.length()),net.minecraft.network.chat.Style.EMPTY));addRenderableWidget(pin);setInitialFocus(pin);
        addRenderableWidget(Button.builder(Component.translatable(data.owner()?"interstice.vehicle.set_pin":"interstice.vehicle.enter_pin"),b->send(data.owner()?2:1)).bounds(x+16,y+93,238,20).build());
        if(data.owner())addRenderableWidget(Button.builder(Component.translatable(data.maintenance()?"interstice.vehicle.maintenance_off":"interstice.vehicle.maintenance_on"),b->send(3)).bounds(x+16,y+124,238,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x+16,y+165,238,20).build());}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){renderBackground(g,mx,my,dt);int x=width/2-135,y=height/2-100;g.fill(x-1,y-1,x+271,y+201,0xff9b8255);g.fill(x,y,x+270,y+200,0xff142029);g.drawString(font,title,x+16,y+14,0xffe3c889,false);g.drawString(font,Component.translatable(data.owner()?"interstice.vehicle.owner_hint":data.admitted()?"interstice.vehicle.crew_hint":data.configured()?"interstice.vehicle.pin_hint":"interstice.vehicle.unconfigured"),x+16,y+37,0xffd0dad8,false);super.render(g,mx,my,dt);}
    @Override public boolean isPauseScreen(){return false;}
}
