package pro.erez.interstice.tide;

/**
 * Client-side cached state of the tide, updated via network packets and interpolated per client tick.
 */
public final class ClientTideState {
    private static volatile TideState current = new TideState(TidePhase.CALM, 0, TidePhase.CALM.minDurationTicks(), 0);

    private ClientTideState() {}

    public static TideState get() {
        return current;
    }

    public static void update(TideState newState) {
        if (newState != null) {
            current = newState;
        }
    }

    /**
     * Client-side predictive tick advance between network updates.
     */
    public static void clientTick() {
        var s = current;
        if (s.phaseTicksElapsed() < s.phaseDurationTicks()) {
            current = new TideState(s.phase(), s.phaseTicksElapsed() + 1, s.phaseDurationTicks(), s.totalCycles());
        }
    }
}
