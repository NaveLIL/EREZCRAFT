package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ship.*;
import pro.erez.interstice.worldgen.*;

/** Actual full-pack ship trial; separate from the protected ocean owner scene. */
public final class SkatNative {
    public static void register(){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(SkatNative.class);}
    private static int highlightChecks;
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST,receiveCanceled=true) public static void selection(net.neoforged.neoforge.client.event.RenderHighlightEvent.Block e){if(!ACTIVE||!FLEET||Minecraft.getInstance().level==null)return;var w=Minecraft.getInstance().level;if(pro.erez.interstice.vehicle.VehicleAccess.part(w.getBlockState(e.getTarget().getBlockPos()))){if(!e.isCanceled())throw new IllegalStateException("Vehicle cell highlight leaked");highlightChecks++;REPORT.addProperty("native_vehicle_highlight_cancelled",highlightChecks);}}
    private static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("skat");
    private static final String MODE=System.getProperty("interstice.skatPhase","create");
    private static final boolean FLEET=Boolean.getBoolean("interstice.skatFleet");
    private static final boolean NEW=MODE.equals("create")||MODE.equals("thrust-create");
    private static final boolean THRUST=MODE.startsWith("thrust");
    private static final JsonObject REPORT=new JsonObject();
    private static JsonObject prior;private static boolean survivalExpected,reviewReady;
    private static int phase,ticks,chunks,stopTicks,loginTicks;private static boolean started,closing;private static long deadline,since,observeAfter;
    private static ServerLevel world;private static ServerSubLevel ship;private static BlockPos origin;
    private static JsonObject readJson(java.nio.file.Path path){try{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    private static BlockPos trialSpawn=new BlockPos(43,35,14);
    private static BlockPos findSeaRoute(){
        var sampler=pro.erez.interstice.worldgen.terrain.NativeColumnSamplerV6.of(world.getChunkSource().randomState());
        var random=new java.util.Random(20261030L);
        for(int attempt=0;attempt<4096;attempt++){
            int x=(random.nextInt(101)-50)*128,z=(random.nextInt(101)-50)*128;
            if(!seaRectangle(sampler,x,z,64)||!seaRectangle(sampler,x,z,8))continue;
            REPORT.addProperty("natural_sea_route_attempts",attempt+1);
            REPORT.addProperty("natural_sea_route_origin",x+",35,"+z);
            REPORT.addProperty("natural_sea_route_width",192);
            REPORT.addProperty("natural_sea_route_length",640);
            REPORT.addProperty("natural_sea_route_density_checked",true);
            REPORT.addProperty("terrain_cleared_for_trial",false);
            return new BlockPos(x,35,z);
        }
        throw new IllegalStateException("No natural open-water rectangle found");
    }
    private static boolean seaRectangle(pro.erez.interstice.worldgen.terrain.NativeColumnSamplerV6 sampler,int x,int z,int step){
        for(int dz=64;dz>=-576;dz-=step)for(int dx=-96;dx<=96;dx+=step)for(int y=34;y<=50;y+=4)
            if(sampler.nativeDensity(x+dx,y,z+dz,true)>=0)return false;
        return true;
    }
    private static CompletableFuture<?> work;private static Vector3d before;private static Quaterniond heading;
    private static long bodyLoadStarted=Long.MIN_VALUE;
    private static boolean coldPowered;private static float maxHudSpeed;private static boolean speedHudCaptured;
    private static final JsonArray propulsionSamples=new JsonArray();
    private static Vector3d sampledAt;private static long sampledTick;private static double drivenPath;
    private static double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxTilt;
    private static final List<UUID> inventories=new ArrayList<>();private static NativeChunkSettler.Session settling;
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static ServerPlayer player(){return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();}
    private static void submit(Runnable run){work=Minecraft.getInstance().getSingleplayerServer().submit(run);}
    private static void report(){try{REPORT.addProperty("phase",phase);REPORT.addProperty("mode",MODE);REPORT.addProperty("owner_profile_used",false);REPORT.addProperty("max_tilt_all_phases",maxTilt);if(THRUST){REPORT.add("propulsion_samples",propulsionSamples);REPORT.addProperty("driven_path_m",drivenPath);}Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("skat-"+MODE+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"skat-"+name+".png",mc.getMainRenderTarget(),m->{});}
    private static void finish(boolean passed,String error){if(ship!=null&&!ship.isRemoved()){var at=ship.logicalPose().position();REPORT.addProperty("saved_world_x",at.x());REPORT.addProperty("saved_world_z",at.z());}if(MODE.equals("glass")||MODE.equals("speed"))REPORT.add("glass",SkatGlassNative.report());LowerToxinRenderer.VERIFY_LATE_PASS=false;REPORT.addProperty("passed",passed);REPORT.addProperty("error",error);report();closing=true;var mc=Minecraft.getInstance();if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());}
    private static SkatHelmEntity helm(){var h=(SkatHelmEntity)world.getBlockEntity(origin.offset(0,4,-6));if(h!=null&&h.security.owner()==null)h.security.claim(player().getUUID());return h;}
    private static void findOrigin(){
        var b=ship.getPlot().getBoundingBox();for(int x=b.minX();x<=b.maxX();x++)for(int y=b.minY();y<=b.maxY();y++)for(int z=b.minZ();z<=b.maxZ();z++)if(world.getBlockEntity(new BlockPos(x,y,z)) instanceof SkatHelmEntity h){origin=h.origin();return;}
        throw new IllegalStateException("Missing moved helm BE");
    }
    private static Vector3d global(double x,double y,double z){return ship.logicalPose().transformPosition(new Vector3d(origin.getX()+x,origin.getY()+y,origin.getZ()+z),new Vector3d());}
    private static void inventoryCheck(){
        int slots=0;var ids=new HashSet<UUID>();for(var c:SkatData.CARGO){var be=world.getBlockEntity(origin.offset(c.anchor()));require(be instanceof SkatCargoEntity,"Cargo BE missing "+c.index());var cargo=(SkatCargoEntity)be;slots+=cargo.getContainerSize();ids.add(cargo.inventoryId());require(cargo.getItem(0).is(Items.DIAMOND)&&cargo.getItem(0).getCount()==c.index()+1,"Cargo altered "+c.index());require(cargo.getItem(53).is(Items.IRON_INGOT)&&cargo.getItem(53).getCount()==64,"Last slot lost");}
        require(ids.size()==SkatData.CARGO.size()&&slots==SkatData.CARGO.size()*54,"Capacity/identity mismatch");if(!inventories.isEmpty())require(ids.equals(new HashSet<>(inventories)),"Container UUID changed during movement");
        REPORT.addProperty("cargo_containers",ids.size());REPORT.addProperty("cargo_slots",slots);
    }
    private static void structureCheck(){
        for(var cell:SkatData.CELLS){var expected=SkatShips.state(cell);var actual=world.getBlockState(origin.offset(cell.pos()));require(actual.is(expected.getBlock()),"Authored ship cell lost: "+cell.pos()+" "+actual);
            if(expected.hasProperty(SkatData.CELL))require(actual.getValue(SkatData.CELL).equals(cell.id()),"Authored cell identity changed: "+cell.pos());
        }REPORT.addProperty("structure_cells_checked",SkatData.CELLS.size());REPORT.addProperty("mass",ship.getMassTracker().getMass());
    }
    private static boolean restorePending;
    private static long fuelBudget(){long value=0;for(int x:new int[]{-3,3})value+=((SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9))).fuelTicks();if(FLEET){var bunker=(SkatCargoEntity)world.getBlockEntity(origin.offset(SkatData.CARGO.getFirst().anchor()));for(int slot=0;slot<bunker.getContainerSize();slot++){var stack=bunker.getItem(slot);if(stack.is(Items.COAL))value+=(long)stack.getCount()*1600;}}return value;}
    private static void initializeHull(){
            inventoryCheck();REPORT.addProperty("lighting_loaded_mask",helm().lighting());if(MODE.equals("reload")&&prior!=null&&prior.has("lighting_saved_mask"))require(helm().lighting()==prior.get("lighting_saved_mask").getAsInt(),"Lighting changed during cold reload");since=world.getGameTime();REPORT.addProperty("ship_uuid",ship.getUniqueId().toString());REPORT.addProperty("mass",ship.getMassTracker().getMass());for(int x:new int[]{-3,3})REPORT.addProperty("initial_fuel_"+x,((SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9))).fuelTicks());
            for(int x:new int[]{-3,3}){var prop=world.getBlockEntity(origin.offset(x,6,10));require(prop instanceof SkatPropellerEntity,"Saved native propeller is missing");REPORT.addProperty("loaded_engine_saved_rpm_"+x,((SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9))).loadedKineticSpeed());REPORT.addProperty("loaded_propeller_saved_rpm_"+x,((SkatPropellerEntity)prop).loadedKineticSpeed());if(MODE.equals("reload")&&prior!=null&&prior.has("cold_powered_drivetrain")){require(Math.abs(((SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9))).loadedKineticSpeed())>1&&Math.abs(((SkatPropellerEntity)prop).loadedKineticSpeed())>1,"Powered drivetrain snapshot was not really persisted");require(((SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9))).getSpeed()==0&&((SkatPropellerEntity)prop).getSpeed()==0,"Loaded drivetrain is not neutral");}}structureCheck();
            if(Boolean.getBoolean("interstice.skatFixtureReset")){require(MODE.equals("resume"),"Fixture reset is preparation only");REPORT.addProperty("fixture_pose_before_reset",ship.logicalPose().toString());var handle=dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);require(handle.isValid(),"Native physics handle not ready");var center=new Vector3d(ship.getMassTracker().getCenterOfMass()).sub(origin.getX(),origin.getY(),origin.getZ()).add(43,35,14);handle.addLinearAndAngularVelocity(new Vector3d(handle.getLinearVelocity()).negate(),new Vector3d(handle.getAngularVelocity()).negate());handle.teleport(center,new Quaterniond());observeAfter=world.getGameTime()+10;minY=Double.POSITIVE_INFINITY;maxY=Double.NEGATIVE_INFINITY;maxTilt=0;REPORT.addProperty("fixture_capsize_reset_before_test",true);}
            for(int x:new int[]{-3,3}){var engine=(SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9));if(!FLEET&&engine.fuelTicks()<(THRUST?1600:800)){engine.addFuel(new ItemStack(Items.COAL,1),false);REPORT.addProperty("test_refuel_"+x,true);}}
            REPORT.addProperty("initial_total_fuel_budget",fuelBudget());var ids=new JsonArray();inventories.forEach(id->ids.add(id.toString()));REPORT.add("inventory_ids",ids);
    }
    private static boolean plotReady(){for(var c:SkatData.CELLS)if(world.getBlockState(origin.offset(c.pos())).isAir())return false;for(var c:SkatData.CARGO)if(!(world.getBlockEntity(origin.offset(c.anchor())) instanceof SkatCargoEntity))return false;for(int x:new int[]{-3,3})if(!(world.getBlockEntity(origin.offset(x,6,9)) instanceof SkatEngineEntity)||!(world.getBlockEntity(origin.offset(x,6,10)) instanceof SkatPropellerEntity))return false;return true;}
    @SubscribeEvent public static void server(ServerTickEvent.Post event){
        if(!ACTIVE||MODE.equals("review"))return;
        if(MODE.equals("reload")&&phase==0&&!closing&&!event.getServer().getPlayerList().getPlayers().isEmpty()){var login=event.getServer().getPlayerList().getPlayers().getFirst();if(login.serverLevel().dimension().equals(IslandWorld.TENSION_WORLD)){var trace=REPORT.has("cold_login_trace")?REPORT.getAsJsonArray("cold_login_trace"):new JsonArray();if(trace.size()<80){var item=new JsonObject();item.addProperty("tick",login.level().getGameTime());item.addProperty("health",login.getHealth());item.addProperty("position",login.position().toString());item.addProperty("frozen_ship",String.valueOf(((dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension)login).sable$getFrozenToSubLevel()));item.addProperty("tracking",String.valueOf(((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)login).sable$getTrackingSubLevel()));trace.add(item);REPORT.add("cold_login_trace",trace);REPORT.addProperty("cold_login_dimension_restores",SkatPlayerLogin.restores);}}}
        if(world==null)return;
        if(coldPowered)for(int x:new int[]{-3,3})if(world.getBlockEntity(origin.offset(x,6,9)) instanceof SkatEngineEntity engine)engine.throttle(96);
        if(closing)return;
        if(phase==7)SkatSystemsNative.serverTick();
        if(settling!=null)settling.tick(Minecraft.getInstance().getSingleplayerServer());
        if(chunks<289){for(int i=0;i<2&&chunks<289;i++,chunks++)world.getChunk((trialSpawn.getX()>>4)+chunks%17-8,(trialSpawn.getZ()>>4)+chunks/17-8);return;}
        if(ship!=null&&world.getGameTime()>=observeAfter){if(player().isPassenger())require(Math.abs(player().getX())<10000&&Math.abs(player().getZ())<10000,"Player entered virtual storage");var p=ship.logicalPose().position();minY=Math.min(minY,p.y());maxY=Math.max(maxY,p.y());var up=new Vector3d(0,1,0);ship.logicalPose().orientation().transform(up);maxTilt=Math.max(maxTilt,Math.acos(Math.clamp(up.y,-1,1)));
            if(THRUST&&(phase>=3&&phase<=8||phase==13)&&world.getGameTime()%10==0){var at=new Vector3d(p);var s=new JsonObject();s.addProperty("tick",world.getGameTime());s.addProperty("phase",phase);s.addProperty("x",p.x());s.addProperty("z",p.z());s.addProperty("y",p.y());s.addProperty("tilt",Math.acos(Math.clamp(up.y,-1,1)));s.addProperty("up_x",up.x);s.addProperty("up_z",up.z);s.addProperty("angular_velocity",String.valueOf(dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship).getAngularVelocity()));if(sampledAt!=null){double distance=Math.hypot(at.x-sampledAt.x,at.z-sampledAt.z);s.addProperty("kmh",distance*72/(world.getGameTime()-sampledTick));if(phase==3)drivenPath+=distance;}sampledAt=at;sampledTick=world.getGameTime();
                for(int x:new int[]{-3,3}){var prop=(SkatPropellerEntity)world.getBlockEntity(origin.offset(x,6,10));s.addProperty("rpm_"+x,prop.getSpeed());s.addProperty("thrust_"+x,prop.getThrust());s.addProperty("airflow_"+x,prop.getAirflow());s.addProperty("air_scaling_"+x,prop.getAirflowScaling());s.addProperty("scaled_thrust_"+x,prop.getScaledThrust());try{var wet=SkatPropellerEntity.class.getDeclaredField("wettedHull");wet.setAccessible(true);s.addProperty("trim_active_"+x,wet.getBoolean(prop));for(String n:new String[]{"throttleRamp","lastAppliedImpulse"}){var f=SkatPropellerEntity.class.getDeclaredField(n);f.setAccessible(true);s.addProperty(n+"_"+x,f.getDouble(prop));}}catch(NoSuchFieldException ignored){}catch(IllegalAccessException error){throw new IllegalStateException(error);}}var keel=global(.5,.05,.5);s.addProperty("keel_y",keel.y);s.addProperty("wave_surface_y",pro.erez.interstice.ocean.LowerToxinSurface.sample(world,keel.x,keel.z,0).height());var v=dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship).getLinearVelocity();s.addProperty("body_velocity_kmh",Math.hypot(v.x(),v.z())*3.6);propulsionSamples.add(s);}
        }
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post event){if(!ACTIVE)return;var mc=Minecraft.getInstance();try{
        if(closing){if(mc.level==null&&mc.screen instanceof TitleScreen&&++stopTicks>20)mc.stop();return;}
        if(phase==0&&mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen&&mc.player!=null){if(survivalExpected){finish(false,"Player died during Survival cold login");return;}REPORT.addProperty("entry_respawn_from_failed_fixture",true);mc.player.respawn();mc.setScreen(null);return;}
        if(phase>0&&mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen){if(phase==7)REPORT.add("systems",SkatSystemsNative.report());finish(false,"Player died during native phase "+phase);return;}
        if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            require(net.neoforged.fml.ModList.get().isLoaded("distanthorizons"),"Mandatory DH absent");REPORT.addProperty("dh_loaded",true);deadline=System.nanoTime()+900_000_000_000L;started=true;
            REPORT.addProperty("version",net.neoforged.fml.ModList.get().getModContainerById("interstice").orElseThrow().getModInfo().getVersion().toString());
            REPORT.addProperty("started_utc",java.time.Instant.now().toString());if(!MODE.equals("review"))report();
            if(!NEW){var proof=mc.gameDirectory.toPath().resolve("skat-create.json");require(Files.isRegularFile(proof),"No prior create evidence");var resumed=mc.gameDirectory.toPath().resolve("skat-resume.json");if(MODE.equals("reload")&&Files.isRegularFile(resumed))proof=resumed;if(MODE.equals("review"))proof=mc.gameDirectory.toPath().resolve("skat-reload.json");prior=JsonParser.parseString(Files.readString(proof)).getAsJsonObject();require(prior.get("process_exit_code").getAsInt()==0&&(prior.get("passed").getAsBoolean()||MODE.equals("resume")&&prior.get("cargo_containers").getAsInt()==SkatData.CARGO.size()&&!prior.get("owner_profile_used").getAsBoolean()),"No valid prior native process");survivalExpected=MODE.equals("reload")&&prior.has("systems")&&prior.getAsJsonObject("systems").has("saved_survival_inside_hold");mc.createWorldOpenFlows().openWorld("skat-owned",()->{});}
            else {require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/skat-owned")),"New disposable world required");var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);mc.createWorldOpenFlows().createFreshLevel("skat-owned",new net.minecraft.world.level.LevelSettings("Скат SK03 · проверка",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261028L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);}return;
        }
        if(MODE.equals("review")){
            if(started&&!reviewReady&&mc.level!=null&&mc.player!=null&&mc.getSingleplayerServer()!=null&&mc.screen==null){reviewReady=true;
                var marker=mc.gameDirectory.toPath().resolve("skat-review-opened");if(!Files.exists(marker)){submit(()->{
                    world=mc.getSingleplayerServer().getLevel(IslandWorld.TENSION_WORLD);ship=SubLevelContainer.getContainer(world).getAllSubLevels().stream().filter(body->body.getUniqueId().toString().equals(prior.get("ship_uuid").getAsString())).findFirst().orElseThrow();findOrigin();
                    var p=player();p.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();var spot=global(.5,4.15,-1.2);p.teleportTo(world,spot.x,spot.y,spot.z,Set.of(),180,0);p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);p.getInventory().add(new ItemStack(Items.COAL,64));p.getInventory().add(new ItemStack(SkatShips.BLUEPRINT.get()));
                });Files.writeString(marker,"Owner review; automation disabled.");}mc.mouseHandler.grabMouse();
            }return;
        }
        if(!started||mc.getOverlay()!=null||SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||mc.screen!=null&&!(phase==7&&SkatSystemsNative.menuExpected()))return;
        require(System.nanoTime()<deadline,"Skat deadline at phase "+phase);mc.mouseHandler.releaseMouse();KeyMapping.releaseAll();
        if(phase>=3)require(maxTilt<.35,"Hull became unstable during the full maneuver: "+maxTilt);
        if(work!=null&&!work.isDone())return;if(work!=null)work.join();
        if(MODE.equals("glass")&&phase==2){submit(()->SkatGlassNative.begin(world,ship,origin));phase=10;return;}
        if(phase==10){if(SkatGlassNative.tick())finish(true,"");return;}
        if(phase==7){if(++ticks%40==0){REPORT.add("systems",SkatSystemsNative.report());report();}if(SkatSystemsNative.tick()){submit(()->{inventoryCheck();structureCheck();SkatSystemsNative.survivalSave();REPORT.addProperty("lighting_saved_mask",helm().lighting());REPORT.add("systems",SkatSystemsNative.report());try{WaveShipFixture.pause(world);WaveShipFixture.flush(world);}catch(Exception e){throw new IllegalStateException(e);}settling=new NativeChunkSettler.Session("Skat systems final native quiet",1200,true);});phase=6;}return;}
        if(phase==0&&survivalExpected){if(++loginTicks<120)return;var p=player();require(p.gameMode.getGameModeForPlayer()==GameType.SURVIVAL&&p.getHealth()>19.9,"Unprotected Survival cold login: "+p.getHealth());REPORT.addProperty("survival_cold_login_health",p.getHealth());REPORT.addProperty("survival_cold_login_client_ticks",loginTicks);}
        if(phase==3||phase==4||phase==13){long elapsed=world.getGameTime()-since;boolean pulse=MODE.equals("reload")&&phase==3&&elapsed<80;boolean drive=!pulse||elapsed%20<10;boolean reverse=MODE.equals("reload")||phase==13;mc.options.keyUp.setDown(drive&&!reverse);mc.options.keyDown.setDown(drive&&reverse);mc.options.keyRight.setDown(phase==4);REPORT.addProperty("drive_direction",reverse?-1:1);if(pulse)REPORT.addProperty("pulsed_throttle",true);var stateField=SkatClient.class.getDeclaredField("state");stateField.setAccessible(true);REPORT.addProperty("client_control_state",String.valueOf(stateField.get(null)));REPORT.addProperty("client_passenger",mc.player.isPassenger());REPORT.addProperty("client_up",mc.options.keyUp.isDown());}
        if((MODE.equals("speed")||THRUST)&&phase==3){mc.options.hideGui=false;float value=SkatClient.displayedSpeed();if(Float.isFinite(value)){maxHudSpeed=Math.max(maxHudSpeed,value);if(world.getGameTime()-since>120&&value>.05&&!speedHudCaptured){shot("speed-hud");speedHudCaptured=true;}}}
        if(++ticks<40)return;ticks=0;report();
        if(phase==0){submit(()->{
            world=mc.getSingleplayerServer().getLevel(IslandWorld.TENSION_WORLD);require(world.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&g.terrainRevision()==6,"Actual V6 required");if(THRUST&&NEW)trialSpawn=findSeaRoute();else if(!NEW){var location=mc.gameDirectory.toPath().resolve("skat-saved-location.json");if(Files.isRegularFile(location)){var saved=readJson(location);require(saved.get("uuid").getAsString().equals(prior.get("ship_uuid").getAsString()),"Saved location UUID differs");trialSpawn=BlockPos.containing(saved.get("x").getAsDouble(),35,saved.get("z").getAsDouble());REPORT.add("saved_pose_loaded_from_sable_index",saved);}else if(prior.has("saved_world_x"))trialSpawn=BlockPos.containing(prior.get("saved_world_x").getAsDouble(),35,prior.get("saved_world_z").getAsDouble());else if(prior.has("propulsion_samples")){var samples=prior.getAsJsonArray("propulsion_samples");var last=samples.get(samples.size()-1).getAsJsonObject();trialSpawn=BlockPos.containing(last.get("x").getAsDouble(),35,last.get("z").getAsDouble());}REPORT.addProperty("cold_load_at_saved_ship",trialSpawn.toShortString());}var p=player();p.teleportTo(world,trialSpawn.getX(),43,trialSpawn.getZ()-34,Set.of(),0,12);p.setGameMode(GameType.CREATIVE);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,200000,0,false,false));p.getAbilities().flying=true;p.onUpdateAbilities();
        });phase=1;}
        else if(phase==1){if(chunks<289)return;var server=mc.getSingleplayerServer();work=server.submit(()->{
            if(NEW)return SkatAssembly.create(world,trialSpawn,o->{
                for(var c:SkatData.CARGO){var cargo=(SkatCargoEntity)world.getBlockEntity(o.offset(c.anchor()));cargo.setItem(0,new ItemStack(Items.DIAMOND,c.index()+1));cargo.setItem(53,new ItemStack(Items.IRON_INGOT,64));inventories.add(cargo.inventoryId());}
                if(FLEET){var b=(SkatCargoEntity)world.getBlockEntity(o.offset(SkatData.CARGO.getFirst().anchor()));b.setItem(1,new ItemStack(Items.COAL,64));REPORT.addProperty("bunker_initial_coal",64);}else for(int x:new int[]{-3,3})((SkatEngineEntity)world.getBlockEntity(o.offset(x,6,9))).addFuel(new ItemStack(Items.COAL,64),false);
            });
            else {
                ship=SubLevelContainer.getContainer(world).getAllSubLevels().stream().filter(s->s.getUniqueId().toString().equals(prior.get("ship_uuid").getAsString())&&!s.isRemoved()).findFirst().orElse(null);
                if(ship==null){if(bodyLoadStarted==Long.MIN_VALUE)bodyLoadStarted=world.getGameTime();REPORT.addProperty("waiting_for_saved_body_ticks",world.getGameTime()-bodyLoadStarted);require(world.getGameTime()-bodyLoadStarted<600,"Saved body did not load within 600 server ticks");return CompletableFuture.<ServerSubLevel>completedFuture(null);}
                require(ship.getUniqueId().toString().equals(prior.get("ship_uuid").getAsString()),"Ship UUID changed on cold reload");for(var id:prior.getAsJsonArray("inventory_ids"))inventories.add(UUID.fromString(id.getAsString()));
                return CompletableFuture.completedFuture(ship);
            }
        }).thenCompose(future->future).thenAcceptAsync(created->{if(created==null){phase=1;return;}ship=created;
            findOrigin();restorePending=true;since=world.getGameTime();
        },server);phase=2;}
        else if(phase==2){if(restorePending){submit(()->{if(!plotReady()){REPORT.addProperty("waiting_for_complete_plot",world.getGameTime()-since);require(world.getGameTime()-since<240,"Saved plot did not load complete hull/cargo within 240 ticks");return;}initializeHull();restorePending=false;REPORT.addProperty("complete_plot_before_setup",true);});return;}if(world.getGameTime()-since<600)return;submit(()->{
            var p=ship.logicalPose().position();REPORT.addProperty("settled_position",p.toString());REPORT.addProperty("min_y",minY);REPORT.addProperty("max_y",maxY);REPORT.addProperty("max_tilt",maxTilt);
            require(p.y()>29&&p.y()<40,"Ship did not float in actual HEAVY: "+p);require(maxTilt<.3,"Unstable hull");inventoryCheck();
            var floor=global(.5,1.64,2.8);double freeboard=floor.y-pro.erez.interstice.ocean.LowerToxinSurface.sample(world,floor.x,floor.z,0).height();REPORT.addProperty("hold_floor_freeboard",freeboard);require(freeboard>.15,"Cargo floor below safe waterline: "+freeboard);
            var pilot=player();var seat=global(-1.5,4.6,-4.5);pilot.teleportTo(world,seat.x,seat.y,seat.z,Set.of(),180,0);pilot.getAbilities().flying=false;pilot.onUpdateAbilities();require(SkatSeatEntity.mount(world,origin.offset(-2,4,-5),pilot),"Skat seat adapter did not mount");helm().interact(pilot);require(pilot.isPassenger(),"Native Create seat did not mount pilot");
            try{var last=SkatHelmEntity.class.getDeclaredField("inputAt");last.setAccessible(true);last.setLong(helm(),world.getGameTime()-20);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}helm().process();require(pilot.getUUID().equals(helm().pilot()),"Lost packets revoked a still-seated pilot");for(int x:new int[]{-3,3})require(((SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9))).getGeneratedSpeed()==0,"Stale input retained propulsion");REPORT.addProperty("input_timeout_stops_but_retains_seated_pilot",true);
            before=new Vector3d(ship.logicalPose().position());heading=new Quaterniond(ship.logicalPose().orientation());since=world.getGameTime();
        });shot("hull");phase=3;}
        else if(phase==3){if(world.getGameTime()-since<(THRUST?600:200))return;submit(()->{
            REPORT.addProperty("server_pilot",String.valueOf(helm().pilot()));REPORT.addProperty("server_passenger",player().isPassenger());for(int x:new int[]{-3,3})REPORT.addProperty("forward_rpm_"+x,((SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9))).getSpeed());
            for(int x:new int[]{-3,3}){var prop=(SkatPropellerEntity)world.getBlockEntity(origin.offset(x,6,10));REPORT.addProperty("prop_rpm_"+x,prop.getSpeed());REPORT.addProperty("prop_thrust_"+x,prop.getThrust());}
            var terrain=new JsonArray();var b=ship.boundingBox();for(int x=(int)Math.floor(b.minX());x<=Math.ceil(b.maxX());x++)for(int y=(int)Math.floor(b.minY());y<=Math.ceil(b.maxY());y++)for(int z=(int)Math.floor(b.minZ());z<=Math.ceil(b.maxZ());z++){var at=new BlockPos(x,y,z);var state=world.getBlockState(at);if(!state.getCollisionShape(world,at).isEmpty()&&terrain.size()<20)terrain.add(at+" "+state.getBlock());}REPORT.add("terrain_in_world_bounds",terrain);
            double distance=ship.logicalPose().position().distance(before);REPORT.addProperty("native_thrust_distance",distance);require(distance>1.5,"Native propulsion produced no motion: "+distance);
            if(MODE.equals("speed")||THRUST){var at=ship.logicalPose().position();double horizontal=Math.hypot(at.x-before.x,at.z-before.z);REPORT.addProperty("measured_horizontal_distance",horizontal);REPORT.addProperty("measured_average_mps",horizontal*20.0/(world.getGameTime()-since));REPORT.addProperty("server_speed_mps",helm().speedMetersPerSecond());REPORT.addProperty("hud_max_speed_mps",maxHudSpeed);REPORT.addProperty("hud_captured",speedHudCaptured);require(speedHudCaptured&&maxHudSpeed>.05&&maxHudSpeed<=64,"Real speed channel/HUD never received sailing motion");}
            for(int x:new int[]{-3,3}){var engine=(SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9));var prop=(SkatPropellerEntity)world.getBlockEntity(origin.offset(x,6,10));require(engine.getSpeed()!=0&&Math.abs(prop.getThrust())>0,"No real kinetic/Aeronautics thrust");if(!FLEET)require(engine.fuelTicks()<REPORT.get("initial_fuel_"+x).getAsInt()+(REPORT.has("test_refuel_"+x)?1600:0),"Fuel not consumed");}
            if(FLEET){long spent=REPORT.get("initial_total_fuel_budget").getAsLong()-fuelBudget();require(spent>0,"Total engine and bunker fuel did not decrease");REPORT.addProperty("fuel_ticks_spent",spent);}
            before=new Vector3d(ship.logicalPose().position());heading=new Quaterniond(ship.logicalPose().orientation());since=world.getGameTime();
        });shot("underway");phase=8;}
        else if(phase==8){if(world.getGameTime()-since<120)return;submit(()->{heading=new Quaterniond(ship.logicalPose().orientation());since=world.getGameTime();REPORT.addProperty("neutral_coast_before_turn_ticks",120);REPORT.addProperty("turn_entry_angular_velocity",String.valueOf(dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship).getAngularVelocity()));});phase=4;}
        else if(phase==4){long turningTicks=world.getGameTime()-since;if(turningTicks<240)return;var currentTurn=new Quaterniond(heading).invert().mul(ship.logicalPose().orientation()).getEulerAnglesYXZ(new Vector3d());REPORT.addProperty("turn_observed_ticks",turningTicks);REPORT.addProperty("turn_observed_radians",Math.abs(currentTurn.y));if(Math.abs(currentTurn.y)<.03&&turningTicks<600)return;submit(()->{
            for(int x:new int[]{-3,3}){var engine=(SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9));var prop=(SkatPropellerEntity)world.getBlockEntity(origin.offset(x,6,10));REPORT.addProperty("turn_rpm_"+x,engine.getSpeed());REPORT.addProperty("turn_thrust_"+x,prop.getThrust());}
            var delta=new Quaterniond(heading).invert().mul(ship.logicalPose().orientation());double yaw=Math.abs(delta.getEulerAnglesYXZ(new Vector3d()).y);REPORT.addProperty("turn_radians",yaw);require(yaw>.03,"Differential thrust did not turn ship");if(!THRUST)player().stopRiding();before=new Vector3d(ship.logicalPose().position());since=world.getGameTime();
        });phase=THRUST?13:5;}
        else if(phase==13){if(world.getGameTime()-since<240)return;submit(()->{
            var velocity=dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship).getLinearVelocity();
            var bow=ship.logicalPose().transformNormal(new Vector3d(0,0,-1));
            double signed=velocity.dot(bow),horizontal=Math.hypot(velocity.x(),velocity.z());
            REPORT.addProperty("reverse_final_speed_kmh",horizontal*3.6);
            REPORT.addProperty("reverse_signed_speed_mps",signed);
            REPORT.addProperty("reverse_distance_m",ship.logicalPose().position().distance(before));
            require(signed<-.5,"Reverse input did not produce actual stern-first motion");
            require(horizontal<6.3,"Reverse exceeded its separate 20 km/h limit");
            for(int x:new int[]{-3,3})require(((SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9))).getGeneratedSpeed()<0,"Reverse drivetrain never engaged");
            REPORT.addProperty("reverse_native_control",true);player().stopRiding();since=world.getGameTime();
        });phase=5;}
        else if(phase==5){if(world.getGameTime()-since<40)return;submit(()->{
            if(FLEET){var b=(SkatCargoEntity)world.getBlockEntity(origin.offset(SkatData.CARGO.getFirst().anchor()));REPORT.addProperty("bunker_remaining_coal",b.getItem(1).getCount());require(b.getItem(1).getCount()<=62,"Both engines did not draw coal from the bunker");REPORT.addProperty("native_bunker_supplies_both",true);}
            require(helm().pilot()==null,"Disconnected pilot lease retained");for(int x:new int[]{-3,3})require(((SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9))).getGeneratedSpeed()==0,"Fail-safe did not stop engine");inventoryCheck();structureCheck();
            REPORT.addProperty("pilot_failsafe",true);REPORT.addProperty("passed_runtime",true);REPORT.addProperty("dedicated_two_players",false);
            if(THRUST&&!FLEET){REPORT.addProperty("dedicated_two_players",false);REPORT.addProperty("propulsion_trial_complete",true);try{WaveShipFixture.pause(world);WaveShipFixture.flush(world);}catch(Exception error){throw new IllegalStateException(error);}settling=new NativeChunkSettler.Session("Skat propulsion final save",1200,true);}
            else if(MODE.equals("speed"))SkatGlassNative.begin(world,ship,origin);else SkatSystemsNative.begin(world,ship,origin);
        });phase=THRUST&&!FLEET?12:MODE.equals("speed")?10:7;}
        else if(phase==12){require(!settling.failed(),settling.failure());if(!settling.ready())return;REPORT.add("final_chunk_settlement",settling.report());finish(true,"");}
        else if(phase==6){require(settling==null||!settling.failed(),settling==null?"":settling.failure());if(settling==null||!settling.ready())return;REPORT.add("final_chunk_settlement",settling.report());submit(()->{coldPowered=true;for(int x:new int[]{-3,3}){((SkatEngineEntity)world.getBlockEntity(origin.offset(x,6,9))).throttle(96);require(Math.abs(((SkatPropellerEntity)world.getBlockEntity(origin.offset(x,6,10))).getSpeed())>1,"Cannot prepare real powered drivetrain snapshot");}REPORT.addProperty("cold_powered_drivetrain",true);try{WaveShipFixture.flush(world);}catch(Exception e){throw new IllegalStateException(e);}});phase=9;}
    else if(phase==9){var savedAt=ship.logicalPose().position();REPORT.addProperty("saved_world_x",savedAt.x());REPORT.addProperty("saved_world_z",savedAt.z());shot("complete");finish(true,"");}
    }catch(Throwable e){e.printStackTrace();if(MODE.equals("review"))return;if(phase==7)REPORT.add("systems",SkatSystemsNative.report());finish(false,e.toString());}}
}
