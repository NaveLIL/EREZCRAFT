package pro.erez.interstice.entity;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import com.azure.azurecortex.navigation.astar.*;
import com.azure.azurecortex.navigation.crawl.CrawlTraversalEvaluator;
import com.azure.azurecortex.navigation.traversal.*;

/** AzureCortex A* and crawl neighbors, adapted to our full cubic bodies.
 * Upstream slim movement is deliberately not used: every node and swept segment
 * must fit the same volume players can hit. No second local graph search runs. */
public final class AzureSpiderNavigation implements TraversalEvaluator {
    private final CaveRiftSpiderEntity spider;
    private final SpiderSurfacePath collision;
    private final Map<BlockPos,SpiderSurfacePath.Step> nodes=new HashMap<>();
    private Vec3 destination;
    public AzureSpiderNavigation(CaveRiftSpiderEntity spider){this.spider=spider;collision=new SpiderSurfacePath(spider);}

    private boolean loaded(BlockPos pos){
        // Azure's neighbor classifiers probe shafts up/down and adjacent columns.
        for(int x=(pos.getX()-3)>>4;x<=(pos.getX()+3)>>4;x++)
            for(int z=(pos.getZ()-3)>>4;z<=(pos.getZ()+3)>>4;z++)
                if(!(spider.level() instanceof net.minecraft.server.level.ServerLevel server)||server.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
    private SpiderSurfacePath.Step node(BlockPos pos){
        if(nodes.containsKey(pos))return nodes.get(pos);
        if(!loaded(pos))return null;
        double radius=spider.getBbWidth()/2;
        Vec3 nominal=Vec3.atBottomCenterOf(pos).add(0,radius,0);
        SpiderSurfacePath.Step best=null;double distance=.95*.95;
        BlockPos base=BlockPos.containing(nominal);
        for(int x=-1;x<=1;x++)for(int y=-2;y<=2;y++)for(int z=-1;z<=1;z++){
            BlockPos support=base.offset(x,y,z);var state=spider.level().getBlockState(support);
            if(!state.getFluidState().isEmpty())continue;
            for(Direction face:Direction.values()){
                if(!state.isFaceSturdy(spider.level(),support,face.getOpposite()))continue;
                Vec3 center=Vec3.atCenterOf(support).subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(.5+radius));
                // A face may slide tangentially; preserve the voxel center in those axes.
                center=switch(face.getAxis()){
                    case X->new Vec3(center.x,nominal.y,nominal.z);
                    case Y->new Vec3(nominal.x,center.y,nominal.z);
                    case Z->new Vec3(nominal.x,nominal.y,center.z);
                };
                boolean touches=true;
                for(Direction.Axis axis:Direction.Axis.values())if(axis!=face.getAxis()){
                    double coordinate=center.get(axis),block=support.get(axis);
                    if(Math.min(coordinate+radius,block+1)-Math.max(coordinate-radius,block)<.05){touches=false;break;}
                }
                if(!touches)continue;
                double score=center.distanceToSqr(nominal);
                if(score<distance&&collision.clear(center)){distance=score;best=new SpiderSurfacePath.Step(support,face,center);}
            }
        }
        nodes.put(pos.immutable(),best);return best;
    }
    public List<SpiderSurfacePath.Step> find(Vec3 goal){
        destination=goal;
        BlockPos start=spider.blockPosition(),end=BlockPos.containing(goal.add(0,-spider.getBbHeight()/2,0));
        if(!loaded(start)||!loaded(end)||node(start)==null)return List.of();
        var search=IncrementalPathSession.of(spider,start,end,24,0,384,new PathNodeCache(),this);
        search.step(384);
        if(Boolean.getBoolean("interstice.azureDebug"))System.out.println("AZURE_ROUTE variant="+spider.getVariant()+" start="+start+" goal="+end+" nodes="+search.nodesSearched()+" result="+search.result());
        List<SpiderSurfacePath.Step> path=new ArrayList<>();
        Vec3 previous=spider.getBoundingBox().getCenter();
        for(BlockPos pos:search.result()){
            var next=node(pos);
            if(next==null||!collision.clearSegment(previous,next.center()))return List.of();
            path.add(next);previous=next.center();
        }
        if(path.isEmpty()||previous.distanceTo(goal)>1.1)return List.of();
        SpiderSurfacePath.Step endStep=null;
        for(Direction face:new Direction[]{Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.DOWN}){
            var support=BlockPos.containing(goal.add(Vec3.atLowerCornerOf(face.getNormal()).scale(spider.getBbWidth()/2+.01)));
            if(loaded(support)&&spider.level().getBlockState(support).isFaceSturdy(spider.level(),support,face.getOpposite())&&collision.clear(goal)){
                endStep=new SpiderSurfacePath.Step(support,face,goal);break;
            }
        }
        if(endStep==null||!collision.clearSegment(previous,goal))return List.of();
        path.add(endStep);
        // Azure emits multi-block rise/drop edges. Keep the displacement guard
        // meaningful by sampling the already collision-checked edge into strides.
        List<SpiderSurfacePath.Step> strides=new ArrayList<>();previous=spider.getBoundingBox().getCenter();
        for(var step:path){
            int count=Math.max(1,(int)Math.ceil(previous.distanceTo(step.center())/.75));Vec3 from=previous;
            for(int i=1;i<=count;i++)strides.add(new SpiderSurfacePath.Step(step.support(),step.face(),from.lerp(step.center(),(double)i/count)));
            previous=step.center();
        }
        return List.copyOf(strides);
    }
    @Override public List<BlockPos> neighbors(TraversalContext context,BlockPos pos,BlockPos goal){
        if(!loaded(pos))return List.of();
        var current=node(pos);if(current==null)return List.of();
        var candidates=new LinkedHashSet<>(CrawlTraversalEvaluator.INSTANCE.neighbors(context,pos,goal));
        // Azure's ground loop validates full grid-centered walking boxes, while
        // its climb loop starts only when already next to a wall. A >1-block
        // body cannot enter that first grid cell as a walk. Bridge that handoff
        // using Azure's own climb classification and the real snapped body.
        for(Direction direction:Direction.values()){
            var next=pos.relative(direction);
            if(loaded(next)&&context.cache().isSafeClimbNode(spider.level(),next,spider)
                    &&context.cache().hasClimbClearance(spider.level(),spider,next))candidates.add(next);
        }
        return candidates.stream().filter(next->{
            var at=node(next);return at!=null&&collision.clearSegment(current.center(),at.center());
        }).toList();
    }
    @Override public double cost(TraversalContext c,BlockPos from,BlockPos to){
        // Upstream cost also collision-tests its slim grid-centered body. Our
        // footprint is snapped to real faces instead; neighbors already swept it.
        var a=node(from);var b=node(to);
        return a==null||b==null?9999:a.center().distanceTo(b.center())+(a.face()==b.face()?0:.15);
    }
    @Override public double heuristic(BlockPos a,BlockPos b){return node(a)==null?9999:node(a).center().distanceTo(destination);}
    @Override public boolean isCloseEnoughToGoal(BlockPos pos,BlockPos goal,int radius){return node(pos)!=null&&node(pos).center().distanceTo(destination)<.65;}
    @Override public boolean isSolidlySeparatedVertically(net.minecraft.world.level.Level level,BlockPos pos,BlockPos goal){return false;}
}
