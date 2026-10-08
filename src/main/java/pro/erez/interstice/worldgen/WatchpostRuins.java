package pro.erez.interstice.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;

/** One candidate per 4x4 chunk region; an authored template is applied only during new-chunk surface generation. */
public final class WatchpostRuins {
    public static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath(Interstice.ID, "watchpost_ruin");
    private record Cell(BlockPos pos, BlockState state, CompoundTag blockEntity) {}
    private static final Map<StructureTemplate, List<Cell>> PLANS = new WeakHashMap<>();
    private WatchpostRuins() {}
    public static boolean candidate(long seed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, 4), regionZ = Math.floorDiv(chunkZ, 4);
        RandomSource random = RandomSource.create(seed ^ (regionX * 341873128712L) ^ (regionZ * 132897987541L) ^ 0x5A7C4F1L);
        return chunkX == regionX * 4 + random.nextInt(4) && chunkZ == regionZ * 4 + random.nextInt(4);
    }
    public static boolean generate(GeometryProfile profile, ChunkAccess chunk, long seed, StructureTemplateManager templates,
                                   net.minecraft.core.HolderLookup.Provider registries) {
        if (!candidate(seed, chunk.getPos().x, chunk.getPos().z)) return false;
        var optional = templates.get(TEMPLATE); if (optional.isEmpty()) throw new IllegalStateException("Missing authored Watchpost ruin template");
        List<Cell> plan = plan(optional.get(), registries);
        int x = chunk.getPos().getMinBlockX() + 4, z = chunk.getPos().getMinBlockZ() + 4;
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int dx = 0; dx < 9; dx++) for (int dz = 0; dz < 9; dz++) {
            int ground = Integer.MIN_VALUE;
            for (int y = profile.maxLand(); y >= profile.minLand(); y--) {
                var state = chunk.getBlockState(new BlockPos(x + dx, y, z + dz));
                if (state.isAir()) continue;
                if (StoneVaults.isGround(state)) ground = y;
                break;
            }
            if (ground == Integer.MIN_VALUE) return false;
            low = Math.min(low, ground); high = Math.max(high, ground);
        }
        if (high - low > 1) return false;
        BlockPos base = new BlockPos(x, high + 1, z);
        int turn = (int)((seed ^ chunk.getPos().toLong()) & 3);
        Rotation rotation = Rotation.values()[turn];
        for (Cell cell : plan) {
            BlockPos point = base.offset(rotated(cell.pos(), turn));
            if (!IslandChunkGenerator.landAllowed(profile, point.getX(), point.getY(), point.getZ()) || !chunk.getBlockState(point).isAir()) return false;
        }
        for (Cell cell : plan) {
            BlockPos point = base.offset(rotated(cell.pos(), turn));
            chunk.setBlockState(point, cell.state().rotate(rotation), false);
            if (cell.blockEntity() != null) {
                CompoundTag tag = cell.blockEntity().copy(); tag.putInt("x", point.getX()); tag.putInt("y", point.getY()); tag.putInt("z", point.getZ());
                tag.putLong("LootTableSeed", seed ^ point.asLong()); chunk.setBlockEntityNbt(tag);
            }
        }
        return true;
    }
    private static BlockPos rotated(BlockPos pos, int turn) {
        return switch (turn) { case 1 -> new BlockPos(8 - pos.getZ(), pos.getY(), pos.getX()); case 2 -> new BlockPos(8 - pos.getX(), pos.getY(), 8 - pos.getZ()); case 3 -> new BlockPos(pos.getZ(), pos.getY(), 8 - pos.getX()); default -> pos; };
    }
    private static synchronized List<Cell> plan(StructureTemplate template, net.minecraft.core.HolderLookup.Provider registries) {
        List<Cell> cached = PLANS.get(template); if (cached != null) return cached;
        CompoundTag data = template.save(new CompoundTag()); List<BlockState> palette = new ArrayList<>();
        for (Tag tag : data.getList("palette", Tag.TAG_COMPOUND)) palette.add(NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), (CompoundTag)tag));
        List<Cell> result = new ArrayList<>();
        for (Tag tag : data.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag)tag; var pos = block.getList("pos", Tag.TAG_INT);
            result.add(new Cell(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), palette.get(block.getInt("state")), block.contains("nbt") ? block.getCompound("nbt").copy() : null));
        }
        result = List.copyOf(result); PLANS.put(template, result); return result;
    }
}
