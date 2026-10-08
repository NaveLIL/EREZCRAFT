package pro.erez.interstice.client;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** Recovers a saved Survival mining attempt and crafts a replacement from its actual inventory. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class SurvivalToolRepairSmoke {
    private static boolean started;
    private static long deadline;
    private static int stage, ticks, pillarY;
    private static final BlockPos TABLE = new BlockPos(-205,70,471);
    private static SurvivalMenuCraft craft;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("interstice.survivalRepair")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true; deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
            mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(5);
            mc.createWorldOpenFlows().openWorld("survival-expedition-route", () -> {}); return;
        }
        if (!started) return;
        if (SmokeWorldPrompts.advance(mc)) return;
        if (System.nanoTime() > deadline) throw new IllegalStateException("Survival tool replacement timed out stage=" + stage);
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.isAlive() || mc.gameMode.getPlayerMode() != GameType.SURVIVAL || mc.player.getAbilities().mayfly || mc.player.getAbilities().instabuild) throw new IllegalStateException("Survival violated");
        if (stage == 0 && ++ticks >= 40) {
            pillarY = mc.player.blockPosition().getY();
            stage = count(mc, Items.COBBLESTONE) < 3 ? 1 : 2; ticks = 0;
            if (stage == 1) equip(mc, Items.WOODEN_PICKAXE);
            System.out.println("SURVIVAL_REPAIR inventory_cobblestone=" + count(mc, Items.COBBLESTONE) + " feet=" + mc.player.position());
        } else if (stage == 1) {
            if (count(mc, Items.COBBLESTONE) >= 3) { stop(mc); pillarY = mc.player.blockPosition().getY(); stage = 2; ticks = 0; return; }
            BlockPos next = null; double distance = Double.MAX_VALUE;
            for (var point : BlockPos.betweenClosed(mc.player.blockPosition().offset(-2,-1,-2), mc.player.blockPosition().offset(2,2,2))) {
                double candidate = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(point));
                if (mc.level.getBlockState(point).is(Blocks.STONE) && candidate < distance) { next = point.immutable(); distance = candidate; }
            }
            if (next == null) throw new IllegalStateException("No reachable natural stone for replacement");
            if (!mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
            look(mc, Vec3.atCenterOf(next)); mc.options.keyAttack.setDown(true);
        } else if (stage == 2) {
            int surfaceY = Integer.getInteger("interstice.repairSurfaceY", 72);
            if (mc.player.getY() >= surfaceY && mc.player.onGround()) { stop(mc); stage = 3; ticks = 0; return; }
            if (pillarY >= surfaceY) { mc.options.keyJump.setDown(false); return; }
            var point = new BlockPos(mc.player.blockPosition().getX(), pillarY, mc.player.blockPosition().getZ());
            if (!mc.level.getBlockState(point).canBeReplaced()) { pillarY++; ticks = 0; return; }
            if (ticks++ == 0) {
                Item material = count(mc, Items.DIRT) > 0 ? Items.DIRT : count(mc, Items.SAND) > 0 ? Items.SAND : count(mc, Items.COBBLESTONE) > 3 ? Items.COBBLESTONE : count(mc, Items.DARK_OAK_LOG) > 0 ? Items.DARK_OAK_LOG : null;
                if (material == null) throw new IllegalStateException("No legitimately acquired climb material while reserving three cobblestone");
                equip(mc, material);
            }
            if (ticks > 5) mc.options.keyJump.setDown(true);
            if (mc.player.getY() >= pillarY + 1.02 && ticks % 3 == 0) {
                var support = point.below(); look(mc, Vec3.atCenterOf(support).add(0,.5,0));
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false));
            }
        } else if (stage == 3) {
            Vec3 target = Vec3.atBottomCenterOf(TABLE);
            if (mc.player.position().distanceToSqr(target) <= 5) {
                stop(mc); look(mc, Vec3.atCenterOf(TABLE));
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(TABLE).add(0,.5,0),Direction.UP,TABLE,false));
                craft = new SurvivalMenuCraft(Items.STONE_PICKAXE, Items.COBBLESTONE,Items.COBBLESTONE,Items.COBBLESTONE,null,Items.STICK,null,null,Items.STICK,null);
                stage = 4; ticks = 0;
            } else {
                look(mc,target); mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(mc.player.horizontalCollision);
            }
        } else if (stage == 4 && craft.tick(mc)) {
            mc.player.closeContainer(); mc.setScreen(null); stage = 5; ticks = 0;
        } else if (stage == 5 && ++ticks >= 20) {
            if (count(mc,Items.STONE_PICKAXE) < 1) throw new IllegalStateException("Real replacement recipe output missing");
            JsonObject result = new JsonObject(); result.addProperty("passed",true); result.addProperty("replacement_crafted",true);
            result.addProperty("raw_copper",count(mc,Items.RAW_COPPER)); result.addProperty("raw_iron",count(mc,Items.RAW_IRON));
            try { Files.writeString(mc.gameDirectory.toPath().resolve("survival-tool-repair.json"),result.toString()); }
            catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            System.out.println("SURVIVAL_REPAIR " + result); stage = 6; ticks = 0;
        } else if (stage == 6 && ++ticks >= 40) { stop(mc); mc.stop(); }
    }
    private static int count(Minecraft mc, Item item) { return mc.player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(stack -> stack.getCount()).sum(); }
    private static void equip(Minecraft mc, Item item) {
        for (int slot = 9; slot < 45; slot++) if (mc.player.inventoryMenu.getSlot(slot).getItem().is(item)) {
            if (slot != 36) mc.gameMode.handleInventoryMouseClick(0,slot,0,ClickType.SWAP,mc.player);
            mc.player.getInventory().selected=0; return;
        }
        throw new IllegalStateException("Actual inventory lacks " + item);
    }
    private static void look(Minecraft mc, Vec3 target) { Vec3 delta=target.subtract(mc.player.getEyePosition()); mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z))); mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)))); }
    private static void stop(Minecraft mc) { mc.options.keyAttack.setDown(false); mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(false); mc.gameMode.stopDestroyBlock(); }
}
