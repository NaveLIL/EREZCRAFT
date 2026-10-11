package pro.erez.interstice;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client atmosphere options; no world data or server lighting is changed. */
public final class IntersticeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue V6_DARKNESS;

    static {
        var builder = new ModConfigSpec.Builder();
        V6_DARKNESS = builder.comment("Remove ambient light only in interstice:islands_v6. Night vision and conduit vision retain vanilla lighting.",
                "Shader packs may add their own ambient light; their visual compatibility is still under review.")
                .define("completeDarknessV6", true);
        SPEC = builder.build();
    }

    private IntersticeClientConfig() {}
}
