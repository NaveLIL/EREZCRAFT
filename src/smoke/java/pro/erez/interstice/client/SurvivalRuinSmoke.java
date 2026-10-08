package pro.erez.interstice.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandWorld;

/** Walks the surveyed terrain, loots the actual ruin, mines real stone and returns by the ordinary echo interaction. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class SurvivalRuinSmoke {
    private static final BlockPos BARREL=new BlockPos(89,175,55);
    private static final List<BlockPos> route=new ArrayList<>();
    private static boolean started;
    private static long began,deadline;
    private static int stage,ticks,index=1,stuck;
    private static int joined,missingTicks;
    private static BlockPos echo;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("interstice.survivalRuin"))return;
        Minecraft mc=Minecraft.getInstance();
        if(!started && mc.screen instanceof TitleScreen) {
            started=true;began=System.nanoTime();deadline=began+TimeUnit.MINUTES.toNanos(15);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);
            stage=Integer.getInteger("interstice.ruinResumeStage",0);
            try {
                var survey=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("realm-route-survey.json"))).getAsJsonObject();
                var tide=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("survival-natural-tide.json"))).getAsJsonObject();
                if(!survey.get("solid_walking_route_exists").getAsBoolean() || !tide.get("passed").getAsBoolean())throw new IllegalStateException("Actual natural tide and solid route required");
                for(var value:survey.getAsJsonArray("route")){var row=value.getAsJsonArray();route.add(new BlockPos(row.get(0).getAsInt(),row.get(1).getAsInt(),row.get(2).getAsInt()));}
                String[] xyz=survey.get("echo").getAsString().split(", ");echo=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));
            }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
            mc.createWorldOpenFlows().openWorld("survival-expedition-route",()->{});return;
        }
        if(!started)return;if(SmokeWorldPrompts.advance(mc))return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Ruin route deadline stage="+stage+" index="+index);
        if(mc.player==null || mc.level==null)return;
        if(++joined<40)return;
        if(!mc.player.isAlive() || mc.gameMode.getPlayerMode()!=GameType.SURVIVAL || mc.player.getAbilities().mayfly || mc.player.getAbilities().instabuild)throw new IllegalStateException("Survival violated");
        if(stage==0 && ++ticks>=40){stage=1;ticks=0;}
        else if(stage==1) {
            if(index>=route.size()){stop(mc);stage=2;ticks=0;return;}
            if(walk(mc,Vec3.atBottomCenterOf(route.get(index)),.17)){stop(mc);index++;stuck=0;if(index%20==0)System.out.println("SURVIVAL_RUIN_WALK index="+index+" feet="+mc.player.position());}
            else if(++stuck>240)throw new IllegalStateException("Real walk blocked at "+route.get(index)+" actual="+mc.player.position());
        } else if(stage==2 && ++ticks>=10){open(mc,BARREL);stage=3;ticks=0;}
        else if(stage==3 && mc.player.containerMenu!=mc.player.inventoryMenu && ++ticks>=10) {
            var menu=mc.player.containerMenu;for(int slot=0;slot<menu.slots.size()-36;slot++)if(!menu.getSlot(slot).getItem().isEmpty())mc.gameMode.handleInventoryMouseClick(menu.containerId,slot,0,ClickType.QUICK_MOVE,mc.player);
            stage=4;ticks=0;
        } else if(stage==4 && ++ticks>=30) {
            mc.player.closeContainer();mc.setScreen(null);
            if(count(mc,Interstice.PRESSURE_COUPLER.get())!=1 || count(mc,Interstice.RIFTSILVER_INGOT.get())<4)throw new IllegalStateException("Actual ruin loot incomplete");
            System.out.println("SURVIVAL_RUIN_LOOT membrane=1 silver="+count(mc,Interstice.RIFTSILVER_INGOT.get()));
            equip(mc,Items.STONE_PICKAXE);stage=5;ticks=0;
        } else if(stage==5) {
            if(++ticks%200==0)System.out.println("SURVIVAL_RUIN_MINING stock="+count(mc,Interstice.RIFTSTONE_ITEM.get())+" feet="+mc.player.position()+" onGround="+mc.player.onGround());
            if(count(mc,Interstice.RIFTSTONE_ITEM.get())>=12){stop(mc);stage=6;ticks=0;return;}
            BlockPos next=null;double nearest=Double.MAX_VALUE;
            var dropped=mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,mc.player.getBoundingBox().inflate(4.5),entity->entity.getItem().is(Interstice.RIFTSTONE_ITEM.get()) && entity.getY()<=mc.player.getY()+1.5 && entity.getY()>=mc.player.getY()-.5).stream().min(java.util.Comparator.comparingDouble(entity->entity.distanceToSqr(mc.player))).orElse(null);
            if(dropped!=null) {
                stop(mc);Vec3 target=new Vec3(dropped.getX(),mc.player.getY(),dropped.getZ());
                if(mc.player.position().distanceToSqr(target)>.04)walk(mc,target,.18);
                return;
            }
            for(var point:BlockPos.betweenClosed(mc.player.blockPosition().offset(-3,0,-3),mc.player.blockPosition().offset(3,2,3))) {
                double distance=mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(point));
                if(mc.level.getBlockState(point).is(Interstice.RIFTSTONE.get()) && distance<nearest && distance<=20.25){next=point.immutable();nearest=distance;}
            }
            if(next==null){stop(mc);if(++missingTicks>60)throw new IllegalStateException("No reachable ruin stone or pickups; inventory="+count(mc,Interstice.RIFTSTONE_ITEM.get()));return;}
            missingTicks=0;mine(mc,next);
        } else if(stage==6 && ++ticks>=40) {
            stop(mc);System.out.println("SURVIVAL_RUIN_STONE count="+count(mc,Interstice.RIFTSTONE_ITEM.get()));
            Screenshot.grab(mc.gameDirectory,"actual-survival-ruin.png",mc.getMainRenderTarget(),message->{});index=route.size()-2;stage=7;ticks=0;stuck=0;
        } else if(stage==7) {
            if(index<0){stop(mc);stage=8;ticks=0;return;}
            if(walk(mc,Vec3.atBottomCenterOf(route.get(index)),.17)){stop(mc);index--;stuck=0;if(index%20==0)System.out.println("SURVIVAL_RUIN_RETURN index="+index+" feet="+mc.player.position());}
            else if(++stuck>240)throw new IllegalStateException("Return walk blocked at "+route.get(index)+" actual="+mc.player.position());
        } else if(stage==8 && ++ticks>=20){open(mc,echo);stage=9;ticks=0;}
        else if(stage==9 && mc.level.dimension().equals(Level.OVERWORLD) && ++ticks>=40) {
            if(count(mc,Interstice.PRESSURE_COUPLER.get())!=1 || count(mc,Interstice.RIFTSTONE_ITEM.get())<12)throw new IllegalStateException("Expedition goods did not survive ordinary echo return");
            JsonObject result=new JsonObject();result.addProperty("passed",true);result.addProperty("actual_walk_steps",route.size());result.addProperty("actual_barrel_looted",true);result.addProperty("actual_echo_return",true);result.addProperty("riftstone",count(mc,Interstice.RIFTSTONE_ITEM.get()));result.addProperty("health",mc.player.getHealth());result.addProperty("seconds",(System.nanoTime()-began)/1e9);result.addProperty("scope","M12 expedition and return; indicator and permanent portal still pending");
            try{Files.writeString(mc.gameDirectory.toPath().resolve("survival-ruin-return.json"),result.toString());}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
            System.out.println("SURVIVAL_RUIN_RESULT "+result);stage=10;ticks=0;
        } else if(stage==10 && ++ticks>=40){stop(mc);mc.stop();}
    }
    private static boolean walk(Minecraft mc,Vec3 target,double range){if(mc.player.position().distanceToSqr(target)<=range*range && mc.player.onGround())return true;look(mc,target);mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(mc.player.horizontalCollision);return false;}
    private static void mine(Minecraft mc,BlockPos point){if(!mc.mouseHandler.isMouseGrabbed()){mc.mouseHandler.grabMouse();mc.options.keyAttack.setDown(false);return;}look(mc,Vec3.atCenterOf(point));mc.options.keyAttack.setDown(true);}
    private static void open(Minecraft mc,BlockPos point){Vec3 delta=mc.player.position().subtract(Vec3.atCenterOf(point));Direction face=Math.abs(delta.x)>Math.abs(delta.z)?delta.x>0?Direction.EAST:Direction.WEST:delta.z>0?Direction.SOUTH:Direction.NORTH;Vec3 hit=Vec3.atCenterOf(point).add(face.getStepX()*.5,.4,face.getStepZ()*.5);look(mc,hit);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,face,point,false));}
    private static void look(Minecraft mc,Vec3 target){Vec3 delta=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
    private static int count(Minecraft mc,Item item){return mc.player.getInventory().items.stream().filter(stack->stack.is(item)).mapToInt(stack->stack.getCount()).sum();}
    private static void equip(Minecraft mc,Item item){for(int slot=9;slot<45;slot++)if(mc.player.inventoryMenu.getSlot(slot).getItem().is(item)){if(slot!=36)mc.gameMode.handleInventoryMouseClick(0,slot,0,ClickType.SWAP,mc.player);mc.player.getInventory().selected=0;return;}throw new IllegalStateException("Real inventory lacks "+item);}
    private static void stop(Minecraft mc){mc.options.keyAttack.setDown(false);mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.gameMode.stopDestroyBlock();}
}
