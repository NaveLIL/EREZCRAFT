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
import pro.erez.interstice.worldgen.VaultMaterials;

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
    public static final DeferredHolder<Block, RiftstoneBlock> RIFTSTONE = BLOCKS.register("riftstone", RiftstoneBlock::new);
    public static final DeferredHolder<Block, AbyssalTurfBlock> ABYSSAL_TURF = BLOCKS.register("abyssal_turf", AbyssalTurfBlock::new);
    public static final DeferredHolder<Block, TideSproutBlock> TIDE_SPROUT = BLOCKS.register("tide_sprout", TideSproutBlock::new);
    public static final DeferredHolder<Block, net.minecraft.world.level.block.RotatedPillarBlock> GLOOMCROWN_LOG = BLOCKS.register("gloomcrown_log", () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_LOG).mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)));
    public static final DeferredHolder<Block, net.minecraft.world.level.block.RotatedPillarBlock> STRIPPED_GLOOMCROWN_LOG = BLOCKS.register("stripped_gloomcrown_log", () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_DARK_OAK_LOG).mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)));
    public static final DeferredHolder<Block, Block> GLOOMCROWN_PLANKS = BLOCKS.register("gloomcrown_planks", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_PLANKS).mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)));
    public static final DeferredHolder<Block, net.minecraft.world.level.block.LeavesBlock> GLOOMCROWN_LEAVES = BLOCKS.register("gloomcrown_leaves", () -> new net.minecraft.world.level.block.LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_LEAVES).mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE).lightLevel(s -> 3)));
    public static final DeferredHolder<Block, GloomcrownSaplingBlock> GLOOMCROWN_SAPLING = BLOCKS.register("gloomcrown_sapling", () -> new GloomcrownSaplingBlock());
    public static final DeferredHolder<Block, Block> RIFT_FRAME = BLOCKS.register("rift_frame", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_BRICKS).mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)));
    public static final DeferredHolder<Block, pro.erez.interstice.rift.RiftPortalBlock> RIFT_PORTAL = BLOCKS.register("rift_portal", pro.erez.interstice.rift.RiftPortalBlock::new);
    public static final DeferredHolder<Block, pro.erez.interstice.rift.RiftEchoBlock> RIFT_ECHO = BLOCKS.register("rift_echo", pro.erez.interstice.rift.RiftEchoBlock::new);
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> RIFT_FRAME_ITEM = ITEMS.register("rift_frame", () -> new net.minecraft.world.item.BlockItem(RIFT_FRAME.get(), new Item.Properties()));
    public static final DeferredHolder<Item, pro.erez.interstice.rift.RiftLensItem> RIFT_LENS = ITEMS.register("rift_lens", pro.erez.interstice.rift.RiftLensItem::new);
    public static final DeferredHolder<Item, pro.erez.interstice.expedition.WayfarerKeyItem> WAYFARER_KEY = ITEMS.register("wayfarer_key", pro.erez.interstice.expedition.WayfarerKeyItem::new);
    public static final DeferredHolder<Item, Item> PRESSURE_COUPLER = ITEMS.register("pressure_coupler", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> GLOOMCROWN_LOG_ITEM = ITEMS.register("gloomcrown_log", () -> new net.minecraft.world.item.BlockItem(GLOOMCROWN_LOG.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> STRIPPED_GLOOMCROWN_LOG_ITEM = ITEMS.register("stripped_gloomcrown_log", () -> new net.minecraft.world.item.BlockItem(STRIPPED_GLOOMCROWN_LOG.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> GLOOMCROWN_PLANKS_ITEM = ITEMS.register("gloomcrown_planks", () -> new net.minecraft.world.item.BlockItem(GLOOMCROWN_PLANKS.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> GLOOMCROWN_LEAVES_ITEM = ITEMS.register("gloomcrown_leaves", () -> new net.minecraft.world.item.BlockItem(GLOOMCROWN_LEAVES.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> GLOOMCROWN_SAPLING_ITEM = ITEMS.register("gloomcrown_sapling", () -> new net.minecraft.world.item.BlockItem(GLOOMCROWN_SAPLING.get(), new Item.Properties()));

    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> VITRIOLITE_ITEM = ITEMS.register("vitriolite", () -> new net.minecraft.world.item.BlockItem(VITRIOLITE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> PYROLITH_ITEM = ITEMS.register("pyrolith", () -> new net.minecraft.world.item.BlockItem(PYROLITH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> AEROLITE_ITEM = ITEMS.register("aerolite", () -> new net.minecraft.world.item.BlockItem(AEROLITE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> PHOSPHORITE_ITEM = ITEMS.register("phosphorite", () -> new net.minecraft.world.item.BlockItem(PHOSPHORITE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> RIFTSILVER_ORE_ITEM = ITEMS.register("riftsilver_ore", () -> new net.minecraft.world.item.BlockItem(RIFTSILVER_ORE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> RIFTSTONE_ITEM = ITEMS.register("riftstone", () -> new net.minecraft.world.item.BlockItem(RIFTSTONE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> ABYSSAL_TURF_ITEM = ITEMS.register("abyssal_turf", () -> new net.minecraft.world.item.BlockItem(ABYSSAL_TURF.get(), new Item.Properties()));
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> TIDE_SPROUT_ITEM = ITEMS.register("tide_sprout", () -> new net.minecraft.world.item.BlockItem(TIDE_SPROUT.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> RAW_RIFTSILVER = ITEMS.register("raw_riftsilver", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> RIFTSILVER_INGOT = ITEMS.register("riftsilver_ingot", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, BucketItem> LIGHT_BUCKET = ITEMS.register("light_toxin_bucket", () -> new LightBucketItem(new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredHolder<Item, BucketItem> HEAVY_BUCKET = ITEMS.register("heavy_toxin_bucket", () -> new pro.erez.interstice.item.HeavyBucketItem(new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredHolder<Item, pro.erez.interstice.item.RiftsilverBucketItem> RIFTSILVER_BUCKET = ITEMS.register("riftsilver_bucket", () -> new pro.erez.interstice.item.RiftsilverBucketItem(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, pro.erez.interstice.item.RiftsilverFilledBucketItem> RIFTSILVER_WATER_BUCKET = ITEMS.register("riftsilver_water_bucket", () -> new pro.erez.interstice.item.RiftsilverFilledBucketItem(net.minecraft.world.level.material.Fluids.WATER, new Item.Properties().craftRemainder(RIFTSILVER_BUCKET.get()).stacksTo(1)));
    public static final DeferredHolder<Item, pro.erez.interstice.item.RiftsilverFilledBucketItem> RIFTSILVER_LAVA_BUCKET = ITEMS.register("riftsilver_lava_bucket", () -> new pro.erez.interstice.item.RiftsilverFilledBucketItem(net.minecraft.world.level.material.Fluids.LAVA, new Item.Properties().craftRemainder(RIFTSILVER_BUCKET.get()).stacksTo(1)));
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
                output.accept(RIFTSILVER_WATER_BUCKET.get());
                output.accept(RIFTSILVER_LAVA_BUCKET.get());
                output.accept(RIFTSILVER_HEAVY_BUCKET.get());
                output.accept(RIFTSILVER_INVERTED_BUCKET.get());
                output.accept(RAW_RIFTSILVER.get());
                output.accept(RIFTSILVER_INGOT.get());
                output.accept(RIFTSILVER_ORE_ITEM.get());
                output.accept(RIFTSTONE_ITEM.get());
                output.accept(ABYSSAL_TURF_ITEM.get());
                VaultMaterials.displayItems(output);
                pro.erez.interstice.worldgen.GardenMaterials.displayItems(output);
                pro.erez.interstice.worldgen.cave.CaveMaterials.displayItems(output);
                pro.erez.interstice.ecology.CaveEcology.displayItems(output);
                pro.erez.interstice.minerals.MineralEcology.displayItems(output);
                pro.erez.interstice.agriculture.RealmAgriculture.displayItems(output);
                pro.erez.interstice.equipment.ExpeditionEquipment.displayItems(output);
                output.accept(pro.erez.interstice.food.TideHeart.FRUIT.get());
                output.accept(TIDE_SPROUT_ITEM.get());
                output.accept(GLOOMCROWN_LOG_ITEM.get());
                output.accept(STRIPPED_GLOOMCROWN_LOG_ITEM.get());
                output.accept(GLOOMCROWN_PLANKS_ITEM.get());
                output.accept(GLOOMCROWN_LEAVES_ITEM.get());
                output.accept(GLOOMCROWN_SAPLING_ITEM.get());
                output.accept(RIFT_FRAME_ITEM.get());
                output.accept(RIFT_LENS.get());
                output.accept(WAYFARER_KEY.get());
                output.accept(PRESSURE_COUPLER.get());
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
        bus.addListener(pro.erez.interstice.item.RiftsilverBucketInteractions::setup);
        FluidLightUpgrade.register(bus);
        pro.erez.interstice.sound.ModSounds.register(bus);
        VaultMaterials.register(bus);
        pro.erez.interstice.worldgen.GardenMaterials.register(bus);
        pro.erez.interstice.food.TideHeart.register(bus);
        pro.erez.interstice.worldgen.cave.CaveMaterials.register(bus);
        pro.erez.interstice.ecology.CaveEcology.register(bus);
        pro.erez.interstice.minerals.MineralEcology.register(bus);
        pro.erez.interstice.agriculture.RealmAgriculture.register(bus);
        pro.erez.interstice.equipment.ExpeditionEquipment.register(bus);
        NeoForge.EVENT_BUS.addListener(pro.erez.interstice.food.TideHeart::tick);
        NeoForge.EVENT_BUS.addListener(pro.erez.interstice.food.TideHeart::playerTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddReloadListenerEvent event) -> event.addListener(new pro.erez.interstice.worldgen.GardenTreeDefinitions()));
        TYPES.register(bus); FLUIDS.register(bus); BLOCKS.register(bus); ITEMS.register(bus); TABS.register(bus);
        GENERATORS.register(bus);
        NeoForge.EVENT_BUS.addListener(FluidLab::registerCommands);
        NeoForge.EVENT_BUS.addListener(ToxicLiquidBlock::onEntityTick);
        NeoForge.EVENT_BUS.addListener(pro.erez.interstice.worldgen.IslandWorld::registerCommands);
        NeoForge.EVENT_BUS.addListener(pro.erez.interstice.tide.TideManager::registerCommands);
    }
}
