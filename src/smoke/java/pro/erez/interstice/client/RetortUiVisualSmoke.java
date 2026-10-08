package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.*;
import pro.erez.interstice.minerals.MineralEcology;

/** Native GUI/browser/button packets in a disposable world; no production shortcut or owner profile. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class RetortUiVisualSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("interstice.retortUiSmoke");
    private static boolean started,finished,quitting,passed;
    private static int stage,ticks,invalidToken;
    private static String reason,firstCompactRow;
    private static long deadline;
    private static BlockPos machine;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static final JsonObject data=new JsonObject();
    private static final ResourceLocation GRAIN=ResourceLocation.fromNamespaceAndPath(Interstice.ID,"retort_grain_separation");

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){var s=settling;if(ENABLED&&s!=null)s.tick(event.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||finished)return;var mc=Minecraft.getInstance();
        try{
            if(quitting){shutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen){
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(8);mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);
                var settings=new LevelSettings("Disposable retort interface",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("native-retort-ui-check",settings,new WorldOptions(20261006L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;require(System.nanoTime()<deadline,"Retort UI deadline at stage "+stage);
            if(mc.player==null||mc.level==null||mc.getConnection()==null)return;
            if(mc.screen instanceof RetortScreen){
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),8,8);
                mc.screen.setFocused(null);
            }
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==0){work=server.submit(()->{prepare(server.getPlayerList().getPlayer(uuid));return true;});settling=new NativeChunkSettler.Session("retort_ui_before_actions",2000);stage=1;return;}
            if(stage==1&&done()&&settling.ready()&&++ticks>=20){data.add("settle_before_actions",settling.report());settling=null;window(mc,1280,720,2);open(mc);stage=2;ticks=0;}
            else if(stage==2&&mc.screen instanceof RetortScreen&&++ticks>=20){
                require(mc.getWindow().getGuiScaledWidth()>=454&&screen(mc).getXSize()==454,"Wide layout did not become visible");
                controlsClearSlots(mc);
                data.addProperty("full_scaled_width",mc.getWindow().getGuiScaledWidth());data.addProperty("client_custom_recipe_count",RetortMenu.recipes(mc.level).size());
                require(RetortMenu.recipes(mc.level).size()>=7,"Synchronized native retort library is incomplete");shot(mc,"retort-ui-browser-full.png");typeSearch(mc,"grain");stage=3;ticks=0;
            }else if(stage==3&&++ticks>=6){
                var rows=visibleRows(mc);require(rows.size()==1&&rows.getFirst().getMessage().getString().equals(new ItemStack(RealmAgriculture.FLOUR.get()).getHoverName().getString()),"Actual search did not find the grain operation");
                press(mc,rows.getFirst());data.addProperty("actual_search_and_recipe_browse",true);shot(mc,"retort-ui-search-full.png");search(mc).setValue("");search(mc).setFocused(false);window(mc,960,720,3);stage=4;ticks=0;
            }else if(stage==4&&++ticks>=8){
                require(mc.getWindow().getGuiScaledWidth()<454&&screen(mc).getXSize()==218,"Compact layout was not exercised");
                controlsClearSlots(mc);
                press(mc,button(mc,"menu.interstice.retort.library"));stage=5;ticks=0;
            }else if(stage==5&&++ticks>=6){
                require(search(mc).visible&&visibleRows(mc).size()==4,"Compact library overlay did not expose recipe list/search");
                data.addProperty("compact_scaled_width",mc.getWindow().getGuiScaledWidth());firstCompactRow=visibleRows(mc).getFirst().getMessage().getString();
                var ui=screen(mc);ui.mouseScrolled(ui.getGuiLeft()+30,ui.getGuiTop()+65,0,-1);stage=6;ticks=0;
            }else if(stage==6&&++ticks>=5){
                require(!firstCompactRow.equals(visibleRows(mc).getFirst().getMessage().getString()),"Real wheel event did not browse additional recipes");
                data.addProperty("actual_compact_overlay_and_scrolling",true);shot(mc,"retort-ui-compact-library.png");typeSearch(mc,"grain");stage=7;ticks=0;
            }else if(stage==7&&++ticks>=5){
                require(visibleRows(mc).size()==1,"Compact search is not operable");press(mc,visibleRows(mc).getFirst());
                var fill=button(mc,"menu.interstice.retort.fill_one");require(fill.active,"Valid one-batch fill is disabled");press(mc,fill);stage=8;ticks=0;
            }else if(stage==8&&++ticks>=12){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var tile=tile(p);
                    require(tile.getItem(0).is(RealmAgriculture.GRAIN.get())&&tile.getItem(0).getCount()==2&&tile.getItem(0).getHoverName().getString().equals("native UI grain")
                            &&p.getInventory().countItem(RealmAgriculture.GRAIN.get())==2&&tile.getItem(6).isEmpty()&&GRAIN.equals(tile.selectedRecipe())&&!tile.hasBatch(),"Real one-batch button lost components, duplicated items or auto-fuelled");
                    invalidToken=256;while(RetortMenu.recipeForToken(p.level(),invalidToken)!=null)invalidToken++;data.addProperty("native_one_batch_fill_preserves_components_and_counts",true);return true;
                });stage=9;ticks=0;
            }else if(stage==9&&done()){
                mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,RetortMenu.FILL_BASE+invalidToken);stage=10;ticks=0;
            }else if(stage==10&&++ticks>=10){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var tile=tile(p);
                    require(((RetortMenu)p.containerMenu).data.get(5)==RetortMenu.FEEDBACK_INVALID&&GRAIN.equals(tile.selectedRecipe())&&tile.getItem(0).getCount()==2&&p.getInventory().countItem(RealmAgriculture.GRAIN.get())==2,"Invalid native recipe token was accepted");
                    data.addProperty("invalid_native_token_rejected",true);return true;
                });stage=11;ticks=0;
            }else if(stage==11&&done()){
                window(mc,1280,720,2);stage=12;ticks=0;
            }else if(stage==12&&++ticks>=8){
                shot(mc,"retort-ui-waiting-fuel.png");mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,45,0,ClickType.QUICK_MOVE,mc.player);stage=13;ticks=0;
            }else if(stage==13&&++ticks>=15){
                require(screen(mc).getMenu().batchActive()&&!button(mc,"menu.interstice.retort.fill_one").active&&!button(mc,"menu.interstice.retort.pinned").active
                        &&!button(mc,"menu.interstice.retort.auto").active,"Running party still exposes active pin/fill/AUTO controls");
                shot(mc,"retort-ui-running-locked.png");mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,RetortMenu.BUTTON_AUTO);
                mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,RetortMenu.fillButton(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"retort_phosphorite_paste")));stage=14;ticks=0;
            }else if(stage==14&&++ticks>=10){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var tile=tile(p);
                    require(tile.hasBatch()&&tile.data.get(0)>0&&tile.data.get(0)<200&&GRAIN.equals(tile.selectedRecipe())&&tile.preview().getFirst().is(RealmAgriculture.FLOUR.get()),"Native forged controls changed a running reserved party");
                    data.addProperty("running_native_controls_disabled_and_server_locked",true);return true;
                });stage=15;ticks=0;
            }else if(stage==15&&done())finish(mc,true,"Native full/compact library, search, scrolling, counted component-safe transfer, invalid-token rejection and active-batch controls passed");
        }catch(Throwable failure){failure.printStackTrace();finish(mc,false,failure.toString());}
    }
    private static RetortBlockEntity tile(ServerPlayer p){return (RetortBlockEntity)p.serverLevel().getBlockEntity(machine);}
    private static void prepare(ServerPlayer p){
        var level=p.serverLevel();var base=p.blockPosition().below();machine=base.offset(2,1,0);
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=0;y<=4;y++)level.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        level.setBlock(machine,RealmAgriculture.RETORT.get().defaultBlockState(),3);p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
        var grain=new ItemStack(RealmAgriculture.GRAIN.get(),4);grain.set(DataComponents.CUSTOM_NAME,Component.literal("native UI grain"));p.getInventory().items.set(0,grain);
        p.getInventory().items.set(5,new ItemStack(MineralEcology.UMBRAL_COAL.get()));p.getInventory().selected=8;p.getInventory().setChanged();p.containerMenu.broadcastChanges();
        p.teleportTo(level,base.getX()+.5,base.getY()+1,base.getZ()-1.5,Set.of(),0,0);
        data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("scope","Disposable prepared platform and Survival inventory; genuine GUI widget events and inventory/button packets, not a resource-gathering Survival journey");
    }
    private static void window(Minecraft mc,int width,int height,int scale){mc.getWindow().setWindowed(width,height);mc.options.guiScale().set(scale);mc.resizeDisplay();}
    private static RetortScreen screen(Minecraft mc){require(mc.screen instanceof RetortScreen,"Actual native retort screen is absent");return (RetortScreen)mc.screen;}
    private static EditBox search(Minecraft mc){return screen(mc).children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).findFirst().orElseThrow();}
    private static List<Button> visibleRows(Minecraft mc){return screen(mc).children().stream().filter(w->w instanceof Button&&w.getClass().getSimpleName().equals("RecipeRow")).map(w->(Button)w).filter(b->b.visible).toList();}
    private static Button button(Minecraft mc,String key){String name=Component.translatable(key).getString();return screen(mc).children().stream().filter(w->w instanceof Button).map(w->(Button)w).filter(b->b.visible&&b.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new IllegalStateException("Missing native button "+key));}
    private static void press(Minecraft mc,Button button){screen(mc).mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0);screen(mc).mouseReleased(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0);}
    private static void controlsClearSlots(Minecraft mc){
        var ui=screen(mc);
        for(var child:ui.children())if(child instanceof Button button&&button.visible)for(var slot:ui.getMenu().slots){
            int x=ui.getGuiLeft()+slot.x-1,y=ui.getGuiTop()+slot.y-1;
            require(button.getX()+button.getWidth()<=x||button.getX()>=x+18||button.getY()+button.getHeight()<=y||button.getY()>=y+18,
                    "Visible native button overlaps real inventory slot "+slot.index+": "+button.getMessage().getString());
        }
        data.addProperty("native_buttons_clear_actual_inventory_slots",true);
    }
    private static void typeSearch(Minecraft mc,String text){var input=search(mc);input.setValue("");screen(mc).mouseClicked(input.getX()+10,input.getY()+8,0);for(char c:text.toCharArray())screen(mc).charTyped(c,0);}
    private static void open(Minecraft mc){mc.player.getInventory().selected=8;var d=Vec3.atCenterOf(machine).subtract(mc.player.getEyePosition());mc.player.setYRot((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(machine),Direction.UP,machine,false));}
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("RETORT_UI_SCREENSHOT "+name));}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void finish(Minecraft mc,boolean success,String why){if(quitting)return;passed=success;reason=why;quitting=true;if(mc.player!=null)mc.player.closeContainer();settling=new NativeChunkSettler.Session("retort_ui_terminal",160);}
    private static void shutdown(Minecraft mc)throws Exception{
        if(mc.getSingleplayerServer()==null){write(mc,false,"Server unavailable during native UI shutdown");mc.stop();finished=true;return;}
        if(settling.failed()){passed=false;reason=settling.failure();}if(!settling.ready())return;
        data.add("settle_terminal",settling.report());data.addProperty("clean_generation_before_mc_stop",true);write(mc,passed,reason);mc.stop();finished=true;
    }
    private static void write(Minecraft mc,boolean success,String why)throws Exception{data.addProperty("passed",success);data.addProperty("stage",stage);data.addProperty("reason",why);Files.writeString(mc.gameDirectory.toPath().resolve("retort-ui-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));System.out.println("RETORT_UI_VALIDATION "+data);}
}
