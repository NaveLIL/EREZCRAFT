package pro.erez.interstice.ship;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.andesite.AndesitePropellerBlock;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlockEntity;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;

/** The actual Aeronautics propeller actor, with SK02's radius and authored visible rotor. */
public final class SkatPropellerBlock extends AndesitePropellerBlock {
    public static final BooleanProperty PORT=BooleanProperty.create("port");
    public SkatPropellerBlock(){super(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(5,25).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(PORT);}
    @Override public BlockEntityType<? extends BasePropellerBlockEntity> getBlockEntityType(){return SkatShips.PROPELLER_ENTITY.get();}
    public static int index(BlockState s,String role){return SkatData.at(new BlockPos(s.getValue(PORT)?-3:3,6,role.equals("engine")?9:10)).id();}
    @Override public VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return SkatData.shape(index(s,"propeller"));}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!level.isClientSide&&!state.is(next.getBlock())&&Math.abs(pos.getX())>1000000&&System.getProperty("interstice.studioSmoke","").equals("skat")){
            System.out.println("SKAT_PROPELLER_REMOVED "+pos+" next="+next+" moving="+moving+" tick="+level.getGameTime());new IllegalStateException("Skat native removal diagnostic").printStackTrace();
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public float getDestroyProgress(net.minecraft.world.level.block.state.BlockState s,net.minecraft.world.entity.player.Player p,net.minecraft.world.level.BlockGetter w,net.minecraft.core.BlockPos pos){return pro.erez.interstice.vehicle.VehicleAccess.destroyProgress(s,p,w,pos);}
}
