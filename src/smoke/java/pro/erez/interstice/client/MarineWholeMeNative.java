package pro.erez.interstice.client;

import appeng.api.config.*;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.menu.me.common.MEStorageMenu;
import com.google.gson.*;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ship.*;
import pro.erez.interstice.worldgen.IslandWorld;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Full authored hull, six real AE2 addon hosts, native ray/packets, finite power. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class MarineWholeMeNative {
    static final String MODE=System.getProperty("interstice.marinePhase","");
    static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("marineae2")&&MODE.startsWith("wholeme-");
    static final boolean RELOAD=MODE.equals("wholeme-reload");
    static final JsonObject REPORT=new JsonObject();static final JsonArray MENUS=new JsonArray();
    static boolean started,closing;static int phase,ticks,quiet,index,round;static long deadline;
    static ServerLevel world;static ServerSubLevel ship;static BlockPos origin;
    static CompletableFuture<?> work;static CompletableFuture<ServerSubLevel> assembly;static NativeChunkSettler.Session settling;static JsonObject prior;
    static Vector3d moveBefore;static long readyAt,waitUntil;
    static Minecraft mc(){return Minecraft.getInstance();}
    static String mode(){return RELOAD?"reload":"create";}
    static Path proof(String mode){return mc().gameDirectory.toPath().resolve("marine-fullme-"+mode+".json");}
    static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayers().getFirst();}
    static void submit(Runnable r){var server=mc().getSingleplayerServer();work=work==null?server.submit(r):work.thenRunAsync(r,server);}
    static void write(){try{REPORT.addProperty("phase",phase);REPORT.addProperty("layout_revision",MarineData.REVISION);REPORT.addProperty("geometry_sha256",MarineData.GEOMETRY_SHA256);REPORT.addProperty("owner_profile_used",false);REPORT.add("native_menus",MENUS);Files.writeString(proof(mode()),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    static void finish(boolean ok,String error){KeyMapping.releaseAll();REPORT.addProperty("passed",ok);REPORT.addProperty("error",error);write();closing=true;if(mc().level!=null)mc().level.disconnect();mc().disconnect(new TitleScreen());}
    static MarineGeneratorEntity generator(){return (MarineGeneratorEntity)world.getBlockEntity(origin.offset(MarineMe.GENERATOR));}
    static IGrid grid(){return generator().getMainNode().getGrid();}
    static void diagnostics(){
        var out=new JsonObject();var core=generator();out.addProperty("server_tick",world.getGameTime());out.addProperty("ready_at",readyAt);out.addProperty("generator_energy",core.getAECurrentPower());out.addProperty("generator_burn",core.burnTicks());out.addProperty("generator_fuel",core.getInternalInventory().getStackInSlot(0).getCount());out.addProperty("generator_ticks",core.generatedTicks());
        var n=core.getMainNode();out.addProperty("core_ready",n.isReady());out.addProperty("core_powered",n.isPowered());out.addProperty("core_booted",n.hasGridBooted());out.addProperty("core_connections",n.getNode()==null?-1:n.getNode().getConnections().size());var nodes=new JsonArray();
        for(var p:MarineMe.TERMINALS){var e=world.getBlockEntity(origin.offset(p));var a=new JsonObject();a.addProperty("position",p.toShortString());if(e instanceof appeng.me.helpers.IGridConnectedBlockEntity host){a.addProperty("ready",host.getMainNode().isReady());a.addProperty("active",host.getMainNode().isActive());a.addProperty("powered",host.getMainNode().isPowered());a.addProperty("booted",host.getMainNode().hasGridBooted());}nodes.add(a);}out.add("terminals",nodes);REPORT.add("grid_diagnostics",out);
    }
    static void setupHelpers(){MarineVesselNative.world=world;MarineVesselNative.ship=ship;MarineVesselNative.locate();origin=MarineVesselNative.origin;}
    static void face(int terminal){
        var c=MarineMe.SCREENS[terminal];double deck=terminal==0?6.95:terminal<4?4.2:1.5;
        double x=terminal==0?0:terminal<4?.35:0;
        var a=MarineVesselNative.global(x,deck+.10,c[2]);var b=MarineVesselNative.global(c[0],c[1],c[2]);
        double dx=b.x-a.x,dy=b.y-(a.y+player().getEyeHeight()),dz=b.z-a.z;
        MarineVesselNative.at(x,deck+.10,c[2],(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))),true);
    }
    static void contents(long diamonds,long gold){
        var inventory=grid().getStorageService().getInventory().getAvailableStacks();
        if(!grid().getEnergyService().isNetworkPowered()){
            // AE2 may unmount offline providers. Inspect the actual cell storage
            // rather than expecting an unpowered network to expose its contents.
            inventory=new appeng.api.stacks.KeyCounter();
            for(var p:List.of(MarineMe.TERMINALS.get(5),MarineMe.SECOND_DRIVE)){var drive=(MarineDriveEntity)world.getBlockEntity(origin.offset(p));for(int i=0;i<drive.getCellCount();i++){var cell=drive.getOriginalCellInventory(i);if(cell!=null)cell.getAvailableStacks(inventory);}}
            REPORT.addProperty("offline_count_source","native AE2 cells");
        }
        need(inventory.get(AEItemKey.of(Items.DIAMOND))==diamonds,"Diamond total changed");
        need(inventory.get(AEItemKey.of(Items.GOLD_INGOT))==gold,"Gold total changed");
        REPORT.addProperty("diamonds",diamonds);REPORT.addProperty("gold_ingots",gold);
    }
    static void shared(){
        diagnostics();need(grid()!=null&&grid().getEnergyService().isNetworkPowered(),"Full-vessel ME network offline");
        for(var p:MarineMe.TERMINALS){var e=world.getBlockEntity(origin.offset(p));var node=e instanceof MarineTerminalEntity t?t.getMainNode():((MarineDriveEntity)e).getMainNode();need(node.isActive()&&node.getGrid()==grid(),"Terminal outside common powered grid: "+p);}
        REPORT.addProperty("six_terminals_same_grid",true);
    }
    static void shot(String id){Screenshot.grab(mc().gameDirectory,"ne01-fullme-"+mode()+"-"+id+".png",mc().getMainRenderTarget(),m->{});}
    static void close(){mc().setScreen(null);submit(()->player().closeContainer());}
    static void click(){
        need(mc().hitResult instanceof BlockHitResult,"Player ray did not hit a block");var hit=(BlockHitResult)mc().hitResult;
        var state=mc().level.getBlockState(hit.getBlockPos());need(state.getBlock() instanceof MarineBlock,"Ray picked foreign geometry");
        var cell=MarineData.cell(state.getValue(MarineData.CELL));need(hit.getBlockPos().subtract(cell.pos()).equals(origin),"Ray picked a different hull");
        var local=hit.getLocation().subtract(origin.getX(),origin.getY(),origin.getZ());need(MarineMe.screenAt(local)==index,"Ray missed visible screen "+index+" at "+local);
        mc().gameMode.useItemOn(mc().player,InteractionHand.MAIN_HAND,hit);
    }
    static void shiftGold(){int slot=-1;for(int i=0;i<mc().player.containerMenu.slots.size();i++)if(mc().player.containerMenu.slots.get(i).getItem().is(Items.GOLD_INGOT)){slot=i;break;}need(slot>=0,"Native player gold slot absent");mc().gameMode.handleInventoryMouseClick(mc().player.containerMenu.containerId,slot,0,ClickType.QUICK_MOVE,mc().player);}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){if(!ACTIVE||closing)return;try{
        if(world==null){world=e.getServer().getLevel(IslandWorld.TENSION_WORLD);if(world!=null)WaveShipFixture.pause(world);}
        if(settling!=null)settling.tick(e.getServer());
    }catch(Exception failure){throw new IllegalStateException(failure);}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!ACTIVE)return;try{
        if(closing){if(mc().level==null&&mc().screen instanceof TitleScreen&&++quiet>30)mc().stop();return;}
        if(!started&&mc().screen instanceof TitleScreen&&mc().getOverlay()==null){started=true;deadline=System.nanoTime()+900_000_000_000L;REPORT.addProperty("mode",mode());REPORT.addProperty("started_utc",java.time.Instant.now().toString());need(net.neoforged.fml.ModList.get().isLoaded("distanthorizons"),"DH required");REPORT.addProperty("dh_loaded",true);REPORT.addProperty("controlled_paused_body_not_propulsion",true);REPORT.addProperty("two_real_clients",false);REPORT.addProperty("native_ae2_addon_hosts",true);
            if(RELOAD){prior=JsonParser.parseString(Files.readString(proof("create"))).getAsJsonObject();need(prior.get("passed").getAsBoolean()&&prior.get("process_exit_code").getAsInt()==0,"Clean prior full ME proof required");need(prior.has("layout_revision")&&prior.get("layout_revision").getAsString().equals(MarineData.REVISION)&&prior.has("geometry_sha256")&&prior.get("geometry_sha256").getAsString().equals(MarineData.GEOMETRY_SHA256),"Historical NE01 layout cannot be reopened with changed cell IDs");mc().createWorldOpenFlows().openWorld("marine-full-me-owned",()->{});}
            else{need(!Files.exists(mc().gameDirectory.toPath().resolve("saves/marine-full-me-owned")),"New disposable world required");var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc().createWorldOpenFlows().createFreshLevel("marine-full-me-owned",new LevelSettings("NE01 full native ME",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261029,false,false),WorldPresets::createNormalWorldDimensions,mc().screen);}return;}
        if(!started||SmokeWorldPrompts.advance(mc())||mc().player==null||mc().level==null||mc().getSingleplayerServer()==null)return;
        need(System.nanoTime()<deadline,"Full ME deadline "+phase);mc().mouseHandler.releaseMouse();
        if(work!=null&&!work.isDone())return;if(work!=null){work.join();work=null;}
        if(assembly!=null){if(!assembly.isDone())return;ship=assembly.join();assembly=null;submit(()->{setupHelpers();REPORT.addProperty("ship_uuid",ship.getUniqueId().toString());MarineVesselNative.allOpen();((MarineDriveEntity)world.getBlockEntity(origin.offset(MarineMe.TERMINALS.get(5)))).getInternalInventory().setItemDirect(0,new ItemStack(MarineMe.item("item_storage_cell_64k")));need(generator().addFuel(new ItemStack(Items.COAL,64),false),"Finite fuel insertion failed");readyAt=world.getGameTime()+160;});return;}
        if(phase==0){if(mc().screen!=null)return;submit(()->{
            player().setGameMode(GameType.CREATIVE);player().getInventory().selected=8;player().getInventory().setItem(8,ItemStack.EMPTY);player().addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,200000,0,false,false));
            if(!RELOAD){BlockPos site=null;for(int y=100;y<world.getMaxBuildHeight()-18&&site==null;y+=12){var p=new BlockPos(32,y,16);boolean clear=true;for(var c:MarineData.CELLS){world.getChunkAt(p.offset(c.pos()));if(!world.getBlockState(p.offset(c.pos())).isAir()){clear=false;break;}}if(clear)site=p;}need(site!=null,"No clear full ME drydock");player().teleportTo(world,site.getX()+22,site.getY()+18,site.getZ()+28,Set.of(),140,20);player().getAbilities().flying=true;player().onUpdateAbilities();assembly=MarineAssembly.create(world,site);}
        });phase=1;ticks=0;write();return;}
        if(phase==1){
            if(ship==null&&RELOAD){if(++ticks%20==0)submit(()->{ship=SubLevelContainer.getContainer(world).getAllSubLevels().stream().filter(s->s.getUniqueId().toString().equals(prior.get("ship_uuid").getAsString())).findFirst().orElse(null);if(ship!=null){setupHelpers();REPORT.addProperty("ship_uuid",ship.getUniqueId().toString());readyAt=world.getGameTime()+160;}});return;}
            if(ship==null)return;if(world.getGameTime()<readyAt){if(++ticks%40==0)submit(()->{diagnostics();write();});return;}submit(()->{shared();if(!RELOAD){need(grid().getStorageService().getInventory().insert(AEItemKey.of(Items.DIAMOND),321,Actionable.MODULATE,IActionSource.empty())==321,"Native drive insert failed");player().getInventory().setItem(9,new ItemStack(Items.GOLD_INGOT,64));}contents(RELOAD?336:321,RELOAD?64:0);REPORT.addProperty("finite_power_ae",generator().getAECurrentPower());MarineVesselNative.camera(22,14,26,0,6,0);});mc().options.hideGui=true;phase=90;ticks=0;return;
        }
        if(phase==90){if(++ticks<60)return;shot("exterior-inspection-night-vision");submit(()->MarineVesselNative.camera(0,7.05,-2.8,0,7.9,2));phase=91;ticks=0;return;}
        if(phase==91){if(++ticks<50)return;shot("bridge-inspection-night-vision");mc().options.hideGui=false;submit(()->face(0));phase=2;ticks=0;return;}
        if(phase==2){if(++ticks<40)return;click();phase=3;ticks=0;return;}
        if(phase==3){if(++ticks<40)return;need(mc().screen!=null&&mc().screen.getClass().getName().startsWith("appeng."),"Native terminal screen absent "+index);submit(()->{need(player().containerMenu instanceof MEStorageMenu menu&&menu.getGridNode().getGrid()==grid(),"Native menu did not use common grid");});var entry=new JsonObject();entry.addProperty("index",index);entry.addProperty("round",round);entry.addProperty("screen",mc().screen.getClass().getName());entry.addProperty("player_ray_picked_native_cell",true);MENUS.add(entry);shot("terminal-"+round+"-"+index);
            if(!RELOAD&&round==0&&index==0){shiftGold();phase=4;ticks=0;}else{close();phase=5;ticks=0;}return;
        }
        if(phase==4){if(++ticks<40)return;submit(()->{contents(321,64);need(player().getInventory().getItem(9).isEmpty(),"Native quick-move duplicated gold");REPORT.addProperty("native_shift_click_insertion",true);});close();phase=5;ticks=0;return;}
        if(phase==5){if(++ticks<20)return;if(++index<6){submit(()->face(index));phase=2;ticks=0;return;}if(round==0){submit(()->{moveBefore=new Vector3d(ship.logicalPose().position());RigidBodyHandle.of(ship).teleport(new Vector3d(moveBefore).add(40,0,0),ship.logicalPose().orientation());});round=1;index=0;phase=6;ticks=0;return;}
            submit(()->{shared();var storage=grid().getStorageService().getInventory();if(!RELOAD){need(storage.insert(AEItemKey.of(Items.DIAMOND),79,Actionable.MODULATE,IActionSource.empty())==79,"Second insert failed");need(storage.extract(AEItemKey.of(Items.DIAMOND),64,Actionable.MODULATE,IActionSource.empty())==64,"Extraction failed");}contents(336,64);generator().enabled(false);grid().getEnergyService().extractAEPower(1e9,Actionable.MODULATE,PowerMultiplier.ONE);});phase=7;ticks=0;return;
        }
        if(phase==6){if(++ticks<80)return;submit(()->{double distance=ship.logicalPose().position().distance(moveBefore);need(distance>30,"Paused body did not process controlled teleport");REPORT.addProperty("controlled_move_distance",distance);shared();face(0);});phase=2;ticks=0;return;}
        if(phase==7){if(++ticks<100)return;submit(()->{need(!grid().getEnergyService().isNetworkPowered(),"Generator off did not depower grid");contents(336,64);player().getInventory().setItem(9,new ItemStack(Items.GOLD_INGOT,1));face(0);REPORT.addProperty("induced_battery_drain",true);REPORT.addProperty("power_loss_preserved_items",true);});index=0;phase=8;ticks=0;return;}
        if(phase==8){if(++ticks<40)return;click();phase=9;ticks=0;return;}
        if(phase==9){if(++ticks<40)return;need(mc().screen!=null&&mc().screen.getClass().getName().startsWith("appeng."),"Offline terminal UI absent");shiftGold();phase=10;ticks=0;return;}
        if(phase==10){if(++ticks<40)return;submit(()->{contents(336,64);need(player().getInventory().getItem(9).getCount()==1,"Offline terminal accepted a transfer");REPORT.addProperty("offline_native_transfer_rejected",true);generator().enabled(true);});close();phase=11;ticks=0;return;}
        if(phase==11){if(++ticks<120)return;submit(()->{shared();contents(336,64);need(generator().generatedTicks()>0&&generator().getInternalInventory().getStackInSlot(0).getCount()<64,"Generator did not consume finite fuel");REPORT.addProperty("native_menu_opens",MENUS.size());REPORT.addProperty("finite_fuel_remaining",generator().getInternalInventory().getStackInSlot(0).getCount());REPORT.addProperty("generator_burn_ticks",generator().burnTicks());REPORT.addProperty("stored_energy_ae",generator().getAECurrentPower());try{WaveShipFixture.flush(world);}catch(Exception e){throw new IllegalStateException(e);}settling=new NativeChunkSettler.Session("NE01 full ME final save",1600,true);});phase=12;return;}
        if(phase==12){need(!settling.failed(),settling.failure());if(!settling.ready())return;REPORT.add("chunk_settlement",settling.report());finish(true,"");}
    }catch(Throwable e){e.printStackTrace();finish(false,e.toString());}}
}
