package pro.erez.interstice.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
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
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.WatchpostRuins;

@GameTestHolder("interstice_expeditions")
@PrefixGameTestTemplate(false)
public final class WatchpostGameTests {
    private static final long SEED = 20261006L;
    private static ChunkPos candidate() {
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) if (WatchpostRuins.candidate(SEED, x, z)) return new ChunkPos(x, z);
        throw new AssertionError("Missing candidate");
    }
    private static ProtoChunk island(GameTestHelper h, ChunkPos pos, GeometryProfile profile, int floor) {
        var chunk = new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(profile.minY(), profile.height()), h.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) chunk.setBlockState(new BlockPos(pos.getMinBlockX() + x, floor, pos.getMinBlockZ() + z), Interstice.ABYSSAL_TURF.get().defaultBlockState(), false);
        return chunk;
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void authoredRuinPlacesWithLootAndPreservesItOnRepeat(GameTestHelper h) {
        var profile = GeometryProfile.TALL; var pos = candidate(); var chunk = island(h, pos, profile, 160);
        h.assertTrue(WatchpostRuins.generate(profile, chunk, SEED, h.getLevel().getStructureManager(), h.getLevel().registryAccess()), "Authored ruin must place on eligible fresh terrain");
        BlockPos barrel = null; int count = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 161; y < 168; y++) {
            BlockPos point = new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z); var state = chunk.getBlockState(point);
            if (!state.isAir()) { count++; h.assertTrue(IslandChunkGenerator.landAllowed(profile, point.getX(), y, point.getZ()), "Ruin must respect toxic sea clearance"); }
            if (state.is(Blocks.BARREL)) barrel = point;
        }
        h.assertTrue(count == 184 && barrel != null, "All source-template blocks and one barrel must be present");
        var before = chunk.getBlockEntityNbt(barrel).copy();
        h.assertTrue(before.getString("LootTable").equals("interstice:chests/watchpost_ruin"), "Barrel must carry the real expedition loot table");
        h.assertTrue(!WatchpostRuins.generate(profile, chunk, SEED, h.getLevel().getStructureManager(), h.getLevel().registryAccess()), "Re-running generation on a completed ruin must not overwrite it");
        h.assertTrue(before.equals(chunk.getBlockEntityNbt(barrel)), "Re-running generation must preserve the barrel and its loot seed"); h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void ruinRejectsObstructionAndSeaClearanceViolations(GameTestHelper h) {
        var pos = candidate(); var profile = GeometryProfile.LEGACY; var chunk = island(h, pos, profile, profile.maxLand() - 1);
        h.assertTrue(!WatchpostRuins.generate(profile, chunk, SEED, h.getLevel().getStructureManager(), h.getLevel().registryAccess()), "A roof too close to the upper sea must be rejected");
        chunk = island(h, pos, GeometryProfile.TALL, 160);
        BlockPos obstacle = new BlockPos(pos.getMinBlockX() + 8, 162, pos.getMinBlockZ() + 8);
        chunk.setBlockState(obstacle, Blocks.CHEST.defaultBlockState(), false);
        h.assertTrue(!WatchpostRuins.generate(GeometryProfile.TALL, chunk, SEED, h.getLevel().getStructureManager(), h.getLevel().registryAccess()), "Existing non-terrain content must prevent overwriting");
        h.assertTrue(chunk.getBlockState(obstacle).is(Blocks.CHEST), "Obstruction must remain intact"); h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void ruinCandidatesAreDeterministicInNegativeRegions(GameTestHelper h) {
        for (int rx = -2; rx <= 2; rx++) for (int rz = -2; rz <= 2; rz++) {
            int count = 0;
            for (int x = rx * 4; x < rx * 4 + 4; x++) for (int z = rz * 4; z < rz * 4 + 4; z++) if (WatchpostRuins.candidate(SEED, x, z)) count++;
            h.assertTrue(count == 1, "Every 4x4 region must have exactly one deterministic candidate");
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void generatedCacheLootIsUsefulAndDoesNotRefillAfterLoad(GameTestHelper h) {
        BlockPos local = new BlockPos(3, 2, 3); h.setBlock(local, Blocks.BARREL);
        var barrel = (BarrelBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(local));
        barrel.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(Interstice.ID, "chests/watchpost_ruin")), SEED);
        int membrane = 0;
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) if (barrel.getItem(slot).is(Interstice.PRESSURE_COUPLER.get())) membrane += barrel.getItem(slot).getCount();
        h.assertTrue(membrane == 1, "A real cache must guarantee exactly one expedition component");
        barrel.clearContent(); var data = barrel.saveWithFullMetadata(h.getLevel().registryAccess());
        var reload = new BarrelBlockEntity(h.absolutePos(local), Blocks.BARREL.defaultBlockState()); reload.setLevel(h.getLevel()); reload.loadWithComponents(data, h.getLevel().registryAccess());
        h.assertTrue(reload.isEmpty(), "Claimed cache must remain empty after block-entity reload");
        var input = CraftingInput.of(3, 3, List.of(new ItemStack(Items.COPPER_INGOT), new ItemStack(Interstice.PRESSURE_COUPLER.get()), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.COMPASS), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.COPPER_INGOT)));
        var output = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow().value().assemble(input, h.getLevel().registryAccess());
        h.assertTrue(output.is(Interstice.TIDE_INDICATOR.get()), "The recovered component must craft the functioning indicator item"); h.succeed();
    }
}
