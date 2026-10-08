package pro.erez.interstice.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import pro.erez.interstice.agriculture.RetortMenu;

/** Native slot UI, drawn directly; no copied or generated background bitmap. */
public final class RetortScreen extends AbstractContainerScreen<RetortMenu> {
    private int recipeIndex;
    private net.minecraft.client.gui.components.Button recipeButton;
    public RetortScreen(RetortMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=218;imageHeight=216;inventoryLabelX=28;inventoryLabelY=120;titleLabelX=8;titleLabelY=7;}
    @Override protected void init(){super.init();recipeButton=addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("menu.interstice.retort.recipes"),button->recipeIndex++)
            .bounds(leftPos+88,topPos+84,110,18).build());}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        int x=leftPos,y=topPos;g.fill(x,y,x+imageWidth,y+imageHeight,0xffb2a6a4);g.fill(x+3,y+3,x+imageWidth-3,y+imageHeight-3,0xff352e3b);
        for(var slot:menu.slots){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff191720);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff645564);}
        int total=menu.data.get(1),progress=total==0?0:26*menu.data.get(0)/total;
        g.fill(x+93,y+54,x+121,y+66,0xff211c26);g.fill(x+94,y+55,x+94+progress,y+65,0xff72a99c);
        g.fill(x+71,y+82,x+75,y+98,0xff211c26);int fuel=16*menu.data.get(2)/3200;g.fill(x+71,y+98-fuel,x+75,y+98,0xffb6845d);
        g.drawString(font,Component.translatable("menu.interstice.retort.inputs"),x+30,y+26,0xffddd0c5,false);
        g.drawString(font,Component.translatable("menu.interstice.retort.preview"),x+129,y+26,0xffddd0c5,false);
        g.drawString(font,Component.translatable("menu.interstice.retort.outputs"),x+129,y+63,0xffddd0c5,false);
        g.drawString(font,Component.translatable("menu.interstice.retort.status."+menu.data.get(3)),x+9,y+107,0xffd3c4bf,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);
        if(recipeButton!=null&&recipeButton.isHovered()&&minecraft.level!=null){
            var recipes=minecraft.level.getRecipeManager().getAllRecipesFor(pro.erez.interstice.agriculture.RealmAgriculture.RETORT_RECIPE_TYPE.get()).stream().sorted(java.util.Comparator.comparing(r->r.id().toString())).toList();
            if(!recipes.isEmpty()){var recipe=recipes.get(Math.floorMod(recipeIndex,recipes.size())).value();var lines=new java.util.ArrayList<Component>();
                recipe.outputs().forEach(s->lines.add(Component.literal(s.getCount()+" × ").append(s.getHoverName())));
                lines.add(Component.translatable("menu.interstice.retort.inputs"));
                for(var input:recipe.inputs()){var choices=input.ingredient().getItems();if(choices.length>0)lines.add(Component.literal(input.count()+" × ").append(choices[0].getHoverName()));}
                lines.add(Component.translatable("menu.interstice.retort.time",recipe.ticks()/20));
                lines.add(Component.translatable("menu.interstice.retort.fuel"));g.renderComponentTooltip(font,lines,x,y);
            }
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){g.drawString(font,title,titleLabelX,titleLabelY,0xffefdfd2,false);g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xffd3c4bf,false);}
}
