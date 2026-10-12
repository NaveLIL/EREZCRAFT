package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.seibel.distanthorizons.api.DhApi;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.block.entity.LootrBarrelBlockEntity;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.navigation.StructureSearchJobs;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.*;

/** Bounded natural FULL census, actual client menu packets, then a separate-JVM cold read. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class EtherealStructureVisualSmoke {
    private static final String MODE=System.getProperty("interstice.etherealSmoke","");
    private static final String WORLD="ethereal-natural-v6-20261006";
    private static final UUID SECOND=UUID.nameUUIDFromBytes("interstice-ethereal-lootr-probe-b".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final long SEED=20261006;
    private static boolean started,finished,shotSaved,closing;
    private static int stage,ticks,image;
    private static long deadline;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static List<StructureSearchJobs.Result> found=List.of();
    private static BlockPos cache;
    private static JsonObject data=new JsonObject();
    @SubscribeEvent public static void server(ServerTickEvent.Post event){
        if(MODE.isEmpty())return;
        TideManager.getSavedData(event.getServer()).setPhase(TidePhase.CALM,24000);
        var session=settling;if(session!=null)session.tick(event.getServer());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(!started&&mc.screen instanceof TitleScreen){
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(20);
                require(mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').contains("/.verification/"),"Native structures need an isolated profile");
                mc.getWindow().setTitle("EREZCRAFT - isolated Ethereal verification");
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);mc.options.framerateLimit().set(60);
                mc.options.fov().set(70);mc.options.bobView().set(false);
                require(DhApi.Delayed.configs!=null,"Mandatory DH did not initialize");
                DhApi.Delayed.configs.graphics().chunkRenderDistance().setValue(32);
                DhApi.Delayed.configs.multiThreading().threadCount().setValue(1);
                DhApi.Delayed.configs.worldGenerator().enableDistantWorldGeneration().setValue(false);
                data.addProperty("scope","One real integrated client with DH/Lootr, natural V6 FULL chunks; second player is a server-side identity probe, not a second TCP client or human Survival.");
                var collectors=new JsonArray();for(var gc:java.lang.management.ManagementFactory.getGarbageCollectorMXBeans())collectors.add(gc.getName());data.add("garbage_collectors",collectors);
                if(MODE.equals("reload")){
                    data=JsonParser.parseString(Files.readString(report(mc,"create"))).getAsJsonObject();
                    require(data.get("passed").getAsBoolean()&&data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Cold check must follow a passed different JVM");
                    mc.createWorldOpenFlows().openWorld(WORLD,()->{});
                }else{
                    require(MODE.equals("create")&&!Files.exists(mc.gameDirectory.toPath().resolve("saves/"+WORLD)),"Creation requires a fresh world");
                    data.addProperty("creator_pid",ProcessHandle.current().pid());
                    var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Ethereal native proof",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                            new WorldOptions(SEED,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                }return;
            }
            if(!started)return;
            require(System.nanoTime()<deadline,"Native deadline stage="+stage);
            if(closing){
                if(!settling.ready())return;
                data.add(MODE+"_settle",settling.report());data.addProperty("passed",true);data.addProperty("finished",true);write(mc);
                if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());finished=true;mc.stop();return;
            }
            if(SmokeWorldPrompts.advance(mc))return;
            if(mc.player==null||mc.level==null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==0){
                work=server.submit(()->{var level=server.getLevel(IslandWorld.TENSION_WORLD);require(level!=null&&level.getSeed()==SEED,"Wrong actual dimension/seed");
                    if(MODE.equals("reload")){cold(server,level,uuid);return true;}
                    prepare(level);data.addProperty("actual_dimension",level.dimension().location().toString());
                    data.addProperty("actual_revision",((IslandChunkGenerator)level.getChunkSource().getGenerator()).terrainRevision());
                    pose(server.getPlayerList().getPlayer(uuid),level,0);return true;
                });stage=1;ticks=0;return;
            }
            if(stage==1&&done()){
                if(MODE.equals("reload")){finish(mc);return;}
                if(mc.screen!=null||!mc.level.dimension().equals(IslandWorld.TENSION_WORLD))return;
                var target=found.get(image).position();
                if(mc.player.position().distanceToSqr(target.getX()+.5,target.getY()+9,target.getZ()+19.5)>.2||!mc.levelRenderer.isSectionCompiled(target)){ticks=0;return;}
                if(++ticks<100)return;
                require(DhApi.Delayed.worldProxy!=null&&DhApi.Delayed.worldProxy.worldLoaded(),"DH is not attached to V6");
                data.addProperty("distant_horizons_loaded",true);shotSaved=false;
                Screenshot.grab(mc.gameDirectory,"ethereal-natural-"+image+".png",mc.getMainRenderTarget(),msg->{shotSaved=true;});stage=2;return;
            }
            if(stage==2&&shotSaved){
                if(++image<Math.min(5,found.size())){work=server.submit(()->{pose(server.getPlayerList().getPlayer(uuid),server.getLevel(IslandWorld.TENSION_WORLD),image);return true;});stage=1;ticks=0;return;}
                work=server.submit(()->{var level=server.getLevel(IslandWorld.TENSION_WORLD);cache=cache(level,found.getFirst());var p=server.getPlayerList().getPlayer(uuid);
                    p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.onUpdateAbilities();
                    p.teleportTo(level,cache.getX()+.5,cache.getY()+1,cache.getZ()+2.5,Set.of(),180,55);p.setDeltaMovement(0,0,0);p.fallDistance=0;
                    var barrel=(LootrBarrelBlockEntity)level.getBlockEntity(cache);var other=probe(server,level);
                    var b=LootrAPI.getInventory(barrel,other);require(!b.isEmpty(),"B received empty loot");
                    data.addProperty("second_inventory",b.saveToTag(level.registryAccess()).toString());data.addProperty("cache",cache.asLong());
                    data.addProperty("cache_uuid",barrel.getInfoUUID().toString());data.addProperty("first_uuid",uuid.toString());return true;});stage=3;ticks=0;return;
            }
            if(stage==3&&done()){
                if(mc.screen!=null||!mc.level.dimension().equals(IslandWorld.TENSION_WORLD)||++ticks<40)return;
                mc.options.hideGui=false;
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(cache),Direction.UP,cache,false));stage=4;ticks=0;return;
            }
            if(stage==4){
                require(++ticks<300,"Native barrel menu did not open");
                if(mc.player.containerMenu==mc.player.inventoryMenu||mc.screen==null)return;
                int stacks=0;for(int i=0;i<27;i++)if(mc.player.containerMenu.getSlot(i).hasItem())stacks++;
                require(stacks>0,"A native menu was empty");data.addProperty("native_menu_loot_stacks",stacks);
                // ClientTick runs before drawing: wait for actual GUI frames before reading the render target.
                if(ticks<20)return;
                data.addProperty("native_menu_screen",mc.screen.getClass().getName());
                data.addProperty("menu_capture_after_client_ticks",ticks);
                Screenshot.grab(mc.gameDirectory,"ethereal-personal-loot-menu.png",mc.getMainRenderTarget(),msg->{});
                for(int i=0;i<27;i++)if(mc.player.containerMenu.getSlot(i).hasItem())mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,i,0,ClickType.QUICK_MOVE,mc.player);
                stage=5;ticks=0;return;
            }
            if(stage==5&&++ticks>=60){
                mc.player.closeContainer();work=server.submit(()->{var level=server.getLevel(IslandWorld.TENSION_WORLD);var barrel=(LootrBarrelBlockEntity)level.getBlockEntity(cache);
                    var p=server.getPlayerList().getPlayer(uuid);require(LootrAPI.getInventory(barrel,p).isEmpty(),"A did not empty loot through native packets");
                    require(LootrAPI.getInventory(barrel,probe(server,level)).saveToTag(level.registryAccess()).toString().equals(data.get("second_inventory").getAsString()),"A took B's loot");
                    data.addProperty("native_item_take_packets",true);data.addProperty("second_player_inventory_preserved",true);removeProbe(server);return true;
                });stage=6;return;
            }
            if(stage==6&&done()){finish(mc);}
        }catch(Throwable failure){data.addProperty("passed",false);data.addProperty("error",failure.toString());write(mc);failure.printStackTrace();finished=true;mc.stop();}
    }
    private static void prepare(ServerLevel level){
        var candidates=new ArrayList<RealmStructuresV5.Slot>();
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){var slot=RealmStructuresV6.slot(SEED,x,z);if(slot!=null&&slot.template().getPath().startsWith("ethereal/"))candidates.add(slot);}
        candidates.sort(Comparator.comparingLong(s->s.chunk().toLong()));
        var results=new ArrayList<StructureSearchJobs.Result>();var rows=new JsonArray();int requested=0,numeric=0;
        for(var slot:candidates){
            numeric++;if(!plausible(level,slot))continue;
            if(requested>=32)break;requested++;
            System.out.println("ETHEREAL_NATURAL_REQUEST "+requested+"/32 template="+slot.template()+" chunk="+slot.chunk());
            level.getChunk(slot.chunk().x,slot.chunk().z);
            var actual=StructureSearchJobs.inspectLoaded(level,slot);if(actual==null)continue;
            var barrel=cache(level,actual);require(level.getBlockEntity(barrel) instanceof LootrBarrelBlockEntity,"Actual natural V6 cache is not Lootr");
            results.add(actual);var row=new JsonObject();row.addProperty("template",actual.template().toString());row.addProperty("position",actual.position().asLong());row.addProperty("cache",barrel.asLong());
            row.addProperty("chunk_x",slot.chunk().x);row.addProperty("chunk_z",slot.chunk().z);rows.add(row);
            if(results.size()>=5)break;
        }
        found=List.copyOf(results);data.add("natural_structures",rows);data.addProperty("numeric_proposals_checked",numeric);data.addProperty("actual_full_requests",requested);
        require(results.size()>=3,"Fixed natural census found fewer than three imported ruins; measured="+results.size());
    }
    private static boolean plausible(ServerLevel level,RealmStructuresV5.Slot slot){
        var g=(IslandChunkGenerator)level.getChunkSource().getGenerator();var size=level.getStructureManager().get(slot.template()).orElseThrow().getSize();
        int width=slot.rotation()%2==0?size.getX():size.getZ(),depth=slot.rotation()%2==0?size.getZ():size.getX();
        long key=mix(SEED^slot.chunk().toLong()^slot.template().toString().hashCode());
        int x0=slot.chunk().getMinBlockX()+1+Math.floorMod(mix(key^0x58L),15-width),z0=slot.chunk().getMinBlockZ()+1+Math.floorMod(mix(key^0x5aL),15-depth);
        int low=999,high=-1;
        for(int x=x0;x<x0+width;x++)for(int z=z0;z<z0+depth;z++){
            var column=g.getBaseColumn(x,z,level,level.getChunkSource().randomState());int top=-1;
            for(int y=g.geometry().maxLand();y>=39;y--)if(!column.getBlock(y).isAir()){if(StoneVaults.isGround(column.getBlock(y)))top=y;break;}
            if(top<39)return false;low=Math.min(low,top);high=Math.max(high,top);
        }
        return high-low<=3&&high+size.getY()<=g.geometry().maxLand();
    }
    private static long mix(long n){n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);}
    private static BlockPos cache(ServerLevel level,StructureSearchJobs.Result result){
        for(int x=result.position().getX()-8;x<=result.position().getX()+8;x++)for(int z=result.position().getZ()-8;z<=result.position().getZ()+8;z++)
            for(int y=result.position().getY();y<result.position().getY()+24;y++){
                var pos=new BlockPos(x,y,z);if(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.EntityBlock&&level.getBlockEntity(pos) instanceof LootrBarrelBlockEntity)return pos;
            }
        throw new IllegalStateException("Natural template lacks instantiated cache: "+result.template());
    }
    private static ServerPlayer probe(MinecraftServer server,ServerLevel level){
        var existing=server.getPlayerList().getPlayer(SECOND);if(existing!=null)return existing;
        var cookie=CommonListenerCookie.createInitial(new GameProfile(SECOND,"LootrProbeB"),false);
        var player=new ServerPlayer(server,level,cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);
        if(cache!=null)player.teleportTo(level,cache.getX()+.5,cache.getY()+1,cache.getZ()+3.5,Set.of(),180,0);
        player.hasChangedDimension();return player;
    }
    private static void removeProbe(MinecraftServer server){var player=server.getPlayerList().getPlayer(SECOND);if(player!=null)server.getPlayerList().remove(player);}
    private static void pose(ServerPlayer p,ServerLevel level,int index){
        var result=found.get(index);var pos=result.position();p.setGameMode(GameType.CREATIVE);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
        p.teleportTo(level,pos.getX()+.5,pos.getY()+9,pos.getZ()+19.5,Set.of(),180,18);p.getAbilities().flying=true;p.onUpdateAbilities();p.setDeltaMovement(0,0,0);p.fallDistance=0;
    }
    private static void cold(MinecraftServer server,ServerLevel level,UUID uuid){
        require(data.get("first_uuid").getAsString().equals(uuid.toString()),"Cold client identity changed");
        cache=BlockPos.of(data.get("cache").getAsLong());level.getChunkAt(cache);
        var barrel=(LootrBarrelBlockEntity)level.getBlockEntity(cache);require(barrel!=null&&barrel.getInfoUUID().toString().equals(data.get("cache_uuid").getAsString()),"Cold container UUID changed");
        require(LootrAPI.getInventory(barrel,server.getPlayerList().getPlayer(uuid)).isEmpty(),"Cold restart refilled A");
        require(LootrAPI.getInventory(barrel,probe(server,level)).saveToTag(level.registryAccess()).toString().equals(data.get("second_inventory").getAsString()),"Cold restart lost B's inventory");
        for(var element:data.getAsJsonArray("natural_structures")){
            var row=element.getAsJsonObject();int x=row.get("chunk_x").getAsInt(),z=row.get("chunk_z").getAsInt();level.getChunk(x,z);
            var slot=RealmStructuresV6.candidate(SEED,x,z);require(StructureSearchJobs.inspectLoaded(level,slot)!=null,"Cold native ruin no longer recognized");
        }
        data.addProperty("reload_pid",ProcessHandle.current().pid());data.addProperty("cold_first_stays_empty",true);data.addProperty("cold_second_stays_unclaimed",true);data.addProperty("cold_natural_structures_preserved",true);
        removeProbe(server);
    }
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void finish(Minecraft mc){settling=new NativeChunkSettler.Session("ethereal-"+MODE+"-shutdown",12000);closing=true;}
    private static Path report(Minecraft mc,String mode){return mc.gameDirectory.toPath().resolve("ethereal-"+mode+"-validation.json");}
    private static void write(Minecraft mc){try{Files.writeString(report(mc,MODE),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static void require(boolean condition,String why){if(!condition)throw new IllegalStateException(why);}
}
