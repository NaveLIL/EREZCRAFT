package pro.erez.interstice.tide;

import java.util.Locale;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/**
 * The four cyclical phases of the Interstice atmospheric and gravitational tide.
 */
public enum TidePhase implements StringRepresentable {
    CALM(0, "calm", 12000, 26400),    // 10..22 minutes of tranquility
    WARNING(1, "warning", 900, 1600),  // 45..80 seconds of harbinger rumble and gathering storm
    SURGE(2, "surge", 1200, 2600),     // 60..130 seconds of violent storm, upward lift, and intense lightning
    EBB(3, "ebb", 400, 800);           // 20..40 seconds of decaying winds and settling air

    private final int id;
    private final String name;
    private final int minDurationTicks;
    private final int maxDurationTicks;

    TidePhase(int id, String name, int minDurationTicks, int maxDurationTicks) {
        this.id = id;
        this.name = name;
        this.minDurationTicks = minDurationTicks;
        this.maxDurationTicks = maxDurationTicks;
    }

    public int id() {
        return id;
    }

    public int minDurationTicks() {
        return minDurationTicks;
    }

    public int maxDurationTicks() {
        return maxDurationTicks;
    }

    public TidePhase next() {
        return switch (this) {
            case CALM -> WARNING;
            case WARNING -> SURGE;
            case SURGE -> EBB;
            case EBB -> CALM;
        };
    }

    public long pickDuration(RandomSource random) {
        if (minDurationTicks == maxDurationTicks) {
            return minDurationTicks;
        }
        return minDurationTicks + random.nextInt(maxDurationTicks - minDurationTicks + 1);
    }

    public static TidePhase byId(int id) {
        return switch (id) {
            case 1 -> WARNING;
            case 2 -> SURGE;
            case 3 -> EBB;
            default -> CALM;
        };
    }

    public static TidePhase byName(String name) {
        if (name == null) return CALM;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "warning" -> WARNING;
            case "surge" -> SURGE;
            case "ebb" -> EBB;
            default -> CALM;
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
