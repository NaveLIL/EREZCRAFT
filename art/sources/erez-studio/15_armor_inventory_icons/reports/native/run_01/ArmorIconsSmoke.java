package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.*;

/** An actual survival inventory plus a diagnostic catalog drawn with the native ItemRenderer. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class ArmorIconsSmoke {
    private static final boolean ACTIVE=System.getProperty("interstice.progressionSmoke","").equals("create");
    private static final String[] TIERS={"riftsilver","riftsteel","aegis","kotomyaf"},PARTS={"helmet","chestplate","leggings","boots"};
    private static final JsonObject REPORT=new JsonObject();private static final JsonArray SHOTS=new JsonArray();
    private static CompletableFuture<?> job;private static volatile NativeChunkSettler.Session settling;
    private static boolean started,finished,closing;private static int phase,ticks;private static long deadline;
    private ArmorIconsSmoke(){}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static Item item(int row,int col){String name=TIERS[row]+"_"+PARTS[col];return row==2?AegisArmor.item(name):row==3?KotomyafArmor.item(name):RealmArmaments.item(name);}
    private static int at(int row,int col){return (row==3?0:9+row*9)+2+col;}
    private static void inspect(Minecraft mc){
        var items=new JsonArray();
        for(int row=0;row<4;row++)for(int col=0;col<4;col++){
            var stack=mc.player.getInventory().getItem(at(row,col));require(stack.is(item(row,col)),"Inventory synchronization: "+TIERS[row]+" "+PARTS[col]);
            var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);var quads=model.getQuads(null,null,RandomSource.create(0));
            require(model.isGui3d()&&!model.isCustomRenderer(),"Expected normally baked 3D item");
            String name=TIERS[row]+"_"+PARTS[col];JsonObject source;
            try(var reader=mc.getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"models/item/"+name+".json"))){source=JsonParser.parseReader(reader).getAsJsonObject();}catch(Exception e){throw new IllegalStateException(e);}
            require(quads.size()==source.getAsJsonArray("quads").size()&&quads.size()>24,"Baked quad count: "+name);
            for(var quad:quads)require(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()),"Missing texture: "+name);
            var palette=source.getAsJsonArray("palette");
            for(int tint=0;tint<7;tint++){
                int expected=0xff000000|Integer.parseInt(palette.get(tint).getAsString().substring(1),16);
                var dye=stack.get(DataComponents.DYED_COLOR);if(tint==0&&dye!=null)expected=0xff000000|dye.rgb();
                require(mc.getItemColors().getColor(stack,tint)==expected,"Item palette must match wearable: "+name+" tint="+tint);
            }
            var record=new JsonObject();record.addProperty("item",name);record.addProperty("quads",quads.size());record.addProperty("cube_count",source.getAsJsonObject("erez_source").get("cubes").getAsInt());record.addProperty("baked_model",model.getClass().getName());items.add(record);
        }
        REPORT.add("items",items);REPORT.addProperty("all_16_native_models_checked",true);
    }
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->System.out.println("ARMOR_ICONS "+name));SHOTS.add(name+".png");}
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("armor-icons.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void finish(Minecraft mc,boolean passed,String reason){
        if(closing||finished)return;REPORT.addProperty("passed",passed);REPORT.addProperty("reason",reason);REPORT.addProperty("phase",phase);REPORT.addProperty("prepared_fixture_not_survival",true);REPORT.addProperty("owner_profile_used",false);REPORT.add("screenshots",SHOTS);
        if(!passed){write(mc);finished=true;mc.stop();return;}
        closing=true;mc.setScreen(null);settling=new NativeChunkSettler.Session("armor_icons_shutdown",2000);
    }
    /** Suppress hover only; the screen, slots, font, player preview and items remain vanilla. */
    private static final class InventoryFixture extends InventoryScreen {
        InventoryFixture(net.minecraft.world.entity.player.Player p){super(p);}
        @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,-10,-10,partial);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class Catalog extends Screen {
        private final boolean variants;
        Catalog(boolean variants){super(Component.literal("EREZ Armor Icons"));this.variants=variants;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xff101720);
            g.drawString(font,Component.literal(variants?"EREZ — окраска и зачарования":"EREZ — миниатюры нашей брони"),16,12,0xffe6edf3,false);
            String[] labels={"Шлем","Нагрудник","Поножи","Ботинки"};
            for(int c=0;c<4;c++)g.drawString(font,labels[c],128+c*75,34,0xff98b6cb,false);
            String[] names={"Серебро","Рифтсталь","Маген","Котомяф"};
            for(int r=0;r<4;r++){
                int y=54+r*46;g.drawString(font,names[r],18,y+12,r==2?0xff70d7ec:r==3?0xffefdfc2:0xffe6edf3,false);
                for(int c=0;c<4;c++){
                    int x=128+c*75;g.fill(x-3,y-3,x+35,y+35,0xff25323e);
                    g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(2,2,2);g.renderItem(minecraft.player.getInventory().getItem(at(r,c)),0,0);g.pose().popPose();
                }
            }
            g.drawString(font,Component.literal("16 предметов • игровые модели • без замены внешнего вида брони"),16,height-18,0xff93a4b5,false);
        }
    }
    @SubscribeEvent public static void server(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(ACTIVE&&settling!=null)settling.tick(event.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){if(settling.failed())throw new IllegalStateException(settling.failure());if(settling.ready()){REPORT.add("settled",settling.report());write(mc);finished=true;mc.stop();}return;}
            if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned fixture profile only");
                require(ModList.get().isLoaded("distanthorizons")&&ModList.get().isLoaded("create")&&ModList.get().isLoaded("sable"),"Full pack and DH required");
                started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(4);mc.resizeDisplay();
                mc.createWorldOpenFlows().createFreshLevel("armor-icons",new LevelSettings("EREZ inventory icon QA",GameType.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261028L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started)return;require(System.nanoTime()<deadline,"Native icon check deadline, phase="+phase);
            if(SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            var server=mc.getSingleplayerServer();
            if(phase==0){var uuid=mc.player.getUUID();job=server.submit(()->{
                var p=server.getPlayerList().getPlayer(uuid);p.setInvulnerable(true);p.setNoGravity(true);p.teleportTo(server.overworld(),0.5,160,0.5,Set.of(),0,0);p.hasChangedDimension();
                for(int r=0;r<4;r++)for(int c=0;c<4;c++)p.getInventory().setItem(at(r,c),new ItemStack(item(r,c)));
                for(var slot:RealmArmor.SLOTS){String kind=switch(slot){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};var stack=new ItemStack(AegisArmor.item("aegis_"+kind));AegisPower.charge(stack,AegisPower.CAPACITY);p.setItemSlot(slot,stack);}
                p.inventoryMenu.broadcastChanges();return true;
            });phase=1;ticks=0;return;}
            if(job!=null&&!job.isDone())return;if(job!=null)job.join();if(mc.getOverlay()!=null)return;if(++ticks<35)return;ticks=0;
            if(phase==1){inspect(mc);REPORT.addProperty("version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("distant_horizons_loaded",true);
                mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING,false);mc.setScreen(new InventoryFixture(mc.player));phase=2;
            }else if(phase==2){require(mc.screen instanceof InventoryScreen,"Actual survival inventory required");shot(mc,"armor-icons-inventory");REPORT.addProperty("actual_inventory_screenshot",true);mc.setScreen(new Catalog(false));phase=3;
            }else if(phase==3){shot(mc,"armor-icons-catalog");job=mc.reloadResourcePacks();phase=4;
            }else if(phase==4){inspect(mc);REPORT.addProperty("resource_reload_checked",true);shot(mc,"armor-icons-after-reload");var uuid=mc.player.getUUID();job=server.submit(()->{
                var p=server.getPlayerList().getPlayer(uuid);p.getInventory().getItem(at(0,1)).set(DataComponents.DYED_COLOR,new DyedItemColor(0x9d4665,false));
                p.getInventory().getItem(at(2,1)).enchant(p.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION),3);p.inventoryMenu.broadcastChanges();return true;
            });phase=5;
            }else if(phase==5){require(mc.player.getInventory().getItem(at(2,1)).hasFoil(),"Native glint synchronization");require(mc.player.getInventory().getItem(at(0,1)).get(DataComponents.DYED_COLOR).rgb()==0x9d4665,"Native dye synchronization");inspect(mc);REPORT.addProperty("dye_and_glint_checked",true);mc.setScreen(new Catalog(true));phase=6;
            }else if(phase==6){shot(mc,"armor-icons-dye-glint");finish(mc,true,"");}
        }catch(Throwable t){t.printStackTrace();if(mc.level!=null)shot(mc,"armor-icons-failure");finish(mc,false,t.toString());}
    }
}
