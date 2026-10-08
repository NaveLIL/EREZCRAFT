package pro.erez.interstice.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.RealmBiomes;
import pro.erez.interstice.worldgen.StoneVaults;
import pro.erez.interstice.worldgen.VaultMaterials;

/** Separate worlds keep mutable feature checks away from baseline density measurements. */
@GameTestHolder("interstice_vaults")
@PrefixGameTestTemplate(false)
public final class StoneVaultGameTests {
    private static ServerLevel world(GameTestHelper h, GeometryProfile profile) {
        return java.util.Objects.requireNonNull(h.getLevel().getServer().getLevel(
                profile.equals(GeometryProfile.TALL) ? IslandWorld.TALL_WORLD : IslandWorld.WORLD));
    }
    private static ProtoChunk island(GameTestHelper h, GeometryProfile profile, int floor) {
        var chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY,
                LevelHeightAccessor.create(profile.minY(), profile.height()),
                h.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null);
        var biome = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(RealmBiomes.STONE_VAULTS);
        chunk.fillBiomesFromNoise((x, y, z, sampler) -> biome, Climate.empty());
        // The real chunk pipeline advances status after filling palettes. A synthetic
        // fixture must do the same before ProtoChunk permits biome queries.
        chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
            chunk.setBlockState(new BlockPos(x, floor, z), Interstice.ABYSSAL_TURF.get().defaultBlockState(), false);
        return chunk;
    }
    private static BlockState[] snapshot(ChunkAccess chunk, GeometryProfile profile) {
        var states = new BlockState[16 * 16 * profile.height()]; int index = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = profile.minY(); y < profile.maxYExclusive(); y++)
            states[index++] = chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z));
        return states;
    }
    @GameTest(templateNamespace = "interstice_vaults", template = "empty", timeoutTicks = 100)
    public static void biomeSelectionUsesWorldSeedAndBothProfiles(GameTestHelper h) {
        for (var profile : List.of(GeometryProfile.LEGACY, GeometryProfile.TALL)) {
            var level = world(h, profile); var generator = (IslandChunkGenerator)level.getChunkSource().getGenerator();
            var source = generator.getBiomeSource();
            h.assertTrue(source.possibleBiomes().stream().anyMatch(holder -> holder.is(RealmBiomes.STONE_VAULTS)), "Runtime biome source omits Stone Vaults");
            h.assertTrue(source.possibleBiomes().stream().anyMatch(holder -> holder.is(RealmBiomes.ASH_ISLANDS)), "Runtime biome source omits the preserved ash islands");
            var settings = generator.generatorSettings().value();
            var lookup = level.registryAccess().registryOrThrow(Registries.NOISE).asLookup();
            var first = RandomState.create(settings, lookup, 20261006L);
            var repeated = RandomState.create(settings, lookup, 20261006L);
            var second = RandomState.create(settings, lookup, 76198123L);
            int vaults = 0, ash = 0, changed = 0;
            for (int x = -1024; x <= 1024; x += 64) for (int z = -1024; z <= 1024; z += 64) {
                int qx = QuartPos.fromBlock(x), qy = QuartPos.fromBlock(profile.minLand() + 8), qz = QuartPos.fromBlock(z);
                var a = source.getNoiseBiome(qx, qy, qz, first.sampler());
                h.assertTrue(a.equals(source.getNoiseBiome(qx, qy, qz, repeated.sampler())), "Recreating the same seed changed biome selection");
                h.assertTrue(a.is(RealmBiomes.STONE_VAULTS) || a.is(RealmBiomes.ASH_ISLANDS) || a.is(RealmBiomes.PALE_GARDENS), "Foreign biome selected in the realm");
                if (a.is(RealmBiomes.STONE_VAULTS)) vaults++; else ash++;
                if (!a.equals(source.getNoiseBiome(qx, qy, qz, second.sampler()))) changed++;
            }
            h.assertTrue(vaults > 0 && ash > 0 && changed > 0, "Biomes must form distinct regions and respond to the world seed");
            System.out.println("VAULT_BIOMES height=" + profile.height() + " vault_samples=" + vaults + " ash_samples=" + ash + " seed_differences=" + changed);
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "interstice_vaults", template = "empty", timeoutTicks = 100)
    public static void legacyBiomeCodecUpgradePreservesGeometryAndCustomSources(GameTestHelper h) {
        var level = world(h, GeometryProfile.TALL); var current = (IslandChunkGenerator)level.getChunkSource().getGenerator();
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        var ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, level.registryAccess());
        for (var profile : List.of(GeometryProfile.LEGACY, GeometryProfile.TALL)) {
            var original = (IslandChunkGenerator)world(h, profile).getChunkSource().getGenerator();
            var old = new IslandChunkGenerator(new FixedBiomeSource(registry.getHolderOrThrow(Biomes.THE_END)), original.generatorSettings(), profile);
            var serialized = IslandChunkGenerator.CODEC.codec().encodeStart(ops, old).getOrThrow();
            var restored = IslandChunkGenerator.CODEC.codec().parse(ops, serialized).getOrThrow();
            h.assertTrue(restored.geometry().equals(profile), "Placeholder-biome upgrade changed geometry bounds");
            h.assertTrue(restored.getBiomeSource().possibleBiomes().stream().anyMatch(b -> b.is(RealmBiomes.STONE_VAULTS)), "Old End placeholder was not upgraded");
            var random = world(h, profile).getChunkSource().randomState();
            for (int x : new int[]{-33, 0, 17, 100}) {
                var a = old.getBaseColumn(x, 7, world(h, profile), random); var b = restored.getBaseColumn(x, 7, world(h, profile), random);
                for (int y = profile.minY(); y < profile.maxYExclusive(); y++)
                    h.assertTrue(a.getBlock(y).equals(b.getBlock(y)), "Biome codec upgrade changed island density or the seas");
            }
        }
        var custom = new IslandChunkGenerator(new FixedBiomeSource(registry.getHolderOrThrow(Biomes.PLAINS)), current.generatorSettings(), GeometryProfile.TALL);
        var restored = IslandChunkGenerator.CODEC.codec().parse(ops, IslandChunkGenerator.CODEC.codec().encodeStart(ops, custom).getOrThrow()).getOrThrow();
        h.assertTrue(restored.getBiomeSource() instanceof FixedBiomeSource && restored.getBiomeSource().possibleBiomes().iterator().next().is(Biomes.PLAINS), "A custom biome source must remain under its author's control");
        h.succeed();
    }
    @GameTest(templateNamespace = "interstice_vaults", template = "empty", timeoutTicks = 100)
    public static void vaultFormsHaveConnectedSupportsAndWalkableShelteredCenters(GameTestHelper h) {
        var root = new BlockPos(7, 56, 7); var forms = new ArrayList<Map<BlockPos, BlockState>>();
        for (int shape = 0; shape <= 2; shape++) for (int height = 5; height <= 7; height++) for (boolean alongX : new boolean[]{true, false}) {
            var plan = StoneVaults.plan(root, height, alongX, shape);
            if (height == 6 && alongX) forms.add(plan);
            h.assertTrue(plan.size() >= 30, "A vault needs a substantial roof and buttresses");
            var reached = new HashSet<BlockPos>(); var queue = new ArrayDeque<BlockPos>();
            plan.keySet().stream().filter(p -> p.getY() == root.getY()).forEach(p -> { reached.add(p); queue.add(p); });
            h.assertTrue(!queue.isEmpty(), "Vault has no ground supports");
            while (!queue.isEmpty()) {
                var p = queue.remove();
                for (var direction : Direction.values()) {
                    var neighbor = p.relative(direction);
                    if (plan.containsKey(neighbor) && reached.add(neighbor)) queue.add(neighbor);
                }
            }
            h.assertTrue(reached.size() == plan.size(), "Roof pieces must connect by block faces to the ground supports, shape=" + shape);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                h.assertTrue(!plan.containsKey(root.offset(dx, 0, dz)) && !plan.containsKey(root.offset(dx, 1, dz)), "Vault center must allow a player to walk underneath");
                boolean roof = false;
                for (int dy = 2; dy <= height + 1; dy++) {
                    var state = plan.get(root.offset(dx, dy, dz));
                    if (state != null && ShelterDetector.isShelteringBlock(state)) roof = true;
                }
                h.assertTrue(roof, "The central 3x3 passage needs a physical tide shelter roof");
            }
        }
        h.assertTrue(!forms.get(0).equals(forms.get(1)) && !forms.get(1).equals(forms.get(2)) && !forms.get(0).equals(forms.get(2)), "Three vault forms must differ geometrically");
        h.succeed();
    }
    @GameTest(templateNamespace = "interstice_vaults", template = "empty", timeoutTicks = 100)
    public static void placementRejectsBlockersUnsupportedGroundAndChunkEdgesAtomically(GameTestHelper h) {
        for (var profile : List.of(GeometryProfile.LEGACY, GeometryProfile.TALL)) {
            int floor = profile.minLand() + 8; var root = new BlockPos(7, floor + 1, 7);
            for (int failure = 0; failure < 3; failure++) {
                var chunk = island(h, profile, floor); var placement = root;
                if (failure == 0) chunk.setBlockState(root.above(), Blocks.CHEST.defaultBlockState(), false);
                if (failure == 1) chunk.setBlockState(root.below(), Blocks.AIR.defaultBlockState(), false);
                if (failure == 2) placement = new BlockPos(2, floor + 1, 7);
                var before = snapshot(chunk, profile);
                h.assertTrue(!StoneVaults.place(profile, chunk, placement, 6, true, 0), "Invalid complete footprint must prevent placement, case=" + failure);
                h.assertTrue(Arrays.equals(before, snapshot(chunk, profile)), "Rejected vault must not leave partial blocks or overwrite content");
            }
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "interstice_vaults", template = "empty", timeoutTicks = 100)
    public static void vaultPlacementRespectsBothSeaClearancesAndKeepsTheOpening(GameTestHelper h) {
        for (var profile : List.of(GeometryProfile.LEGACY, GeometryProfile.TALL)) {
            int floor = profile.minLand() + 8; var root = new BlockPos(7, floor + 1, 7); var chunk = island(h, profile, floor);
            h.assertTrue(StoneVaults.place(profile, chunk, root, 6, true, 0), "Eligible island must accept the vault");
            for (var entry : StoneVaults.plan(root, 6, true, 0).entrySet()) {
                var p = entry.getKey();
                h.assertTrue(chunk.getBlockState(p).equals(entry.getValue()), "Complete vault was not written");
                h.assertTrue(IslandChunkGenerator.landAllowed(profile, p.getX(), p.getY(), p.getZ()), "Vault breached a toxic-sea clearance band");
            }
            h.assertTrue(chunk.getBlockState(root).isAir() && chunk.getBlockState(root.above()).isAir(), "Footprint support must not fill the open passage");
            var edge = island(h, profile, profile.maxLand() - 2); var before = snapshot(edge, profile);
            h.assertTrue(!StoneVaults.place(profile, edge, new BlockPos(7, profile.maxLand() - 1, 7), 7, true, 2), "High roof must be rejected near the upper sea");
            h.assertTrue(Arrays.equals(before, snapshot(edge, profile)), "Clearance rejection left partial rock");
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "interstice_vaults", template = "empty", timeoutTicks = 100)
    public static void geologicalSurfacePreservesRiftsilverAndTheOriginalFloraPockets(GameTestHelper h) {
        var profile = GeometryProfile.TALL; var chunk = island(h, profile, 100);
        var ore = new BlockPos(7, 100, 7); chunk.setBlockState(ore, Interstice.RIFTSILVER_ORE.get().defaultBlockState(), false);
        var plant = new BlockPos(1, 101, 1); chunk.setBlockState(plant, Interstice.TIDE_SPROUT.get().defaultBlockState(), false);
        StoneVaults.geologicalSurface(profile, chunk, 20261006L);
        h.assertTrue(chunk.getBlockState(ore).is(Interstice.RIFTSILVER_ORE.get()), "Geological surface conversion must preserve accessible expedition ore");
        h.assertTrue(chunk.getBlockState(plant).is(Interstice.TIDE_SPROUT.get()) && chunk.getBlockState(plant.below()).is(Interstice.ABYSSAL_TURF.get()), "Surface conversion must preserve existing authored flora and its living support");
        int rock = 0, pockets = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            var state = chunk.getBlockState(new BlockPos(x, 100, z));
            if (state.is(VaultMaterials.WEATHERED_VAULTSTONE.get()) || state.is(VaultMaterials.VAULTSTONE.get())) rock++;
            if (state.is(Interstice.ABYSSAL_TURF.get())) pockets++;
        }
        h.assertTrue(rock > 0, "Vault biome must expose its own geological surface");
        // Wider coordinates are covered by natural generation; this local patch need not intersect a pocket.
        System.out.println("VAULT_SURFACE rock=" + rock + " preserved_turf=" + pockets);
        h.succeed();
    }
    @GameTest(templateNamespace = "interstice_vaults", template = "empty", timeoutTicks = 600)
    public static void naturalNewChunksContainVaultsInLegacyAndTallWorlds(GameTestHelper h) {
        for (var profile : List.of(GeometryProfile.LEGACY, GeometryProfile.TALL)) {
            var level = world(h, profile); var generator = (IslandChunkGenerator)level.getChunkSource().getGenerator();
            var random = level.getChunkSource().randomState(); int found = 0;
            for (int radius = 0; radius <= 16 && found == 0; radius++) for (int cx = -radius; cx <= radius && found == 0; cx++) for (int cz = -radius; cz <= radius && found == 0; cz++) {
                if (Math.max(Math.abs(cx), Math.abs(cz)) != radius || !StoneVaults.candidate(level.getSeed(), cx, cz)) continue;
                int centerX = cx * 16 + 7, centerZ = cz * 16 + 7;
                if (!generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(centerX), QuartPos.fromBlock(profile.minLand() + 8), QuartPos.fromBlock(centerZ), random.sampler()).is(RealmBiomes.STONE_VAULTS)) continue;
                var chunk = level.getChunk(cx, cz); int additiveRock = 0;
                for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                    var column = generator.getBaseColumn(x, z, level, random);
                    for (int y = profile.minLand(); y <= profile.maxLand(); y++) {
                        var p = new BlockPos(x, y, z); var state = chunk.getBlockState(p);
                        if (state.is(VaultMaterials.VAULTSTONE.get()) || state.is(VaultMaterials.WEATHERED_VAULTSTONE.get())) {
                            h.assertTrue(IslandChunkGenerator.landAllowed(profile, x, y, z), "Natural vault rock violates the sea-clearance contract");
                            if (column.getBlock(y).isAir()) additiveRock++;
                        }
                    }
                }
                if (additiveRock > 20) {
                    found++;
                    System.out.println("NATURAL_STONE_VAULT height=" + profile.height() + " chunk=" + chunk.getPos() + " additive_rock=" + additiveRock);
                }
            }
            h.assertTrue(found > 0, "The actual surface pipeline did not create a natural Stone Vault within 16 chunks, height=" + profile.height());
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "interstice_vaults", template = "empty", timeoutTicks = 100)
    public static void buildingMaterialsHaveToolTagsRealLootAndWorkingRecipes(GameTestHelper h) {
        var blocks = List.<Block>of(VaultMaterials.VAULTSTONE.get(), VaultMaterials.WEATHERED_VAULTSTONE.get(), VaultMaterials.POLISHED_VAULTSTONE.get(), VaultMaterials.VAULTSTONE_BRICKS.get(),
                VaultMaterials.VAULTSTONE_SLAB.get(), VaultMaterials.VAULTSTONE_STAIRS.get(), VaultMaterials.VAULTSTONE_WALL.get(),
                VaultMaterials.POLISHED_VAULTSTONE_SLAB.get(), VaultMaterials.POLISHED_VAULTSTONE_STAIRS.get(), VaultMaterials.POLISHED_VAULTSTONE_WALL.get(),
                VaultMaterials.VAULTSTONE_BRICKS_SLAB.get(), VaultMaterials.VAULTSTONE_BRICKS_STAIRS.get(), VaultMaterials.VAULTSTONE_BRICKS_WALL.get());
        var tool = new ItemStack(Items.IRON_PICKAXE); var point = h.absolutePos(new BlockPos(1, 1, 1));
        for (var block : blocks) {
            var state = block.defaultBlockState();
            h.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE) && tool.isCorrectToolForDrops(state), "New building material lacks a usable mining tool tag: " + block);
            var drops = Block.getDrops(state, h.getLevel(), point, null, null, tool);
            h.assertTrue(drops.stream().anyMatch(stack -> stack.is(block.asItem()) && stack.getCount() == 1), "Building material has no real self-drop loot table: " + block);
            if (block instanceof SlabBlock) {
                var doubleDrops = Block.getDrops(state.setValue(SlabBlock.TYPE, SlabType.DOUBLE), h.getLevel(), point, null, null, tool);
                h.assertTrue(doubleDrops.stream().anyMatch(stack -> stack.is(block.asItem()) && stack.getCount() == 2), "Double slabs must return two items");
            }
        }
        crafted(h, 2, 2, repeated(VaultMaterials.VAULTSTONE.get(), 4), VaultMaterials.POLISHED_VAULTSTONE.get(), 4);
        crafted(h, 2, 2, repeated(VaultMaterials.POLISHED_VAULTSTONE.get(), 4), VaultMaterials.VAULTSTONE_BRICKS.get(), 4);
        for (var family : List.of(List.<Block>of(VaultMaterials.VAULTSTONE.get(), VaultMaterials.VAULTSTONE_SLAB.get(), VaultMaterials.VAULTSTONE_STAIRS.get(), VaultMaterials.VAULTSTONE_WALL.get()),
                List.<Block>of(VaultMaterials.POLISHED_VAULTSTONE.get(), VaultMaterials.POLISHED_VAULTSTONE_SLAB.get(), VaultMaterials.POLISHED_VAULTSTONE_STAIRS.get(), VaultMaterials.POLISHED_VAULTSTONE_WALL.get()),
                List.<Block>of(VaultMaterials.VAULTSTONE_BRICKS.get(), VaultMaterials.VAULTSTONE_BRICKS_SLAB.get(), VaultMaterials.VAULTSTONE_BRICKS_STAIRS.get(), VaultMaterials.VAULTSTONE_BRICKS_WALL.get()))) {
            var base = family.get(0);
            crafted(h, 3, 1, repeated(base, 3), family.get(1), 6);
            crafted(h, 3, 3, List.of(new ItemStack(base), ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(base), new ItemStack(base), ItemStack.EMPTY, new ItemStack(base), new ItemStack(base), new ItemStack(base)), family.get(2), 4);
            crafted(h, 3, 2, repeated(base, 6), family.get(3), 6);
        }
        h.succeed();
    }
    private static List<ItemStack> repeated(Block block, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> new ItemStack(block)).toList();
    }
    private static void crafted(GameTestHelper h, int width, int height, List<ItemStack> input, Block expected, int count) {
        var grid = CraftingInput.of(width, height, input);
        var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel());
        h.assertTrue(recipe.isPresent(), "Missing usable crafting recipe for " + expected);
        var result = recipe.orElseThrow().value().assemble(grid, h.getLevel().registryAccess());
        h.assertTrue(result.is(expected.asItem()) && result.getCount() == count, "Crafting recipe returned the wrong building result for " + expected);
    }
}
