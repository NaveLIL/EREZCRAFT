package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.block.SpiderEggSacBlock;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;
import pro.erez.interstice.entity.ToxinSpitEntity;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class CaveSpiderGameTests {
    private CaveSpiderGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spiderVariantsAndAttributesInitializedProperly(GameTestHelper h) {
        var spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        
        spider.setVariant(CaveRiftSpiderEntity.Variant.SKIRMISHER);
        h.assertTrue(spider.getMaxHealth() == 16.0, "Skirmisher health mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.36, "Skirmisher speed mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.ATTACK_DAMAGE) == 5.0, "Skirmisher damage mismatch");

        spider.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        h.assertTrue(spider.getMaxHealth() == 30.0, "Lurker health mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.20, "Lurker base speed mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8.0, "Lurker damage mismatch");

        spider.setVariant(CaveRiftSpiderEntity.Variant.SPITTER);
        h.assertTrue(spider.getMaxHealth() == 14.0, "Spitter health mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.28, "Spitter speed mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3.0, "Spitter damage mismatch");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lurkerCalmAtDistanceAndBurstsInProximity(GameTestHelper h) {
        var lurker = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        lurker.setPos(h.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        lurker.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        h.getLevel().addFreshEntity(lurker);

        h.assertTrue(!lurker.isBursting(), "Lurker should spawn calm");

        // Distance > 3.5: remains calm
        lurker.tick();
        h.assertTrue(!lurker.isBursting(), "Lurker should not burst without close prey or provoke");

        // Trigger burst ambush
        lurker.triggerAmbushBurst();
        h.assertTrue(lurker.isBursting(), "Lurker must enter burst state");
        h.assertTrue(lurker.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.50, "Lurker sprint speed should be boosted above 0.50");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void packCoordinatesAndAlertsComrades(GameTestHelper h) {
        var s1 = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        s1.setPos(h.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        s1.setVariant(CaveRiftSpiderEntity.Variant.SKIRMISHER);
        h.getLevel().addFreshEntity(s1);

        var s2 = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        s2.setPos(h.absolutePos(new BlockPos(5, 2, 5)).getBottomCenter());
        s2.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        h.getLevel().addFreshEntity(s2);

        var player = TestPlayers.create(h, new BlockPos(1, 2, 1), GameType.SURVIVAL);

        s1.alertPack(player);
        h.assertTrue(s2.getTarget() == player, "Comrade spider should acquire alerted pack target");
        h.assertTrue(s2.isBursting(), "Lurker comrade should trigger burst sprint on pack alert");
        TestPlayers.remove(player);

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void toxinSpitInflictsCorrosiveEffects(GameTestHelper h) {
        var spitter = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        spitter.setPos(h.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        spitter.setVariant(CaveRiftSpiderEntity.Variant.SPITTER);
        h.getLevel().addFreshEntity(spitter);

        var spit = new ToxinSpitEntity(h.getLevel(), spitter, 0.5, 0.0, 0.5);
        h.getLevel().addFreshEntity(spit);

        var player = TestPlayers.create(h, new BlockPos(3, 2, 3), GameType.SURVIVAL);

        // Hit entity directly
        spit.onHitEntity(new net.minecraft.world.phys.EntityHitResult(player));

        h.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Toxin spit should apply slowness");
        h.assertTrue(player.hasEffect(MobEffects.POISON), "Toxin spit should apply poison");
        TestPlayers.remove(player);

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void riftCobwebAllowsSpidersAndSlowsOthers(GameTestHelper h) {
        var spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        spider.setDeltaMovement(1.0, 0.0, 1.0);

        var web = Interstice.RIFT_COBWEB.get();
        web.entityInside(web.defaultBlockState(), h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)), spider);

        // Delta movement of native spider must NOT be reduced to stuck (0.2, 0.05, 0.2)
        h.assertTrue(spider.getDeltaMovement().x > 0.5, "Cave spider should move freely through rift web");

        var victim = TestPlayers.create(h, new BlockPos(2, 2, 2), GameType.SURVIVAL);
        victim.setDeltaMovement(1.0, 0.0, 1.0);
        web.entityInside(web.defaultBlockState(), h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)), victim);
        h.assertTrue(victim.getDeltaMovement().x <= 0.25, "Non-spider entity must be slowed by rift web");
        TestPlayers.remove(victim);

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spiderEggSacBurstReleasesDefenders(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        h.setBlock(new BlockPos(3, 2, 3), Interstice.SPIDER_EGG_SAC.get());

        var player = TestPlayers.create(h, new BlockPos(4, 2, 4), GameType.SURVIVAL);

        SpiderEggSacBlock.burstEggSac(h.getLevel(), pos, player);

        // Check spawned defenders
        var defenders = h.getLevel().getEntitiesOfClass(CaveRiftSpiderEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(4.0));
        h.assertTrue(!defenders.isEmpty(), "Egg sac burst should spawn defenders");
        h.assertTrue(defenders.get(0).getTarget() == player, "Spawned defenders should target the egg sac destroyer");
        TestPlayers.remove(player);

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void caveSpawnRulesStrictlyEnforced(GameTestHelper h) {
        var groundPos = new BlockPos(2, 2, 2);
        h.setBlock(groundPos, net.minecraft.world.level.block.Blocks.STONE);
        var spawnPos = h.absolutePos(groundPos.above());

        // 1. Without solid roof, spawn must be rejected
        h.assertTrue(!CaveRiftSpiderEntity.checkCaveSpiderSpawnRules(
                Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL, spawnPos, h.getLevel().getRandom()),
                "Spawn without cave roof must be rejected");

        // 2. Under tree leaves, spawn must be rejected
        h.setBlock(groundPos.above(3), net.minecraft.world.level.block.Blocks.OAK_LEAVES);
        h.assertTrue(!CaveRiftSpiderEntity.checkCaveSpiderSpawnRules(
                Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL, spawnPos, h.getLevel().getRandom()),
                "Spawn under leaves must be rejected");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void spiderAttackBypassesArmorAndDealsTrueDamage(GameTestHelper h) {
        var spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        spider.setVariant(CaveRiftSpiderEntity.Variant.SKIRMISHER); // 5.0 damage
        h.getLevel().addFreshEntity(spider);

        // Player 1: completely unarmored
        var nakedPlayer = TestPlayers.create(h, new BlockPos(2, 2, 2), GameType.SURVIVAL);
        nakedPlayer.setHealth(20.0F);

        // Player 2: heavily armored with Netherite Armor equivalent (20 armor points)
        var armoredPlayer = TestPlayers.create(h, new BlockPos(4, 2, 4), GameType.SURVIVAL);
        armoredPlayer.setHealth(20.0F);
        var armorAttr = armoredPlayer.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) armorAttr.setBaseValue(20.0);

        // Let the 60-tick native ServerPlayer spawn invulnerability guard expire
        h.runAtTickTime(70, () -> {
            try {
                nakedPlayer.invulnerableTime = 0;
                armoredPlayer.invulnerableTime = 0;

                // Attack both players
                spider.doHurtTarget(nakedPlayer);
                spider.doHurtTarget(armoredPlayer);

                float damageToNaked = 20.0F - nakedPlayer.getHealth();
                float damageToArmored = 20.0F - armoredPlayer.getHealth();

                // Normally, 20 armor reduces 5.0 damage by 80% (damage taken = 1.0)
                // With armor-piercing damage, both take full 5.0 damage!
                h.assertTrue(damageToNaked == 5.0F, "Unarmored player must take full 5.0 true damage, but took: " + damageToNaked);
                h.assertTrue(damageToArmored == 5.0F, "Armored player must take full 5.0 true damage (ignoring armor), but took: " + damageToArmored);
            } finally {
                TestPlayers.remove(nakedPlayer);
                TestPlayers.remove(armoredPlayer);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lurkerPerformsAmbushLungeWhenTriggered(GameTestHelper h) {
        var groundPos = new BlockPos(2, 1, 2);
        h.setBlock(groundPos, net.minecraft.world.level.block.Blocks.STONE);
        var lurker = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        lurker.setPos(h.absolutePos(groundPos.above()).getBottomCenter());
        lurker.setOnGround(true);
        lurker.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        h.getLevel().addFreshEntity(lurker);

        var player = TestPlayers.create(h, new BlockPos(5, 2, 2), GameType.SURVIVAL);
        lurker.setTarget(player);

        // Trigger ambush burst
        lurker.triggerAmbushBurst();

        // Check that lunge impulse was applied
        Vec3 vel = lurker.getDeltaMovement();
        h.assertTrue(vel.horizontalDistance() > 0.4, "Lurker must lunge forward with velocity on ambush trigger");
        h.assertTrue(vel.y > 0.1, "Lurker must have upward leap velocity on ambush trigger");

        TestPlayers.remove(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spidersHuntPreyAndRefuseFriendlyFire(GameTestHelper h) {
        var spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        var brother = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        var bat = new Bat(EntityType.BAT, h.getLevel());
        var zombie = new Zombie(EntityType.ZOMBIE, h.getLevel());
        var golem = new IronGolem(EntityType.IRON_GOLEM, h.getLevel());

        // Validate prey targeting
        h.assertTrue(spider.canTargetEntity(bat), "Cave rift spiders must target bats");
        h.assertTrue(spider.canTargetEntity(zombie), "Cave rift spiders must target zombies");
        h.assertTrue(spider.canTargetEntity(golem), "Cave rift spiders must target iron golems");

        // Validate complete immunity to friendly fire
        h.assertTrue(!spider.canTargetEntity(brother), "Cave rift spiders must NEVER target fellow cave rift spiders");
        h.assertTrue(!spider.canAttack(brother), "canAttack must return false for fellow spiders");

        spider.setTarget(brother);
        h.assertTrue(spider.getTarget() == null, "setTarget must reject targeting fellow spiders");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ceilingClimbingAndAdhesionHoldsEntity(GameTestHelper h) {
        // Build a stone ceiling at y=4
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
        }

        var spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        // Position directly under ceiling (y=3.25, bb height is 0.7, top is 3.95, stone ceiling is at y=4.0)
        spider.setPos(h.absolutePos(new BlockPos(2, 0, 2)).getX() + 0.5,
                      h.absolutePos(new BlockPos(2, 0, 2)).getY() + 3.25,
                      h.absolutePos(new BlockPos(2, 0, 2)).getZ() + 0.5);
        h.getLevel().addFreshEntity(spider);

        h.assertTrue(spider.hasCeilingAbove(), "Spider must detect stone ceiling above");

        // Tick spider to engage ceiling clinging
        spider.tick();
        h.assertTrue(spider.isClimbingCeiling(), "Spider must enter isClimbingCeiling state");

        // Test travel physics counteracts gravity on ceiling
        spider.travel(Vec3.ZERO);
        h.assertTrue(spider.getDeltaMovement().y >= 0.0, "Delta movement Y must stay sticky (+0.02) to hold to ceiling without dropping");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lurkerPerformsCeilingDropAmbushOnPreyBeneath(GameTestHelper h) {
        // Ceiling at y=5
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                h.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
            }
        }

        var lurker = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        lurker.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        lurker.setPos(h.absolutePos(new BlockPos(2, 0, 2)).getX() + 0.5,
                      h.absolutePos(new BlockPos(2, 0, 2)).getY() + 4.25,
                      h.absolutePos(new BlockPos(2, 0, 2)).getZ() + 0.5);
        h.getLevel().addFreshEntity(lurker);
        lurker.setClimbingCeiling(true);

        // Prey directly underneath at y=1
        var player = TestPlayers.create(h, new BlockPos(2, 1, 2), GameType.SURVIVAL);
        lurker.setTarget(player);

        // Tick lurker to trigger ceiling drop ambush
        lurker.tick();

        h.assertTrue(!lurker.isClimbingCeiling(), "Lurker must detach from ceiling to ambush");
        h.assertTrue(lurker.isBursting(), "Lurker must enter burst sprint during ambush drop");
        h.assertTrue(lurker.getDeltaMovement().y < -0.5, "Lurker must plunge downwards towards prey with high downward velocity");

        TestPlayers.remove(player);
        h.succeed();
    }
}
