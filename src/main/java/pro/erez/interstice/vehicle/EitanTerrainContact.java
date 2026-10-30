package pro.erez.interstice.vehicle;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Three stock rays discover the terrain. Analytic circle support over each hit
 * top face removes the instant half-block spring compression of point probes.
 * Moving sub-level contacts retain Offroad's original path. No extra world rays.
 */
public final class EitanTerrainContact {
    private static final class Scratch {final Vector3d point=new Vector3d(),normal=new Vector3d();final double[] q=new double[4],p=new double[4];}
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    public static Vec3 support(EitanWheelEntity wheel,Vec3 raw,Vector3dc rolling,Pose3dc pose,BlockHitResult hit,Vec3 anchor){
        if(hit.getDirection()!=Direction.UP||Sable.HELPER.getContaining(wheel.getLevel(),hit.getLocation())!=null)return raw;
        var state=wheel.getLevel().getBlockState(hit.getBlockPos());var boxes=wheel.terrainBoxes(state.getCollisionShape(wheel.getLevel(),hit.getBlockPos()));
        var s=SCRATCH.get();s.normal.set(0,1,0);pose.transformNormalInverse(s.normal);if(s.normal.y<.7)return raw;
        double slope=-(s.normal.x*rolling.x()+s.normal.z*rolling.z())/s.normal.y;
        double rawQ=(raw.x-anchor.x)*rolling.x()+(raw.z-anchor.z)*rolling.z(),y0=raw.y-slope*rawQ;
        double best=Double.NEGATIVE_INFINITY;var at=hit.getLocation();var pos=hit.getBlockPos();
        for(var box:boxes){double top=pos.getY()+box.maxY;if(Math.abs(top-at.y)>1e-4||at.x<pos.getX()+box.minX-1e-5||at.x>pos.getX()+box.maxX+1e-5||at.z<pos.getZ()+box.minZ-1e-5||at.z>pos.getZ()+box.maxZ+1e-5)continue;
            for(int i=0;i<4;i++){double x=(i==0||i==3?box.minX:box.maxX)+pos.getX(),z=(i<2?box.minZ:box.maxZ)+pos.getZ();s.point.set(x,top,z);pose.transformPositionInverse(s.point);double dx=s.point.x-anchor.x,dz=s.point.z-anchor.z;s.q[i]=dx*rolling.x()+dz*rolling.z();s.p[i]=dx*rolling.z()-dz*rolling.x();}
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<4;i++){int j=(i+1)%4;if(Math.abs(s.p[i])<1e-5){min=Math.min(min,s.q[i]);max=Math.max(max,s.q[i]);}if(s.p[i]*s.p[j]<0){double t=s.p[i]/(s.p[i]-s.p[j]),q=s.q[i]+t*(s.q[j]-s.q[i]);min=Math.min(min,q);max=Math.max(max,q);}}
            if(min<=max)best=Math.max(best,EitanDynamics.circleSupport(y0,slope,min,max,EitanDynamics.RADIUS));
        }
        return Double.isFinite(best)?new Vec3(raw.x,best,raw.z):raw;
    }
    private EitanTerrainContact(){}
}
