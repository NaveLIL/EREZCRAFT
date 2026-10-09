package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.equipment.*;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.worldgen.GardenMaterials;

/** Prepared Creative inventories, ordinary client menu/pickup packets and different-JVM persistence. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class BackpackVisualSmoke {
    private static final String MODE=System.getProperty("interstice.backpackSmoke","");
    private static final String WORLD="native-portable-backpack-check";
    private static boolean started,finished,closing,passed;
    private static String reason;
    private static int stage,ticks;
    private static long deadline;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static JsonObject data=new JsonObject();
    private static ItemEntity pickup;
    private static net.minecraft.client.gui.screens.Screen lastMenuScreen;
    private BackpackVisualSmoke(){}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){var s=settling;if(!MODE.isEmpty()&&s!=null)s.tick(event.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){shutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen){
                require(MODE.equals("create")||MODE.equals("reload"),"Unknown backpack smoke mode");started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(8);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
                if(MODE.equals("create")){
                    require(!Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat")),"Disposable backpack save already exists");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Native portable backpack checks",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261006L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                }else{
                    data=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("backpack-create-validation.json"))).getAsJsonObject();require(data.get("passed").getAsBoolean()&&data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Missing successful separate-JVM creation");
                    mc.createWorldOpenFlows().openWorld(WORLD,()->{});
                }return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;require(System.nanoTime()<deadline,"Backpack stage deadline "+stage);
            if(mc.player==null||mc.level==null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            if(stage==0){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);if(MODE.equals("create"))prepare(p);else verifyCold(p);return true;});settling=new NativeChunkSettler.Session("backpack_initial_generation",2000);stage=1;ticks=0;return;
            }
            if(stage==1&&done()&&settling.ready()&&++ticks>=30){data.add("settle_before_native_menu",settling.report());settling=null;if(MODE.equals("create"))open(mc,0);else openRegisteredKey(mc,0);stage=MODE.equals("create")?2:40;ticks=0;}
            else if(stage==2&&menu(mc,54)&&++ticks>=20){shift(mc,56);shift(mc,54);shift(mc,55);shift(mc,58);stage=3;ticks=0;}
            else if(stage==3&&++ticks>=20){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var field=p.getInventory().getItem(0);require(count(field,RealmAgriculture.GRAIN.get(),true)==40&&count(field,RealmAgriculture.GRAIN.get(),false)==24,"Native shift transfer lost named/plain grain separation");require(count(field,Items.IRON_PICKAXE,false)==1,"Native shift transfer lost damaged tool");require(p.getInventory().getItem(13).is(Items.SHULKER_BOX),"Native menu allowed nested shulker storage");return true;});stage=4;ticks=0;
            }else if(stage==4&&done()){button(mc,BackpackMenu.SORT);stage=5;ticks=0;}
            else if(stage==5&&++ticks>=15){button(mc,BackpackMenu.STASH);stage=6;ticks=0;}
            else if(stage==6&&++ticks>=15){button(mc,BackpackMenu.REFILL);stage=7;ticks=0;}
            else if(stage==7&&++ticks>=15){button(mc,BackpackMenu.MODE);stage=8;ticks=0;}
            else if(stage==8&&++ticks>=20){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var field=p.getInventory().getItem(0);require(BackpackStorage.mode(field)==BackpackStorage.MATCHING,"Native mode button did not enable matching pickup");require(p.getInventory().getItem(2).is(MineralEcology.UMBRAL_COAL.get())&&p.getInventory().getItem(2).getCount()==16&&count(field,MineralEcology.UMBRAL_COAL.get(),false)==0,"Native stash/refill lost or duplicated coal");require(p.getInventory().getItem(13).is(Items.SHULKER_BOX)&&p.getInventory().getItem(14).is(Items.BUNDLE),"Stash accepted blocked nested containers");var sorted=BackpackStorage.read(field);require(sorted.get(0).is(RealmAgriculture.GRAIN.get())&&sorted.get(1).is(RealmAgriculture.GRAIN.get())&&sorted.get(2).is(Items.IRON_PICKAXE),"Native sort did not reorder the initial tool-first layout");checkTool(field);data.addProperty("native_shift_sort_stash_refill_and_matching_button",true);data.addProperty("native_nested_shulker_and_bundle_rejected",true);return true;});stage=9;ticks=0;
            }else if(stage==9&&done()&&menu(mc,54)&&++ticks>=20){shot(mc,"backpack-field-54.png");mc.player.closeContainer();work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);pickup=spawn(p,namedGrain(6));return true;});stage=10;ticks=0;}
            else if(stage==10&&done()){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);return count(p.getInventory().getItem(0),RealmAgriculture.GRAIN.get(),true)==46&&pickup.isRemoved();});stage=11;ticks=0;
            }else if(stage==11&&done()){
                if(!Boolean.TRUE.equals(work.join())){require(++ticks<120,"Actual matching pickup did not reach the field pack");work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);return count(p.getInventory().getItem(0),RealmAgriculture.GRAIN.get(),true)==46&&pickup.isRemoved();});return;}
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);p.getInventory().items.set(15,new ItemStack(MineralEcology.PHOSPHORITE_CRYSTAL.get(),8));p.getInventory().setChanged();p.containerMenu.broadcastChanges();data.addProperty("ordinary_matching_item_pickup_preserved_components",true);return true;});stage=12;ticks=0;
            }else if(stage==12&&done()&&++ticks>=20){open(mc,1);stage=13;ticks=0;}
            else if(stage==13&&menu(mc,72)&&++ticks>=20){shift(mc,78);button(mc,BackpackMenu.MODE);stage=14;ticks=0;}
            else if(stage==14&&++ticks>=15){button(mc,BackpackMenu.MODE);stage=15;ticks=0;}
            else if(stage==15&&++ticks>=20){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var expedition=p.getInventory().getItem(1);require(BackpackStorage.mode(expedition)==BackpackStorage.MATERIALS&&count(expedition,MineralEcology.PHOSPHORITE_CRYSTAL.get(),false)==8,"Native expedition material mode/transfer failed");return true;});stage=16;ticks=0;
            }else if(stage==16&&done()&&menu(mc,72)&&++ticks>=20){shot(mc,"backpack-expedition-72.png");mc.player.closeContainer();work=server.submit(()->{pickup=spawn(server.getPlayerList().getPlayer(id),new ItemStack(MineralEcology.UMBRAL_COAL.get(),5));return true;});stage=17;ticks=0;}
            else if(stage==17&&done()){work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);return count(p.getInventory().getItem(1),MineralEcology.UMBRAL_COAL.get(),false)==5&&pickup.isRemoved();});stage=18;ticks=0;}
            else if(stage==18&&done()){
                if(!Boolean.TRUE.equals(work.join())){require(++ticks<120,"Actual materials pickup did not reach expedition pack");work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);return count(p.getInventory().getItem(1),MineralEcology.UMBRAL_COAL.get(),false)==5&&pickup.isRemoved();});return;}
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);verifySaved(p);data.addProperty("ordinary_expedition_material_pickup",true);data.addProperty("field_uuid",BackpackStorage.id(p.getInventory().getItem(0)).toString());data.addProperty("expedition_uuid",BackpackStorage.id(p.getInventory().getItem(1)).toString());return true;});stage=19;
            }else if(stage==19&&done()){finish(mc,true,"Native54/72 slot menus, actual inventory/button packets, matching/material pickup and component-preserving saved packs passed");}
            else if(stage==40&&menu(mc,54)&&++ticks>=30){require(mc.player.containerMenu.slots.size()==91,"Cold field menu slot layout differs");data.addProperty("native_registered_hotkey_open",true);data.addProperty("native_registered_hotkey","B");shot(mc,"backpack-field-54-cold.png");mc.player.closeContainer();stage=41;ticks=0;}
            else if(stage==41&&++ticks>=20){open(mc,1);stage=42;ticks=0;}
            else if(stage==42&&menu(mc,72)&&++ticks>=30){require(mc.player.containerMenu.slots.size()==110,"Cold expedition menu slot layout differs");shot(mc,"backpack-expedition-72-cold.png");mc.player.closeContainer();work=server.submit(()->{verifyCold(server.getPlayerList().getPlayer(id));return true;});stage=43;ticks=0;}
            else if(stage==43&&done()){finish(mc,true,"Different-JVM reload preserves both UUIDs, mode, exact item counts, custom data/name and tool damage; both native menus reopened");}
        }catch(Throwable failure){failure.printStackTrace();finish(mc,false,failure.toString());}
    }
    private static void prepare(ServerPlayer p){
        var level=p.serverLevel();var base=p.blockPosition().below();for(int x=-2;x<=9;x++)for(int z=-2;z<=9;z++){level.setBlock(base.offset(x,0,z),GardenMaterials.PALEHEART_PLANKS.get().defaultBlockState(),3);for(int y=1;y<=5;y++)level.setBlock(base.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);}
        p.setGameMode(GameType.CREATIVE);p.teleportTo(level,base.getX()+3.5,base.getY()+1,base.getZ()+3.5,Set.of(),0,0);p.getInventory().clearContent();
        var field=new ItemStack(ExpeditionEquipment.FIELD_BACKPACK.get());field.set(DataComponents.CUSTOM_NAME,Component.literal("Native saved field pack"));
        var expedition=new ItemStack(ExpeditionEquipment.EXPEDITION_BACKPACK.get());expedition.set(DataComponents.CUSTOM_NAME,Component.literal("Native saved expedition pack"));
        var tool=new ItemStack(Items.IRON_PICKAXE);tool.setDamageValue(7);tool.set(DataComponents.CUSTOM_NAME,Component.literal("Native component tool"));var marker=new CompoundTag();marker.putInt("native_marker",173);tool.set(DataComponents.CUSTOM_DATA,CustomData.of(marker));
        p.getInventory().items.set(0,field);p.getInventory().items.set(1,expedition);p.getInventory().items.set(2,new ItemStack(MineralEcology.UMBRAL_COAL.get(),4));p.getInventory().items.set(9,namedGrain(40));p.getInventory().items.set(10,new ItemStack(RealmAgriculture.GRAIN.get(),24));p.getInventory().items.set(11,tool);p.getInventory().items.set(12,new ItemStack(MineralEcology.UMBRAL_COAL.get(),12));p.getInventory().items.set(13,new ItemStack(Items.SHULKER_BOX));p.getInventory().items.set(14,new ItemStack(Items.BUNDLE));p.getInventory().setChanged();p.containerMenu.broadcastChanges();
        data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("scope","Prepared disposable Creative platform, inventory and pickup entities; ordinary native RMB, inventory packets and menu buttons. No autonomous Survival route or vanilla upgrade crafting is claimed.");
    }
    private static ItemStack namedGrain(int amount){var stack=new ItemStack(RealmAgriculture.GRAIN.get(),amount);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Native named grain"));var tag=new CompoundTag();tag.putInt("native_marker",808);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return stack;}
    private static ItemEntity spawn(ServerPlayer p,ItemStack stack){var entity=new ItemEntity(p.serverLevel(),p.getX(),p.getY()+.1,p.getZ(),stack);entity.setNoPickUpDelay();entity.setDeltaMovement(0,0,0);p.serverLevel().addFreshEntity(entity);return entity;}
    private static int count(ItemStack pack,Item item,boolean named){return BackpackStorage.read(pack).stream().filter(s->s.is(item)&&(!item.equals(RealmAgriculture.GRAIN.get())||s.has(DataComponents.CUSTOM_NAME)==named)).mapToInt(ItemStack::getCount).sum();}
    private static void checkTool(ItemStack pack){var tool=BackpackStorage.read(pack).stream().filter(s->s.is(Items.IRON_PICKAXE)).findFirst().orElseThrow();require(tool.getCount()==1&&tool.getDamageValue()==7&&tool.getHoverName().getString().equals("Native component tool")&&tool.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("native_marker")==173,"Stored tool components changed");}
    private static void verifySaved(ServerPlayer p){var field=p.getInventory().getItem(0);var expedition=p.getInventory().getItem(1);require(field.is(ExpeditionEquipment.FIELD_BACKPACK.get())&&expedition.is(ExpeditionEquipment.EXPEDITION_BACKPACK.get())&&BackpackStorage.valid(field)&&BackpackStorage.valid(expedition),"Saved pack source/type/contents are invalid");require(BackpackStorage.mode(field)==1&&BackpackStorage.mode(expedition)==2,"Saved automatic modes changed");require(count(field,RealmAgriculture.GRAIN.get(),true)==46&&count(field,RealmAgriculture.GRAIN.get(),false)==24&&count(expedition,MineralEcology.PHOSPHORITE_CRYSTAL.get(),false)==8&&count(expedition,MineralEcology.UMBRAL_COAL.get(),false)==5,"Saved pack item quantities changed");checkTool(field);var named=BackpackStorage.read(field).stream().filter(s->s.is(RealmAgriculture.GRAIN.get())&&s.has(DataComponents.CUSTOM_NAME)).findFirst().orElseThrow();require(ItemStack.isSameItemSameComponents(named,namedGrain(1)),"Saved named grain components changed");require(field.getHoverName().getString().equals("Native saved field pack")&&expedition.getHoverName().getString().equals("Native saved expedition pack"),"Pack custom names changed");}
    private static void verifyCold(ServerPlayer p){verifySaved(p);require(BackpackStorage.id(p.getInventory().getItem(0)).toString().equals(data.get("field_uuid").getAsString())&&BackpackStorage.id(p.getInventory().getItem(1)).toString().equals(data.get("expedition_uuid").getAsString()),"Cold reload changed backpack identities");data.addProperty("reload_pid",ProcessHandle.current().pid());data.addProperty("cold_exact_components_quantities_ids_modes_preserved",true);}
    private static void open(Minecraft mc,int slot){require(mc.screen==null&&mc.player.containerMenu==mc.player.inventoryMenu,"Native backpack should open from inventory only");mc.player.getInventory().selected=slot;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    private static void openRegisteredKey(Minecraft mc,int slot){
        require(mc.screen==null&&mc.player.containerMenu==mc.player.inventoryMenu,"Registered B key should open from world only");
        // Send the ordinary held-slot packet before the registered key handler's custom payload;
        // a cold save initially restores selected slot1, so changing only client fields would race.
        mc.player.getInventory().selected=slot;mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        KeyMapping.click(InputConstants.getKey(org.lwjgl.glfw.GLFW.GLFW_KEY_B,-1));
    }
    private static boolean menu(Minecraft mc,int size){
        boolean ready=mc.screen!=null&&mc.player.containerMenu instanceof BackpackMenu pack&&pack.capacity==size&&pack.container.getContainerSize()==size;
        if(ready&&mc.screen!=lastMenuScreen){lastMenuScreen=mc.screen;org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),4,4);}
        return ready;
    }
    private static void shift(Minecraft mc,int slot){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,ClickType.QUICK_MOVE,mc.player);}
    private static void button(Minecraft mc,int button){mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,button);}
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("BACKPACK_SCREENSHOT "+name));}
    private static void require(boolean yes,String message){if(!yes)throw new IllegalStateException(message);}
    private static void finish(Minecraft mc,boolean success,String why){if(closing)return;closing=true;passed=success;reason=why;if(mc.player!=null)mc.player.closeContainer();mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.broadcastOptions();settling=new NativeChunkSettler.Session("backpack_terminal_shutdown",160);if(!success)write(mc,true);}
    private static void shutdown(Minecraft mc){if(mc.getSingleplayerServer()==null){finished=true;write(mc,false);mc.stop();return;}if(settling.failed()&&!data.has("quiescence_failed")){passed=false;reason=settling.failure();data.addProperty("quiescence_failed",true);write(mc,true);}if(!settling.ready())return;data.add("settle_terminal",settling.report());data.addProperty("clean_generation_before_mc_stop",true);finished=true;settling=null;write(mc,false);mc.stop();}
    private static void write(Minecraft mc,boolean pending){data.addProperty("passed",passed);data.addProperty("reason",reason);data.addProperty("mode",MODE);data.addProperty("stage",stage);data.addProperty("shutdown_pending",pending);try{Files.writeString(mc.gameDirectory.toPath().resolve("backpack-"+MODE+"-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception failure){failure.printStackTrace();}System.out.println("BACKPACK_VALIDATION "+data);}
}
