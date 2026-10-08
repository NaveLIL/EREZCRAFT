package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.FarmHydration;
import pro.erez.interstice.agriculture.NutrientReservoirBlock;
import pro.erez.interstice.agriculture.NutrientReservoirEntity;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.toxin.OverworldToxinHazard;
import pro.erez.interstice.worldgen.NativeCropPatches;
import pro.erez.interstice.worldgen.RealmBiomes;
import pro.erez.interstice.worldgen.terrain.FreeTerraNoise;

/** Integration checks at the boundaries of the chemical farm, beyond its ordinary happy path. */
@GameTestHolder("interstice_agriculture")
@PrefixGameTestTemplate(false)
public final class FarmingIntegrityGameTests {
    private static final BlockPos SOIL = new BlockPos(6, 1, 6);

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void actualOverworldCorrosionSparesOwnCropsAndStillDestroysEarthCrops(GameTestHelper h) {
        var level = h.getLevel();
        h.assertTrue(OverworldToxinHazard.isHazardousDimension(level), "Fixture is not a foreign ecosystem");
        h.setBlock(SOIL, Interstice.HEAVY_BLOCK.get());
        h.setBlock(SOIL.east(), RealmAgriculture.FARMLAND.get());
        h.setBlock(SOIL.east().above(), RealmAgriculture.ROOT_CROP.get().getStateForAge(3));
        h.setBlock(SOIL.west(), Blocks.FARMLAND);
        h.setBlock(SOIL.west().above(), Blocks.WHEAT);
        var random = RandomSource.create(20261008L);
        for (int i = 0; i < 240; i++) OverworldToxinHazard.corrodeEnvironment(level, h.absolutePos(SOIL), random);
        h.assertTrue(level.getBlockState(h.absolutePos(SOIL.east().above())).is(RealmAgriculture.ROOT_CROP.get()),
                "Native crop tagged as crops was destroyed by its own irrigation outside the realm");
        h.assertTrue(!level.getBlockState(h.absolutePos(SOIL.west().above())).is(Blocks.WHEAT),
                "Crop adaptation accidentally disabled corrosion of vanilla plants");
        h.assertTrue(!level.getBlockState(h.absolutePos(SOIL.west())).is(Blocks.FARMLAND),
                "Crop adaptation accidentally protected terrestrial soil");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void flowingRedNutritionObeysHorizontalAndVerticalBounds(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(SOIL);
        var cropPos = SOIL.above();
        var crop = RealmAgriculture.GRAIN_CROP.get();
        h.setBlock(SOIL, RealmAgriculture.FARMLAND.get());
        h.setBlock(cropPos, crop);
        var edge = SOIL.offset(4, 1, 4);
        h.setBlock(edge, Interstice.HEAVY.get().getFlowing(3, false).createLegacyBlock());
        h.assertTrue(FarmHydration.scan(level, pos) == FarmHydration.State.WET,
                "Supported red flow at the inclusive irrigation corner is ignored");
        h.assertTrue(crop.canGrow(level, h.absolutePos(cropPos), level.getBlockState(h.absolutePos(cropPos))),
                "Flow irrigates the soil but cannot nourish its crop");
        h.setBlock(edge, Blocks.AIR);
        h.setBlock(SOIL.offset(5, 0, 0), Interstice.HEAVY_BLOCK.get());
        h.assertTrue(FarmHydration.scan(level, pos) != FarmHydration.State.WET, "Red fluid five blocks away bypasses the radius");
        h.setBlock(SOIL.offset(5, 0, 0), Blocks.AIR);
        h.setBlock(SOIL.offset(3, 2, 0), Interstice.HEAVY_BLOCK.get());
        h.assertTrue(FarmHydration.scan(level, pos) != FarmHydration.State.WET, "Fluid two blocks above soil bypasses vertical range");
        h.setBlock(SOIL.offset(3, 2, 0), Blocks.AIR);
        h.setBlock(SOIL.offset(3, -1, 0), Interstice.HEAVY_BLOCK.get());
        h.assertTrue(FarmHydration.scan(level, pos) != FarmHydration.State.WET, "Sealed fluid below the root plane incorrectly irrigates");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reservoirUsesSavedAmountAndDrainReturnsExactlyOneExistingContainer(GameTestHelper h) {
        var level = h.getLevel();
        var local = SOIL.east(2);
        var pos = h.absolutePos(local);
        h.setBlock(SOIL, RealmAgriculture.FARMLAND.get());
        h.setBlock(local, RealmAgriculture.RESERVOIR.get().defaultBlockState().setValue(NutrientReservoirBlock.FILLED, true));
        var reservoir = (NutrientReservoirEntity) level.getBlockEntity(pos);
        h.assertTrue(reservoir.amount() == 0 && FarmHydration.scan(level, h.absolutePos(SOIL)) != FarmHydration.State.WET,
                "Decorative filled state creates liquid without saved contents");
        var player = TestPlayers.create(h, new BlockPos(8, 2, 5), GameType.SURVIVAL);
        try {
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Interstice.RIFTSILVER_WATER_BUCKET.get()));
            level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(reservoir.amount() == 0 && player.getMainHandItem().is(Interstice.RIFTSILVER_WATER_BUCKET.get()),
                    "Reservoir accepts water or consumes a rejected filled bucket");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Interstice.RIFTSILVER_HEAVY_BUCKET.get()));
            level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(reservoir.amount() == 1000 && player.getMainHandItem().is(Interstice.RIFTSILVER_BUCKET.get()),
                    "Accepted fill did not retain exactly one real bucket of red fluid");
            var extra = new ItemStack(Interstice.RIFTSILVER_HEAVY_BUCKET.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, extra);
            level.getBlockState(pos).useItemOn(extra, level, player, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(reservoir.amount() == 1000 && extra.getCount() == 1,
                    "Full reservoir absorbs a second bucket or manufactures capacity");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Interstice.RIFTSILVER_BUCKET.get()));
            level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(reservoir.amount() == 0 && player.getMainHandItem().is(Interstice.RIFTSILVER_HEAVY_BUCKET.get()),
                    "Drain did not return red fluid in the existing silver container");
            h.assertTrue(FarmHydration.scan(level, h.absolutePos(SOIL)) != FarmHydration.State.WET,
                    "Drained reservoir still nourishes crops through stale visual state");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Interstice.RIFTSILVER_BUCKET.get()));
            level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(reservoir.amount() == 0 && player.getMainHandItem().is(Interstice.RIFTSILVER_BUCKET.get()),
                    "Second drain creates fluid from an empty reservoir");
            h.succeed();
        } finally { TestPlayers.remove(player); }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void immatureCultivatorDoesNotDestroyAdvanceOrConsumeAnything(GameTestHelper h) {
        h.setBlock(SOIL, RealmAgriculture.FARMLAND.get());
        h.setBlock(SOIL.above(), RealmAgriculture.ROOT_CROP.get().getStateForAge(2));
        var level = h.getLevel();
        var pos = h.absolutePos(SOIL.above());
        var player = TestPlayers.create(h, new BlockPos(6, 2, 5), GameType.SURVIVAL);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RealmAgriculture.CULTIVATOR.get()));
            player.getInventory().setItem(1, new ItemStack(RealmAgriculture.ROOT.get(), 7));
            player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
            h.assertTrue(level.getBlockState(pos).is(RealmAgriculture.ROOT_CROP.get())
                            && RealmAgriculture.ROOT_CROP.get().getAge(level.getBlockState(pos)) == 2,
                    "Cultivator modifies an immature plant");
            h.assertTrue(player.getMainHandItem().getDamageValue() == 0 && player.getInventory().getItem(1).getCount() == 7,
                    "Rejected immature harvest consumes durability or planting inventory");
            h.succeed();
        } finally { TestPlayers.remove(player); }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oneCellShoreDoesNotCreateHalfASeedColonyOrMutateItsGround(GameTestHelper h) {
        long seed = 0;
        long key;
        do { key = FreeTerraNoise.mix(seed ^ 0x4147524950415443L); if (Math.floorMod(key, 12) == 0) break; seed++; } while (seed < 1000);
        h.assertTrue(seed < 1000, "No deterministic colony gate fixture found");
        var random = RandomSource.create(key);
        int x = 2 + random.nextInt(12), z = 2 + random.nextInt(12);
        var soil = new BlockPos(x, GeometryProfile.TALL.lowerSeaTop(), z);
        var level = h.getLevel();
        var chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, LevelHeightAccessor.create(0, 256),
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        var garden = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(RealmBiomes.PALE_GARDENS);
        chunk.fillBiomesFromNoise((bx, by, bz, sampler) -> garden, Climate.empty());
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
        for (int px = 0; px < 16; px++) for (int pz = 0; pz < 16; pz++) {
            chunk.setBlockState(new BlockPos(px, soil.getY(), pz), MineralEcology.ROOT_LOAM.get().defaultBlockState(), false);
            chunk.setBlockState(new BlockPos(px, soil.getY() + 1, pz), Interstice.RIFTSTONE.get().defaultBlockState(), false);
        }
        chunk.setBlockState(soil.above(), Blocks.AIR.defaultBlockState(), false);
        chunk.setBlockState(soil.east(2), Interstice.HEAVY_BLOCK.get().defaultBlockState(), false);
        int placed = NativeCropPatches.generate(GeometryProfile.TALL, chunk, seed, 4);
        h.assertTrue(placed == 0, "A one-cell shore generates an incomplete colony without both crop types");
        h.assertTrue(chunk.getBlockState(soil).is(MineralEcology.ROOT_LOAM.get()) && chunk.getBlockState(soil.above()).isAir(),
                "Rejected incomplete colony still writes cultivated ground or a free planting item");
        h.succeed();
    }
}
