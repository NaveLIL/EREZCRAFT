package pro.erez.interstice.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import pro.erez.interstice.Interstice;

/** Material panels reuse whole licensed tiles; PNG pattern and alpha are never repainted. */
public final class GuiMaterials {
    public static final ResourceLocation METAL=ResourceLocation.fromNamespaceAndPath(Interstice.ID,"textures/gui/retort_metal.png");
    public static final ResourceLocation CLOTH=ResourceLocation.fromNamespaceAndPath(Interstice.ID,"textures/gui/backpack_cloth.png");
    private GuiMaterials(){}
    public static void tiles(GuiGraphics g,ResourceLocation tile,int x,int y,int width,int height){
        g.enableScissor(x,y,x+width,y+height);for(int yy=y;yy<y+height;yy+=16)for(int xx=x;xx<x+width;xx+=16)g.blit(tile,xx,yy,0,0,16,16,16,16);g.disableScissor();
    }
    public static void panel(GuiGraphics g,int x,int y,int width,int height,boolean backpack){panel(g,x,y,width,height,backpack,0);}
    public static void panel(GuiGraphics g,int x,int y,int width,int height,boolean backpack,int cornerAccent){
        g.fill(x-1,y-1,x+width+1,y+height+1,0xff13191b);tiles(g,METAL,x,y,width,height);
        if(backpack)tiles(g,CLOTH,x+5,y+5,width-10,height-10);
        g.fill(x+5,y+5,x+width-5,y+height-5,backpack?0xc01d2522:0xde1b2529);
        g.fill(x,y,x+width,y+1,0xffb4c2c3);g.fill(x,y,x+1,y+height,0xff8c9da0);g.fill(x+width-1,y+1,x+width,y+height,0xff36474d);g.fill(x+1,y+height-1,x+width,y+height,0xff25383e);
        int rivet=cornerAccent!=0?cornerAccent:0xffc3d3d0;
        for(int xx:new int[]{x+2,x+width-4})for(int yy:new int[]{y+2,y+height-4}){g.fill(xx,yy,xx+2,yy+2,0xff18292e);g.fill(xx,yy,xx+1,yy+1,rivet);}
    }
    public static void inset(GuiGraphics g,int x,int y,int width,int height,boolean cloth){tiles(g,cloth?CLOTH:METAL,x,y,width,height);g.fill(x,y,x+width,y+height,cloth?0xd42b3029:0xdf233238);}
    public static void slot(GuiGraphics g,int x,int y,int role){
        int edge=role==1?0xffc2a86f:role==2?0xff7bbaa6:role==3?0xffadbdc1:0xff768b94;
        g.fill(x-1,y-1,x+17,y+17,0xff0d181d);g.fill(x,y+16,x+17,y+17,edge);g.fill(x+16,y,x+17,y+17,edge);
        tiles(g,METAL,x,y,16,16);g.fill(x,y,x+16,y+16,role==1?0xde343026:0xe3232e34);
        if(role==2)g.fill(x,y,x+16,y+1,0xff628c80);
    }
}
