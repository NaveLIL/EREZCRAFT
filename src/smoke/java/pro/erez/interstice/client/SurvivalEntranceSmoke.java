package pro.erez.interstice.client;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandWorld;

/** Controlled thunder is a declared test condition. The first transfer is the real random cauldron interaction. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class SurvivalEntranceSmoke {
    private static final BlockPos CENTRE=new BlockPos(-205,70,472), COPPER=CENTRE.east(3), CAULDRON=COPPER.above();
    private static boolean started;
    private static long deadline;
    private static int stage,ticks,joined,attempts;
    private static volatile BlockPos water,shore;
    private static volatile Throwable failure;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("interstice.survivalEntrance")) return;
        Minecraft mc=Minecraft.getInstance();
        if(!started && mc.screen instanceof TitleScreen) {
            started=true; deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(20); mc.options.pauseOnLostFocus=false; mc.options.renderDistance().set(5);
            mc.createWorldOpenFlows().openWorld("survival-expedition-route",()->{}); return;
        }
        if(!started) return;
        if(SmokeWorldPrompts.advance(mc)) return;
        if(failure!=null) throw new IllegalStateException("Survival entrance failed",failure);
        if(System.nanoTime()>deadline) throw new IllegalStateException("Entrance deadline stage="+stage);
        if(mc.player==null || mc.level==null || mc.screen!=null || ++joined<40) return;
        if(!mc.player.isAlive() || mc.gameMode.getPlayerMode()!=GameType.SURVIVAL || mc.player.getAbilities().mayfly || mc.player.getAbilities().instabuild) throw new IllegalStateException("Survival violated");
        if(mc.level.dimension().equals(IslandWorld.TALL_WORLD) && stage<10) {
            stop(mc); JsonObject result=new JsonObject(); result.addProperty("passed",true); result.addProperty("actual_cauldron_interaction",true); result.addProperty("attempts",attempts);
            result.addProperty("weather_condition","controlled thunder in the private test world; production random chance unchanged"); result.addProperty("realm_feet",mc.player.blockPosition().toShortString());
            result.addProperty("scope","M12 first entry confirmed; ruin, tide, return and permanent portal pending");
            try{Files.writeString(mc.gameDirectory.toPath().resolve("survival-entry.json"),result.toString());}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
            System.out.println("SURVIVAL_ENTRY "+result); stage=10;ticks=0; return;
        }
        if(stage==0) {
            equip(mc,Items.STONE_PICKAXE);
            var upper=CENTRE.east().above();
            if(!mc.level.getBlockState(upper).isAir()) mine(mc,upper);
            else {stop(mc);stage=1;}
        } else if(stage==1) {
            var door=CENTRE.east();
            if(!mc.level.getBlockState(door).isAir()) mine(mc,door);
            else {stop(mc);stage=2;ticks=0;}
        } else if(stage==2) {
            if(walk(mc,Vec3.atBottomCenterOf(CENTRE.east()),.1)) {
                stop(mc);
                if(mc.level.getBlockState(COPPER.below()).canBeReplaced()) {equip(mc,Items.COBBLESTONE);if(++ticks%10==0) place(mc,COPPER.below());}
                else {equip(mc,Items.COPPER_BLOCK);stage=3;ticks=0;}
            }
        } else if(stage==3) {
            if(++ticks==10) place(mc,COPPER);
            if(ticks>=20) {if(!mc.level.getBlockState(COPPER).is(Blocks.COPPER_BLOCK)) throw new IllegalStateException("Actual copper block placement failed");equip(mc,Items.CAULDRON);stage=4;ticks=0;}
        } else if(stage==4) {
            if(++ticks==10) place(mc,CAULDRON);
            if(ticks>=20) {
                if(!mc.level.getBlockState(CAULDRON).is(Blocks.CAULDRON)) throw new IllegalStateException("Actual cauldron placement failed");
                stage=5;ticks=0;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                server.execute(()->{try{
                    var player=server.getPlayerList().getPlayer(uuid);var level=player.serverLevel();var start=player.blockPosition();double nearest=Double.MAX_VALUE;
                    if(server.getWorldData().isAllowCommands()) throw new IllegalStateException("Cheats enabled");
                    for(int dx=-48;dx<=48;dx++) for(int dz=-48;dz<=48;dz++) {
                        int x=start.getX()+dx,z=start.getZ()+dz;int y=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;var pos=new BlockPos(x,y,z);
                        if(!level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource()) continue;
                        for(Direction side:Direction.Plane.HORIZONTAL) {
                            var land=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.relative(side));
                            if(land.getY()>y+3 || !level.getFluidState(land.below()).isEmpty() || level.getBlockState(land.below()).getCollisionShape(level,land.below()).isEmpty()) continue;
                            if(!level.getBlockState(land).getCollisionShape(level,land).isEmpty() || !level.getBlockState(land.above()).getCollisionShape(level,land.above()).isEmpty()) continue;
                            double distance=start.distSqr(land);if(distance<nearest){nearest=distance;water=pos.immutable();shore=land.immutable();}
                        }
                    }
                    if(water==null) throw new IllegalStateException("No reachable natural shore within 48 blocks");
                    System.out.println("SURVIVAL_WATER source="+water+" shore="+shore);
                }catch(Throwable error){failure=error;}});
            }
        } else if(stage==5 && shore!=null) {
            if(walk(mc,Vec3.atBottomCenterOf(shore),.15)){stop(mc);equip(mc,Items.BUCKET);stage=6;ticks=0;}
        } else if(stage==6) {
            look(mc,Vec3.atCenterOf(water).add(0,.4,0));
            if(++ticks==10) mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            if(ticks>=30){if(!mc.player.getMainHandItem().is(Items.WATER_BUCKET)) throw new IllegalStateException("Natural source did not fill the real bucket");stage=7;ticks=0;
                var server=mc.getSingleplayerServer();server.execute(()->server.overworld().setWeatherParameters(0,24000,true,true));
                System.out.println("SURVIVAL_WEATHER controlled thunder; real cauldron chance remains 1/8");}
        } else if(stage==7) {
            if(walk(mc,Vec3.atBottomCenterOf(CENTRE.east()),.15)){stop(mc);stage=8;ticks=0;}
        } else if(stage==8) {
            if(++ticks==10) useCauldron(mc);
            if(ticks>=25){var state=mc.level.getBlockState(CAULDRON);if(!state.is(Blocks.WATER_CAULDRON) || state.getValue(LayeredCauldronBlock.LEVEL)!=3) throw new IllegalStateException("Real bucket did not fill cauldron");stage=9;ticks=0;}
        } else if(stage==9) {
            if(++ticks==10){useCauldron(mc);attempts++;System.out.println("SURVIVAL_CAULDRON_ATTEMPT "+attempts);}
            if(ticks>=60){if(!mc.player.getMainHandItem().is(Items.WATER_BUCKET)) throw new IllegalStateException("Vanilla pickup changed unexpectedly");stage=8;ticks=0;}
        } else if(stage==10 && ++ticks>=100){stop(mc);mc.stop();}
    }
    private static void useCauldron(Minecraft mc){Vec3 hit=Vec3.atCenterOf(CAULDRON).add(-.5,.3,0);look(mc,hit);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,Direction.WEST,CAULDRON,false));}
    private static void place(Minecraft mc,BlockPos point){for(Direction toward:new Direction[]{Direction.DOWN,Direction.WEST,Direction.NORTH,Direction.SOUTH,Direction.EAST}){var support=point.relative(toward);if(mc.level.getBlockState(support).isAir() || mc.level.getBlockState(support).canBeReplaced())continue;Direction face=toward.getOpposite();Vec3 hit=Vec3.atCenterOf(support).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5);if(mc.player.getEyePosition().distanceToSqr(hit)>20.25)continue;look(mc,hit);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,face,support,false));return;}throw new IllegalStateException("No physical support for "+point);}
    private static void mine(Minecraft mc,BlockPos point){if(!mc.mouseHandler.isMouseGrabbed()){mc.mouseHandler.grabMouse();mc.options.keyAttack.setDown(false);return;}look(mc,Vec3.atCenterOf(point));mc.options.keyAttack.setDown(true);}
    private static boolean walk(Minecraft mc,Vec3 target,double range){if(mc.player.position().distanceToSqr(target)<=range*range)return true;look(mc,target);mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(mc.player.horizontalCollision);return false;}
    private static void look(Minecraft mc,Vec3 target){Vec3 delta=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
    private static void equip(Minecraft mc,Item item){for(int slot=9;slot<45;slot++)if(mc.player.inventoryMenu.getSlot(slot).getItem().is(item)){if(slot!=36)mc.gameMode.handleInventoryMouseClick(0,slot,0,ClickType.SWAP,mc.player);mc.player.getInventory().selected=0;return;}throw new IllegalStateException("Real inventory lacks "+item);}
    private static void stop(Minecraft mc){mc.options.keyAttack.setDown(false);mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.gameMode.stopDestroyBlock();}
}
