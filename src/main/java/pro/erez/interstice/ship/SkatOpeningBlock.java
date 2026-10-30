package pro.erez.interstice.ship;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
public final class SkatOpeningBlock extends SkatBlock {
    public static final BooleanProperty OPEN=BooleanProperty.create("open");
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(OPEN);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext context){var result=super.getShape(s,w,p,context);
        if(!s.getValue(OPEN))for(var b:SkatData.cell(s.getValue(SkatData.CELL)).closed())result=Shapes.or(result,Shapes.box(b[0],b[1],b[2],b[3],b[4],b[5]));return result;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level w,BlockPos p,Player player,BlockHitResult hit){
        if(player.isShiftKeyDown())return InteractionResult.PASS;
        if(!w.isClientSide){var c=SkatData.cell(s.getValue(SkatData.CELL));var origin=p.subtract(c.pos());boolean open=!s.getValue(OPEN);
            if(!open)for(var other:SkatData.CELLS)if(other.opening().equals(c.opening()))for(var box:other.closed()){
                var pos=origin.offset(other.pos());var aabb=new AABB(box[0],box[1],box[2],box[3],box[4],box[5]).move(pos);
                if(occupied(w,pos,aabb))return InteractionResult.FAIL;
            }
            for(var other:SkatData.CELLS)if(other.opening().equals(c.opening())){var pos=origin.offset(other.pos());var state=w.getBlockState(pos);if(state.is(this))w.setBlock(pos,state.setValue(OPEN,open),3);}
        }return InteractionResult.sidedSuccess(w.isClientSide);
    }
    public static boolean isOpen(Level w,BlockPos origin,String type){for(var c:SkatData.CELLS)if(c.opening().equals(type)){var s=w.getBlockState(origin.offset(c.pos()));return s.hasProperty(OPEN)&&s.getValue(OPEN);}return false;}
    private static boolean occupied(Level world,BlockPos pos,AABB local){
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(world);
        if(container==null||!container.inBounds(pos))return !world.getEntities(null,local).isEmpty();
        var plot=container.getPlot(new net.minecraft.world.level.ChunkPos(pos));
        if(plot==null)return !world.getEntities(null,local).isEmpty();
        var pose=plot.getSubLevel().logicalPose();
        AABB bounds=null;
        for(double x:new double[]{local.minX,local.maxX})for(double y:new double[]{local.minY,local.maxY})for(double z:new double[]{local.minZ,local.maxZ}){
            var p=pose.transformPosition(new org.joml.Vector3d(x,y,z));
            var point=new AABB(p.x,p.y,p.z,p.x,p.y,p.z);bounds=bounds==null?point:bounds.minmax(point);
        }
        // Players remain in world coordinates while the hull lives in a Sable plot.
        return !world.getEntities(null,bounds).isEmpty();
    }
}
