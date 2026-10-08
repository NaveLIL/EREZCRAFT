package pro.erez.interstice.agriculture;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

public final class RealmAgriculture {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,Interstice.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE,Interstice.ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(BuiltInRegistries.MENU,Interstice.ID);
    private static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE,Interstice.ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER,Interstice.ID);
    private static final List<DeferredHolder<Item,? extends Item>> DISPLAY=new ArrayList<>();
    public static final DeferredHolder<Block,RetortBlock> RETORT=BLOCKS.register("reaction_retort",RetortBlock::new);
    public static final DeferredHolder<Block,ToxicFarmlandBlock> FARMLAND=BLOCKS.register("toxic_farmland",ToxicFarmlandBlock::new);
    public static final DeferredHolder<Block,NativeCropBlock> GRAIN_CROP=BLOCKS.register("ash_grain_crop",()->new NativeCropBlock(true));
    public static final DeferredHolder<Block,NativeCropBlock> ROOT_CROP=BLOCKS.register("crimson_root_crop",()->new NativeCropBlock(false));
    public static final DeferredHolder<Block,NutrientReservoirBlock> RESERVOIR=BLOCKS.register("nutrient_reservoir",NutrientReservoirBlock::new);
    public static final DeferredHolder<Block,FenceBlock> FENCE=BLOCKS.register("reinforced_fence",()->new FenceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).strength(4,12).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Block,FenceGateBlock> GATE=BLOCKS.register("reinforced_fence_gate",()->new FenceGateBlock(net.minecraft.world.level.block.state.properties.WoodType.OAK,BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE_GATE).strength(4,12).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Block,NativeLanternBlock> LANTERN=BLOCKS.register("bioluminescent_lantern",NativeLanternBlock::new);
    public static final DeferredHolder<Item,Item> WIRE=component("riftsilver_wire"),MESH=component("riftsilver_mesh"),SORBENT=component("umbral_sorbent"),PASTE=component("phosphorite_paste"),LINING=component("vitriolite_lining"),PURE_LINING=component("pure_vitriolite_lining"),FLOUR=component("ash_flour"),FIBER=component("plant_fiber");
    public static final DeferredHolder<Item,ItemNameBlockItem> GRAIN=item("ash_grain",()->new ItemNameBlockItem(GRAIN_CROP.get(),new Item.Properties()){
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<net.minecraft.network.chat.Component> text,TooltipFlag flags){text.add(net.minecraft.network.chat.Component.translatable("tooltip.interstice.native_crop"));}
    });
    public static final DeferredHolder<Item,AgricultureItems.ToxicRoot> ROOT=item("crimson_root",AgricultureItems.ToxicRoot::new);
    public static final DeferredHolder<Item,Item> BREAD=item("purified_bread",()->new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(.6F).build())));
    public static final DeferredHolder<Item,Item> CLEAN_ROOT=item("purified_root",()->new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(.6F).build())));
    public static final DeferredHolder<Item,AgricultureItems.Fertilizer> FERTILIZER=item("mineral_fertilizer",AgricultureItems.Fertilizer::new);
    public static final DeferredHolder<Item,AgricultureItems.Cultivator> CULTIVATOR=item("slicing_cultivator",AgricultureItems.Cultivator::new);
    public static final DeferredHolder<Item,AgricultureItems.ReservoirItem> RESERVOIR_ITEM=item("nutrient_reservoir",AgricultureItems.ReservoirItem::new);
    static {for(var entry:Map.of("reaction_retort",RETORT,"toxic_farmland",FARMLAND,"reinforced_fence",FENCE,"reinforced_fence_gate",GATE,"bioluminescent_lantern",LANTERN).entrySet())item(entry.getKey(),()->new BlockItem(entry.getValue().get(),new Item.Properties()));}
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<RetortBlockEntity>> RETORT_ENTITY=ENTITIES.register("reaction_retort",()->BlockEntityType.Builder.of(RetortBlockEntity::new,RETORT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NutrientReservoirEntity>> RESERVOIR_ENTITY=ENTITIES.register("nutrient_reservoir",()->BlockEntityType.Builder.of(NutrientReservoirEntity::new,RESERVOIR.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<RetortMenu>> RETORT_MENU=MENUS.register("reaction_retort",()->IMenuTypeExtension.create(RetortMenu::new));
    public static final DeferredHolder<RecipeType<?>,RecipeType<RetortRecipe>> RETORT_RECIPE_TYPE=TYPES.register("retort",()->new RecipeType<>(){public String toString(){return "interstice:retort";}});
    public static final DeferredHolder<RecipeSerializer<?>,RetortRecipe.Serializer> RETORT_SERIALIZER=SERIALIZERS.register("retort",RetortRecipe.Serializer::new);
    private static DeferredHolder<Item,Item> component(String name){return item(name,()->new Item(new Item.Properties()));}
    private static <T extends Item> DeferredHolder<Item,T> item(String name,java.util.function.Supplier<T> factory){var holder=ITEMS.register(name,factory);DISPLAY.add(holder);return holder;}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);TYPES.register(bus);SERIALIZERS.register(bus);}
    public static void displayItems(CreativeModeTab.Output output){DISPLAY.forEach(i->output.accept(i.get()));}
    private RealmAgriculture(){}
}
