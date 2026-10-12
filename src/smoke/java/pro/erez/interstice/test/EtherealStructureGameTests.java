package pro.erez.interstice.test;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.block.entity.LootrBarrelBlockEntity;
import noobanidus.mods.lootr.common.data.LootrSavedData;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.*;

/** Real templates/placement and upstream per-player inventories, not a simulated chest implementation. */
@GameTestHolder("interstice_ethereal")
@PrefixGameTestTemplate(false)
public final class EtherealStructureGameTests {
    private static final long SEED = 20261006;
    private static ProtoChunk terrain(GameTestHelper h, int y) {
        var chunk = new ProtoChunk(new ChunkPos(-19, 23), UpgradeData.EMPTY, LevelHeightAccessor.create(0, 256),
                h.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null);
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++)
                chunk.setBlockState(new BlockPos(x, y, z), Interstice.ABYSSAL_TURF.get().defaultBlockState(), false);
        return chunk;
    }
    private static long fingerprint(ProtoChunk chunk) {
        long result = 1;
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++)
                for (int y = 0; y < 256; y++) result = result * 31 + chunk.getBlockState(new BlockPos(x, y, z)).hashCode();
        return result;
    }
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void everyImportedTemplateResolvesAndPlacesAllFourRotationsWithPersonalCaches(GameTestHelper h) {
        h.assertTrue(RealmStructuresV6.templates().size() == 23, "V6 needs nineteen imported variants and four retained shelters/stations");
        for (var id : RealmStructuresV6.templates()) {
            var source = h.getLevel().getStructureManager().get(id).orElseThrow().save(new CompoundTag());
            for (var element : source.getList("palette", Tag.TAG_COMPOUND)) {
                var state = (CompoundTag)element;
                var name = ResourceLocation.parse(state.getString("Name"));
                var block = BuiltInRegistries.BLOCK.getOptional(name).orElseThrow();
                h.assertTrue(block != Blocks.AIR && !name.getNamespace().equals("estructs"), "Unresolved or marker block in " + id);
                for (var key : state.getCompound("Properties").getAllKeys())
                    h.assertTrue(block.getStateDefinition().getProperty(key) != null, "Obsolete block property " + key + " in " + id);
            }
            for (int turn = 0; turn < 4; turn++) {
                var chunk = terrain(h, 72);
                h.assertTrue(RealmStructuresV6.place(GeometryProfile.TALL, chunk, SEED, IslandWorld.TENSION_WORLD,
                        h.getLevel().getStructureManager(), h.getLevel().registryAccess(), id, turn, 35), "Cannot place " + id + " rotation=" + turn);
                int caches = 0, cells = 0;
                for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
                    for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                        h.assertTrue(chunk.getBlockState(new BlockPos(x, 72, z)).is(Interstice.ABYSSAL_TURF.get()), "Structure replaced its ground");
                        for (int y = 73; y < 104; y++) {
                            var pos = new BlockPos(x, y, z); var state = chunk.getBlockState(pos);
                            if (!state.isAir()) {
                                cells++;
                                h.assertTrue(x > chunk.getPos().getMinBlockX() && x < chunk.getPos().getMaxBlockX()
                                        && z > chunk.getPos().getMinBlockZ() && z < chunk.getPos().getMaxBlockZ(), "Structure writes outside its margin");
                            }
                            var data = chunk.getBlockEntityNbt(pos);
                            if (data != null && data.contains("LootTable")) {
                                caches++;
                                h.assertTrue(data.getString("id").equals("lootr:lootr_barrel")
                                        && BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals("lootr:lootr_barrel"), "V6 cache is not an upstream Lootr barrel");
                                h.assertTrue(data.getInt("x") == x && data.getInt("y") == y && data.getInt("z") == z, "Rotated cache NBT position mismatch");
                            }
                        }
                    }
                h.assertTrue(caches > 0 && cells >= 10, "Template has no usable architecture/cache: " + id);
                long before = fingerprint(chunk);
                h.assertTrue(!RealmStructuresV6.place(GeometryProfile.TALL, chunk, SEED, IslandWorld.TENSION_WORLD,
                        h.getLevel().getStructureManager(), h.getLevel().registryAccess(), id, turn, 35)
                        && before == fingerprint(chunk), "Repeated generation refills or rebuilds a ruin");
            }
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void V6OnlyCatalogUsesSharedAnchorsAndAllVariantsAcrossFixedSeeds(GameTestHelper h) {
        var all = new HashSet<ResourceLocation>();
        for (long seed : new long[]{0, 1, -1, SEED, 76198123, 4294967297L})
            for (int x = -16; x < 16; x++) for (int z = -16; z < 16; z++) {
                var original = RealmStructuresV5.slot(seed, x, z); var selected = RealmStructuresV6.slot(seed, x, z);
                h.assertTrue((original == null) == (selected == null), "A second scheduling grid inflated structure density");
                if (selected == null) continue;
                all.add(selected.template());
                h.assertTrue(original.chunk().equals(selected.chunk()) && original.rotation() == selected.rotation(), "V6 changed the shared rare/forest anchor field");
                h.assertTrue(selected.equals(RealmStructuresV6.candidate(seed, selected.chunk().x, selected.chunk().z)), "Negative coordinates or evaluation order changed a candidate");
                if (selected.family() == RealmStructuresV5.Family.LABORATORY || selected.family() == RealmStructuresV5.Family.OBSERVATION_POST)
                    h.assertTrue(selected.template().getPath().startsWith("ethereal/"), "A replaced old building remains scheduled in V6");
            }
        h.assertTrue(all.equals(new HashSet<>(RealmStructuresV6.templates())), "Some imported variants can never be scheduled");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void dimensionSeaAndObstructionRejectionLeaveEveryBlockUntouched(GameTestHelper h) {
        var template = ResourceLocation.fromNamespaceAndPath(Interstice.ID, "ethereal/tower/ruined_tower0");
        var safe = terrain(h, 60); long before = fingerprint(safe);
        for (var dimension : java.util.List.of(IslandWorld.VANILLA_WORLD, IslandWorld.WORLD, net.minecraft.world.level.Level.OVERWORLD))
            h.assertTrue(!RealmStructuresV6.place(GeometryProfile.TALL, safe, SEED, dimension, h.getLevel().getStructureManager(),
                    h.getLevel().registryAccess(), template, 0, 35) && before == fingerprint(safe), "Imported ruin escaped V6");
        for (int y : new int[]{37, GeometryProfile.TALL.maxLand() - 10}) {
            var unsafe = terrain(h, y); long hash = fingerprint(unsafe);
            h.assertTrue(!RealmStructuresV6.place(GeometryProfile.TALL, unsafe, SEED, IslandWorld.TENSION_WORLD,
                    h.getLevel().getStructureManager(), h.getLevel().registryAccess(), template, 0, 35)
                    && hash == fingerprint(unsafe), "Tall ruin breached sea clearance or wrote a partial plan");
        }
        var pos = new BlockPos(safe.getPos().getMinBlockX() + 8, 62, safe.getPos().getMinBlockZ() + 8);
        safe.setBlockState(pos, Blocks.CHEST.defaultBlockState(), false); before = fingerprint(safe);
        h.assertTrue(!RealmStructuresV6.place(GeometryProfile.TALL, safe, SEED, IslandWorld.TENSION_WORLD,
                h.getLevel().getStructureManager(), h.getLevel().registryAccess(), template, 0, 35)
                && before == fingerprint(safe), "Placement clears existing content");
        h.succeed();
    }
    private static int count(Container inventory) {
        int result = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) result += inventory.getItem(i).getCount();
        return result;
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void upstreamLootrSeparatesPlayersAndNativeSaveKeepsTheirIndividualClaims(GameTestHelper h) {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("lootr:lootr_barrel"));
        h.assertTrue(block != Blocks.AIR, "Pinned Lootr dependency absent");
        var local = new BlockPos(2, 2, 2); h.setBlock(local, block);
        var barrel = (LootrBarrelBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(local));
        barrel.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, RealmStructuresV5.Family.OBSERVATION_POST.lootTable()), SEED);
        var first = TestPlayers.create(h, new BlockPos(2, 2, 3), GameType.SURVIVAL);
        var second = TestPlayers.create(h, new BlockPos(3, 2, 3), GameType.SURVIVAL);
        try {
            var a = LootrAPI.getInventory(barrel, first); var b = LootrAPI.getInventory(barrel, second);
            h.assertTrue(a != b && count(a) >= 2 && count(b) >= 2, "Players share an inventory or do not receive actual native loot");
            int secondCount = count(b); a.clearContent(); a.setChanged();
            h.assertTrue(count(b) == secondCount && LootrAPI.getInventory(barrel, first).isEmpty(), "Taking A's loot emptied B's cache or refilled A");
            var saved = ((LootrSavedData)LootrAPI.getData(barrel)).save(new CompoundTag(), h.getLevel().registryAccess());
            var loaded = LootrSavedData.load(saved, h.getLevel().registryAccess());
            h.assertTrue(loaded.getInventory(first.getUUID()).isEmpty() && count(loaded.getInventory(second.getUUID())) == secondCount,
                    "Native upstream serialization forgot per-player claims");
            System.out.println("ETHEREAL_LOOTR_TWO_PLAYERS container=" + barrel.getInfoUUID() + " first=0 second=" + secondCount + " native_nbt_reload=true");
        } finally { TestPlayers.remove(first); TestPlayers.remove(second); }
        h.succeed();
    }
}
