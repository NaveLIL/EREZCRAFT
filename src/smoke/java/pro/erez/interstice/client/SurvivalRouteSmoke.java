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
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;

/** M12 preparation in a fresh, ordinary Survival save. No inventory grants, teleports or world edits. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class SurvivalRouteSmoke {
    private static final String MODE = System.getProperty("interstice.survivalRoute", "");
    private static final String WORLD = "survival-expedition-route";
    private static final JsonArray ACTIONS = new JsonArray();
    private static boolean started;
    private static long began, deadline;
    private static int stage, ticks, craftStep;
    private static volatile BlockPos chest;
    private static volatile Throwable failure;
    private static int sourceSlot;
    private static BlockPos table, mining;
    private static int surfaceY, pillarY;
    private static Vec3 digCenter;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (MODE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true; began = System.nanoTime(); deadline = began + TimeUnit.MINUTES.toNanos(15);
            mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(5);
            if (MODE.equals("bootstrap")) {
                if (Files.exists(mc.gameDirectory.toPath().resolve("saves/" + WORLD + "/level.dat"))) throw new IllegalStateException("Fresh Survival profile required");
                mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings("Survival expedition route", GameType.SURVIVAL,
                        false, Difficulty.NORMAL, false, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261006L, true, true), WorldPresets::createNormalWorldDimensions, mc.screen);
            } else mc.createWorldOpenFlows().openWorld(WORLD, () -> {});
            return;
        }
        if (!started) return;
        if (SmokeWorldPrompts.advance(mc)) return;
        if (failure != null) throw new IllegalStateException("Survival route preparation failed", failure);
        if (System.nanoTime() > deadline) throw new IllegalStateException("Survival preparation deadline, stage=" + stage);
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (mc.gameMode.getPlayerMode() != GameType.SURVIVAL || mc.player.getAbilities().instabuild || mc.player.getAbilities().mayfly)
            throw new IllegalStateException("Survival mode was violated");
        if (!mc.player.isAlive()) throw new IllegalStateException("Preparation ended in death; not a successful route");
        if (stage == 0) {
            if (MODE.equals("equipment")) {
                stage = 10; ticks = 0;
                try {
                    var data = com.google.gson.JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("survival-preparation.json"))).getAsJsonObject();
                    for (var action : data.getAsJsonArray("actions")) ACTIONS.add(action);
                } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                action("resumed_survival", "cold JVM restart of the naturally prepared save");
                return;
            }
            stage = 1; ticks = 0;
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> { try {
                var player = server.getPlayerList().getPlayer(uuid); var level = player.serverLevel(); BlockPos start = player.blockPosition();
                if (server.getWorldData().isAllowCommands()) throw new IllegalStateException("Cheats enabled");
                double nearest = Double.MAX_VALUE;
                BlockPos spawn = level.getSharedSpawnPos();
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                    for (BlockPos pos : level.getChunk((spawn.getX() >> 4) + dx, (spawn.getZ() >> 4) + dz).getBlockEntitiesPos())
                        if (level.getBlockState(pos).is(Blocks.CHEST) && start.distSqr(pos) < nearest) { nearest = start.distSqr(pos); chest = pos.immutable(); }
                if (chest == null) throw new IllegalStateException("Natural bonus chest not found near spawn");
                System.out.println("SURVIVAL_NATURAL_CHEST spawn=" + spawn.toShortString() + " player=" + start.toShortString() + " chest=" + chest.toShortString());
            } catch (Throwable error) { failure = error; } });
            action("fresh_survival", "Normal difficulty; structures and vanilla bonus chest enabled; cheats disabled");
            return;
        }
        if (stage == 1 && chest != null) {
            if (walk(mc, Vec3.atCenterOf(chest), 2.3)) {
                stopMoving(mc); look(mc, Vec3.atCenterOf(chest));
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(chest).add(0,.5,0), Direction.UP, chest, false));
                stage = 2; ticks = 0;
            }
        } else if (stage == 2 && ++ticks >= 10 && mc.player.containerMenu != mc.player.inventoryMenu) {
            var menu = mc.player.containerMenu;
            for (int slot = 0; slot < menu.slots.size() - 36; slot++) if (!menu.getSlot(slot).getItem().isEmpty())
                mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
            action("looted_natural_bonus_chest", chest.toShortString());
            stage = 3; ticks = 0;
        } else if (stage == 3 && ++ticks >= 20) {
            mc.player.closeContainer(); mc.setScreen(null); stage = 4; ticks = 0; craftStep = 0;
        } else if (stage == 4 && ++ticks % 5 == 0) {
            // One log through the real 2x2 crafting menu, with server-authoritative slot synchronization.
            var menu = mc.player.inventoryMenu;
            if (craftStep == 0 && mc.player.getInventory().items.stream().noneMatch(stack -> stack.is(ItemTags.LOGS))) {
                if (mc.player.getInventory().items.stream().filter(stack -> stack.is(ItemTags.PLANKS)).mapToInt(ItemStack::getCount).sum() < 4)
                    throw new IllegalStateException("This natural bonus chest needs additional wood gathering before workbench crafting");
                action("used_naturally_looted_planks", "no log in bonus chest; four or more existing planks available");
                stage = 5; craftStep = 0; ticks = 0; return;
            }
            if (craftStep == 0) { sourceSlot = find(mc, true); click(mc, sourceSlot, 0); }
            if (craftStep == 1) click(mc, 1, 1);
            if (craftStep == 2) click(mc, sourceSlot, 0);
            if (craftStep == 3) {
                if (!menu.getSlot(0).getItem().is(ItemTags.PLANKS)) throw new IllegalStateException("Real plank recipe has no output");
                click(mc, 0, 0, ClickType.QUICK_MOVE); action("crafted_planks", "one naturally looted log -> four planks");
                stage = 5; craftStep = 0; ticks = 0; return;
            }
            craftStep++;
        } else if (stage == 5 && ++ticks % 5 == 0) {
            if (craftStep == 0) { sourceSlot = find(mc, false); click(mc, sourceSlot, 0); }
            if (craftStep >= 1 && craftStep <= 4) click(mc, craftStep, 1);
            if (craftStep == 5 && !mc.player.inventoryMenu.getCarried().isEmpty()) click(mc, sourceSlot, 0);
            if (craftStep == 6) {
                if (!mc.player.inventoryMenu.getSlot(0).getItem().is(Items.CRAFTING_TABLE)) throw new IllegalStateException("Real crafting-table recipe has no output");
                click(mc, 0, 0, ClickType.QUICK_MOVE); action("crafted_workbench", "four naturally obtained planks"); stage = 6; ticks = 0;
            }
            craftStep++;
        } else if (stage == 6 && ++ticks >= 40) {
            JsonObject report = new JsonObject(); report.addProperty("passed", true); report.addProperty("scope", "M12 preparation only; full expedition remains pending");
            report.addProperty("cheats", false); report.addProperty("bonus_chest", true); report.addProperty("seconds", (System.nanoTime() - began) / 1e9);
            JsonArray inventory = new JsonArray();
            for (ItemStack stack : mc.player.getInventory().items) if (!stack.isEmpty()) inventory.add(stack.getItem().toString() + " x" + stack.getCount());
            report.add("inventory", inventory); report.add("actions", ACTIONS);
            try { Files.writeString(mc.gameDirectory.toPath().resolve("survival-preparation.json"), report.toString()); }
            catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            stopMoving(mc); mc.stop(); stage = 7;
        }
        if (stage == 10 && ++ticks >= 40) {
            surfaceY = mc.player.blockPosition().getY();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos pos = mc.player.blockPosition().relative(direction);
                if (mc.level.getBlockState(pos).canBeReplaced() && mc.level.getBlockState(pos.below()).isSolidRender(mc.level, pos.below())) { table = pos; break; }
            }
            if (table == null) throw new IllegalStateException("No adjacent supported spot for workbench");
            equip(mc, Items.CRAFTING_TABLE); stage = 11; ticks = 0;
        } else if (stage == 11) {
            if (++ticks == 10) place(mc, table);
            if (ticks >= 20) {
                if (!mc.level.getBlockState(table).is(Blocks.CRAFTING_TABLE)) throw new IllegalStateException("Actual workbench placement failed; feet=" + mc.player.position() + " target=" + table + " state=" + mc.level.getBlockState(table) + " held=" + mc.player.getMainHandItem());
                action("placed_workbench", table.toShortString());
                equipPick(mc);
                digCenter = new Vec3(mc.player.blockPosition().getX() + .5, mc.player.getY(), mc.player.blockPosition().getZ() + .5);
                stage = 19; ticks = 0;
            }
        } else if (stage == 19) {
            // The player's collision box otherwise overlaps a neighbouring block and cannot descend into the one-block shaft.
            if (walk(mc, digCenter, .06)) {
                stopMoving(mc);
                if (++ticks >= 10) { stage = 12; ticks = 0; }
            }
        } else if (stage == 12 && ++ticks >= 10) {
            if (mc.screen == null && !mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
            if (ticks % 200 == 0) {
                System.out.println("SURVIVAL_MINING_STATE feet=" + mc.player.position() + " below=" + mc.level.getBlockState(mc.player.blockPosition().below()) + " screen=" + mc.screen + " grabbed=" + mc.mouseHandler.isMouseGrabbed() + " paused=" + mc.isPaused());
                if (ticks == 200) net.minecraft.client.Screenshot.grab(mc.gameDirectory, "mining-diagnostics.png", mc.getMainRenderTarget(), message -> {});
            }
            if (count(mc, Items.COBBLESTONE) >= 3) {
                mc.options.keyAttack.setDown(false); mc.gameMode.stopDestroyBlock(); pillarY = mc.player.blockPosition().getY();
                action("mined_cobblestone", "three natural stone blocks, actual pickaxe/drops; feet=" + mc.player.blockPosition().toShortString());
                stage = 13; ticks = 0; mining = null;
            } else {
                BlockPos target = mc.player.blockPosition().below();
                if (!mc.level.getFluidState(target).isEmpty()) throw new IllegalStateException("Digging stopped at unsafe fluid");
                if (mc.level.getBlockState(target).isAir()) return;
                look(mc, Vec3.atCenterOf(target));
                // Hold the actual attack input. Calling continueDestroyBlock alone is reset by Minecraft's normal key handler.
                mc.options.keyAttack.setDown(true);
                if (ticks % 200 == 0) System.out.println("SURVIVAL_MINING feet=" + mc.player.position() + " target=" + mc.level.getBlockState(target) + " tool=" + mc.player.getMainHandItem());
            }
        } else if (stage == 13) {
            if (mc.player.getY() >= surfaceY && mc.player.onGround()) {
                mc.options.keyJump.setDown(false); stage = 14; ticks = 0;
                action("climbed_with_collected_blocks", "returned to surface by ordinary jumping and placement");
                return;
            }
            if (pillarY >= surfaceY) { mc.options.keyJump.setDown(false); return; }
            BlockPos target = new BlockPos(mc.player.blockPosition().getX(), pillarY, mc.player.blockPosition().getZ());
            if (!mc.level.getBlockState(target).canBeReplaced()) { pillarY++; ticks = 0; return; }
            if (ticks++ == 0) equipPillar(mc);
            if (ticks > 5) mc.options.keyJump.setDown(true);
            if (mc.player.getY() >= pillarY + 1.02 && ticks % 3 == 0) place(mc, target);
        } else if (stage == 14 && ++ticks >= 10) {
            look(mc, Vec3.atCenterOf(table));
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(table).add(0,.5,0), Direction.UP, table, false));
            stage = 15; ticks = 0; craftStep = 0;
        } else if (stage == 15 && mc.player.containerMenu != mc.player.inventoryMenu && ++ticks % 5 == 0) {
            var menu = mc.player.containerMenu;
            if (craftStep == 0) { sourceSlot = findInMenu(mc, Items.COBBLESTONE); tableClick(mc, sourceSlot, 0, ClickType.PICKUP); }
            if (craftStep >= 1 && craftStep <= 3) tableClick(mc, craftStep, 1, ClickType.PICKUP);
            if (craftStep == 4 && !menu.getCarried().isEmpty()) tableClick(mc, sourceSlot, 0, ClickType.PICKUP);
            if (craftStep == 5) { sourceSlot = findInMenu(mc, Items.STICK); tableClick(mc, sourceSlot, 0, ClickType.PICKUP); }
            if (craftStep == 6) tableClick(mc, 5, 1, ClickType.PICKUP);
            if (craftStep == 7) tableClick(mc, 8, 1, ClickType.PICKUP);
            if (craftStep == 8 && !menu.getCarried().isEmpty()) tableClick(mc, sourceSlot, 0, ClickType.PICKUP);
            if (craftStep == 9) {
                if (!menu.getSlot(0).getItem().is(Items.STONE_PICKAXE)) throw new IllegalStateException("Actual stone pickaxe recipe did not match");
                tableClick(mc, 0, 0, ClickType.QUICK_MOVE); action("crafted_stone_pickaxe", "three mined cobblestone + two naturally obtained sticks");
                stage = 16; ticks = 0;
            }
            craftStep++;
        } else if (stage == 16 && ++ticks >= 20) {
            mc.player.closeContainer(); mc.setScreen(null);
            if (count(mc, Items.STONE_PICKAXE) < 1) throw new IllegalStateException("Crafted pickaxe absent from inventory");
            JsonObject report = new JsonObject(); report.addProperty("passed", true); report.addProperty("scope", "M12 resource preparation only; expedition pending");
            report.addProperty("seconds", (System.nanoTime() - began) / 1e9); report.add("actions", ACTIONS);
            report.addProperty("stone_pickaxes", count(mc, Items.STONE_PICKAXE));
            try { Files.writeString(mc.gameDirectory.toPath().resolve("survival-equipment.json"), report.toString()); }
            catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            stage = 17; ticks = 0;
        } else if (stage == 17 && ++ticks >= 40) { stopMoving(mc); mc.stop(); }
    }
    private static void click(Minecraft mc, int slot, int button) { click(mc, slot, button, ClickType.PICKUP); }
    private static void click(Minecraft mc, int slot, int button, ClickType type) { mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot, button, type, mc.player); }
    private static int find(Minecraft mc, boolean log) {
        for (int slot = 9; slot < 45; slot++) if (mc.player.inventoryMenu.getSlot(slot).getItem().is(log ? ItemTags.LOGS : ItemTags.PLANKS)) return slot;
        throw new IllegalStateException("Naturally obtained " + (log ? "logs" : "planks") + " missing");
    }
    private static boolean walk(Minecraft mc, Vec3 target, double range) {
        if (mc.player.position().distanceToSqr(target) <= range * range) return true;
        look(mc, target); mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(mc.player.horizontalCollision);
        return false;
    }
    private static void look(Minecraft mc, Vec3 target) {
        Vec3 delta = target.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
    }
    private static void stopMoving(Minecraft mc) { mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(false); mc.options.keyAttack.setDown(false); }
    private static int count(Minecraft mc, net.minecraft.world.item.Item item) {
        return mc.player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static void equip(Minecraft mc, net.minecraft.world.item.Item item) {
        for (int slot = 9; slot < 45; slot++) if (mc.player.inventoryMenu.getSlot(slot).getItem().is(item)) {
            if (slot != 36) mc.gameMode.handleInventoryMouseClick(0, slot, 0, ClickType.SWAP, mc.player);
            mc.player.getInventory().selected = 0; return;
        }
        throw new IllegalStateException("Naturally obtained equipment missing: " + item);
    }
    private static void equipPick(Minecraft mc) { equip(mc, count(mc, Items.STONE_PICKAXE) > 0 ? Items.STONE_PICKAXE : Items.WOODEN_PICKAXE); }
    private static void equipPillar(Minecraft mc) {
        if (count(mc, Items.DIRT) > 0) { equip(mc, Items.DIRT); return; }
        for (var stack : mc.player.getInventory().items) if (stack.is(ItemTags.PLANKS)) { equip(mc, stack.getItem()); return; }
        throw new IllegalStateException("No legally acquired blocks for climbing");
    }
    private static void place(Minecraft mc, BlockPos target) {
        BlockPos support = target.below(); look(mc, Vec3.atCenterOf(support).add(0,.5,0));
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0), Direction.UP, support, false));
        System.out.println("SURVIVAL_PLACEMENT target=" + target.toShortString() + " result=" + result + " held=" + mc.player.getMainHandItem() + " support=" + mc.level.getBlockState(support));
    }
    private static int findInMenu(Minecraft mc, net.minecraft.world.item.Item item) {
        var menu = mc.player.containerMenu;
        for (int slot = 10; slot < menu.slots.size(); slot++) if (menu.getSlot(slot).getItem().is(item)) return slot;
        throw new IllegalStateException("Actual crafting ingredients missing: " + item);
    }
    private static void tableClick(Minecraft mc, int slot, int button, ClickType type) { mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, button, type, mc.player); }
    private static void action(String name, String detail) {
        JsonObject action = new JsonObject(); action.addProperty("action", name); action.addProperty("detail", detail); action.addProperty("seconds", (System.nanoTime() - began) / 1e9); ACTIONS.add(action);
        System.out.println("SURVIVAL_ROUTE " + action);
    }
}
