package pro.erez.interstice.rift;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

/** One finite outside-world discovery aid; permanent portal materials remain native to the realm. */
public final class RiftInitiation {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Interstice.ID);
    public static final DeferredHolder<Item, RiftInitiatorItem> UNSTABLE_INITIATOR =
            ITEMS.register("unstable_rift_initiator", RiftInitiatorItem::new);

    private RiftInitiation() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    public static void displayItems(CreativeModeTab.Output output) {
        output.accept(UNSTABLE_INITIATOR.get());
    }
}
