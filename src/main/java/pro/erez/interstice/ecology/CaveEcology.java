package pro.erez.interstice.ecology;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

/** Cave ecology registrations; no global entity hooks or changes to ordinary dimensions. */
public final class CaveEcology {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, Interstice.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Interstice.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Interstice.ID);
    private static final List<DeferredHolder<Item, ? extends BlockItem>> DISPLAY = new ArrayList<>();
    public static final DeferredHolder<Block, CavePlantBlock> GLOW_BLOOM = register("glow_bloom", () -> new CavePlantBlock(false, false));
    public static final DeferredHolder<Block, CavePlantBlock> HANGING_GLOW_BLOOM = register("hanging_glow_bloom", () -> new CavePlantBlock(true, false));
    public static final DeferredHolder<Block, CavePlantBlock> STING_FROND = register("sting_frond", () -> new CavePlantBlock(false, true));
    public static final DeferredHolder<Block, ClingweedBlock> CLINGWEED = BLOCKS.register("clingweed", ClingweedBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClingweedBlockEntity>> CLINGWEED_ENTITY = ENTITIES.register("clingweed", () -> BlockEntityType.Builder.of(ClingweedBlockEntity::new, CLINGWEED.get()).build(null));
    static { DISPLAY.add(ITEMS.register("clingweed", () -> new ClingweedItem(CLINGWEED.get()))); }
    private CaveEcology() {}
    private static <T extends Block> DeferredHolder<Block, T> register(String name, Supplier<T> factory) {
        var block = BLOCKS.register(name, factory);
        DISPLAY.add(ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
        return block;
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); }
    public static void displayItems(CreativeModeTab.Output output) { DISPLAY.forEach(item -> output.accept(item.get())); }
}
