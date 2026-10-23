package pro.erez.interstice.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Separates the surface normal from heading within its tangent plane. */
public final class SpiderSurfaceFrame {
    public static Quaternionf rotation(Direction before,Direction after,float progress){
        var up=new Vector3f(0,1,0);
        return new Quaternionf().rotationTo(up,before.getOpposite().step())
                .slerp(new Quaternionf().rotationTo(up,after.getOpposite().step()),progress);
    }
    public static float bodyYaw(Quaternionf rotation,Vec3 forward,float fallback){
        var local=new Quaternionf(rotation).conjugate().transform(new Vector3f((float)forward.x,(float)forward.y,(float)forward.z));
        if(local.x*local.x+local.z*local.z<.00001F)return fallback;
        return (float)Math.toDegrees(Math.atan2(-local.x,local.z));
    }
    private SpiderSurfaceFrame(){}
}
