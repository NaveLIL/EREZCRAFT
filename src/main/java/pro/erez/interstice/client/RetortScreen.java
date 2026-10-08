package pro.erez.interstice.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import pro.erez.interstice.agriculture.*;

/** Searchable recipe library around the unchanged native machine slots, with a compact-screen overlay. */
public final class RetortScreen extends AbstractContainerScreen<RetortMenu> {
    private static final int TEXT=0xffe3ebed,MUTED=0xffb3c2c9,ACCENT=0xff87c5b1,WARN=0xffe2b775;
    private EditBox search;
    private Button autoButton,libraryButton,pinButton,fillButton;
    private final List<RecipeRow> rows=new ArrayList<>();
    private ResourceLocation browsed;
    private int scroll,lastToken=-1;
    private boolean compact,libraryOpen;
    private String query="";

    public RetortScreen(RetortMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);imageHeight=232;inventoryLabelX=28;inventoryLabelY=120;titleLabelX=9;titleLabelY=8;
    }
    @Override protected void init(){
        compact=width<454;imageWidth=compact?218:454;super.init();rows.clear();
        autoButton=addRenderableWidget(Button.builder(Component.translatable("menu.interstice.retort.auto"),b->action(RetortMenu.BUTTON_AUTO)).bounds(leftPos+91,topPos+94,57,12).build());
        autoButton.setTooltip(Tooltip.create(Component.translatable("menu.interstice.retort.auto_hint")));
        libraryButton=addRenderableWidget(Button.builder(Component.translatable("menu.interstice.retort.library"),b->{libraryOpen=!libraryOpen;updateWidgets();})
                .bounds(leftPos+151,topPos+94,57,12).build());
        int side=sideX();
        search=addRenderableWidget(new EditBox(font,leftPos+side+8,topPos+22,202,18,Component.translatable("menu.interstice.retort.search")));
        search.setMaxLength(64);search.setHint(Component.translatable("menu.interstice.retort.search"));search.setValue(query);
        search.setResponder(value->{query=value;scroll=0;updateWidgets();});
        for(int i=0;i<4;i++){
            final int row=i;
            rows.add(addRenderableWidget(new RecipeRow(Button.builder(Component.empty(),b->{var entries=filtered();int at=scroll+row;if(at<entries.size())browsed=entries.get(at).id();updateWidgets();})
                    .bounds(leftPos+side+8,topPos+46+i*18,202,18),i)));
        }
        pinButton=addRenderableWidget(Button.builder(Component.translatable("menu.interstice.retort.pin"),b->{var r=selected();if(r!=null)action(RetortMenu.recipeToken(r.id()));})
                .bounds(leftPos+side+8,topPos+212,98,18).build());
        fillButton=addRenderableWidget(Button.builder(Component.translatable("menu.interstice.retort.fill_one"),b->{var r=selected();if(r!=null)action(RetortMenu.fillButton(r.id()));})
                .bounds(leftPos+side+112,topPos+212,98,18).build());
        pinButton.setTooltip(Tooltip.create(Component.translatable("menu.interstice.retort.pin_hint")));
        updateWidgets();
    }
    private int sideX(){return compact?0:224;}
    private boolean showingLibrary(){return !compact||libraryOpen;}
    private void action(int button){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button);}
    private List<RecipeHolder<RetortRecipe>> recipes(){return minecraft==null||minecraft.level==null?List.of():RetortMenu.recipes(minecraft.level);}
    private List<RecipeHolder<RetortRecipe>> filtered(){
        String text=query.strip().toLowerCase(Locale.ROOT);return recipes().stream().filter(r->{
            if(text.isEmpty())return true;
            if(r.id().toString().toLowerCase(Locale.ROOT).contains(text))return true;
            if(r.value().outputs().stream().anyMatch(s->s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(text)))return true;
            return r.value().inputs().stream().anyMatch(in->Arrays.stream(in.ingredient().getItems()).anyMatch(s->s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(text)));
        }).toList();
    }
    private RecipeHolder<RetortRecipe> selected(){
        var all=recipes();if(all.isEmpty())return null;
        if(browsed!=null)for(var r:all)if(r.id().equals(browsed))return r;
        for(var r:all)if(RetortMenu.recipeToken(r.id())==menu.data.get(4)){browsed=r.id();return r;}
        for(var r:all)if(r.value().matches(menu.currentInputs(),minecraft.level)){browsed=r.id();return r;}
        browsed=all.getFirst().id();return all.getFirst();
    }
    private List<ItemStack> playerItems(){return java.util.stream.IntStream.range(0,36).mapToObj(i->minecraft.player.getInventory().items.get(i).copy()).toList();}
    private int fillReason(){
        if(menu.batchActive())return RetortMenu.FEEDBACK_BUSY;var selected=selected();
        if(selected==null)return RetortMenu.FEEDBACK_INVALID;
        if(!menu.outputsFit(selected.value()))return RetortMenu.FEEDBACK_OUTPUT_SPACE;
        return RetortMenu.fillPlan(selected.value(),menu.currentInputs().items(),playerItems()).reason();
    }
    private int available(RetortRecipe.Input input){
        int count=0;for(int i=0;i<6;i++)if(input.ingredient().test(menu.getSlot(i).getItem()))count+=menu.getSlot(i).getItem().getCount();
        for(var stack:playerItems())if(input.ingredient().test(stack))count+=stack.getCount();return count;
    }
    private void updateWidgets(){
        if(search==null||minecraft==null||minecraft.player==null)return;
        int token=menu.data.get(4);if(token!=lastToken){lastToken=token;for(var r:recipes())if(RetortMenu.recipeToken(r.id())==token)browsed=r.id();}
        boolean show=showingLibrary();search.visible=show;pinButton.visible=fillButton.visible=show;
        libraryButton.visible=compact;libraryButton.setMessage(Component.translatable(libraryOpen?"menu.interstice.retort.back":"menu.interstice.retort.library"));
        libraryButton.setY(topPos+(libraryOpen?4:94));
        autoButton.visible=!compact||!libraryOpen;autoButton.active=!menu.batchActive()&&token!=0;
        autoButton.setMessage(Component.translatable(token==0?"menu.interstice.retort.auto_active":"menu.interstice.retort.auto"));
        var entries=filtered();scroll=Math.max(0,Math.min(scroll,Math.max(0,entries.size()-4)));
        for(int i=0;i<rows.size();i++){var row=rows.get(i);row.visible=show&&scroll+i<entries.size();row.active=row.visible;row.entry=row.visible?entries.get(scroll+i):null;if(row.entry!=null){row.setMessage(row.entry.value().outputs().getFirst().getHoverName());row.setTooltip(Tooltip.create(row.entry.value().outputs().getFirst().getHoverName()));}}
        var r=selected();boolean valid=r!=null&&RetortMenu.recipeForToken(minecraft.level,RetortMenu.recipeToken(r.id()))!=null;
        pinButton.active=valid&&!menu.batchActive()&&RetortMenu.recipeToken(r.id())!=token;
        pinButton.setMessage(Component.translatable(r!=null&&RetortMenu.recipeToken(r.id())==token?"menu.interstice.retort.pinned":"menu.interstice.retort.pin"));
        int reason=fillReason();fillButton.active=valid&&reason==RetortMenu.FEEDBACK_FILLED;
        fillButton.setTooltip(Tooltip.create(Component.translatable(reason==RetortMenu.FEEDBACK_FILLED?"menu.interstice.retort.fill_hint":"menu.interstice.retort.feedback."+reason)));
    }
    private void panel(GuiGraphics g,int x,int y,int w,int h){GuiMaterials.panel(g,x,y,w,h,false);}
    private String clipped(Component text,int width){String value=text.getString();return font.width(value)<=width?value:font.plainSubstrByWidth(value,Math.max(0,width-font.width("…")))+"…";}
    private Component statusText(){
        int status=menu.data.get(3);
        if(menu.batchActive()&&status==2)return Component.translatable("menu.interstice.retort.pending_fuel");
        if(menu.batchActive()&&status==3)return Component.translatable("menu.interstice.retort.pending_output");
        return Component.translatable("menu.interstice.retort.status."+status);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        int x=leftPos,y=topPos;panel(g,x,y,imageWidth,imageHeight);
        if(!compact||!libraryOpen){
            GuiMaterials.inset(g,x+7,y+23,204,82,false);
            for(var slot:menu.slots)GuiMaterials.slot(g,x+slot.x,y+slot.y,slot.index>=10&&slot.index<13?2:slot.index>=7&&slot.index<10?1:0);
            g.drawString(font,Component.translatable("menu.interstice.retort.inputs"),x+30,y+26,MUTED,false);
            g.drawString(font,Component.translatable("menu.interstice.retort.preview"),x+129,y+26,MUTED,false);
            g.drawString(font,Component.translatable("menu.interstice.retort.outputs"),x+129,y+63,MUTED,false);
            int total=menu.data.get(1),progress=total==0?0:Math.min(26,26*menu.data.get(0)/total);
            g.fill(x+93,y+52,x+121,y+64,0xff15131c);g.fill(x+94,y+53,x+94+progress,y+63,ACCENT);
            if(total>0)g.drawString(font,Math.min(100,100*menu.data.get(0)/total)+"%",x+95,y+67,TEXT,false);
            int fuel=16*Math.max(0,Math.min(3200,menu.data.get(2)))/3200;g.fill(x+75,y+82,x+80,y+98,0xff15131c);g.fill(x+75,y+98-fuel,x+80,y+98,0xffd79968);
            g.drawString(font,clipped(statusText(),199),x+9,y+107,menu.data.get(3)>1?WARN:ACCENT,false);
            if(menu.data.get(5)>0)g.drawString(font,clipped(Component.translatable("menu.interstice.retort.feedback."+menu.data.get(5)),198),x+9,y+215,MUTED,false);
        }
        if(showingLibrary()){
            int side=x+sideX();if(!compact)g.fill(side-4,y+5,side-3,y+imageHeight-5,0xff5e7a85);
            g.drawString(font,Component.translatable("menu.interstice.retort.library_title"),side+8,y+8,TEXT,false);
            if(filtered().isEmpty())g.drawString(font,Component.translatable("menu.interstice.retort.no_results"),side+8,y+56,MUTED,false);
            int count=filtered().size();if(count>4){g.fill(side+213,y+46,side+215,y+118,0xff15131c);int thumb=Math.max(12,72*4/count),at=scroll*(72-thumb)/(count-4);g.fill(side+213,y+46+at,side+215,y+46+at+thumb,ACCENT);}
            var r=selected();if(r!=null){
                g.drawString(font,Component.translatable("menu.interstice.retort.ingredients_counted"),side+8,y+131,MUTED,false);
                for(int i=0;i<r.value().inputs().size();i++){
                    var input=r.value().inputs().get(i);var choices=input.ingredient().getItems();int ix=side+8+(i%3)*69,iy=y+147+(i/3)*26;
                    GuiMaterials.inset(g,ix-1,iy-1,67,20,false);
                    if(choices.length>0)g.renderItem(choices[(int)(minecraft.level.getGameTime()/30%choices.length)],ix,iy);
                    int have=available(input);g.drawString(font,Math.min(have,999)+"/"+input.count(),ix+20,iy+5,have>=input.count()?ACCENT:WARN,false);
                }
                g.drawString(font,Component.translatable("menu.interstice.retort.batch_time",r.value().ticks()/20),side+8,y+194,TEXT,false);
            }
        }
    }
    @Override protected void renderSlot(GuiGraphics g,Slot slot){if(!compact||!libraryOpen)super.renderSlot(g,slot);}
    @Override protected void renderSlotHighlight(GuiGraphics g,Slot slot,int x,int y,float partial){if(!compact||!libraryOpen)super.renderSlotHighlight(g,slot,x,y,partial);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){if(!compact||!libraryOpen){g.drawString(font,clipped(title,198),titleLabelX,titleLabelY,TEXT,false);g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,MUTED,false);}}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        updateWidgets();super.render(g,x,y,partial);
        if(!compact||!libraryOpen)renderTooltip(g,x,y);
        if(isHovering(7,105,204,13,x,y)&&(!compact||!libraryOpen))g.renderComponentTooltip(font,List.of(statusText(),Component.translatable(menu.batchActive()?"menu.interstice.retort.saved_batch":"menu.interstice.retort.auto_hint")),x,y);
        if(isHovering(91,50,31,28,x,y)&&(!compact||!libraryOpen)){
            int total=menu.data.get(1),remaining=Math.max(0,total-menu.data.get(0));
            g.renderComponentTooltip(font,List.of(Component.translatable("menu.interstice.retort.progress_hint",menu.data.get(0),total),Component.translatable("menu.interstice.retort.remaining",(remaining+19)/20),Component.translatable("menu.interstice.retort.saved_batch")),x,y);
        }
        if(isHovering(72,80,10,20,x,y)&&(!compact||!libraryOpen))g.renderComponentTooltip(font,List.of(Component.translatable("menu.interstice.retort.heat_hint",menu.data.get(2),menu.data.get(2)/20),Component.translatable("menu.interstice.retort.fuel_separate")),x,y);
        if(showingLibrary()){
            var r=selected();if(r!=null){
                for(int i=0;i<r.value().inputs().size();i++)if(isHovering(sideX()+7+(i%3)*69,146+(i/3)*26,67,20,x,y)){
                    var input=r.value().inputs().get(i);var lines=new ArrayList<Component>();var choices=input.ingredient().getItems();
                    for(int j=0;j<Math.min(choices.length,6);j++)lines.add(choices[j].getHoverName());
                    lines.add(Component.translatable("menu.interstice.retort.have_need",available(input),input.count()));lines.add(Component.translatable("menu.interstice.retort.count_scope"));g.renderComponentTooltip(font,lines,x,y);
                }
                if(isHovering(sideX()+8,192,202,15,x,y))g.renderComponentTooltip(font,List.of(Component.translatable("menu.interstice.retort.batch_heat",r.value().ticks()),Component.translatable("menu.interstice.retort.heat_hint",menu.data.get(2),menu.data.get(2)/20),Component.translatable("menu.interstice.retort.fuel_separate")),x,y);
            }
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(showingLibrary()&&isHovering(sideX()+6,43,210,80,x,y)){scroll+=vertical<0?1:vertical>0?-1:0;updateWidgets();return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(compact&&libraryOpen){search.setFocused(search.isMouseOver(x,y));for(var widget:List.of(search,pinButton,fillButton,libraryButton))if(widget.mouseClicked(x,y,button)){setFocused(widget);return true;}for(var row:rows)if(row.mouseClicked(x,y,button)){setFocused(row);return true;}return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button){return compact&&libraryOpen||super.mouseReleased(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return compact&&libraryOpen||super.mouseDragged(x,y,button,dx,dy);}
    @Override protected void slotClicked(Slot slot,int index,int button,ClickType type){if(!compact||!libraryOpen)super.slotClicked(slot,index,button,type);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(search!=null&&search.visible&&search.isFocused()){if(key==256){search.setFocused(false);return true;}return search.keyPressed(key,scan,modifiers);}
        if(compact&&libraryOpen&&key==256){libraryOpen=false;updateWidgets();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char code,int modifiers){if(search!=null&&search.visible&&search.isFocused())return search.charTyped(code,modifiers);return super.charTyped(code,modifiers);}
    private final class RecipeRow extends Button {
        private final int row;
        private RecipeHolder<RetortRecipe> entry;
        RecipeRow(Button.Builder builder,int row){super(builder);this.row=row;}
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial){
            if(entry==null)return;boolean chosen=entry.id().equals(browsed),pinned=RetortMenu.recipeToken(entry.id())==menu.data.get(4);int x=getX(),y=getY();
            GuiMaterials.inset(g,x,y,getWidth(),getHeight(),false);g.fill(x,y,x+getWidth(),y+getHeight(),chosen?0x804b727c:isHoveredOrFocused()?0x60415e68:0x30212d34);
            if(chosen)g.fill(x,y,x+2,y+getHeight(),ACCENT);
            var output=entry.value().outputs().getFirst();g.renderItem(output,x+4,y+1);
            Component name=Component.literal(output.getCount()+" × ").append(output.getHoverName());g.drawString(font,clipped(name,getWidth()-31),x+24,y+5,pinned?ACCENT:TEXT,false);
            if(pinned)g.fill(x+getWidth()-5,y+6,x+getWidth()-2,y+12,ACCENT);
        }
    }
}
