package pro.erez.interstice.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public class MarineBlock extends Block {
    public static final IntegerProperty POSE=IntegerProperty.create("pose",0,3);
    public MarineBlock(){super(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(5,25).noOcclusion().lightLevel(s->MarineData.cell(s.getValue(MarineData.CELL)).light()));registerDefaultState(stateDefinition.any().setValue(MarineData.CELL,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(MarineData.CELL);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return MarineData.shape(s.getValue(MarineData.CELL),s.hasProperty(POSE)?s.getValue(POSE):0);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level w,BlockPos p,Player player,BlockHitResult hit){
        var c=MarineData.cell(s.getValue(MarineData.CELL));var engineOrigin=p.subtract(c.pos());var enginePoint=hit.getLocation().subtract(engineOrigin.getX(),engineOrigin.getY(),engineOrigin.getZ());
        if(MarineMe.openScreen(w,engineOrigin,enginePoint,player))return InteractionResult.sidedSuccess(w.isClientSide);
        if(Math.abs(enginePoint.x)<1.25&&enginePoint.y>1.5&&enginePoint.y<3.0&&enginePoint.z>-7.1&&enginePoint.z< -3.7&&w.getBlockEntity(engineOrigin.offset(MarineMe.GENERATOR)) instanceof MarineGeneratorEntity g){
            if(!w.isClientSide){if(player.isShiftKeyDown())g.enabled(!g.enabled());player.displayClientMessage(net.minecraft.network.chat.Component.literal("NE01 · "+(g.enabled()?"ВКЛ":"ВЫКЛ")+" · "+(int)g.getAECurrentPower()+" AE"),true);}return InteractionResult.sidedSuccess(w.isClientSide);
        }
        if(c.mechanisms().isEmpty())return InteractionResult.PASS;
        if(w.isClientSide)return InteractionResult.SUCCESS;
        int mask=s.getValue(POSE);int chosen=0;double best=Double.POSITIVE_INFINITY;
        var at=hit.getLocation().subtract(p.getX(),p.getY(),p.getZ());
        for(int i=0;i<c.mechanisms().size();i++)for(var b:((mask&(1<<i))==0?c.mechanisms().get(i).closed():c.mechanisms().get(i).open())){
            double dx=Math.max(b[0]-at.x,Math.max(0,at.x-b[3])),dy=Math.max(b[1]-at.y,Math.max(0,at.y-b[4])),dz=Math.max(b[2]-at.z,Math.max(0,at.z-b[5]));double distance=dx*dx+dy*dy+dz*dz;if(distance<best){best=distance;chosen=i;}
        }
        int global=c.mechanisms().get(chosen).index();boolean open=(mask&(1<<chosen))==0;var origin=p.subtract(c.pos());
        // Endpoint poses must not place a leaf or gangway inside a living actor.
        // This is deliberately conservative for rotated Sable hulls.
        for(var other:MarineData.CELLS)for(var g:other.mechanisms())if(g.index()==global)for(var b:open?g.open():g.closed()){
            var local=new net.minecraft.world.phys.AABB(b[0],b[1],b[2],b[3],b[4],b[5]).move(origin.offset(other.pos()));
            if(occupied(w,origin.offset(other.pos()),local))return InteractionResult.FAIL;
        }
        setPose(w,origin,global,open);
        return InteractionResult.CONSUME;
    }
    @Override protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState s,Level w,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        var c=MarineData.cell(s.getValue(MarineData.CELL));var origin=p.subtract(c.pos());var at=hit.getLocation().subtract(origin.getX(),origin.getY(),origin.getZ());
        boolean engine=Math.abs(at.x)<1.25&&at.y>1.5&&at.y<3.0&&at.z>-7.1&&at.z< -3.7;
        if((engine||w.getBlockEntity(p) instanceof MarineGeneratorEntity)&&w.getBlockEntity(origin.offset(MarineMe.GENERATOR)) instanceof MarineGeneratorEntity generator){
            if(w.isClientSide)return ItemInteractionResult.SUCCESS;
            if(generator.addFuel(stack,player.isCreative()))return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    public static void setPose(Level w,BlockPos origin,int global,boolean open){
        for(var c:MarineData.CELLS)for(int i=0;i<c.mechanisms().size();i++)if(c.mechanisms().get(i).index()==global){var p=origin.offset(c.pos());var state=w.getBlockState(p);if(!state.hasProperty(POSE))continue;int mask=state.getValue(POSE);w.setBlock(p,state.setValue(POSE,open?mask|(1<<i):mask&~(1<<i)),3);}
    }
    private static boolean occupied(Level w,BlockPos pos,net.minecraft.world.phys.AABB local){
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(w);
        if(container==null||!container.inBounds(pos))return !w.getEntities(null,local).isEmpty();
        var plot=container.getPlot(new net.minecraft.world.level.ChunkPos(pos));if(plot==null)return !w.getEntities(null,local).isEmpty();
        net.minecraft.world.phys.AABB bounds=null;
        for(double x:new double[]{local.minX,local.maxX})for(double y:new double[]{local.minY,local.maxY})for(double z:new double[]{local.minZ,local.maxZ}){
            var p=plot.getSubLevel().logicalPose().transformPosition(new org.joml.Vector3d(x,y,z));var point=new net.minecraft.world.phys.AABB(p.x,p.y,p.z,p.x,p.y,p.z);bounds=bounds==null?point:bounds.minmax(point);
        }
        return !w.getEntities(null,bounds.deflate(.001)).isEmpty();
    }
    public static final class Opening extends MarineBlock {
        public Opening(){super();registerDefaultState(defaultBlockState().setValue(POSE,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(POSE);}
    }
}
