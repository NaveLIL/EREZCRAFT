package pro.erez.interstice.vehicle;

import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Bounded ride-height adjustment. Only Offroad spring anchors move; the hull
 * keeps its collisions, pose and normal rigid-body solver. Four casts at 5 Hz,
 * no chunk loads, and at most 0.60 m of additional suspension travel. */
public final class EitanClearance {
    public static float target(EitanController c){
        var body=c.body();if(body==null)return 0;
        double gap=Double.NEGATIVE_INFINITY;
        for(int side=-1;side<=1;side+=2)for(int end=-1;end<=1;end+=2){
            double x=side*1.1,z=end*4.10;
            var from=c.world(x,2,z);var to=c.world(x,-1,z);
            boolean loaded=true;for(int cx=((int)Math.floor(Math.min(from.x,to.x)))>>4;cx<=((int)Math.floor(Math.max(from.x,to.x)))>>4;cx++)for(int cz=((int)Math.floor(Math.min(from.z,to.z)))>>4;cz<=((int)Math.floor(Math.max(from.z,to.z)))>>4;cz++)if(!c.getLevel().hasChunkAt(new net.minecraft.core.BlockPos(cx<<4,0,cz<<4)))loaded=false;
            if(!loaded)continue;
            var cast=new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty());
            ((ClipContextExtension)cast).sable$setIgnoredSubLevel(body);
            VehicleMetrics.hit("eitan_clearance_ray");var hit=c.getLevel().clip(cast);
            if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP)continue;
            gap=Math.max(gap,hit.getLocation().y-c.world(x,.25,z).y+.08);
        }
        return Double.isFinite(gap)?EitanDynamics.clearanceTarget(c.clearanceLift,gap):0;
    }
    private EitanClearance(){}
}
