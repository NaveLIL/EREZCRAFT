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
public final class FirstPersonCalibration {
    private static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("fpcalibrate");
    private static final String[] TIERS={"iron","riftsilver","riftsteel"};
    private static final String[] NAMES={"ReferenceIron","ReferenceSilver","ReferenceSteel"};
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
    private FirstPersonCalibration(){}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static UUID profile(String texture,int offset){
        for(int i=offset;i<offset+1000;i++){
            UUID id=new UUID(0x1133557700004000L,0x8000000000000000L+i);
            if(DefaultPlayerSkin.get(id).texture().getPath().endsWith(texture))return id;
        }throw new IllegalStateException("No selected native default skin");
    }
    private static String kind(EquipmentSlot s){return switch(s){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    private static final String[] TOOLS={"iron_pickaxe","riftsilver_pickaxe","riftsteel_pickaxe","iron_axe","riftsilver_axe","riftsteel_axe","iron_shovel","riftsilver_shovel","riftsteel_shovel","iron_hoe","riftsilver_hoe","riftsteel_hoe"};
    private static final int[] QUADS={198,138,120,114,210,150,132,126};
    private static final float[][] FP_PLAN={{.496F,-.64F,2.87242F,-2.4F,-45},{.68F,-4,-1.6F,1.13F,-45},{.8F,-4,-3.2F,1.13F,-45},{.68F,-3,0,0,-45},{.8F,-3,-1.6F,0,-45},{.68F,-2,0,0,-60},{.8F,-2,-1.6F,0,-60},{.85F,-2,-2.5F,-.8F,-45},{.68F,-4,0,1.13F,-45}};
    private static int fpTool(int index){return index==0?6:8;}
    private static String fpLabel(int index){return index==0?"vanilla-shovel":"candidate-"+(index-1);}
    private static int group,firstIndex;
    private static int current(int i){return group*3+i;}
    private static net.minecraft.world.item.Item tool(int i){return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TOOLS[i].startsWith("iron_")?"minecraft":Interstice.ID,TOOLS[i]));}
    private static void equip(ServerPlayer p,String tier){int i=Arrays.asList(TIERS).indexOf(tier);for(var slot:RealmArmor.SLOTS)p.setItemSlot(slot,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(tool(current(i))));p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();}
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
    private static void skinFixtures(Minecraft mc)throws Exception{
        // Only the three owned test PlayerInfo entries: no account skin, external skin service or armor renderer changes.
        var cache=AbstractClientPlayer.class.getDeclaredField("playerInfo");cache.setAccessible(true);
        for(int i=0;i<3;i++){
            var info=mc.getConnection().getPlayerInfo(profiles[i]);require(info!=null,"Missing test player info");
            boolean slim=false;
            var skin=new PlayerSkin(net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/player/"+(slim?"slim/alex.png":"wide/steve.png")),null,null,null,slim?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,true);
            var fixture=new net.minecraft.client.multiplayer.PlayerInfo(info.getProfile(),false){
                @Override public PlayerSkin getSkin(){return skin;}
                @Override public GameType getGameMode(){return info.getGameMode();}
            };
            cache.set(actor(mc,i),fixture);
        }
        REPORT.addProperty("skin_fixture","All three reference players use the same Steve/WIDE body");
    }
    private static void shot(Minecraft mc,String name){
        Screenshot.grab(mc.gameDirectory,"reference-"+name+".png",mc.getMainRenderTarget(),m->System.out.println("ARMOR_GALLERY "+name));SHOTS.add("reference-"+name+".png");
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
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("first-person-calibration.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}

    private static JsonArray vec(org.joml.Vector3f v){var a=new JsonArray();a.add(v.x());a.add(v.y());a.add(v.z());return a;}
    private static void measure(Minecraft mc){
        var all=new JsonArray();
        for(int i=0;i<TOOLS.length;i++){
            var stack=new ItemStack(tool(i));var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
            require(model!=mc.getModelManager().getMissingModel(),"Missing reference model "+TOOLS[i]);
            var quads=new ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
            quads.addAll(model.getQuads(null,null,net.minecraft.util.RandomSource.create(0)));
            for(var d:Direction.values())quads.addAll(model.getQuads(null,d,net.minecraft.util.RandomSource.create(0)));
            var vertices=new ArrayList<org.joml.Vector3f>();
            for(var q:quads){
                require(!q.getSprite().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Missing texture "+TOOLS[i]);
                // Sprite front/back canvases include transparent pixels; side boundary quads trace the actual opaque silhouette.
                if(q.getDirection()==Direction.NORTH||q.getDirection()==Direction.SOUTH)continue;
                int[] data=q.getVertices();int stride=data.length/4;
                for(int v=0;v<4;v++)vertices.add(new org.joml.Vector3f(Float.intBitsToFloat(data[v*stride]),Float.intBitsToFloat(data[v*stride+1]),Float.intBitsToFloat(data[v*stride+2])));
            }
            require(!vertices.isEmpty(),"Opaque-boundary vertices required");
            var row=new JsonObject();row.addProperty("item",TOOLS[i]);row.addProperty("baked_quads",quads.size());
            for(var context:List.of(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)){
                var t=model.getTransforms().getTransform(context);var pose=new com.mojang.blaze3d.vertex.PoseStack();t.apply(false,pose);pose.translate(-.5,-.5,-.5);
                var pts=new ArrayList<org.joml.Vector3f>();float diameter=0,reach=0;var min=new org.joml.Vector3f(Float.POSITIVE_INFINITY);var max=new org.joml.Vector3f(Float.NEGATIVE_INFINITY);
                for(var v:vertices){var p=pose.last().pose().transformPosition(new org.joml.Vector3f(v));pts.add(p);min.min(p);max.max(p);reach=Math.max(reach,p.length());}
                for(int j=0;j<pts.size();j++)for(int k=0;k<j;k++)diameter=Math.max(diameter,pts.get(j).distance(pts.get(k)));
                var r=new JsonObject();r.add("scale",vec(t.scale));r.add("rotation",vec(t.rotation));r.add("translation",vec(t.translation));r.add("extent_model_units",vec(max.sub(min).mul(16)));r.addProperty("opaque_geometry_diameter_model_units",diameter*16);r.addProperty("max_distance_from_hand_origin_model_units",reach*16);row.add(context.name(),r);
            }all.add(row);
        }REPORT.add("native_metrics",all);
        var ratios=new JsonArray();
        for(int group=0;group<4;group++)for(int tier=1;tier<3;tier++){
            var iron=all.get(group*3).getAsJsonObject();var own=all.get(group*3+tier).getAsJsonObject();var ratioRow=new JsonObject();ratioRow.addProperty("item",own.get("item").getAsString());
            for(var context:List.of(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)){
                double ratio=own.getAsJsonObject(context.name()).get("opaque_geometry_diameter_model_units").getAsDouble()/iron.getAsJsonObject(context.name()).get("opaque_geometry_diameter_model_units").getAsDouble();
                require(Math.abs(ratio-1)<.02,"Vanilla size calibration "+own.get("item")+" "+context+" ratio="+ratio);ratioRow.addProperty(context.name(),ratio);
            }ratios.add(ratioRow);
        }REPORT.add("vanilla_calibration_ratios",ratios);REPORT.addProperty("calibrated_to_vanilla",true);REPORT.addProperty("measurement_note","Vanilla opaque boundary excludes transparent sprite canvases; metrics include each item's actual baked display transform. Different silhouette shapes and grips mean diameter is not shaft length.");
    }
    private static void groupItems(Minecraft mc){job=mc.getSingleplayerServer().submit(()->{for(int i=0;i<3;i++){reset(ACTORS.get(i),i);equip(ACTORS.get(i),TIERS[i]);}return true;});ticks=0;}
    private static void closeItem(Minecraft mc,int index){
        job=mc.getSingleplayerServer().submit(()->{for(int i=0;i<3;i++){reset(ACTORS.get(i),i);if(i>0)ACTORS.get(i).setPos(48,200,48);}ACTORS.get(0).setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(tool(index)));return true;});
        camera(mc,X[0]-2.4,201.02,27.8,-29.75F,0);
    }
    private static void firstItem(Minecraft mc,int index){if(index>0){var f=FP_PLAN[index-1];var model=mc.getItemRenderer().getModel(new ItemStack(tool(8)),mc.level,mc.player,0);var t=model.getTransforms().getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);t.scale.set(f[0]);t.translation.set(f[1]/16,f[2]/16,f[3]/16);t.rotation.set(0,f[4],-20);}job=mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(observer);p.getInventory().selected=0;p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(tool(fpTool(index))));p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));return true;});ticks=0;}
    private static class Catalog extends net.minecraft.client.gui.screens.Screen{
        Catalog(){super(net.minecraft.network.chat.Component.literal("VANILLA IRON / SILVER FP CALIBRATION / RIFTSTEEL FP CALIBRATION"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float dt){
            g.fill(0,0,width,height,0xff17232d);g.drawCenteredString(font,title,width/2,12,0xffdeecee);
            String[] kinds={"Pickaxe","Axe","Shovel","Hoe"};String[] tiers={"Vanilla iron","FP CALIBRATION silver","FP CALIBRATION riftsteel"};
            for(int tier=0;tier<3;tier++)for(int kind=0;kind<4;kind++){
                int x=(int)(width*((kind+.5)/4)),y=38+tier*145;var stack=new ItemStack(tool(kind*3+tier));
                g.drawCenteredString(font,tiers[tier]+" / "+kinds[kind],x,y,0xffbcd1d9);
                g.pose().pushPose();g.pose().translate(x-32,y+17,0);g.pose().scale(4,4,4);g.renderItem(stack,0,0);g.pose().popPose();g.renderItem(stack,x-8,y+95);
            }
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){if(job!=null&&job.isDone())job.join();if(settling.failed())throw new IllegalStateException(settling.failure());if(settling.ready()){REPORT.add("settled",settling.report());finished=true;write(mc);mc.stop();}return;}
            if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned isolated profile required");
                require(ModList.get().isLoaded("distanthorizons")&&ModList.get().isLoaded("create")&&ModList.get().isLoaded("sable"),"Full pack required");
                started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(8);profiles=new UUID[]{profile("wide/steve.png",10),profile("wide/steve.png",100),profile("wide/steve.png",200)};
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.fov().set(42);mc.options.fovEffectScale().set(0D);mc.options.bobView().set(false);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/vanilla-tool-reference")),"Fresh comparison required");
                mc.createWorldOpenFlows().createFreshLevel("vanilla-tool-reference",new LevelSettings("Owned vanilla tool comparison",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261027L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started)return;require(System.nanoTime()<deadline,"Reference deadline phase="+phase);
            if(SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(skinsFixed)for(int i=0;i<3;i++){var p=actor(mc,i);p.yBodyRot=p.yBodyRotO=p.yHeadRot=p.yHeadRotO=180;}
            var server=mc.getSingleplayerServer();
            if(phase==0){observer=mc.player.getUUID();job=server.submit(()->{prepare(server.getPlayerList().getPlayer(observer));return true;});phase=1;ticks=0;return;}
            if(job!=null&&!job.isDone())return;if(job!=null)job.join();if(++ticks<(phase==1?70:24))return;ticks=0;
            if(phase==1){
                if(mc.level.getPlayerByUUID(profiles[0])==null||mc.level.getPlayerByUUID(profiles[1])==null||mc.level.getPlayerByUUID(profiles[2])==null)return;
                if(!skinsFixed){skinFixtures(mc);skinsFixed=true;measure(mc);}
                for(int i=0;i<3;i++)require(actor(mc,i).getMainHandItem().is(tool(current(i))),"Tracked group item");
                REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("distant_horizons_loaded",true);REPORT.addProperty("mod_version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());
                shot(mc,"group-"+group);if(++group<4)groupItems(mc);else{firstIndex=0;closeItem(mc,firstIndex);phase=2;}
            }else if(phase==2){
                require(actor(mc,0).getMainHandItem().is(tool(firstIndex)),"Same-player held item synchronized");shot(mc,"held-"+TOOLS[firstIndex]);
                if(++firstIndex<TOOLS.length)closeItem(mc,firstIndex);else{
                    job=server.submit(()->{for(var p:ACTORS)p.setPos(48,200,48);var p=server.getPlayerList().getPlayer(observer);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setInvulnerable(true);p.getInventory().clearContent();return true;});
                    mc.options.hideGui=false;REPORT.addProperty("probe_only",true);REPORT.add("firstperson_candidates",new Gson().toJsonTree(FP_PLAN));mc.options.fov().set(70);camera(mc,32,201.62,25,0,15);firstIndex=0;firstItem(mc,firstIndex);phase=3;
                }
            }else if(phase==3){
                require(!mc.options.hideGui,"Hands visible");require(mc.player.getMainHandItem().is(tool(fpTool(firstIndex))),"Same-observer first-person item synchronized");shot(mc,"first-"+fpLabel(firstIndex));
                if(++firstIndex<=FP_PLAN.length)firstItem(mc,firstIndex);else{REPORT.addProperty("visible_first_person_views",FP_PLAN.length+1);finish(mc,true,"");}
            }else if(phase==4){shot(mc,"catalog");REPORT.addProperty("minecraft_version",net.minecraft.SharedConstants.getCurrentVersion().getName());finish(mc,true,"");}
        }catch(Throwable e){e.printStackTrace();if(mc.level!=null)shot(mc,"failure");finish(mc,false,e.toString());}
    }
}
