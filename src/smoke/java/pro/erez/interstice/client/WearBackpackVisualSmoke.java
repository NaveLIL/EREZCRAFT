package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.equipment.*;
import pro.erez.interstice.worldgen.GardenMaterials;

/** Separate disposable prepared fixture: native wearing, registered B, inventory equipment button and cold attachment save. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class WearBackpackVisualSmoke {
    private static final String MODE = System.getProperty("interstice.wearBackpackSmoke", "");
    private static final String WORLD = "native-worn-backpack-check";
    private static boolean started, finished, closing, passed;
    private static String reason;
    private static int stage, ticks;
    private static long deadline;
    private static BlockPos base;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static JsonObject data = new JsonObject();
    private WearBackpackVisualSmoke() {}

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        var session = settling; if (!MODE.isEmpty() && session != null) session.tick(event.getServer());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (MODE.isEmpty() || finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (closing) { shutdown(mc); return; }
            if (!started && mc.screen instanceof TitleScreen) {
                require(MODE.equals("create") || MODE.equals("reload"), "Unknown wearable smoke mode");
                started = true; deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(8);
                mc.options.pauseOnLostFocus = false; mc.options.hideGui = false;
                mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5); mc.options.framerateLimit().set(60);
                if (MODE.equals("create")) {
                    require(!Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat")), "Disposable wearable save already exists");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,
                            new LevelSettings("Native worn backpack checks", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                    new GameRules(), WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20261006L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
                } else {
                    data = JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("wear-backpack-create-validation.json"))).getAsJsonObject();
                    require(data.get("passed").getAsBoolean() && data.get("creator_pid").getAsLong() != ProcessHandle.current().pid(), "No successful different-JVM creation");
                    base = new BlockPos(data.get("base_x").getAsInt(), data.get("base_y").getAsInt(), data.get("base_z").getAsInt());
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> {});
                }
                return;
            }
            if (!started || SmokeWorldPrompts.advance(mc)) return;
            require(System.nanoTime() < deadline, "Wearable backpack deadline at stage " + stage);
            if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
            var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
            if (stage == 0) {
                work = server.submit(() -> { var p = server.getPlayerList().getPlayer(id); if (MODE.equals("create")) prepare(p); else verifyCold(p); return true; });
                settling = new NativeChunkSettler.Session("wear_backpack_initial_generation", 2000); stage = 1; ticks = 0;
            } else if (stage == 1 && done() && settling.ready() && ++ticks >= 30) {
                if (MODE.equals("create") && !mc.player.getInventory().getItem(0).is(ExpeditionEquipment.FIELD_BACKPACK.get())) { ticks = 29; return; }
                if (MODE.equals("reload") && !clientWorn(mc)) { ticks = 29; return; }
                data.add("settle_before_wearing", settling.report()); settling = null; validateLayers(mc);
                if (MODE.equals("create")) {
                    require(mc.screen == null, "Wear must begin in the ordinary world");
                    mc.player.getInventory().selected = 0; mc.getConnection().send(new ServerboundSetCarriedItemPacket(0));
                    mc.player.setShiftKeyDown(true);
                    mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); stage = 2; ticks = 0;
                } else {
                    registeredB(mc); stage = 40; ticks = 0;
                }
            } else if (stage == 2 && ++ticks >= 20) {
                mc.player.setShiftKeyDown(false);
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                work = server.submit(() -> { var p = server.getPlayerList().getPlayer(id); verifyWorn(p);
                    require(p.getInventory().getItem(0).isEmpty(), "Shift RMB copied a held backpack instead of wearing it");
                    data.addProperty("actual_client_shift_rmb_equips_without_armor_slot", true); return true; });
                stage = 3; ticks = 0;
            } else if (stage == 3 && done() && clientWorn(mc) && ++ticks >= 25) {
                registeredB(mc); stage = 4; ticks = 0;
            } else if (stage == 4 && backpackMenu(mc) && ++ticks >= 25) {
                work = server.submit(() -> { var p = server.getPlayerList().getPlayer(id); verifyWorn(p);
                    require(p.containerMenu instanceof BackpackMenu menu && menu.sourceSlot == BackpackHarness.SLOT, "Registered B opened an inventory pack instead of the worn pack");
                    data.addProperty("registered_B_opens_worn_slot_41", true); return true; });
                stage = 5; ticks = 0;
            } else if (stage == 5 && done() && backpackMenu(mc) && ++ticks >= 20) {
                shot(mc, "wear-backpack-54-menu.png"); mc.player.closeContainer(); stage = 6; ticks = 0;
            } else if (stage == 6 && mc.screen == null && ++ticks >= 20) {
                mc.setScreen(new InventoryScreen(mc.player)); stage = 7; ticks = 0;
            } else if (stage == 7 && mc.screen instanceof InventoryScreen && ++ticks >= 20) {
                String label = Component.translatable("menu.interstice.harness.button").getString();
                var button = mc.screen.children().stream().filter(widget -> widget instanceof Button b && b.getMessage().getString().equals(label))
                        .map(widget -> (Button) widget).findFirst().orElseThrow(() -> new IllegalStateException("Ordinary inventory has no equipment button"));
                require(mc.screen.mouseClicked(button.getX() + button.getWidth() / 2D, button.getY() + button.getHeight() / 2D, 0),
                        "Native equipment button did not handle a mouse click");
                data.addProperty("actual_inventory_equipment_button_clicked", true); stage = 8; ticks = 0;
            } else if (stage == 8 && mc.screen instanceof HarnessScreen && mc.player.containerMenu instanceof HarnessMenu && ++ticks >= 25) {
                require(mc.player.containerMenu.getSlot(0).getItem().is(ExpeditionEquipment.FIELD_BACKPACK.get()), "Equipment screen does not show the actual worn item");
                shot(mc, "wear-backpack-equipment.png");
                click(mc, 0, ClickType.QUICK_MOVE); stage = 9; ticks = 0;
            } else if (stage == 9 && ++ticks >= 25) {
                work = server.submit(() -> { var p = server.getPlayerList().getPlayer(id);
                    require(BackpackHarness.get(p).isEmpty() && inventoryPackCount(p) == 1, "Equipment shift-take duplicated or failed to remove the worn pack");
                    require(ItemStack.matches(p.getItemBySlot(EquipmentSlot.CHEST), chest()), "Taking off a pack changes chest armor");
                    data.addProperty("actual_equipment_slot_takeoff_once", true); return true; });
                stage = 10; ticks = 0;
            } else if (stage == 10 && done() && mc.player.containerMenu instanceof HarnessMenu && ++ticks >= 20) {
                int slot = mc.player.containerMenu.slots.stream().filter(s -> s.container == mc.player.getInventory() && s.getItem().is(ExpeditionEquipment.FIELD_BACKPACK.get()))
                        .mapToInt(s -> s.index).findFirst().orElseThrow(() -> new IllegalStateException("Removed bag is absent from real player slots"));
                click(mc, slot, ClickType.PICKUP); stage = 11; ticks = 0;
            } else if (stage == 11 && mc.player.containerMenu instanceof HarnessMenu && BackpackStorage.isPack(mc.player.containerMenu.getCarried()) && ++ticks >= 15) {
                click(mc, 0, ClickType.PICKUP); stage = 12; ticks = 0;
            } else if (stage == 12 && mc.player.containerMenu instanceof HarnessMenu && mc.player.containerMenu.getCarried().isEmpty() && clientWorn(mc) && ++ticks >= 20) {
                work = server.submit(() -> { var p = server.getPlayerList().getPlayer(id); verifyWorn(p);
                    require(inventoryPackCount(p) == 0 && p.containerMenu.getCarried().isEmpty(), "Equipment slot insertion leaves a second bag or copied cursor");
                    data.addProperty("actual_equipment_slot_rewear_once", true); return true; });
                stage = 13; ticks = 0;
            } else if (stage == 13 && done()) {
                mc.player.closeContainer(); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); stage = 14; ticks = 0;
            } else if (stage == 14 && mc.screen == null && clientWorn(mc) && ++ticks >= 100) {
                require(mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK, "Third-person back camera was not active");
                shot(mc, "wear-backpack-third-person-back.png");
                data.addProperty("native_third_person_back_view_with_chest_armor", true);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); stage = 15; ticks = 0;
            } else if (stage == 15 && mc.screen == null && clientWorn(mc) && ++ticks >= 70) {
                shot(mc, "wear-backpack-third-person-front.png");
                work = server.submit(() -> { var p = server.getPlayerList().getPlayer(id); verifyWorn(p);
                    data.addProperty("worn_uuid", BackpackStorage.id(BackpackHarness.get(p)).toString());
                    data.addProperty("armor_unchanged_through_actual_wear_and_equipment_menu", true); return true; });
                stage = 16;
            } else if (stage == 16 && done()) {
                finish(mc, true, "Native Shift RMB wears one loaded pack without using armor; registered B and ordinary inventory equipment button/slot clicks passed; native third-person views captured");
            } else if (stage == 40 && backpackMenu(mc) && ++ticks >= 30) {
                shot(mc, "wear-backpack-54-menu-cold.png"); mc.player.closeContainer();
                data.addProperty("cold_registered_B_opens_worn_pack", true); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); stage = 41; ticks = 0;
            } else if (stage == 41 && mc.screen == null && clientWorn(mc) && ++ticks >= 100) {
                shot(mc, "wear-backpack-third-person-back-cold.png");
                work = server.submit(() -> { verifyCold(server.getPlayerList().getPlayer(id)); return true; }); stage = 42;
            } else if (stage == 42 && done()) {
                finish(mc, true, "Different-JVM attachment reload preserves worn identity, exact stored components/counts and chest armor; B reopened the actual worn storage and native third-person scene");
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); finish(mc, false, failure.toString());
        }
    }

    private static void prepare(ServerPlayer p) {
        var level = p.serverLevel(); base = p.blockPosition().below();
        for (int x = -3; x <= 12; x++) for (int z = -4; z <= 13; z++) {
            level.setBlock(base.offset(x, 0, z), GardenMaterials.PALEHEART_PLANKS.get().defaultBlockState(), 3);
            for (int y = 1; y <= 7; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(base.offset(6, 1, 8), RealmAgriculture.RETORT.get().defaultBlockState(), 3);
        level.setDayTime(6000); p.teleportTo(level, base.getX() + 3.5, base.getY() + 1, base.getZ() + 6.5, Set.of(), 0, 15);
        p.getInventory().clearContent(); p.setGameMode(GameType.SURVIVAL); p.getFoodData().setFoodLevel(20);
        p.setItemSlot(EquipmentSlot.CHEST, chest()); require(BackpackHarness.get(p).isEmpty(), "Fresh fixture already wears a backpack");
        var bag = new ItemStack(ExpeditionEquipment.FIELD_BACKPACK.get()); bag.set(DataComponents.CUSTOM_NAME, Component.literal("Native worn field pack"));
        var contents = BackpackStorage.read(bag); contents.set(0, grain(40)); contents.set(1, new ItemStack(RealmAgriculture.GRAIN.get(), 24));
        contents.set(53, tool()); BackpackStorage.write(bag, contents); BackpackStorage.ensureId(bag);
        p.getInventory().setItem(0, bag); p.getInventory().selected = 0; p.getInventory().setChanged(); p.inventoryMenu.broadcastChanges();
        data.addProperty("creator_pid", ProcessHandle.current().pid()); data.addProperty("initial_uuid", BackpackStorage.id(bag).toString());
        data.addProperty("base_x", base.getX()); data.addProperty("base_y", base.getY()); data.addProperty("base_z", base.getZ());
        data.addProperty("scope", "Disposable prepared platform/items, then Survival interaction rules: actual client Shift/RMB, registered B, ordinary inventory button and equipment slot packets. No unaided Survival resource/recipe route is claimed.");
    }
    private static ItemStack chest() {
        var stack = new ItemStack(Items.DIAMOND_CHESTPLATE); stack.setDamageValue(17);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Chest armor stays equipped")); return stack;
    }
    private static ItemStack grain(int amount) {
        var stack = new ItemStack(RealmAgriculture.GRAIN.get(), amount); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Worn grain components"));
        var marker = new CompoundTag(); marker.putInt("wear_marker", 808); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker)); return stack;
    }
    private static ItemStack tool() {
        var stack = new ItemStack(Items.IRON_PICKAXE); stack.setDamageValue(7); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Worn tool components"));
        var marker = new CompoundTag(); marker.putInt("wear_marker", 173); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker)); return stack;
    }
    private static void verifyWorn(ServerPlayer p) {
        var worn = BackpackHarness.get(p); require(worn.is(ExpeditionEquipment.FIELD_BACKPACK.get()) && BackpackStorage.valid(worn), "Actual worn attachment is absent or invalid");
        require(BackpackStorage.id(worn).toString().equals(data.get("initial_uuid").getAsString()), "Wearing or slot movement changes the source pack UUID");
        var stored = BackpackStorage.read(worn);
        require(ItemStack.matches(stored.get(0), grain(40)) && stored.get(1).is(RealmAgriculture.GRAIN.get()) && stored.get(1).getCount() == 24
                && ItemStack.matches(stored.get(53), tool()), "Worn storage loses exact sparse slots, damage, names, custom data or quantities");
        require(stored.stream().filter(s -> !s.isEmpty()).count() == 3, "Wear/equipment movement creates additional stored stacks");
        require(worn.getHoverName().getString().equals("Native worn field pack") && BackpackStorage.mode(worn) == BackpackStorage.OFF, "Wear changed the backpack name or default mode");
        require(ItemStack.matches(p.getItemBySlot(EquipmentSlot.CHEST), chest()), "Backpack consumes or changes the chest armor slot");
    }
    private static void verifyCold(ServerPlayer p) {
        verifyWorn(p); require(inventoryPackCount(p) == 0, "Cold reload also restores a second inventory copy");
        require(BackpackStorage.id(BackpackHarness.get(p)).toString().equals(data.get("worn_uuid").getAsString()), "Cold attachment identity differs from saved owner");
        data.addProperty("reload_pid", ProcessHandle.current().pid()); data.addProperty("cold_attachment_exact_components_and_armor_preserved", true);
    }
    private static int inventoryPackCount(ServerPlayer p) {
        return p.getInventory().items.stream().filter(BackpackStorage::isPack).mapToInt(ItemStack::getCount).sum()
                + p.getInventory().offhand.stream().filter(BackpackStorage::isPack).mapToInt(ItemStack::getCount).sum();
    }
    private static boolean clientWorn(Minecraft mc) {
        var bag = BackpackHarness.get(mc.player);
        return bag.is(ExpeditionEquipment.FIELD_BACKPACK.get()) && BackpackStorage.id(bag) != null
                && BackpackStorage.id(bag).toString().equals(data.get("initial_uuid").getAsString())
                && ItemStack.matches(mc.player.getItemBySlot(EquipmentSlot.CHEST), chest());
    }
    private static boolean backpackMenu(Minecraft mc) {
        boolean ready = mc.screen instanceof BackpackScreen && mc.player.containerMenu instanceof BackpackMenu menu
                && menu.sourceSlot == BackpackHarness.SLOT && menu.capacity == 54;
        if (ready) org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 4, 4); return ready;
    }
    private static void registeredB(Minecraft mc) {
        require(mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu, "B should be pressed from the ordinary world");
        KeyMapping.click(InputConstants.getKey(org.lwjgl.glfw.GLFW.GLFW_KEY_B, -1));
    }
    private static void validateLayers(Minecraft mc) throws ReflectiveOperationException {
        var field = LivingEntityRenderer.class.getDeclaredField("layers"); field.setAccessible(true); var checks = new JsonObject();
        for (var skin : new PlayerSkin.Model[]{PlayerSkin.Model.WIDE, PlayerSkin.Model.SLIM}) {
            var renderer = mc.getEntityRenderDispatcher().getSkinMap().get(skin);
            require(renderer instanceof LivingEntityRenderer<?, ?>, "Native player skin renderer is missing");
            boolean found = ((java.util.List<?>) field.get(renderer)).stream().anyMatch(layer -> layer instanceof BackpackLayer);
            require(found, "Backpack render layer missing from " + skin); checks.addProperty(skin.name(), found);
        }
        data.add("native_wide_and_slim_renderer_layers", checks); data.addProperty("actual_local_player_skin", mc.player.getSkin().model().name());
    }
    private static void click(Minecraft mc, int slot, ClickType type) { mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, type, mc.player); }
    private static boolean done() { if (work == null || !work.isDone()) return false; work.join(); return true; }
    private static void shot(Minecraft mc, String name) { Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> System.out.println("WEAR_BACKPACK_SCREENSHOT " + name)); }
    private static void require(boolean yes, String message) { if (!yes) throw new IllegalStateException(message); }
    private static void finish(Minecraft mc, boolean success, String why) {
        if (closing) return; closing = true; passed = success; reason = why;
        if (mc.player != null) mc.player.closeContainer(); mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5); mc.options.broadcastOptions();
        settling = new NativeChunkSettler.Session("wear_backpack_terminal_shutdown", 160); if (!success) write(mc, true);
    }
    private static void shutdown(Minecraft mc) {
        if (mc.getSingleplayerServer() == null) { finished = true; write(mc, false); mc.stop(); return; }
        if (settling.failed() && !data.has("quiescence_failed")) { passed = false; reason = settling.failure(); data.addProperty("quiescence_failed", true); write(mc, true); }
        if (!settling.ready()) return; data.add("settle_terminal", settling.report()); data.addProperty("clean_generation_before_mc_stop", true);
        finished = true; settling = null; write(mc, false); mc.stop();
    }
    private static void write(Minecraft mc, boolean pending) {
        data.addProperty("passed", passed); data.addProperty("reason", reason); data.addProperty("mode", MODE); data.addProperty("stage", stage); data.addProperty("shutdown_pending", pending);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("wear-backpack-" + MODE + "-validation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(data)); }
        catch (Exception failure) { failure.printStackTrace(); } System.out.println("WEAR_BACKPACK_VALIDATION " + data);
    }
}
