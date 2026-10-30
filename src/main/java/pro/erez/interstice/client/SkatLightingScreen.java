package pro.erez.interstice.client;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import pro.erez.interstice.ship.SkatNetwork;
/** Ordinary crew control panel; server authorizes every change against the moving helm. */
public final class SkatLightingScreen extends Screen {
    public final BlockPos helm;private int mask;private final Map<Integer,Button> switches=new LinkedHashMap<>();
    public SkatLightingScreen(BlockPos helm,int mask){super(Component.translatable("screen.interstice.skat_lighting"));this.helm=helm;this.mask=mask;}
    public int lighting(){return mask;}
    public Button switchFor(int channel){return switches.get(channel);}
    private Component label(int c){return Component.translatable("screen.interstice.skat_light_"+c).append(": ").append(Component.translatable((mask&c)!=0?"options.on":"options.off"));}
    public void update(int mask){this.mask=mask;switches.forEach((c,b)->b.setMessage(label(c)));}
    @Override protected void init(){switches.clear();addRenderableWidget(Button.builder(Component.translatable("interstice.vehicle.access_title"),b->PacketDistributor.sendToServer(new pro.erez.interstice.vehicle.VehicleNetwork.Request(helm,0,""))).bounds(width/2-98,height/2+47,196,20).build());int i=0;for(int c:new int[]{2,1,4}){var b=new Switch(width/2-98,height/2-40+28*i++,label(c),button->PacketDistributor.sendToServer(new SkatNetwork.Lighting(helm,c,(mask&c)==0)));switches.put(c,addRenderableWidget(b));}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        renderBackground(g,mx,my,dt);
        int x=width/2-112,y=height/2-78;g.fill(x-2,y-2,x+226,y+154,0xff101b21);g.fill(x,y,x+224,y+152,0xf51c2a30);g.fill(x,y,x+224,y+1,0xff75c6c2);g.fill(x,y+151,x+224,y+128,0xff8c7959);
        g.drawString(font,title,x+14,y+13,0xffb8d9d6,false);for(var widget:renderables)widget.render(g,mx,my,dt);
    }
    private static final class Switch extends Button {
        Switch(int x,int y,Component text,OnPress press){super(x,y,196,22,text,press,DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){int x=getX(),y=getY();g.fill(x,y,x+width,y+height,isHoveredOrFocused()?0xff31464c:0xff23353b);g.fill(x,y,x+2,y+height,isHoveredOrFocused()?0xff75c6c2:0xff8c7959);g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,getMessage(),x+width/2,y+7,0xffd8e5df);}
    }
}
