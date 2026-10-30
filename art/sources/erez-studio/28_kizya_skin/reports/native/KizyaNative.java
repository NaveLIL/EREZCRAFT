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
public final class KizyaNative {
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
    private static boolean skinsFixed; private static int vanillaShots;
    private static volatile boolean prepared;
    private static volatile int movement;
    private static int phase,ticks; private static float observerYaw,observerPitch;
    private static long deadline;
    private static CompletableFuture<?> job;
    private static volatile NativeChunkSettler.Session settling;
    private KizyaNative(){}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static UUID profile(String texture,int offset){
        for(int i=offset;i<offset+1000;i++){
            UUID id=new UUID(0x1133557700004000L,0x8000000000000000L+i);
            if(DefaultPlayerSkin.get(id).texture().getPath().endsWith(texture))return id;
        }throw new IllegalStateException("No selected native default skin");
    }
    private static String kind(EquipmentSlot s){return switch(s){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    private static void equip(ServerPlayer p,String tier){
        for(var slot:RealmArmor.SLOTS){var item=tier.equals("aegis")?AegisArmor.item(tier+"_"+kind(slot)):RealmArmaments.item(tier+"_"+kind(slot));
            var stack=new ItemStack(item);if(tier.equals("aegis"))AegisPower.charge(stack,AegisPower.CAPACITY);p.setItemSlot(slot,stack);}
        p.inventoryMenu.broadcastChanges();
    }
    private static void reset(ServerPlayer p,int i){
        p.stopUsingItem();p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);p.setShiftKeyDown(false);p.setSprinting(false);
        p.setSwimming(false);p.setPos(X[i],200,32);p.setYRot(180);p.setYHeadRot(180);p.yBodyRot=180;p.setXRot(0);p.setDeltaMovement(Vec3.ZERO);
    }
    private static ServerPlayer connect(ServerLevel level,int index){
        var cookie=CommonListenerCookie.createInitial(new GameProfile(profiles[index],NAMES[index]),false);
        var player=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);CHANNELS.add(channel);
        NetworkRegistry.configureMockConnection(connection);level.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.SURVIVAL);player.teleportTo(level,X[index],200,32,Set.of(),180,0);player.hasChangedDimension();
        player.setNoGravity(true);player.setInvulnerable(true);reset(player,index);return player;
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
        observerYaw=yaw;observerPitch=pitch;
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
    private static byte[] avatarData;
    private static final java.nio.file.Path ART=java.nio.file.Path.of("D:/repos/Blender/creations/28_kizya_skin");
    private static final Map<String,Integer> ELEMENTS=new HashMap<>();
    private static void paintedVanillaSkin(Minecraft mc)throws Exception {
        var key=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"kizya_painted_fixture");
        try(var input=Files.newInputStream(ART.resolve("exports/Kizya_Pink_PK02_slim_64x64.png"))){
            var image=com.mojang.blaze3d.platform.NativeImage.read(input);require(image.getWidth()==64&&image.getHeight()==64,"Painted Java skin dimensions");
            mc.getTextureManager().register(key,new net.minecraft.client.renderer.texture.DynamicTexture(image));
        }
        var cache=AbstractClientPlayer.class.getDeclaredField("playerInfo");cache.setAccessible(true);
        for(int i=0;i<3;i++){
            var info=mc.getConnection().getPlayerInfo(profiles[i]);require(info!=null,"Fixture player info");
            var skin=new PlayerSkin(key,null,null,null,PlayerSkin.Model.SLIM,true);
            var fixture=new net.minecraft.client.multiplayer.PlayerInfo(info.getProfile(),false){@Override public PlayerSkin getSkin(){return skin;}@Override public GameType getGameMode(){return info.getGameMode();}};
            cache.set(actor(mc,i),fixture);
        }
        REPORT.addProperty("painted_64_skin_loaded",true);REPORT.addProperty("local_skin_fixture_not_account_login",true);
    }
    private static void compileAvatar()throws Exception{
        var testConfig=new com.tom.cpl.config.ConfigEntry(new java.util.TreeMap<>(),()->{});
        var mapField=com.tom.cpl.config.ConfigEntry.class.getDeclaredField("data");mapField.setAccessible(true);
        require(mapField.get(testConfig).getClass().getName().contains("SynchronizedMap"),"CPM config concurrency guard must be active");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(8);var checks=new ArrayList<java.util.concurrent.Future<?>>();
        for(int thread=0;thread<8;thread++){final int worker=thread;checks.add(pool.submit(()->{for(int n=0;n<1000;n++)testConfig.getEntry("servers").getEntry("shared").getEntry("player"+((n+worker)%64)).getEntry("safety").getBoolean("enabled",true);}));}
        try{for(var task:checks)task.get();}finally{pool.shutdown();}
        require(testConfig.getEntry("servers").getEntry("shared").keySet().size()==64,"Concurrent configuration entries preserved");REPORT.addProperty("cpm_config_parallel_probe_calls",8000);
        var file=com.tom.cpm.shared.io.ModelFile.load(ART.resolve("exports/Kizya_Pink_PK02.cpmmodel").toFile());avatarData=file.getDataBlock();
        var metadata=JsonParser.parseString(Files.readString(ART.resolve("reports/CPM_FORMAT_CHECK.json"))).getAsJsonObject();
        for(var entry:metadata.getAsJsonObject("runtime_element_ids").entrySet())ELEMENTS.put(entry.getKey(),entry.getValue().getAsInt());
        require(ELEMENTS.size()==139,"Painted PK02 element count");require(avatarData.length<30*1024,"Self-contained model packet under CPM local limit");
        REPORT.addProperty("model_elements",ELEMENTS.size());REPORT.addProperty("cpm_packet_bytes",avatarData.length);REPORT.addProperty("self_contained_model",true);
    }
    @SuppressWarnings("unchecked") private static com.tom.cpm.shared.definition.ModelDefinition definition(Minecraft mc,int i){
        var loader=com.tom.cpm.shared.MinecraftClientAccess.get().getDefinitionLoader();var p=loader.getLoadedPlayer(actor(mc,i).getGameProfile());
        require(p!=null,"CPM tracked player absent");var d=p.getModelDefinition();require(d!=null&&d.doRender(),"CPM model not rendered: "+(d==null?"null":d.getResolveState()+" "+d.getError()));return d;
    }
    @SuppressWarnings("unchecked") private static void skinFixtures(Minecraft mc)throws Exception{
        compileAvatar();
        job=mc.getSingleplayerServer().submit(()->{
            var network=(com.tom.cpm.shared.network.NetHandler)com.tom.cpm.shared.MinecraftServerAccess.get().getNetHandler();
            for(var p:ACTORS)network.setSkin(p,avatarData,false);return true;
        });
        REPORT.addProperty("skin_fixture","Server assigns self-contained CPM packet to three owned players; unmodified client receives through CPM tracking protocol");
    }
    private static void visibility(Minecraft mc,int index,String name,boolean expected){
        var d=definition(mc,index);var c=d.getElementById(ELEMENTS.get(name));require(c!=null,"Missing cube "+name);
        require(c.display==expected,"Equipment clearance "+name+" expected="+expected+" actual="+c.display);
    }


    private static void shot(Minecraft mc,String name){
        Screenshot.grab(mc.gameDirectory,"kizya-"+name+".png",mc.getMainRenderTarget(),m->System.out.println("ARMOR_GALLERY "+name));SHOTS.add("kizya-"+name+".png");
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
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("kizya-native.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void clearAll(){for(var p:ACTORS){for(var slot:RealmArmor.SLOTS)p.setItemSlot(slot,ItemStack.EMPTY);BackpackHarness.set(p,ItemStack.EMPTY);}}
    private static void focus(Minecraft mc,boolean back){
        var server=mc.getSingleplayerServer();job=server.submit(()->{for(int i=0;i<3;i++){reset(ACTORS.get(i),i);ACTORS.get(i).teleportTo(server.overworld(),i==1?32:48,200,i==1?32:48,Set.of(),180,0);ACTORS.get(i).hasChangedDimension();}return true;});
        camera(mc,32,201.0,back?36.8:27.2,back?180:0,0);
    }
    private static void lineup(Minecraft mc){var s=mc.getSingleplayerServer();job=s.submit(()->{for(int i=0;i<3;i++){reset(ACTORS.get(i),i);ACTORS.get(i).teleportTo(s.overworld(),X[i],200,32,Set.of(),180,0);ACTORS.get(i).hasChangedDimension();}return true;});camera(mc,32,201.02,25,0,0);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){if(job!=null&&job.isDone())job.join();if(settling.failed())throw new IllegalStateException(settling.failure());if(settling.ready()){REPORT.add("settled",settling.report());finished=true;write(mc);mc.stop();}return;}
            if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned profile required");
                require(ModList.get().isLoaded("cpm")&&ModList.get().isLoaded("distanthorizons")&&ModList.get().isLoaded("create")&&ModList.get().isLoaded("sable"),"Full pack and CPM required");
                started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(8);profiles=new UUID[]{profile("wide/steve.png",10),profile("wide/steve.png",100),profile("wide/steve.png",200)};
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.fov().set(32);mc.options.fovEffectScale().set(0D);mc.options.bobView().set(false);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/avatar-native")),"Fresh avatar world required");
                mc.createWorldOpenFlows().createFreshLevel("avatar-native",new LevelSettings("EREZ 3D avatar checks",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261027L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started)return;require(System.nanoTime()<deadline,"Avatar deadline phase="+phase);
            if(SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(mc.getOverlay()!=null)return;
            mc.mouseHandler.releaseMouse();mc.player.setYRot(observerYaw);mc.player.yRotO=observerYaw;mc.player.setXRot(observerPitch);mc.player.xRotO=observerPitch;
            if(skinsFixed)for(int i=0;i<3;i++){var p=actor(mc,i);p.yBodyRot=p.yBodyRotO=p.yHeadRot=p.yHeadRotO=180;}
            var server=mc.getSingleplayerServer();
            if(phase==0){observer=mc.player.getUUID();job=server.submit(()->{prepare(server.getPlayerList().getPlayer(observer));clearAll();return true;});phase=1;ticks=0;return;}
            if(job!=null&&!job.isDone())return;if(job!=null)job.join();if(++ticks<(phase==1?80:35))return;ticks=0;
            if(phase==1){if(mc.level.getPlayerByUUID(profiles[0])==null||mc.level.getPlayerByUUID(profiles[1])==null||mc.level.getPlayerByUUID(profiles[2])==null)return;
                if(vanillaShots==0){paintedVanillaSkin(mc);vanillaShots=1;focus(mc,false);return;}
                if(vanillaShots==1){shot(mc,"vanilla-64-front");vanillaShots=2;focus(mc,true);return;}
                if(vanillaShots==2){shot(mc,"vanilla-64-back");vanillaShots=3;focus(mc,false);return;}
                if(!skinsFixed){lineup(mc);skinFixtures(mc);skinsFixed=true;return;}
                for(int i=0;i<3;i++){definition(mc,i);require(Arrays.equals(avatarData,com.tom.cpm.shared.MinecraftClientAccess.get().getDefinitionLoader().getModel(actor(mc,i).getGameProfile())),"Server model bytes must reach observer through CPM network");}REPORT.addProperty("server_to_client_sync_checked",true);REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("cpm_loaded",true);REPORT.addProperty("distant_horizons_loaded",true);focus(mc,false);phase=2;
            }else if(phase==2){visibility(mc,1,"hair_crown",true);visibility(mc,1,"left_pinafore_shorts",true);shot(mc,"bare-front");focus(mc,true);phase=3;
            }else if(phase==3){shot(mc,"bare-back");camera(mc,29.1,201.05,27.4,-32,0);phase=4;
            }else if(phase==4){shot(mc,"bare-quarter");lineup(mc);job=server.submit(()->{for(var p:ACTORS)p.setShiftKeyDown(true);return true;});phase=5;
            }else if(phase==5){for(int i=0;i<3;i++)require(actor(mc,i).isCrouching(),"Native crouch");shot(mc,"bare-crouch");REPORT.addProperty("crouching_checked",true);job=server.submit(()->{for(int i=0;i<3;i++)reset(ACTORS.get(i),i);movement=1;return true;});phase=6;
            }else if(phase==6){boolean walking=false;for(int i=0;i<3;i++)walking|=actor(mc,i).walkAnimation.speed()>.01F;require(walking,"Native walking");shot(mc,"bare-walking");REPORT.addProperty("walking_checked",true);job=server.submit(()->{movement=0;for(int i=0;i<3;i++){reset(ACTORS.get(i),i);equip(ACTORS.get(i),TIERS[i]);}return true;});phase=7;
            }else if(phase==7){for(int i=0;i<3;i++){visibility(mc,i,"hair_crown",false);visibility(mc,i,"front_lock_-1_0_0",false);visibility(mc,i,"left_pinafore_shorts",false);visibility(mc,i,"left_shoe",false);}shot(mc,"realm-armor-front");camera(mc,32,201.02,39,180,0);phase=8;
            }else if(phase==8){shot(mc,"realm-armor-back");REPORT.addProperty("realm_armor_clearance_checked",true);job=server.submit(()->{clearAll();for(var s:RealmArmor.SLOTS)ACTORS.get(0).setItemSlot(s,new ItemStack(KotomyafArmor.item("kotomyaf_"+kind(s))));
                    var iron=new Item[]{Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS};var nether=new Item[]{Items.NETHERITE_HELMET,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_LEGGINGS,Items.NETHERITE_BOOTS};int j=0;for(var s:RealmArmor.SLOTS){ACTORS.get(1).setItemSlot(s,new ItemStack(iron[j]));ACTORS.get(2).setItemSlot(s,new ItemStack(nether[j++]));}return true;});camera(mc,32,201.02,25,0,0);phase=9;
            }else if(phase==9){for(int i=0;i<3;i++){visibility(mc,i,"hair_crown",false);visibility(mc,i,"front_lock_-1_0_0",false);}shot(mc,"cat-iron-netherite");REPORT.addProperty("kotomyaf_vanilla_checked",true);job=server.submit(()->{clearAll();ACTORS.get(0).setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));ACTORS.get(1).setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));ACTORS.get(2).setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));return true;});phase=10;
            }else if(phase==10){visibility(mc,0,"hair_crown",false);visibility(mc,0,"front_lock_-1_0_0",true);visibility(mc,1,"hair_crown",true);visibility(mc,1,"front_lock_-1_0_0",false);visibility(mc,2,"front_lock_-1_0_0",true);visibility(mc,2,"left_pinafore_shorts",false);shot(mc,"mixed-slots");REPORT.addProperty("per_slot_clearance_checked",true);
                job=server.submit(()->{clearAll();for(int i=0;i<3;i++){ACTORS.get(i).setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));BackpackHarness.set(ACTORS.get(i),new ItemStack(ExpeditionEquipment.RIFT_BACKPACK.get()));}return true;});camera(mc,32,201.02,39,180,0);phase=11;
            }else if(phase==11){for(int i=0;i<3;i++)visibility(mc,i,"back_lock_00_0",false);shot(mc,"elytra-backpacks");job=server.submit(()->{for(var p:ACTORS){p.setPos(p.getX(),203,p.getZ());p.setOnGround(false);p.startFallFlying();}return true;});camera(mc,32,203.6,41,180,5);phase=12;
            }else if(phase==12){for(int i=0;i<3;i++)require(actor(mc,i).isFallFlying(),"Native flight");shot(mc,"elytra-flight");REPORT.addProperty("elytra_backpacks_checked",true);
                job=server.submit(()->{clearAll();var l=server.overworld();for(int x=24;x<=40;x++)for(int z=29;z<=37;z++)for(int y=200;y<=203;y++)l.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),3);for(int i=0;i<3;i++){var p=ACTORS.get(i);p.stopFallFlying();reset(p,i);p.setPos(X[i],201,32);p.setSprinting(true);p.setDeltaMovement(new Vec3(0,0,-.01));}return true;});camera(mc,32,201.4,35.8,180,0);phase=13;
            }else if(phase==13){for(int i=0;i<3;i++)require(actor(mc,i).isSwimming(),"Native swimming");shot(mc,"swimming");REPORT.addProperty("swimming_checked",true);
                job=server.submit(()->{clearAll();var l=server.overworld();for(int x=21;x<=43;x++)for(int z=21;z<=43;z++)for(int y=200;y<=204;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);for(int i=0;i<3;i++)reset(ACTORS.get(i),i);return true;});camera(mc,32,201.02,25,0,0);phase=14;
            }else if(phase==14){job=mc.reloadResourcePacks();phase=15;
            }else if(phase==15){for(int i=0;i<3;i++)definition(mc,i);shot(mc,"after-reload");REPORT.addProperty("resource_reload_checked",true);
                job=server.submit(()->{var p=server.getPlayerList().getPlayer(observer);p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);p.setNoGravity(true);p.getInventory().clearContent();p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();var n=(com.tom.cpm.shared.network.NetHandler)com.tom.cpm.shared.MinecraftServerAccess.get().getNetHandler();n.setSkin(p,avatarData,false);return true;});mc.options.hideGui=false;camera(mc,32,201.62,25,0,35);phase=16;
            }else if(phase==16){var loader=com.tom.cpm.shared.MinecraftClientAccess.get().getDefinitionLoader();require(Arrays.equals(avatarData,loader.getModel(mc.player.getGameProfile())),"Own model synchronized");require(mc.player.getMainHandItem().isEmpty()&&mc.player.getOffhandItem().isEmpty(),"Unarmed first-person view required");shot(mc,"first-person-unarmed");REPORT.addProperty("first_person_capture",true);REPORT.addProperty("unarmed_hand_checked",true);finish(mc,true,"");}
        }catch(Throwable e){e.printStackTrace();if(mc.level!=null)shot(mc,"failure");finish(mc,false,e.toString());}
    }
}
