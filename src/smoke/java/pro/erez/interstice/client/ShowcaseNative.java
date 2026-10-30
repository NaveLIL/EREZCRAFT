package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
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
import org.joml.Vector3d;
import org.joml.Quaterniond;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.*;
import pro.erez.interstice.worldgen.terrain.NativeColumnSamplerV6;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;
import pro.erez.interstice.fauna.*;
import pro.erez.interstice.gear.*;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.ship.*;
import pro.erez.interstice.vehicle.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Disposable in-game group photography. All subjects, lights and camera pixels are native Minecraft. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class ShowcaseNative {
    static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("showcase");
    static final List<ServerPlayer> actors=new ArrayList<>();
    static final List<EmbeddedChannel> channels=new ArrayList<>();
    static final List<CaveRiftSpiderEntity> spiders=new ArrayList<>();
    static final List<ServerSubLevel> bodies=new ArrayList<>();
    static final List<BlockPos> origins=new ArrayList<>();
    static final List<EitanController> apcs=new ArrayList<>();
    static final JsonObject report=new JsonObject();
    static final JsonArray shots=new JsonArray();
    static final String[] tiers={"riftsilver","riftsteel","engineer","aegis","kotomyaf","engineer","riftsilver","riftsteel","diamond","diamond"};
    static boolean started,ready,closing,skins,photoTide;static int phase,ticks,loaded,cycle;static long deadline,lastControl=-1;
    static volatile int bx,bz;static ServerLevel world;static UUID viewer;static ArmorStand camera;static Lozhnokoren root;
    static CompletableFuture<?> work;static CompletableFuture<ServerSubLevel> assembly;
    static double camX=35,camY=49,camZ=-47,targetX=0,targetY=40,targetZ=7;static float fov=55;
    static Minecraft mc(){return Minecraft.getInstance();}
    static Path dir(){return mc().gameDirectory.toPath();}
    static void need(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static ServerPlayer viewer(){return mc().getSingleplayerServer().getPlayerList().getPlayer(viewer);}
    static BlockPos pos(int x,int y,int z){return new BlockPos(bx+x,y,bz+z);}
    static void block(int x,int y,int z,Block block){world.setBlock(pos(x,y,z),block.defaultBlockState(),3);}
    static void state(int x,int y,int z,BlockState state){world.setBlock(pos(x,y,z),state,3);}
    static void submit(Runnable r){work=mc().getSingleplayerServer().submit(r);}
    static void saveReport(){try{report.addProperty("phase",phase);report.addProperty("ready",ready);report.addProperty("base_x",bx);report.addProperty("base_z",bz);report.addProperty("owner_profile_used",false);report.addProperty("native_pixels_no_composite",true);report.addProperty("night_vision",false);report.addProperty("staged_photo_not_survival_test",true);report.add("screenshots",shots);Files.writeString(dir().resolve("showcase.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}}
    static void clearSite(BlockPos origin){for(var entity:world.getEntities(null,new net.minecraft.world.phys.AABB(origin).inflate(4,4,7)))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();}
    static int ground(NativeColumnSamplerV6 sampler,int x,int z){for(int y=49;y>=34;y--)if(sampler.nativeDensity(x,y,z,false)>0)return y;return 0;}
    static void chooseBeach(){
        world=mc().getSingleplayerServer().getLevel(IslandWorld.TENSION_WORLD);
        need(world.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&g.terrainRevision()==6,"Actual V6 required");
        var sampler=NativeColumnSamplerV6.of(world.getChunkSource().randomState());double best=-1e9;
        for(int z=-512;z<=512;z+=32)for(int x=-512;x<=512;x+=32){
            boolean sea=true;for(int dx:new int[]{-32,-16,0,16,32})for(int dz:new int[]{-38,-22,-10})for(int y:new int[]{34,38,44})if(sampler.nativeDensity(x+dx,y,z+dz,false)>0)sea=false;
            if(!sea)continue;double score=0;int land=0;
            for(int dx:new int[]{-20,0,20})for(int dz:new int[]{10,25,40}){int h=ground(sampler,x+dx,z+dz);if(h>0){land++;score+=20-Math.abs(h-38);}}
            if(land<6)continue;score-=Math.hypot(x,z)*.003;if(score>best){best=score;bx=x;bz=z;}
        }
        need(best>0,"Natural coast not found");report.addProperty("natural_coast_score",best);
        var p=viewer();p.setGameMode(GameType.SPECTATOR);p.teleportTo(world,bx+20,48,bz-30,Set.of(),0,0);p.hasChangedDimension();p.removeAllEffects();p.setNoGravity(true);
        world.setDayTime(18000);world.setWeatherParameters(120000,0,false,false);
        world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,world.getServer());world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,world.getServer());
        TideManager.getSavedData(world.getServer()).setPhase(TidePhase.CALM,24000);
    }
    static void dressBeach(){
        // Small hand-built expedition campsite on a sampled natural V6 shore, only this disposable world.
        for(int x=-31;x<=31;x++)for(int z=-5;z<=33;z++){
            double edge=29-2*Math.sin(z*.37);if(Math.abs(x)>edge)continue;
            int floor=z<0?34:z<3?35:z<6?36:37;
            for(int y=31;y<=floor;y++)block(x,y,z,y==floor?(z<6?Blocks.GRAVEL:Interstice.ABYSSAL_TURF.get()):Interstice.RIFTSTONE.get());
            for(int y=floor+1;y<=52;y++)block(x,y,z,Blocks.AIR);
        }
        // The assembled ship must start in real air, with actual lower toxin underneath.
        for(int x=-37;x<=0;x++)for(int z=-34;z<=-6;z++)for(int y=35;y<=48;y++)if(!world.getBlockState(pos(x,y,z)).isAir())block(x,y,z,Blocks.AIR);
        for(int x=-8;x<=8;x++)for(int z=5;z<=13;z++){
            block(x,43,z,Interstice.GLOOMCROWN_PLANKS.get());
            if((x==-8||x==8)&&(z==5||z==13))for(int y=38;y<=42;y++)block(x,y,z,Interstice.STRIPPED_GLOOMCROWN_LOG.get());
        }
        // Roof rim, with a shallow pitched centre; open front for the group photograph.
        for(int x=-9;x<=9;x++)for(int z=4;z<=14;z++)if(x==-9||x==9||z==4||z==14)block(x,43,z,Interstice.GLOOMCROWN_LOG.get());
        for(int x=-6;x<=6;x++)for(int z=8;z<=10;z++)block(x,44,z,Interstice.GLOOMCROWN_PLANKS.get());
        for(int x:new int[]{-27,-20,-10,0,10,20,27})for(int z:new int[]{2,18,29}){
            int floor=z<6?36:37;block(x,floor+1,z,Blocks.COBBLED_DEEPSLATE_WALL);block(x,floor+2,z,Blocks.TORCH);
        }
        for(int x:new int[]{-7,-3,2,7})for(int z:new int[]{6,12}){block(x,38,z,Blocks.COBBLED_DEEPSLATE_WALL);block(x,39,z,Blocks.TORCH);}
        for(int x:new int[]{-7,0,7})state(x,42,8,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        // Tidy cargo and authored local flora; all are ordinary registered game blocks.
        block(-6,38,12,Blocks.BARREL);block(-5,38,12,Blocks.CHEST);block(6,38,12,Blocks.CRAFTING_TABLE);block(7,38,12,Blocks.FURNACE);
        block(5,38,10,Blocks.CAMPFIRE);
        for(int x:new int[]{-28,26})for(int z:new int[]{24,30}){for(int y=38;y<=44;y++)block(x,y,z,Interstice.GLOOMCROWN_LOG.get());for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(Math.abs(dx)+Math.abs(dz)<=4)state(x+dx,44,z+dz,Interstice.GLOOMCROWN_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));}
        // Open columns for the two unlucky visitors, not sheltered by distant floating terrain.
        for(int x:new int[]{10,13})for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=40;y<=224;y++)block(x+dx,y,3+dz,Blocks.AIR);
    }
    static String suffix(EquipmentSlot slot){return switch(slot){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    static Item item(String id){return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));}
    static ServerPlayer actor(int i,double x,double y,double z,float yaw){
        UUID id=UUID.nameUUIDFromBytes(("EREZ showcase Steve "+i).getBytes(StandardCharsets.UTF_8));
        var cookie=CommonListenerCookie.createInitial(new GameProfile(id,"ErezFriend"+(i+1)),false);
        var p=new ServerPlayer(world.getServer(),world,cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);NetworkRegistry.configureMockConnection(connection);world.getServer().getPlayerList().placeNewPlayer(connection,p,cookie);
        p.setGameMode(GameType.SURVIVAL);p.teleportTo(world,bx+x,y,bz+z,Set.of(),yaw,0);p.hasChangedDimension();p.setInvulnerable(true);p.setNoGravity(i<8);p.yBodyRot=yaw;p.setYHeadRot(yaw);p.removeAllEffects();
        for(var slot:RealmArmor.SLOTS){String prefix=i>=8?"minecraft:diamond_":"interstice:"+tiers[i]+"_";var it=item(prefix+suffix(slot));need(it!=Items.AIR,"Missing armor "+prefix);p.setItemSlot(slot,new ItemStack(it));}
        if(i<8){String held=switch(i){case 0->"interstice:riftsilver_pickaxe";case 1->"interstice:riftsteel_axe";case 2->"interstice:tide_indicator";case 3->"interstice:riftsteel_longsword";case 4->"minecraft:cod";case 5->"minecraft:torch";case 6->"interstice:riftsilver_shovel";default->"minecraft:torch";};p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item(held)));}
        p.inventoryMenu.broadcastChanges();return p;
    }
    static void cast(){
        var board=world.getServer().getScoreboard();var team=board.addPlayerTeam("erez_photo");team.setNameTagVisibility(Team.Visibility.NEVER);team.setCollisionRule(Team.CollisionRule.NEVER);
        double[][] placements={{-5.7,38,6.8,167},{-3.7,38,7.2,180},{-1.5,38,6.8,191},{1,38,7.1,160},{3.3,38,7.4,173},{5.4,38,8,193},{-3,38,10,165},{1,38,10.2,185},{10,37,3,160},{13,37,3,195}};
        for(int i=0;i<placements.length;i++){var v=placements[i];var p=actor(i,v[0],v[1],v[2],(float)v[3]);actors.add(p);board.addPlayerToTeam(p.getScoreboardName(),team);}
        actors.get(0).setShiftKeyDown(true);actors.get(4).setShiftKeyDown(true);
        root=LozhnokorenFauna.TYPE.get().create(world);root.setNoAi(true);root.setPersistenceRequired();root.setInvulnerable(true);root.moveTo(bx+23,38,bz+21,-30,0);world.addFreshEntity(root);
        for(int i=0;i<3;i++){var s=Interstice.CAVE_RIFT_SPIDER.get().create(world);s.setVariant(CaveRiftSpiderEntity.Variant.values()[i]);s.setNoAi(true);s.setPersistenceRequired();s.setInvulnerable(true);s.moveTo(bx+(i==0?-5:i==1?9:-10),i==2?42.1:38,bz+(i==0?3:i==1?8:9),155+i*55,0);if(i==2){s.setPos(bx-4,42.1,bz+9);s.setNoGravity(true);}world.addFreshEntity(s);spiders.add(s);}
        photoTide=true;TideManager.getSavedData(world.getServer()).setPhase(TidePhase.SURGE,2400);TideSync.broadcast(TideManager.getState(world.getServer()));
        report.addProperty("players",actors.size());report.addProperty("armor_sets",5);report.addProperty("fauna", "LK01 + three original spider species; ceiling attachment is native");
    }
    static BlockPos findOrigin(ServerSubLevel body,boolean skat){var b=body.getPlot().getBoundingBox();for(int x=b.minX();x<=b.maxX();x++)for(int y=b.minY();y<=b.maxY();y++)for(int z=b.minZ();z<=b.maxZ();z++){var p=new BlockPos(x,y,z);var be=world.getBlockEntity(p);if(skat&&be instanceof SkatHelmEntity h)return h.origin();if(!skat&&be instanceof EitanController c){apcs.add(c);return c.origin();}}throw new IllegalStateException("Missing relocated vehicle control");}
    static void poseBody(int i,double x,double y,double z,double degrees){var b=bodies.get(i);var o=origins.get(i);var h=RigidBodyHandle.of(b);need(h.isValid(),"Physics handle not ready");var q=new Quaterniond().rotationY(Math.toRadians(degrees));var center=new Vector3d(b.getMassTracker().getCenterOfMass()).sub(o.getX(),o.getY(),o.getZ());q.transform(center);center.add(bx+x,y,bz+z);h.addLinearAndAngularVelocity(new Vector3d(h.getLinearVelocity()).negate(),new Vector3d(h.getAngularVelocity()).negate());h.teleport(center,q);}
    static void configureVehicles(){
        poseBody(0,-21,35,-16,90);poseBody(1,-15,38,13,145);poseBody(2,15,38,13,-25);
        for(int i=0;i<apcs.size();i++){var c=apcs.get(i);c.engine=true;c.lights=true;c.brake=true;c.fuelTicks=200000;for(int channel=2;channel<=5;channel++)c.target[channel]=1;if(i==0)c.target[0]=1;c.sync();}
        for(int x:new int[]{-3,3})if(world.getBlockEntity(origins.getFirst().offset(x,6,9)) instanceof SkatEngineEntity engine)engine.addFuel(new ItemStack(Items.COAL,64),false);
    }
    static void skinFixtures()throws Exception{var field=AbstractClientPlayer.class.getDeclaredField("playerInfo");field.setAccessible(true);for(var p:actors){var peer=mc().level.getPlayerByUUID(p.getUUID());if(!(peer instanceof AbstractClientPlayer a))return;var info=mc().getConnection().getPlayerInfo(p.getUUID());if(info==null)return;var skin=new PlayerSkin(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"),null,null,null,PlayerSkin.Model.WIDE,true);field.set(a,new PlayerInfo(info.getProfile(),false){@Override public PlayerSkin getSkin(){return skin;}@Override public GameType getGameMode(){return info.getGameMode();}});}skins=true;}
    static void camera(){if(mc().level==null)return;if(camera==null)camera=new ArmorStand(EntityType.ARMOR_STAND,mc().level);camera.setInvisible(true);camera.removeAllEffects();double x=bx+camX,y=camY-camera.getEyeHeight(),z=bz+camZ;double dx=targetX-camX,dy=targetY-camY,dz=targetZ-camZ;float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));camera.setPos(x,y,z);camera.xo=x;camera.yo=y;camera.zo=z;camera.setYRot(yaw);camera.yRotO=yaw;camera.yHeadRot=camera.yHeadRotO=yaw;camera.yBodyRot=camera.yBodyRotO=yaw;camera.setXRot(pitch);camera.xRotO=pitch;mc().setCameraEntity(camera);mc().options.fov().set((int)fov);mc().options.hideGui=true;}
    static void shot(String name){need(name.matches("[a-zA-Z0-9_-]+"),"Safe screenshot name");mc().getEntityRenderDispatcher().setRenderHitBoxes(false);Screenshot.grab(mc().gameDirectory,"showcase-"+name+".png",mc().getMainRenderTarget(),message->{});var row=new JsonObject();row.addProperty("name",name);row.addProperty("camera",camX+","+camY+","+camZ);row.addProperty("target",targetX+","+targetY+","+targetZ);row.addProperty("fov",fov);row.addProperty("width",mc().getMainRenderTarget().width);row.addProperty("height",mc().getMainRenderTarget().height);try{var iris=Class.forName("net.irisshaders.iris.api.v0.IrisApi");row.addProperty("shader_pack_in_use",(Boolean)iris.getMethod("isShaderPackInUse").invoke(iris.getMethod("getInstance").invoke(null)));}catch(Exception ex){throw new IllegalStateException(ex);}row.addProperty("night_vision",mc().player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION));row.addProperty("tide_phase",TideManager.getState(world.getServer()).phase().name());row.addProperty("diamond_1_y",actors.get(8).getY());row.addProperty("diamond_2_y",actors.get(9).getY());shots.add(row);saveReport();}
    static void control()throws Exception{
        var path=dir().resolve("showcase-control.json");if(!Files.isRegularFile(path))return;var c=JsonParser.parseString(Files.readString(path)).getAsJsonObject();long id=c.get("id").getAsLong();if(id<=lastControl)return;lastControl=id;
        if(c.has("camera")){var a=c.getAsJsonArray("camera");camX=a.get(0).getAsDouble();camY=a.get(1).getAsDouble();camZ=a.get(2).getAsDouble();targetX=a.get(3).getAsDouble();targetY=a.get(4).getAsDouble();targetZ=a.get(5).getAsDouble();if(a.size()>6)fov=a.get(6).getAsFloat();}
        if(c.has("shaders")){var cl=Class.forName("net.irisshaders.iris.Iris");var config=cl.getMethod("getIrisConfig").invoke(null);config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,c.get("shaders").getAsBoolean());config.getClass().getMethod("save").invoke(config);cl.getMethod("reload").invoke(null);}
        if(c.has("size")){var a=c.getAsJsonArray("size");org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(),a.get(0).getAsInt(),a.get(1).getAsInt());mc().resizeDisplay();}
        if(c.has("physics")){submit(()->{try{var cl=Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");var system=cl.getMethod("get",net.minecraft.world.level.Level.class).invoke(null,world);WaveShipFixture.call(system,"setPaused",!c.get("physics").getAsBoolean());}catch(Exception ex){throw new IllegalStateException(ex);}});}
        if(c.has("fauna")){var a=c.getAsJsonArray("fauna");submit(()->{LivingEntity p=a.get(0).getAsInt()==3?root:spiders.get(a.get(0).getAsInt());p.moveTo(bx+a.get(1).getAsDouble(),a.get(2).getAsDouble(),bz+a.get(3).getAsDouble(),a.get(4).getAsFloat(),0);p.setYHeadRot(a.get(4).getAsFloat());p.yBodyRot=a.get(4).getAsFloat();p.setNoGravity(true);if(p instanceof CaveRiftSpiderEntity s)s.travel(Vec3.ZERO);if(p instanceof Lozhnokoren l&&a.size()>5)try{var m=Lozhnokoren.class.getDeclaredMethod("phase",Lozhnokoren.Phase.class);m.setAccessible(true);m.invoke(l,Lozhnokoren.Phase.valueOf(a.get(5).getAsString()));}catch(Exception ex){throw new IllegalStateException(ex);}});}
        if(c.has("actor")){var a=c.getAsJsonArray("actor");submit(()->{var p=actors.get(a.get(0).getAsInt());p.teleportTo(world,bx+a.get(1).getAsDouble(),a.get(2).getAsDouble(),bz+a.get(3).getAsDouble(),Set.of(),a.get(4).getAsFloat(),0);p.setYHeadRot(a.get(4).getAsFloat());p.yBodyRot=a.get(4).getAsFloat();p.setDeltaMovement(Vec3.ZERO);if(a.size()>5)p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(item(a.get(5).getAsString())));if(a.size()>6)p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item(a.get(6).getAsString())));if(a.size()>7)p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(item(a.get(7).getAsString())));if(a.size()>8&&a.get(8).getAsBoolean())p.swing(InteractionHand.MAIN_HAND);p.inventoryMenu.broadcastChanges();});}
        if(c.has("body")){var a=c.getAsJsonArray("body");submit(()->poseBody(a.get(0).getAsInt(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble()));}
        if(c.has("blocks")){var rows=c.getAsJsonArray("blocks");submit(()->{for(var entry:rows){var a=entry.getAsJsonArray();var b=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(a.get(3).getAsString()));block(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),b);}});}
        if(c.has("tide")){photoTide=c.get("tide").getAsBoolean();}
        if(c.has("release"))submit(()->{cycle=0;for(int i=8;i<10;i++){var p=actors.get(i);p.teleportTo(world,bx+(i==8?10:13),37,bz+3,Set.of(),170,0);p.setDeltaMovement(Vec3.ZERO);}});
        if(c.has("shot")){shot(c.get("shot").getAsString());}
        if(c.has("finish")&&c.get("finish").getAsBoolean()){submit(()->{try{WaveShipFixture.pause(world);WaveShipFixture.flush(world);}catch(Exception e){throw new IllegalStateException(e);}world.getServer().saveEverything(false,true,true);});closing=true;}
        report.addProperty("last_control",id);saveReport();
    }
    @SubscribeEvent public static void server(ServerTickEvent.Post e){if(!ACTIVE||closing)return;
        for(int i=0;i<actors.size();i++){var p=actors.get(i);p.connection.tick();Object packet;while((packet=channels.get(i).readOutbound())!=null){if(packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket a)p.connection.handleKeepAlive(new net.minecraft.network.protocol.common.ServerboundKeepAlivePacket(a.getId()));else if(packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket a)p.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(a.getId()));io.netty.util.ReferenceCountUtil.release(packet);}}
        if(!ready||world==null)return;world.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false,e.getServer());if(world.getGameTime()%40==0)for(int x=-31;x<=31;x++)for(int z=-5;z<=33;z++)for(int y=35;y<=48;y++)if(world.getBlockState(pos(x,y,z)).is(Blocks.FIRE))block(x,y,z,Blocks.AIR);
        if(world.getGameTime()%60==0)actors.get(5).swing(InteractionHand.MAIN_HAND);if(world.getGameTime()%60==30)actors.get(6).swing(InteractionHand.MAIN_HAND);
        if(photoTide){if(TideManager.getState(e.getServer()).phase()!=TidePhase.SURGE||TideManager.getState(e.getServer()).progress()>.65){TideManager.getSavedData(e.getServer()).setPhase(TidePhase.SURGE,2400);TideSync.broadcast(TideManager.getState(e.getServer()));}cycle++;
            for(int i=8;i<10;i++){var p=actors.get(i);if(cycle==1||p.getY()>49){p.teleportTo(world,bx+(i==8?10:13),37,bz+3,Set.of(),i==8?160:205,0);p.setDeltaMovement(Vec3.ZERO);}BuoyancyController.applyEntityBuoyancy(p,TideManager.getState(e.getServer()).buoyancyIntensity());p.travel(Vec3.ZERO);p.hurtMarked=true;}
        }
        if(world.getGameTime()%20==0){var proof=new JsonObject();proof.addProperty("tide_intensity",TideManager.getState(e.getServer()).buoyancyIntensity());var a=new JsonArray();for(var p:actors){var row=new JsonObject();row.addProperty("name",p.getScoreboardName());row.addProperty("y",p.getY());row.addProperty("sheltered",ShelterDetector.isSheltered(world,p));row.addProperty("gravity",p.getAttributeValue(Attributes.GRAVITY));row.addProperty("no_gravity",p.isNoGravity());a.add(row);}proof.add("actors",a);proof.addProperty("ceiling_spider",spiders.get(2).isClimbingCeiling());proof.addProperty("ceiling_support",spiders.get(2).hasCeilingAbove());proof.addProperty("ceiling_position",spiders.get(2).position().toString());proof.addProperty("ceiling_box",spiders.get(2).getBoundingBox().toString());proof.addProperty("root_y",root.getY());proof.addProperty("root_phase",root.phase().name());var vehicles=new JsonArray();for(var c:apcs){var vehicle=new JsonObject();vehicle.addProperty("engine",c.engine);vehicle.addProperty("lights",c.lights);var motion=new JsonArray();for(float v:c.progress)motion.add(v);vehicle.add("mechanism_progress",motion);vehicles.add(vehicle);}proof.add("eitan",vehicles);boolean nv=false;for(var p:actors)nv|=p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);proof.addProperty("crew_night_vision",nv);report.add("live",proof);saveReport();}
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post e){if(!ACTIVE)return;try{
        if(closing){if(work!=null&&!work.isDone())return;if(work!=null)work.join();report.addProperty("finished",true);saveReport();if(mc().level!=null)mc().level.disconnect();mc().disconnect(new TitleScreen());mc().stop();return;}
        if(!started&&mc().screen instanceof TitleScreen&&mc().getOverlay()==null){need(dir().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Disposable profile required");boolean resume=Files.exists(dir().resolve("saves/showcase-owned"));if(resume)need(Files.isRegularFile(dir().resolve("showcase-automated-profile")),"Only this disposable photograph world may resume");Files.writeString(dir().resolve("showcase-automated-profile"),"Disposable photograph scene, no owner world");started=true;deadline=System.nanoTime()+3_600_000_000_000L;for(var id:List.of("distanthorizons","sable","create","offroad","iris"))need(ModList.get().isLoaded(id),"Full pack missing "+id);report.addProperty("dh_loaded",true);report.addProperty("pid",ProcessHandle.current().pid());mc().options.pauseOnLostFocus=false;mc().options.hideGui=true;mc().options.renderDistance().set(10);mc().options.simulationDistance().set(6);mc().options.bobView().set(false);mc().options.fovEffectScale().set(0D);mc().options.gamma().set(1D);mc().options.setCameraType(CameraType.FIRST_PERSON);var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);if(resume){phase=20;mc().createWorldOpenFlows().openWorld("showcase-owned",()->{});return;}mc().createWorldOpenFlows().createFreshLevel("showcase-owned",new LevelSettings("Междуморье · фото друзей",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261030L,false,false),WorldPresets::createNormalWorldDimensions,mc().screen);return;}
        if(!started)return;need(System.nanoTime()<deadline,"Photographic session timeout");if(SmokeWorldPrompts.advance(mc())||mc().level==null||mc().player==null||mc().getSingleplayerServer()==null)return;
        if(work!=null&&!work.isDone())return;if(work!=null){work.join();work=null;}
        if(phase==20){viewer=mc().player.getUUID();submit(()->{JsonObject old;try{old=JsonParser.parseString(Files.readString(dir().resolve("showcase.json"))).getAsJsonObject();}catch(Exception ex){throw new IllegalStateException(ex);}bx=old.get("base_x").getAsInt();bz=old.get("base_z").getAsInt();world=mc().getSingleplayerServer().getLevel(IslandWorld.TENSION_WORLD);viewer().setGameMode(GameType.SPECTATOR);viewer().teleportTo(world,bx+20,48,bz-30,Set.of(),0,0);viewer().hasChangedDimension();viewer().removeAllEffects();});phase=21;ticks=0;return;}
        if(phase==21){if(++ticks<100)return;submit(()->{var all=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(world).getAllSubLevels();need(all.size()==1,"Expected one saved Skat in interrupted scene: "+all.size());var b=all.getFirst();bodies.add(b);origins.add(findOrigin(b,true));});phase=4;ticks=0;return;}
        if(phase==0){viewer=mc().player.getUUID();submit(ShowcaseNative::chooseBeach);phase=1;saveReport();return;}
        if(phase==1){if(loaded<169){submit(()->{for(int i=0;i<2&&loaded<169;i++,loaded++)world.getChunk((bx>>4)+loaded%13-6,(bz>>4)+loaded/13-6);});return;}submit(ShowcaseNative::dressBeach);phase=2;saveReport();return;}
        if(phase==2){submit(()->assembly=SkatAssembly.create(world,pos(-18,35,-19),viewer));phase=3;return;}
        if(phase==3||phase==5||phase==7){if(!assembly.isDone())return;var b=assembly.join();submit(()->{bodies.add(b);origins.add(findOrigin(b,bodies.size()==1));});phase++;ticks=0;saveReport();return;}
        if(phase==4){if(++ticks<60)return;submit(()->{clearSite(pos(-15,38,14));assembly=EitanAssembly.create(world,pos(-15,38,14),viewer);});phase=5;return;}
        if(phase==6){if(++ticks<60)return;submit(()->{clearSite(pos(15,38,14));assembly=EitanAssembly.create(world,pos(15,38,14),viewer);});phase=7;return;}
        if(phase==8){if(++ticks<60)return;submit(()->{configureVehicles();cast();});phase=9;ticks=0;return;}
        camera();if(!skins&&actors.size()==10)skinFixtures();
        if(phase==9){if(++ticks<600)return;submit(()->{try{WaveShipFixture.pause(world);}catch(Exception ex){throw new IllegalStateException(ex);}need(actors.subList(0,8).stream().allMatch(p->ShelterDetector.isSheltered(world,p)),"Crew not protected by actual shelter");});ready=true;phase=10;ticks=0;saveReport();return;}
        if(ready){if(++ticks==60)shot("draft");if(ticks%10==0)control();}
    }catch(Throwable ex){ex.printStackTrace();report.addProperty("error",ex.toString());saveReport();work=null;closing=true;}}
}
