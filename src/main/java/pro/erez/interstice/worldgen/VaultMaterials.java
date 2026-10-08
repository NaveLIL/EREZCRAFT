package pro.erez.interstice.worldgen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

import java.util.ArrayList;
import java.util.List;

/** The natural rock and usable building family of the Stone Vaults. */
public final class VaultMaterials {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, Interstice.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Interstice.ID);
    private static final List<DeferredHolder<Item, BlockItem>> BUILDING_ITEMS = new ArrayList<>();

    public static final DeferredHolder<Block, Block> VAULTSTONE = block("vaultstone", 2.5F);
    public static final DeferredHolder<Block, Block> WEATHERED_VAULTSTONE = block("weathered_vaultstone", 1.8F);
    public static final DeferredHolder<Block, Block> POLISHED_VAULTSTONE = block("polished_vaultstone", 2.5F);
    public static final DeferredHolder<Block, Block> VAULTSTONE_BRICKS = block("vaultstone_bricks", 2.5F);

    public static final DeferredHolder<Block, SlabBlock> VAULTSTONE_SLAB = slab("vaultstone_slab", VAULTSTONE);
    public static final DeferredHolder<Block, StairBlock> VAULTSTONE_STAIRS = stairs("vaultstone_stairs", VAULTSTONE);
    public static final DeferredHolder<Block, WallBlock> VAULTSTONE_WALL = wall("vaultstone_wall", VAULTSTONE);
    public static final DeferredHolder<Block, SlabBlock> POLISHED_VAULTSTONE_SLAB = slab("polished_vaultstone_slab", POLISHED_VAULTSTONE);
    public static final DeferredHolder<Block, StairBlock> POLISHED_VAULTSTONE_STAIRS = stairs("polished_vaultstone_stairs", POLISHED_VAULTSTONE);
    public static final DeferredHolder<Block, WallBlock> POLISHED_VAULTSTONE_WALL = wall("polished_vaultstone_wall", POLISHED_VAULTSTONE);
    public static final DeferredHolder<Block, SlabBlock> VAULTSTONE_BRICKS_SLAB = slab("vaultstone_bricks_slab", VAULTSTONE_BRICKS);
    public static final DeferredHolder<Block, StairBlock> VAULTSTONE_BRICKS_STAIRS = stairs("vaultstone_bricks_stairs", VAULTSTONE_BRICKS);
    public static final DeferredHolder<Block, WallBlock> VAULTSTONE_BRICKS_WALL = wall("vaultstone_bricks_wall", VAULTSTONE_BRICKS);

    private VaultMaterials() {}

    private static BlockBehaviour.Properties stone(float hardness) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                .mapColor(MapColor.COLOR_PURPLE).strength(hardness, 6.0F).requiresCorrectToolForDrops();
    }

    private static DeferredHolder<Block, Block> block(String name, float hardness) {
        var holder = BLOCKS.register(name, () -> new Block(stone(hardness)));
        item(name, holder);
        return holder;
    }

    private static DeferredHolder<Block, SlabBlock> slab(String name, DeferredHolder<Block, Block> base) {
        var holder = BLOCKS.register(name, () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(base.get())));
        item(name, holder);
        return holder;
    }

    private static DeferredHolder<Block, StairBlock> stairs(String name, DeferredHolder<Block, Block> base) {
        var holder = BLOCKS.register(name, () -> new StairBlock(base.get().defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(base.get())));
        item(name, holder);
        return holder;
    }

    private static DeferredHolder<Block, WallBlock> wall(String name, DeferredHolder<Block, Block> base) {
        var holder = BLOCKS.register(name, () -> new WallBlock(BlockBehaviour.Properties.ofFullCopy(base.get())));
        item(name, holder);
        return holder;
    }

    private static void item(String name, DeferredHolder<Block, ? extends Block> block) {
        BUILDING_ITEMS.add(ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    public static void displayItems(CreativeModeTab.Output output) {
        BUILDING_ITEMS.forEach(item -> output.accept(item.get()));
    }
}
