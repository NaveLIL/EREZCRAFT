package pro.erez.interstice.test;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.RealmGear;
import pro.erez.interstice.tide.BuoyancyController;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideState;
import pro.erez.interstice.worldgen.IslandWorld;

/** Native registered entity ticks in isolated fixtures, not a claimed two-client flight test. */
@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class V6TideTrajectoryGameTests {
    private static final float TINY = .000001F;

    /** Removing goals retains native travel; NoAI would disable isEffectiveAi and gravity movement. */
    private static final class MotionZombie extends Zombie {
        private MotionZombie(Level level) {
            super(EntityType.ZOMBIE, level);
            goalSelector.removeAllGoals(goal -> true);
            targetSelector.removeAllGoals(goal -> true);
            setPersistenceRequired();
            setInvulnerable(true);
        }
        private void startGliding() { setSharedFlag(7, true); }
    }

    private static void clear(ServerLevel world, int x, int z, int radius, int bottom, int top) {
        world.getChunk(x >> 4, z >> 4);
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) for (int y = bottom; y <= top; y++)
            world.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 3);
    }

    private static MotionZombie actor(GameTestHelper h, int localX, int y) {
        var column = h.absolutePos(new BlockPos(localX, 0, 6));
        clear(h.getLevel(), column.getX(), column.getZ(), 0, y - 40, y + 42);
        var zombie = new MotionZombie(h.getLevel());
        zombie.moveTo(column.getX() + .5, y, column.getZ() + .5, 0, 0);
        h.assertTrue(h.getLevel().addFreshEntity(zombie) && zombie.isEffectiveAi() && !zombie.isNoGravity(),
                "A registered native actor must retain real travel and gravity instead of frozen NoAI/NoGravity flags");
        return zombie;
    }

    private static void remove(Consumer<?> listener, Entity... actors) {
        NeoForge.EVENT_BUS.unregister(listener);
        for (var actor : actors) if (actor != null && !actor.isRemoved()) actor.discard();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeCalmTinyOldOffsetMidpointAndPeakTrajectoriesSeparateTheBoundaryFromTheAcceptedPeak(GameTestHelper h) {
        var calm = actor(h, 1, 120);
        var tiny = actor(h, 4, 120);
        var oldOffset = actor(h, 7, 120);
        var midpoint = actor(h, 10, 120);
        var peak = actor(h, 13, 120);
        Consumer<EntityTickEvent.Pre> wind = event -> {
            var entity = event.getEntity();
            if (entity == calm) BuoyancyController.applyEntityBuoyancy(calm, 0);
            else if (entity == tiny) BuoyancyController.applyEntityBuoyancy(tiny, TINY);
            else if (entity == midpoint) BuoyancyController.applyEntityBuoyancy(midpoint, .5F);
            else if (entity == peak) BuoyancyController.applyEntityBuoyancy(peak, 1);
            else if (entity == oldOffset) oldOffset.getAttribute(Attributes.GRAVITY).addOrUpdateTransientModifier(
                    new AttributeModifier(BuoyancyController.BUOYANCY_ID, -.08 - .028 * TINY, AttributeModifier.Operation.ADD_VALUE));
        };
        NeoForge.EVENT_BUS.addListener(wind);
        h.runAtTickTime(30, () -> {
            try {
                for (var zombie : List.of(calm, tiny, oldOffset, midpoint, peak))
                    h.assertTrue(zombie.tickCount >= 20 && zombie.tickCount <= 32, "The trajectory must contain actual registered entity ticks: " + zombie.tickCount);
                double calmDrop = 120 - calm.getY(), tinyDrop = 120 - tiny.getY(), middleDrop = 120 - midpoint.getY(), peakRise = peak.getY() - 120;
                System.out.println("V6_TIDE_NATIVE_TRAJECTORIES ticks=" + calm.tickCount + " calm_drop=" + calmDrop + " tiny_drop=" + tinyDrop
                        + " old_offset_drop=" + (120 - oldOffset.getY()) + " middle_drop=" + middleDrop + " peak_rise=" + peakRise);
                h.assertTrue(calmDrop > 15 && calmDrop < 45 && Math.abs(tinyDrop - calmDrop) < .02
                        && Math.abs(tiny.getDeltaMovement().y - calm.getDeltaMovement().y) < .002,
                        "An almost-zero tide must follow ordinary native falling position and velocity without a sudden suspension");
                h.assertTrue(Math.abs(oldOffset.getY() - 120) < .02 && oldOffset.getY() - tiny.getY() > 15,
                        "The old constant gravity offset must visibly reproduce the boundary suspension that this fix removes");
                h.assertTrue(middleDrop > 3 && middleDrop < calmDrop && midpoint.getDeltaMovement().y < 0,
                        "Half intensity must restore slower ordinary descent before the phase reaches zero");
                h.assertTrue(peakRise > 4 && peakRise < 20 && peak.getDeltaMovement().y > 0,
                        "The accepted full tide must still produce bounded upward motion under native travel");
            } finally {
                remove(wind, calm, tiny, oldOffset, midpoint, peak);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeEbbTailToZeroKeepsFallingVelocityAndAccumulatedFallDistance(GameTestHelper h) {
        var calm = actor(h, 4, 120);
        var tail = actor(h, 10, 120);
        for (var zombie : List.of(calm, tail)) {
            zombie.setDeltaMovement(0, -.3, 0);
            zombie.fallDistance = 8;
        }
        Consumer<EntityTickEvent.Pre> wind = event -> {
            if (event.getEntity() == calm) BuoyancyController.applyEntityBuoyancy(calm, 0);
            else if (event.getEntity() == tail) BuoyancyController.applyEntityBuoyancy(tail, tail.tickCount < 10 ? TINY : 0);
        };
        NeoForge.EVENT_BUS.addListener(wind);
        h.runAtTickTime(22, () -> {
            try {
                h.assertTrue(calm.tickCount >= 15 && tail.tickCount == calm.tickCount && tail.getY() < 115
                        && Math.abs(tail.getY() - calm.getY()) < .02 && Math.abs(tail.getDeltaMovement().y - calm.getDeltaMovement().y) < .002,
                        "The end of EBB must retain the native falling trajectory rather than change velocity at the zero boundary");
                h.assertTrue(tail.fallDistance > 8 && calm.fallDistance > 8
                        && !tail.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID),
                        "Removing the final almost-zero force must preserve accumulated fall risk and remove its transient modifier");
            } finally {
                remove(wind, calm, tail);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void enteringAndBreakingARealRoofReversesNativeMotionWithoutWipingDownwardFalls(GameTestHelper h) {
        var zombie = actor(h, 7, 60);
        var roof = new BlockPos(zombie.getBlockX(), 66, zombie.getBlockZ());
        double[] exposed = {0};
        Consumer<EntityTickEvent.Pre> wind = event -> {
            if (event.getEntity() == zombie) BuoyancyController.applyEntityBuoyancy(zombie, 1);
        };
        NeoForge.EVENT_BUS.addListener(wind);
        h.runAtTickTime(10, () -> {
            try {
                h.assertTrue(zombie.tickCount >= 8 && zombie.getY() > 60.5 && zombie.getDeltaMovement().y > 0, "The exposed native actor must first rise");
                exposed[0] = zombie.getY();
                h.getLevel().setBlock(roof, Blocks.STONE.defaultBlockState(), 3);
                h.assertTrue(ShelterDetector.isSheltered(h.getLevel(), zombie), "A real roof in the profile's permitted band must become shelter immediately");
            } catch (RuntimeException | Error failure) {
                remove(wind, zombie);
                throw failure;
            }
        });
        h.runAtTickTime(20, () -> {
            try {
                h.assertTrue(zombie.getDeltaMovement().y < -.2 && zombie.getY() < exposed[0]
                        && !zombie.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID),
                        "Native travel beneath the roof must restore downward motion and remove tide lift");
                zombie.fallDistance = 12;
                h.getLevel().setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
            } catch (RuntimeException | Error failure) {
                remove(wind, zombie);
                throw failure;
            }
        });
        h.runAtTickTime(25, () -> {
            try {
                h.assertTrue(zombie.getDeltaMovement().y < 0 && zombie.fallDistance > 0,
                        "Restored upward force cannot globally erase fall distance while the actor is still falling");
            } catch (RuntimeException | Error failure) {
                remove(wind, zombie);
                throw failure;
            }
        });
        h.runAtTickTime(48, () -> {
            try {
                h.assertTrue(zombie.getDeltaMovement().y > 0 && zombie.fallDistance == 0 && !ShelterDetector.isSheltered(h.getLevel(), zombie),
                        "After the actual falling motion is arrested, a broken roof must allow bounded rise and the existing upward-only fall reset");
            } finally {
                h.getLevel().setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
                remove(wind, zombie);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void heldBallastKeepsNativeFeetGroundedAndOnlyPaysExposedActiveSeconds(GameTestHelper h) {
        var player = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        var world = player.server.getLevel(IslandWorld.TALL_WORLD);
        h.assertTrue(world != null, "The retained tall geometry is required for a real held-ballast check");
        int x = 1136, z = 1136;
        var profile=pro.erez.interstice.geometry.GeometryProfiles.get(world);
        int roofLimit=(int)Math.floor(pro.erez.interstice.SeaSurface.cellMinimum(profile,x,z,true))-profile.clearance();
        clear(world, x, z, 1, 99, roofLimit+1);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) world.setBlock(new BlockPos(x + dx, 99, z + dz), Blocks.STONE.defaultBlockState(), 3);
        player.teleportTo(world, x + .5, 100.01, z + .5, java.util.Set.of(), 0, 0);
        player.hasChangedDimension();
        var belt = new ItemStack(RealmGear.BALLAST_BELT.get());
        player.setItemInHand(InteractionHand.OFF_HAND, belt);
        int startedTicks = player.tickCount;
        var roof = new BlockPos(x, 104, z);
        var surge = new TideState(TidePhase.SURGE, 500, 2000, 0);
        int[] travelSamples = {0};
        boolean[] groundedTravel = {true};
        Consumer<PlayerTickEvent.Pre> wind = event -> {
            if (event.getEntity() == player) {
                BuoyancyController.applyEntityBuoyancy(player, 1);
                RealmGear.tick(player, surge);
            }
        };
        Consumer<PlayerTickEvent.Post> travel = event -> {
            if (event.getEntity() == player) {
                travelSamples[0]++;
                if(travelSamples[0]<=12)System.out.println("V6_BELT_TRAVEL sample="+travelSamples[0]+" tick="+player.tickCount+" pos="+player.position()
                        +" delta="+player.getDeltaMovement()+" gravity="+player.getAttributeValue(Attributes.GRAVITY)+" grounded="+player.onGround()
                        +" roof="+ShelterDetector.isSheltered(world,player)+" fluid="+player.isInFluidType()+" noPhysics="+player.noPhysics
                        +" below="+world.getBlockState(player.blockPosition().below())+" beltDamage="+belt.getDamageValue());
                if (travelSamples[0] > 4) groundedTravel[0] &= player.getY() >= 99.99 && player.getY() <= 100.05
                        && player.getDeltaMovement().y <= 0 && player.onGround();
            }
        };
        NeoForge.EVENT_BUS.addListener(wind);
        NeoForge.EVENT_BUS.addListener(travel);
        // Embedded test connections are not ticked by the server's real network connection list.
        // Drive its native doTick entry once per actual GameTest tick; do not claim a two-client proof.
        for(int step=1;step<=82;step++)h.runAtTickTime(step,player::doTick);
        h.runAtTickTime(30, () -> {
            try {
                System.out.println("V6_BELT_CHECK tickDelta="+(player.tickCount-startedTicks)+" samples="+travelSamples[0]+" allGrounded="+groundedTravel[0]
                        +" pos="+player.position()+" delta="+player.getDeltaMovement()+" grounded="+player.onGround()+" noGravity="+player.isNoGravity()
                        +" flying="+player.getAbilities().flying+" mayfly="+player.getAbilities().mayfly+" beltDamage="+belt.getDamageValue());
                h.assertTrue(player.tickCount - startedTicks >= 20 && travelSamples[0] >= 20 && groundedTravel[0]
                        && Math.abs(player.getY() - 100) < .05 && player.onGround()
                        && !player.getAbilities().flying && !player.getAbilities().mayfly && !player.isNoGravity(),
                        "The real held belt must keep native feet on solid ground at the unchanged peak without flight or frozen gravity");
                h.assertTrue(belt.getDamageValue() == 1, "Actual eligible player ticks must spend one active second, preserving the 240-second resource accounting");
                world.setBlock(roof, Blocks.STONE.defaultBlockState(), 3);
            } catch (RuntimeException | Error failure) {
                NeoForge.EVENT_BUS.unregister(wind);
                NeoForge.EVENT_BUS.unregister(travel);
                TestPlayers.remove(player);
                throw failure;
            }
        });
        h.runAtTickTime(60, () -> {
            try {
                h.assertTrue(player.onGround() && Math.abs(player.getY() - 100) < .05 && belt.getDamageValue() == 1,
                        "A real roof must keep the player grounded while thirty sheltered ticks spend no more ballast");
                world.setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
            } catch (RuntimeException | Error failure) {
                NeoForge.EVENT_BUS.unregister(wind);
                NeoForge.EVENT_BUS.unregister(travel);
                TestPlayers.remove(player);
                throw failure;
            }
        });
        h.runAtTickTime(82, () -> {
            try {
                h.assertTrue(player.onGround() && Math.abs(player.getY() - 100) < .05 && belt.getDamageValue() == 2
                        && belt.getCount() == 1 && !player.getAbilities().flying && !player.getAbilities().mayfly,
                        "Breaking shelter must resume finite active wear without changing native ground contact or flight abilities");
            } finally {
                NeoForge.EVENT_BUS.unregister(wind);
                NeoForge.EVENT_BUS.unregister(travel);
                world.setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
                TestPlayers.remove(player);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeLadderWaterPassengerAndElytraStatesContinueToExcludeTideLift(GameTestHelper h) {
        var ladder = actor(h, 2, 60);
        var water = actor(h, 6, 60);
        var passenger = actor(h, 10, 60);
        var boat = EntityType.BOAT.create(h.getLevel());
        h.assertTrue(boat != null, "Native passenger exclusion requires a real boat");
        var ladderPos = ladder.blockPosition();
        for (int dy = -1; dy <= 2; dy++) {
            h.getLevel().setBlock(ladderPos.offset(0, dy, 1), Blocks.STONE.defaultBlockState(), 3);
            if (dy >= 0) h.getLevel().setBlock(ladderPos.above(dy), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH), 3);
        }
        h.getLevel().setBlock(ladderPos.below(), Blocks.STONE.defaultBlockState(), 3);
        var waterPos = water.blockPosition();
        h.getLevel().setBlock(waterPos.below(2), Blocks.STONE.defaultBlockState(), 3);
        for (int dy = -1; dy <= 4; dy++) h.getLevel().setBlock(waterPos.above(dy), Blocks.WATER.defaultBlockState(), 3);
        var boatPos = passenger.blockPosition();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) h.getLevel().setBlock(boatPos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
        boat.moveTo(boatPos.getX() + .5, 60, boatPos.getZ() + .5, 0, 0);
        h.assertTrue(h.getLevel().addFreshEntity(boat) && passenger.startRiding(boat), "The native actor must actually become a vehicle passenger");
        var glider = new MotionZombie(h.getLevel());
        var glidePos = h.absolutePos(new BlockPos(6, 0, 11));
        for (int dx = -1; dx <= 22; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = 95; y <= 145; y++)
            h.getLevel().setBlock(new BlockPos(glidePos.getX() + dx, y, glidePos.getZ() + dz), Blocks.AIR.defaultBlockState(), 3);
        glider.moveTo(glidePos.getX() + .5, 120, glidePos.getZ() + .5, -90, 0);
        glider.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        glider.setDeltaMovement(.6, -.1, 0);
        glider.startGliding();
        h.assertTrue(h.getLevel().addFreshEntity(glider), "Elytra exclusion requires a registered native gliding living entity");
        double glideStart = glider.getX();
        Consumer<EntityTickEvent.Pre> wind = event -> {
            if (event.getEntity() == ladder || event.getEntity() == water || event.getEntity() == passenger || event.getEntity() == glider)
                BuoyancyController.applyEntityBuoyancy(event.getEntity(), 1);
        };
        NeoForge.EVENT_BUS.addListener(wind);
        h.runAtTickTime(25, () -> {
            try {
                h.assertTrue(ladder.tickCount >= 15 && ladder.onClimbable() && !ladder.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID)
                        && ladder.getY() < 61, "Real ladder contact must retain native ladder motion rather than receive tide lift");
                h.assertTrue(water.tickCount >= 15 && water.isInWater() && !water.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID)
                        && water.getY() < 61, "Actual water ticks must retain the native fluid path without upward atmospheric force");
                h.assertTrue(passenger.tickCount >= 15 && boat.tickCount >= 15 && passenger.isPassenger() && passenger.getVehicle() == boat
                        && !passenger.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID),
                        "The real vehicle relationship must exclude atmospheric lift throughout native passenger ticks");
                h.assertTrue(glider.tickCount >= 15 && glider.isFallFlying() && glider.getX() > glideStart + 2 && glider.getDeltaMovement().y < 0
                        && !glider.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID) && !glider.isNoGravity(),
                        "A real equipped elytra living entity must move under native glide gravity without atmospheric lift or frozen gravity");
            } finally {
                remove(wind, ladder, water, passenger, boat, glider);
            }
            h.succeed();
        });
    }
}
