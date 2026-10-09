package pro.erez.interstice.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.rift.RiftLinks;
import pro.erez.interstice.rift.RiftSafety;
import pro.erez.interstice.rift.RiftTravel;
import pro.erez.interstice.tide.BuoyancyController;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.worldgen.IslandWorld;

/** V6 stage A regressions; runs in the existing disposable rift GameTest group. */
@GameTestHolder("interstice_rifts")
@PrefixGameTestTemplate(false)
public final class V6GameplayRegressionGameTests {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Interstice.ID, path);
    }

    private static ServerLevel openColumn(GameTestHelper h, int x) {
        var world = h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD);
        h.assertTrue(world != null, "The retained tall world must remain available");
        world.getChunk(x >> 4, 910 >> 4);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = 100; y <= 215; y++)
            world.setBlock(new BlockPos(x + dx, y, 910 + dz), Blocks.AIR.defaultBlockState(), 3);
        return world;
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void actualV5RiftArrivalUnlocksFrameAndLensWithoutManualAwards(GameTestHelper h) {
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 13; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var player = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        try {
            var root = player.server.getAdvancements().get(id("root"));
            var discovery = player.server.getAdvancements().get(id("rift_discovery"));
            h.assertTrue(root != null && discovery != null, "Both discovery advancements must deserialize from the installed resources");
            h.assertTrue(!player.getAdvancements().getOrStartProgress(root).isDone()
                    && !player.getAdvancements().getOrStartProgress(discovery).isDone()
                    && !player.getRecipeBook().contains(id("rift_frame")) && !player.getRecipeBook().contains(id("rift_lens")),
                    "A new outside player cannot start with realm discovery or its portal recipes");
            var realm = player.server.getLevel(IslandWorld.VANILLA_WORLD);
            h.assertTrue(realm != null, "V5 must remain a playable archive after adding V6");
            var echo = RiftSafety.prepareEcho(realm, RiftSafety.defaultHint(realm));
            h.assertTrue(echo != null, "The V5 connection must have a real safe echo");
            var source = new RiftLinks.Endpoint(h.getLevel().dimension(), player.blockPosition(), Direction.Axis.X);
            var link = RiftLinks.get(player.server).add(RiftLinks.Kind.FISHING, source,
                    new RiftLinks.Endpoint(IslandWorld.VANILLA_WORLD, echo, Direction.Axis.X));
            h.assertTrue(RiftTravel.enter(player, source, RiftLinks.Kind.FISHING), "Native rift travel must enter the saved V5 destination");
            player.hasChangedDimension();
            h.assertTrue(player.level().dimension().equals(IslandWorld.VANILLA_WORLD)
                    && player.getPersistentData().getUUID(RiftTravel.ACTIVE).equals(link.id()),
                    "Adding a new default realm cannot retarget the stored V5 connection");
            h.assertTrue(player.getAdvancements().getOrStartProgress(root).isDone()
                    && player.getAdvancements().getOrStartProgress(discovery).isDone(),
                    "Actual V5 arrival must complete both discovery advancements through changed_dimension");
            h.assertTrue(player.getRecipeBook().contains(id("rift_frame")) && player.getRecipeBook().contains(id("rift_lens")),
                    "The native discovery reward must put both portal recipes into the player's recipe book");
        } finally {
            TestPlayers.remove(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wetSolidRoofsKeepShelterAndBreakingThemRestoresExposure(GameTestHelper h) {
        int x = 930;
        var world = openColumn(h, x);
        var entity = EntityType.ZOMBIE.create(world);
        h.assertTrue(entity != null, "A living entity is required for the actual footprint test");
        entity.moveTo(x + .5, 100, 910.5, 0, 0);
        var roof = new BlockPos(x, 104, 910);
        List<BlockState> dryRoofs = List.of(
                Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM),
                Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP),
                Blocks.STONE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HALF, Half.BOTTOM),
                Blocks.STONE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
        try {
            for (var dry : dryRoofs) {
                world.setBlock(roof, dry, 3);
                h.assertTrue(ShelterDetector.isSheltered(world, entity), "The dry roof must shelter: " + dry);
                var wet = dry.setValue(BlockStateProperties.WATERLOGGED, true);
                world.setBlock(roof, wet, 3);
                h.assertTrue(!world.getFluidState(roof).isEmpty() && ShelterDetector.isSheltered(world, entity),
                        "Water must not erase the solid roof's physical shelter: " + wet);
                BuoyancyController.applyEntityBuoyancy(entity, 1);
                h.assertTrue(!entity.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID),
                        "A living entity beneath the wet roof must not receive tide lift");
                world.setBlock(roof, dry, 3);
                h.assertTrue(world.getFluidState(roof).isEmpty() && ShelterDetector.isSheltered(world, entity),
                        "Draining the same roof must retain shelter");
            }
            for (var rejected : List.of(Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState(),
                    Interstice.LIGHT_SEA.get().defaultBlockState(), Interstice.HEAVY_BLOCK.get().defaultBlockState(),
                    Blocks.BEDROCK.defaultBlockState())) {
                world.setBlock(roof, rejected, 3);
                h.assertTrue(!ShelterDetector.isSheltered(world, entity), "Liquid or the technical shell cannot become a roof: " + rejected);
            }
            var profile = GeometryProfiles.get(world);
            h.assertTrue(world.getBlockState(new BlockPos(x, profile.upperMaximum() + 2, 910)).is(Interstice.LIGHT_SEA.get()),
                    "The open-column fixture must still have the real upper toxic sea overhead");
            world.setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(!ShelterDetector.isSheltered(world, entity), "The naturally present upper sea must not shelter an exposed entity");
            world.setBlock(roof, Blocks.COBBLESTONE.defaultBlockState(), 3);
            BuoyancyController.applyEntityBuoyancy(entity, 1);
            h.assertTrue(ShelterDetector.isSheltered(world, entity)
                    && !entity.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID), "A rebuilt roof must immediately shelter");
            world.setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
            BuoyancyController.applyEntityBuoyancy(entity, 1);
            h.assertTrue(!ShelterDetector.isSheltered(world, entity)
                    && entity.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID), "Breaking the roof must immediately restore exposure and lift");
        } finally {
            world.setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
            BuoyancyController.removeBuoyancy(entity);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeNonPlayerDimensionTransferDoesNotCarryTransientTideGravity(GameTestHelper h) {
        int x = 954;
        var world = openColumn(h, x);
        var zombie = EntityType.ZOMBIE.create(world);
        h.assertTrue(zombie != null, "A real non-player living entity is required");
        zombie.setNoAi(true);
        zombie.moveTo(x + .5, 100, 910.5, 0, 0);
        h.assertTrue(world.addFreshEntity(zombie), "The source mob must be registered in the actual realm");
        Entity moved = null;
        try {
            BuoyancyController.applyEntityBuoyancy(zombie, 1);
            h.assertTrue(zombie.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID)
                    && zombie.getAttributeValue(Attributes.GRAVITY) < 0,
                    "The mob must actually carry the transient upward tide modifier before transfer");
            var target = h.absolutePos(new BlockPos(6, 3, 6));
            moved = zombie.changeDimension(new DimensionTransition(h.getLevel(), target.getBottomCenter(), Vec3.ZERO,
                    0, 0, DimensionTransition.DO_NOTHING));
            h.assertTrue(moved instanceof Zombie && moved.level() == h.getLevel() && zombie.isRemoved(),
                    "The native dimension transfer must replace the registered source mob with its destination entity");
            var destination = (Zombie) moved;
            h.assertTrue(destination.getUUID().equals(zombie.getUUID())
                    && !destination.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID)
                    && Math.abs(destination.getAttributeValue(Attributes.GRAVITY) - .08) < 1e-6,
                    "The same saved mob must regain ordinary gravity outside the realm without a player cleanup event");
        } finally {
            if (moved != null) moved.discard();
            if (!zombie.isRemoved()) zombie.discard();
        }
        h.succeed();
    }
}
