package pro.erez.interstice.rift;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** Shared physical connections; deliberately separate from each player's personal return bookmark. */
public final class RiftLinks extends SavedData {
    public static final String FILE_ID = "interstice_rifts";
    public enum Kind { PORTAL, FISHING, CAULDRON }
    public record Endpoint(ResourceKey<Level> dimension, BlockPos pos, Direction.Axis axis) {
        public Endpoint { pos = pos.immutable(); if (axis == Direction.Axis.Y) throw new IllegalArgumentException("Vertical frame axis"); }
    }
    public record Link(UUID id, Kind kind, Endpoint source, Endpoint echo) {}
    private final Map<UUID, Link> links = new LinkedHashMap<>();
    public static RiftLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RiftLinks::new, RiftLinks::load, null), FILE_ID);
    }
    public Link source(Endpoint endpoint, Kind kind) {
        for (Link link : links.values()) if (link.kind() == kind && link.source().equals(endpoint)) return link;
        return null;
    }
    public Link echo(ResourceKey<Level> dimension, BlockPos pos) {
        for (Link link : links.values()) if (link.echo().dimension().equals(dimension) && link.echo().pos().equals(pos)) return link;
        return null;
    }
    public Link byId(UUID id) { return links.get(id); }
    public Link add(Kind kind, Endpoint source, Endpoint echo) {
        if (source(source, kind) != null || echo(echo.dimension(), echo.pos()) != null) throw new IllegalStateException("Rift endpoint already linked");
        Link link = new Link(UUID.randomUUID(), kind, source, echo);
        links.put(link.id(), link); setDirty(); return link;
    }
    public int size() { return links.size(); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Version", 1);
        ListTag records = new ListTag();
        for (Link link : links.values()) {
            CompoundTag record = new CompoundTag(); record.putUUID("Id", link.id()); record.putString("Kind", link.kind().name());
            record.put("Source", endpointTag(link.source())); record.put("Echo", endpointTag(link.echo())); records.add(record);
        }
        tag.put("Links", records); return tag;
    }
    private static CompoundTag endpointTag(Endpoint endpoint) {
        CompoundTag tag = new CompoundTag(); tag.putString("Dimension", endpoint.dimension().location().toString());
        tag.putLong("Pos", endpoint.pos().asLong()); tag.putString("Axis", endpoint.axis().getName()); return tag;
    }
    private static Endpoint endpoint(CompoundTag tag) {
        ResourceLocation name = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (name == null || !tag.contains("Pos", Tag.TAG_LONG)) return null;
        String axis = tag.getString("Axis"); if (!axis.equals("x") && !axis.equals("z")) return null;
        return new Endpoint(ResourceKey.create(Registries.DIMENSION, name), BlockPos.of(tag.getLong("Pos")), axis.equals("x") ? Direction.Axis.X : Direction.Axis.Z);
    }
    public static RiftLinks load(CompoundTag tag, HolderLookup.Provider provider) {
        RiftLinks result = new RiftLinks();
        for (Tag entry : tag.getList("Links", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) entry;
            Endpoint source = endpoint(record.getCompound("Source")), echo = endpoint(record.getCompound("Echo"));
            if (source == null || echo == null || !record.hasUUID("Id")) continue;
            Kind kind; try { kind = Kind.valueOf(record.getString("Kind")); } catch (IllegalArgumentException ignored) { continue; }
            UUID id = record.getUUID("Id");
            if (result.links.containsKey(id) || result.source(source, kind) != null || result.echo(echo.dimension(), echo.pos()) != null) continue;
            result.links.put(id, new Link(id, kind, source, echo));
        }
        return result;
    }
}
