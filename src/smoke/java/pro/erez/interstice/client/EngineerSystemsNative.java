package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.*;

/** Full-pack client, actual menu packets and air ticks, then a distinct JVM cold reload. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class EngineerSystemsNative {
    private static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("engineersystems");
    private static final boolean RELOAD=System.getProperty("interstice.engineerCold","create").equals("reload");
    private static final String WORLD="engineer-d02-owned";
    private static final BlockPos STORED=new BlockPos(28,200,28);
    private static final JsonObject REPORT=new JsonObject();private static final JsonArray SHOTS=new JsonArray();
    private static boolean started,finished,closing;private static int phase,ticks;private static long deadline,waterStart;
    private static CompletableFuture<?> work;private static NativeChunkSettler.Session settling;
    private static UUID visible,hidden;private static int waterReserve,waterAir;private static long otherPid;private static int warmup;
    private EngineerSystemsNative(){}
    private static void require(boolean test,String why){if(!test)throw new IllegalStateException(why);}
    private static String kind(EquipmentSlot s){return switch(s){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    private static void equip(ServerPlayer p){for(var s:RealmArmor.SLOTS)p.setItemSlot(s,new ItemStack(EngineerArmor.item(kind(s))));
        var cell=new ItemStack(EngineerArmor.CELL.get());EngineerModules.energy(cell,1200000);EngineerModules.set(p.getItemBySlot(EquipmentSlot.HEAD),0,cell);
        var air=new ItemStack(EngineerArmor.CASSETTE.get());EngineerModules.set(p.getItemBySlot(EquipmentSlot.CHEST),0,air);
        var belt=new ItemStack(RealmGear.BALLAST_BELT.get());belt.setDamageValue(37);EngineerModules.set(p.getItemBySlot(EquipmentSlot.CHEST),1,belt);
    }
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static void submit(Minecraft mc,Runnable action){work=mc.getSingleplayerServer().submit(()->{action.run();return true;});ticks=0;}
    private static void shot(Minecraft mc,String name){String file="engineer-d02-"+name+".png";Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),unused->{});SHOTS.add(file);}
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("engineer-d02-"+(RELOAD?"reload":"create")+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void finish(Minecraft mc,boolean passed,String reason){
        if(finished||closing)return;REPORT.addProperty("passed",passed);REPORT.addProperty("reason",reason);REPORT.addProperty("phase",phase);REPORT.addProperty("pid",ProcessHandle.current().pid());REPORT.addProperty("owner_profile_used",false);REPORT.addProperty("prepared_fixture_not_survival",true);REPORT.add("screenshots",SHOTS);
        if(!passed)write(mc);closing=true;settling=new NativeChunkSettler.Session("engineer_d02_shutdown",2000);
    }
    private static void prepare(Minecraft mc){
        var p=player(mc);var level=p.serverLevel();level.setDayTime(6000);level.setWeatherParameters(120000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
        level.getChunk(1,1);level.getChunk(1,2);level.getChunk(2,1);level.getChunk(2,2);
        for(int x=20;x<=46;x++)for(int z=20;z<=46;z++){level.setBlock(new BlockPos(x,199,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);for(int y=200;y<=206;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        for(int x=20;x<=46;x++)for(int y=200;y<=205;y++)level.setBlock(new BlockPos(x,y,46),Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),3);
        for(int y=200;y<=204;y++)for(int z=27;z<=33;z++)level.setBlock(new BlockPos(34,y,z),Blocks.STONE.defaultBlockState(),3);
        for(int x=31;x<=33;x++)for(int y=200;y<=202;y++)level.setBlock(new BlockPos(x,y,35),Blocks.STONE.defaultBlockState(),3);
        p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.teleportTo(level,32.5,200,31.5,Set.of(),0,0);p.hasChangedDimension();p.getInventory().clearContent();equip(p);
        var tool=new ItemStack(RealmArmaments.item("riftsteel_pickaxe"));RealmWorktools.setArea(tool,true);p.setItemSlot(EquipmentSlot.MAINHAND,tool);
        var seen=EntityType.COW.create(level);seen.setPos(29.5,200,32);seen.setNoAi(true);level.addFreshEntity(seen);visible=seen.getUUID();
        var behind=EntityType.COW.create(level);behind.setPos(36.5,200,30);behind.setNoAi(true);level.addFreshEntity(behind);hidden=behind.getUUID();p.inventoryMenu.broadcastChanges();
    }
    private static void coldSave(Minecraft mc){
        var p=player(mc);var level=p.serverLevel();level.setBlock(STORED,Blocks.CHEST.defaultBlockState(),3);var chest=(ChestBlockEntity)level.getBlockEntity(STORED);require(chest!=null,"Cold storage chest exists");
        var head=p.getItemBySlot(EquipmentSlot.HEAD).copy();var cell=EngineerModules.get(head,0);EngineerModules.energy(cell,1234025);cell.set(DataComponents.CUSTOM_NAME,Component.literal("Owned fractional cell"));var data=EngineerModules.data(cell);data.putString("unknown_owner_data","keep");EngineerModules.data(cell,data);EngineerModules.set(head,0,cell);head.setDamageValue(27);
        var body=p.getItemBySlot(EquipmentSlot.CHEST).copy();var air=EngineerModules.get(body,0);EngineerModules.reserve(air,11111);EngineerModules.set(body,0,air);var belt=EngineerModules.get(body,1);belt.setDamageValue(37);var b=EngineerModules.data(belt);b.putInt("interstice_ballast_active_ticks",17);EngineerModules.data(belt,b);EngineerModules.set(body,1,belt);
        chest.setItem(0,head);chest.setItem(1,body);chest.setChanged();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);p.server.getPlayerList().saveAll();REPORT.addProperty("saved_fractional_energy",1234025);REPORT.addProperty("saved_breathing_ticks",11111);REPORT.addProperty("saved_physical_belt_damage",37);
    }
    private static void coldCheck(Minecraft mc){
        var p=player(mc);var level=p.serverLevel();level.getChunk(STORED);var chest=(ChestBlockEntity)level.getBlockEntity(STORED);require(chest!=null,"Real region chest survived");var head=chest.getItem(0);var body=chest.getItem(1);var cell=EngineerModules.get(head,0);
        require(EngineerModules.energy(cell)==1234025&&cell.getHoverName().getString().equals("Owned fractional cell")&&EngineerModules.data(cell).getString("unknown_owner_data").equals("keep"),"Exact fractional charge and module metadata after cold reload");
        require(head.getDamageValue()==27&&EngineerModules.reserve(EngineerModules.get(body,0))==11111,"Native wear and spent reserve survive region reload");var belt=EngineerModules.get(body,1);require(belt.getDamageValue()==37&&EngineerModules.data(belt).getInt("interstice_ballast_active_ticks")==17,"Actual belt and partial wear counter survive");
        REPORT.addProperty("cold_region_items_and_fractional_resources",true);REPORT.addProperty("creation_pid",otherPid);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing)return;
            if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned profile only");require(ModList.get().isLoaded("distanthorizons")&&ModList.get().isLoaded("create")&&ModList.get().isLoaded("sable"),"Full pack / DH required");
                REPORT.addProperty("distant_horizons_loaded",true);REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("mod_version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());
                started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.fov().set(70);mc.options.bobView().set(false);mc.options.guiScale().set(3);mc.resizeDisplay();
                if(RELOAD){var old=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("engineer-d02-create.json"))).getAsJsonObject();require(old.get("passed").getAsBoolean(),"Passed create required");otherPid=old.get("pid").getAsLong();require(otherPid!=ProcessHandle.current().pid(),"Cold reload needs a different JVM");mc.createWorldOpenFlows().openWorld(WORLD,()->{});}
                else{require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/"+WORLD)),"Fresh owned world required");mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Engineer D02 isolated mechanics",GameType.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261028L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);}return;
            }
            if(!started)return;require(System.nanoTime()<deadline,"D02 deadline phase="+phase);if(mc.getOverlay()!=null||SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;mc.getToasts().clear();
            if(work!=null&&!work.isDone())return;if(work!=null)work.join();if(++ticks<30)return;ticks=0;
            if(RELOAD){
                if(phase==0){submit(mc,()->{coldCheck(mc);var p=player(mc);var stored=(ChestBlockEntity)p.serverLevel().getBlockEntity(STORED);p.setItemSlot(EquipmentSlot.HEAD,stored.removeItemNoUpdate(0));p.setItemSlot(EquipmentSlot.CHEST,stored.removeItemNoUpdate(1));stored.setChanged();p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(EngineerArmor.item("leggings")));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(EngineerArmor.item("boots")));var pick=new ItemStack(RealmArmaments.item("riftsteel_pickaxe"));RealmWorktools.setArea(pick,true);p.setItemSlot(EquipmentSlot.MAINHAND,pick);p.teleportTo(p.serverLevel(),32.5,200,31.5,Set.of(),0,0);p.hasChangedDimension();});phase=1;}
                else if(phase==1){mc.levelRenderer.allChanged();phase=2;warmup=0;}
                else if(phase==2&&++warmup>=10){require(EngineerClient.active(),"Reloaded physical cell powers the visor");require(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&RealmWorktools.preview(mc.player,mc.player.getMainHandItem(),hit.getBlockPos(),hit.getDirection()).size()==9,"Real saved wall has nine eligible blocks after reload");mc.gui.getChat().clearMessages(true);shot(mc,"cold-reload");finish(mc,true,"");}return;
            }
            if(phase==0){submit(mc,()->prepare(mc));phase=1;}
            else if(phase==1){if(++warmup<4)return;require(EngineerArmor.worn(mc.player,EquipmentSlot.HEAD)&&EngineerClient.active(),"Real client received equipped powered engineer");require(mc.player.getArmorValue()==22,"Native material attributes arrived");require(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&RealmWorktools.preview(mc.player,mc.player.getMainHandItem(),hit.getBlockPos(),hit.getDirection()).size()==9,"Client sees the real eligible 3x3 footprint");mc.gui.getChat().clearMessages(true);REPORT.addProperty("client_shared_3x3_preview",true);shot(mc,"hud-and-tool-preview");mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);phase=2;}
            else if(phase==2){shot(mc,"e12-equipped");mc.options.setCameraType(CameraType.FIRST_PERSON);PacketDistributor.sendToServer(new EngineerNetworking.Action(1));phase=3;}
            else if(phase==3){Entity seen=null,behind=null;for(var e:mc.level.entitiesForRendering()){if(e.getUUID().equals(visible))seen=e;if(e.getUUID().equals(hidden))behind=e;}require(seen!=null&&behind!=null&&EngineerClient.scanned(seen)&&!EngineerClient.scanned(behind),"Actual C2S / S2C pulse includes visible cow and excludes wall target");REPORT.addProperty("network_pulse_los_and_energy",true);shot(mc,"visible-pulse");PacketDistributor.sendToServer(new EngineerNetworking.Action(0));phase=4;}
            else if(phase==4){require(mc.screen instanceof EngineerScreen&&mc.player.containerMenu instanceof EngineerMenu,"Native server menu opens through packet");require(mc.player.containerMenu.getSlot(0).getItem().is(EngineerArmor.CELL.get()),"Cell arrived in real socket");shot(mc,"modules-menu");mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,ClickType.QUICK_MOVE,mc.player);phase=5;}
            else if(phase==5){require(mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Cell withdrawn through native menu");int found=-1,count=0;for(int i=3;i<39;i++){var stack=mc.player.containerMenu.getSlot(i).getItem();if(stack.is(EngineerArmor.CELL.get())){found=i;count+=stack.getCount();}}require(count==1,"Exactly one physical cell after network transfer");mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,found,0,ClickType.QUICK_MOVE,mc.player);phase=6;}
            else if(phase==6){require(mc.player.containerMenu.getSlot(0).getItem().is(EngineerArmor.CELL.get()),"Same cell reinstalled through native menu");REPORT.addProperty("network_menu_module_conservation",true);mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,1);phase=7;}
            else if(phase==7){var head=mc.player.getItemBySlot(EquipmentSlot.HEAD);REPORT.addProperty("nv_client_flag",EngineerSystems.night(head));REPORT.addProperty("nv_client_active",EngineerClient.active());REPORT.addProperty("nv_client_camera_self",mc.getCameraEntity()==mc.player);REPORT.addProperty("nv_client_virtual",mc.player.hasEffect(MobEffects.NIGHT_VISION));REPORT.addProperty("nv_client_real",mc.player.getActiveEffectsMap().get(MobEffects.NIGHT_VISION)!=null);REPORT.addProperty("nv_client_energy",EngineerModules.energy(EngineerModules.get(head,0)));submit(mc,()->{var p=player(mc);REPORT.addProperty("nv_server_flag",EngineerSystems.night(p.getItemBySlot(EquipmentSlot.HEAD)));REPORT.addProperty("nv_server_active",EngineerSystems.active(p));REPORT.addProperty("nv_server_valid_menu",p.containerMenu.stillValid(p));});phase=70;}
            else if(phase==70){require(EngineerSystems.night(mc.player.getItemBySlot(EquipmentSlot.HEAD))&&mc.player.hasEffect(MobEffects.NIGHT_VISION)&&mc.player.getActiveEffectsMap().get(MobEffects.NIGHT_VISION)==null,"Powered virtual night vision without real potion mutation");mc.player.closeContainer();submit(mc,()->{var p=player(mc);p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,500));EngineerSystems.toggle(p.getItemBySlot(EquipmentSlot.HEAD),true);});phase=8;}
            else if(phase==8){require(mc.player.hasEffect(MobEffects.NIGHT_VISION)&&mc.player.getActiveEffectsMap().get(MobEffects.NIGHT_VISION)!=null,"Real potion survives switching virtual optic off");REPORT.addProperty("virtual_night_vision_and_real_potion_preserved",true);submit(mc,()->{var p=player(mc);p.removeEffect(MobEffects.NIGHT_VISION);var level=p.serverLevel();for(int x=39;x<=42;x++)for(int z=39;z<=42;z++)for(int y=200;y<=204;y++)level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),3);p.teleportTo(level,40.5,200,40.5,Set.of(),0,0);p.hasChangedDimension();p.setAirSupply(73);var helmet=p.getItemBySlot(EquipmentSlot.HEAD);var cell=EngineerModules.get(helmet,0);EngineerModules.energy(cell,0);EngineerModules.set(helmet,0,cell);waterReserve=EngineerModules.reserve(EngineerModules.get(p.getItemBySlot(EquipmentSlot.CHEST),0));waterStart=level.getGameTime();});phase=9;}
            else if(phase==9){if(mc.level.getGameTime()-waterStart<45)return;submit(mc,()->{var p=player(mc);waterAir=p.getAirSupply();int reserve=EngineerModules.reserve(EngineerModules.get(p.getItemBySlot(EquipmentSlot.CHEST),0));require(waterAir<=73&&waterAir>=65&&reserve<waterReserve&&reserve>=waterReserve-120,"Actual vanilla air mixin holds old air and spends real reserve underwater");REPORT.addProperty("real_underwater_breathing_without_battery",true);REPORT.addProperty("held_vanilla_air",waterAir);REPORT.addProperty("underwater_reserved_ticks_spent",waterReserve-reserve);});phase=10;}
            else if(phase==10){require(!EngineerClient.active(),"Battery empty disables the electronic visor");shot(mc,"mechanical-breathing-empty-battery");submit(mc,()->{var p=player(mc);var chest=p.getItemBySlot(EquipmentSlot.CHEST);var cassette=EngineerModules.get(chest,0);EngineerModules.reserve(cassette,0);EngineerModules.set(chest,0,cassette);waterAir=p.getAirSupply();waterStart=p.level().getGameTime();});phase=11;}
            else if(phase==11){if(mc.level.getGameTime()-waterStart<35)return;submit(mc,()->{var p=player(mc);require(p.getAirSupply()<waterAir,"Depleted cassette restores ordinary drowning budget");int before=p.getAirSupply();EngineerModules.set(p.getItemBySlot(EquipmentSlot.CHEST),0,new ItemStack(EngineerArmor.CASSETTE.get()));require(p.getAirSupply()==before,"Swapping a fresh cassette cannot create a lungful");REPORT.addProperty("depletion_and_swaps_never_refill_air",true);p.teleportTo(p.serverLevel(),32.5,200,30.5,Set.of(),0,25);p.hasChangedDimension();var head=p.getItemBySlot(EquipmentSlot.HEAD);var cell=EngineerModules.get(head,0);EngineerModules.energy(cell,100000);EngineerModules.set(head,0,cell);p.serverLevel().setBlock(new BlockPos(32,200,33),pro.erez.interstice.agriculture.RealmAgriculture.RETORT.get().defaultBlockState(),3);p.serverLevel().setBlock(new BlockPos(31,200,32),RealmGear.BEACON.get().defaultBlockState(),3);var indicator=new ItemStack(Interstice.TIDE_INDICATOR.get());p.setItemSlot(EquipmentSlot.OFFHAND,indicator);require(RouteMarkers.bind(p,indicator,(RouteBeaconEntity)p.serverLevel().getBlockEntity(new BlockPos(31,200,32))),"Bind a real known route marker");});phase=12;}
            else if(phase==12){var field=EngineerClient.class.getDeclaredField("status");field.setAccessible(true);var status=(EngineerNetworking.Status)field.get(null);require(status!=null&&!status.diagnostic().getString().isEmpty()&&!status.route().getString().isEmpty(),"Actual server machine diagnostic and owned marker arrive in visor HUD");REPORT.addProperty("machine_and_paired_marker_hud",true);shot(mc,"diagnostic-and-marker");submit(mc,()->coldSave(mc));phase=13;}
            else if(phase==13){shot(mc,"ready-for-cold-save");finish(mc,true,"");}
        }catch(Throwable error){error.printStackTrace();if(mc.level!=null)shot(mc,"failure");finish(mc,false,error.toString());}
    }
    @SubscribeEvent public static void server(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        if(!ACTIVE||!closing||finished)return;settling.tick(event.getServer());
        if(settling.failed()){REPORT.addProperty("passed",false);REPORT.addProperty("reason",settling.failure());finished=true;var mc=Minecraft.getInstance();write(mc);mc.stop();}
        else if(settling.ready()){REPORT.add("settled",settling.report());finished=true;var mc=Minecraft.getInstance();write(mc);mc.stop();}
    }
}
