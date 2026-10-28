package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.armortrim.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.equipment.*;
import pro.erez.interstice.gear.*;

/** Native photographs of server-connected test players, never armor stands or Blender renders. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class ToolsNative {
    private static final boolean ACTIVE=System.getProperty("interstice.progressionSmoke","").equals("create");
    private static final String[] TIERS={"riftsilver","riftsteel","aegis"};
    private static final String[] NAMES={"EREZ_Silver","EREZ_Steel","EREZ_Magen"};
    private static final double[] X={34.5,32,29.5};
    private static final List<ServerPlayer> ACTORS=new ArrayList<>();
    private static final List<EmbeddedChannel> CHANNELS=new ArrayList<>();
    private static final JsonObject REPORT=new JsonObject();
    private static final JsonArray SHOTS=new JsonArray();
    private static final Map<String,long[]> OLD_VERTICES=new HashMap<>();
    private static UUID observer;
    private static UUID[] profiles;
    private static boolean started,finished,closing;
    private static boolean skinsFixed;
    private static volatile boolean prepared;
    private static volatile int movement;
    private static int phase,ticks;
    private static long deadline;
    private static CompletableFuture<?> job;
    private static volatile NativeChunkSettler.Session settling;
    private ToolsNative(){}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static UUID profile(String texture,int offset){
        for(int i=offset;i<offset+1000;i++){
            UUID id=new UUID(0x1133557700004000L,0x8000000000000000L+i);
            if(DefaultPlayerSkin.get(id).texture().getPath().endsWith(texture))return id;
        }throw new IllegalStateException("No selected native default skin");
    }
    private static String kind(EquipmentSlot s){return switch(s){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    private static final String[] TOOLS={"tether_spool","lift_controller","structure_surveyor"};
    private static net.minecraft.world.item.Item tool(int i){return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,TOOLS[i]));}
    private static void equip(ServerPlayer p,String tier){int i=Arrays.asList(TIERS).indexOf(tier);for(var slot:RealmArmor.SLOTS)p.setItemSlot(slot,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(tool(i)));p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();}
    private static void reset(ServerPlayer p,int i){
        p.stopUsingItem();p.setShiftKeyDown(false);p.setSprinting(false);
        p.setSwimming(false);p.setPos(X[i],200,32);p.setYRot(180);p.setYHeadRot(180);p.yBodyRot=180;p.setXRot(0);p.setDeltaMovement(Vec3.ZERO);
    }
    private static ServerPlayer connect(ServerLevel level,int index){
        var cookie=CommonListenerCookie.createInitial(new GameProfile(profiles[index],NAMES[index]),false);
        var player=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);CHANNELS.add(channel);
        NetworkRegistry.configureMockConnection(connection);level.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.SURVIVAL);player.teleportTo(level,X[index],200,32,Set.of(),180,0);player.hasChangedDimension();
        player.setNoGravity(true);player.setInvulnerable(true);equip(player,TIERS[index]);reset(player,index);return player;
    }
    private static void prepare(ServerPlayer viewer){
        var level=viewer.server.overworld();level.getChunk(1,1);level.getChunk(1,2);level.getChunk(2,1);level.getChunk(2,2);
        level.setDayTime(6000);level.setWeatherParameters(120000,0,false,false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,viewer.server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,viewer.server);
        for(int x=20;x<=44;x++)for(int z=20;z<=44;z++){
            level.setBlock(new BlockPos(x,199,z),(Math.floorMod(x+z,2)==0?Blocks.SMOOTH_STONE:Blocks.POLISHED_ANDESITE).defaultBlockState(),3);
            for(int y=200;y<=205;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(new BlockPos(x,206,z),Blocks.AIR.defaultBlockState(),3);
            if(x==20||x==44||z==20||z==44)for(int y=200;y<206;y++)level.setBlock(new BlockPos(x,y,z),(y==200||y==205?Blocks.POLISHED_DEEPSLATE:Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState(),3);
        }
        viewer.setGameMode(GameType.SPECTATOR);viewer.teleportTo(level,32,199.4,25,Set.of(),0,0);viewer.hasChangedDimension();viewer.setNoGravity(true);
        var board=viewer.server.getScoreboard();var team=board.addPlayerTeam("erez_armor_gallery");team.setNameTagVisibility(Team.Visibility.NEVER);team.setCollisionRule(Team.CollisionRule.NEVER);
        for(int i=0;i<3;i++){var actor=connect(level,i);ACTORS.add(actor);board.addPlayerToTeam(NAMES[i],team);}
        prepared=true;
    }
    @SubscribeEvent public static void server(ServerTickEvent.Post event){
        if(!ACTIVE)return;if(settling!=null)settling.tick(event.getServer());
        if(!prepared||closing)return;
        for(int i=0;i<ACTORS.size();i++){
            var p=ACTORS.get(i);p.connection.tick();
            // These are supported mock client connections; acknowledge ordinary protocol liveness.
            Object packet;
            while((packet=CHANNELS.get(i).readOutbound())!=null){
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket alive)
                    p.connection.handleKeepAlive(new net.minecraft.network.protocol.common.ServerboundKeepAlivePacket(alive.getId()));
                else if(packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket position)
                    p.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(position.getId()));
                io.netty.util.ReferenceCountUtil.release(packet);
            }
            if(movement==1){double z=32+Math.sin(event.getServer().overworld().getGameTime()*.12)*.4;p.move(MoverType.SELF,new Vec3(0,0,z-p.getZ()));}
            p.setYRot(180);p.setYHeadRot(180);p.yBodyRot=180;
        }
    }
    private static void camera(Minecraft mc,double x,double eyeY,double z,float yaw,float pitch){
        var server=mc.getSingleplayerServer();job=server.submit(()->{var p=server.getPlayerList().getPlayer(observer);p.teleportTo(server.overworld(),x,eyeY-1.62,z,Set.of(),yaw,pitch);p.hasChangedDimension();return true;});ticks=0;
    }
    private static void closeCamera(Minecraft mc,int focus){
        var server=mc.getSingleplayerServer();
        job=server.submit(()->{for(int i=0;i<3;i++){reset(ACTORS.get(i),i);if(i!=focus)ACTORS.get(i).setPos(48,200,48);}
            var p=server.getPlayerList().getPlayer(observer);p.teleportTo(server.overworld(),X[focus]-2.4,201.02-1.62,27.8,Set.of(),-29.75F,0);p.hasChangedDimension();return true;});ticks=0;
    }
    private static AbstractClientPlayer actor(Minecraft mc,int i){
        var p=mc.level.getPlayerByUUID(profiles[i]);require(p instanceof AbstractClientPlayer,"Tracked native player missing: "+NAMES[i]);return (AbstractClientPlayer)p;
    }
    private static void inspect(Minecraft mc){
        var players=new JsonArray();
        for(int i=0;i<3;i++){
            var p=actor(mc,i);var row=new JsonObject();row.addProperty("tier",TIERS[i]);row.addProperty("name",p.getGameProfile().getName());row.addProperty("skin_model",p.getSkin().model().id());row.addProperty("entity_class",p.getClass().getName());
            for(var slot:RealmArmor.SLOTS){
                var expected=TIERS[i].equals("aegis")?AegisArmor.item("aegis_"+kind(slot)):RealmArmaments.item(TIERS[i]+"_"+kind(slot));
                require(p.getItemBySlot(slot).is(expected),"Equipment did not synchronize: "+TIERS[i]+" "+slot);
            }
            long n=0;for(var slot:RealmArmor.SLOTS)n+=RealmArmorClient.model(TIERS[i],slot).renderedVertices;
            require(n>0,"Normal player renderer did not submit armor vertices");row.addProperty("native_armor_vertices",n);players.add(row);
        }
        REPORT.add("players",players);
        require(actor(mc,0).getSkin().model()==PlayerSkin.Model.WIDE&&actor(mc,1).getSkin().model()==PlayerSkin.Model.SLIM,"Both native arm widths required");
    }
    private static void skinFixtures(Minecraft mc)throws Exception{
        // Only the three owned test PlayerInfo entries: no account skin, external skin service or armor renderer changes.
        var cache=AbstractClientPlayer.class.getDeclaredField("playerInfo");cache.setAccessible(true);
        for(int i=0;i<3;i++){
            var info=mc.getConnection().getPlayerInfo(profiles[i]);require(info!=null,"Missing test player info");
            boolean slim=i==1;
            var skin=new PlayerSkin(net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/player/"+(slim?"slim/alex.png":"wide/steve.png")),null,null,null,slim?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,true);
            var fixture=new net.minecraft.client.multiplayer.PlayerInfo(info.getProfile(),false){
                @Override public PlayerSkin getSkin(){return skin;}
                @Override public GameType getGameMode(){return info.getGameMode();}
            };
            cache.set(actor(mc,i),fixture);
        }
        REPORT.addProperty("skin_fixture","Local vanilla Steve/WIDE and Alex/SLIM on the three test profiles only");
    }
    private static void shot(Minecraft mc,String name){
        Screenshot.grab(mc.gameDirectory,"tools-"+name+".png",mc.getMainRenderTarget(),m->System.out.println("ARMOR_GALLERY "+name));SHOTS.add("tools-"+name+".png");
    }
    private static void finish(Minecraft mc,boolean passed,String reason){
        if(finished||closing)return;REPORT.addProperty("passed",passed);REPORT.addProperty("reason",reason);REPORT.addProperty("phase",phase);REPORT.addProperty("pid",ProcessHandle.current().pid());
        REPORT.addProperty("prepared_fixture_not_survival",true);REPORT.addProperty("owner_profile_used",false);REPORT.add("screenshots",SHOTS);
        if(!passed){finished=true;write(mc);mc.stop();return;}
        closing=true;job=mc.getSingleplayerServer().submit(()->{
            for(var p:ACTORS)mc.getSingleplayerServer().getPlayerList().remove(p);prepared=false;
            return true;
        });settling=new NativeChunkSettler.Session("armor_player_gallery_shutdown",2000);
    }
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("tools-native.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void inspectTools(Minecraft mc){
        var rows=new JsonArray();int[] expected={252,180,222};
        for(int i=0;i<3;i++){
            var stack=new ItemStack(tool(i));var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
            var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create(0));require(quads.size()==expected[i],"Exact native element quads "+TOOLS[i]+" got "+quads.size());
            for(var q:quads)require(!q.getSprite().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Missing face texture");
            require(!model.getParticleIcon().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Missing particle texture");
            require(model.isGui3d(),"Actual native 3D item required");var row=new JsonObject();row.addProperty("item",TOOLS[i]);row.addProperty("quads",quads.size());row.addProperty("gui_3d",true);rows.add(row);
        }REPORT.add("native_models",rows);
    }
    private static class Catalog extends net.minecraft.client.gui.screens.Screen{
        Catalog(){super(net.minecraft.network.chat.Component.literal("Экспедиционные приборы — T01"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float dt){
            g.fill(0,0,width,height,0xff17232d);g.drawCenteredString(font,title,width/2,18,0xffdeecee);
            String[] labels={"Катушка","Пульт подъёмника","Искатель структур"};
            for(int i=0;i<3;i++){int x=width*(i+1)/4;g.drawCenteredString(font,labels[i],x,53,0xffbcd1d9);
                g.pose().pushPose();g.pose().translate(x-48,75,0);g.pose().scale(6,6,6);g.renderItem(new ItemStack(tool(i)),0,0);g.pose().popPose();
                g.renderItem(new ItemStack(tool(i)),x-8,190);
            }super.render(g,mx,my,dt);
        }
    }
    private static void selectTool(Minecraft mc,int index){job=mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(observer);p.getInventory().selected=0;p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(tool(index)));p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(tool(index)));p.inventoryMenu.broadcastChanges();p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));return true;});ticks=0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){if(job!=null&&job.isDone())job.join();if(settling.failed())throw new IllegalStateException(settling.failure());if(settling.ready()){REPORT.add("settled",settling.report());finished=true;write(mc);mc.stop();}return;}
            if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned profile required");
                require(ModList.get().isLoaded("distanthorizons")&&ModList.get().isLoaded("create")&&ModList.get().isLoaded("sable"),"Full pack and DH required");require(!ModList.get().isLoaded("cpm"),"Rejected avatar dependency must not enter this candidate");
                started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(8);profiles=new UUID[]{profile("wide/steve.png",10),profile("slim/alex.png",100),profile("wide/steve.png",200)};
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.fov().set(42);mc.options.fovEffectScale().set(0D);mc.options.bobView().set(false);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/tools-native")),"Fresh tool gallery required");
                mc.createWorldOpenFlows().createFreshLevel("tools-native",new LevelSettings("EREZ tool geometry checks",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261027L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started)return;require(System.nanoTime()<deadline,"Tool deadline phase="+phase);
            if(SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(skinsFixed)for(int i=0;i<3;i++){var p=actor(mc,i);p.yBodyRot=p.yBodyRotO=p.yHeadRot=p.yHeadRotO=180;}
            var server=mc.getSingleplayerServer();
            if(phase==0){observer=mc.player.getUUID();job=server.submit(()->{prepare(server.getPlayerList().getPlayer(observer));return true;});phase=1;ticks=0;return;}
            if(job!=null&&!job.isDone())return;if(job!=null)job.join();if(++ticks<(phase==1?70:30))return;ticks=0;
            if(phase==1){if(mc.level.getPlayerByUUID(profiles[0])==null||mc.level.getPlayerByUUID(profiles[1])==null||mc.level.getPlayerByUUID(profiles[2])==null)return;
                if(!skinsFixed){skinFixtures(mc);skinsFixed=true;return;}inspectTools(mc);for(int i=0;i<3;i++)require(actor(mc,i).getMainHandItem().is(tool(i)),"Held item synchronization");
                REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("distant_horizons_loaded",true);REPORT.addProperty("mod_version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());
                shot(mc,"held-front");camera(mc,28,201.05,26,-34,0);phase=2;
            }else if(phase==2){shot(mc,"held-quarter");job=server.submit(()->{for(int i=0;i<3;i++){ACTORS.get(i).setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);ACTORS.get(i).setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(tool(i)));}return true;});camera(mc,32,201.02,25,0,0);phase=3;
            }else if(phase==3){for(int i=0;i<3;i++)require(actor(mc,i).getOffhandItem().is(tool(i)),"Offhand synchronization");shot(mc,"held-left");REPORT.addProperty("third_person_both_hands_checked",true);
                job=server.submit(()->{var p=server.getPlayerList().getPlayer(observer);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setInvulnerable(true);p.getInventory().clearContent();p.getInventory().selected=0;for(int i=0;i<3;i++)p.getInventory().setItem(i,new ItemStack(tool(i)));p.inventoryMenu.broadcastChanges();p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));return true;});mc.options.hideGui=false;mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));phase=4;
            }else if(phase==4){for(int i=0;i<3;i++)require(mc.player.getInventory().getItem(i).is(tool(i)),"Inventory synchronization");shot(mc,"inventory");mc.options.guiScale().set(4);mc.resizeDisplay();mc.setScreen(new Catalog());phase=5;
            }else if(phase==5){shot(mc,"catalog");mc.setScreen(null);mc.options.guiScale().set(2);mc.resizeDisplay();camera(mc,32,201.62,25,0,20);selectTool(mc,0);phase=6;
            }else if(phase>=6&&phase<=8){int i=phase-6;require(mc.player.getMainHandItem().is(tool(i))&&mc.player.getOffhandItem().is(tool(i)),"Both first-person items arrived");shot(mc,"first-person-"+TOOLS[i]);if(i<2){selectTool(mc,i+1);phase++;}else{REPORT.addProperty("first_person_both_hands_captured",true);mc.options.guiScale().set(4);mc.resizeDisplay();mc.setScreen(new Catalog());job=mc.reloadResourcePacks();phase=9;}
            }else if(phase==9){inspectTools(mc);shot(mc,"after-reload");REPORT.addProperty("resource_reload_checked",true);job=server.submit(()->{var p=server.getPlayerList().getPlayer(observer);for(int i=0;i<3;i++)p.getInventory().getItem(i).set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);p.inventoryMenu.broadcastChanges();return true;});phase=10;
            }else if(phase==10){for(int i=0;i<3;i++)require(mc.player.getInventory().getItem(i).hasFoil(),"Glint arrived");shot(mc,"glint-inventory");REPORT.addProperty("glint_checked",true);finish(mc,true,"");}
        }catch(Throwable e){e.printStackTrace();if(mc.level!=null)shot(mc,"failure");finish(mc,false,e.toString());}
    }
}
