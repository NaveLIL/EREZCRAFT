package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.IslandWorld;

/** Progress belongs to vanilla player advancements; transient tide attempts cannot survive death/logout. */
@EventBusSubscriber(modid = Interstice.ID)
public final class ExpeditionTrials {
    public static final String JOURNAL_RECEIVED = "interstice:field_journal_received";
    private record Attempt(long cycle, long shelteredSince, long lastTick) {}
    private static final Map<UUID, Attempt> TIDES = new HashMap<>();
    private static final Set<UUID> CHECKED = new HashSet<>();
    private ExpeditionTrials() {}
    public static boolean completed(ServerPlayer player, String id) {
        var holder = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(Interstice.ID, id));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }
    public static void award(ServerPlayer player, String id, String criterion) {
        var holder = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(Interstice.ID, id));
        if (holder != null && holder.value().criteria().containsKey(criterion)) player.getAdvancements().award(holder, criterion);
    }
    public static void ensureJournal(ServerPlayer player) {
        if (player.getPersistentData().getBoolean(JOURNAL_RECEIVED)) return;
        player.getPersistentData().putBoolean(JOURNAL_RECEIVED, true);
        var journal = FieldJournal.create(player);
        if (!player.getInventory().add(journal)) player.drop(journal, false);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.interstice.field_journal"), false);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && completed(player, "root")) ensureJournal(player);
    }
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        TIDES.remove(player.getUUID());
        if (IslandWorld.isIsland(event.getTo())) ensureJournal(player);
        if (IslandWorld.isIsland(event.getFrom()) && event.getTo().equals(Level.OVERWORLD) && player.isAlive())
            award(player, "trials/return_home", "returned");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().overworld().getGameTime() % 20 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) process(player);
    }
    private static void process(ServerPlayer player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || !IslandWorld.isIsland(player.level().dimension())) {
            TIDES.remove(player.getUUID()); return;
        }
        // Existing characters already inside the realm also get the new visible route and journal.
        if (CHECKED.add(player.getUUID())) {
            String path = player.level().dimension().location().getPath();
            String criterion = switch (path) {
                case "islands" -> "entered_islands";
                case "islands_tall" -> "entered_tall";
                default -> "entered_" + path.replace("islands_", "");
            };
            award(player, "root", criterion);
            for (String id : List.of("rift_discovery", "arrival_guidance")) {
                String c = path.equals("islands") ? "legacy" : path.equals("islands_tall") ? "tall" : path.replace("islands_", "");
                award(player, id, c);
            }
            ensureJournal(player);
            // Re-evaluate held materials after upgrade. No grants for items the player does not own.
            for (var stack : player.getInventory().items)
                if (!stack.isEmpty()) CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), stack);
        }
        observeTide(player, TideManager.getState(player.server), ShelterDetector.isSheltered(player.level(), player));
        if (RealmArmaments.protectivePieces(player) == 4) award(player, "trials/riftsteel_suit", "equipped");
    }
    public static void observeTide(ServerPlayer player, TideState tide, boolean sheltered) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || !IslandWorld.isIsland(player.level().dimension())) {
            TIDES.remove(player.getUUID()); return;
        }
        Attempt old = TIDES.get(player.getUUID());
        long now = player.server.overworld().getGameTime();
        if (tide.phase() == TidePhase.SURGE) {
            if (!sheltered) { TIDES.remove(player.getUUID()); return; }
            long since = old != null && old.cycle() == tide.totalCycles() && now - old.lastTick() <= 40 ? old.shelteredSince() : now;
            TIDES.put(player.getUUID(), new Attempt(tide.totalCycles(), since, now));
        } else if (tide.phase() == TidePhase.EBB) {
            if (old != null && old.cycle() == tide.totalCycles() && now - old.shelteredSince() >= 200 && now - old.lastTick() <= 40 && sheltered)
                award(player, "trials/survive_tide", "survived");
            TIDES.remove(player.getUUID());
        } else TIDES.remove(player.getUUID());
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TIDES.remove(player.getUUID());
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        event.getEntity().getPersistentData().putBoolean(JOURNAL_RECEIVED, event.getOriginal().getPersistentData().getBoolean(JOURNAL_RECEIVED));
        TIDES.remove(event.getEntity().getUUID()); CHECKED.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        TIDES.remove(event.getEntity().getUUID()); CHECKED.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { TIDES.clear(); CHECKED.clear(); }
}
