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
public final class TideIndicatorNative {
    private static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("tideindicator");
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

    private static final net.minecraft.client.resources.model.ModelResourceLocation CLOCK_REFERENCE=new net.minecraft.client.resources.model.ModelResourceLocation(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"item/tide_clock_reference"),"standalone");
    @EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class ReferenceModels {
        @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event){if(ACTIVE)event.register(CLOCK_REFERENCE);}
        @SubscribeEvent public static void bake(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event){if(ACTIVE){var model=event.getModels().get(CLOCK_REFERENCE);require(model!=null,"MB3D reference loaded");event.getModels().put(new net.minecraft.client.resources.model.ModelResourceLocation(net.minecraft.resources.ResourceLocation.withDefaultNamespace("clock"),"inventory"),model);}}
    }
    private static void compareClockPose(Minecraft mc){
        var ours=mc.getItemRenderer().getModel(new ItemStack(tool(0)),mc.level,mc.player,0);
        var donor=mc.getItemRenderer().getModel(new ItemStack(Items.CLOCK),mc.level,mc.player,0);
        var donorQuads=donor.getQuads(null,null,net.minecraft.util.RandomSource.create(0));require(donorQuads.size()==13,"Original MB3D clock, not vanilla");for(var q:donorQuads)require(!q.getSprite().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Original clock texture must exist");
        for(var context:new ItemDisplayContext[]{ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND}){
            var a=ours.getTransforms().getTransform(context);var b=donor.getTransforms().getTransform(context);
            require(a.rotation.equals(b.rotation)&&a.translation.equals(b.translation)&&a.scale.equals(b.scale),"Exact MB3D hand pose "+context);
        }REPORT.addProperty("all_four_baked_hand_transforms_equal_original_mb3d_clock",true);
    }
    private TideIndicatorNative(){}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static UUID profile(String texture,int offset){
        for(int i=offset;i<offset+1000;i++){
            UUID id=new UUID(0x1133557700004000L,0x8000000000000000L+i);
            if(DefaultPlayerSkin.get(id).texture().getPath().endsWith(texture))return id;
        }throw new IllegalStateException("No selected native default skin");
    }
    private static String kind(EquipmentSlot s){return switch(s){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    private static final String[] TOOLS={"tide_indicator","tide_indicator","tide_indicator"};
    private static net.minecraft.world.item.Item tool(int i){if(i==2)return Items.CLOCK;return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,TOOLS[i]));}
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
        Screenshot.grab(mc.gameDirectory,"tide-indicator-"+name+".png",mc.getMainRenderTarget(),m->System.out.println("ARMOR_GALLERY "+name));SHOTS.add("tide-indicator-"+name+".png");
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
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("tide-indicator-native.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static java.lang.reflect.Field needleField;
    private static void angle(float value){try{if(needleField==null){needleField=ClientTideEffects.class.getDeclaredField("currentAngle");needleField.setAccessible(true);}needleField.setFloat(null,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void inspectTools(Minecraft mc){
        float old=ClientTideEffects.getSmoothedAngle();var rows=new JsonArray();
        try{for(int i=0;i<16;i++){
            angle((i+.25F)/16);var model=mc.getItemRenderer().getModel(new ItemStack(tool(0)),mc.level,mc.player,0);
            var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create(0));require(quads.size()==313,"TI02 native face count "+i+" got "+quads.size());
            String expected="tide_indicator_"+String.format(java.util.Locale.ROOT,"%02d",i);boolean dial=false;float minZ=Float.POSITIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
            for(var q:quads){var sprite=q.getSprite().contents().name();require(!sprite.equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Missing TI02 texture");if(sprite.getPath().endsWith(expected))dial=true;var v=q.getVertices();for(int k=0;k<4;k++){float z=Float.intBitsToFloat(v[k*8+1]);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}}
            require(dial,"Registered tide predicate did not choose state "+i);require(model.isGui3d(),"TI02 must be native 3D");require((maxZ-minZ)*16<=.881,"Thin depth exceeded");
            var row=new JsonObject();row.addProperty("state",i);row.addProperty("quads",quads.size());row.addProperty("depth_model_units",(maxZ-minZ)*16);row.addProperty("registered_predicate_selected_expected_texture",true);rows.add(row);
        }}finally{angle(old);}REPORT.add("all_16_native_states",rows);
    }
    private static class Catalog extends net.minecraft.client.gui.screens.Screen{
        private boolean foil;
        Catalog(){super(net.minecraft.network.chat.Component.literal("TI02 / THIN POCKET BAROMETER / 16 STATES"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float dt){
            g.fill(0,0,width,height,0xff17232d);g.drawCenteredString(font,title,width/2,12,0xffdeecee);float old=ClientTideEffects.getSmoothedAngle();
            try{for(int i=0;i<16;i++){int x=width*(i%4*2+1)/8,y=40+(i/4)*52;angle((i+.25F)/16);g.drawCenteredString(font,String.format(java.util.Locale.ROOT,"%02d",i),x,y,0xffbcd1d9);g.pose().pushPose();g.pose().translate(x-16,y+11,0);g.pose().scale(2,2,2);var stack=new ItemStack(tool(0));if(foil)stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);g.renderItem(stack,0,0);g.pose().popPose();}}finally{angle(old);}
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
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/tide-indicator-native")),"Fresh tool gallery required");
                mc.createWorldOpenFlows().createFreshLevel("tide-indicator-native",new LevelSettings("EREZ tool geometry checks",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261027L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started)return;require(System.nanoTime()<deadline,"Tool deadline phase="+phase);
            if(mc.getOverlay()!=null)return;
            if(SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(skinsFixed)for(int i=0;i<3;i++){var p=actor(mc,i);p.yBodyRot=p.yBodyRotO=p.yHeadRot=p.yHeadRotO=180;}
            mc.getToasts().clear();
            var server=mc.getSingleplayerServer();
            if(phase==0){observer=mc.player.getUUID();job=server.submit(()->{prepare(server.getPlayerList().getPlayer(observer));return true;});phase=1;ticks=0;return;}
            if(job!=null&&!job.isDone())return;if(job!=null)job.join();if(++ticks<(phase==1?70:30))return;ticks=0;
            if(phase==1){if(mc.level.getPlayerByUUID(profiles[0])==null||mc.level.getPlayerByUUID(profiles[1])==null||mc.level.getPlayerByUUID(profiles[2])==null)return;
                if(!skinsFixed){skinFixtures(mc);skinsFixed=true;return;}inspectTools(mc);compareClockPose(mc);for(int i=0;i<3;i++)require(actor(mc,i).getMainHandItem().is(tool(i)),"Held item synchronization");
                REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("distant_horizons_loaded",true);REPORT.addProperty("mod_version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());
                shot(mc,"held-front");camera(mc,28,201.05,26,-34,0);phase=2;
            }else if(phase==2){shot(mc,"held-quarter");job=server.submit(()->{for(int i=0;i<3;i++){ACTORS.get(i).setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);ACTORS.get(i).setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(tool(i)));}return true;});camera(mc,32,201.02,25,0,0);phase=3;
            }else if(phase==3){for(int i=0;i<3;i++)require(actor(mc,i).getOffhandItem().is(tool(i)),"Offhand synchronization");shot(mc,"held-left");REPORT.addProperty("third_person_both_hands_checked",true);
                job=server.submit(()->{var p=server.getPlayerList().getPlayer(observer);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setInvulnerable(true);p.getInventory().clearContent();p.getInventory().selected=0;for(int i=0;i<3;i++)p.getInventory().setItem(i,new ItemStack(tool(i)));p.inventoryMenu.broadcastChanges();p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));return true;});mc.options.hideGui=false;mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));phase=4;
            }else if(phase==4){for(int i=0;i<3;i++)require(mc.player.getInventory().getItem(i).is(tool(i)),"Inventory synchronization");shot(mc,"inventory");mc.options.guiScale().set(4);mc.resizeDisplay();mc.setScreen(new Catalog());phase=5;
            }else if(phase==5){shot(mc,"catalog");mc.setScreen(null);mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeDisplay();REPORT.addProperty("first_person_fov",70);camera(mc,32,201.62,25,0,20);job=server.submit(()->{var p=server.getPlayerList().getPlayer(observer);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.CLOCK));p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.CLOCK));p.inventoryMenu.broadcastChanges();return true;});phase=15;
            }else if(phase==15){require(mc.player.getMainHandItem().is(Items.CLOCK)&&mc.player.getOffhandItem().is(Items.CLOCK),"Reference clocks arrived");shot(mc,"donor-clock-both-hands");selectTool(mc,0);phase=6;
            }else if(phase==6){require(mc.player.getMainHandItem().is(tool(0))&&mc.player.getOffhandItem().is(tool(0)),"Both first-person indicators arrived");shot(mc,"first-person-both-hands");REPORT.addProperty("first_person_both_hands_captured",true);mc.options.guiScale().set(4);mc.resizeDisplay();mc.setScreen(new Catalog());job=mc.reloadResourcePacks();phase=9;
            }else if(phase==9){inspectTools(mc);compareClockPose(mc);shot(mc,"after-reload");REPORT.addProperty("resource_reload_checked",true);job=server.submit(()->{var p=server.getPlayerList().getPlayer(observer);for(int i=0;i<3;i++){var stack=new ItemStack(tool(i));stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);p.getInventory().setItem(i,stack);}p.inventoryMenu.broadcastChanges();return true;});phase=10;
            }else if(phase==10){for(int i=0;i<3;i++)require(mc.player.getInventory().getItem(i).hasFoil(),"Glint arrived");((Catalog)mc.screen).foil=true;phase=13;return;
            }else if(phase==13){shot(mc,"glint-inventory");REPORT.addProperty("glint_checked",true);mc.setScreen(null);mc.options.hideGui=true;job=server.submit(()->{var p=server.getPlayerList().getPlayer(observer);var level=server.overworld();var wall=new BlockPos(32,202,20);level.setBlock(wall,Blocks.SMOOTH_STONE.defaultBlockState(),3);p.teleportTo(level,32.5,200,23.5,Set.of(),180,0);p.hasChangedDimension();p.setGameMode(GameType.SURVIVAL);p.setShiftKeyDown(true);var stack=new ItemStack(tool(0));p.setItemSlot(EquipmentSlot.MAINHAND,stack);p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);var hit=new net.minecraft.world.phys.BlockHitResult(new Vec3(32.5,202.5,21),Direction.SOUTH,wall,false);var result=p.gameMode.useItemOn(p,level,stack,InteractionHand.MAIN_HAND,hit);p.setShiftKeyDown(false);require(result.consumesAction()&&stack.isEmpty(),"Real wall mount consumes the indicator");var frames=level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,new net.minecraft.world.phys.AABB(wall).inflate(3),f->f.getItem().is(tool(0)));require(frames.size()==1,"Exactly one mounted indicator");REPORT.addProperty("frame_uuid",frames.getFirst().getUUID().toString());p.inventoryMenu.broadcastChanges();return true;});phase=11;
            }else if(phase==11){var frame=mc.level.entitiesForRendering();boolean mounted=false;for(var e:frame)if(e instanceof net.minecraft.world.entity.decoration.ItemFrame f&&f.getItem().is(tool(0)))mounted=true;require(mounted,"Wall frame reached the real client");REPORT.addProperty("native_wall_mount_checked",true);camera(mc,32.5,202.5,24.5,180,0);phase=12;
            }else if(phase==12){shot(mc,"wall-mounted");finish(mc,true,"");}
        }catch(Throwable e){e.printStackTrace();if(mc.level!=null)shot(mc,"failure");finish(mc,false,e.toString());}
    }
}
