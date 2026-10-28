package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.*;
import pro.erez.interstice.worldgen.IslandWorld;

/** Disposable full-pack visual and two-process persistence proof; not unassisted Survival. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class ExpeditionProgressionSmoke {
    private static final String MODE=System.getProperty("interstice.progressionSmoke","");
    private static final String WORLD="epg";
    private static boolean started,finished,closing;
    private static volatile NativeChunkSettler.Session settling;
    private static int stage,ticks;
    private static long deadline;
    private static volatile long serverGameTime, preparedAt;
    private static CompletableFuture<?> work;
    private static final JsonObject report=new JsonObject();
    private ExpeditionProgressionSmoke(){}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){
        if(MODE.isEmpty())return;
        serverGameTime=event.getServer().overworld().getGameTime();
        var s=settling;if(s!=null)s.tick(event.getServer());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){
                if(settling.failed())throw new IllegalStateException(settling.failure());
                if(settling.ready()){finished=true;report.add("settled",settling.report());write(mc);mc.stop();}
                return;
            }
            if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned profile required");
                require(ModList.get().isLoaded("distanthorizons")&&ModList.get().isLoaded("create")&&ModList.get().isLoaded("sable"),"Full pack and DH required");
                started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(12);
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);
                mc.options.languageCode="ru_ru";
                if(MODE.equals("create")){
                    require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/"+WORLD)),"Fresh world required");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Owned progression proof",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261026L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                }else{
                    var old=JsonParser.parseString(Files.readString(path(mc,"create"))).getAsJsonObject();
                    require(old.get("passed").getAsBoolean()&&old.get("pid").getAsLong()!=ProcessHandle.current().pid(),"Cold reload requires a passed, different creation JVM");
                    mc.createWorldOpenFlows().openWorld(WORLD,()->{});
                }
                return;
            }
            if(!started)return;require(System.nanoTime()<deadline,"Native progression deadline stage="+stage);
            if(SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==0){work=server.submit(()->{
                var p=server.getPlayerList().getPlayer(uuid);
                if(MODE.equals("create"))prepare(p);else verifyCold(p);
                preparedAt=p.server.overworld().getGameTime();
                return true;
            });stage=1;ticks=0;return;}
            if(stage==1&&done()&&serverGameTime-preparedAt>=100&&++ticks>20){
                require(mc.level.dimension().equals(IslandWorld.CURRENT_WORLD),"Client must reach real V6");
                require(mc.player.getMainHandItem().is(FieldJournal.JOURNAL.get()),"Journal must synchronize to client hand");
                require(mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND).consumesAction(),"Normal use packet must open journal");
                stage=2;ticks=0;
            }else if(stage==2&&++ticks>25){
                if(!(mc.screen instanceof BookViewScreen)&&ticks<240)return;
                require(mc.screen instanceof BookViewScreen,"Ordinary item use must open native book UI; screen="+(mc.screen==null?"null":mc.screen.getClass().getName()));
                var content=mc.player.getMainHandItem().get(DataComponents.WRITTEN_BOOK_CONTENT);
                require(content!=null&&content.pages().size()==16,"All sixteen pages must arrive");
                int max=0;
                for(var page:content.pages()){
                    int lines=mc.font.split(page.raw(),114).size();max=Math.max(max,lines);
                    require(lines<=14,"Book page overflows native viewport: "+lines+" lines: "+page.raw().getString());
                }
                report.addProperty("native_book_pages",content.pages().size());report.addProperty("maximum_page_lines",max);
                shot(mc,"progression-"+MODE+"-journal.png");stage=3;ticks=0;
            }else if(stage==3&&++ticks>15){
                mc.setScreen(new AdvancementsScreen(mc.player.connection.getAdvancements()));stage=4;ticks=0;
            }else if(stage==4&&++ticks>25){
                require(mc.screen instanceof AdvancementsScreen,"Native advancement screen must remain open");
                var root=mc.player.connection.getAdvancements().getTree().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"root"));
                require(root!=null,"Realm root must arrive over ordinary advancement packets");
                mc.player.connection.getAdvancements().setSelectedTab(root.holder(),true);
                stage=5;ticks=0;
            }else if(stage==5&&++ticks>25){
                shot(mc,"progression-"+MODE+"-trials.png");mc.setScreen(new InventoryScreen(mc.player));stage=6;ticks=0;
            }else if(stage==6&&++ticks>25){
                for(var entry:RealmArmaments.EQUIPMENT.entrySet()){
                    var stack=new ItemStack(entry.getValue().get());
                    var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
                    require(model!=mc.getModelManager().getMissingModel(),"Missing equipment model: "+entry.getKey());
                    require(!model.getQuads(null,null,net.minecraft.util.RandomSource.create(1)).isEmpty(),"Empty equipment geometry: "+entry.getKey());
                }
                var longModel=mc.getItemRenderer().getModel(new ItemStack(RealmArmaments.item("riftsteel_longsword")),mc.level,mc.player,0);
                var longQuads=longModel.getQuads(null,null,net.minecraft.util.RandomSource.create(1));
                require(longQuads.size()==704,"Explicit LS-03 geometry must bake as 704 quads, not an automatically generated sprite: "+longQuads.size());
                float tipX=0,tipY=0;
                for(var quad:longQuads){var v=quad.getVertices();int stride=v.length/4;
                    for(int vertex=0;vertex<4;vertex++){tipX=Math.max(tipX,Float.intBitsToFloat(v[vertex*stride]));tipY=Math.max(tipY,Float.intBitsToFloat(v[vertex*stride+1]));}}
                require(tipX>1&&tipY>1,"The real baked blade must extend beyond the original 16-unit sprite square");
                report.addProperty("longsword_baked_quads",longQuads.size());
                var chest=mc.player.getItemBySlot(EquipmentSlot.CHEST);
                require(chest.is(net.minecraft.tags.ItemTags.DYEABLE)&&RealmArmorClient.model(true,EquipmentSlot.CHEST).cubeCount()==81,"Authored dark teal armor geometry must replace leather while preserving dye support");
                shot(mc,"progression-"+MODE+"-equipment.png");report.addProperty("models_checked",RealmArmaments.EQUIPMENT.size());
                report.addProperty("mod_version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());report.addProperty("distant_horizons_loaded",true);report.addProperty("mod_count",ModList.get().getMods().size());
                mc.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.getInventory().selected=1;
                mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));
                stage=7;ticks=0;
            }else if(stage==7&&++ticks>30){
                require(mc.player.getMainHandItem().is(RealmArmaments.item("riftsteel_longsword")),"Longsword must reach the client hand");
                shot(mc,"progression-"+MODE+"-longsword-firstperson.png");
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);stage=8;ticks=0;
            }else if(stage==8&&++ticks>30){
                shot(mc,"progression-"+MODE+"-longsword-thirdperson.png");
                mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.getInventory().selected=2;
                mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(2));
                stage=9;ticks=0;
            }else if(stage==9&&++ticks>30){
                require(mc.player.getMainHandItem().is(RealmArmaments.item("riftsteel_sword")),"Original sword must remain selectable");
                shot(mc,"progression-"+MODE+"-original-sword-firstperson.png");
                report.addProperty("both_sword_hand_views",true);
                mc.player.getInventory().selected=0;
                mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
                stage=10;ticks=0;
            }else if(stage==10&&++ticks>30){
                require(mc.player.getMainHandItem().is(FieldJournal.JOURNAL.get()),"Restore journal before the cold restart");
                if(ArmorNativeProbe.tick(mc,report))finish(mc,true,"");
            }
        }catch(Throwable error){error.printStackTrace();if(mc.level!=null)shot(mc,"progression-"+MODE+"-failure.png");finish(mc,false,error.toString());}
    }
    private static void prepare(ServerPlayer p){
        var world=p.server.getLevel(IslandWorld.CURRENT_WORLD);require(world!=null,"Real V6 required");
        int x=32,z=32;world.getChunk(x>>4,z>>4);
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            world.setBlock(new BlockPos(x+dx,79,z+dz),Blocks.STONE.defaultBlockState(),3);
            for(int y=80;y<=83;y++)world.setBlock(new BlockPos(x+dx,y,z+dz),Blocks.AIR.defaultBlockState(),3);
            world.setBlock(new BlockPos(x+dx,84,z+dz),Blocks.STONE.defaultBlockState(),3);
        }
        p.teleportTo(world,x+.5,80,z+.5,Set.of(),0,0);p.hasChangedDimension();p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setInvulnerable(true);
        p.getInventory().clearContent();ExpeditionTrials.ensureJournal(p);
        p.getInventory().setItem(0,FieldJournal.create(p));p.getInventory().selected=0;
        int slot=1;
        for(var entry:RealmArmaments.EQUIPMENT.entrySet())p.getInventory().setItem(slot++,new ItemStack(entry.getValue().get()));
        reserveHotbar(p,"riftsteel_longsword",1);reserveHotbar(p,"riftsteel_sword",2);
        var longsword=p.getInventory().getItem(1);longsword.setDamageValue(23);
        longsword.set(DataComponents.CUSTOM_NAME,Component.literal("Длинный меч: сохранение"));
        reserveHotbar(p,"riftsteel_pickaxe",3);
        var worktool=p.getInventory().getItem(3);RealmWorktools.setArea(worktool,true);worktool.setDamageValue(41);
        worktool.set(DataComponents.CUSTOM_NAME,Component.literal("WT03 persistence"));
        var custom=worktool.get(DataComponents.CUSTOM_DATA).copyTag();custom.putString("studio_sentinel","preserve");
        worktool.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(custom));
        int engineerSlot=26;
        for(var kind:List.of("helmet","chestplate","leggings","boots")){
            var piece=new ItemStack(EngineerArmor.item(kind));piece.setDamageValue(engineerSlot-16);
            piece.set(DataComponents.CUSTOM_NAME,Component.literal("E12 "+kind));p.getInventory().setItem(engineerSlot++,piece);
        }
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(RealmArmaments.item("riftsteel_helmet")));
        p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RealmArmaments.item("riftsteel_chestplate")));
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(RealmArmaments.item("riftsteel_leggings")));
        p.setItemSlot(EquipmentSlot.FEET,new ItemStack(RealmArmaments.item("riftsteel_boots")));
        p.getItemBySlot(EquipmentSlot.CHEST).setDamageValue(37);
        p.getItemBySlot(EquipmentSlot.CHEST).set(DataComponents.CUSTOM_NAME,Component.literal("Проверка сохранения"));
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
    }
    private static void verifyCold(ServerPlayer p){
        require(p.level().dimension().equals(IslandWorld.CURRENT_WORLD),"Cold dimension changed");
        require(p.getItemBySlot(EquipmentSlot.CHEST).is(RealmArmaments.item("riftsteel_chestplate"))&&p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue()==37,"Cold worn item/damage changed");
        require(p.getItemBySlot(EquipmentSlot.CHEST).getHoverName().getString().equals("Проверка сохранения"),"Cold custom name changed");
        require(ExpeditionTrials.completed(p,"trials/riftsteel_suit"),"Completed native advancement must survive cold reload");
        require(p.getPersistentData().getBoolean(ExpeditionTrials.JOURNAL_RECEIVED),"Journal delivery flag must survive cold reload");
        require(p.getInventory().countItem(FieldJournal.JOURNAL.get())==1,"Cold login duplicated or lost journal");
        require(p.getInventory().getItem(1).is(RealmArmaments.item("riftsteel_longsword"))
            && p.getInventory().getItem(1).getDamageValue()==23
            && p.getInventory().getItem(1).getHoverName().getString().equals("Длинный меч: сохранение"),"Cold longsword identity, wear or name changed");
        require(p.getInventory().getItem(2).is(RealmArmaments.item("riftsteel_sword")),"Cold original sword disappeared");
        var worktool=p.getInventory().getItem(3);
        require(worktool.is(RealmArmaments.item("riftsteel_pickaxe"))&&RealmWorktools.area(worktool)
            &&worktool.getDamageValue()==41&&worktool.getHoverName().getString().equals("WT03 persistence")
            &&worktool.get(DataComponents.CUSTOM_DATA).copyTag().getString("studio_sentinel").equals("preserve"),"Cold tool mode, name, wear or custom data changed");
        int engineerSlot=26;
        for(var kind:List.of("helmet","chestplate","leggings","boots")){
            var piece=p.getInventory().getItem(engineerSlot);
            require(piece.is(EngineerArmor.item(kind))&&piece.getDamageValue()==engineerSlot-16
                &&piece.getHoverName().getString().equals("E12 "+kind),"Cold engineer item changed: "+kind);engineerSlot++;
        }
        report.addProperty("cold_worktool_mode_and_custom_data",true);report.addProperty("cold_engineer_items",true);
        report.addProperty("cold_distinct_swords",true);
        p.setNoGravity(true);report.addProperty("cold_equipment_advancement_and_journal",true);
    }
    private static void reserveHotbar(ServerPlayer p,String item,int hotbar){
        for(int i=0;i<p.getInventory().items.size();i++)if(p.getInventory().getItem(i).is(RealmArmaments.item(item))){
            var displaced=p.getInventory().getItem(hotbar);p.getInventory().setItem(hotbar,p.getInventory().getItem(i));
            p.getInventory().setItem(i,displaced);return;
        }
        throw new IllegalStateException("Missing prepared item "+item);
    }
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("PROGRESSION_SCREENSHOT "+name));}
    private static Path path(Minecraft mc,String mode){return mc.gameDirectory.toPath().resolve("progression-"+mode+".json");}
    private static void write(Minecraft mc){try{Files.writeString(path(mc,MODE),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void finish(Minecraft mc,boolean passed,String reason){
        if(finished)return;report.addProperty("passed",passed);report.addProperty("reason",reason);report.addProperty("pid",ProcessHandle.current().pid());report.addProperty("stage",stage);
        report.addProperty("owner_world_used",false);report.addProperty("prepared_fixture_not_survival",true);
        if(!passed||mc.getSingleplayerServer()==null){finished=true;write(mc);mc.stop();return;}
        closing=true;mc.setScreen(null);mc.options.renderDistance().set(2);mc.options.broadcastOptions();
        settling=new NativeChunkSettler.Session("progression_native_shutdown",2000);
    }
}
