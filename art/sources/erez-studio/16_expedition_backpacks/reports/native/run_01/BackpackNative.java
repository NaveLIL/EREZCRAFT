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
public final class BackpackNative {
    private static final boolean ACTIVE=System.getProperty("interstice.progressionSmoke","").equals("create");
    private static final String[] TIERS={"riftsilver","riftsteel","aegis","aegis","aegis","aegis","aegis","aegis","aegis","aegis"};
    private static final String[] NAMES={"Pack_Field","Pack_Expedition","Pack_Rift","Pack_Bench3","Pack_Bench4","Pack_Bench5","Pack_Bench6","Pack_Bench7","Pack_Bench8","Pack_Bench9"};
    private static final double[] X={34.5,32,29.5,38,39.8,41.6,43.4,45.2,47,48.8};
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
    private BackpackNative(){}
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
        BackpackHarness.set(p,new ItemStack(tier.equals("aegis")?ExpeditionEquipment.RIFT_BACKPACK.get():tier.equals("riftsteel")?ExpeditionEquipment.EXPEDITION_BACKPACK.get():ExpeditionEquipment.FIELD_BACKPACK.get()));p.inventoryMenu.broadcastChanges();
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
        Screenshot.grab(mc.gameDirectory,"backpacks-"+name+".png",mc.getMainRenderTarget(),m->System.out.println("ARMOR_GALLERY "+name));SHOTS.add("backpacks-"+name+".png");
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
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("backpacks-native.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static volatile int benchStage;private static long frameLast;private static int warm;
    private static final List<Double> frameSamples=new ArrayList<>();private static final JsonArray benchResults=new JsonArray();
    private static void replaceLayers(boolean legacy)throws Exception{
        var field=net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredField("layers");field.setAccessible(true);
        for(var entry:Minecraft.getInstance().getEntityRenderDispatcher().getSkinMap().values()){
            if(!(entry instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer))continue;
            @SuppressWarnings("unchecked") var layers=(List<Object>)field.get(renderer);
            for(int i=0;i<layers.size();i++)if(layers.get(i) instanceof BackpackLayer||layers.get(i) instanceof LegacyBackpackLayer)
                layers.set(i,legacy?new LegacyBackpackLayer(renderer):new BackpackLayer(renderer));
        }
    }
    @SubscribeEvent public static void frames(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event){
        if(!ACTIVE||benchStage<=0||benchStage>3)return;long now=System.nanoTime();
        if(frameLast==0){frameLast=now;return;}double ms=(now-frameLast)/1_000_000D;frameLast=now;
        if(warm++<180)return;frameSamples.add(ms);if(frameSamples.size()<720)return;
        try{
            var sorted=new ArrayList<>(frameSamples);sorted.sort(Double::compare);var row=new JsonObject();
            row.addProperty("renderer",benchStage==2?"B01_new":"previous_v20");row.addProperty("pass",benchStage);row.addProperty("samples",sorted.size());
            row.addProperty("median_frame_ms",sorted.get(sorted.size()/2));row.addProperty("p95_frame_ms",sorted.get((int)(sorted.size()*.95)));row.addProperty("median_fps",1000/sorted.get(sorted.size()/2));benchResults.add(row);
            benchStage++;replaceLayers(benchStage==3);warm=0;frameSamples.clear();frameLast=0;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void beginBench(Minecraft mc)throws Exception{replaceLayers(true);benchStage=1;warm=0;frameLast=0;frameSamples.clear();}
    private static void packItems(Minecraft mc){
        var rows=new JsonArray();for(String name:List.of("field_backpack","expedition_backpack","rift_backpack")){
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,name));var stack=new ItemStack(item);
            var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);var faces=model.getQuads(null,null,net.minecraft.util.RandomSource.create(0));require(model.isGui3d()&&faces.size()>100,"Native 3D pack miniature: "+name);
            for(var face:faces)require(!face.getSprite().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Missing pack miniature texture");
            var row=new JsonObject();row.addProperty("item",name);row.addProperty("quads",faces.size());rows.add(row);
        }REPORT.add("native_item_models",rows);
        for(String tier:List.of("riftsilver","riftsteel","aegis","kotomyaf"))for(String part:List.of("helmet","chestplate","leggings","boots")){
            String name=tier+"_"+part;var stack=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,name)));
            var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);var faces=model.getQuads(null,null,net.minecraft.util.RandomSource.create(0));require(model.isGui3d()&&faces.size()>24,"Previous armor miniature regression: "+name);
            for(var f:faces)require(!f.getSprite().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Previous armor texture regression");
        }REPORT.addProperty("previous_16_armor_icons_checked",true);
    }
    private static final class ItemCatalog extends net.minecraft.client.gui.screens.Screen {
        ItemCatalog(){super(net.minecraft.network.chat.Component.literal("EREZ backpacks"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){
            g.fill(0,0,width,height,0xff101720);g.drawString(font,"EREZ — новые рюкзаки",18,18,0xffe6edf3,false);
            String[] names={"Полевой","Экспедиционный","Разломный"};String[] ids={"field_backpack","expedition_backpack","rift_backpack"};
            for(int i=0;i<3;i++){int px=45+i*145;g.drawString(font,names[i],px,70,0xffb9d3df,false);g.pose().pushPose();g.pose().translate(px,98,0);g.pose().scale(5,5,5);g.renderItem(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,ids[i]))),0,0);g.pose().popPose();}
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){if(job!=null&&job.isDone())job.join();if(settling.failed())throw new IllegalStateException(settling.failure());if(settling.ready()){REPORT.add("settled",settling.report());finished=true;write(mc);mc.stop();}return;}
            if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned test profile required");
                require(ModList.get().isLoaded("distanthorizons")&&ModList.get().isLoaded("create")&&ModList.get().isLoaded("sable"),"Full EREZCRAFT and DH required");
                started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(12);profiles=new UUID[10];profiles[0]=profile("wide/steve.png",10);profiles[1]=profile("slim/alex.png",100);for(int i=2;i<10;i++)profiles[i]=profile("wide/steve.png",200+i*100);
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.fov().set(32);mc.options.fovEffectScale().set(0D);mc.options.bobView().set(false);mc.options.hideGui=true;mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);mc.options.framerateLimit().set(260);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.createWorldOpenFlows().createFreshLevel("backpacks-native",new LevelSettings("EREZ backpack QA",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261029L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started)return;require(System.nanoTime()<deadline,"Backpack test deadline phase="+phase);
            if(SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(skinsFixed)for(int i=0;i<3;i++){var p=actor(mc,i);p.yBodyRot=180;p.yBodyRotO=180;p.yHeadRot=180;p.yHeadRotO=180;}
            var server=mc.getSingleplayerServer();
            if(phase==0){observer=mc.player.getUUID();job=server.submit(()->{prepare(server.getPlayerList().getPlayer(observer));return true;});phase=1;ticks=0;return;}
            if(job!=null&&!job.isDone())return;if(job!=null)job.join();if(++ticks<(phase==1?70:35))return;ticks=0;
            if(phase==1){
                if(mc.level.getPlayerByUUID(profiles[0])==null||mc.level.getPlayerByUUID(profiles[1])==null||mc.level.getPlayerByUUID(profiles[2])==null)return;
                if(!skinsFixed){skinFixtures(mc);skinsFixed=true;return;}inspect(mc);packItems(mc);
                require(BackpackModels.renderedQuads>0,"Actual player layer must render the new pack geometry");
                for(int i=0;i<3;i++)require(BackpackStorage.capacity(BackpackHarness.get(actor(mc,i)))==54+i*18-(i==2?6:0),"Native pack synchronization");
                REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("distant_horizons_loaded",true);REPORT.addProperty("mod_version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());
                shot(mc,"armor-front");camera(mc,32,201.02,39,180,0);phase=2;
            }else if(phase==2){shot(mc,"armor-back");camera(mc,27.8,201.05,38.2,-145.9F,0);phase=3;
            }else if(phase==3){shot(mc,"armor-quarter");closeCamera(mc,0);phase=4;
            }else if(phase>=4&&phase<=6){shot(mc,new String[]{"field-close","expedition-close","rift-close"}[phase-4]);if(phase<6){closeCamera(mc,phase-3);phase++;}else{job=server.submit(()->{for(int i=0;i<3;i++){reset(ACTORS.get(i),i);ACTORS.get(i).setShiftKeyDown(true);}return true;});camera(mc,32,200.85,39,180,0);phase=7;}
            }else if(phase==7){for(int i=0;i<3;i++)require(actor(mc,i).isCrouching(),"Native crouch");shot(mc,"crouching");REPORT.addProperty("crouching_checked",true);job=server.submit(()->{for(int i=0;i<3;i++)reset(ACTORS.get(i),i);movement=1;return true;});phase=8;
            }else if(phase==8){shot(mc,"walking");REPORT.addProperty("walking_checked",true);job=server.submit(()->{movement=0;for(int i=0;i<3;i++){reset(ACTORS.get(i),i);for(var s:RealmArmor.SLOTS)ACTORS.get(i).setItemSlot(s,ItemStack.EMPTY);}return true;});camera(mc,32,201.02,39,180,0);phase=9;
            }else if(phase==9){shot(mc,"bare-back");job=server.submit(()->{for(var p:ACTORS)for(var s:RealmArmor.SLOTS)p.setItemSlot(s,new ItemStack(KotomyafArmor.item("kotomyaf_"+kind(s))));return true;});phase=10;
            }else if(phase==10){shot(mc,"kotomyaf-back");REPORT.addProperty("kotomyaf_checked",true);job=server.submit(()->{for(int i=0;i<3;i++){equip(ACTORS.get(i),TIERS[i]);ACTORS.get(i).setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));}return true;});phase=11;
            }else if(phase==11){shot(mc,"elytra-folded");job=server.submit(()->{for(var p:ACTORS){p.setPos(p.getX(),203,p.getZ());p.setOnGround(false);p.startFallFlying();}return true;});camera(mc,32,203.6,41,180,5);phase=12;
            }else if(phase==12){for(int i=0;i<3;i++)require(actor(mc,i).isFallFlying(),"Native elytra flight pose required");shot(mc,"elytra-flight");REPORT.addProperty("elytra_checked",true);
                job=server.submit(()->{for(int i=0;i<3;i++){ACTORS.get(i).stopFallFlying();reset(ACTORS.get(i),i);equip(ACTORS.get(i),TIERS[i]);}return true;});mc.options.guiScale().set(4);mc.resizeDisplay();mc.setScreen(new ItemCatalog());phase=13;
            }else if(phase==13){shot(mc,"item-miniatures");mc.setScreen(null);mc.options.fov().set(65);
                job=server.submit(()->{var level=server.overworld();for(int i=3;i<10;i++)ACTORS.add(connect(level,i));for(int i=0;i<10;i++){var p=ACTORS.get(i);equip(p,"aegis");p.setPos(32+(i-4.5)*1.6,200,32);p.setDeltaMovement(Vec3.ZERO);}return true;});camera(mc,32,201.1,46,180,0);phase=14;
            }else if(phase==14){require(mc.level.players().size()>=11,"Ten tracked players plus observer required");beginBench(mc);phase=15;
            }else if(phase==15){if(benchStage<=3)return;replaceLayers(false);REPORT.add("frame_benchmark",benchResults);REPORT.addProperty("benchmark_players",10);REPORT.addProperty("benchmark_vsync",false);REPORT.addProperty("benchmark_frame_cap",260);REPORT.addProperty("gpu",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));shot(mc,"ten-players");finish(mc,true,"");}
        }catch(Throwable e){e.printStackTrace();if(mc.level!=null)shot(mc,"failure");finish(mc,false,e.toString());}
    }
}
