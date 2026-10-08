package pro.erez.interstice.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.rift.RiftLinks;
import pro.erez.interstice.rift.RiftTravel;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.worldgen.IslandWorld;

/** Waits through the unmodified calendar under the real entry roof and reads a walking route to the natural ruin. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class SurvivalTideRouteSmoke {
    private static boolean started, surveying, sawSurge;
    private static long began, deadline;
    private static int ticks, finishTicks;
    private static volatile boolean finished;
    private static volatile Throwable failure;
    private static volatile String phase="joining";
    private static String lastPhase="";
    private static long surgeSamples;
    private static double startY;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("interstice.survivalTideRoute")) return;
        Minecraft mc=Minecraft.getInstance();
        if(!started && mc.screen instanceof TitleScreen) {
            started=true;began=System.nanoTime();deadline=began+TimeUnit.MINUTES.toNanos(45);
            mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);
            mc.createWorldOpenFlows().openWorld("survival-expedition-route",()->{});return;
        }
        if(!started)return;
        if(SmokeWorldPrompts.advance(mc))return;
        if(failure!=null)throw new IllegalStateException("Natural tide route check failed",failure);
        if(System.nanoTime()>deadline)throw new IllegalStateException("Natural tide timeout phase="+phase);
        if(mc.player==null || mc.level==null || mc.screen!=null)return;
        if(!mc.player.isAlive() || mc.gameMode.getPlayerMode()!=GameType.SURVIVAL || mc.player.getAbilities().mayfly || mc.player.getAbilities().instabuild || !mc.level.dimension().equals(IslandWorld.TALL_WORLD))throw new IllegalStateException("Survival realm invariants violated");
        if(finished) {if(++finishTicks==10)Screenshot.grab(mc.gameDirectory,"survived-natural-tide.png",mc.getMainRenderTarget(),message->{});if(finishTicks>=60)mc.stop();return;}
        if(++ticks<40)return;
        var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        if(!surveying) {
            surveying=true;startY=mc.player.getY();
            server.execute(()->{try{
                var player=server.getPlayerList().getPlayer(uuid);var level=player.serverLevel();
                if(server.getWorldData().isAllowCommands() || !ShelterDetector.isSheltered(level,player))throw new IllegalStateException("Cheats enabled or entry roof does not shelter actual player");
                var link=RiftLinks.get(server).byId(player.getPersistentData().getUUID(RiftTravel.ACTIVE));
                if(link==null || link.kind()!=RiftLinks.Kind.CAULDRON)throw new IllegalStateException("First entrance link absent");
                JsonObject survey=new JsonObject();survey.addProperty("scope","read-only fixed-seed route planning; actual walk remains pending");survey.addProperty("echo",link.echo().pos().toShortString());
                var start=player.blockPosition();Map<String,BlockPos> cells=new HashMap<>();JsonArray terrain=new JsonArray();
                for(int x=-12;x<=100;x++)for(int z=-12;z<=70;z++) {
                    BlockPos feet=null;
                    for(int y=173;y<=177;y++) {
                        var floor=new BlockPos(x,y-1,z);var pos=floor.above();
                        if(!level.getFluidState(floor).isEmpty() || !level.getBlockState(floor).isCollisionShapeFullBlock(level,floor))continue;
                        if(!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty())continue;
                        if(level.noCollision(new AABB(x+.2,y,z+.2,x+.8,y+1.8,z+.8)) && (feet==null || Math.abs(y-start.getY())<Math.abs(feet.getY()-start.getY())))feet=pos;
                    }
                    if(feet!=null){cells.put(key(x,z),feet);JsonArray row=new JsonArray();row.add(x);row.add(feet.getY());row.add(z);terrain.add(row);}
                }
                survey.add("walkable_cells",terrain);var queue=new ArrayDeque<BlockPos>();var parents=new HashMap<String,String>();
                String initial=key(start.getX(),start.getZ());queue.add(start);parents.put(initial,"");BlockPos goal=null;
                while(!queue.isEmpty()) {
                    var current=queue.removeFirst();
                    if(Math.abs(current.getX()-89)+Math.abs(current.getZ()-55)<=2 && current.getY()==175){goal=current;break;}
                    for(int[] delta:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        String nextKey=key(current.getX()+delta[0],current.getZ()+delta[1]);var next=cells.get(nextKey);
                        if(next==null || parents.containsKey(nextKey) || Math.abs(next.getY()-current.getY())>1)continue;
                        parents.put(nextKey,key(current.getX(),current.getZ()));queue.add(next);
                    }
                }
                JsonArray route=new JsonArray();
                if(goal!=null){var reversed=new java.util.ArrayList<BlockPos>();for(String k=key(goal.getX(),goal.getZ());!k.isEmpty();k=parents.get(k))reversed.add(k.equals(initial)?start:cells.get(k));java.util.Collections.reverse(reversed);for(var pos:reversed){JsonArray row=new JsonArray();row.add(pos.getX());row.add(pos.getY());row.add(pos.getZ());route.add(row);}}
                survey.addProperty("solid_walking_route_exists",goal!=null);survey.addProperty("route_steps",route.size());survey.add("route",route);
                Files.writeString(mc.gameDirectory.toPath().resolve("realm-route-survey.json"),survey.toString());
                System.out.println("SURVIVAL_REALM_SURVEY solid_route="+(goal!=null)+" steps="+route.size()+" echo="+link.echo().pos());
            }catch(Throwable error){failure=error;}});
        }
        if(ticks%20==0)server.execute(()->{try{
            var player=server.getPlayerList().getPlayer(uuid);var state=TideManager.getState(server);phase=state.phase().name();boolean roof=ShelterDetector.isSheltered(player.serverLevel(),player);
            if(!player.isAlive() || player.getHealth()<19.9 || Math.abs(player.getY()-startY)>.2 || !roof)throw new IllegalStateException("Natural shelter trial failed: health="+player.getHealth()+" y="+player.getY()+" roof="+roof);
            if(state.phase()==TidePhase.SURGE){sawSurge=true;surgeSamples++;}
            if(!phase.equals(lastPhase) || ticks%1200==0){lastPhase=phase;System.out.println("SURVIVAL_NATURAL_TIDE phase="+phase+" remaining_ticks="+state.remainingTicks()+" roof="+roof+" health="+player.getHealth());}
            if(sawSurge && state.phase()==TidePhase.CALM) {
                JsonObject report=new JsonObject();report.addProperty("passed",true);report.addProperty("natural_calendar",true);report.addProperty("roof_protected_actual_player",true);report.addProperty("surge_samples",surgeSamples);report.addProperty("health",player.getHealth());report.addProperty("seconds",(System.nanoTime()-began)/1e9);report.addProperty("scope","M12 natural tide under entry roof; ruin loot and return still pending");
                Files.writeString(mc.gameDirectory.toPath().resolve("survival-natural-tide.json"),report.toString());finished=true;System.out.println("SURVIVAL_NATURAL_TIDE "+report);
            }
        }catch(Throwable error){failure=error;}});
    }
    private static String key(int x,int z){return x+","+z;}
}
