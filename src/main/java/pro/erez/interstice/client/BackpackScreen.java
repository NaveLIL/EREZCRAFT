package pro.erez.interstice.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import pro.erez.interstice.equipment.BackpackMenu;

public final class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {
    private static final int TEXT=0xffe3e9e1,MUTED=0xffbbc6ba,ACCENT=0xff91d0bd;
    private final List<Button> controls=new ArrayList<>();
    private Button mode;
    public BackpackScreen(BackpackMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=menu.width;imageHeight=236;inventoryLabelX=(imageWidth-162)/2;inventoryLabelY=126;titleLabelY=5;}
    @Override protected void init(){super.init();controls.clear();int x=leftPos+(imageWidth-166)/2;
        mode=button("collect",BackpackMenu.MODE,x,28);button("sort",BackpackMenu.SORT,x+31,32);button("stash",BackpackMenu.STASH,x+66,46);button("refill",BackpackMenu.REFILL,x+115,51);updateControls();}
    private Button button(String label,int action,int x,int width){var b=addRenderableWidget(Button.builder(Component.translatable("menu.interstice.backpack."+label),button->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}).bounds(x,topPos+214,width,18).build());b.setTooltip(Tooltip.create(Component.translatable("menu.interstice.backpack."+label+"_hint")));controls.add(b);return b;}
    @Override protected void containerTick(){super.containerTick();updateControls();}
    private void updateControls(){for(var b:controls)b.active=menu.getCarried().isEmpty();if(mode!=null){mode.setMessage(Component.translatable("menu.interstice.backpack.mode."+menu.data.get(0)));mode.setTooltip(Tooltip.create(Component.translatable("tooltip.interstice.backpack.mode."+menu.data.get(0))));}}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        GuiMaterials.panel(g,leftPos,topPos,imageWidth,imageHeight,true);
        for(var slot:menu.slots){int x=leftPos+slot.x,y=topPos+slot.y;boolean locked=slot.getContainerSlot()==menu.sourceSlot&&slot.container!=menu.container;GuiMaterials.slot(g,x,y,locked?1:0);}
        g.fill(leftPos+5,topPos+123,leftPos+imageWidth-5,topPos+124,0xff61786f);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){String used=menu.data.get(1)+"/"+menu.capacity;int counter=font.width(used);g.drawString(font,font.plainSubstrByWidth(title.getString(),imageWidth-counter-22),8,5,TEXT,false);g.drawString(font,used,imageWidth-counter-8,5,ACCENT,false);g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,MUTED,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
}
