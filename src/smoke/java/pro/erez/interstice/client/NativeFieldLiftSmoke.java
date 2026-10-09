package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.lift.*;
import pro.erez.interstice.minerals.MineralEcology;

/** Prepared flat fixture, real Survival placement/use/menu/passenger packets, two different JVMs. */
@EventBusSubscriber(modid = "interstice", value = Dist.CLIENT)
public final class NativeFieldLiftSmoke {
    private static final String MODE = System.getProperty("interstice.fieldLiftSmoke", "");
    private static final String WORLD = "native-field-lift-check";
    private static final BlockPos ANCHOR = new BlockPos(8, 72, 8);
    private static boolean started, finished, closing, passed;
    private static String reason;
    private static int stage, ticks;
    private static long deadline;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static JsonObject data = new JsonObject();
    private static long motionStart;
    private static double motionStartY;
    private static int motionStartFuel;
    private NativeFieldLiftSmoke() {}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { if (!MODE.isEmpty() && settling != null) settling.tick(event.getServer()); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (MODE.isEmpty() || finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (closing) { shutdown(mc); return; }
            if (!started && mc.screen instanceof TitleScreen) {
                require(MODE.equals("create") || MODE.equals("reload"), "Invalid field lift smoke mode"); started = true;
                deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
                mc.options.pauseOnLostFocus = false; mc.options.hideGui = false; mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5); mc.options.framerateLimit().set(60);
                if (MODE.equals("create")) {
                    require(!Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat")), "Disposable field lift save already exists");
                    var rules = new GameRules(); rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings("Native field lift fixture", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20261009L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                } else {
                    data = JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("lift-create-validation.json"))).getAsJsonObject();
                    require(data.get("passed").getAsBoolean() && data.get("creator_pid").getAsLong() != ProcessHandle.current().pid(), "Reload requires a successful different-JVM creation");
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> {});
                }
                return;
            }
            if (!started || SmokeWorldPrompts.advance(mc)) return;
            require(System.nanoTime() < deadline, "Native lift stage deadline " + stage);
            if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            if (stage == 0) {
                work = server.submit(() -> { var player = server.getPlayerList().getPlayer(uuid); if (MODE.equals("create")) prepare(player); return true; });
                settling = new NativeChunkSettler.Session("field_lift_initial_flat_fixture", 1600); stage = 1; ticks = 0; return;
            }
            if (stage == 1 && done() && settling.ready() && ++ticks >= 30) {
                data.add("settle_initial", settling.report()); settling = null;
                if (MODE.equals("create")) { select(mc, 0); click(mc, ANCHOR.below()); stage = 2; }
                else { stage = 30; work = server.submit(() -> verifyCold(server.getPlayerList().getPlayer(uuid))); }
                ticks = 0;
            } else if (stage == 2 && ++ticks >= 20) {
                work = server.submit(() -> { var player = server.getPlayerList().getPlayer(uuid); var anchor = anchor(player); require(anchor != null && anchor.lift() != null, "Native anchor Item placement did not spawn its real vehicle");
                    require(player.getInventory().getItem(0).isEmpty(), "Survival anchor placement did not consume its item"); data.addProperty("anchor_uuid", anchor.anchorId().toString()); data.addProperty("lift_uuid", anchor.liftId().toString()); return true; });
                stage = 3; ticks = 0;
            } else if (stage == 3 && done() && ++ticks >= 12) { select(mc, 2); click(mc, ANCHOR); stage = 4; ticks = 0; }
            else if (stage == 4 && ++ticks >= 20) {
                work = server.submit(() -> { var player = server.getPlayerList().getPlayer(uuid); require(anchor(player).fuel() == 3200 && player.getInventory().getItem(2).getCount() == 1, "Actual native coal click did not fund finite transport"); return true; }); stage = 5; ticks = 0;
            } else if (stage == 5 && done() && ++ticks >= 12) { select(mc, 1); sneak(mc, true); click(mc, ANCHOR); stage = 6; ticks = 0; }
            else if (stage == 6 && ++ticks >= 20) {
                sneak(mc, false); work = server.submit(() -> { var player = server.getPlayerList().getPlayer(uuid); require(player.getInventory().getItem(1).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().hasUUID("LiftAnchorId"), "Native controller click did not persist a bound anchor"); return true; }); stage = 7; ticks = 0;
            } else if (stage == 7 && done() && clientLift(mc) != null && ++ticks >= 12) { nativeInteract(mc, true, "cargo_interaction_sent"); stage = 8; ticks = 0; }
            else if (stage == 8) {
                ticks++;
                if(ticks==10&&done())work=diagnostic(mc,"cargo_after_ten_client_ticks",null);
                if(cargoMenu(mc)&&ticks>=20&&done()){shift(mc,9);shift(mc,10);stage=9;ticks=0;}
                else if(ticks>=40){require(work==null||work.isDone(),"Native cargo diagnostic not completed within40clientticks");done();require(cargoMenu(mc),"Native cargo screen not acknowledged within40clientticks; see cargo interaction telemetry");}
            }
            else if (stage == 9 && cargoMenu(mc) && ++ticks >= 25) {
                work = server.submit(() -> { var player = server.getPlayerList().getPlayer(uuid); checkCargo(anchor(player).lift()); require(player.getInventory().getItem(9).isEmpty() && player.getInventory().getItem(10).isEmpty(), "Native cargo transfer did not consume source slots"); data.addProperty("native_cargo_menu_transfer", true); return true; }); stage = 10; ticks = 0;
            } else if (stage == 10 && done() && ++ticks >= 12) {
                shot(mc, "field-lift-native-cargo.png"); mc.player.closeContainer(); sneak(mc, false); stage = 11; ticks = 0;
            } else if (stage == 11 && ++ticks >= 15 && clientLift(mc) != null) { nativeInteract(mc, false, "boarding_interaction_sent"); stage = 12; ticks = 0; }
            else if (stage == 12) {
                ticks++;
                if(mc.player.isPassenger()&&ticks>=12&&done()){work=server.submit(()->beginMotion(server.getPlayerList().getPlayer(uuid)));stage=13;ticks=0;}
                else if(ticks>=40&&done())require(mc.player.isPassenger(),"Native boarding not acknowledged within40clientticks; see boarding interaction telemetry");
            } else if (stage == 13 && done()) { select(mc, 1); mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); stage = 14; ticks = 0; }
            else if (stage == 14) {
                if (work == null || work.isDone()) {
                    if (work != null) work.join();
                    work = server.submit(() -> motionComplete(server.getPlayerList().getPlayer(uuid), true)); stage = 15;
                }
            } else if (stage == 15 && done()) {
                if (!Boolean.TRUE.equals(work.join())) { stage = 14; return; }
                select(mc, 1); mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); stage = 16; ticks = 0;
            } else if (stage == 16 && ++ticks >= 20) {
                work = server.submit(() -> saveStopped(server.getPlayerList().getPlayer(uuid), true)); stage = 17; ticks = 0;
            } else if (stage == 17 && done() && ++ticks >= 20) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); stage = 18; ticks = 0;
            } else if (stage == 18 && ++ticks >= 30) { shot(mc, "field-lift-native-survival-rider.png"); finish(mc, true, "Real Survival anchor/fuel/controller/cargo and >100 native passenger ticks verified"); }
            else if (stage == 30 && done()) {
                if (!Boolean.TRUE.equals(work.join())) { require(++ticks < 180, "Saved cargo vehicle did not load in its anchor chunk"); work = server.submit(() -> verifyCold(server.getPlayerList().getPlayer(uuid))); return; }
                work = server.submit(() -> { var player = server.getPlayerList().getPlayer(uuid); var lift = anchor(player).lift();
                    // Fixture view only: reconnect is world-owned, so restore a safe standing pose before real boarding.
                    player.teleportTo(player.serverLevel(), lift.getX(), lift.getY() + .35, lift.getZ(), java.util.Set.of(), 90, 15); player.hasChangedDimension();
                    data.addProperty("reload_prepared_standing_pose_before_native_board", true); return true; }); stage = 31; ticks = 0;
            } else if (stage == 31 && done() && clientLift(mc) != null && ++ticks >= 20) { nativeInteract(mc, false, "reload_boarding_interaction_sent"); stage = 32; ticks = 0; }
            else if(stage==32){ticks++;if(mc.player.isPassenger()&&ticks>=15&&done()){work=server.submit(()->beginMotion(server.getPlayerList().getPlayer(uuid)));stage=33;ticks=0;}
                else if(ticks>=40&&done())require(mc.player.isPassenger(),"Native reload boarding not acknowledged within40clientticks; see interaction telemetry");}
            else if (stage == 33 && done()) { select(mc, 1); sneak(mc, false); mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                data.addProperty("reload_direction_uses_native_rmb_without_sneak_dismount",true);stage = 34; ticks = 0; }
            else if (stage == 34) {
                if (work == null || work.isDone()) { if (work != null) work.join(); work = server.submit(() -> motionComplete(server.getPlayerList().getPlayer(uuid), false)); stage = 35; }
            } else if (stage == 35 && done()) {
                if (!Boolean.TRUE.equals(work.join())) { stage = 34; return; }
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); sneak(mc, false); stage = 36; ticks = 0;
            } else if (stage == 36 && ++ticks >= 20) { work = server.submit(() -> saveStopped(server.getPlayerList().getPlayer(uuid), false)); stage = 37; ticks = 0; }
            else if (stage == 37 && done() && ++ticks >= 20) { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); stage = 38; ticks = 0; }
            else if (stage == 38 && ++ticks >= 30) { shot(mc, "field-lift-native-cold-reload.png"); finish(mc, true, "Different-JVM identity/cargo/fuel and native descending passenger transport verified"); }
        } catch (Throwable failure) { failure.printStackTrace(); finish(mc, false, failure.toString()); }
    }
    private static void prepare(ServerPlayer player) {
        var level = player.serverLevel(); level.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, player.server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server); level.setDayTime(6000);
        level.getChunk(0, 0);
        for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++) {
            level.setBlock(new BlockPos(x, 71, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 72; y <= 126; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(RealmLift.ANCHOR_ITEM.get()));
        player.getInventory().setItem(1, new ItemStack(RealmLift.CONTROLLER.get())); player.getInventory().setItem(2, new ItemStack(MineralEcology.UMBRAL_COAL.get(), 2));
        player.getInventory().setItem(9, cargoTool()); player.getInventory().setItem(10, new ItemStack(MineralEcology.WORLD_STICK.get(), 12));
        player.setGameMode(GameType.SURVIVAL); player.teleportTo(level, 12, 72, 8.5, java.util.Set.of(), 90, 15); player.hasChangedDimension();
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
        data.addProperty("creator_pid", ProcessHandle.current().pid()); data.addProperty("fixture_role", "Prepared flat site and supplies; subsequent placement, fuel, controller, cargo and passenger actions use native Survival packets");
    }
    private static ItemStack cargoTool() {
        var tool = new ItemStack(Items.DIAMOND_PICKAXE); tool.setDamageValue(123); tool.set(DataComponents.CUSTOM_NAME, Component.literal("Field cargo tool"));
        var custom = new CompoundTag(); custom.putString("expedition_marker", "native-lift-two-jvm"); tool.set(DataComponents.CUSTOM_DATA, CustomData.of(custom)); return tool;
    }
    private static FieldAnchorEntity anchor(ServerPlayer player) { return player.serverLevel().getBlockEntity(ANCHOR) instanceof FieldAnchorEntity anchor ? anchor : null; }
    private static void checkCargo(FieldLiftEntity lift) {
        require(lift != null && lift.cargo().getContainerSize() == 9, "Missing real nine-slot cargo platform");
        int tools = 0, sticks = 0;
        for (int i = 0; i < 9; i++) {
            var item = lift.cargo().getItem(i);
            if (item.is(Items.DIAMOND_PICKAXE)) { require(ItemStack.matches(item, cargoTool()), "Native cargo lost custom name, data, damage or count"); tools += item.getCount(); }
            if (item.is(MineralEcology.WORLD_STICK.get())) sticks += item.getCount();
        }
        require(tools == 1 && sticks == 12, "Native cargo quantities changed or duplicated");
    }
    private static boolean beginMotion(ServerPlayer player) {
        var anchor = anchor(player); var lift = anchor.lift();
        require(player.getVehicle() == lift && player.isPassenger() && lift.getControllingPassenger() == null, "Native boarding did not create the correct passenger relationship");
        require(!player.getAbilities().mayfly && !player.getAbilities().flying && player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "Transport proof cannot use creative or flight flags");
        require(lift.shouldBeSaved() && !player.saveWithoutId(new CompoundTag()).contains("RootVehicle"), "World-owned lift is duplicated in player RootVehicle data");
        motionStart = player.serverLevel().getGameTime(); motionStartY = lift.getY(); motionStartFuel = anchor.fuel(); return true;
    }
    private static boolean motionComplete(ServerPlayer player, boolean up) {
        var anchor = anchor(player); var lift = anchor.lift(); long elapsed = player.serverLevel().getGameTime() - motionStart;
        require(player.isAlive() && player.getVehicle() == lift && player.connection != null, "Native passenger was detached, killed or kicked");
        require(!player.getAbilities().mayfly && !player.getAbilities().flying, "Native movement enabled flight flags");
        if (elapsed < 120) return false;
        double moved = lift.getY() - motionStartY;
        require(up ? moved > 6 && moved < 10 : moved < -6 && moved > -10, "Actual native transport failed its bounded speed: " + moved);
        require(motionStartFuel - anchor.fuel() > 100 && motionStartFuel - anchor.fuel() < 180, "Finite moving fuel did not follow real native ticks");
        data.addProperty(MODE + "_native_passenger_ticks", elapsed); data.addProperty(MODE + "_actual_vertical_movement", moved);
        data.addProperty(MODE + "_survival_no_flight_and_no_kick", true); return true;
    }
    private static boolean saveStopped(ServerPlayer player, boolean create) {
        var anchor = anchor(player); var lift = anchor.lift(); require(!anchor.commanded() && !lift.moving(), "Ordinary controller stop did not halt native motion");
        checkCargo(lift); require(lift.getUUID().toString().equals(data.get("lift_uuid").getAsString()), "Controller interaction replaced the saved platform UUID");
        long count = player.serverLevel().getEntitiesOfClass(FieldLiftEntity.class, new AABB(ANCHOR).inflate(2, 52, 2)).stream().filter(e -> e.anchorPos().equals(ANCHOR)).count();
        require(count == 1, "A second cargo platform appeared for the same field anchor");
        if (create) { data.addProperty("saved_fuel", anchor.fuel()); data.addProperty("saved_lift_y", lift.getY()); data.addProperty("native_boarded_player_has_no_rootvehicle", true); }
        else data.addProperty("cold_preserved_components_quantities_single_uuid", true);
        return true;
    }
    private static boolean verifyCold(ServerPlayer player) {
        var anchor = anchor(player); if (anchor == null || anchor.lift() == null) return false;
        var lift = anchor.lift(); require(anchor.anchorId().toString().equals(data.get("anchor_uuid").getAsString()) && lift.getUUID().toString().equals(data.get("lift_uuid").getAsString()), "Different-JVM reload changed anchor/vehicle identities");
        require(anchor.fuel() == data.get("saved_fuel").getAsInt() && Math.abs(lift.getY() - data.get("saved_lift_y").getAsDouble()) < .001, "Paused cold transport moved or refilled fuel");
        checkCargo(lift); data.addProperty("reload_pid", ProcessHandle.current().pid()); data.addProperty("cold_fuel_paused_exact", true); return true;
    }
    private static FieldLiftEntity clientLift(Minecraft mc) { return mc.level.getEntitiesOfClass(FieldLiftEntity.class, new AABB(ANCHOR).inflate(2, 52, 2)).stream().findFirst().orElse(null); }
    private static boolean cargoMenu(Minecraft mc) { return mc.player.containerMenu instanceof ChestMenu chest && chest.getContainer().getContainerSize() == 9 && mc.screen != null; }
    private static void select(Minecraft mc, int slot) { mc.player.getInventory().selected = slot; mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot)); }
    private static void click(Minecraft mc, BlockPos pos) { mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(new Vec3(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5), net.minecraft.core.Direction.UP, pos, false)); }
    private static void sneak(Minecraft mc, boolean value) {
        // LocalPlayer.isShiftKeyDown reads Input, not the shared entity flag used by setShiftKeyDown.
        // Native interact packets encode this Input value and overwrite the server's secondary-use state.
        mc.player.input.shiftKeyDown=value;mc.player.setShiftKeyDown(value);
        mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, value ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
    }
    private static void nativeInteract(Minecraft mc,boolean secondary,String label){
        require(clientLift(mc)!=null,"Tracked lift entity missing for native interaction");sneak(mc,secondary);
        var result=mc.gameMode.interact(mc.player,clientLift(mc),InteractionHand.MAIN_HAND);
        work=diagnostic(mc,label,result.toString());
    }
    private static CompletableFuture<?> diagnostic(Minecraft mc,String label,String result){
        var client=new JsonObject();var entity=clientLift(mc);
        client.addProperty("player_position",mc.player.position().toString());client.addProperty("player_uuid",mc.player.getUUID().toString());
        client.addProperty("input_shift",mc.player.input.shiftKeyDown);client.addProperty("is_shift",mc.player.isShiftKeyDown());client.addProperty("passenger",mc.player.isPassenger());
        client.addProperty("menu",mc.player.containerMenu.getClass().getName());client.addProperty("menu_id",mc.player.containerMenu.containerId);
        client.addProperty("screen",mc.screen==null?"null":mc.screen.getClass().getName());if(result!=null)client.addProperty("native_returned_interaction_result",result);
        if(entity!=null){client.addProperty("entity_id",entity.getId());client.addProperty("entity_uuid",entity.getUUID().toString());client.addProperty("entity_position",entity.position().toString());client.addProperty("distance_squared",mc.player.distanceToSqr(entity));}
        var uuid=mc.player.getUUID();var server=mc.getSingleplayerServer();
        return server.submit(()->{
            var record=new JsonObject();record.add("client",client);var player=server.getPlayerList().getPlayer(uuid);var anchor=anchor(player);var lift=anchor==null?null:anchor.lift();
            var actual=new JsonObject();actual.addProperty("player_position",player.position().toString());actual.addProperty("player_uuid",player.getUUID().toString());
            actual.addProperty("is_shift",player.isShiftKeyDown());actual.addProperty("passenger",player.isPassenger());actual.addProperty("menu",player.containerMenu.getClass().getName());actual.addProperty("menu_id",player.containerMenu.containerId);
            if(anchor!=null){actual.addProperty("owner_uuid",anchor.owner()==null?"null":anchor.owner().toString());actual.addProperty("authorized",anchor.authorized(player));actual.addProperty("fuel",anchor.fuel());actual.addProperty("status",anchor.status().name());}
            if(lift!=null){actual.addProperty("entity_id",lift.getId());actual.addProperty("entity_uuid",lift.getUUID().toString());actual.addProperty("entity_position",lift.position().toString());actual.addProperty("distance_squared",player.distanceToSqr(lift));actual.addProperty("native_reach_check",player.canInteractWithEntity(lift.getBoundingBox(),1.0));}
            record.add("server",actual);data.add(label,record);System.out.println("FIELD_LIFT_NATIVE_INTERACTION "+label+" "+record);return true;
        });
    }
    private static void shift(Minecraft mc, int slot) { mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player); }
    private static boolean done() { if (work == null || !work.isDone()) return false; work.join(); return true; }
    private static void shot(Minecraft mc, String filename) { Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(), message -> System.out.println("FIELD_LIFT_SCREENSHOT " + filename)); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void finish(Minecraft mc, boolean success, String why) {
        if (closing) return; closing = true; passed = success; reason = why;
        if (mc.player != null) mc.player.closeContainer(); mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5); mc.options.broadcastOptions();
        settling = new NativeChunkSettler.Session("field_lift_terminal_shutdown", 240); if (!success) write(mc, true);
    }
    private static void shutdown(Minecraft mc) {
        if (mc.getSingleplayerServer() == null) { finished = true; write(mc, false); mc.stop(); return; }
        if (settling.failed() && !data.has("quiescence_failed")) { passed = false; reason = settling.failure(); data.addProperty("quiescence_failed", true); write(mc, true); }
        if (!settling.ready()) return; data.add("settle_terminal", settling.report()); data.addProperty("clean_generation_before_mc_stop", true); finished = true; settling = null; write(mc, false); mc.stop();
    }
    private static void write(Minecraft mc, boolean pending) {
        data.addProperty("passed", passed); data.addProperty("reason", reason); data.addProperty("mode", MODE); data.addProperty("stage", stage); data.addProperty("shutdown_pending", pending);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("lift-" + MODE + "-validation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(data)); }
        catch (Exception failure) { failure.printStackTrace(); }
        System.out.println("FIELD_LIFT_VALIDATION " + data);
    }
}
