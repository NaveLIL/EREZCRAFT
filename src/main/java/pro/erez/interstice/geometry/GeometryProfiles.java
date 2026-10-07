package pro.erez.interstice.geometry;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

public final class GeometryProfiles {
    // Configuration arrives before ClientLevel exists. No current-world/global-Minecraft lookup.
    private static final Map<Connection, Map<ResourceLocation, GeometryProfile>> CONNECTIONS = new WeakHashMap<>();
    private GeometryProfiles() {}

    public static GeometryProfile get(BlockGetter view) {
        if (view instanceof GeometryView bound) return bound.intersticeGeometry();
        if (view instanceof ServerLevel level) {
            return level.getChunkSource().getGenerator() instanceof IslandChunkGenerator islands
                    ? islands.geometry() : GeometryProfile.LEGACY;
        }
        if (view instanceof WorldGenRegion region) return get(region.getLevel());
        if (view instanceof LevelChunk chunk) return get(chunk.getLevel());
        // Legacy laboratories, ordinary dimensions and standalone block views retain the old heights.
        return GeometryProfile.LEGACY;
    }

    public static synchronized void configure(Connection connection, Map<ResourceLocation, GeometryProfile> profiles) {
        CONNECTIONS.put(connection, Map.copyOf(profiles));
    }

    public static synchronized GeometryProfile forClientLevel(Connection connection, ResourceKey<Level> key,
                                                               net.minecraft.world.level.LevelHeightAccessor level) {
        Map<ResourceLocation, GeometryProfile> profiles = CONNECTIONS.get(connection);
        if (profiles == null) throw new IllegalStateException("Interstice geometry was not configured before world entry");
        GeometryProfile profile = profiles.get(key.location());
        if (profile == null) return GeometryProfile.LEGACY;
        profile.checkHeight(level);
        return profile;
    }
}
