package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.ecology.RealmEcology;
import pro.erez.interstice.ecology.SporePodBlock;
import pro.erez.interstice.ecology.SporePodGas;
import pro.erez.interstice.rift.RiftSafety;

@GameTestHolder("interstice_rifts")
@PrefixGameTestTemplate(false)
public final class ForestRiftSafetyGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativeForestHazardsInvalidateDryRiftLandingWithoutRejectingHarmlessClouds(GameTestHelper h) {
        BlockPos feet = h.absolutePos(new BlockPos(6, 2, 6));
        var level = h.getLevel();
        level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
        AreaEffectCloud harmless = null;
        try {
            h.assertTrue(RiftSafety.standing(level, feet), "Clear dry stone landing was rejected before forest hazards");

            level.setBlock(feet, RealmEcology.VENOM_REED.get().defaultBlockState(), 3);
            h.assertTrue(level.noCollision(new AABB(feet.getX() + .2, feet.getY() + .01, feet.getZ() + .2,
                    feet.getX() + .8, feet.getY() + 1.91, feet.getZ() + .8)), "Reed fixture must exercise a real non-colliding hazard");
            h.assertTrue(!RiftSafety.standing(level, feet), "Non-colliding venom reed was accepted as a portal exit");

            level.setBlock(feet, RealmEcology.SPORE_POD.get().defaultBlockState()
                    .setValue(SporePodBlock.PHASE, SporePodBlock.Phase.SPENT).setValue(SporePodBlock.FUSE, 0), 3);
            h.assertTrue(!RiftSafety.standing(level, feet), "Spent pod on the arrival cell was accepted as a clear exit");
            level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(RiftSafety.standing(level, feet), "Removing the spent pod did not restore the clear landing");

            h.assertTrue(SporePodGas.emit(level, feet, 0), "The fixture did not spawn real native pod gas");
            h.assertTrue(!RiftSafety.standing(level, feet), "Active native pod gas was accepted as a safe portal exit");
            level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(feet).inflate(4),
                    cloud -> cloud.getTags().contains(SporePodGas.ENTITY_TAG)).forEach(AreaEffectCloud::discard);
            h.assertTrue(RiftSafety.standing(level, feet), "Removed native gas left a false permanent landing hazard");

            harmless = new AreaEffectCloud(level, feet.getX() + .5, feet.getY() + .1, feet.getZ() + .5);
            harmless.setRadius(2.25F);
            harmless.setWaitTime(0);
            harmless.setDuration(40);
            h.assertTrue(level.addFreshEntity(harmless), "Harmless vanilla cloud fixture was not registered");
            h.assertTrue(RiftSafety.standing(level, feet), "An unrelated effect-free vanilla cloud was treated as forest poison");
            h.succeed();
        } finally {
            if (harmless != null) harmless.discard();
            level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(feet).inflate(4),
                    cloud -> cloud.getTags().contains(SporePodGas.ENTITY_TAG)).forEach(AreaEffectCloud::discard);
            level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
