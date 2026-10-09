package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.lang.reflect.Field;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.lift.*;
import pro.erez.interstice.fauna.CanopySentinel;
import pro.erez.interstice.gear.RouteBeaconEntity;
import net.minecraft.world.item.Items;
import pro.erez.interstice.smoke.net.V6NetworkFiles;

/** A real independent client connects through vanilla TCP; never creates an integrated server. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class V6NetworkPeerSmoke {
    private static final int PEER=Integer.getInteger("interstice.v6NetworkPeer",0);
    private static final int CONNECT_PORT=Integer.getInteger("interstice.v6NetworkPort",V6NetworkFiles.PORT);
    private static boolean started,finished,connecting,hello,closing,guard,finalReceiptReady;
    private static long deadline;
    private static int ticks,token=-1,phaseTicks,reconnects,logins,reconnectAt,lastServerTick;
    private static String action="",session="";
    private static boolean sent,shift,up;
    private static final JsonObject report=new JsonObject();
    private static final JsonArray phases=new JsonArray();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(PEER<1||PEER>2||finished||guard)return;var mc=Minecraft.getInstance();ticks++;
        try{
            if(!started){if(!(mc.screen instanceof TitleScreen))return;started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(50);V6NetworkFiles.validate();require(CONNECT_PORT==26593||CONNECT_PORT==26594||CONNECT_PORT==26595,"Peer connect port must be26593/26594/26595");
                require(mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').contains("/.verification/"),"Peer profile is not isolated");require(mc.getUser().getName().equals(V6NetworkFiles.name(PEER)),"Root must pass the exact offline peer username");
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);mc.options.hideGui=false;
                report.addProperty("peer",PEER);report.addProperty("connect_host","127.0.0.1");report.addProperty("connect_port",CONNECT_PORT);report.addProperty("latency_injected_by_peer",false);report.addProperty("pid",ProcessHandle.current().pid());report.addProperty("username",mc.getUser().getName());report.addProperty("mock_connection",false);report.addProperty("integrated_server",false);report.add("phase_actions",phases);write(mc,false,"Waiting for dedicated smoke server");
            }
            if(closing){stop(mc);return;}require(System.nanoTime()<deadline,"Peer network deadline");
            if(!Files.isRegularFile(V6NetworkFiles.EVIDENCE.resolve("state.json")))return;var state=V6NetworkFiles.read("state.json");
            lastServerTick=state.get("server_tick").getAsInt();String announced=state.get("session_id").getAsString();
            if(session.isEmpty())session=announced;require(session.equals(announced),"Evidence directory changed to another server session");
            if(Files.isRegularFile(V6NetworkFiles.EVIDENCE.resolve("runner-request.json"))){var request=V6NetworkFiles.read("runner-request.json");if(request.get("session_id").getAsString().equals(session)){closing=true;write(mc,false,"Own purpose-built runner requested clean closure");return;}}
            if(state.has("finished")&&state.get("finished").getAsBoolean()){token=state.get("token").getAsInt();finalReceiptReady=true;closing=true;report.addProperty("minimum_network_passed",state.get("minimum_network_passed").getAsBoolean());report.addProperty("phase_pass_scope","All server behavioral phase checks at completion; final server quiescence/PID closure are independently required by launcher");write(mc,state.has("phase_checks_passed")&&state.get("phase_checks_passed").getAsBoolean(),"Dedicated scenario ended; whole behavioral pass and minimum scope are separate");return;}
            if(!state.has("server_ready")||!state.get("server_ready").getAsBoolean())return;
            if(mc.screen instanceof DisconnectedScreen){if(action.equals("reconnect")&&!hello&&ticks<=reconnectAt+80){mc.setScreen(new TitleScreen());return;}String reason=disconnectReason(mc.screen);report.addProperty("unexpected_disconnect",reason);throw new IllegalStateException("Actual TCP disconnect: "+reason);}
            if(mc.player==null||mc.level==null||mc.getConnection()==null){
                if(!connecting&&ticks>=reconnectAt&&mc.screen instanceof TitleScreen){connect(mc);connecting=true;}return;
            }
            require(mc.getSingleplayerServer()==null,"Peer unexpectedly owns an integrated server");require(mc.getConnection().getConnection().isConnected(),"TCP connection closed");
            if(!hello){hello=true;connecting=false;logins++;report.addProperty("actual_player_uuid",mc.player.getUUID().toString());mc.getConnection().sendCommand("v6net hello "+ProcessHandle.current().pid());}
            var peer=state.getAsJsonObject("peers").getAsJsonObject(Integer.toString(PEER));if(peer==null)return;int next=state.get("token").getAsInt();
            if(next!=token){release(mc);token=next;phaseTicks=0;sent=false;action=peer.get("action").getAsString();var p=new JsonObject();p.addProperty("token",token);p.addProperty("action",action);p.addProperty("client_tick",ticks);phases.add(p);System.out.println("V6_NET_PEER_PHASE peer="+PEER+" token="+token+" action="+action);}
            phaseTicks++;if(peer.has("dimension")&&!mc.level.dimension().location().toString().equals(peer.get("dimension").getAsString()))return;if(ticks%20==0)write(mc,false,"Running "+action);if(peer.has("delay_ticks")&&phaseTicks<peer.get("delay_ticks").getAsInt())return;
            if(action.equals("wait")){if(!sent&&phaseTicks>=10)ack(mc);return;}
            if(action.equals("reconnect")){if(!sent&&phaseTicks>=10){sent=true;report.addProperty("expected_reconnects",++reconnects);guard=true;release(mc);try{if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());}finally{guard=false;}hello=false;connecting=false;reconnectAt=ticks+40;}return;}
            if(action.equals("respawn")){if(mc.screen instanceof DeathScreen){var button=mc.screen.children().stream().filter(w->w instanceof Button b&&b.active&&b.getMessage().getString().equals(Component.translatable("deathScreen.respawn").getString())).map(w->(Button)w).findFirst();if(button.isPresent()&&!sent){button.get().onPress();sent=true;report.addProperty("native_respawn_button",true);}}else if(mc.player.isAlive()&&phaseTicks>30&&!sent)ack(mc);return;}
            if(action.equals("portal_walk")){mc.player.setYRot(0);mc.player.setXRot(0);double target=peer.get("stop_z").getAsDouble();double error=target-mc.player.getZ();up(mc,error>.15);mc.options.keyDown.setDown(error<-.15);if(peer.has("expected_dimension")&&mc.level.dimension().location().toString().equals(peer.get("expected_dimension").getAsString())){up(mc,false);mc.options.keyDown.setDown(false);if(!sent)ack(mc);}return;}
            if(action.equals("winch_motion")){mc.player.setYRot(90);mc.player.setXRot(0);if(phaseTicks<=22){up(mc,true);mc.options.keySprint.setDown(true);mc.options.keyJump.setDown(phaseTicks<=2);}else{up(mc,false);mc.options.keySprint.setDown(false);mc.options.keyJump.setDown(false);sneak(mc,true);}if(phaseTicks>=300&&!sent)ack(mc);return;}
            if(action.equals("crouch")){sneak(mc,true);if(phaseTicks>=30&&!sent)ack(mc);return;}
            if(action.equals("mine")){var target=V6NetworkFiles.point(peer.getAsJsonObject("target"));if(!mc.level.hasChunkAt(target))return;select(mc,peer.has("slot")?peer.get("slot").getAsInt():0);look(mc,target);if(!mc.level.getBlockState(target).isAir()){if(phaseTicks==1)mc.gameMode.startDestroyBlock(target,Direction.DOWN);mc.gameMode.continueDestroyBlock(target,Direction.DOWN);}else if(!sent)ack(mc);return;}
            if(action.equals("use_block")){if(phaseTicks<20||phaseTicks%20!=0)return;if(!sent){var preparedTarget=V6NetworkFiles.point(peer.getAsJsonObject("target"));if(!mc.level.hasChunkAt(preparedTarget)||(peer.has("expected_block")&&!BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(preparedTarget).getBlock()).toString().equals(peer.get("expected_block").getAsString())))return;select(mc,peer.has("slot")?peer.get("slot").getAsInt():0);sneak(mc,peer.has("sneak")&&peer.get("sneak").getAsBoolean());var target=V6NetworkFiles.point(peer.getAsJsonObject("target"));look(mc,target);Direction face=Direction.byName(peer.has("face")?peer.get("face").getAsString():"up");var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(target),face==null?Direction.UP:face,target,false));phases.get(phases.size()-1).getAsJsonObject().addProperty("native_use_result",result.toString());if(result.consumesAction())ack(mc);}return;}
            if(action.equals("lift_menu")||action.equals("cold_lift_menu")){if(phaseTicks<20)return;var lift=lift(mc,peer);if(lift==null)return;if(peer.has("lift_uuid"))require(lift.getUUID().toString().equals(peer.get("lift_uuid").getAsString()),"Native menu tracked another lift UUID");if(!(mc.player.containerMenu instanceof FieldLiftMenu)){if(phaseTicks%20==0){sneak(mc,true);mc.gameMode.interact(mc.player,lift,InteractionHand.MAIN_HAND);}return;}if(action.equals("cold_lift_menu")){int actual=0;for(int slot=0;slot<27;slot++){var stack=mc.player.containerMenu.getSlot(slot).getItem();if(stack.is(Items.STONE))actual+=stack.getCount();}if(actual==26&&phaseTicks>=30&&!sent){report.addProperty("cold_native_menu_stone_count",actual);report.addProperty("cold_native_menu_lift_uuid",lift.getUUID().toString());ack(mc);}return;}if(!sent){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,58,0,ClickType.QUICK_MOVE,mc.player);report.addProperty("native_lift_27_slot_menu",mc.player.containerMenu.slots.size()==63);ack(mc);}return;}
            if(action.equals("cold_observe")){if(phaseTicks<20)return;var beacon=mc.level.getBlockEntity(V6NetworkFiles.point(peer.getAsJsonObject("beacon_pos")));var entity=mc.level.getEntity(peer.get("guardian_id").getAsInt());if(!(beacon instanceof RouteBeaconEntity marker)||!(entity instanceof CanopySentinel sentinel))return;require(marker.markerId().toString().equals(peer.get("beacon_uuid").getAsString()),"Client observed another persisted beacon UUID");require(sentinel.getUUID().toString().equals(peer.get("guardian_uuid").getAsString()),"Client observed another persisted guardian UUID");report.addProperty("cold_actual_beacon_uuid",marker.markerId().toString());report.addProperty("cold_actual_guardian_uuid",sentinel.getUUID().toString());report.addProperty("cold_actual_guardian_synced_phase",sentinel.phase().name());if(!sent)ack(mc);return;}
            if(action.equals("lift_board")){var lift=lift(mc,peer);if(lift==null)return;sneak(mc,false);if(!mc.player.isPassenger()&&phaseTicks%20==0)mc.gameMode.interact(mc.player,lift,InteractionHand.MAIN_HAND);if(mc.player.isPassenger()&&!sent)ack(mc);return;}
            if(action.equals("lift_control")){if(phaseTicks<20)return;mc.player.closeContainer();sneak(mc,false);select(mc,0);if(!sent){var target=V6NetworkFiles.point(peer.getAsJsonObject("target"));look(mc,target);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false));ack(mc);}return;}
            if(!sent&&phaseTicks>=20)ack(mc);
        }catch(Throwable failure){failure.printStackTrace();report.addProperty("error",failure.toString());closing=true;try{write(mc,false,failure.toString());}catch(Exception ignored){}}
    }
    private static void connect(Minecraft mc){ConnectScreen.startConnecting(new TitleScreen(),mc,new ServerAddress("127.0.0.1",CONNECT_PORT),new ServerData("V6 isolated dedicated", "127.0.0.1:"+CONNECT_PORT,ServerData.Type.OTHER),false,null);}
    private static void select(Minecraft mc,int slot){mc.player.getInventory().selected=slot;mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));}
    private static void look(Minecraft mc,BlockPos p){var d=Vec3.atCenterOf(p).subtract(mc.player.getEyePosition());mc.player.setYRot((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
    private static void ack(Minecraft mc){sent=true;mc.getConnection().sendCommand("v6net ack "+token);}
    private static void up(Minecraft mc,boolean value){up=value;mc.options.keyUp.setDown(value);}
    private static void sneak(Minecraft mc,boolean value){if(shift==value)return;shift=value;mc.options.keyShift.setDown(value);if(mc.player!=null){mc.player.input.shiftKeyDown=value;mc.player.setShiftKeyDown(value);if(mc.getConnection()!=null)mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,value?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}}
    private static void release(Minecraft mc){up(mc,false);mc.options.keyDown.setDown(false);sneak(mc,false);mc.options.keyJump.setDown(false);mc.options.keySprint.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);if(mc.gameMode!=null)mc.gameMode.stopDestroyBlock();}
    private static FieldLiftEntity lift(Minecraft mc,JsonObject peer){if(!peer.has("lift_id"))return null;return mc.level.getEntity(peer.get("lift_id").getAsInt()) instanceof FieldLiftEntity lift?lift:null;}
    private static String disconnectReason(Object screen){try{Field field=DisconnectedScreen.class.getDeclaredField("details");field.setAccessible(true);return ((DisconnectionDetails)field.get(screen)).reason().getString();}catch(Exception absent){return screen.toString();}}
    private static void write(Minecraft mc,boolean success,String reason)throws Exception{report.addProperty("passed",success);report.addProperty("reason",reason);report.addProperty("token",token);report.addProperty("observed_server_token",token);report.addProperty("observed_server_tick",lastServerTick);report.addProperty("logins",logins);report.addProperty("client_tick",ticks);report.addProperty("session_id",session);report.addProperty("actual_connection",mc.getConnection()!=null&&mc.getConnection().getConnection().isConnected());if(mc.player!=null&&mc.level!=null){var p=new JsonObject();p.addProperty("x",mc.player.getX());p.addProperty("y",mc.player.getY());p.addProperty("z",mc.player.getZ());p.addProperty("dimension",mc.level.dimension().location().toString());p.addProperty("mayfly",mc.player.getAbilities().mayfly);p.addProperty("flying",mc.player.getAbilities().flying);p.addProperty("passenger",mc.player.isPassenger());p.addProperty("shift",mc.player.isShiftKeyDown());p.addProperty("menu_id",mc.player.containerMenu.containerId);p.addProperty("uuid",mc.player.getUUID().toString());report.add("last_client",p);}V6NetworkFiles.write("peer-"+PEER+".json",report);}
    private static void stop(Minecraft mc)throws Exception{release(mc);guard=true;if(finalReceiptReady&&mc.getConnection()!=null&&mc.getConnection().getConnection().isConnected())mc.getConnection().sendCommand("v6net receipt "+token);try{if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());}finally{guard=false;}report.addProperty("own_client_clean_disconnect",true);report.addProperty("final_connected",false);V6NetworkFiles.write("peer-"+PEER+".json",report);finished=true;mc.stop();}
    private static void require(boolean b,String text){if(!b)throw new IllegalStateException(text);}
}
