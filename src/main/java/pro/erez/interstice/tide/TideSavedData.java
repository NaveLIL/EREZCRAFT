package pro.erez.interstice.tide;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-side persistent storage of the Interstice tide calendar.
 * Saves phase, elapsed ticks, chosen phase duration, and total cycle counter.
 */
public final class TideSavedData extends SavedData {
    public static final String FILE_ID = "interstice_tide";
    public static final int CURRENT_VERSION = 1;

    private TidePhase phase = TidePhase.CALM;
    private long phaseTicksElapsed = 0;
    private long phaseDurationTicks = TidePhase.CALM.minDurationTicks();
    private long totalCycles = 0;

    public TideSavedData() {}

    public TideSavedData(RandomSource random) {
        this.phase = TidePhase.CALM;
        this.phaseDurationTicks = TidePhase.CALM.pickDuration(random);
        this.phaseTicksElapsed = 0;
        this.totalCycles = 0;
        setDirty();
    }

    public synchronized TideState snapshot() {
        return new TideState(phase, phaseTicksElapsed, phaseDurationTicks, totalCycles);
    }

    public synchronized void setPhase(TidePhase newPhase, long durationTicks) {
        TidePhase previous = this.phase;
        this.phase = newPhase != null ? newPhase : TidePhase.CALM;
        this.phaseDurationTicks = Math.max(1, durationTicks);
        this.phaseTicksElapsed = 0;
        if (previous == TidePhase.EBB && this.phase == TidePhase.CALM) {
            this.totalCycles++;
        }
        setDirty();
    }

    /**
     * Advances calendar by one server tick.
     * @return true if the phase has expired and a transition is required.
     */
    public synchronized boolean tick() {
        phaseTicksElapsed++;
        setDirty();
        return phaseTicksElapsed >= phaseDurationTicks;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", CURRENT_VERSION);
        tag.putByte("Phase", (byte) phase.id());
        tag.putLong("TicksElapsed", phaseTicksElapsed);
        tag.putLong("DurationTicks", phaseDurationTicks);
        tag.putLong("TotalCycles", totalCycles);
        return tag;
    }

    public static TideSavedData load(CompoundTag tag, HolderLookup.Provider registries, RandomSource fallbackRandom) {
        TideSavedData data = new TideSavedData();
        if (tag.contains("Phase")) {
            data.phase = TidePhase.byId(tag.getByte("Phase"));
        } else {
            data.phase = TidePhase.CALM;
        }
        data.phaseTicksElapsed = Math.max(0, tag.getLong("TicksElapsed"));
        long duration = tag.getLong("DurationTicks");
        if (duration <= 0) {
            duration = data.phase.pickDuration(fallbackRandom);
        }
        data.phaseDurationTicks = duration;
        data.totalCycles = Math.max(0, tag.getLong("TotalCycles"));
        return data;
    }

    public static SavedData.Factory<TideSavedData> factory(RandomSource random) {
        return new SavedData.Factory<>(
                () -> new TideSavedData(random),
                (tag, provider) -> TideSavedData.load(tag, provider, random),
                null
        );
    }

    public static TideSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(level.random), FILE_ID);
    }
}
