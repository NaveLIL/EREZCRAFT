package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.tether.*;

/** Two JVMs, ordinary block use and real client movement packets. Preparations are explicitly recorded. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class WinchVisualSmoke {
    private static final String MODE=System.getProperty("interstice.winchSmoke","");
    private static final String WORLD="native-winch-check";
    private static boolean started,finished,quitting,passed;
    private static int stage,ticks;
    private static long deadline;
    private static String reason;
    private static BlockPos base,anchor;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static JsonObject data=new JsonObject();
    private static double initialDistance,maxForce,maxPositionStep;
    private static Vec3 lastPosition;
    private static long motionTicks,ownedLineTicks,tautLineTicks;
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){
        if(MODE.isEmpty())return;var session=settling;if(session!=null)session.tick(event.getServer());
        if(stage==4||stage==5){var p=event.getServer().getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());if(p!=null){
            var node=WinchLinks.loaded(p.serverLevel(),anchor);if(node!=null&&WinchLinks.matches(p,node)&&node.hook(p.getUUID())!=null){ownedLineTicks++;var force=WinchLinks.acceleration(WinchLinks.source(node),WinchLinks.endpoint(p),p.getKnownMovement(),8);maxForce=Math.max(maxForce,force.length());if(force.length()>0)tautLineTicks++;}
            if(lastPosition!=null)maxPositionStep=Math.max(maxPositionStep,p.position().distanceTo(lastPosition));lastPosition=p.position();motionTicks++;
        }}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(quitting){shutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen){started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(8);mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);
                if(MODE.equals("create")){var settings=new LevelSettings("Disposable elastic winch",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel(WORLD,settings,new WorldOptions(20261009L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);}
                else{data=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("winch-create-validation.json"))).getAsJsonObject();require(data.get("passed").getAsBoolean()&&data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"No successful different-JVM saved winch");base=new BlockPos(data.get("base_x").getAsInt(),data.get("base_y").getAsInt(),data.get("base_z").getAsInt());anchor=base.offset(0,38,-2);mc.createWorldOpenFlows().openWorld(WORLD,()->{});}return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;require(System.nanoTime()<deadline,"Winch stage timeout "+stage);if(mc.player==null||mc.level==null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==0){work=server.submit(()->{
                var p=server.getPlayerList().getPlayer(uuid);
                if(MODE.equals("create"))prepare(p);else{var node=WinchLinks.loaded(p.serverLevel(),anchor);require(node!=null&&node.anchorId().toString().equals(data.get("saved_anchor_id").getAsString())&&WinchLinks.matches(p,node)&&node.hooks().size()==1&&WinchLinks.link(p).length()==32,"Cold player/block metadata disagrees or duplicated its hook");data.addProperty("reload_pid",ProcessHandle.current().pid());data.addProperty("cold_owned_uuid_length_and_hook_preserved",true);WinchLinks.sync(p,node,true);}
                return true;
            });settling=new NativeChunkSettler.Session("winch_before_actions",2000);stage=1;ticks=0;return;}
            if(stage==1&&done()&&settling.ready()&&++ticks>=20){data.add("settle_before_actions",settling.report());settling=null;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if(MODE.equals("create")){use(mc);stage=2;}else{shot(mc,"winch-cold-restored.png");work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var node=WinchLinks.loaded(p.serverLevel(),anchor);p.serverLevel().setBlock(BlockPos.containing(WinchLinks.source(node).lerp(WinchLinks.endpoint(p),.5)),Blocks.STONE.defaultBlockState(),3);return true;});stage=20;}ticks=0;
            }else if(stage==2&&++ticks>=12){work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);data.add("ordinary_use_server_state",state(p));require(WinchLinks.link(p)!=null&&WinchLinks.link(p).length()==32&&p.getInventory().items.get(0).getDamageValue()==1,"Ordinary client spool use did not attach one paid32-block line: "+state(p));data.addProperty("ordinary_client_self_attachment",true);
                // Explicit short-line fixture: tests physical tension earlier than a24/32-block fall.
                WinchLinks.detach(p);require(WinchLinks.attach(p,p.serverLevel(),anchor,8),"Prepared8-block physics line could not reattach");data.addProperty("prepared_short_physics_line",8);initialDistance=WinchLinks.source(WinchLinks.loaded(p.serverLevel(),anchor)).distanceTo(WinchLinks.endpoint(p));return true;});stage=3;ticks=0;
            }else if(stage==3&&done()&&++ticks>=5){shot(mc,"winch-connected.png");mc.player.setYRot(180);mc.player.setXRot(0);lastPosition=null;motionTicks=ownedLineTicks=tautLineTicks=0;mc.options.keyUp.setDown(true);mc.options.keySprint.setDown(true);mc.options.keyJump.setDown(true);stage=4;ticks=0;
            }else if(stage==4){if(++ticks==2)mc.options.keyJump.setDown(false);if(ticks>=22){mc.options.keyUp.setDown(false);mc.options.keySprint.setDown(false);stage=5;ticks=0;}}
            else if(stage==5){if(++ticks==35)shot(mc,"winch-real-tension.png");if(ticks>=140){work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var node=WinchLinks.loaded(p.serverLevel(),anchor);
                        require(p.isAlive()&&p.connection!=null&&p.connection.getConnection().isConnected(),"Real motion disconnected/killed the fixture player");require(!p.getAbilities().mayfly&&!p.getAbilities().flying&&!p.noPhysics,"Winch enabled a flight/noPhysics workaround");require(maxForce>0&&maxForce<=.12000001&&maxPositionStep<5&&motionTicks>=100&&ownedLineTicks>0&&tautLineTicks>0,"Real-client physical episode lacked bounded owned tension or teleported: "+state(p)+"; ownedTicks="+ownedLineTicks+"; tautTicks="+tautLineTicks+"; force="+maxForce);
                        data.addProperty("actual_server_motion_ticks",motionTicks);data.addProperty("owned_line_motion_ticks",ownedLineTicks);data.addProperty("owned_taut_line_ticks",tautLineTicks);data.addProperty("max_observed_acceleration",maxForce);data.addProperty("max_observed_position_step",maxPositionStep);data.addProperty("ordinary_motion_packets_no_flight_flag_or_kick",true);
                        data.addProperty("actual_detachment_observed_without_kick",WinchLinks.link(p)==null);return true;});stage=6;ticks=0;}}
            else if(stage==6&&done()){work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);WinchLinks.detach(p);p.teleportTo(p.serverLevel(),base.getX()-1.5,base.getY()+35,base.getZ()-3.5,Set.of(),180,0);p.hasChangedDimension();p.setHealth(20);p.getInventory().selected=0;data.addProperty("prepared_grounded_cold_save_after_physics",true);return true;});stage=7;ticks=0;}
            else if(stage==7&&done()&&++ticks>=20){use(mc);stage=8;ticks=0;}
            else if(stage==8&&++ticks>=12){work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var node=WinchLinks.loaded(p.serverLevel(),anchor);require(node!=null&&node.hooks().size()==1&&WinchLinks.matches(p,node)&&WinchLinks.link(p).length()==32,"Grounded cold save did not restore one valid32-block attachment");data.addProperty("saved_anchor_id",node.anchorId().toString());return true;});stage=9;ticks=0;}
            else if(stage==9&&done())finish(mc,true,"Ordinary paid spool use and bounded owned traction passed; the 140-client-tick motion episode also includes travel after detachment, not continuous suspension. Grounded attachment saved for cold JVM restart");
            else if(stage==20&&done()&&++ticks>=10){work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var node=WinchLinks.loaded(p.serverLevel(),anchor);require(WinchLinks.link(p)==null&&node.hooks().isEmpty(),"Loaded rope ignored a real block obstruction");data.addProperty("cold_obstruction_detaches_without_extra_hook",true);return true;});stage=21;ticks=0;}
            else if(stage==21&&done()){shot(mc,"winch-obstacle-release.png");finish(mc,true,"Different-JVM saved anchor/player identity revalidated; real obstruction released its one hook");}
        }catch(Throwable failure){failure.printStackTrace();finish(mc,false,failure.toString());}
    }
    private static void prepare(ServerPlayer p){
        base=p.blockPosition().below();anchor=base.offset(0,38,-2);var level=p.serverLevel();
        for(int x=-12;x<=12;x++)for(int z=-14;z<=5;z++){level.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);for(int y=1;y<=42;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        // Approach the NORTH rope outlet. A raised outlet keeps the ordinary sprint-jump line clear of the ledge.
        for(int x=-2;x<=2;x++)for(int z=-5;z<=-1;z++)level.setBlock(base.offset(x,34,z),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(anchor,RiftTethers.WINCH.get().defaultBlockState(),3);p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().items.set(0,new ItemStack(RiftTethers.TETHER_SPOOL.get()));p.getInventory().selected=0;p.getInventory().setChanged();p.containerMenu.broadcastChanges();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,12000,3,false,false));p.teleportTo(level,base.getX()-1.5,base.getY()+35,base.getZ()-3.5,Set.of(),180,0);p.hasChangedDimension();require(WinchLinks.clear(level,WinchLinks.source(WinchLinks.loaded(level,anchor)),WinchLinks.endpoint(p),p),"Prepared player did not face a clear rope outlet");
        data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("base_x",base.getX());data.addProperty("base_y",base.getY());data.addProperty("base_z",base.getZ());data.addProperty("scope","Prepared disposableSurvival platform/inventory/short8-block testline andResistanceIV protects fixture landing only; actualordinary use/movementpackets, noflight/noPhysics/teleportduringphysics episode; not a resource-gatheringSurvival expedition");
    }
    private static void use(Minecraft mc){mc.player.getInventory().selected=0;var d=Vec3.atCenterOf(anchor).subtract(mc.player.getEyePosition());mc.player.setYRot((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(anchor),Direction.NORTH,anchor,false));data.addProperty("ordinary_use_client_result_stage_"+stage,result.toString());}
    private static JsonObject state(ServerPlayer p){var out=new JsonObject();var node=WinchLinks.loaded(p.serverLevel(),anchor);var link=WinchLinks.link(p);out.addProperty("player",p.position().toString());out.addProperty("anchor",anchor.toShortString());out.addProperty("item",p.getMainHandItem().toString());out.addProperty("item_damage",p.getMainHandItem().getDamageValue());out.addProperty("length",link==null?0:link.length());out.addProperty("hooks",node==null?-1:node.hooks().size());out.addProperty("reach",p.canInteractWithBlock(anchor,0));out.addProperty("los",node!=null&&WinchLinks.clear(p.serverLevel(),WinchLinks.source(node),WinchLinks.endpoint(p),p));out.addProperty("known_movement",p.getKnownMovement().toString());return out;}
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->System.out.println("WINCH_SCREENSHOT "+name));}
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
    private static void finish(Minecraft mc,boolean success,String text){if(quitting)return;passed=success;reason=text;quitting=true;mc.options.keyUp.setDown(false);mc.options.keyUse.setDown(false);mc.options.keyJump.setDown(false);mc.options.keySprint.setDown(false);settling=new NativeChunkSettler.Session("winch_terminal",160);}
    private static void shutdown(Minecraft mc)throws Exception{if(mc.getSingleplayerServer()==null){write(mc,false,"No server at terminal");mc.stop();finished=true;return;}if(settling.failed()){passed=false;reason=settling.failure();}if(!settling.ready())return;data.add("settle_terminal",settling.report());write(mc,passed,reason);mc.stop();finished=true;}
    private static void write(Minecraft mc,boolean success,String text)throws Exception{data.addProperty("passed",success);data.addProperty("stage",stage);data.addProperty("reason",text);Files.writeString(mc.gameDirectory.toPath().resolve("winch-"+MODE+"-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));System.out.println("WINCH_VALIDATION "+data);}
}
