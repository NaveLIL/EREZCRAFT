package pro.erez.interstice.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import pro.erez.interstice.ship.SkatCargoMenu;
/** Uses the approved dark teal/brass card, without changing vanilla slot positions. */
public final class SkatCargoScreen extends AbstractContainerScreen<SkatCargoMenu> {
    public SkatCargoScreen(SkatCargoMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageHeight=222;inventoryLabelY=imageHeight-94;}
    @Override protected void renderBg(GuiGraphics g,float dt,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x-2,y-2,x+imageWidth+2,y+imageHeight+2,0xff101b21);g.fill(x,y,x+imageWidth,y+imageHeight,0xf51c2a30);g.fill(x,y,x+imageWidth,y+1,0xff75c6c2);g.fill(x,y+imageHeight-1,x+imageWidth,y+imageHeight,0xff8c7959);
        for(var slot:menu.slots){int sx=x+slot.x,sy=y+slot.y;g.fill(sx-1,sy-1,sx+17,sy+17,0xff526060);g.fill(sx,sy,sx+16,sy+16,0xff111d24);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,titleLabelX,titleLabelY,0xffb8d9d6,false);g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xffc8b58b,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){super.render(g,mx,my,dt);renderTooltip(g,mx,my);}
}
