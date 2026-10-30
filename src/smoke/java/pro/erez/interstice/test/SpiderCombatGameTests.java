package pro.erez.interstice.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;
import pro.erez.interstice.entity.ToxinSpitEntity;

/** Native server collisions and the shared armor contract; these are not full-modpack tests. */
@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class SpiderCombatGameTests {
    private SpiderCombatGameTests() {}

    private static void cleanup(GameTestHelper helper, Entity... entities) {
        helper.testInfo.addListener(new GameTestListener() {
            private void close() { for (Entity entity : entities) entity.discard(); }
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { close(); }
        });
    }

    private static void corridor(GameTestHelper helper) {
        for (int x = 0; x <= 25; x++) for (int z = 1; z <= 9; z++) {
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 7, z), Blocks.STONE);
            for (int y = 2; y < 7; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    private static CaveRiftSpiderEntity owner(GameTestHelper helper, BlockPos pos) {
        var owner = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), helper.getLevel());
        owner.setVariant(CaveRiftSpiderEntity.Variant.SPITTER);
        owner.setNoAi(true); owner.setNoGravity(true); owner.setPersistenceRequired();
        owner.setYRot(-90);owner.yBodyRot=owner.yHeadRot=-90;
        owner.moveTo(helper.absolutePos(pos).getBottomCenter());
        helper.getLevel().addFreshEntity(owner); cleanup(helper, owner);
        return owner;
    }

    private static Zombie target(GameTestHelper helper, BlockPos pos) {
        var target = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        target.setBaby(false); target.setNoAi(true); target.setNoGravity(true); target.setPersistenceRequired();
        target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        target.moveTo(helper.absolutePos(pos).getBottomCenter());
        helper.getLevel().addFreshEntity(target); cleanup(helper, target);
        return target;
    }

    private static List<ToxinSpitEntity> shots(GameTestHelper helper, CaveRiftSpiderEntity owner) {
        return helper.getLevel().getEntitiesOfClass(ToxinSpitEntity.class,
                new AABB(Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(0, 1, 0))),
                        Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(26, 9, 10)))),
                entity -> entity.getOwner() == owner);
    }

    private static void hits(GameTestHelper helper, BlockPos ownerPos, BlockPos targetPos, boolean ceiling) {
        corridor(helper);
        var owner = owner(helper, ownerPos); owner.setClimbingCeiling(ceiling);
        var target = target(helper, targetPos);
        helper.assertTrue(helper.getLevel().isPositionEntityTicking(target.blockPosition()), "Projectile target lies outside the ticking fixture");
        owner.performRangedAttack(target, 1);
        var shots = shots(helper, owner);
        helper.assertTrue(shots.size() == 1, "Visible in-range stationary target did not receive a shot");
        cleanup(helper, shots.toArray(Entity[]::new));
        helper.assertTrue(shots.getFirst().getDeltaMovement().length() <= .8500001,
                "Accuracy repair increased accepted launch speed");
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(shots.getFirst().isRemoved(), "Real projectile did not finish its impact: ticks="+shots.getFirst().tickCount+" position="+shots.getFirst().position()+" prey="+target.position());
            helper.assertTrue(Math.abs(target.getHealth() - 16) < .001,
                    "Real shot failed to deal exactly four damage at the stationary target: " + target.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = "spider_combat", batch = "spider_combat_ten", timeoutTicks = 60)
    public static void actualProjectileHitsStationaryZombieTenBlocksAway(GameTestHelper helper) {
        hits(helper, new BlockPos(3, 2, 5), new BlockPos(13, 2, 5), false);
    }

    @GameTest(template = "spider_combat", batch = "spider_combat_fourteen", timeoutTicks = 60)
    public static void actualProjectileHitsStationaryZombieAtFourteenBlockLimit(GameTestHelper helper) {
        hits(helper, new BlockPos(3, 2, 5), new BlockPos(17, 2, 5), false);
    }

    @GameTest(template = "spider_combat", batch = "spider_combat_ceiling", timeoutTicks = 60)
    public static void ceilingVariantAimsFromActualMuzzleAtLowerPrey(GameTestHelper helper) {
        hits(helper, new BlockPos(3, 5, 5), new BlockPos(13, 2, 5), true);
    }

    @GameTest(template = "spider_combat", batch = "spider_combat_raised", timeoutTicks = 60)
    public static void actualProjectileReachesRaisedPrey(GameTestHelper helper) {
        hits(helper, new BlockPos(3, 2, 5), new BlockPos(13, 4, 5), false);
    }

    @GameTest(template = "spider_combat", batch = "spider_combat_low_roof", timeoutTicks = 60)
    public static void visibleTargetUnderLowRoofDoesNotReceiveShotThroughBlockedArc(GameTestHelper helper) {
        corridor(helper);
        var owner = owner(helper, new BlockPos(3, 2, 5));
        var target = target(helper, new BlockPos(13, 2, 5));
        // This obstacle is above the direct eye ray, but inside the actual ballistic arc.
        // Leave clearance for the taller/wider body while the arc grazes the near roof edge.
        owner.setPos(owner.getX()-.25, owner.getY(), owner.getZ());
        // Updated for the taller variant eye; still requires clear sight and a blocked ballistic arc.
        helper.setBlock(new BlockPos(4, 3, 5), Blocks.STONE);
        owner.getSensing().tick();
        helper.assertTrue(helper.getLevel().noCollision(owner,owner.getBoundingBox()), "Roof fixture intersects the enlarged body");
        helper.assertTrue(owner.getSensing().hasLineOfSight(target), "Fixture blocked straight sight rather than the arc");
        owner.performRangedAttack(target, 1);
        helper.assertTrue(shots(helper, owner).isEmpty(), "Trajectory preflight ignored a real collider above the eye ray");
        helper.succeed();
    }

    @GameTest(template = "spider_combat", batch = "spider_combat_late_cover", timeoutTicks = 60)
    public static void coverPlacedAfterLaunchStopsTheNativeProjectile(GameTestHelper helper) {
        corridor(helper);
        var owner = owner(helper, new BlockPos(3, 2, 5));
        var target = target(helper, new BlockPos(13, 2, 5));
        owner.performRangedAttack(target, 1);
        var shots = shots(helper, owner);
        helper.assertTrue(shots.size() == 1, "Clear path failed to launch its projectile");
        cleanup(helper, shots.toArray(Entity[]::new));
        for (int y = 2; y <= 6; y++) helper.setBlock(new BlockPos(8, y, 5), Blocks.STONE);
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(shots.getFirst().isRemoved() && target.getHealth() == 20,
                    "Native moving projectile bypassed shelter placed after launch");
            helper.succeed();
        });
    }

    @GameTest(template = "spider_combat", batch = "spider_combat_mod_armor", timeoutTicks = 60)
    public static void nonVanillaEquipmentArmorProtectsOrdinaryDamageButNeitherSpiderAttack(GameTestHelper helper) {
        corridor(helper);
        var owner = owner(helper, new BlockPos(3, 2, 5));
        var target = target(helper, new BlockPos(13, 2, 5));
        // A registered non-vanilla item with normal equipment attributes, confined to this fixture.
        var armor = new ItemStack(Interstice.SPIDER_EGG_SAC_ITEM.get());
        helper.assertTrue(!BuiltInRegistries.ITEM.getKey(armor.getItem()).getNamespace().equals("minecraft"),
                "Armor fixture accidentally used a vanilla item");
        armor.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(ResourceLocation.fromNamespaceAndPath(Interstice.ID, "test_armor"),
                        20, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(ResourceLocation.fromNamespaceAndPath(Interstice.ID, "test_toughness"),
                        8, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST).build());
        target.setItemSlot(EquipmentSlot.CHEST, armor);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(target.getAttributeValue(Attributes.ARMOR) >= 20
                            && target.getAttributeValue(Attributes.ARMOR_TOUGHNESS) >= 8,
                    "Equipped non-vanilla armor fixture never activated real attributes");
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().mobAttack(owner), 5);
            helper.assertTrue(target.getHealth() > 18 && target.getHealth() < 20,
                    "Ordinary damage did not demonstrate actual equipped armor protection");
            target.setHealth(20); target.invulnerableTime = 0;
            owner.setVariant(CaveRiftSpiderEntity.Variant.SKIRMISHER);
            helper.assertTrue(owner.doHurtTarget(target) && target.getHealth() == 15,
                    "Non-vanilla normal armor reduced the accepted five-damage spider bite");
            target.setHealth(20); target.invulnerableTime = 0;
            owner.setVariant(CaveRiftSpiderEntity.Variant.SPITTER);
            owner.performRangedAttack(target, 1);
            var shots = shots(helper, owner);
            helper.assertTrue(shots.size() == 1, "Non-vanilla equipped prey failed to receive a spit");
            cleanup(helper, shots.toArray(Entity[]::new));
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(target.getHealth() == 16, "Non-vanilla normal armor reduced the real four-damage projectile");
            helper.succeed();
        });
    }
}
