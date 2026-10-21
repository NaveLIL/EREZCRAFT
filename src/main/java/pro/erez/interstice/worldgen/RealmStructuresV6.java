package pro.erez.interstice.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;

/** V6-only CC0 ruins. Shares the existing rare anchor/forest budget, never schedules a second grid. */
public final class RealmStructuresV6 {
    private static final List<ResourceLocation> RUINS = List.of(
            id("arch/ruined_archway0"), id("arch/ruined_archway1"), id("arch/ruined_staircase0"),
            id("monolith/tiny_pillar"), id("monolith/small_pillar"), id("monolith/medium_pillar"), id("monolith/large_pillar"),
            id("shrine/empty"), id("shrine/flower"), id("shrine/gold"), id("shrine/lodestone"), id("shrine/burner"),
            id("relic/ancient_golem"));
    private static final List<ResourceLocation> TOWERS = List.of(
            id("tower/ruined_tower0"), id("tower/ruined_tower1"), id("tower/ruined_tower2"),
            id("tower/ruined_tower3"), id("tower/ruined_tower4"), id("tower/ruined_tower5"));
    private static final List<ResourceLocation> TEMPLATES;
    static {
        var all = new ArrayList<ResourceLocation>();
        for (var family : List.of(RealmStructuresV5.Family.REFUGE, RealmStructuresV5.Family.CARGO_STATION))
            for (int variant = 0; variant < 2; variant++) all.add(family.template(variant));
        all.addAll(RUINS); all.addAll(TOWERS); TEMPLATES = List.copyOf(all);
    }
    private RealmStructuresV6() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Interstice.ID, "ethereal/" + path); }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
    public static List<ResourceLocation> templates() { return TEMPLATES; }
    public static RealmStructuresV5.Slot slot(long seed, int cellX, int cellZ) {
        return slot(seed,cellX,cellZ,true);
    }
    public static RealmStructuresV5.Slot slot(long seed, int cellX, int cellZ, boolean complexes) {
        var original = RealmStructuresV5.slot(seed, cellX, cellZ);
        if (original == null) return null;
        if (complexes && RealmComplexStructuresV6.scheduled(seed, original.chunk())) return null;
        var pool = switch (original.family()) {
            case LABORATORY -> RUINS;
            case OBSERVATION_POST -> TOWERS;
            default -> null;
        };
        if (pool == null) return original;
        int variant = Math.floorMod(mix(seed ^ original.chunk().toLong() ^ 0x455448455245414CL), pool.size());
        return new RealmStructuresV5.Slot(original.family(), variant, original.chunk(), original.rotation(), pool.get(variant));
    }
    public static RealmStructuresV5.Slot candidate(long seed, int chunkX, int chunkZ) {
        return candidate(seed,chunkX,chunkZ,true);
    }
    public static RealmStructuresV5.Slot candidate(long seed, int chunkX, int chunkZ,boolean complexes) {
        var selected = slot(seed, Math.floorDiv(chunkX, RealmStructuresV5.CELL_CHUNKS), Math.floorDiv(chunkZ, RealmStructuresV5.CELL_CHUNKS),complexes);
        return selected != null && selected.chunk().x == chunkX && selected.chunk().z == chunkZ ? selected : null;
    }
    public static boolean generate(GeometryProfile profile, ChunkAccess chunk, long seed, WorldGenRegion region, int minimum) {
        if (!IslandWorld.TENSION_WORLD.equals(region.getLevel().dimension())) return false;
        boolean complexes=region.getLevel().getServer().getWorldData().worldGenOptions().generateStructures();
        var selected=slot(seed,Math.floorDiv(chunk.getPos().x,RealmStructuresV5.CELL_CHUNKS),Math.floorDiv(chunk.getPos().z,RealmStructuresV5.CELL_CHUNKS),complexes);
        if(selected==null||!selected.chunk().equals(chunk.getPos()))return false;
        var caves = pro.erez.interstice.worldgen.terrain.NativeColumnSamplerV6.of(region.getLevel().getChunkSource().randomState()).forestCaves();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++)
                if (caves.entranceColumn(x, z)) return false;
        return place(profile, chunk, seed, region.getLevel().dimension(), region.getLevel().getStructureManager(), region.registryAccess(),selected.template(),selected.rotation(),minimum);
    }
    public static boolean generate(GeometryProfile profile, ChunkAccess chunk, long seed, ResourceKey<Level> dimension,
                                   StructureTemplateManager manager, HolderLookup.Provider registries, int minimum) {
        var selected = candidate(seed, chunk.getPos().x, chunk.getPos().z);
        return selected != null && place(profile, chunk, seed, dimension, manager, registries, selected.template(), selected.rotation(), minimum);
    }
    public static boolean place(GeometryProfile profile, ChunkAccess chunk, long seed, ResourceKey<Level> dimension,
                                StructureTemplateManager manager, HolderLookup.Provider registries,
                                ResourceLocation template, int rotation, int minimum) {
        if (!IslandWorld.TENSION_WORLD.equals(dimension) || !profile.equals(GeometryProfile.TALL)
                || rotation < 0 || rotation > 3 || !TEMPLATES.contains(template)) return false;
        return RealmStructuresV5.placeTemplate(profile, chunk, seed, manager, registries, template, rotation, minimum, true);
    }
}
