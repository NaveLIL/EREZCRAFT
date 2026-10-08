package pro.erez.interstice.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

/** Complete material family and hanging vines for the Pale Gardens. */
public final class GardenMaterials {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,Interstice.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    private static final List<DeferredHolder<Item,BlockItem>> DISPLAY=new ArrayList<>();
    public static final DeferredHolder<Block,RotatedPillarBlock> PALEHEART_LOG=register("paleheart_log",()->new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)) {
        @Override public BlockState getToolModifiedState(BlockState state,UseOnContext context,ItemAbility action,boolean simulate) {
            if(action==ItemAbilities.AXE_STRIP) return STRIPPED_PALEHEART_LOG.get().defaultBlockState().setValue(AXIS,state.getValue(AXIS));
            return super.getToolModifiedState(state,context,action,simulate);
        }
    });
    public static final DeferredHolder<Block,RotatedPillarBlock> STRIPPED_PALEHEART_LOG=register("stripped_paleheart_log",()->new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_LOG)));
    public static final DeferredHolder<Block,Block> PALEHEART_PLANKS=register("paleheart_planks",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
    public static final DeferredHolder<Block,LeavesBlock> PALEHEART_LEAVES=register("paleheart_leaves",()->new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES).lightLevel(s->1)));
    public static final DeferredHolder<Block,GardenSapling> PALEHEART_SAPLING=register("paleheart_sapling",GardenSapling::new);
    public static final DeferredHolder<Block,GardenSapling> CROWN_SAPLING=register("crown_sapling",()->new GardenSapling(){
        @Override protected net.minecraft.resources.ResourceLocation definition(){return GardenTreeDefinitions.CROWN;}
    });
    public static final DeferredHolder<Block,SlabBlock> PALEHEART_SLAB=register("paleheart_slab",()->new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SLAB)));
    public static final DeferredHolder<Block,StairBlock> PALEHEART_STAIRS=register("paleheart_stairs",()->new StairBlock(PALEHEART_PLANKS.get().defaultBlockState(),BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_STAIRS)));
    public static final DeferredHolder<Block,FenceBlock> PALEHEART_FENCE=register("paleheart_fence",()->new FenceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE)));
    public static final DeferredHolder<Block,FenceGateBlock> PALEHEART_FENCE_GATE=register("paleheart_fence_gate",()->new FenceGateBlock(WoodType.OAK,BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE_GATE)));
    public static final DeferredHolder<Block,Block> PALESTONE=register("palestone",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(2F,6F)));
    public static final DeferredHolder<Block,Block> PALESTONE_BRICKS=register("palestone_bricks",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)));
    public static final DeferredHolder<Block,SlabBlock> PALESTONE_SLAB=register("palestone_slab",()->new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB)));
    public static final DeferredHolder<Block,StairBlock> PALESTONE_STAIRS=register("palestone_stairs",()->new StairBlock(PALESTONE.get().defaultBlockState(),BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_STAIRS)));
    public static final DeferredHolder<Block,WallBlock> PALESTONE_WALL=register("palestone_wall",()->new WallBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICK_WALL)));
    public static final DeferredHolder<Block,GardenBush> PALE_FERN=register("pale_fern",GardenBush::new);
    public static final DeferredHolder<Block,GardenVineBlock> PALE_VINE=register("pale_vine",GardenVineBlock::new);
    // No BlockItem: a harvested food cannot plant a crown pod on the ground.
    public static final DeferredHolder<Block,pro.erez.interstice.food.CrownFruitBlock> CROWN_FRUIT=BLOCKS.register("crown_fruit",pro.erez.interstice.food.CrownFruitBlock::new);
    public static final DeferredHolder<Block,CarpetBlock> PALE_LITTER=register("pale_litter",()->new CarpetBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MOSS_CARPET).lightLevel(s->1)) {
        @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) { return level.getBlockState(pos.below()).is(Interstice.ABYSSAL_TURF.get()); }
    });
    private static <T extends Block> DeferredHolder<Block,T> register(String name,java.util.function.Supplier<T> supplier) {
        var block=BLOCKS.register(name,supplier); DISPLAY.add(ITEMS.register(name,()->new BlockItem(block.get(),new Item.Properties()))); return block;
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
    public static void displayItems(CreativeModeTab.Output output) { DISPLAY.forEach(item->output.accept(item.get())); }
    public static class GardenBush extends BushBlock {
        public GardenBush() { this(BlockBehaviour.Properties.ofFullCopy(Blocks.FERN).randomTicks()); }
        public GardenBush(BlockBehaviour.Properties properties) { super(properties); }
        @Override protected boolean mayPlaceOn(BlockState state,BlockGetter level,BlockPos pos) { return state.is(Interstice.ABYSSAL_TURF.get()); }
        @Override public com.mojang.serialization.MapCodec<? extends GardenBush> codec() { return simpleCodec(GardenBush::new); }
    }
    public static class GardenSapling extends GardenBush implements BonemealableBlock {
        public GardenSapling() { super(); }
        public GardenSapling(BlockBehaviour.Properties properties) { super(properties); }
        @Override public com.mojang.serialization.MapCodec<GardenSapling> codec() { return simpleCodec(GardenSapling::new); }
        protected net.minecraft.resources.ResourceLocation definition(){return GardenTreeDefinitions.PALEHEART;}
        @Override public void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) { if(random.nextInt(7)==0) GardenTrees.grow(level,pos,random,definition()); }
        @Override public boolean isValidBonemealTarget(LevelReader level,BlockPos pos,BlockState state) { return true; }
        @Override public boolean isBonemealSuccess(Level level,RandomSource random,BlockPos pos,BlockState state) { return true; }
        @Override public void performBonemeal(ServerLevel level,RandomSource random,BlockPos pos,BlockState state) { GardenTrees.grow(level,pos,random,definition()); }
    }
}
