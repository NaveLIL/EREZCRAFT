package pro.erez.interstice.minerals;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

/** First mining/lighting chain uses only the realm's own wood and minerals. */
public final class MineralEcology {
    public static final int COAL_BURN_TICKS=3200;
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,Interstice.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE,Interstice.ID);
    private static final List<DeferredHolder<Item,? extends Item>> DISPLAY=new ArrayList<>();
    public static final DeferredHolder<Block,Block> ROOT_LOAM=material("root_loam",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT)));
    public static final DeferredHolder<Block,Block> RIFT_SHALE=material("rift_shale",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).strength(2.2F,6F)));
    public static final DeferredHolder<Block,Block> TOXIC_SAND=material("toxic_sand",()->new net.minecraft.world.level.block.ColoredFallingBlock(new net.minecraft.util.ColorRGBA(0xffb1ab78),BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)));
    public static final DeferredHolder<Block,MineralFrostBlock> MINERAL_FROST=BLOCKS.register("mineral_frost",()->new MineralFrostBlock());
    public static final DeferredHolder<Block,MineralPowderBlock> MINERAL_POWDER=BLOCKS.register("mineral_powder",()->new MineralPowderBlock());
    static{item("mineral_frost",()->new BlockItem(MINERAL_FROST.get(),new Item.Properties()));item("mineral_powder",()->new BlockItem(MINERAL_POWDER.get(),new Item.Properties()));}
    public static final DeferredHolder<Block,RiftOreBlock> RIFTSILVER_SEAM=ore("riftsilver_seam",1,3,3F,0);
    public static final DeferredHolder<Block,RiftOreBlock> UMBRAL_COAL_ORE=ore("umbral_coal_ore",0,2,2.5F,0);
    public static final DeferredHolder<Block,RiftOreBlock> PHOSPHORITE_ORE=ore("phosphorite_ore",1,3,2.5F,2);
    public static final DeferredHolder<Block,RiftOreBlock> VITRIOLITE_ORE=ore("vitriolite_ore",2,4,3.5F,0);
    public static final DeferredHolder<Item,Item> UMBRAL_COAL=item("umbral_coal",()->new Item(new Item.Properties()){
        @Override public int getBurnTime(ItemStack stack,RecipeType<?> recipe){return COAL_BURN_TICKS;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){text.add(Component.translatable("tooltip.interstice.umbral_coal"));}
    });
    public static final DeferredHolder<Item,Item> PHOSPHORITE_CRYSTAL=item("phosphorite_crystal",()->new Item(new Item.Properties()));
    public static final DeferredHolder<Item,Item> VITRIOLITE_SHARD=item("vitriolite_shard",()->new Item(new Item.Properties()));
    public static final DeferredHolder<Item,Item> WORLD_STICK=item("world_stick",()->new Item(new Item.Properties()){
        @Override public int getBurnTime(ItemStack stack,RecipeType<?> recipe){return 100;}
    });
    public static final DeferredHolder<Item,Item> LUMINOUS_BUD=item("luminous_bud",()->new Item(new Item.Properties()){
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){text.add(Component.translatable("tooltip.interstice.luminous_bud"));}
    });
    public static final DeferredHolder<Block,TorchBlock> COAL_TORCH=BLOCKS.register("coal_torch",()->new TorchBlock(ParticleTypes.SMALL_FLAME,BlockBehaviour.Properties.ofFullCopy(Blocks.TORCH).lightLevel(s->12)));
    public static final DeferredHolder<Block,WallTorchBlock> COAL_WALL_TORCH=BLOCKS.register("coal_wall_torch",()->new WallTorchBlock(ParticleTypes.SMALL_FLAME,BlockBehaviour.Properties.ofFullCopy(Blocks.WALL_TORCH).lightLevel(s->12)));
    public static final DeferredHolder<Block,LivingTorchBlock> LIVING_TORCH=BLOCKS.register("living_torch",()->new LivingTorchBlock());
    public static final DeferredHolder<Block,LivingWallTorchBlock> LIVING_WALL_TORCH=BLOCKS.register("living_wall_torch",()->new LivingWallTorchBlock());
    public static final DeferredHolder<Item,StandingAndWallBlockItem> COAL_TORCH_ITEM=item("coal_torch",()->new StandingAndWallBlockItem(COAL_TORCH.get(),COAL_WALL_TORCH.get(),new Item.Properties(),Direction.DOWN));
    public static final DeferredHolder<Item,StandingAndWallBlockItem> LIVING_TORCH_ITEM=item("living_torch",()->new StandingAndWallBlockItem(LIVING_TORCH.get(),LIVING_WALL_TORCH.get(),new Item.Properties(),Direction.DOWN){
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){text.add(Component.translatable("tooltip.interstice.living_torch"));}
    });
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<LivingTorchBlockEntity>> LIVING_TORCH_ENTITY=ENTITIES.register("living_torch",()->BlockEntityType.Builder.of(LivingTorchBlockEntity::new,LIVING_TORCH.get(),LIVING_WALL_TORCH.get()).build(null));
    private MineralEcology(){}
    private static DeferredHolder<Block,RiftOreBlock> ore(String name,int min,int max,float strength,int light){
        var block=BLOCKS.register(name,()->new RiftOreBlock(UniformInt.of(min,max),BlockBehaviour.Properties.ofFullCopy(Blocks.COAL_ORE).strength(strength,6F).lightLevel(s->light)));
        item(name,()->new BlockItem(block.get(),new Item.Properties()));return block;
    }
    private static DeferredHolder<Block,Block> material(String name,java.util.function.Supplier<Block> factory){var block=BLOCKS.register(name,factory);item(name,()->new BlockItem(block.get(),new Item.Properties()));return block;}
    private static <T extends Item> DeferredHolder<Item,T> item(String name,java.util.function.Supplier<T> factory){var holder=ITEMS.register(name,factory);DISPLAY.add(holder);return holder;}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);}
    public static void displayItems(CreativeModeTab.Output output){DISPLAY.forEach(item->output.accept(item.get()));}
}
