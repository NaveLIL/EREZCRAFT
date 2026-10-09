package pro.erez.interstice.test;

import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.RealmStructuresV5;

/** Actual authored NBT, ProtoChunk placement and native loot: not a simulated schematic parser. */
@GameTestHolder("interstice_living")
@PrefixGameTestTemplate(false)
public final class RealmStructureGameTests {
    private static final long SEED = 20261009L;
    private static ProtoChunk terrain(GameTestHelper h, ChunkPos pos, int low, int step) {
        var profile = GeometryProfile.TALL;
        var chunk = new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(profile.minY(), profile.height()),
                h.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int y = low + (x >= 8 ? step : 0);
            chunk.setBlockState(new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z), Interstice.ABYSSAL_TURF.get().defaultBlockState(), false);
        }
        return chunk;
    }
    private static boolean place(GameTestHelper h, ProtoChunk chunk, RealmStructuresV5.Family family, int variant, int rotation) {
        return RealmStructuresV5.place(GeometryProfile.TALL, chunk, SEED, h.getLevel().getStructureManager(), h.getLevel().registryAccess(),
                family.template(variant), rotation, GeometryProfile.TALL.lowerSeaTop() + 1);
    }
    private static boolean generate(GameTestHelper h, ProtoChunk chunk) {
        return RealmStructuresV5.generate(GeometryProfile.TALL, chunk, SEED, h.getLevel().getStructureManager(), h.getLevel().registryAccess(),
                GeometryProfile.TALL.lowerSeaTop() + 1);
    }
    private static int occupied(ProtoChunk chunk, int minimum) {
        int count = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = minimum; y <= GeometryProfile.TALL.maxLand(); y++)
            if (!chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z)).isAir()) count++;
        return count;
    }
    private static BlockPos barrel(ProtoChunk chunk) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 35; y <= GeometryProfile.TALL.maxLand(); y++) {
            BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
            if (chunk.getBlockState(pos).is(Blocks.BARREL)) return pos;
        }
        throw new AssertionError("Authored structure lacks a real loot barrel");
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void allEightAuthoredTemplatesPlaceInFourRotationsWithoutBorderWrites(GameTestHelper h) {
        h.assertTrue(RealmStructuresV5.templates().size() == 8, "Four families require two distinct editable NBT variants each");
        var signatures = new HashSet<Integer>();
        for (var family : RealmStructuresV5.Family.values()) for (int variant = 0; variant < 2; variant++) {
            int count = -1;
            for (int rotation = 0; rotation < 4; rotation++) {
                var chunk = terrain(h, new ChunkPos(-19, 23), 72, 0);
                h.assertTrue(place(h, chunk, family, variant, rotation), "Template must place on clear native terrain: " + family + "/" + variant + "/" + rotation);
                int placed = occupied(chunk, 73);
                h.assertTrue(placed > 150, "An authored family must be a usable shelter or station, not a marker");
                if (count >= 0) h.assertTrue(count == placed, "Rotating rectangular templates lost blocks");
                count = placed;
                BlockPos loot = barrel(chunk); var data = chunk.getBlockEntityNbt(loot);
                h.assertTrue(data != null && data.getString("LootTable").equals(family.lootTable().toString()), "Template barrel must contain its family's actual loot table");
                h.assertTrue(data.getInt("x") == loot.getX() && data.getInt("y") == loot.getY() && data.getInt("z") == loot.getZ(), "Rotated block-entity positions must match placed blocks");
                for (int edge = 0; edge < 16; edge++) for (int y = 73; y < 90; y++) {
                    int sx = chunk.getPos().getMinBlockX(), sz = chunk.getPos().getMinBlockZ();
                    h.assertTrue(chunk.getBlockState(new BlockPos(sx, y, sz + edge)).isAir()
                            && chunk.getBlockState(new BlockPos(sx + 15, y, sz + edge)).isAir()
                            && chunk.getBlockState(new BlockPos(sx + edge, y, sz)).isAir()
                            && chunk.getBlockState(new BlockPos(sx + edge, y, sz + 15)).isAir(), "Every rotation must retain its generation-chunk border");
                }
            }
            h.assertTrue(signatures.add(count), "Authored variants must differ in their actual block plans");
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sharedFamilyBudgetIsSparseSeededAndKeepsBoundarySeparation(GameTestHelper h) {
        for (long seed : new long[]{0, SEED, SEED + (1L << 40)}) {
            int empty = 0; var all = new ArrayList<RealmStructuresV5.Slot>(); var families = new HashSet<RealmStructuresV5.Family>();
            for (int x = -12; x < 12; x++) for (int z = -12; z < 12; z++) {
                var slot = RealmStructuresV5.slot(seed, x, z);
                if (slot == null) { empty++; continue; }
                all.add(slot); families.add(slot.family());
                h.assertTrue(slot.equals(RealmStructuresV5.slot(seed, x, z)), "Reload or evaluation order must not change structure families");
                h.assertTrue(slot.equals(RealmStructuresV5.candidate(seed, slot.chunk().x, slot.chunk().z)), "Negative floor-divided cells must find their own anchor");
                int count = 0;
                for (int cx = x * RealmStructuresV5.CELL_CHUNKS; cx < (x + 1) * RealmStructuresV5.CELL_CHUNKS; cx++)
                    for (int cz = z * RealmStructuresV5.CELL_CHUNKS; cz < (z + 1) * RealmStructuresV5.CELL_CHUNKS; cz++)
                        if (RealmStructuresV5.candidate(seed, cx, cz) != null) count++;
                h.assertTrue(count == 1, "An occupied region cannot schedule overlapping families");
            }
            for (int a = 0; a < all.size(); a++) for (int b = a + 1; b < all.size(); b++) {
                var first = all.get(a).chunk(); var second = all.get(b).chunk();
                h.assertTrue(Math.hypot(first.x - second.x, first.z - second.z) >= RealmStructuresV5.MIN_SEPARATION_CHUNKS,
                        "Different structure families clustered across a region edge");
            }
            h.assertTrue(empty > 230 && empty < 450 && families.size() == 4, "Distribution must contain empty regions and every family over multiple seeds");
            System.out.println("V5_STRUCTURE_BUDGET seed=" + seed + " occupied=" + all.size() + " empty=" + empty + " cells=576 minChunkGap=" + RealmStructuresV5.MIN_SEPARATION_CHUNKS);
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unevenGroundGetsOnlyShortSupportsAndNeverTerraforming(GameTestHelper h) {
        var chunk = terrain(h, new ChunkPos(0, 0), 60, 3);
        h.assertTrue(place(h, chunk, RealmStructuresV5.Family.REFUGE, 0, 0), "An eligible three-block slope must support a short foundation");
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int y = x >= 8 ? 63 : 60;
            h.assertTrue(chunk.getBlockState(new BlockPos(x, y, z)).is(Interstice.ABYSSAL_TURF.get()), "Placement must preserve every existing ground block");
            h.assertTrue(chunk.getBlockState(new BlockPos(x, 59, z)).isAir(), "Placement must not turn an island into a column to bedrock");
        }
        var steep = terrain(h, new ChunkPos(0, 0), 60, 5);
        h.assertTrue(!place(h, steep, RealmStructuresV5.Family.REFUGE, 0, 0), "Steep terrain must skip structure placement instead of being flattened");
        h.assertTrue(occupied(steep, 66) == 0, "A rejected slope cannot partially place a structure");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obstructionAndUnsafeSeaPositionsRejectAtomically(GameTestHelper h) {
        var chunk = terrain(h, new ChunkPos(0, 0), 60, 0);
        BlockPos chest = new BlockPos(8, 62, 8);
        chunk.setBlockState(chest, Blocks.CHEST.defaultBlockState(), false);
        var before = new net.minecraft.nbt.CompoundTag(); before.putString("id", "minecraft:chest"); before.putString("CustomName", "\"Existing player cache\"");
        before.putInt("x", chest.getX()); before.putInt("y", chest.getY()); before.putInt("z", chest.getZ());
        chunk.setBlockEntityNbt(before.copy());
        h.assertTrue(!place(h, chunk, RealmStructuresV5.Family.REFUGE, 0, 0), "Existing non-terrain content must abort the whole structure");
        h.assertTrue(occupied(chunk, 61) == 1 && chunk.getBlockState(chest).is(Blocks.CHEST), "A failed plan may not write any other blocks");
        h.assertTrue(before.equals(chunk.getBlockEntityNbt(chest)), "A failed plan must preserve the existing container NBT");
        var shoreline = terrain(h, new ChunkPos(0, 0), GeometryProfile.TALL.lowerSeaTop() + 3, 0);
        h.assertTrue(!place(h, shoreline, RealmStructuresV5.Family.REFUGE, 0, 0), "Structures must not stand in the toxic shoreline clearance");
        var ceiling = terrain(h, new ChunkPos(0, 0), GeometryProfile.TALL.maxLand() - 2, 0);
        h.assertTrue(!place(h, ceiling, RealmStructuresV5.Family.OBSERVATION_POST, 0, 0), "A tall roof must not intersect the upper toxic sea");
        h.assertTrue(occupied(shoreline, 40) == 0 && occupied(ceiling, GeometryProfile.TALL.maxLand() - 1) == 0, "Unsafe altitude rejection must be atomic");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void repeatGenerationDoesNotRefillClaimedLootOrRebuildStructures(GameTestHelper h) {
        RealmStructuresV5.Slot slot = null;
        for (int x = -5; x <= 5 && slot == null; x++) for (int z = -5; z <= 5 && slot == null; z++) slot = RealmStructuresV5.slot(SEED, x, z);
        h.assertTrue(slot != null, "Test seed must select an occupied region");
        var chunk = terrain(h, slot.chunk(), 72, 0);
        h.assertTrue(generate(h, chunk), "The scheduled family must use the real authored-template generator");
        BlockPos loot = barrel(chunk); var claimed = chunk.getBlockEntityNbt(loot).copy();
        claimed.remove("LootTable"); claimed.remove("LootTableSeed"); claimed.putString("CustomName", "\"Claimed cache\"");
        chunk.setBlockEntityNbt(claimed.copy());
        int count = occupied(chunk, 73);
        h.assertTrue(!generate(h, chunk), "A completed structure must not be reconstructed on a second call");
        h.assertTrue(count == occupied(chunk, 73) && claimed.equals(chunk.getBlockEntityNbt(loot)), "Repeat calls may not restore a claimed loot table or change its block entity");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void actualFamilyLootIsBoundedUsefulAndStaysClaimedAcrossReload(GameTestHelper h) {
        int index = 0;
        for (var family : RealmStructuresV5.Family.values()) {
            BlockPos local = new BlockPos(2 + index++, 2, 2); h.setBlock(local, Blocks.BARREL);
            var barrel = (BarrelBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(local));
            barrel.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, family.lootTable()), SEED);
            int count = 0;
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                var item = barrel.getItem(slot);
                if (!item.isEmpty()) {
                    h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).getNamespace().equals(Interstice.ID), "Structure loot must support the native material economy");
                    count += item.getCount();
                }
            }
            h.assertTrue(count >= 2 && count <= 16, "Loot must load real registered supplies without giant or empty rewards: " + family + " count=" + count);
            barrel.clearContent(); var saved = barrel.saveWithFullMetadata(h.getLevel().registryAccess());
            var loaded = new BarrelBlockEntity(h.absolutePos(local), barrel.getBlockState()); loaded.setLevel(h.getLevel());
            loaded.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(loaded.isEmpty(), "Unpacked loot must stay claimed after native block-entity reload");
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void structuresRefuseLegacyGeometryAndNonCandidateChunks(GameTestHelper h) {
        var chunk = terrain(h, new ChunkPos(0, 0), 60, 0);
        h.assertTrue(RealmStructuresV5.candidate(SEED, 0, 0) == null && !generate(h, chunk), "A noncandidate generation chunk cannot acquire an ordinary structure");
        h.assertTrue(!RealmStructuresV5.place(GeometryProfile.LEGACY, chunk, SEED, h.getLevel().getStructureManager(), h.getLevel().registryAccess(),
                RealmStructuresV5.Family.REFUGE.template(0), 0, GeometryProfile.LEGACY.minLand()), "New V5 structures cannot run against the retained legacy profile");
        h.assertTrue(occupied(chunk, 61) == 0, "Rejected legacy or scheduling paths must leave terrain intact");
        h.succeed();
    }
}
