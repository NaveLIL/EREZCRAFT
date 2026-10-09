package pro.erez.interstice.navigation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

/** One early local-material navigation tool; its recipe does not require a pressure coupler. */
public final class RealmNavigation {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    public static final DeferredHolder<Item,StructureSurveyorItem> SURVEYOR=ITEMS.register("structure_surveyor",StructureSurveyorItem::new);
    private RealmNavigation() {}
    public static void register(IEventBus bus){ITEMS.register(bus);}
    public static void displayItems(CreativeModeTab.Output output){output.accept(SURVEYOR.get());}
}
