package pro.erez.interstice.client;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.item.TideIndicatorItem;
import pro.erez.interstice.rift.RiftLinks;
import pro.erez.interstice.rift.RiftTravel;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.worldgen.IslandWorld;

/** Actual expedition goods -> recipes -> player-built frame -> contact transfer -> echo return -> natural indicator phase change. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class SurvivalPortalSmoke {
    private static final BlockPos TABLE=new BlockPos(-205,70,471);
    private static boolean started;
    private static long began,deadline;
    private static int joined,stage,ticks,frameCrafts,cell;
    private static SurvivalMenuCraft craft;
    private static volatile BlockPos origin,echo;
    private static volatile Throwable failure;
    private static volatile boolean resolved;
    private static TidePhase initialPhase;
    private static final List<BlockPos> frame=new ArrayList<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("interstice.survivalPortal"))return;
        Minecraft mc=Minecraft.getInstance();
        if(!started && mc.screen instanceof TitleScreen) {
            started=true;began=System.nanoTime();deadline=began+TimeUnit.MINUTES.toNanos(40);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);
            mc.createWorldOpenFlows().openWorld("survival-expedition-route",()->{});return;
        }
        if(!started)return;if(SmokeWorldPrompts.advance(mc))return;
        if(failure!=null)throw new IllegalStateException("Survival portal trial failed",failure);
        if(System.nanoTime()>deadline)throw new IllegalStateException("Portal trial timeout stage="+stage);
        if(mc.player==null || mc.level==null || ++joined<40)return;
        if(!mc.player.isAlive() || mc.gameMode.getPlayerMode()!=GameType.SURVIVAL || mc.player.getAbilities().mayfly || mc.player.getAbilities().instabuild)throw new IllegalStateException("Survival violated");
        if(stage==0) {
            if(count(mc,Interstice.PRESSURE_COUPLER.get())!=1 || count(mc,Interstice.RIFTSILVER_INGOT.get())<4 || count(mc,Interstice.RIFTSTONE_ITEM.get())<12)throw new IllegalStateException("Recorded expedition goods required");
            open(mc,TABLE);craft=new SurvivalMenuCraft(Interstice.TIDE_INDICATOR.get(),Items.COPPER_INGOT,Interstice.PRESSURE_COUPLER.get(),Items.COPPER_INGOT,Items.COPPER_INGOT,Items.COMPASS,Items.COPPER_INGOT,Items.COPPER_INGOT,Items.AMETHYST_SHARD,Items.COPPER_INGOT);stage=1;
        } else if(stage==1 && craft.tick(mc)) {
            mc.player.closeContainer();mc.setScreen(null);equip(mc,Interstice.TIDE_INDICATOR.get());stage=2;ticks=0;
        } else if(stage==2 && ++ticks>=20) {
            if(count(mc,Interstice.TIDE_INDICATOR.get())!=1)throw new IllegalStateException("Actual indicator recipe absent");
            initialPhase=TideIndicatorItem.resolveState(mc.level).phase();mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            System.out.println("SURVIVAL_INDICATOR_FIRST_USE phase="+initialPhase);stage=3;ticks=0;
        } else if(stage==3 && ++ticks>=20) {
            open(mc,TABLE);craft=new SurvivalMenuCraft(Interstice.RIFT_LENS.get(),null,Items.AMETHYST_SHARD,null,Items.AMETHYST_SHARD,Interstice.RIFTSILVER_INGOT.get(),Items.AMETHYST_SHARD,null,Items.COMPASS,null);stage=4;
        } else if(stage==4 && craft.tick(mc)) {
            System.out.println("SURVIVAL_CRAFTED_LENS");craft=frameRecipe();stage=5;
        } else if(stage==5 && craft.tick(mc)) {
            if(++frameCrafts<3)craft=frameRecipe();else{mc.player.closeContainer();mc.setScreen(null);stage=6;ticks=0;}
        } else if(stage==6 && ++ticks>=20) {
            if(count(mc,Interstice.RIFT_FRAME_ITEM.get())!=12 || count(mc,Interstice.RIFT_LENS.get())!=1)throw new IllegalStateException("Actual frame/lens recipes incomplete");
            stage=7;ticks=0;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            server.execute(()->{try {
                var player=server.getPlayerList().getPlayer(uuid);var level=player.serverLevel();var start=player.blockPosition();
                if(server.getWorldData().isAllowCommands())throw new IllegalStateException("Cheats enabled");
                for(int radius=3;radius<=12 && origin==null;radius++)for(int dx=-radius;dx<=radius && origin==null;dx++)for(int dz=-radius;dz<=radius && origin==null;dz++) {
                    if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                    var base=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,start.offset(dx,0,dz));boolean clear=true;
                    for(int w=-1;w<=2;w++) {
                        var below=base.offset(w,-1,0);if(!level.getBlockState(below).isCollisionShapeFullBlock(level,below) || !level.getFluidState(below).isEmpty()){clear=false;break;}
                        for(int y=0;y<=4;y++)if(!level.getBlockState(base.offset(w,y,0)).isAir()){clear=false;break;}
                    }
                    if(clear && Math.abs(base.getY()-start.getY())<=2)origin=base.above();
                }
                if(origin==null)throw new IllegalStateException("No natural clear frame plot near base");
                System.out.println("SURVIVAL_PORTAL_PLOT origin="+origin);
            }catch(Throwable error){failure=error;}});
        } else if(stage==7 && origin!=null) {
            var stance=origin.offset(0,-1,-2);if(walk(mc,Vec3.atBottomCenterOf(stance),.15)) {
                stop(mc);for(int x=-1;x<=2;x++)frame.add(origin.offset(x,-1,0));for(int y=0;y<=2;y++){frame.add(origin.offset(-1,y,0));frame.add(origin.offset(2,y,0));}
                frame.add(origin.offset(-1,3,0));frame.add(origin.above(3));frame.add(origin.offset(1,3,0));stage=8;ticks=0;
            }
        } else if(stage==8 && ++ticks%10==0) {
            if(cell>=frame.size()){mc.options.keyShift.setDown(false);equip(mc,Interstice.RIFT_LENS.get());stage=9;ticks=0;return;}
            var point=frame.get(cell);Item item=cell==10?(count(mc,Items.COBBLESTONE)>0?Items.COBBLESTONE:Items.DARK_OAK_LOG):Interstice.RIFT_FRAME_ITEM.get();
            if(!mc.level.getBlockState(point).isAir()){cell++;return;}
            equip(mc,item);place(mc,point);
        } else if(stage==9 && ++ticks>=20) {
            open(mc,origin.below());stage=10;ticks=0;
        } else if(stage==10 && ++ticks>=20) {
            if(!mc.level.getBlockState(origin).is(Interstice.RIFT_PORTAL.get()))throw new IllegalStateException("Player-built frame did not activate");
            Screenshot.grab(mc.gameDirectory,"actual-survival-portal.png",mc.getMainRenderTarget(),message->{});stage=11;ticks=0;
        } else if(stage==11) {
            if(mc.level.dimension().equals(IslandWorld.TALL_WORLD)) {
                stop(mc);stage=12;ticks=0;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                server.execute(()->{try{var player=server.getPlayerList().getPlayer(uuid);var link=RiftLinks.get(server).byId(player.getPersistentData().getUUID(RiftTravel.ACTIVE));if(link==null || link.kind()!=RiftLinks.Kind.PORTAL)throw new IllegalStateException("Portal contact did not produce its own link");echo=link.echo().pos();System.out.println("SURVIVAL_PERMANENT_PORTAL_ENTERED echo="+echo);}catch(Throwable error){failure=error;}});
            } else if(walk(mc,Vec3.atBottomCenterOf(origin),.17))stop(mc);
        } else if(stage==12 && echo!=null && ++ticks>=80) {open(mc,echo);stage=13;ticks=0;}
        else if(stage==13 && mc.level.dimension().equals(Level.OVERWORLD) && ++ticks>=40) {
            System.out.println("SURVIVAL_PERMANENT_PORTAL_RETURNED");equip(mc,Interstice.TIDE_INDICATOR.get());stage=16;ticks=0;
        } else if(stage==16) {
            // Re-enter after the ordinary cooldown; the realm roof is safer than idling among vanilla monsters.
            if(++ticks<25)return;
            if(mc.level.dimension().equals(IslandWorld.TALL_WORLD)){stop(mc);stage=14;ticks=0;System.out.println("SURVIVAL_INDICATOR_SAFE_WAIT under_portal_echo_roof");}
            else if(walk(mc,Vec3.atBottomCenterOf(origin),.17))stop(mc);
        } else if(stage==14) {
            if(++ticks%20==0) {
                var client=TideIndicatorItem.resolveState(mc.level);var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                if(ticks%1200==0)System.out.println("SURVIVAL_INDICATOR_WAIT phase="+client.phase()+" remaining="+client.remainingTicks());
                if(client.phase()!=initialPhase)server.execute(()->{try {
                    var actual=TideManager.getState(server);if(actual.phase()!=client.phase())return;
                    var player=server.getPlayerList().getPlayer(uuid);if(!player.getMainHandItem().is(Interstice.TIDE_INDICATOR.get()))throw new IllegalStateException("Crafted indicator not held");
                    JsonObject result=new JsonObject();result.addProperty("passed",true);result.addProperty("indicator_crafted_and_used",true);result.addProperty("natural_phase_change",initialPhase+" -> "+actual.phase());result.addProperty("client_server_phase_match",true);result.addProperty("player_built_frame",true);result.addProperty("actual_portal_contact",true);result.addProperty("actual_echo_return",true);result.addProperty("seconds",(System.nanoTime()-began)/1e9);result.addProperty("world_game_ticks",server.overworld().getGameTime());
                    Files.writeString(mc.gameDirectory.toPath().resolve("survival-portal-indicator.json"),result.toString());resolved=true;System.out.println("SURVIVAL_PORTAL_RESULT "+result);
                }catch(Throwable error){failure=error;}});
            }
            if(resolved){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=15;ticks=0;}
        } else if(stage==15 && ++ticks>=40){open(mc,echo);stage=17;ticks=0;}
        else if(stage==17 && mc.level.dimension().equals(Level.OVERWORLD) && ++ticks>=20){stop(mc);mc.stop();}
    }
    private static SurvivalMenuCraft frameRecipe(){return new SurvivalMenuCraft(Interstice.RIFT_FRAME_ITEM.get(),null,Interstice.RIFTSTONE_ITEM.get(),null,Interstice.RIFTSTONE_ITEM.get(),Interstice.RIFTSILVER_INGOT.get(),Interstice.RIFTSTONE_ITEM.get(),null,Interstice.RIFTSTONE_ITEM.get(),null);}
    private static int count(Minecraft mc,Item item){return mc.player.getInventory().items.stream().filter(stack->stack.is(item)).mapToInt(stack->stack.getCount()).sum();}
    private static void equip(Minecraft mc,Item item){for(int slot=9;slot<45;slot++)if(mc.player.inventoryMenu.getSlot(slot).getItem().is(item)){if(slot!=36)mc.gameMode.handleInventoryMouseClick(0,slot,0,ClickType.SWAP,mc.player);mc.player.getInventory().selected=0;return;}throw new IllegalStateException("Actual inventory lacks "+item);}
    private static void open(Minecraft mc,BlockPos point){Vec3 delta=mc.player.position().subtract(Vec3.atCenterOf(point));Direction face=Math.abs(delta.x)>Math.abs(delta.z)?delta.x>0?Direction.EAST:Direction.WEST:delta.z>0?Direction.SOUTH:Direction.NORTH;Vec3 hit=Vec3.atCenterOf(point).add(face.getStepX()*.5,.4,face.getStepZ()*.5);look(mc,hit);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,face,point,false));}
    private static void place(Minecraft mc,BlockPos point){for(Direction toward:new Direction[]{Direction.DOWN,Direction.WEST,Direction.EAST,Direction.NORTH,Direction.SOUTH}){var support=point.relative(toward);if(mc.level.getBlockState(support).isAir() || mc.level.getBlockState(support).canBeReplaced())continue;var face=toward.getOpposite();var hit=Vec3.atCenterOf(support).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5);if(mc.player.getEyePosition().distanceToSqr(hit)>20.25)continue;look(mc,hit);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,face,support,false));return;}throw new IllegalStateException("No real reachable support for frame block "+point);}
    private static boolean walk(Minecraft mc,Vec3 target,double range){if(mc.player.position().distanceToSqr(target)<=range*range && mc.player.onGround())return true;look(mc,target);mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(mc.player.horizontalCollision);return false;}
    private static void look(Minecraft mc,Vec3 target){Vec3 delta=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
    private static void stop(Minecraft mc){mc.options.keyAttack.setDown(false);mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.gameMode.stopDestroyBlock();}
}
