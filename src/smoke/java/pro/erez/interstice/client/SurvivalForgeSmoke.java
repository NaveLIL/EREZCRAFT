package pro.erez.interstice.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;

/** Real quarry, sheltered furnace, smelting and vanilla entrance-kit recipes in the recorded Survival world. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class SurvivalForgeSmoke {
    private static final BlockPos CENTRE = new BlockPos(-205,70,472), TABLE = CENTRE.north(), FURNACE = CENTRE.south();
    private static boolean started;
    private static long began, deadline;
    private static int stage, ticks, pillarY, campCell;
    private static int joinedTicks;
    private static SurvivalMenuCraft craft;
    private static BlockPos misplacedFurnace;
    private static final List<BlockPos> camp = new ArrayList<>();
    private static final JsonArray actions = new JsonArray();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("interstice.survivalForge")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true; began = System.nanoTime(); deadline = began + TimeUnit.MINUTES.toNanos(15);
            stage = Integer.getInteger("interstice.forgeResumeStage", 0);
            mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(5);
            mc.createWorldOpenFlows().openWorld("survival-expedition-route", () -> {}); return;
        }
        if (!started) return;
        if (SmokeWorldPrompts.advance(mc)) return;
        if (System.nanoTime() > deadline) throw new IllegalStateException("Survival forge timeout stage=" + stage);
        if (mc.player == null || mc.level == null) return;
        if (++joinedTicks < 40) return;
        if (!mc.player.isAlive() || mc.gameMode.getPlayerMode() != GameType.SURVIVAL || mc.player.getAbilities().mayfly || mc.player.getAbilities().instabuild) throw new IllegalStateException("Survival violated");
        if (stage == 0) {
            if (walk(mc,Vec3.atBottomCenterOf(CENTRE),.06)) { stop(mc); if (++ticks >= 10) { stage=1; ticks=0; action(mc,"returned_to_recorded_workbench"); } }
        } else if (stage == 1) {
            if (mc.player.getY() <= 63.01 && mc.player.onGround()) { stop(mc); stage=2; ticks=0; return; }
            var below=mc.player.blockPosition().below();
            if (!mc.level.getFluidState(below).isEmpty()) throw new IllegalStateException("Water in original quarry shaft");
            if (!mc.level.getBlockState(below).isAir()) {
                Item tool=mc.level.getBlockState(below).is(BlockTags.PLANKS) || mc.level.getBlockState(below).is(BlockTags.LOGS) ? Items.STONE_AXE : Items.STONE_PICKAXE;
                if (!mc.player.getMainHandItem().is(tool)) equip(mc,tool);
                mine(mc,below);
            }
        } else if (stage == 2) {
            if (count(mc,Items.COBBLESTONE) >= 20) { stop(mc); stage=3; ticks=0; return; }
            if (!mc.player.getMainHandItem().is(Items.STONE_PICKAXE)) equip(mc,Items.STONE_PICKAXE);
            BlockPos next=null; double nearest=Double.MAX_VALUE;
            for (var point:BlockPos.betweenClosed(mc.player.blockPosition().offset(-2,0,-2),mc.player.blockPosition().offset(2,2,2))) {
                double distance=mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(point));
                if (mc.level.getBlockState(point).is(Blocks.STONE) && distance<nearest && distance<=20.25) { next=point.immutable(); nearest=distance; }
            }
            if (next==null) throw new IllegalStateException("Original quarry lacks reachable stone");
            mine(mc,next);
            if (++ticks%200==0) System.out.println("SURVIVAL_QUARRY cobblestone="+count(mc,Items.COBBLESTONE)+" feet="+mc.player.position());
        } else if (stage==3 && ++ticks>=40) { pillarY=mc.player.blockPosition().getY(); stage=4; ticks=0; action(mc,"quarried_furnace_and_shelter_blocks"); }
        else if (stage==4) {
            if (mc.player.getY()>=70 && mc.player.onGround()) { stop(mc); stage=5; ticks=0; action(mc,"rebuilt_shaft_with_reclaimed_planks"); return; }
            if (pillarY>=70) { mc.options.keyJump.setDown(false); return; }
            var point=new BlockPos(CENTRE.getX(),pillarY,CENTRE.getZ());
            if (!mc.level.getBlockState(point).canBeReplaced()) { pillarY++; ticks=0; return; }
            if (ticks++==0) {
                Item plank=null; for (var stack:mc.player.getInventory().items) if (stack.is(ItemTags.PLANKS)) { plank=stack.getItem(); break; }
                if (plank==null) throw new IllegalStateException("Reclaimed shaft planks missing"); equip(mc,plank);
            }
            if (ticks>5) mc.options.keyJump.setDown(true);
            if (mc.player.getY()>=pillarY+1.02 && ticks%3==0) placeAbove(mc,point);
        } else if (stage==5 && ++ticks>=10) {
            open(mc,TABLE); craft=new SurvivalMenuCraft(Items.FURNACE,Items.COBBLESTONE,Items.COBBLESTONE,Items.COBBLESTONE,Items.COBBLESTONE,null,Items.COBBLESTONE,Items.COBBLESTONE,Items.COBBLESTONE,Items.COBBLESTONE); stage=6; ticks=0;
        } else if (stage==6 && craft.tick(mc)) {
            mc.player.closeContainer(); mc.setScreen(null); equip(mc,Items.FURNACE); stage=7; ticks=0; action(mc,"crafted_furnace");
        } else if (stage==7) {
            if (!mc.level.getBlockState(FURNACE).is(Blocks.FURNACE) && count(mc,Items.FURNACE)==0) {
                for (int dy=-3;dy<=3;dy++) if (mc.level.getBlockState(FURNACE.offset(0,dy,0)).is(Blocks.FURNACE)) misplacedFurnace=FURNACE.offset(0,dy,0);
                if (misplacedFurnace==null) throw new IllegalStateException("Crafted furnace is absent from both inventory and nearby terrain");
                equip(mc,Items.STONE_PICKAXE); stage=19; ticks=0; action(mc,"recovering_furnace_placed_on_lower_ground"); return;
            }
            if (!walk(mc,Vec3.atBottomCenterOf(CENTRE),.06)) return;
            stop(mc);
            if (!mc.level.getBlockState(FURNACE).is(Blocks.FURNACE) && !mc.level.getBlockState(FURNACE).canBeReplaced()) {
                if (!mc.player.getMainHandItem().is(Items.STONE_PICKAXE)) equip(mc,Items.STONE_PICKAXE);
                mine(mc,FURNACE); ticks=0; return;
            }
            if (mc.level.getBlockState(FURNACE.below()).canBeReplaced()) {
                if (!mc.player.getMainHandItem().is(Items.COBBLESTONE)) equip(mc,Items.COBBLESTONE);
                if (++ticks%10==0) { placeAbove(mc,FURNACE.below()); ticks=0; }
                return;
            }
            stop(mc);
            if (!mc.level.getBlockState(FURNACE).is(Blocks.FURNACE) && !mc.player.getMainHandItem().is(Items.FURNACE)) equip(mc,Items.FURNACE);
            if (++ticks==10) placeAbove(mc,FURNACE);
            if (ticks>=20) {
                if (!mc.level.getBlockState(FURNACE).is(Blocks.FURNACE)) throw new IllegalStateException("Actual furnace placement failed target="+mc.level.getBlockState(FURNACE)+" held="+mc.player.getMainHandItem());
                for (Direction direction:Direction.Plane.HORIZONTAL) for (int y=0;y<=1;y++) camp.add(CENTRE.relative(direction).above(y));
                camp.add(CENTRE.east().above(2)); camp.add(CENTRE.above(2));
                equip(mc,Items.COBBLESTONE); mc.options.keyShift.setDown(true); stage=8; ticks=0;
            }
        } else if (stage==8 && ++ticks%10==0) {
            if (campCell>=camp.size()) { mc.options.keyShift.setDown(false); action(mc,"built_actual_smelting_shelter"); stage=9; ticks=0; return; }
            var target=camp.get(campCell);
            if (!mc.level.getBlockState(target).canBeReplaced()) { campCell++; return; }
            if (target.equals(CENTRE.above(2))) {
                var support=CENTRE.east().above(2); look(mc,Vec3.atCenterOf(support));
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support),Direction.WEST,support,false));
            } else placeAbove(mc,target);
        } else if (stage==9 && ++ticks>=10) { open(mc,FURNACE); stage=10; ticks=0; }
        else if (stage==10 && mc.player.containerMenu!=mc.player.inventoryMenu && ++ticks>=10) {
            quick(mc,Items.RAW_COPPER); quick(mc,Items.COAL); stage=11; ticks=0; action(mc,"started_real_copper_smelting");
        } else if (stage==11) {
            if (++ticks%20==0 && !mc.player.containerMenu.getSlot(2).getItem().isEmpty()) click(mc,2,0,ClickType.QUICK_MOVE);
            if (count(mc,Items.COPPER_INGOT)>=9) { quick(mc,Items.RAW_IRON); stage=12; ticks=0; action(mc,"smelted_nine_copper_ingots"); }
        } else if (stage==12) {
            if (++ticks%20==0 && !mc.player.containerMenu.getSlot(2).getItem().isEmpty()) click(mc,2,0,ClickType.QUICK_MOVE);
            if (count(mc,Items.IRON_INGOT)>=10) { mc.player.closeContainer(); mc.setScreen(null); stage=13; ticks=0; action(mc,"smelted_ten_iron_ingots"); }
        } else if (stage==13 && ++ticks>=10) {
            open(mc,TABLE); craft=new SurvivalMenuCraft(Items.COPPER_BLOCK,Items.COPPER_INGOT,Items.COPPER_INGOT,Items.COPPER_INGOT,Items.COPPER_INGOT,Items.COPPER_INGOT,Items.COPPER_INGOT,Items.COPPER_INGOT,Items.COPPER_INGOT,Items.COPPER_INGOT); stage=14;
        } else if (stage==14 && craft.tick(mc)) {
            action(mc,"crafted_copper_block"); craft=new SurvivalMenuCraft(Items.CAULDRON,Items.IRON_INGOT,null,Items.IRON_INGOT,Items.IRON_INGOT,null,Items.IRON_INGOT,Items.IRON_INGOT,Items.IRON_INGOT,Items.IRON_INGOT); stage=15;
        } else if (stage==15 && craft.tick(mc)) {
            action(mc,"crafted_cauldron"); craft=new SurvivalMenuCraft(Items.BUCKET,Items.IRON_INGOT,null,Items.IRON_INGOT,null,Items.IRON_INGOT,null,null,null,null); stage=16;
        } else if (stage==16 && craft.tick(mc)) { mc.player.closeContainer(); mc.setScreen(null); stage=17; ticks=0; }
        else if (stage==17 && ++ticks>=20) {
            if (count(mc,Items.COPPER_BLOCK)!=1 || count(mc,Items.CAULDRON)!=1 || count(mc,Items.BUCKET)!=1) throw new IllegalStateException("Real entrance-kit recipes incomplete");
            JsonObject result=new JsonObject(); result.addProperty("passed",true); result.addProperty("scope","M12 actual Survival entrance kit; first entry pending"); result.addProperty("seconds",(System.nanoTime()-began)/1e9); result.add("actions",actions);
            try { Files.writeString(mc.gameDirectory.toPath().resolve("survival-forge.json"),result.toString()); } catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            stage=18; ticks=0; System.out.println("SURVIVAL_FORGE "+result);
        } else if (stage==18 && ++ticks>=40) { stop(mc); mc.stop(); }
        else if (stage==19) {
            if (mc.level.getBlockState(misplacedFurnace).is(Blocks.FURNACE)) { mine(mc,misplacedFurnace); return; }
            stop(mc);
            if (count(mc,Items.FURNACE)==0) walk(mc,Vec3.atBottomCenterOf(misplacedFurnace),.3);
            else { stop(mc); stage=7; ticks=0; action(mc,"recovered_actual_furnace_drop"); }
        }
    }
    private static void open(Minecraft mc,BlockPos point) {
        Vec3 delta=mc.player.position().subtract(Vec3.atCenterOf(point));
        Direction face=Math.abs(delta.x)>Math.abs(delta.z) ? delta.x>0 ? Direction.EAST : Direction.WEST : delta.z>0 ? Direction.SOUTH : Direction.NORTH;
        Vec3 hit=Vec3.atCenterOf(point).add(face.getStepX()*.5,.4,face.getStepZ()*.5);
        look(mc,hit); mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,face,point,false));
    }
    private static void placeAbove(Minecraft mc,BlockPos point) {
        for (Direction toward:new Direction[]{Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.UP}) {
            var support=point.relative(toward);
            if (mc.level.getBlockState(support).isAir() || mc.level.getBlockState(support).canBeReplaced()) continue;
            Direction face=toward.getOpposite(); Vec3 hit=Vec3.atCenterOf(support).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5);
            if (mc.player.getEyePosition().distanceToSqr(hit)>20.25) continue;
            look(mc,hit); mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,face,support,false)); return;
        }
        throw new IllegalStateException("No real supporting block face for placement at "+point);
    }
    private static void mine(Minecraft mc,BlockPos point) {
        if(!mc.mouseHandler.isMouseGrabbed()) { mc.mouseHandler.grabMouse(); mc.options.keyAttack.setDown(false); return; }
        look(mc,Vec3.atCenterOf(point)); mc.options.keyAttack.setDown(true);
    }
    private static boolean walk(Minecraft mc,Vec3 target,double range) { if(mc.player.position().distanceToSqr(target)<=range*range) return true; look(mc,target); mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(mc.player.horizontalCollision); return false; }
    private static void look(Minecraft mc,Vec3 target) { Vec3 delta=target.subtract(mc.player.getEyePosition()); mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z))); mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)))); }
    private static int count(Minecraft mc,Item item) { return mc.player.getInventory().items.stream().filter(stack->stack.is(item)).mapToInt(stack->stack.getCount()).sum(); }
    private static void equip(Minecraft mc,Item item) { for(int slot=9;slot<45;slot++) if(mc.player.inventoryMenu.getSlot(slot).getItem().is(item)) { if(slot!=36) mc.gameMode.handleInventoryMouseClick(0,slot,0,ClickType.SWAP,mc.player); mc.player.getInventory().selected=0; return; } throw new IllegalStateException("Actual inventory lacks "+item); }
    private static void quick(Minecraft mc,Item item) { for(int slot=3;slot<mc.player.containerMenu.slots.size();slot++) if(mc.player.containerMenu.getSlot(slot).getItem().is(item)) { click(mc,slot,0,ClickType.QUICK_MOVE); return; } throw new IllegalStateException("Actual smelting stock missing "+item); }
    private static void click(Minecraft mc,int slot,int button,ClickType type) { mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,button,type,mc.player); }
    private static void stop(Minecraft mc) { mc.options.keyAttack.setDown(false); mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(false); mc.gameMode.stopDestroyBlock(); }
    private static void action(Minecraft mc,String name) {
        JsonObject entry=new JsonObject(); entry.addProperty("action",name); entry.addProperty("seconds",(System.nanoTime()-began)/1e9); entry.addProperty("health",mc.player.getHealth()); entry.addProperty("food",mc.player.getFoodData().getFoodLevel()); actions.add(entry); System.out.println("SURVIVAL_FORGE "+entry);
        JsonObject checkpoint=new JsonObject(); checkpoint.addProperty("stage",stage); checkpoint.add("actions",actions);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("survival-forge-checkpoint.json"),checkpoint.toString()); } catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
