package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.tide.BuoyancyController;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideState;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class BuoyancyGameTests {

    private static ServerLevel islandWorld(GameTestHelper h) {
        return java.util.Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD));
    }

    private static void clearTestColumn(ServerLevel world, int centerX, int centerZ, int radius, int minY, int maxY) {
        world.getChunk(centerX >> 4, centerZ >> 4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (!world.getBlockState(pos).isAir()) {
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shelterDetectsVariousRoofMaterials(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 320, 320, 2, 101, 215);
        Zombie entity = EntityType.ZOMBIE.create(world);
        h.assertTrue(entity != null, "Failed to create test zombie");
        entity.moveTo(320.5, 100.0, 320.5, 0, 0);

        BlockPos roofPos = new BlockPos(320, 104, 320);

        // 1. Artificial solid roof (Cobblestone)
        world.setBlock(roofPos, Blocks.COBBLESTONE.defaultBlockState(), 3);
        h.assertTrue(ShelterDetector.isSheltered(world, entity), "Cobblestone roof must shelter entity");

        // 2. Glass roof (TransparentBlock)
        world.setBlock(roofPos, Blocks.GLASS.defaultBlockState(), 3);
        h.assertTrue(ShelterDetector.isSheltered(world, entity), "Glass roof must shelter entity");

        // 3. Stained Glass
        world.setBlock(roofPos, Blocks.TINTED_GLASS.defaultBlockState(), 3);
        h.assertTrue(ShelterDetector.isSheltered(world, entity), "Tinted glass roof must shelter entity");

        // 4. Slab (Bottom slab and Top slab)
        world.setBlock(roofPos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
        h.assertTrue(ShelterDetector.isSheltered(world, entity), "Bottom slab roof must shelter entity");
        world.setBlock(roofPos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 3);
        h.assertTrue(ShelterDetector.isSheltered(world, entity), "Top slab roof must shelter entity");

        // 5. Leaves (Foliage canopy)
        world.setBlock(roofPos, Blocks.OAK_LEAVES.defaultBlockState(), 3);
        h.assertTrue(ShelterDetector.isSheltered(world, entity), "Leaves roof must shelter entity");

        // Cleanup
        world.setBlock(roofPos, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(!ShelterDetector.isSheltered(world, entity), "Air above must NOT shelter entity");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shelterDetectsHoleAndEdgeOverhang(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 340, 320, 3, 101, 215);
        Zombie entity = EntityType.ZOMBIE.create(world);
        h.assertTrue(entity != null, "Failed to create test zombie");

        // Place entity centered at (340.5, 100.0, 320.5)
        entity.moveTo(340.5, 100.0, 320.5, 0, 0);

        // Build a 3x3 roof at Y=104 except a hole in the center (340, 104, 320)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = new BlockPos(340 + dx, 104, 320 + dz);
                if (dx == 0 && dz == 0) {
                    world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                } else {
                    world.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        // Entity directly under hole is exposed
        h.assertTrue(!ShelterDetector.isSheltered(world, entity), "Hole in roof must expose entity");

        // Move entity under solid section of roof at (339.5, 100.0, 320.5)
        entity.moveTo(339.5, 100.0, 320.5, 0, 0);
        h.assertTrue(ShelterDetector.isSheltered(world, entity), "Entity under solid section must be sheltered");

        // Move entity to overhang cliff edge at (338.9, 100.0, 320.5) where footprint extends past roof edge (X=338 has no roof)
        entity.moveTo(338.9, 100.0, 320.5, 0, 0);
        h.assertTrue(!ShelterDetector.isSheltered(world, entity), "Overhang edge exposure must NOT be sheltered");

        // Cleanup
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlock(new BlockPos(340 + dx, 104, 320 + dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shelterIgnoresFluidsAndBedrockRoof(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 360, 320, 2, 101, 215);
        Zombie entity = EntityType.ZOMBIE.create(world);
        h.assertTrue(entity != null, "Failed to create test zombie");
        entity.moveTo(360.5, 100.0, 320.5, 0, 0);

        BlockPos roofPos = new BlockPos(360, 104, 320);

        // Water pool overhead does NOT count as shelter
        world.setBlock(roofPos, Blocks.WATER.defaultBlockState(), 3);
        h.assertTrue(!ShelterDetector.isSheltered(world, entity), "Water pool must not count as shelter");

        // Toxic light sea block overhead does NOT count as shelter
        world.setBlock(roofPos, Interstice.LIGHT_SEA.get().defaultBlockState(), 3);
        h.assertTrue(!ShelterDetector.isSheltered(world, entity), "Toxic upper sea must not count as shelter");

        // Bedrock overhead does NOT count as shelter
        world.setBlock(roofPos, Blocks.BEDROCK.defaultBlockState(), 3);
        h.assertTrue(!ShelterDetector.isSheltered(world, entity), "Bedrock shell must not count as shelter");

        // Cleanup
        world.setBlock(roofPos, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shelterReactsImmediatelyToBreakingRoof(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 380, 320, 2, 101, 215);
        Zombie entity = EntityType.ZOMBIE.create(world);
        h.assertTrue(entity != null, "Failed to create test zombie");
        entity.moveTo(380.5, 100.0, 320.5, 0, 0);

        BlockPos roofPos = new BlockPos(380, 104, 320);

        // Place roof
        world.setBlock(roofPos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        h.assertTrue(ShelterDetector.isSheltered(world, entity), "Placed planks roof must shelter");

        // Break roof
        world.setBlock(roofPos, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(!ShelterDetector.isSheltered(world, entity), "Destroyed roof must immediately expose entity");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void buoyancyModifierAppliedDuringSurgeAndDecaysInEbb(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 400, 320, 2, 101, 215);
        Zombie entity = EntityType.ZOMBIE.create(world);
        h.assertTrue(entity != null, "Failed to create test zombie");
        entity.moveTo(400.5, 100.0, 320.5, 0, 0);

        var gravityAttr = entity.getAttribute(Attributes.GRAVITY);
        h.assertTrue(gravityAttr != null, "Zombie must have GRAVITY attribute");
        h.assertTrue(Math.abs(gravityAttr.getValue() - 0.08) < 1e-4, "Initial gravity must be default 0.08");

        // 1. In CALM: intensity = 0.0 -> no modifier
        BuoyancyController.applyEntityBuoyancy(entity, 0.0F);
        h.assertTrue(!gravityAttr.hasModifier(BuoyancyController.BUOYANCY_ID), "No buoyancy modifier during CALM");
        h.assertTrue(Math.abs(gravityAttr.getValue() - 0.08) < 1e-4, "Gravity remains 0.08 during CALM");

        // 2. In SURGE: intensity = 1.0 -> effective gravity becomes negative (-0.028)
        BuoyancyController.applyEntityBuoyancy(entity, 1.0F);
        h.assertTrue(gravityAttr.hasModifier(BuoyancyController.BUOYANCY_ID), "Buoyancy modifier must be present during SURGE");
        h.assertTrue(gravityAttr.getValue() < 0.0, "Effective gravity must be negative during peak SURGE; got " + gravityAttr.getValue());
        h.assertTrue(Math.abs(gravityAttr.getValue() - (-0.028)) < 1e-4, "Peak SURGE gravity should be -0.028; got " + gravityAttr.getValue());

        // 3. In EBB: intensity = 0.5 -> intermediate decayed gravity
        BuoyancyController.applyEntityBuoyancy(entity, 0.5F);
        h.assertTrue(gravityAttr.hasModifier(BuoyancyController.BUOYANCY_ID), "Buoyancy modifier must still exist during EBB");
        h.assertTrue(gravityAttr.getValue() > 0 && gravityAttr.getValue() < 0.08,
                "Halfway EBB must restore some ordinary downward gravity before the phase ends");

        // 4. Return to CALM: intensity = 0.0 -> modifier removed
        BuoyancyController.applyEntityBuoyancy(entity, 0.0F);
        h.assertTrue(!gravityAttr.hasModifier(BuoyancyController.BUOYANCY_ID), "Buoyancy modifier must be removed after tide ends");
        h.assertTrue(Math.abs(gravityAttr.getValue() - 0.08) < 1e-4, "Gravity restored to 0.08");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shelteredEntityDoesNotReceiveBuoyancy(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 420, 320, 2, 101, 215);
        Zombie entity = EntityType.ZOMBIE.create(world);
        h.assertTrue(entity != null, "Failed to create test zombie");
        entity.moveTo(420.5, 100.0, 320.5, 0, 0);

        BlockPos roofPos = new BlockPos(420, 103, 320);
        world.setBlock(roofPos, Blocks.STONE.defaultBlockState(), 3);

        var gravityAttr = entity.getAttribute(Attributes.GRAVITY);
        h.assertTrue(gravityAttr != null, "Zombie must have GRAVITY attribute");

        // During peak surge, sheltered entity must not receive buoyancy
        BuoyancyController.applyEntityBuoyancy(entity, 1.0F);
        h.assertTrue(!gravityAttr.hasModifier(BuoyancyController.BUOYANCY_ID), "Sheltered entity must NOT receive buoyancy");
        h.assertTrue(Math.abs(gravityAttr.getValue() - 0.08) < 1e-4, "Sheltered entity gravity must remain 0.08");

        // Cleanup
        world.setBlock(roofPos, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void specialCasesDoNotReceiveBuoyancy(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 440, 320, 2, 101, 215);
        Zombie entity = EntityType.ZOMBIE.create(world);
        h.assertTrue(entity != null, "Failed to create test zombie");
        entity.moveTo(440.5, 100.0, 320.5, 0, 0);

        var gravityAttr = entity.getAttribute(Attributes.GRAVITY);
        h.assertTrue(gravityAttr != null, "Zombie must have GRAVITY attribute");

        // 1. In water
        BlockPos waterPos = entity.blockPosition();
        world.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
        entity.updateFluidHeightAndDoFluidPushing();
        try {
            var f = net.minecraft.world.entity.Entity.class.getDeclaredField("wasTouchingWater");
            f.setAccessible(true);
            f.setBoolean(entity, true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        BuoyancyController.applyEntityBuoyancy(entity, 1.0F);
        h.assertTrue(!gravityAttr.hasModifier(BuoyancyController.BUOYANCY_ID), "Entity in water must not receive tide buoyancy");
        world.setBlock(waterPos, Blocks.AIR.defaultBlockState(), 3);

        // 2. On ladder
        BlockPos ladderPos = entity.blockPosition();
        world.setBlock(ladderPos, Blocks.LADDER.defaultBlockState(), 3);
        BuoyancyController.applyEntityBuoyancy(entity, 1.0F);
        h.assertTrue(!gravityAttr.hasModifier(BuoyancyController.BUOYANCY_ID), "Entity on ladder must not receive tide buoyancy");
        world.setBlock(ladderPos, Blocks.AIR.defaultBlockState(), 3);

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fallDistanceBehaviorPreserved(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 460, 320, 2, 101, 215);
        Zombie entity = EntityType.ZOMBIE.create(world);
        h.assertTrue(entity != null, "Failed to create test zombie");
        entity.moveTo(460.5, 100.0, 320.5, 0, 0);

        // Simulate falling downward with downward velocity
        entity.fallDistance = 12.0F;
        entity.setDeltaMovement(new Vec3(0, -0.6, 0));

        // When applying buoyancy while still moving downward, fall distance is NOT zeroed
        BuoyancyController.applyEntityBuoyancy(entity, 1.0F);
        h.assertTrue(entity.fallDistance > 0.0F, "Fall distance must not be globally wiped while moving downward");

        // When upward force arrests the fall and lifts upward (vy >= 0), fall distance is cleared
        entity.setDeltaMovement(new Vec3(0, 0.2, 0));
        BuoyancyController.applyEntityBuoyancy(entity, 1.0F);
        h.assertTrue(entity.fallDistance == 0.0F, "Upward lift should reset fall distance");

        BuoyancyController.removeBuoyancy(entity);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shelterBenchmark(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 480, 320, 2, 101, 215);
        Zombie entity = EntityType.ZOMBIE.create(world);
        h.assertTrue(entity != null, "Failed to create test zombie");
        entity.moveTo(480.5, 100.0, 320.5, 0, 0);

        BlockPos roofPos = new BlockPos(480, 105, 320);
        world.setBlock(roofPos, Blocks.COBBLESTONE.defaultBlockState(), 3);

        // Warm up
        for (int i = 0; i < 200; i++) {
            ShelterDetector.isSheltered(world, entity);
        }

        // Measure 1,000 shelter checks
        long start = System.nanoTime();
        int checks = 1000;
        int shelteredCount = 0;
        for (int i = 0; i < checks; i++) {
            if (ShelterDetector.isSheltered(world, entity)) shelteredCount++;
        }
        long elapsedNanos = System.nanoTime() - start;
        double avgNanos = (double) elapsedNanos / checks;
        double avgMicros = avgNanos / 1000.0;

        System.out.println("SHELTER_BENCHMARK checks=" + checks + " sheltered=" + shelteredCount
                + " total_elapsed_ms=" + String.format(java.util.Locale.ROOT, "%.3f", elapsedNanos / 1_000_000.0)
                + " avg_per_check_us=" + String.format(java.util.Locale.ROOT, "%.3f", avgMicros));

        h.assertTrue(shelteredCount == checks, "All checks must succeed");
        // Strict assertion: shelter check must take less than 50 microseconds per check
        h.assertTrue(avgMicros < 50.0, "Shelter check is too slow; took " + avgMicros + " us");

        world.setBlock(roofPos, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tallEntityBumpedAgainstCeilingSlabIsSheltered(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 500, 320, 2, 101, 215);

        // Place a ceiling slab at Y=104
        BlockPos slabPos = new BlockPos(500, 104, 320);
        world.setBlock(slabPos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);

        // An entity whose head is bumped into the slab (maxY = 104.05)
        Zombie tallEntity = EntityType.ZOMBIE.create(world);
        h.assertTrue(tallEntity != null, "Failed to create zombie");
        // Zombie height is 1.95. Position at 102.1 makes maxY = 104.05
        tallEntity.moveTo(500.5, 102.1, 320.5, 0, 0);

        h.assertTrue(ShelterDetector.isSheltered(world, tallEntity),
                "Entity touching/bumped against bottom of ceiling slab must be detected as sheltered");

        // Verify buoyancy controller removes modifier when entity is against ceiling slab
        BuoyancyController.applyEntityBuoyancy(tallEntity, 1.0F);
        var attr = tallEntity.getAttribute(Attributes.GRAVITY);
        h.assertTrue(attr != null && !attr.hasModifier(BuoyancyController.BUOYANCY_ID),
                "Sheltered entity under slab ceiling must NOT retain buoyancy modifier");

        world.setBlock(slabPos, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void droppedItemRisesAndDisintegratesAtUpperSea(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 520, 320, 2, 101, 215);

        ItemEntity item = new ItemEntity(world, 520.5, 102.0, 320.5, new ItemStack(Items.DIAMOND));
        world.addFreshEntity(item);

        // 1. Initial state: item on ground, apply buoyancy
        BuoyancyController.applyEntityBuoyancy(item, 1.0F);
        h.assertTrue(item.getDeltaMovement().y > 0.0,
                "Item out in the open during surge must receive upward buoyant velocity");

        // 2. Teleport item to the lower boundary of the upper toxic sea
        var profile = GeometryProfiles.get(world);
        double seaBottom = SeaSurface.cellMinimum(profile, 520, 320, true);
        item.moveTo(520.5, seaBottom + 0.5, 320.5, 0, 0);

        // 3. Applying buoyancy at or above the upper sea must disintegrate the item
        BuoyancyController.applyEntityBuoyancy(item, 1.0F);
        h.assertTrue(item.isRemoved(),
                "Item touching upper toxic sea must be disintegrated / discarded");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tideTransitionToCalmCleansUpAllMobBuoyancy(GameTestHelper h) {
        ServerLevel world = islandWorld(h);
        clearTestColumn(world, 540, 320, 2, 101, 215);

        Zombie mob = EntityType.ZOMBIE.create(world);
        h.assertTrue(mob != null, "Failed to create zombie");
        mob.moveTo(540.5, 105.0, 320.5, 0, 0);

        // During SURGE, mob in open receives buoyancy
        BuoyancyController.applyEntityBuoyancy(mob, 1.0F);
        var attr = mob.getAttribute(Attributes.GRAVITY);
        h.assertTrue(attr != null && attr.hasModifier(BuoyancyController.BUOYANCY_ID),
                "Mob in open during surge must have buoyancy modifier");

        // When tide transitions to CALM (intensity 0.0F), cleanup strips modifier
        BuoyancyController.applyEntityBuoyancy(mob, 0.0F);
        h.assertTrue(!attr.hasModifier(BuoyancyController.BUOYANCY_ID),
                "Transition to 0.0F intensity must completely remove buoyancy modifier so mob falls");

        h.succeed();
    }
}

