package pro.erez.interstice.equipment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.attachment.AttachmentType;
import pro.erez.interstice.Interstice;

public final class ExpeditionEquipment {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Interstice.ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, Interstice.ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, Interstice.ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, Interstice.ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Interstice.ID);

    public static final DeferredHolder<Item, BackpackItem> FIELD_BACKPACK = ITEMS.register("field_backpack", () -> new BackpackItem(54));
    public static final DeferredHolder<Item, BackpackItem> EXPEDITION_BACKPACK = ITEMS.register("expedition_backpack", () -> new BackpackItem(72));
    public static final DeferredHolder<Item, BackpackItem> RIFT_BACKPACK = ITEMS.register("rift_backpack", () -> new BackpackItem(84));
    public static final DeferredHolder<Item, Item> CHEMOTROPHIC_FABRIC = ITEMS.register("chemotrophic_fabric", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> BLANK_MODULE = ITEMS.register("blank_module", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, BackpackModuleItem> MAGNET_MODULE_TIER1 = ITEMS.register("magnet_module_tier1", () -> new BackpackModuleItem(ModuleType.MAGNET, 1, new Item.Properties()));
    public static final DeferredHolder<Item, BackpackModuleItem> FEEDER_MODULE_TIER1 = ITEMS.register("feeder_module_tier1", () -> new BackpackModuleItem(ModuleType.FEEDER, 1, new Item.Properties()));
    public static final DeferredHolder<Item, BackpackModuleItem> COMPRESSION_MODULE_TIER1 = ITEMS.register("compression_module_tier1", () -> new BackpackModuleItem(ModuleType.COMPRESSION, 1, new Item.Properties()));

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemContainerContents>> BACKPACK_MODULES =
            COMPONENTS.registerComponentType("backpack_modules", b -> b.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC).networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC).cacheEncoding());

    public static final DeferredHolder<MenuType<?>, MenuType<BackpackMenu>> PACK_MENU = MENUS.register("backpack", () -> IMenuTypeExtension.create(BackpackMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<HarnessMenu>> HARNESS_MENU = MENUS.register("backpack_harness", () -> new MenuType<>(HarnessMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemStack>> WORN_BACKPACK = ATTACHMENTS.register("worn_backpack", () -> AttachmentType.builder(() -> ItemStack.EMPTY).serialize(BackpackHarness.WORN_CODEC).copyOnDeath().build());
    public static final DeferredHolder<RecipeSerializer<?>, BackpackUpgradeRecipe.Serializer> UPGRADE_SERIALIZER = RECIPES.register("backpack_upgrade", BackpackUpgradeRecipe.Serializer::new);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        COMPONENTS.register(bus);
        MENUS.register(bus);
        RECIPES.register(bus);
        ATTACHMENTS.register(bus);
        bus.addListener(BackpackNetworking::register);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BackpackPickup::collect);
        NeoForge.EVENT_BUS.addListener(BackpackModules::onPlayerTick);
    }

    public static void displayItems(CreativeModeTab.Output output) {
        output.accept(CHEMOTROPHIC_FABRIC.get());
        output.accept(FIELD_BACKPACK.get());
        output.accept(EXPEDITION_BACKPACK.get());
        output.accept(RIFT_BACKPACK.get());
        output.accept(BLANK_MODULE.get());
        output.accept(MAGNET_MODULE_TIER1.get());
        output.accept(FEEDER_MODULE_TIER1.get());
        output.accept(COMPRESSION_MODULE_TIER1.get());
    }

    private ExpeditionEquipment() {}
}
