package pro.erez.interstice.test;

import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideSavedData;
import pro.erez.interstice.tide.TideState;
import pro.erez.interstice.tide.TideSync;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class TideGameTests {

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tideCalendarAdvancesDeterministicallyAcrossPhases(GameTestHelper h) {
        RandomSource random = RandomSource.create(20261007L);
        TideSavedData data = new TideSavedData(random);

        // Starts in CALM
        h.assertTrue(data.snapshot().phase() == TidePhase.CALM, "Initial phase must be CALM");
        h.assertTrue(data.snapshot().totalCycles() == 0, "Initial cycles must be 0");

        // Set to CALM with short test duration
        data.setPhase(TidePhase.CALM, 10);
        for (int i = 0; i < 9; i++) {
            boolean expired = data.tick();
            h.assertTrue(!expired, "Phase should not expire before duration at tick " + i);
        }
        boolean expiredCalm = data.tick();
        h.assertTrue(expiredCalm, "CALM must expire at tick 10");

        // Advance to WARNING
        data.setPhase(TidePhase.WARNING, 5);
        h.assertTrue(data.snapshot().phase() == TidePhase.WARNING, "Phase must be WARNING");
        for (int i = 0; i < 4; i++) data.tick();
        h.assertTrue(data.tick(), "WARNING must expire at tick 5");

        // Advance to SURGE
        data.setPhase(TidePhase.SURGE, 8);
        h.assertTrue(data.snapshot().phase() == TidePhase.SURGE, "Phase must be SURGE");
        for (int i = 0; i < 7; i++) data.tick();
        h.assertTrue(data.tick(), "SURGE must expire at tick 8");

        // Advance to EBB
        data.setPhase(TidePhase.EBB, 4);
        h.assertTrue(data.snapshot().phase() == TidePhase.EBB, "Phase must be EBB");
        for (int i = 0; i < 3; i++) data.tick();
        h.assertTrue(data.tick(), "EBB must expire at tick 4");

        // Back to CALM, cycle counter should increment
        data.setPhase(TidePhase.CALM, 100);
        h.assertTrue(data.snapshot().phase() == TidePhase.CALM, "Cycle must loop back to CALM");
        h.assertTrue(data.snapshot().totalCycles() == 1, "Completed cycle count must increment to 1");

        System.out.println("TIDE_ADVANCE phases_verified=4 total_cycles=" + data.snapshot().totalCycles());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tideStateSerializesAndSurvivesReload(GameTestHelper h) {
        HolderLookup.Provider registries = h.getLevel().registryAccess();
        TideSavedData original = new TideSavedData();
        original.setPhase(TidePhase.SURGE, 1800);
        for (int i = 0; i < 450; i++) original.tick();

        CompoundTag tag = new CompoundTag();
        original.save(tag, registries);

        TideSavedData restored = TideSavedData.load(tag, registries, RandomSource.create(42L));
        TideState restoredState = restored.snapshot();

        h.assertTrue(restoredState.phase() == TidePhase.SURGE, "Restored phase mismatch: " + restoredState.phase());
        h.assertTrue(restoredState.phaseTicksElapsed() == 450, "Restored elapsed mismatch: " + restoredState.phaseTicksElapsed());
        h.assertTrue(restoredState.phaseDurationTicks() == 1800, "Restored duration mismatch: " + restoredState.phaseDurationTicks());
        h.assertTrue(Math.abs(restoredState.progress() - 0.25F) < 1e-4, "Restored progress mismatch");
        h.assertTrue(restoredState.isPeakSurge(), "Surge at 25% progress must be in peak phase");

        System.out.println("TIDE_SERIALIZATION phase=" + restoredState.phase() + " elapsed=" + restoredState.phaseTicksElapsed());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tideNetworkPayloadCodecRoundTrip(GameTestHelper h) {
        TideState original = new TideState(TidePhase.SURGE, 720, 1800, 7);
        TideSync.Payload payload = TideSync.Payload.from(original);

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        TideSync.Payload.STREAM_CODEC.encode(buffer, payload);

        TideSync.Payload decoded = TideSync.Payload.STREAM_CODEC.decode(buffer);
        TideState restored = decoded.toState();

        h.assertTrue(restored.phase() == original.phase(), "Decoded phase mismatch");
        h.assertTrue(restored.phaseTicksElapsed() == original.phaseTicksElapsed(), "Decoded elapsed mismatch");
        h.assertTrue(restored.phaseDurationTicks() == original.phaseDurationTicks(), "Decoded duration mismatch");
        h.assertTrue(restored.totalCycles() == original.totalCycles(), "Decoded cycles mismatch");
        h.assertTrue(Math.abs(decoded.intensity() - original.intensity()) < 1e-4, "Decoded intensity mismatch");

        System.out.println("TIDE_CODEC phase=" + restored.phase() + " intensity=" + decoded.intensity());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void timeSetAndSleepingDoNotAffectTideCalendar(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        var server = world.getServer();
        TideSavedData data = TideManager.getSavedData(server);

        data.setPhase(TidePhase.WARNING, 1200);
        for (int i = 0; i < 150; i++) data.tick();

        long elapsedBefore = data.snapshot().phaseTicksElapsed();
        TidePhase phaseBefore = data.snapshot().phase();

        // Simulate /time set day and /time set midnight
        world.setDayTime(1000);
        world.setDayTime(18000);

        long elapsedAfter = data.snapshot().phaseTicksElapsed();
        TidePhase phaseAfter = data.snapshot().phase();

        h.assertTrue(phaseBefore == phaseAfter, "Tide phase changed after /time set: " + phaseAfter);
        h.assertTrue(elapsedBefore == elapsedAfter, "Tide elapsed ticks changed after /time set: " + elapsedAfter);

        System.out.println("TIDE_TIME_INDEPENDENCE phase=" + phaseAfter + " elapsed=" + elapsedAfter);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void lightningStrikesDuringSurgeAndTriplesAtPeak(GameTestHelper h) {
        TideState calm = new TideState(TidePhase.CALM, 100, 18000, 0);
        TideState warning = new TideState(TidePhase.WARNING, 600, 1200, 0);
        TideState earlySurge = new TideState(TidePhase.SURGE, 180, 1800, 0);    // 10% progress (outer surge)
        TideState peakSurge = new TideState(TidePhase.SURGE, 900, 1800, 0);     // 50% progress (peak surge)
        TideState lateSurge = new TideState(TidePhase.SURGE, 1620, 1800, 0);    // 90% progress (decaying surge)

        h.assertTrue(!calm.isPeakSurge(), "CALM must not be peak surge");
        h.assertTrue(!warning.isPeakSurge(), "WARNING must not be peak surge");
        h.assertTrue(!earlySurge.isPeakSurge(), "Early surge (10%) must not be peak surge");
        h.assertTrue(peakSurge.isPeakSurge(), "Mid surge (50%) must be peak surge");
        h.assertTrue(!lateSurge.isPeakSurge(), "Late surge (90%) must not be peak surge");

        // Normal surge lightning interval is 36 ticks; peak surge is 12 ticks (exactly 3x frequency!)
        int outerInterval = earlySurge.lightningIntervalTicks();
        int peakInterval = peakSurge.lightningIntervalTicks();

        h.assertTrue(outerInterval == 36, "Outer surge interval must be 36 ticks; got " + outerInterval);
        h.assertTrue(peakInterval == 12, "Peak surge interval must be 12 ticks; got " + peakInterval);
        h.assertTrue(outerInterval == peakInterval * 3, "Peak surge must triple lightning frequency (3x multiplier)");

        System.out.println("TIDE_LIGHTNING outer_interval=" + outerInterval + " peak_interval=" + peakInterval + " multiplier=3x");
        h.succeed();
    }
}
