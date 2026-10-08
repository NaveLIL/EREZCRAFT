package pro.erez.interstice.worldgen.cave;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

/** Stone deposits in all three cave habitats; no vanilla bitmap or hardcoded vanilla growth. */
public final class CaveMaterials {
    private CaveMaterials() {}
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,Interstice.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    private static final List<DeferredHolder<Item,BlockItem>> DISPLAY=new ArrayList<>();
    public static final DeferredHolder<Block,CaveSpireBlock> ASH_SPIRE=spire("ash_spire");
    public static final DeferredHolder<Block,CaveSpireBlock> GARDEN_SPIRE=spire("garden_spire");
    public static final DeferredHolder<Block,CaveSpireBlock> VAULT_SPIRE=spire("vault_spire");
    private static DeferredHolder<Block,CaveSpireBlock> spire(String name) {
        var block=BLOCKS.register(name,()->new CaveSpireBlock());
        DISPLAY.add(ITEMS.register(name,()->new BlockItem(block.get(),new Item.Properties())));
        return block;
    }
    public static void register(IEventBus bus) {BLOCKS.register(bus);ITEMS.register(bus);}
    public static void displayItems(CreativeModeTab.Output output) {DISPLAY.forEach(item->output.accept(item.get()));}
    public static final class CaveSpireBlock extends Block {
        public static final EnumProperty<Direction> TIP_DIRECTION=BlockStateProperties.VERTICAL_DIRECTION;
        public static final EnumProperty<DripstoneThickness> THICKNESS=BlockStateProperties.DRIPSTONE_THICKNESS;
        public CaveSpireBlock() {this(BlockBehaviour.Properties.ofFullCopy(Blocks.POINTED_DRIPSTONE).sound(SoundType.DRIPSTONE_BLOCK));}
        public CaveSpireBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(TIP_DIRECTION,Direction.UP).setValue(THICKNESS,DripstoneThickness.TIP));
        }
        @Override public com.mojang.serialization.MapCodec<CaveSpireBlock> codec() {return simpleCodec(CaveSpireBlock::new);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction direction=context.getClickedFace()==Direction.DOWN?Direction.DOWN:Direction.UP;
            return defaultBlockState().setValue(TIP_DIRECTION,direction);
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {builder.add(TIP_DIRECTION,THICKNESS);}
        @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
            var direction=state.getValue(TIP_DIRECTION);
            var supportPos=pos.relative(direction.getOpposite());
            var support=level.getBlockState(supportPos);
            if(support.getBlock() instanceof CaveSpireBlock) return support.getValue(TIP_DIRECTION)==direction;
            return support.getFluidState().isEmpty() && support.isFaceSturdy(level,supportPos,direction);
        }
        @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
            if(direction==state.getValue(TIP_DIRECTION).getOpposite()&&!canSurvive(state,level,pos))return Blocks.AIR.defaultBlockState();
            return super.updateShape(state,direction,neighbor,level,pos,neighborPos);
        }
        @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
            double wide=switch(state.getValue(THICKNESS)) {case TIP,TIP_MERGE->3;case FRUSTUM->5;case MIDDLE->7;case BASE->9;};
            double thin=Math.max(1,wide-2);
            VoxelShape bottom=Block.box(8-wide/2,0,8-wide/2,8+wide/2,8,8+wide/2);
            VoxelShape top=Block.box(8-thin/2,8,8-thin/2,8+thin/2,16,8+thin/2);
            if(state.getValue(TIP_DIRECTION)==Direction.DOWN) {
                bottom=Block.box(8-thin/2,0,8-thin/2,8+thin/2,8,8+thin/2);
                top=Block.box(8-wide/2,8,8-wide/2,8+wide/2,16,8+wide/2);
            }
            return Shapes.or(bottom,top);
        }
    }
}
