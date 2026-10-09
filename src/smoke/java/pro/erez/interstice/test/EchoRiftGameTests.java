package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.EchoRiftEntity;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class EchoRiftGameTests {
    private EchoRiftGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void echoRiftRewindsPositionAndResetsFallDistance(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(3, 2, 3)).getBottomCenter();
        var rift = new EchoRiftEntity(Interstice.ECHO_RIFT.get(), h.getLevel());
        rift.setPos(center);
        h.getLevel().addFreshEntity(rift);

        // Spawn a living entity inside the anomaly
        var zombie = new Zombie(EntityType.ZOMBIE, h.getLevel());
        var startPos = center.add(0.5, 0, 0.5);
        zombie.setPos(startPos);
        h.getLevel().addFreshEntity(zombie);

        // Record initial state in rift
        rift.tick();

        // Move entity forward and simulate fall distance
        var movedPos = startPos.add(1.5, 0, 1.5);
        zombie.setPos(movedPos);
        zombie.fallDistance = 18.0F;

        // Simulate loop threshold by running ticks to trigger rewind
        for (int i = 0; i < EchoRiftEntity.BUFFER_SIZE; i++) {
            rift.tick();
        }

        // Entity must have been snapped back to startPos and fall distance reset
        h.assertTrue(zombie.distanceToSqr(startPos) < 0.25, "Entity should be rewound to start position");
        h.assertTrue(zombie.fallDistance == 0.0F, "Fall distance must be reset to 0 after spatial rewind");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void echoRiftCooldownPreventsImmediateReLoop(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(3, 2, 3)).getBottomCenter();
        var rift = new EchoRiftEntity(Interstice.ECHO_RIFT.get(), h.getLevel());
        rift.setPos(center);
        h.getLevel().addFreshEntity(rift);

        var zombie = new Zombie(EntityType.ZOMBIE, h.getLevel());
        zombie.setPos(center);
        h.getLevel().addFreshEntity(zombie);

        // Complete 1 loop
        for (int i = 0; i < EchoRiftEntity.BUFFER_SIZE + 2; i++) {
            rift.tick();
        }

        // Entity is now under immunity cooldown
        var midPos = center.add(1.0, 0, 1.0);
        zombie.setPos(midPos);

        // Next 5 ticks should NOT rewind immediately
        for (int i = 0; i < 5; i++) {
            rift.tick();
        }

        h.assertTrue(zombie.distanceToSqr(midPos) < 0.1, "Entity under immunity cooldown must not be rewound immediately");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void echoRiftDeflectsProjectilesWithReversedVelocity(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(3, 2, 3)).getBottomCenter();
        var rift = new EchoRiftEntity(Interstice.ECHO_RIFT.get(), h.getLevel());
        rift.setPos(center);
        h.getLevel().addFreshEntity(rift);

        // Shoot arrow towards rift
        var arrow = new Arrow(h.getLevel(), center.x - 2.0, center.y + 0.8, center.z, new ItemStack(Items.ARROW), null);
        arrow.setDeltaMovement(1.0, 0, 0); // Flying in +X direction
        h.getLevel().addFreshEntity(arrow);

        // Rift ticks and detects projectile
        rift.tick();

        // Projectile velocity must be reversed (-X)
        Vec3 vel = arrow.getDeltaMovement();
        h.assertTrue(vel.x < 0.0, "Arrow velocity X must be reversed into negative direction upon rift entry");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void echoRiftHarvestingWithBottleYieldsShard(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(3, 2, 3)).getBottomCenter();
        var rift = new EchoRiftEntity(Interstice.ECHO_RIFT.get(), h.getLevel());
        rift.setPos(center);
        rift.setStability(1.0F);
        h.getLevel().addFreshEntity(rift);

        var player = TestPlayers.create(h, new BlockPos(3, 2, 3), GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));

        var result = rift.interact(player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.consumesAction(), "Interacting with bottle must consume action");

        // Player must have received Echo Shard
        boolean hasShard = player.getInventory().contains(new ItemStack(Interstice.ECHO_SHARD.get()));
        h.assertTrue(hasShard, "Player must receive Echo Shard in inventory");
        h.assertTrue(rift.getStability() < 0.5F, "Rift stability must deplete after harvesting");

        TestPlayers.remove(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void echoShardItemUsageResetsFallAndAppliesSlip(GameTestHelper h) {
        var player = TestPlayers.create(h, new BlockPos(2, 2, 2), GameType.SURVIVAL);
        var shardStack = new ItemStack(Interstice.ECHO_SHARD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, shardStack);
        player.fallDistance = 25.0F;

        // Use echo shard
        var shardItem = Interstice.ECHO_SHARD.get();
        shardItem.use(h.getLevel(), player, InteractionHand.MAIN_HAND);

        h.assertTrue(player.fallDistance == 0.0F, "Using Echo Shard must reset fall distance to 0");
        h.assertTrue(player.getCooldowns().isOnCooldown(shardItem), "Echo Shard must enter cooldown after usage");

        TestPlayers.remove(player);
        h.succeed();
    }
}
