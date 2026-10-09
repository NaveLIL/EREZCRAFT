package pro.erez.interstice.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;

/** V5-only authored shelters: a shared, sparse anchor budget, atomic placement, no terrain clearing. */
public final class RealmStructuresV5 {
    public static final int CELL_CHUNKS = 18;
    public static final int MARGIN_CHUNKS = 2;
    public static final int MIN_SEPARATION_CHUNKS = 15;
    public static final int MAX_SLOPE = 3;
    public static final double OCCUPIED_CELL_CHANCE = .58;
    public enum Family {
        REFUGE("refuge", "roofed", "collapsed"),
        CARGO_STATION("cargo_station", "covered_loading", "broken_gantry"),
        LABORATORY("laboratory", "sample_hall", "breached"),
        OBSERVATION_POST("observation_post", "sheltered", "tilted_roof");
        private final String directory;
        private final String[] variants;
        Family(String directory, String... variants) { this.directory = directory; this.variants = variants; }
        public ResourceLocation template(int variant) {
            if (variant < 0 || variant >= variants.length) throw new IllegalArgumentException("Unknown structure variant");
            return ResourceLocation.fromNamespaceAndPath(Interstice.ID, directory + "/" + variants[variant]);
        }
        public ResourceLocation lootTable() {
            return ResourceLocation.fromNamespaceAndPath(Interstice.ID, "chests/v5/" + directory);
        }
    }
    public record Slot(Family family, int variant, ChunkPos chunk, int rotation) {
        public ResourceLocation template() { return family.template(variant); }
    }
    private record Cell(BlockPos pos, BlockState state, CompoundTag blockEntity) {}
    private record Plan(int width, int height, int depth, List<Cell> cells) {}
    private static final Map<StructureTemplate, Plan> PLANS = new WeakHashMap<>();
    private RealmStructuresV5() {}
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
    private static double unit(long value) { return (mix(value) >>> 11) * 0x1.0p-53; }
    private static long cellSeed(long seed, int x, int z) {
        return mix(seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ 0x56355348454C5445L);
    }
    private static Slot proposal(long seed, int regionX, int regionZ) {
        long key = cellSeed(seed, regionX, regionZ);
        if (unit(key ^ 0x4F43435550494544L) >= OCCUPIED_CELL_CHANCE) return null;
        int interior = CELL_CHUNKS - 2 * MARGIN_CHUNKS;
        int x = Math.floorMod(mix(key ^ 0x58414E43484F52L), interior);
        int z = Math.floorMod(mix(key ^ 0x5A414E43484F52L), interior);
        Family family = Family.values()[Math.floorMod(mix(key ^ 0x46414D494C59L), Family.values().length)];
        int variant = (int)(mix(key ^ 0x56415249414E54L) & 1);
        int rotation = (int)(mix(key ^ 0x524F544154494F4EL) & 3);
        return new Slot(family, variant,
                new ChunkPos(regionX * CELL_CHUNKS + MARGIN_CHUNKS + x, regionZ * CELL_CHUNKS + MARGIN_CHUNKS + z), rotation);
    }
    /** Widely jittered proposals, then deterministic neighbour priority; no narrow rows of anchors. */
    public static Slot slot(long seed, int regionX, int regionZ) {
        Slot selected = proposal(seed, regionX, regionZ); if (selected == null) return null;
        long priority = mix(cellSeed(seed, regionX, regionZ) ^ 0x5052494F52495459L);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            Slot other = proposal(seed, regionX + dx, regionZ + dz); if (other == null) continue;
            long gapX = (long)selected.chunk.x - other.chunk.x, gapZ = (long)selected.chunk.z - other.chunk.z;
            if (gapX * gapX + gapZ * gapZ >= MIN_SEPARATION_CHUNKS * MIN_SEPARATION_CHUNKS) continue;
            long otherPriority = mix(cellSeed(seed, regionX + dx, regionZ + dz) ^ 0x5052494F52495459L);
            int order = Long.compareUnsigned(otherPriority, priority);
            if (order < 0 || order == 0 && (dx < 0 || dx == 0 && dz < 0)) return null;
        }
        return selected;
    }
    public static Slot candidate(long seed, int chunkX, int chunkZ) {
        Slot slot = slot(seed, Math.floorDiv(chunkX, CELL_CHUNKS), Math.floorDiv(chunkZ, CELL_CHUNKS));
        return slot != null && slot.chunk.x == chunkX && slot.chunk.z == chunkZ ? slot : null;
    }
    /** Conservative immutable field: one rare candidate chunk is reserved at every height, even if its terrain later rejects the template. */
    public static boolean forestReserved(long seed, int blockX, int blockZ) {
        return candidate(seed, Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16)) != null;
    }
    /** Seed-scoped memoization for a generation probe. It reads no placed blocks or mutable neighbour state. */
    public static java.util.function.BiPredicate<Integer, Integer> forestReservation(long seed) {
        var cache = new java.util.concurrent.ConcurrentHashMap<Long, Boolean>();
        return (x, z) -> {
            int chunkX = Math.floorDiv(x, 16), chunkZ = Math.floorDiv(z, 16);
            return cache.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), key -> candidate(seed, chunkX, chunkZ) != null);
        };
    }
    public static List<ResourceLocation> templates() {
        var result = new ArrayList<ResourceLocation>();
        for (Family family : Family.values()) for (int variant = 0; variant < 2; variant++) result.add(family.template(variant));
        return List.copyOf(result);
    }
    public static boolean generate(GeometryProfile profile, ChunkAccess chunk, long seed, WorldGenRegion region, int minimum) {
        return generate(profile, chunk, seed, region.getLevel().getStructureManager(), region.registryAccess(), minimum);
    }
    /** The overload also permits real ProtoChunk/template-manager acceptance tests without a mock region. */
    public static boolean generate(GeometryProfile profile, ChunkAccess chunk, long seed,
                                   StructureTemplateManager manager, HolderLookup.Provider registries, int minimum) {
        Slot selected = candidate(seed, chunk.getPos().x, chunk.getPos().z);
        return selected != null && place(profile, chunk, seed, manager, registries, selected.template(), selected.rotation(), minimum);
    }
    /** Same atomic placement path for an authored preview or tests; never clears existing blocks. */
    public static boolean place(GeometryProfile profile, ChunkAccess chunk, long seed,
                               StructureTemplateManager manager, HolderLookup.Provider registries,
                               ResourceLocation template, int rotation, int minimum) {
        if (!profile.equals(GeometryProfile.TALL) || rotation < 0 || rotation > 3 || !templates().contains(template)) return false;
        var authored = manager.get(template).orElseThrow(() -> new IllegalStateException("Missing V5 authored template " + template));
        Plan plan = plan(authored, registries);
        int width = rotation % 2 == 0 ? plan.width : plan.depth;
        int depth = rotation % 2 == 0 ? plan.depth : plan.width;
        long key = mix(seed ^ chunk.getPos().toLong() ^ template.toString().hashCode());
        // Both the template and the jitter retain a one-block generation-chunk margin.
        int x = chunk.getPos().getMinBlockX() + 1 + Math.floorMod(mix(key ^ 0x58L), 15 - width);
        int z = chunk.getPos().getMinBlockZ() + 1 + Math.floorMod(mix(key ^ 0x5AL), 15 - depth);
        int[][] ground = new int[width][depth];
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        int bottom = Math.max(minimum, profile.lowerSeaTop() + 5);
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) {
            int y = exposedGround(profile, chunk, x + dx, z + dz, bottom);
            if (y < bottom) return false;
            ground[dx][dz] = y; low = Math.min(low, y); high = Math.max(high, y);
        }
        if (high - low > MAX_SLOPE) return false;
        BlockPos base = new BlockPos(x, high + 1, z);
        // The full interior, not just occupied NBT cells, must be unobstructed and have safe headroom.
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) for (int dy = 0; dy < plan.height; dy++) {
            BlockPos point = base.offset(dx, dy, dz);
            if (!inside(chunk, point) || !IslandChunkGenerator.featureAllowed(profile, point.getX(), point.getY(), point.getZ(), minimum)
                    || !chunk.getBlockState(point).isAir()) return false;
        }
        var placement = new LinkedHashMap<BlockPos, Cell>();
        Rotation turn = Rotation.values()[rotation];
        for (Cell cell : plan.cells) {
            BlockPos local = rotated(cell.pos, rotation, plan.width, plan.depth);
            BlockPos point = base.offset(local);
            placement.put(point, new Cell(point, cell.state.rotate(turn), cell.blockEntity));
            if (cell.pos.getY() == 0) {
                for (int y = ground[local.getX()][local.getZ()] + 1; y < base.getY(); y++) {
                    BlockPos foot = new BlockPos(point.getX(), y, point.getZ());
                    if (!chunk.getBlockState(foot).isAir()) return false;
                    placement.put(foot, new Cell(foot, VaultMaterials.WEATHERED_VAULTSTONE.get().defaultBlockState(), null));
                }
            }
        }
        // No block or block-entity writes occur until every validation above has passed.
        for (Cell cell : placement.values()) {
            chunk.setBlockState(cell.pos, cell.state, false);
            if (cell.blockEntity != null) {
                CompoundTag nbt = cell.blockEntity.copy();
                nbt.putInt("x", cell.pos.getX()); nbt.putInt("y", cell.pos.getY()); nbt.putInt("z", cell.pos.getZ());
                nbt.putLong("LootTableSeed", mix(seed ^ cell.pos.asLong() ^ template.toString().hashCode()));
                chunk.setBlockEntityNbt(nbt);
            }
        }
        return true;
    }
    private static int exposedGround(GeometryProfile profile, ChunkAccess chunk, int x, int z, int minimum) {
        for (int y = profile.maxLand(); y >= minimum; y--) {
            BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
            if (!state.isAir()) return StoneVaults.isGround(state) ? y : -1;
        }
        return -1;
    }
    private static boolean inside(ChunkAccess chunk, BlockPos pos) {
        return chunk.getPos().getMinBlockX() <= pos.getX() && pos.getX() <= chunk.getPos().getMaxBlockX()
                && chunk.getPos().getMinBlockZ() <= pos.getZ() && pos.getZ() <= chunk.getPos().getMaxBlockZ();
    }
    private static BlockPos rotated(BlockPos pos, int turn, int width, int depth) {
        return switch (turn) {
            case 1 -> new BlockPos(depth - 1 - pos.getZ(), pos.getY(), pos.getX());
            case 2 -> new BlockPos(width - 1 - pos.getX(), pos.getY(), depth - 1 - pos.getZ());
            case 3 -> new BlockPos(pos.getZ(), pos.getY(), width - 1 - pos.getX());
            default -> pos;
        };
    }
    private static synchronized Plan plan(StructureTemplate template, HolderLookup.Provider registries) {
        Plan cached = PLANS.get(template); if (cached != null) return cached;
        CompoundTag data = template.save(new CompoundTag());
        var size = data.getList("size", Tag.TAG_INT);
        int width = size.getInt(0), height = size.getInt(1), depth = size.getInt(2);
        if (width < 1 || width > 13 || depth < 1 || depth > 13 || height < 1 || height > 12)
            throw new IllegalStateException("V5 templates must fit their chunk-local bounds");
        var palette = new ArrayList<BlockState>();
        for (Tag item : data.getList("palette", Tag.TAG_COMPOUND)) {
            BlockState state = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), (CompoundTag)item);
            if (state.isAir()) throw new IllegalStateException("Unknown or air block in authored V5 template palette");
            palette.add(state);
        }
        var result = new ArrayList<Cell>(); var positions = new HashSet<BlockPos>();
        for (Tag item : data.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag)item; var location = block.getList("pos", Tag.TAG_INT);
            BlockPos pos = new BlockPos(location.getInt(0), location.getInt(1), location.getInt(2));
            if (pos.getX() < 0 || pos.getX() >= width || pos.getY() < 0 || pos.getY() >= height || pos.getZ() < 0 || pos.getZ() >= depth
                    || !positions.add(pos)) throw new IllegalStateException("Invalid authored V5 template position");
            int state = block.getInt("state");
            if (state < 0 || state >= palette.size()) throw new IllegalStateException("Invalid authored V5 template palette index");
            result.add(new Cell(pos, palette.get(state), block.contains("nbt") ? block.getCompound("nbt").copy() : null));
        }
        if (result.isEmpty() || result.stream().noneMatch(cell -> cell.pos.getY() == 0))
            throw new IllegalStateException("Authored V5 template requires a supported floor");
        Plan plan = new Plan(width, height, depth, List.copyOf(result)); PLANS.put(template, plan); return plan;
    }
}
