package pro.erez.interstice.test;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.fauna.StrongRealmFauna;
import pro.erez.interstice.fauna.UpperSeaCharge;
import pro.erez.interstice.fauna.UpperSeaGhast;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder("interstice_strong_fauna")
@PrefixGameTestTemplate(false)
public final class StrongRealmFaunaGameTests {
    private StrongRealmFaunaGameTests() {}
    private static void cleanup(GameTestHelper h, List<Entity> entities) {
        h.testInfo.addListener(new GameTestListener() {
            private void close() { entities.forEach(Entity::discard); }
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { close(); }
        });
    }
    private static void room(GameTestHelper h) {
        for (int x = 0; x < 60; x++) for (int z = 0; z < 12; z++) {
            h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            for (int y = 2; y < 29; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }
    private static Zombie zombie(GameTestHelper h, Vec3 point, List<Entity> cleanup) {
        var zombie = new Zombie(EntityType.ZOMBIE, h.getLevel());
        zombie.setNoAi(true); zombie.setNoGravity(true); zombie.setPersistenceRequired();
        zombie.setPos(point); h.getLevel().addFreshEntity(zombie); cleanup.add(zombie); return zombie;
    }
    @GameTest(template = "empty", batch = "strong_dh_null_component", timeoutTicks = 100)
    public static void compiledDhMaterialInjectionReturnsOnNullBeforeWorldOrCallbackAccess(GameTestHelper h) throws Exception {
        // Artificial GameTestServer cannot load DH 3.3.3, and mixin classes may
        // not be instantiated directly. Inspect the real compiled injection's
        // null control-flow; a full native client separately calls actual DH AIR.
        try (var stream = StrongRealmFaunaGameTests.class.getClassLoader().getResourceAsStream(
                "pro/erez/interstice/mixin/DhV6MaterialMixin.class")) {
            h.assertTrue(stream != null, "Compiled DH material mixin is absent");
            var type = new ClassNode(); new ClassReader(stream).accept(type, 0);
            var method = type.methods.stream().filter(m -> m.name.equals("interstice$weakForestGlow")).findFirst().orElseThrow();
            AbstractInsnNode current = nextInstruction(method.instructions.getFirst());
            h.assertTrue(current instanceof VarInsnNode load && current.getOpcode() == Opcodes.ALOAD && load.var == 0,
                    "Null guard does not begin with this.blockState");
            current = nextInstruction(current.getNext());
            h.assertTrue(current instanceof FieldInsnNode field && current.getOpcode() == Opcodes.GETFIELD
                    && field.name.equals("blockState"), "Null guard does not read the shadow state first");
            current = nextInstruction(current.getNext());
            h.assertTrue(current instanceof JumpInsnNode && current.getOpcode() == Opcodes.IFNONNULL,
                    "Shadow state has no leading nullable branch");
            current = nextInstruction(current.getNext());
            h.assertTrue(current != null && current.getOpcode() == Opcodes.RETURN,
                    "Synthetic null AIR path reaches client world/classification/callback instead of returning unchanged");
        }
        System.out.println("DH_NULL_MATERIAL_COMPONENT compiled_null_path=immediate_return world_access=0 callback_access=0");
        h.succeed();
    }
    private static AbstractInsnNode nextInstruction(AbstractInsnNode node) {
        while (node != null && node.getOpcode() < 0) node = node.getNext();
        return node;
    }
    @GameTest(template = "empty", batch = "strong_realm_scope", timeoutTicks = 100)
    public static void actualV6ScopeRejectsLegacyAndUnloadedCandidates(GameTestHelper h) {
        var v6 = h.getLevel().getServer().getLevel(IslandWorld.TENSION_WORLD);
        var v5 = h.getLevel().getServer().getLevel(IslandWorld.VANILLA_WORLD);
        h.assertTrue(v6 != null && v5 != null && StrongRealmFauna.isV6(v6), "Actual V6 dimension and generator required");
        h.assertTrue(!StrongRealmFauna.isV6(v5) && !StrongRealmFauna.isV6(h.getLevel()), "Scope leaked into V5/Overworld");
        h.assertTrue(!StrongRealmFauna.trySpawnSkeleton(v5, BlockPos.ZERO)
                        && !StrongRealmFauna.trySpawnGhast(h.getLevel(), BlockPos.ZERO), "Natural spawns leaked into legacy worlds");
        var unloaded = new BlockPos(18_000_000, 96, 18_000_000);
        int before = v6.getChunkSource().getLoadedChunksCount();
        h.assertTrue(!StrongRealmFauna.cavernHabitat(v6, unloaded) && !StrongRealmFauna.airborneHabitat(v6, unloaded), "Unloaded habitat admitted");
        h.assertTrue(v6.getChunkSource().getLoadedChunksCount() == before, "Habitat inspection forced new chunks");
        boolean rule = v6.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        v6.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, v6.getServer());
        try { h.assertTrue(!StrongRealmFauna.spawningEnabled(v6) && !StrongRealmFauna.hasMonsterCapacity(v6), "doMobSpawning=false ignored"); }
        finally { v6.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(rule, v6.getServer()); }
        h.succeed();
    }
    @GameTest(template = "empty", batch = "strong_realm_habitats", timeoutTicks = 200)
    public static void loadedV6CavernsRejectLeavesLightAndInsufficientHeadroom(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(IslandWorld.TENSION_WORLD);
        h.assertTrue(level != null && StrongRealmFauna.isV6(level), "Actual V6 required for habitat rules");
        var center = new BlockPos(8_008, 60, 8_008);
        level.getChunk(center); // Explicit preparation of this disposable GameTest world, never production spawning.
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            for (int y = -1; y <= 24; y++) level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(center.below(), Interstice.RIFTSTONE.get().defaultBlockState(), 3);
        level.setBlock(center.above(5), Interstice.RIFTSTONE.get().defaultBlockState(), 3);
        h.assertTrue(StrongRealmFauna.cavernHabitat(level, center), "Prepared dry rock cave rejected");
        level.setBlock(center.above(5), Blocks.OAK_LEAVES.defaultBlockState(), 3);
        h.assertTrue(!StrongRealmFauna.cavernHabitat(level, center), "Tree roof admitted skeleton");
        level.setBlock(center.above(5), Interstice.RIFTSTONE.get().defaultBlockState(), 3);
        level.setBlock(center.above(2), Interstice.RIFTSTONE.get().defaultBlockState(), 3);
        h.assertTrue(!StrongRealmFauna.cavernHabitat(level, center), "Two-block cavity admitted tall skeleton");
        level.setBlock(center.above(2), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(center, Blocks.WATER.defaultBlockState(), 3);
        h.assertTrue(!StrongRealmFauna.cavernHabitat(level, center), "Wet cavity admitted skeleton");
        level.setBlock(center, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(center.relative(net.minecraft.core.Direction.EAST), Blocks.GLOWSTONE.defaultBlockState(), 3);
        h.runAfterDelay(15, () -> {
            h.assertTrue(!StrongRealmFauna.cavernHabitat(level, center), "Block light did not suppress cave skeleton");
            var air = center.above(45);
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
                for (int y = -2; y <= 6; y++) level.setBlock(air.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(StrongRealmFauna.airborneHabitat(level, air), "Prepared open air rejected");
            level.setBlock(air.above(3), Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(!StrongRealmFauna.airborneHabitat(level, air), "Roofed air pocket admitted ghast");
            h.succeed();
        });
    }
    @GameTest(template = "empty", batch = "strong_charge_range", timeoutTicks = 100)
    public static void upperToxinChargeHitsStationaryTargetsAtTenTwentyFourAndFortySevenBlocks(GameTestHelper h) {
        room(h); var entities = new ArrayList<Entity>(); cleanup(h, entities);
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 13, 5)));
        var owner = new UpperSeaGhast(StrongRealmFauna.UPPER_SEA_GHAST.get(), h.getLevel()); owner.setPos(origin); owner.setNoAi(true); entities.add(owner);
        int hits = 0;
        for (int range : new int[]{10, 24, 47}) for (int elevation : new int[]{-8, 0, 8}) {
            var target = zombie(h, origin.add(range, elevation - .9, 0), entities);
            Vec3 destination = target.getBoundingBox().getCenter();
            var projectile = new UpperSeaCharge(h.getLevel(), owner, origin, destination); entities.add(projectile);
            for (int tick = 0; tick < 70 && !projectile.isRemoved(); tick++) projectile.tick();
            h.assertTrue(projectile.isRemoved() && target.getHealth() == 14, "Upper charge fell short/missed or changed direct damage range=" + range + " elevation=" + elevation);
            h.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "Corrosive slowing status missing");
            h.assertTrue(!target.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "Upper toxin bypassed vanilla undead poison immunity");
            hits++; target.discard();
        }
        System.out.println("UPPER_TOXIN_PROJECTILE_RANGE hits=" + hits + " cases=stationary_real_collision_sweeps gravity_drop=0");
        h.succeed();
    }
    @GameTest(template = "empty", batch = "strong_charge_living_status", timeoutTicks = 100)
    public static void upperToxinChargePoisonsAndSlowsNonUndeadLivingTargets(GameTestHelper h) {
        room(h); var entities = new ArrayList<Entity>(); cleanup(h, entities);
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 8, 5)));
        var owner = new UpperSeaGhast(StrongRealmFauna.UPPER_SEA_GHAST.get(), h.getLevel());
        owner.setPos(origin); owner.setNoAi(true); entities.add(owner);
        var target = EntityType.COW.create(h.getLevel());
        h.assertTrue(target != null, "Living toxin-status fixture is absent");
        target.setNoAi(true); target.setNoGravity(true); target.setPersistenceRequired();
        target.setPos(origin.add(10, 0, 0)); h.getLevel().addFreshEntity(target); entities.add(target);
        float before = target.getHealth();
        var charge = new UpperSeaCharge(h.getLevel(), owner, origin, target.getBoundingBox().getCenter()); entities.add(charge);
        for (int tick = 0; tick < 30 && !charge.isRemoved(); tick++) charge.tick();
        h.assertTrue(charge.isRemoved() && target.getHealth() == before - 6, "Living target did not receive exactly six points of direct toxin damage");
        h.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.POISON)
                        && target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "Living target lost poison or slowing");
        h.succeed();
    }
    @GameTest(template = "empty", batch = "strong_charge_wall", timeoutTicks = 100)
    public static void upperToxinChargeCannotDamageThroughSolidShelterOrReplaceBlocks(GameTestHelper h) {
        room(h); var entities = new ArrayList<Entity>(); cleanup(h, entities);
        for (int z = 0; z < 12; z++) for (int y = 2; y < 29; y++) h.setBlock(new BlockPos(12, y, z), Blocks.STONE);
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 8, 5)));
        var owner = new UpperSeaGhast(StrongRealmFauna.UPPER_SEA_GHAST.get(), h.getLevel()); owner.setPos(origin); owner.setNoAi(true); entities.add(owner);
        var behindWall = zombie(h, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(13, 8, 5))), entities);
        var charge = new UpperSeaCharge(h.getLevel(), owner, origin, behindWall.getBoundingBox().getCenter()); entities.add(charge);
        for (int tick = 0; tick < 30 && !charge.isRemoved(); tick++) charge.tick();
        h.assertTrue(charge.isRemoved() && behindWall.getHealth() == 20
                && !behindWall.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "Toxin projectile/splash crossed a solid roof/wall");
        h.assertBlockPresent(Blocks.STONE, new BlockPos(12, 8, 5));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(11, 8, 5));
        h.assertTrue(!owner.fireAt(behindWall), "Attack ignored line of sight");
        h.succeed();
    }
    @GameTest(template = "empty", batch = "strong_charge_nbt", timeoutTicks = 100)
    public static void savedUpperChargeAgeIsBoundedAndExpiresWithoutChunkLoading(GameTestHelper h) {
        var entity = new UpperSeaCharge(StrongRealmFauna.UPPER_SEA_CHARGE.get(), h.getLevel());
        entity.setPos(h.absolutePos(new BlockPos(3, 8, 5)).getCenter());
        var tag = entity.saveWithoutId(new CompoundTag()); tag.putInt("UpperChargeAge", Integer.MAX_VALUE);
        var restored = new UpperSeaCharge(StrongRealmFauna.UPPER_SEA_CHARGE.get(), h.getLevel()); restored.load(tag);
        restored.tick(); h.assertTrue(restored.isRemoved(), "Malformed saved charge received unlimited life");
        tag.putInt("UpperChargeAge", -7); restored = new UpperSeaCharge(StrongRealmFauna.UPPER_SEA_CHARGE.get(), h.getLevel()); restored.load(tag);
        h.assertTrue(restored.saveWithoutId(new CompoundTag()).getInt("UpperChargeAge") == 0 && restored.isNoGravity(), "NBT age or gravity invalid");
        entity.discard(); restored.discard(); h.succeed();
    }
    @GameTest(template = "empty", batch = "strong_vanilla_equipment", timeoutTicks = 100)
    public static void vanillaWitherSkeletonEquipmentAndOrdinaryGhastRemainVanilla(GameTestHelper h) {
        var skeleton = EntityType.WITHER_SKELETON.create(h.getLevel());
        skeleton.moveTo(h.absolutePos(new BlockPos(4, 2, 5)).getBottomCenter());
        EventHooks.finalizeMobSpawn(skeleton, h.getLevel(), h.getLevel().getCurrentDifficultyAt(skeleton.blockPosition()), MobSpawnType.NATURAL, null);
        h.assertTrue(skeleton.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.STONE_SWORD)
                        && skeleton.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4, "Natural skeleton lost vanilla finalize behavior");
        var vanilla = new Ghast(EntityType.GHAST, h.getLevel());
        var local = new UpperSeaGhast(StrongRealmFauna.UPPER_SEA_GHAST.get(), h.getLevel());
        h.assertTrue(vanilla.getMaxHealth() == 10 && local.getMaxHealth() == 28, "Realm attributes altered vanilla ghasts");
        var saved = local.saveWithoutId(new CompoundTag()); local.setHealth(11); saved = local.saveWithoutId(new CompoundTag());
        var restored = new UpperSeaGhast(StrongRealmFauna.UPPER_SEA_GHAST.get(), h.getLevel()); restored.load(saved);
        h.assertTrue(restored.getHealth() == 11, "Saved wounded realm ghast healed");
        skeleton.discard(); vanilla.discard(); local.discard(); restored.discard(); h.succeed();
    }
    @GameTest(template = "empty", batch = "strong_ghast_ai", timeoutTicks = 180)
    public static void actualRangedAiWarnsBeforeFiringOnlyCustomToxinCharge(GameTestHelper h) {
        room(h); var entities = new ArrayList<Entity>(); cleanup(h, entities);
        var origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 8, 5)));
        var ghast = new UpperSeaGhast(StrongRealmFauna.UPPER_SEA_GHAST.get(), h.getLevel());
        ghast.setPersistenceRequired(); ghast.setNoGravity(true); ghast.setPos(origin);
        h.getLevel().addFreshEntity(ghast); entities.add(ghast);
        var target = zombie(h, origin.add(15, 0, 0), entities);
        // NoAi does not disable Zombie.aiStep's sunburn. Keep the intended
        // stationary zombie, but isolate toxin damage from daylight randomness.
        // A carved pumpkin has no armor points and never breaks from sunlight.
        target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        ghast.setTarget(target);
        int[] warningRun = {0}, warningBeforeShot = {-1}, firedAge = {-1};
        h.onEachTick(() -> {
            if (ghast.isCharging()) warningRun[0]++;
            // The native float goal can leave the finite GT structure bounds.
            // Follow the owner-bound charge itself rather than mistaking that
            // movement for a missing projectile or warning.
            for (var entity : h.getLevel().getAllEntities()) {
                h.assertTrue(!(entity instanceof LargeFireball ball) || ball.getOwner() != ghast, "Realm AI fired vanilla fireball");
                if (entity instanceof UpperSeaCharge charge && charge.getOwner() == ghast && firedAge[0] < 0) {
                    firedAge[0] = ghast.tickCount; warningBeforeShot[0] = warningRun[0]; entities.add(charge);
                }
            }
            if (!ghast.isCharging() && firedAge[0] < 0) warningRun[0] = 0;
            if (target.getHealth() < 20) {
                var source = target.getLastDamageSource();
                h.assertTrue(source != null && source.getDirectEntity() instanceof UpperSeaCharge
                        && source.getEntity() == ghast, "Stationary AI target was damaged by another source rather than its owner's toxin charge");
                h.assertTrue(firedAge[0] >= UpperSeaGhast.WINDUP_TICKS
                        && warningBeforeShot[0] >= UpperSeaGhast.WINDUP_TICKS - 10,
                        "Shot had no sustained native windup: ghastAge=" + firedAge[0] + " chargedTicks=" + warningBeforeShot[0] + " gameTestTick=" + h.getTick());
                System.out.println("UPPER_GHAST_NATIVE_AI shot_age=" + firedAge[0] + " consecutive_visible_warning=" + warningBeforeShot[0]
                        + " hit_source=own_upper_sea_charge no_vanilla_fireballs=true");
                h.succeed();
            }
        });
    }
}
