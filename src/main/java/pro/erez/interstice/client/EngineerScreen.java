package pro.erez.interstice.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import pro.erez.interstice.gear.*;

public final class EngineerScreen extends AbstractContainerScreen<EngineerMenu>{
    public EngineerScreen(EngineerMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=194;inventoryLabelY=100;}
    @Override protected void init(){
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("menu.interstice.engineer.visor"),b->send(0)).bounds(leftPos+8,topPos+61,76,18).build());
        addRenderableWidget(Button.builder(Component.translatable("menu.interstice.engineer.night"),b->send(1)).bounds(leftPos+92,topPos+61,76,18).build());
        var pulse=Button.builder(Component.translatable("menu.interstice.engineer.pulse"),b->send(2)).bounds(leftPos+8,topPos+82,160,16).build();pulse.setTooltip(Tooltip.create(Component.translatable("tooltip.interstice.engineer.pulse")));addRenderableWidget(pulse);
    }
    private void send(int button){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button);}
    @Override protected void renderBg(GuiGraphics g,float dt,int mx,int my){
        g.fill(leftPos-1,topPos-1,leftPos+imageWidth+1,topPos+imageHeight+1,0xff101a20);
        GuiMaterials.tiles(g,GuiMaterials.METAL,leftPos,topPos,imageWidth,imageHeight);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xe51c2a30);
        for(var slot:menu.slots){int x=leftPos+slot.x,y=topPos+slot.y;g.fill(x-1,y-1,x+17,y+17,0xff9aafaa);g.fill(x,y,x+16,y+16,0xff102027);}
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,title,8,6,0xe2ece8,false);
        var head=minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
        g.drawString(font,Component.translatable("menu.interstice.engineer.settings",Component.translatable(EngineerSystems.visor(head)?"menu.interstice.engineer.on":"menu.interstice.engineer.off"),Component.translatable(EngineerSystems.night(head)?"menu.interstice.engineer.on":"menu.interstice.engineer.off")),8,19,0xaccdbb,false);
        for(int i=0;i<3;i++){var name=Component.translatable("menu.interstice.engineer.socket."+i);g.drawString(font,name,38+i*50-font.width(name)/2,53,0xa9c9c1,false);}
        g.drawString(font,playerInventoryTitle,8,100,0xaccdbb,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float dt){super.render(g,x,y,dt);renderTooltip(g,x,y);}
}
