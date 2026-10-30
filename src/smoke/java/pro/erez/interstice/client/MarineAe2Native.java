package pro.erez.interstice.client;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.*;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.DriveBlockEntity;
import com.google.gson.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandWorld;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Disposable full-client AE2/Sable compatibility probe, never a user world. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class MarineAe2Native {
    static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("marineae2")&&Set.of("create","reload").contains(System.getProperty("interstice.marinePhase","create"));
    static final String MODE=System.getProperty("interstice.marinePhase","create");
    static final JsonObject REPORT=new JsonObject();
    static boolean started,closing;static int phase,ticks,quiet;static long deadline;
    static ServerLevel world;static ServerSubLevel ship;static BlockPos drivePos;
    static CompletableFuture<?> work;static CompletableFuture<ServerSubLevel> assembly;static Vector3d start;static JsonObject prior;
    static NativeChunkSettler.Session settling;
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){if(ACTIVE&&!closing&&settling!=null)settling.tick(event.getServer());}
    static Minecraft mc(){return Minecraft.getInstance();}
    static Path proof(String mode){return mc().gameDirectory.toPath().resolve("marine-"+mode+".json");}
    static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayers().getFirst();}
    static void submit(Runnable r){work=mc().getSingleplayerServer().submit(r);}
    static void write(){try{REPORT.addProperty("phase",phase);REPORT.addProperty("owner_profile_used",false);Files.writeString(proof(MODE),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    static void finish(boolean ok,String error){REPORT.addProperty("passed",ok);REPORT.addProperty("error",error);write();closing=true;if(mc().level!=null)mc().level.disconnect();mc().disconnect(new TitleScreen());}
    static Item item(String id){var i=BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:"+id));need(i!=Items.AIR,"Missing AE2 item "+id);return i;}
    static net.minecraft.world.level.block.state.BlockState block(String id){var b=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:"+id));need(b!=Blocks.AIR,"Missing AE2 block "+id);return b.defaultBlockState();}
    static void findDrive(){var b=ship.getPlot().getBoundingBox();for(int x=b.minX();x<=b.maxX();x++)for(int y=b.minY();y<=b.maxY();y++)for(int z=b.minZ();z<=b.maxZ();z++){var p=new BlockPos(x,y,z);if(world.getBlockEntity(p) instanceof DriveBlockEntity){drivePos=p;return;}}throw new IllegalStateException("Drive missing after Sable relocation");}
    static IPart terminal(int n){return PartHelper.getPart(world,drivePos.offset(n==0?1:3,0,0),Direction.NORTH);}
    static IGrid grid(){return ((DriveBlockEntity)world.getBlockEntity(drivePos)).getMainNode().getGrid();}
    static void check(long expected){var g=grid();need(g!=null&&g.getEnergyService().isNetworkPowered(),"AE2 network offline");for(int i=0;i<2;i++){var t=terminal(i);need(t!=null&&t.getGridNode()!=null&&t.getGridNode().isActive(),"Terminal inactive "+i);need(t.getGridNode().getGrid()==g,"Terminals do not share storage");}long actual=g.getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(Items.DIAMOND));need(actual==expected,"ME contents changed: expected "+expected+", got "+actual);REPORT.addProperty("diamond_count",actual);REPORT.addProperty("network_powered",true);REPORT.addProperty("two_terminals_one_grid",true);}
    static void create(){
        world=mc().getSingleplayerServer().getLevel(IslandWorld.TENSION_WORLD);need(world!=null,"V6 absent");
        int deckY=-1;String firstObstruction="";
        for(int y=38;y<world.getMaxBuildHeight()-12;y++){
            boolean clear=true;
            for(int x=40;x<=48&&clear;x++)for(int z=12;z<=18&&clear;z++)for(int dy=0;dy<=1;dy++){
                var at=new BlockPos(x,y+dy,z);world.getChunkAt(at);var state=world.getBlockState(at);
                if(!state.isAir()){clear=false;if(firstObstruction.isEmpty())firstObstruction=at+" "+state;break;}
            }
            if(clear){deckY=y;break;}
        }
        need(deckY>=0,"No clear two-layer construction site; first obstruction "+firstObstruction);
        REPORT.addProperty("construction_deck_y",deckY);REPORT.addProperty("first_candidate_obstruction",firstObstruction);
        var p=player();p.setGameMode(GameType.CREATIVE);p.teleportTo(world,44,deckY+5,10,Set.of(),0,15);p.getAbilities().flying=true;p.onUpdateAbilities();
        var positions=new ArrayList<BlockPos>();
        for(int x=40;x<=48;x++)for(int z=12;z<=18;z++){var at=new BlockPos(x,deckY,z);world.setBlock(at,Blocks.OAK_PLANKS.defaultBlockState(),3);positions.add(at);}
        var core=new BlockPos(43,deckY+1,14);drivePos=core;
        for(var entry:Map.of(core,block("drive"),core.west(),block("creative_energy_cell"),core.south(),block("controller")).entrySet()){world.setBlock(entry.getKey(),entry.getValue(),3);positions.add(entry.getKey());}
        ((DriveBlockEntity)world.getBlockEntity(core)).getInternalInventory().setItemDirect(0,new ItemStack(item("item_storage_cell_64k")));
        for(int x=44;x<=46;x++){var at=new BlockPos(x,deckY+1,14);PartHelper.setPart(world,at,null,p,(IPartItem<?>)item("fluix_glass_cable"));positions.add(at);}
        for(int x:new int[]{44,46})PartHelper.setPart(world,new BlockPos(x,deckY+1,14),Direction.NORTH,p,(IPartItem<?>)item("terminal"));
        REPORT.addProperty("source_blocks",positions.size());
        var barriers=positions.stream().map(at->new ChunkPos(at)).distinct().map(cp->world.getChunkSource().getLightEngine().waitForPendingTasks(cp.x,cp.z)).toArray(CompletableFuture[]::new);
        final int baseY=deckY;
        assembly=CompletableFuture.allOf(barriers).thenApplyAsync(unused->{var result=SubLevelAssemblyHelper.assembleBlocks(world,new BlockPos(44,baseY,15),positions,new BoundingBox3i(40,baseY,12,48,baseY+1,18));need(result!=null,"Sable assembly rejected AE2 raft");result.setName("NE01 AE2 isolated compatibility probe");return result;},world.getServer());
    }
    static void open(int index){var at=ship.logicalPose().transformPosition(new Vector3d(drivePos.getX()+(index==0?1:3)+.5,drivePos.getY()+.5,drivePos.getZ()-.8),new Vector3d());var p=player();p.closeContainer();p.teleportTo(world,at.x,at.y,at.z,Set.of(),0,0);p.getAbilities().flying=true;p.onUpdateAbilities();terminal(index).onUseWithoutItem(p,Vec3.atCenterOf(drivePos.offset(index==0?1:3,0,0)));REPORT.addProperty("menu_server_"+index,p.containerMenu.getClass().getName());need(p.containerMenu.getClass().getName().startsWith("appeng."),"Native AE2 menu did not open");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!ACTIVE)return;try{
        if(closing){if(mc().level==null&&mc().screen instanceof TitleScreen&&++quiet>30)mc().stop();return;}
        if(!started&&mc().screen instanceof TitleScreen&&mc().getOverlay()==null){started=true;deadline=System.nanoTime()+600_000_000_000L;REPORT.addProperty("mode",MODE);REPORT.addProperty("started_utc",java.time.Instant.now().toString());need(net.neoforged.fml.ModList.get().isLoaded("distanthorizons"),"DH required");REPORT.addProperty("dh_loaded",true);
            if(MODE.equals("reload")){prior=JsonParser.parseString(Files.readString(proof("create"))).getAsJsonObject();need(prior.get("passed").getAsBoolean(),"Prior create probe failed");mc().createWorldOpenFlows().openWorld("marine-ae2-owned",()->{});}
            else{need(!Files.exists(mc().gameDirectory.toPath().resolve("saves/marine-ae2-owned")),"New disposable profile required");var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc().createWorldOpenFlows().createFreshLevel("marine-ae2-owned",new LevelSettings("NE01 AE2 probe",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261029,false,false),WorldPresets::createNormalWorldDimensions,mc().screen);}return;}
        if(!started||SmokeWorldPrompts.advance(mc())||mc().player==null||mc().level==null||mc().getSingleplayerServer()==null)return;
        need(System.nanoTime()<deadline,"Marine probe deadline at phase "+phase);
        if(work!=null&&!work.isDone())return;if(work!=null){work.join();work=null;}
        if(assembly!=null){if(!assembly.isDone())return;ship=assembly.join();assembly=null;submit(()->{findDrive();REPORT.addProperty("ship_uuid",ship.getUniqueId().toString());});return;}
        if(phase==0){if(mc().screen!=null)return;submit(()->{if(MODE.equals("reload")){world=mc().getSingleplayerServer().getLevel(IslandWorld.TENSION_WORLD);need(player().serverLevel()==world,"Cold player did not return to V6");REPORT.addProperty("cold_login_dimension",world.dimension().location().toString());}else create();});phase=1;ticks=0;write();return;}
        if(phase==1){
            if(ship==null&&MODE.equals("reload")){if(++ticks%20==0)submit(()->{ship=SubLevelContainer.getContainer(world).getAllSubLevels().stream().filter(s->s.getUniqueId().toString().equals(prior.get("ship_uuid").getAsString())).findFirst().orElse(null);REPORT.addProperty("cold_loaded_bodies",SubLevelContainer.getContainer(world).getAllSubLevels().size());REPORT.addProperty("cold_wait_client_ticks",ticks);if(ship!=null){findDrive();REPORT.addProperty("ship_uuid",ship.getUniqueId().toString());}write();});return;}
            if(ship==null||++ticks<160)return;submit(()->{need(grid()!=null,"Grid absent after assembly");if(!MODE.equals("reload")){long inserted=grid().getStorageService().getInventory().insert(AEItemKey.of(Items.DIAMOND),321,Actionable.MODULATE,IActionSource.empty());need(inserted==321,"ME insert failed");}check(MODE.equals("reload")?336:321);start=new Vector3d(ship.logicalPose().position());open(0);});phase=2;ticks=0;return;}
        if(phase==2){if(++ticks<40)return;need(mc().screen!=null&&mc().screen.getClass().getName().startsWith("appeng."),"Client did not receive AE2 screen");REPORT.addProperty("native_screen_before_move",mc().screen.getClass().getName());Screenshot.grab(mc().gameDirectory,"marine-"+MODE+"-terminal-1.png",mc().getMainRenderTarget(),m->{});mc().setScreen(null);submit(()->{player().closeContainer();var handle=RigidBodyHandle.of(ship);handle.teleport(new Vector3d(ship.logicalPose().position()).add(40,0,0),ship.logicalPose().orientation());});phase=3;ticks=0;return;}
        if(phase==3){if(++ticks<100)return;submit(()->{double distance=ship.logicalPose().position().distance(start);need(distance>30,"Body pose did not move across chunk boundaries");REPORT.addProperty("controlled_pose_distance",distance);check(MODE.equals("reload")?336:321);open(1);});phase=4;ticks=0;return;}
        if(phase==4){if(++ticks<40)return;need(mc().screen!=null&&mc().screen.getClass().getName().startsWith("appeng."),"Client terminal after movement absent");Screenshot.grab(mc().gameDirectory,"marine-"+MODE+"-terminal-2.png",mc().getMainRenderTarget(),m->{});mc().setScreen(null);submit(()->{player().closeContainer();if(!MODE.equals("reload")){var inv=grid().getStorageService().getInventory();need(inv.insert(AEItemKey.of(Items.DIAMOND),79,Actionable.MODULATE,IActionSource.empty())==79,"Second insert failed");need(inv.extract(AEItemKey.of(Items.DIAMOND),64,Actionable.MODULATE,IActionSource.empty())==64,"Extraction failed");}check(336);try{WaveShipFixture.flush(world);}catch(Exception e){throw new IllegalStateException(e);}REPORT.addProperty("save_flush_returned",true);});phase=5;ticks=0;return;}
        if(phase==5){if(++ticks<40)return;submit(()->{settling=new NativeChunkSettler.Session("Marine AE2 clean save",1200,true);});phase=6;return;}
        if(phase==6){need(!settling.failed(),settling.failure());if(!settling.ready())return;REPORT.add("final_chunk_settlement",settling.report());REPORT.addProperty("controlled_movement_not_propulsion_test",true);REPORT.addProperty("native_part_api_not_player_raytrace",true);REPORT.addProperty("two_real_clients_tested",false);finish(true,"");}
    }catch(Throwable e){e.printStackTrace();finish(false,e.toString());}}
}
