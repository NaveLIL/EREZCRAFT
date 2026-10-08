package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.fluid.FluidReactions;
import pro.erez.interstice.worldgen.IslandWorld;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import java.util.function.Consumer;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class FluidReactionGameTests {

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void heavyToxinAndWaterFormsVitriolite(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos1 = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos pos2 = h.absolutePos(new BlockPos(2, 2, 3));

        level.setBlock(pos1, Interstice.HEAVY_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(pos2, Blocks.WATER.defaultBlockState(), 3);

        boolean hasVitriolite = level.getBlockState(pos1).is(Interstice.VITRIOLITE.get())
                || level.getBlockState(pos2).is(Interstice.VITRIOLITE.get());
        if (!hasVitriolite) {
            FluidReactions.handleFluidContact(level, pos1, level.getFluidState(pos1));
            hasVitriolite = level.getBlockState(pos1).is(Interstice.VITRIOLITE.get())
                    || level.getBlockState(pos2).is(Interstice.VITRIOLITE.get());
        }
        h.assertTrue(hasVitriolite, "Reaction must produce Vitriolite block");

        // Clean up
        level.setBlock(pos1, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos2, Blocks.AIR.defaultBlockState(), 3);

        System.out.println("REACTION_VITRIOLITE verified=true");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void heavyToxinAndLavaFormsPyrolith(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos1 = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos pos2 = h.absolutePos(new BlockPos(2, 2, 3));

        level.setBlock(pos1, Interstice.HEAVY_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(pos2, Blocks.LAVA.defaultBlockState(), 3);

        boolean hasPyrolith = level.getBlockState(pos1).is(Interstice.PYROLITH.get())
                || level.getBlockState(pos2).is(Interstice.PYROLITH.get());
        if (!hasPyrolith) {
            FluidReactions.handleFluidContact(level, pos1, level.getFluidState(pos1));
            hasPyrolith = level.getBlockState(pos1).is(Interstice.PYROLITH.get())
                    || level.getBlockState(pos2).is(Interstice.PYROLITH.get());
        }
        h.assertTrue(hasPyrolith, "Reaction must produce Pyrolith block");

        // Clean up
        level.setBlock(pos1, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos2, Blocks.AIR.defaultBlockState(), 3);

        System.out.println("REACTION_PYROLITH verified=true");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void lightToxinAndWaterFormsAerolite(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos1 = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos pos2 = h.absolutePos(new BlockPos(2, 2, 3));

        level.setBlock(pos1, Interstice.LIGHT_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(pos2, Blocks.WATER.defaultBlockState(), 3);

        boolean hasAerolite = level.getBlockState(pos1).is(Interstice.AEROLITE.get())
                || level.getBlockState(pos2).is(Interstice.AEROLITE.get());
        if (!hasAerolite) {
            FluidReactions.handleFluidContact(level, pos1, level.getFluidState(pos1));
            hasAerolite = level.getBlockState(pos1).is(Interstice.AEROLITE.get())
                    || level.getBlockState(pos2).is(Interstice.AEROLITE.get());
        }
        h.assertTrue(hasAerolite, "Reaction must produce Aerolite block");

        // Clean up
        level.setBlock(pos1, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos2, Blocks.AIR.defaultBlockState(), 3);

        System.out.println("REACTION_AEROLITE verified=true");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void lightToxinAndLavaFormsPhosphorite(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos1 = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos pos2 = h.absolutePos(new BlockPos(2, 2, 3));

        level.setBlock(pos1, Interstice.LIGHT_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(pos2, Blocks.LAVA.defaultBlockState(), 3);

        boolean hasPhosphorite = level.getBlockState(pos1).is(Interstice.PHOSPHORITE.get())
                || level.getBlockState(pos2).is(Interstice.PHOSPHORITE.get());
        if (!hasPhosphorite) {
            FluidReactions.handleFluidContact(level, pos1, level.getFluidState(pos1));
            hasPhosphorite = level.getBlockState(pos1).is(Interstice.PHOSPHORITE.get())
                    || level.getBlockState(pos2).is(Interstice.PHOSPHORITE.get());
        }
        h.assertTrue(hasPhosphorite, "Reaction must produce Phosphorite block");

        // Clean up
        level.setBlock(pos1, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos2, Blocks.AIR.defaultBlockState(), 3);

        System.out.println("REACTION_PHOSPHORITE verified=true");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void annihilationCooldownsDoNotCrossDimensions(GameTestHelper h) {
        ServerLevel first = h.getLevel().getServer().getLevel(IslandWorld.WORLD);
        ServerLevel second = h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD);
        BlockPos pos = new BlockPos(16008, 60, 16008);
        int[] explosions = new int[2];
        Consumer<ExplosionEvent.Detonate> listener = event -> {
            if (event.getExplosion().center().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 1.0) < 1.0) {
                if (event.getLevel() == first) explosions[0]++;
                if (event.getLevel() == second) explosions[1]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            for (ServerLevel world : new ServerLevel[]{first, second}) {
                for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) {
                    world.setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
                world.setBlock(pos, Interstice.HEAVY_BLOCK.get().defaultBlockState(), 3);
                world.setBlock(pos.south(), Interstice.LIGHT_BLOCK.get().defaultBlockState(), 3);
            }
            h.assertTrue(explosions[0] == 1 && explosions[1] == 1,
                    "Both dimensions must explode independently at identical coordinates: " + explosions[0] + "/" + explosions[1]);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void heavyAndLightToxinsAnnihilate(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos1 = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos pos2 = h.absolutePos(new BlockPos(2, 2, 3));

        level.setBlock(pos1, Interstice.HEAVY_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(pos2, Interstice.LIGHT_BLOCK.get().defaultBlockState(), 3);

        boolean consumed = !level.getBlockState(pos1).is(Interstice.HEAVY_BLOCK.get())
                && !level.getBlockState(pos2).is(Interstice.LIGHT_BLOCK.get());
        if (!consumed) {
            FluidReactions.handleFluidContact(level, pos1, level.getFluidState(pos1));
            consumed = !level.getBlockState(pos1).is(Interstice.HEAVY_BLOCK.get())
                    && !level.getBlockState(pos2).is(Interstice.LIGHT_BLOCK.get());
        }
        h.assertTrue(consumed, "Heavy toxin + Light toxin must annihilate upon contact");

        System.out.println("REACTION_ANNIHILATION verified=true");
        h.succeed();
    }
}
