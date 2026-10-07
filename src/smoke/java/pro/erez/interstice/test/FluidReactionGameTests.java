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
