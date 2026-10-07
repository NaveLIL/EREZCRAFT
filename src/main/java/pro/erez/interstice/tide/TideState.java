package pro.erez.interstice.tide;

/**
 * Immutable snapshot of the dimension's tide state at a given tick.
 */
public record TideState(
        TidePhase phase,
        long phaseTicksElapsed,
        long phaseDurationTicks,
        long totalCycles
) {
    public TideState {
        if (phase == null) phase = TidePhase.CALM;
        if (phaseTicksElapsed < 0) phaseTicksElapsed = 0;
        if (phaseDurationTicks <= 0) phaseDurationTicks = Math.max(1, phase.minDurationTicks());
        if (totalCycles < 0) totalCycles = 0;
    }

    public float progress() {
        if (phaseDurationTicks <= 0) return 0.0F;
        float p = (float) phaseTicksElapsed / (float) phaseDurationTicks;
        return Math.clamp(p, 0.0F, 1.0F);
    }

    public long remainingTicks() {
        return Math.max(0, phaseDurationTicks - phaseTicksElapsed);
    }

    /**
     * Normalized 0.0..1.0 intensity of the atmospheric disturbance.
     */
    public float intensity() {
        float p = progress();
        return switch (phase) {
            case CALM -> 0.0F;
            case WARNING -> p * 0.5F;
            case SURGE -> {
                if (p < 0.2F) {
                    yield 0.5F + (p / 0.2F) * 0.5F;
                } else if (p <= 0.8F) {
                    yield 1.0F; // Full peak intensity
                } else {
                    yield 1.0F - ((p - 0.8F) / 0.2F) * 0.5F;
                }
            }
            case EBB -> (1.0F - p) * 0.5F;
        };
    }

    /**
     * Peak phase of the surge: maximal buoyant forces and tripled lightning strikes.
     */
    public boolean isPeakSurge() {
        if (phase != TidePhase.SURGE) return false;
        float p = progress();
        return p >= 0.2F && p <= 0.8F;
    }

    /**
     * Target tick interval between lightning strikes during storms.
     * Peak surge triples lightning frequency compared to ordinary surge.
     */
    public int lightningIntervalTicks() {
        if (phase != TidePhase.SURGE) return Integer.MAX_VALUE;
        return isPeakSurge() ? 12 : 36;
    }

    /**
     * Normalized 0.0..1.0 intensity of the upward buoyant lifting force.
     * Ramps in SURGE, stays full, and smoothly decays to 0.0 during EBB.
     */
    public float buoyancyIntensity() {
        if (phase == TidePhase.SURGE) {
            float p = progress();
            return p < 0.05F ? p / 0.05F : 1.0F;
        } else if (phase == TidePhase.EBB) {
            return 1.0F - progress();
        }
        return 0.0F;
    }
}
