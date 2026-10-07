package pro.erez.interstice.sound;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

/**
 * Sound events for the atmospheric tide cycle in Interstice.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Interstice.ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_WARNING = SOUNDS.register("tide.warning",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Interstice.ID, "tide.warning")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_SURGE = SOUNDS.register("tide.surge",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Interstice.ID, "tide.surge")));

    private ModSounds() {}

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
