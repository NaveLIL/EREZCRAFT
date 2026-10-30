package pro.erez.interstice.vehicle;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** Stationary geometry fixture. Deliberately absent from ordinary pack registries. */
public final class EitanPreview {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,Interstice.ID);
    public static final DeferredHolder<Block,Part> PART=BLOCKS.register("eitan_preview",Part::new);
    public static void register(IEventBus bus){BLOCKS.register(bus);if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())bus.addListener(pro.erez.interstice.client.EitanModels::geometry);}
    public static final class Part extends Block {
        Part(){super(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(5,25).noOcclusion().lightLevel(s->EitanPreviewData.CELLS.get(s.getValue(EitanPreviewData.CELL)).light()));registerDefaultState(stateDefinition.any().setValue(EitanPreviewData.CELL,0).setValue(EitanPreviewData.POSE,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(EitanPreviewData.CELL,EitanPreviewData.POSE);}
        @Override protected VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext context){return EitanPreviewData.shape(s.getValue(EitanPreviewData.CELL),s.getValue(EitanPreviewData.POSE));}
    }
    public static void place(ServerLevel level,BlockPos origin){
        for(var c:EitanPreviewData.CELLS){var p=origin.offset(c.pos());if(p.getY()<level.getMinBuildHeight()||p.getY()>=level.getMaxBuildHeight()||!level.hasChunkAt(p)||!level.getBlockState(p).isAir()||level.getBlockEntity(p)!=null)throw new IllegalArgumentException("E01 needs a clear loaded site: "+p);}
        for(var c:EitanPreviewData.CELLS)level.setBlock(origin.offset(c.pos()),PART.get().defaultBlockState().setValue(EitanPreviewData.CELL,c.id()),3);
    }
    /** Explicit test fixture poses; this is not the gameplay animation controller. */
    public static void pose(ServerLevel level,BlockPos origin,int pose){
        if(pose<0||pose>3)throw new IllegalArgumentException("E01 pose");
        for(var c:EitanPreviewData.CELLS){var p=origin.offset(c.pos());var s=level.getBlockState(p);if(s.getBlock()!=PART.get()||s.getValue(EitanPreviewData.CELL)!=c.id())throw new IllegalStateException("Incomplete E01 fixture: "+p);}
        for(var c:EitanPreviewData.CELLS){var p=origin.offset(c.pos());level.setBlock(p,level.getBlockState(p).setValue(EitanPreviewData.POSE,pose),3);}
    }
    private EitanPreview(){}
}
