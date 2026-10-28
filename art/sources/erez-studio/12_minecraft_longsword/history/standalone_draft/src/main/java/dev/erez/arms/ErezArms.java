package dev.erez.arms;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(ErezArms.MOD_ID)
public final class ErezArms {
    public static final String MOD_ID = "erez_arms";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<SwordItem> IRON_LONGSWORD = ITEMS.register("iron_longsword",
        () -> new SwordItem(Tiers.IRON, new Item.Properties()
            .attributes(SwordItem.createAttributes(Tiers.IRON, 3, -2.4F))));

    public ErezArms(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ErezArms::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(IRON_LONGSWORD.get());
        }
    }
}
