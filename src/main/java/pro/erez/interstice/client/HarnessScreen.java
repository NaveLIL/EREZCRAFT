package pro.erez.interstice.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import pro.erez.interstice.equipment.HarnessMenu;

public final class HarnessScreen extends AbstractContainerScreen<HarnessMenu> {
    public HarnessScreen(HarnessMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=166;inventoryLabelY=70;}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        GuiMaterials.panel(g,leftPos,topPos,imageWidth,imageHeight,true);
        for(var slot:menu.slots)GuiMaterials.slot(g,leftPos+slot.x,topPos+slot.y,slot.index==0?2:0);
        g.fill(leftPos+77,topPos+31,leftPos+99,topPos+53,0xff7bb9a5);g.fill(leftPos+79,topPos+33,leftPos+97,topPos+51,0xff202727);
        g.drawCenteredString(font,Component.translatable("menu.interstice.harness.slot"),leftPos+88,topPos+19,0xffc5d4cf);
        g.drawCenteredString(font,Component.translatable("menu.interstice.harness.hint"),leftPos+88,topPos+58,0xffaab9b5);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,title,8,6,0xffedf0ed,false);g.drawString(font,playerInventoryTitle,8,70,0xffc7d0cb,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
