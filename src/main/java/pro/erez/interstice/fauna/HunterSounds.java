package pro.erez.interstice.fauna;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

public final class HunterSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,Interstice.ID);
    public static final DeferredHolder<SoundEvent,SoundEvent> AMBIENT=add("ambient"), ALERT=add("alert"),
            WINDUP=add("windup"), ATTACK=add("attack"), HURT=add("hurt"), DEATH=add("death");
    private static DeferredHolder<SoundEvent,SoundEvent> add(String name) {
        return SOUNDS.register("vault_hunter."+name,()->SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Interstice.ID,"vault_hunter."+name)));
    }
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
    private HunterSounds() {}
}
