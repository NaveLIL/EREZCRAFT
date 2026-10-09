package pro.erez.interstice.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.fauna.CanopySentinel;
import pro.erez.interstice.fauna.RealmFauna;
import pro.erez.interstice.food.CrownFruitBlock;
import pro.erez.interstice.food.TideHeart;
import pro.erez.interstice.worldgen.GardenMaterials;

/**
 * Real server ticks on prepared Overworld platforms, with explicitly initialized crown homes.
 * TestPlayers uses NeoForge mock connections: these are AI/serialization GameTests, not a
 * real-network, naturally spawned, or independent Survival acceptance run. Each batch is
 * separate because a twenty-block territory extends beyond the sixteen-block fixture.
 */
@GameTestHolder("interstice_fauna")
@PrefixGameTestTemplate(false)
public final class CanopySentinelGameTests {
    private static final BlockPos HOME = new BlockPos(8, 2, 8);

    private static final class Fixture {
        final GameTestHelper h;
        final BlockPos home;
        final CanopySentinel mob;
        final List<CanopySentinel> mobs = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
        boolean closed;

        Fixture(GameTestHelper h, boolean raised) {
            this.h = h;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= 10; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
            BlockPos localHome = raised ? HOME.above(3) : HOME;
            this.home = h.absolutePos(localHome);
            if (raised) {
                h.setBlock(HOME, GardenMaterials.CROWN_LOG.get());
                h.setBlock(HOME.above(), GardenMaterials.CROWN_LOG.get());
            } else h.setBlock(HOME.below().west(), GardenMaterials.CROWN_LOG.get());
            h.setBlock(localHome.below(), leaf());
            this.mob = createMob();
            mob.moveTo(home.getX() + .5, home.getY(), home.getZ() + .5, 0, 0);
            mob.initializeHome(home);
            h.assertTrue(home.equals(mob.home()), "Explicit valid crown home was rejected");
            h.assertTrue(h.getLevel().addFreshEntity(mob), "Prepared sentinel was not added to the real server level");
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { close(); }
            });
        }

        CanopySentinel createMob() {
            var created = RealmFauna.CANOPY_SENTINEL.get().create(h.getLevel());
            h.assertTrue(created != null, "Registered sentinel entity type cannot create its native entity");
            var sentinel = Objects.requireNonNull(created);
            sentinel.setPersistenceRequired();
            mobs.add(sentinel);
            return sentinel;
        }

        ServerPlayer player(int x, boolean crouching) {
            var player = TestPlayers.create(h, new BlockPos(x, 2, 8), GameType.SURVIVAL);
            player.setShiftKeyDown(crouching);
            players.add(player);
            return player;
        }

        void close() {
            if (closed) return;
            closed = true;
            mobs.forEach(CanopySentinel::discard);
            players.forEach(player -> {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) == player) TestPlayers.remove(player);
            });
        }
    }

    private static net.minecraft.world.level.block.state.BlockState leaf() {
        return GardenMaterials.CROWN_LEAVES.get().defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 1).setValue(LeavesBlock.PERSISTENT, true);
    }

    private static boolean phase(CanopySentinel mob, String name) {
        return mob.phase().name().equals(name);
    }

    private static boolean returning(CanopySentinel mob) {
        return phase(mob, "RETURN") || phase(mob, "COOLDOWN");
    }

    private static void relocate(Fixture fixture, ServerPlayer player, double x, double y, double z) {
        player.teleportTo(fixture.h.getLevel(), x, y, z, Set.of(), 0, 0);
        player.hasChangedDimension();
    }

    private static CompoundTag save(CanopySentinel mob) {
        return mob.saveWithoutId(new CompoundTag());
    }

    private static void harvestPreparedRipeFruit(Fixture fixture, ServerPlayer player, BlockPos fruit) {
        var h = fixture.h;
        h.getLevel().setBlock(fruit.below(), leaf(), 3);
        h.getLevel().setBlock(fruit, GardenMaterials.CROWN_FRUIT.get().defaultBlockState().setValue(CrownFruitBlock.AGE, 3), 3);
        int before = player.getInventory().countItem(TideHeart.FRUIT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(fruit), Direction.UP, fruit, false));
        h.assertTrue(h.getLevel().getBlockState(fruit).getValue(CrownFruitBlock.AGE) == 0
                        && player.getInventory().countItem(TideHeart.FRUIT.get()) == before + 1,
                "Prepared ripe fruit did not perform its real AGE3-to-AGE0 harvest and inventory transfer");
    }

    @GameTest(template = "empty", batch = "canopy_warning", timeoutTicks = 140)
    public static void ordinaryApproachHasARealWarningBeforeNativeMeleeDamage(GameTestHelper h) {
        var f = new Fixture(h, false);
        var player = f.player(9, false);
        float health = player.getHealth();
        long[] firstWarning = {-1};
        h.onEachTick(() -> {
            if (phase(f.mob, "WARNING")) {
                if (firstWarning[0] < 0) firstWarning[0] = h.getTick();
                h.assertTrue(player.getHealth() == health, "Sentinel damaged its target during the visible warning");
            }
        });
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(phase(f.mob, "WARNING") && f.mob.getTarget() == player, "Ordinary standing visitor did not receive a warning");
        }).thenExecute(() -> {
            h.assertTrue(f.mob.warningTicksRemaining() > 0 && f.mob.warningTicksRemaining() <= CanopySentinel.WARNING_TICKS,
                    "New warning has no finite remaining timer");
        }).thenWaitUntil(() -> {
            h.assertTrue(phase(f.mob, "CHASE") && f.mob.getTarget() == player, "Real ticks did not finish the warning into a chase");
        }).thenExecute(() -> {
            // Observing occurs after entity ticks, so the first observed warning can be one tick old.
            h.assertTrue(firstWarning[0] >= 0 && h.getTick() - firstWarning[0] >= CanopySentinel.WARNING_TICKS - 1,
                    "Chase skipped the required warning interval");
        }).thenWaitUntil(() -> {
            h.assertTrue(player.getHealth() < health, "Native chase never performed a damaging melee attack");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "canopy_crouching", timeoutTicks = 170)
    public static void crouchingVisitorIsIgnoredAndCrouchingDuringChaseReleasesTheTarget(GameTestHelper h) {
        var f = new Fixture(h, false);
        var player = f.player(9, true);
        float[] health = {player.getHealth()};
        h.startSequence().thenIdle(30).thenExecute(() -> {
            h.assertTrue(f.mob.getTarget() == null && phase(f.mob, "CALM") && player.getHealth() == health[0],
                    "Quiet crouching visitor provoked territorial aggression");
            player.setShiftKeyDown(false);
        }).thenWaitUntil(() -> {
            h.assertTrue(f.mob.getTarget() == player && phase(f.mob, "CHASE"), "Standing visitor never entered native chase");
        }).thenExecute(() -> {
            player.setShiftKeyDown(true);
            health[0] = player.getHealth();
        }).thenWaitUntil(() -> {
            h.assertTrue(f.mob.getTarget() == null && returning(f.mob), "Crouching did not release a current chase target");
        }).thenIdle(12).thenExecute(() -> {
            h.assertTrue(f.mob.getTarget() == null && player.getHealth() == health[0], "Released crouching visitor was attacked or reacquired");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "canopy_target_hold", timeoutTicks = 130)
    public static void nearerSecondHarvesterDoesNotReplaceAValidTargetDuringMinimumHold(GameTestHelper h) {
        var f = new Fixture(h, false);
        var first = f.player(11, false);
        BlockPos fruit = f.home.east();
        harvestPreparedRipeFruit(f, first, fruit);
        f.mob.observeFruitHarvest(first, fruit);
        h.assertTrue(f.mob.getTarget() == first && phase(f.mob, "WARNING"), "Successful first harvest did not begin a warning");
        // The public harvest notification gives a known selection tick, avoiding scan-frame uncertainty.
        long selected = h.getTick();
        var second = f.player(9, false);
        harvestPreparedRipeFruit(f, second, fruit);
        f.mob.observeFruitHarvest(second, fruit);
        h.startSequence().thenExecuteFor(CanopySentinel.MIN_TARGET_HOLD, () -> {
            h.assertTrue(first.isAlive() && f.mob.getTarget() == first,
                    "A nearby second harvester replaced a still-valid target before minimum hold elapsed");
        }).thenExecute(() -> {
            h.assertTrue(h.getTick() - selected >= CanopySentinel.MIN_TARGET_HOLD,
                    "Target-hold observation did not use actual server ticks");
        }).thenSucceed();
    }

    private static void invalidTarget(Fixture f, Consumer<ServerPlayer> invalidate) {
        var h = f.h;
        var player = f.player(9, false);
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(phase(f.mob, "CHASE") && f.mob.getTarget() == player, "No live chase exists before target invalidation");
        }).thenExecute(() -> invalidate.accept(player)).thenWaitUntil(() -> {
            h.assertTrue(f.mob.getTarget() == null && returning(f.mob), "Invalid target remained attached to a chase");
        }).thenIdle(8).thenExecute(() -> {
            h.assertTrue(f.mob.getTarget() == null && !phase(f.mob, "CHASE"), "Released invalid target was reacquired");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "canopy_dead_target", timeoutTicks = 130)
    public static void realPlayerDeathReleasesTheChase(GameTestHelper h) {
        invalidTarget(new Fixture(h, false), player -> player.setHealth(0));
    }

    @GameTest(template = "empty", batch = "canopy_dimension_target", timeoutTicks = 130)
    public static void actualPlayerDimensionTransferReleasesTheChase(GameTestHelper h) {
        invalidTarget(new Fixture(h, false), player -> {
            var other = h.getLevel().getServer().getLevel(Level.NETHER);
            h.assertTrue(other != null, "Native Nether dimension is required for dimension-transfer coverage");
            player.teleportTo(Objects.requireNonNull(other), .5, 80, .5, Set.of(), 0, 0);
            player.hasChangedDimension();
        });
    }

    @GameTest(template = "empty", batch = "canopy_disconnected_target", timeoutTicks = 130)
    public static void mockConnectionDisconnectAndServerRemovalReleaseTheChase(GameTestHelper h) {
        invalidTarget(new Fixture(h, false), player -> {
            player.connection.disconnect(Component.literal("Canopy sentinel GameTest disconnect"));
            TestPlayers.remove(player);
        });
    }

    @GameTest(template = "empty", batch = "canopy_radius_cooldown", timeoutTicks = 440)
    public static void visitorOutsideChaseRadiusCausesReturnAndFiniteNonAttackingCooldown(GameTestHelper h) {
        var f = new Fixture(h, false);
        var player = f.player(9, false);
        int[] initialCooldown = {0};
        float[] health = {player.getHealth()};
        h.onEachTick(() -> {
            if (initialCooldown[0] > 0 && phase(f.mob, "COOLDOWN")) {
                h.assertTrue(f.mob.getTarget() == null && player.getHealth() == health[0], "Cooldown attacked or retained a player target");
            }
        });
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(phase(f.mob, "CHASE") && f.mob.getTarget() == player, "No native chase exists before boundary exit");
        }).thenExecute(() -> {
            relocate(f, player, f.home.getX() + CanopySentinel.CHASE_RADIUS + 4.5, f.home.getY() + 2, f.home.getZ() + .5);
        }).thenWaitUntil(() -> {
            h.assertTrue(f.mob.getTarget() == null && returning(f.mob), "Visitor beyond the chase boundary was not released");
        }).thenWaitUntil(() -> {
            h.assertTrue(phase(f.mob, "COOLDOWN"), "Return did not finish into bounded cooldown");
        }).thenExecute(() -> {
            initialCooldown[0] = f.mob.cooldownTicksRemaining();
            h.assertTrue(initialCooldown[0] > CanopySentinel.COOLDOWN_TICKS / 2
                            && initialCooldown[0] <= CanopySentinel.COOLDOWN_TICKS,
                    "Return has no normal finite cooldown");
            relocate(f, player, f.home.getX() + 1.5, f.home.getY(), f.home.getZ() + .5);
            health[0] = player.getHealth();
        }).thenIdle(CanopySentinel.COOLDOWN_TICKS / 2).thenExecute(() -> {
            h.assertTrue(phase(f.mob, "COOLDOWN") && f.mob.cooldownTicksRemaining() > 0
                            && f.mob.cooldownTicksRemaining() < initialCooldown[0] && f.mob.getTarget() == null,
                    "Cooldown neither counted real ticks nor suppressed a returned visitor");
        }).thenWaitUntil(() -> {
            h.assertTrue(phase(f.mob, "WARNING") && f.mob.getTarget() == player, "Expired cooldown never allows a new complete warning");
        }).thenExecute(() -> h.assertTrue(player.getHealth() == health[0], "New warning immediately dealt cooldown-delayed damage")).thenSucceed();
    }

    @GameTest(template = "empty", batch = "canopy_chase_limit", timeoutTicks = 310)
    public static void validSurvivalTargetCannotKeepAChaseRunningBeyondItsMaximum(GameTestHelper h) {
        var f = new Fixture(h, false);
        // A real resistance effect and low perimeter keep this long-running target alive and local.
        // They do not change game mode, entity AI, target validity, gravity, or chase timers.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            if (x == 0 || x == 15 || z == 0 || z == 15) h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        }
        var player = f.player(11, false);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 310, 4, false, false));
        long[] chaseBegan = {-1};
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(phase(f.mob, "CHASE") && f.mob.getTarget() == player, "Long pursuit never began after warning");
        }).thenExecute(() -> chaseBegan[0] = h.getTick()).thenWaitUntil(() -> {
            h.assertTrue(returning(f.mob) && f.mob.getTarget() == null, "Maximum chase duration never released a valid local target");
        }).thenExecute(() -> {
            h.assertTrue(player.isAlive() && player.level() == h.getLevel() && !player.isShiftKeyDown()
                            && player.position().distanceTo(Vec3.atBottomCenterOf(f.home)) < CanopySentinel.HOME_RADIUS,
                    "Long chase ended because its target became invalid, not because of the chase limit");
            long elapsed = h.getTick() - chaseBegan[0];
            h.assertTrue(elapsed >= CanopySentinel.MAX_CHASE_TICKS - 1 && elapsed <= CanopySentinel.MAX_CHASE_TICKS + 2,
                    "Valid local chase did not stop at its documented finite limit");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "canopy_felled_home", timeoutTicks = 180)
    public static void removedBranchAndLeafUseGravityThenDisableTerritorialGroundAggression(GameTestHelper h) {
        var f = new Fixture(h, true);
        var player = f.player(11, true);
        double originalY = f.mob.getY();
        h.startSequence().thenIdle(5).thenExecute(() -> {
            h.assertTrue(f.mob.getTarget() == null && Math.abs(f.mob.getY() - originalY) < .15, "Raised crown fixture was not initially supported and calm");
            h.getLevel().removeBlock(f.home.below(2), false);
            h.getLevel().removeBlock(f.home.below(3), false);
            h.getLevel().removeBlock(f.home.below(), false);
        }).thenWaitUntil(() -> {
            h.assertTrue(f.mob.onGround() && f.mob.getY() <= originalY - 2.5, "Sentinel floated after its real supporting crown was removed");
        }).thenExecute(() -> {
            player.setShiftKeyDown(false);
            h.assertTrue(!save(f.mob).getBoolean("CanopyResident"), "Ground fallback retained territorial canopy residency");
        }).thenIdle(40).thenExecute(() -> {
            h.assertTrue(f.mob.isAlive() && f.mob.getTarget() == null && !phase(f.mob, "WARNING") && !phase(f.mob, "CHASE")
                            && player.getHealth() == player.getMaxHealth(),
                    "Felled crown caused death, floating, or renewed ground-level territorial attacks");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "canopy_warning_nbt", timeoutTicks = 160)
    public static void nativeNbtKeepsPartialWarningHomeTimersAndTargetUuidThenResolvesCurrentPlayer(GameTestHelper h) {
        var f = new Fixture(h, false);
        var player = f.player(9, false);
        float health = player.getHealth();
        CompoundTag[] saved = {null};
        CanopySentinel[] restored = {null};
        h.onEachTick(() -> {
            if (restored[0] != null && phase(restored[0], "WARNING"))
                h.assertTrue(player.getHealth() == health, "Reloaded partial warning attacked before warning completion");
        });
        h.startSequence().thenWaitUntil(() -> h.assertTrue(phase(f.mob, "WARNING") && f.mob.getTarget() == player, "No warning exists to persist"))
                .thenIdle(8).thenExecute(() -> {
                    saved[0] = save(f.mob);
                    h.assertTrue(saved[0].hasUUID("CanopyTarget") && saved[0].getUUID("CanopyTarget").equals(player.getUUID())
                                    && f.mob.warningTicksRemaining() > 0 && f.mob.warningTicksRemaining() < CanopySentinel.WARNING_TICKS,
                            "Saved fixture has no real partial warning or target UUID");
                    f.mob.discard();
                }).thenIdle(2).thenExecute(() -> {
                    restored[0] = f.createMob();
                    restored[0].load(saved[0]);
                    CompoundTag roundTrip = save(restored[0]);
                    h.assertTrue(f.home.equals(restored[0].home()) && phase(restored[0], "WARNING")
                                    && restored[0].warningTicksRemaining() == saved[0].getInt("WarningRemaining")
                                    && restored[0].cooldownTicksRemaining() == saved[0].getInt("CooldownRemaining")
                                    && roundTrip.getString("CanopyHomeDimension").equals(saved[0].getString("CanopyHomeDimension"))
                                    && roundTrip.getBoolean("CanopyResident") == saved[0].getBoolean("CanopyResident")
                                    && roundTrip.hasUUID("CanopyTarget")
                                    && roundTrip.getUUID("CanopyTarget").equals(player.getUUID())
                                    && roundTrip.getInt("TargetHold") == saved[0].getInt("TargetHold")
                                    && roundTrip.getInt("ChaseElapsed") == saved[0].getInt("ChaseElapsed")
                                    && roundTrip.getInt("ReturnElapsed") == saved[0].getInt("ReturnElapsed"),
                            "Native entity load lost home, phase, remaining warning, hold/chase timers, or target identity");
                    h.assertTrue(h.getLevel().addFreshEntity(restored[0]), "Restored native sentinel did not enter the server level");
                }).thenWaitUntil(() -> h.assertTrue(restored[0].getTarget() == player, "Saved target UUID did not resolve the current server player"))
                .thenWaitUntil(() -> h.assertTrue(player.getHealth() < health, "Loaded warning/chase never resumed native melee"))
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "canopy_cooldown_nbt", timeoutTicks = 220)
    public static void nativeNbtKeepsSpentCooldownInsteadOfRestartingItOrImmediatelyReacquiring(GameTestHelper h) {
        var f = new Fixture(h, false);
        var player = f.player(9, false);
        CompoundTag[] saved = {null};
        CanopySentinel[] restored = {null};
        float[] health = {player.getHealth()};
        h.startSequence().thenWaitUntil(() -> h.assertTrue(phase(f.mob, "CHASE"), "No chase exists before cooldown save"))
                .thenExecute(() -> player.setShiftKeyDown(true))
                .thenWaitUntil(() -> h.assertTrue(phase(f.mob, "COOLDOWN"), "Released chase never reached cooldown"))
                .thenIdle(30).thenExecute(() -> {
                    saved[0] = save(f.mob);
                    h.assertTrue(f.mob.cooldownTicksRemaining() > 0 && f.mob.cooldownTicksRemaining() < CanopySentinel.COOLDOWN_TICKS,
                            "Save did not contain a genuinely spent cooldown");
                    f.mob.discard();
                }).thenIdle(2).thenExecute(() -> {
                    restored[0] = f.createMob();
                    restored[0].load(saved[0]);
                    h.assertTrue(phase(restored[0], "COOLDOWN") && restored[0].home().equals(f.home)
                                    && restored[0].cooldownTicksRemaining() == saved[0].getInt("CooldownRemaining"),
                            "Native load restarted or dropped a remaining cooldown/home");
                    h.assertTrue(h.getLevel().addFreshEntity(restored[0]), "Restored cooldown entity could not enter the real level");
                    player.setShiftKeyDown(false);
                    health[0] = player.getHealth();
                }).thenIdle(12).thenExecute(() -> {
                    h.assertTrue(phase(restored[0], "COOLDOWN") && restored[0].getTarget() == null
                                    && restored[0].cooldownTicksRemaining() < saved[0].getInt("CooldownRemaining")
                                    && player.getHealth() == health[0],
                            "Loaded cooldown froze, reacquired immediately, or damaged its visitor");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "canopy_absent_target_nbt", timeoutTicks = 100)
    public static void nativeNbtWithDisconnectedTargetClearsIdentityOnActualServerTicks(GameTestHelper h) {
        var f = new Fixture(h, false);
        var player = f.player(9, false);
        CompoundTag[] saved = {null};
        CanopySentinel[] restored = {null};
        h.startSequence().thenWaitUntil(() -> h.assertTrue(phase(f.mob, "WARNING") && f.mob.getTarget() == player, "No native target exists to save"))
                .thenExecute(() -> {
                    saved[0] = save(f.mob);
                    f.mob.discard();
                    TestPlayers.remove(player);
                }).thenIdle(2).thenExecute(() -> {
                    restored[0] = f.createMob();
                    restored[0].load(saved[0]);
                    h.assertTrue(h.getLevel().addFreshEntity(restored[0]), "Absent-target restored entity could not enter the real level");
                }).thenWaitUntil(() -> {
                    h.assertTrue(restored[0].getTarget() == null && returning(restored[0])
                                    && !save(restored[0]).hasUUID("CanopyTarget"),
                            "Saved disconnected target UUID remained armed after actual resolution ticks");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "canopy_malformed_nbt", timeoutTicks = 80)
    public static void malformedPhasesTimersAndUnresolvableTargetsCannotProduceImmediateAttacks(GameTestHelper h) {
        var f = new Fixture(h, false);
        var player = f.player(11, false);
        float health = player.getHealth();
        CompoundTag template = save(f.mob);
        f.mob.discard();
        List<CanopySentinel> restored = new ArrayList<>();
        h.startSequence().thenIdle(2).thenExecute(() -> {
            for (int sample = 0; sample < 2; sample++) {
                var broken = template.copy();
                // These two malformed samples are distinct real entities, not duplicate UUIDs.
                broken.putUUID("UUID", UUID.randomUUID());
                broken.putInt("CanopyPhase", sample == 0 ? Integer.MAX_VALUE : CanopySentinel.Phase.CHASE.ordinal());
                broken.putInt("WarningRemaining", Integer.MIN_VALUE);
                broken.putInt("TargetHold", Integer.MAX_VALUE);
                broken.putInt("ChaseElapsed", Integer.MIN_VALUE);
                broken.putInt("ReturnElapsed", Integer.MAX_VALUE);
                broken.putInt("CooldownRemaining", Integer.MIN_VALUE);
                if (sample == 0) {
                    broken.putUUID("CanopyTarget", UUID.randomUUID());
                    broken.putString("CanopyHomeDimension", "%%%invalid-dimension%%%");
                } else broken.remove("CanopyTarget");
                var loaded = f.createMob();
                loaded.load(broken);
                h.assertTrue(loaded.warningTicksRemaining() >= 0 && loaded.warningTicksRemaining() <= CanopySentinel.WARNING_TICKS
                                && loaded.cooldownTicksRemaining() >= 0 && loaded.cooldownTicksRemaining() <= CanopySentinel.COOLDOWN_TICKS,
                        "Malformed NBT left unbounded or negative public timers");
                h.assertTrue(h.getLevel().addFreshEntity(loaded), "Malformed-data entity could not enter a real level safely");
                restored.add(loaded);
            }
        }).thenExecuteFor(12, () -> {
            h.assertTrue(player.getHealth() == health, "Malformed NBT authorized damage without a new complete warning");
        }).thenExecute(() -> {
            for (var mob : restored) h.assertTrue(!phase(mob, "CHASE") && mob.getTarget() == null,
                    "Malformed phase or missing/unresolvable target remained an armed chase");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "canopy_legacy_spawn_guard", timeoutTicks = 40)
    public static void preparedRealCrownAndFruitNeverPermitNaturalSpawningInOrdinaryOverworld(GameTestHelper h) {
        var f = new Fixture(h, false);
        h.assertTrue(h.getLevel().dimension().equals(Level.OVERWORLD) && !RealmFauna.isV6(h.getLevel()),
                "Legacy spawn guard fixture must be the ordinary native Overworld");
        h.setBlock(new BlockPos(5, 1, 8), GardenMaterials.CROWN_LOG.get());
        for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++) h.setBlock(new BlockPos(x, 1, z), leaf());
        BlockPos fruit = h.absolutePos(new BlockPos(10, 2, 8));
        var ripe = GardenMaterials.CROWN_FRUIT.get().defaultBlockState().setValue(CrownFruitBlock.AGE, 3);
        h.getLevel().setBlock(fruit, ripe, 3);
        h.assertTrue(ripe.canSurvive(h.getLevel(), fruit), "Legacy guard scene lacks actual valid crown fruit support");
        var area = new AABB(f.home).inflate(RealmFauna.LOCAL_RADIUS);
        int before = h.getLevel().getEntitiesOfClass(CanopySentinel.class, area).size();
        h.assertTrue(!RealmFauna.spawnInLoadedCrown(h.getLevel(), new ChunkPos(f.home)),
                "Prepared real crown/fruit bypassed the strict legacy-dimension natural-spawn guard");
        h.assertTrue(h.getLevel().getEntitiesOfClass(CanopySentinel.class, area).size() == before,
                "Rejected legacy spawn still added an entity to the ordinary Overworld");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "canopy_population_nbt", timeoutTicks = 40)
    public static void populationNativeNbtDeduplicatesPositiveAndNegativeChunksAndNeverReopensOccupiedKeys(GameTestHelper h) {
        long first = new ChunkPos(5, 9).toLong();
        long negative = new ChunkPos(-7, -12).toLong();
        long untouched = new ChunkPos(5, -12).toLong();
        var data = new RealmFauna.PopulationData();
        h.assertTrue(!data.inhabited(first) && !data.inhabited(negative), "New population ledger contains unmarked occupied chunks");
        data.mark(first);
        data.mark(first);
        data.mark(negative);
        data.mark(negative);
        CompoundTag encoded = data.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(encoded.getLongArray("InhabitedCrownChunks").length == 2,
                "Duplicate population marks were serialized as additional occupied entries");
        var loaded = RealmFauna.PopulationData.load(encoded, h.getLevel().registryAccess());
        h.assertTrue(loaded.inhabited(first) && loaded.inhabited(negative) && !loaded.inhabited(untouched),
                "Native population load lost a positive/negative key or invented a new occupied chunk");
        loaded.mark(first);
        loaded.mark(negative);
        CompoundTag secondSave = loaded.save(new CompoundTag(), h.getLevel().registryAccess());
        var reloaded = RealmFauna.PopulationData.load(secondSave, h.getLevel().registryAccess());
        h.assertTrue(secondSave.getLongArray("InhabitedCrownChunks").length == 2
                        && reloaded.inhabited(first) && reloaded.inhabited(negative) && !reloaded.inhabited(untouched),
                "Reload or duplicate marks reopened a previously occupied crown chunk or duplicated its key");
        h.succeed();
    }

    private CanopySentinelGameTests() {}
}
