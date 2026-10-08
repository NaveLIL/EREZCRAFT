package pro.erez.interstice.expedition;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** A player's one-use emergency entitlement is stored here, never in the key or its copies. */
public final class ExpeditionLedger extends SavedData {
    public record Origin(ResourceKey<Level> dimension, BlockPos feet) { public Origin { feet = feet.immutable(); } }
    public record Journey(UUID id, Origin origin, boolean entered, boolean spent) {}
    private final Map<UUID, Journey> journeys = new HashMap<>();
    public static ExpeditionLedger get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ExpeditionLedger::new, ExpeditionLedger::load, null), "interstice_expeditions"); }
    public Journey journey(UUID player) { return journeys.get(player); }
    public Journey prepare(UUID player, Origin origin) { Journey next = new Journey(UUID.randomUUID(), origin, false, false); journeys.put(player, next); setDirty(); return next; }
    public Journey arrive(UUID player, Origin origin) {
        Journey previous = journeys.get(player);
        if (previous != null && previous.entered()) return previous;
        Journey next = new Journey(previous == null ? UUID.randomUUID() : previous.id(), origin, true, previous != null && previous.spent());
        journeys.put(player, next); setDirty(); return next;
    }
    public boolean spend(UUID player, UUID journey) {
        Journey current = journeys.get(player);
        if (current == null || !current.id().equals(journey) || !current.entered() || current.spent()) return false;
        journeys.put(player, new Journey(current.id(), current.origin(), true, true)); setDirty(); return true;
    }
    public void abandon(UUID player) { Journey current = journeys.get(player); if (current != null && current.entered() && !current.spent()) spend(player, current.id()); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Version", 1); ListTag list = new ListTag();
        journeys.forEach((player, journey) -> {
            CompoundTag value = new CompoundTag(); value.putUUID("Player", player); value.putUUID("Journey", journey.id());
            value.putString("Dimension", journey.origin().dimension().location().toString()); value.putLong("Feet", journey.origin().feet().asLong());
            value.putBoolean("Entered", journey.entered()); value.putBoolean("Spent", journey.spent()); list.add(value);
        });
        tag.put("Journeys", list); return tag;
    }
    public static ExpeditionLedger load(CompoundTag tag, HolderLookup.Provider provider) {
        ExpeditionLedger result = new ExpeditionLedger();
        for (Tag entry : tag.getList("Journeys", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) entry; ResourceLocation dimension = ResourceLocation.tryParse(value.getString("Dimension"));
            if (dimension == null || !value.hasUUID("Player") || !value.hasUUID("Journey") || !value.contains("Feet", Tag.TAG_LONG)) continue;
            Origin origin = new Origin(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(value.getLong("Feet")));
            result.journeys.putIfAbsent(value.getUUID("Player"), new Journey(value.getUUID("Journey"), origin, value.getBoolean("Entered"), value.getBoolean("Spent")));
        }
        return result;
    }
}
