package pro.erez.interstice.probe;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Loaded exclusively by the explicitly selected infection profiles. */
@Mod(InfectionProbe.ID)
public final class InfectionProbe {
    public static final String ID = "interstice_probe";

    public InfectionProbe(IEventBus bus) {
        bus.addListener((RegisterGameTestsEvent event) -> event.register(InfectionProbeTests.class));
    }
}
