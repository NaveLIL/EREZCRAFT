package pro.erez.interstice;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.network.chat.Component;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGenerator;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

@Mod(Interstice.ID)
public final class Interstice {
    public static final String ID = "interstice";
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, ID);
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS=DeferredRegister.create(BuiltInRegistries.CHUNK_GENERATOR,ID);
    private static final DeferredHolder<MapCodec<? extends ChunkGenerator>,MapCodec<IslandChunkGenerator>> ISLAND_GENERATOR=GENERATORS.register("coupled_islands",()->IslandChunkGenerator.CODEC);

    public static final DeferredHolder<FluidType, ToxicFluidType> LIGHT_TYPE = TYPES.register("light_toxin", () -> new ToxicFluidType(true));
    public static final DeferredHolder<FluidType, ToxicFluidType> HEAVY_TYPE = TYPES.register("heavy_toxin", () -> new ToxicFluidType(false));
    public static final DeferredHolder<Fluid, FlowingFluid> LIGHT = FLUIDS.register("light_toxin", () -> new LightFluid.Source(lightProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> LIGHT_FLOW = FLUIDS.register("flowing_light_toxin", () -> new LightFluid.Flowing(lightProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> HEAVY = FLUIDS.register("heavy_toxin", () -> new BaseFlowingFluid.Source(heavyProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> HEAVY_FLOW = FLUIDS.register("flowing_heavy_toxin", () -> new BaseFlowingFluid.Flowing(heavyProperties()));
    public static final DeferredHolder<Block, ToxicLiquidBlock> LIGHT_BLOCK = BLOCKS.register("light_toxin", () -> new ToxicLiquidBlock(LIGHT.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).lightLevel(s -> 15)));
    public static final DeferredHolder<Block, OceanLiquidBlock> LIGHT_SEA = BLOCKS.register("light_sea", () -> new OceanLiquidBlock(LIGHT.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).lightLevel(s -> 15)));
    public static final DeferredHolder<Block, ToxicLiquidBlock> HEAVY_BLOCK = BLOCKS.register("heavy_toxin", () -> new ToxicLiquidBlock(HEAVY.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)));
    public static final DeferredHolder<Block, Block> VITRIOLITE = BLOCKS.register("vitriolite", () -> new Block(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK).strength(2.0F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Block, Block> PYROLITH = BLOCKS.register("pyrolith", () -> new Block(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED).strength(4.5F, 1200.0F).sound(net.minecraft.world.level.block.SoundType.DEEPSLATE).requiresCorrectToolForDrops().lightLevel(s -> 3)));
    public static final DeferredHolder<Block, Block> AEROLITE = BLOCKS.register("aerolite", () -> new Block(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_CYAN).strength(1.2F, 3.0F).sound(net.minecraft.world.level.block.SoundType.TUFF).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Block, Block> PHOSPHORITE = BLOCKS.register("phosphorite", () -> new Block(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GREEN).strength(2.5F, 6.0F).sound(net.minecraft.world.level.block.SoundType.GLASS).lightLevel(s -> 7)));
    public static final DeferredHolder<Block, net.minecraft.world.level.block.DropExperienceBlock> RIFTSILVER_ORE = BLOCKS.register("riftsilver_ore",
            () -> new net.minecraft.world.level.block.DropExperienceBlock(net.minecraft.util.valueproviders.UniformInt.of(1, 3),
                    BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE).strength(3.0F, 3.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> VITRIOLITE_ITEM = ITEMS.register("vitriolite", () -> new net.minecraft.world.item.BlockItem(VITRIOLITE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> PYROLITH_ITEM = ITEMS.register("pyrolith", () -> new net.minecraft.world.item.BlockItem(PYROLITH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> AEROLITE_ITEM = ITEMS.register("aerolite", () -> new net.minecraft.world.item.BlockItem(AEROLITE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> PHOSPHORITE_ITEM = ITEMS.register("phosphorite", () -> new net.minecraft.world.item.BlockItem(PHOSPHORITE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> RIFTSILVER_ORE_ITEM = ITEMS.register("riftsilver_ore", () -> new net.minecraft.world.item.BlockItem(RIFTSILVER_ORE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> RAW_RIFTSILVER = ITEMS.register("raw_riftsilver", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> RIFTSILVER_INGOT = ITEMS.register("riftsilver_ingot", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, BucketItem> LIGHT_BUCKET = ITEMS.register("light_toxin_bucket", () -> new LightBucketItem(new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredHolder<Item, BucketItem> HEAVY_BUCKET = ITEMS.register("heavy_toxin_bucket", () -> new pro.erez.interstice.item.HeavyBucketItem(new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredHolder<Item, pro.erez.interstice.item.RiftsilverBucketItem> RIFTSILVER_BUCKET = ITEMS.register("riftsilver_bucket", () -> new pro.erez.interstice.item.RiftsilverBucketItem(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, pro.erez.interstice.item.RiftsilverHeavyBucketItem> RIFTSILVER_HEAVY_BUCKET = ITEMS.register("riftsilver_heavy_bucket", () -> new pro.erez.interstice.item.RiftsilverHeavyBucketItem(new Item.Properties().craftRemainder(RIFTSILVER_BUCKET.get()).stacksTo(1)));
    public static final DeferredHolder<Item, pro.erez.interstice.item.RiftsilverInvertedBucketItem> RIFTSILVER_INVERTED_BUCKET = ITEMS.register("riftsilver_inverted_bucket", () -> new pro.erez.interstice.item.RiftsilverInvertedBucketItem(new Item.Properties().craftRemainder(RIFTSILVER_BUCKET.get()).stacksTo(1)));
    public static final DeferredHolder<Item, pro.erez.interstice.item.TideIndicatorItem> TIDE_INDICATOR = ITEMS.register("tide_indicator", () -> new pro.erez.interstice.item.TideIndicatorItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("fluids", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.interstice"))
            .icon(() -> RIFTSILVER_INVERTED_BUCKET.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(LIGHT_BUCKET.get());
                output.accept(HEAVY_BUCKET.get());
                output.accept(RIFTSILVER_BUCKET.get());
                output.accept(RIFTSILVER_HEAVY_BUCKET.get());
                output.accept(RIFTSILVER_INVERTED_BUCKET.get());
                output.accept(RAW_RIFTSILVER.get());
                output.accept(RIFTSILVER_INGOT.get());
                output.accept(RIFTSILVER_ORE_ITEM.get());
                output.accept(TIDE_INDICATOR.get());
                output.accept(VITRIOLITE_ITEM.get());
                output.accept(PYROLITH_ITEM.get());
                output.accept(AEROLITE_ITEM.get());
                output.accept(PHOSPHORITE_ITEM.get());
            }).build());

    private static BaseFlowingFluid.Properties lightProperties() {
        return new BaseFlowingFluid.Properties(LIGHT_TYPE, LIGHT, LIGHT_FLOW).block(LIGHT_BLOCK).bucket(LIGHT_BUCKET).tickRate(5).levelDecreasePerBlock(1).explosionResistance(100);
    }
    private static BaseFlowingFluid.Properties heavyProperties() {
        return new BaseFlowingFluid.Properties(HEAVY_TYPE, HEAVY, HEAVY_FLOW).block(HEAVY_BLOCK).bucket(HEAVY_BUCKET).tickRate(5).levelDecreasePerBlock(1).explosionResistance(100);
    }
    public Interstice(IEventBus bus) {
        bus.addListener(pro.erez.interstice.geometry.GeometrySync::registerPayloads);
        bus.addListener(pro.erez.interstice.geometry.GeometrySync::registerTask);
        bus.addListener(pro.erez.interstice.tide.TideSync::registerPayloads);
        FluidLightUpgrade.register(bus);
        pro.erez.interstice.sound.ModSounds.register(bus);
        TYPES.register(bus); FLUIDS.register(bus); BLOCKS.register(bus); ITEMS.register(bus); TABS.register(bus);
        GENERATORS.register(bus);
        NeoForge.EVENT_BUS.addListener(FluidLab::registerCommands);
        NeoForge.EVENT_BUS.addListener(ToxicLiquidBlock::onEntityTick);
        NeoForge.EVENT_BUS.addListener(pro.erez.interstice.worldgen.IslandWorld::registerCommands);
        NeoForge.EVENT_BUS.addListener(pro.erez.interstice.tide.TideManager::registerCommands);
    }
}
