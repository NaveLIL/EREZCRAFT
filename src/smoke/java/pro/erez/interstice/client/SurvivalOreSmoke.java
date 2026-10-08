package pro.erez.interstice.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;

/** Guided fixed-seed Survival resource run. Planning reads terrain; all mining, movement and loot are ordinary client actions. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class SurvivalOreSmoke {
    private static boolean started;
    private static long began, deadline;
    private static int stage, ticks, surfaceY, pillarY;
    private static Item wanted;
    private static volatile BlockPos ore, surface;
    private static volatile Throwable failure;
    private static final JsonArray actions = new JsonArray();
    private static Vec3 shaft;
    private static boolean recovered;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("interstice.survivalOres")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true; began = System.nanoTime(); deadline = began + TimeUnit.MINUTES.toNanos(15);
            mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(5);
            mc.createWorldOpenFlows().openWorld("survival-expedition-route", () -> {}); return;
        }
        if (!started) return;
        if (SmokeWorldPrompts.advance(mc)) return;
        if (failure != null) throw new IllegalStateException("Survival resource route failed", failure);
        if (System.nanoTime() > deadline) throw new IllegalStateException("Resource route timeout stage=" + stage);
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        if (!mc.player.isAlive() || mc.gameMode.getPlayerMode() != GameType.SURVIVAL || mc.player.getAbilities().mayfly || mc.player.getAbilities().instabuild)
            throw new IllegalStateException("Survival invariants violated");
        if (stage == 0 && ++ticks >= 40) {
            if (!recovered && Integer.getInteger("interstice.oreResumeSurfaceY", 0) > 0) {
                recovered = true; surfaceY = Integer.getInteger("interstice.oreResumeSurfaceY", 0);
                pillarY = mc.player.blockPosition().getY(); stage = 4; ticks = 0; action(mc, "resumed_saved_shaft_climb"); return;
            }
            wanted = count(mc, Items.RAW_COPPER) < 9 ? Items.RAW_COPPER : count(mc, Items.RAW_IRON) < 10 ? Items.RAW_IRON : count(mc, Items.COAL) < 3 ? Items.COAL : null;
            if (wanted == null) { report(mc); stage = 9; ticks = 0; return; }
            if (count(mc, Items.STONE_PICKAXE) == 0) throw new IllegalStateException("Craft another stone pickaxe from collected materials before continuing");
            stage = 1; ticks = 0; ore = null; surface = null;
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> { try {
                var player = server.getPlayerList().getPlayer(uuid); var level = player.serverLevel(); var start = player.blockPosition();
                if (server.getWorldData().isAllowCommands()) throw new IllegalStateException("Cheats enabled");
                double nearest = Double.MAX_VALUE;
                for (var point : BlockPos.betweenClosed(start.offset(-24, -35, -24), start.offset(24, -2, 24))) {
                    if (!matches(level.getBlockState(point), wanted)) continue;
                    var top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, point);
                    if (!level.getFluidState(top.below()).isEmpty() || top.getY() < point.getY() + 3 || top.getY() > start.getY() + 8
                            || level.getBlockState(top.below()).is(net.minecraft.tags.BlockTags.LOGS) || level.getBlockState(top.below()).is(net.minecraft.tags.BlockTags.LEAVES)) continue;
                    boolean dry = true; int gap = 0;
                    for (int y = point.getY() - 2; y < top.getY(); y++) {
                        var column = new BlockPos(point.getX(), y, point.getZ());
                        if (!level.getFluidState(column).isEmpty()) { dry = false; break; }
                        gap = level.getBlockState(column).isAir() ? gap + 1 : 0;
                        if (gap > 2) { dry = false; break; }
                    }
                    if (!dry) continue;
                    double score = start.distSqr(point);
                    if (score < nearest) { nearest = score; ore = point.immutable(); surface = top.immutable(); }
                }
                if (ore == null) throw new IllegalStateException("No dry guided ore shaft within 24 blocks: " + wanted);
                System.out.println("SURVIVAL_ORE_PLAN resource=" + wanted + " natural_ore=" + ore + " surface=" + surface);
            } catch (Throwable error) { failure = error; } });
        } else if (stage == 1 && surface != null) {
            Vec3 target = new Vec3(surface.getX() + .5, surface.getY(), surface.getZ() + .5);
            if (walk(mc, target, .06)) {
                stop(mc); shaft = target; surfaceY = surface.getY();
                if (++ticks >= 10) { equip(mc, Items.STONE_PICKAXE); stage = 2; ticks = 0; action(mc, "walked_to_natural_ore_shaft"); }
            }
        } else if (stage == 2) {
            // A centred one-block shaft makes the natural ore accessible without teleporting to underground coordinates.
            var below = mc.player.blockPosition().below();
            if (below.getY() <= ore.getY()) { stop(mc); stage = 3; ticks = 0; return; }
            if (!mc.level.getFluidState(below).isEmpty()) throw new IllegalStateException("Unsafe fluid along guided shaft");
            if (!mc.level.getBlockState(below).isAir()) mine(mc, below);
            if (++ticks % 200 == 0) System.out.println("SURVIVAL_SHAFT feet=" + mc.player.position() + " target=" + below);
        } else if (stage == 3) {
            int needed = wanted == Items.RAW_COPPER ? 9 : wanted == Items.RAW_IRON ? 10 : 3;
            if (count(mc, wanted) >= needed || ++ticks > 1200) { stop(mc); stage = 5; ticks = 0; action(mc, "ore_collection_checkpoint"); return; }
            BlockPos next = null; double nearest = Double.MAX_VALUE;
            for (var point : BlockPos.betweenClosed(mc.player.blockPosition().offset(-3, -2, -3), mc.player.blockPosition().offset(3, 2, 3))) {
                if (!matches(mc.level.getBlockState(point), wanted)) continue;
                double distance = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(point));
                if (distance <= 20.25 && distance < nearest) { nearest = distance; next = point.immutable(); }
            }
            if (next == null) { stop(mc); stage = 5; ticks = 0; action(mc, "vein_exhausted"); }
            else mine(mc, next);
        } else if (stage == 5 && ++ticks >= 40) {
            // Vanilla drops cannot be picked up for ten ticks. Sealing the shaft immediately buries fresh ore drops.
            stop(mc); pillarY = mc.player.blockPosition().getY(); stage = 4; ticks = 0; action(mc, "collected_after_pickup_delay");
        } else if (stage == 4) {
            if (mc.player.getY() >= surfaceY && mc.player.onGround()) { stop(mc); stage = 0; ticks = 0; action(mc, "climbed_back_to_surface"); return; }
            if (pillarY >= surfaceY) { mc.options.keyJump.setDown(false); return; }
            BlockPos target = new BlockPos(mc.player.blockPosition().getX(), pillarY, mc.player.blockPosition().getZ());
            if (!mc.level.getBlockState(target).canBeReplaced()) { pillarY++; ticks = 0; return; }
            if (ticks++ == 0) equipBuildingBlock(mc);
            if (ticks > 5) mc.options.keyJump.setDown(true);
            if (mc.player.getY() >= pillarY + 1.02 && ticks % 3 == 0) {
                BlockPos support = target.below(); look(mc, Vec3.atCenterOf(support).add(0,.5,0));
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0), Direction.UP, support, false));
            }
        } else if (stage == 9 && ++ticks >= 40) { stop(mc); mc.stop(); }
    }
    private static boolean matches(BlockState state, Item item) {
        return item == Items.RAW_COPPER ? state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE) : item == Items.RAW_IRON ? state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE) : state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE);
    }
    private static void mine(Minecraft mc, BlockPos point) {
        if (mc.player.getMainHandItem().isEmpty()) throw new IllegalStateException("Pickaxe broke during actual mining");
        if (!mc.mouseHandler.isMouseGrabbed()) { mc.mouseHandler.grabMouse(); mc.options.keyAttack.setDown(false); return; }
        look(mc, Vec3.atCenterOf(point)); mc.options.keyAttack.setDown(true);
    }
    private static int count(Minecraft mc, Item item) { return mc.player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static void equip(Minecraft mc, Item item) {
        for (int slot = 9; slot < 45; slot++) if (mc.player.inventoryMenu.getSlot(slot).getItem().is(item)) {
            if (slot != 36) mc.gameMode.handleInventoryMouseClick(0, slot, 0, ClickType.SWAP, mc.player);
            mc.player.getInventory().selected = 0; return;
        }
        throw new IllegalStateException("Legitimate inventory lacks " + item);
    }
    private static void equipBuildingBlock(Minecraft mc) {
        for (Item item : new Item[] { Items.DIRT, Items.COBBLESTONE, Items.SANDSTONE, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.SAND, Items.GRAVEL })
            if (count(mc, item) > 0) { equip(mc, item); return; }
        for (ItemStack stack : mc.player.getInventory().items) if (stack.is(ItemTags.PLANKS)) { equip(mc, stack.getItem()); return; }
        throw new IllegalStateException("No collected blocks for climbing");
    }
    private static boolean walk(Minecraft mc, Vec3 target, double range) {
        if (mc.player.position().distanceToSqr(target) <= range * range) return true;
        look(mc, target); mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(mc.player.horizontalCollision); return false;
    }
    private static void look(Minecraft mc, Vec3 target) {
        Vec3 delta = target.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
    }
    private static void stop(Minecraft mc) { mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(false); mc.options.keyAttack.setDown(false); mc.gameMode.stopDestroyBlock(); }
    private static void action(Minecraft mc, String name) {
        JsonObject entry = new JsonObject(); entry.addProperty("action", name); entry.addProperty("seconds", (System.nanoTime() - began) / 1e9); entry.addProperty("feet", mc.player.blockPosition().toShortString());
        entry.addProperty("raw_copper", count(mc, Items.RAW_COPPER)); entry.addProperty("raw_iron", count(mc, Items.RAW_IRON)); entry.addProperty("coal", count(mc, Items.COAL)); actions.add(entry); System.out.println("SURVIVAL_RESOURCES " + entry);
        JsonObject checkpoint = new JsonObject(); checkpoint.addProperty("stage", stage); checkpoint.addProperty("surface_y", surfaceY); checkpoint.add("actions", actions);
        JsonArray inventory = new JsonArray();
        for (ItemStack stack : mc.player.getInventory().items) if (!stack.isEmpty()) inventory.add(stack.getItem() + " x" + stack.getCount() + " damage=" + stack.getDamageValue());
        checkpoint.add("inventory", inventory);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("survival-ore-checkpoint.json"), checkpoint.toString()); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    private static void report(Minecraft mc) {
        JsonObject report = new JsonObject(); report.addProperty("passed", true); report.addProperty("scope", "guided Survival ore preparation; M12 expedition still pending");
        report.addProperty("seconds", (System.nanoTime() - began) / 1e9); report.add("actions", actions);
        JsonArray inventory = new JsonArray(); for (ItemStack stack : mc.player.getInventory().items) if (!stack.isEmpty()) inventory.add(stack.getItem() + " x" + stack.getCount()); report.add("inventory", inventory);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("survival-ores.json"), report.toString()); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
