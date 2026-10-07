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
    public static final DeferredHolder<Item, BucketItem> LIGHT_BUCKET = ITEMS.register("light_toxin_bucket", () -> new LightBucketItem(new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredHolder<Item, BucketItem> HEAVY_BUCKET = ITEMS.register("heavy_toxin_bucket", () -> new BucketItem(HEAVY.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredHolder<Item, pro.erez.interstice.item.TideIndicatorItem> TIDE_INDICATOR = ITEMS.register("tide_indicator", () -> new pro.erez.interstice.item.TideIndicatorItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("fluids", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.interstice"))
            .icon(() -> LIGHT_BUCKET.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(LIGHT_BUCKET.get());
                output.accept(HEAVY_BUCKET.get());
                output.accept(TIDE_INDICATOR.get());
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
