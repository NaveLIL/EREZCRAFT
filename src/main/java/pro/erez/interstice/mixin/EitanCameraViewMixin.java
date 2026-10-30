package pro.erez.interstice.mixin;
import net.minecraft.client.Camera;
import org.joml.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.client.EitanCameras;
@Mixin(Camera.class)
public abstract class EitanCameraViewMixin {
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow protected abstract void setPosition(double x,double y,double z);
    @Inject(method="setup",at=@At("TAIL")) private void interstice$sensorPose(net.minecraft.world.level.BlockGetter level,net.minecraft.world.entity.Entity entity,boolean detached,boolean mirrored,float partial,CallbackInfo ci){
        if(EitanCameras.CAPTURING){rotation.set(EitanCameras.orientation);forwards.set(0,0,-1).rotate(rotation);up.set(0,1,0).rotate(rotation);left.set(-1,0,0).rotate(rotation);return;}
        if(detached||entity!=net.minecraft.client.Minecraft.getInstance().player||!(entity.getVehicle() instanceof pro.erez.interstice.vehicle.EitanSeatEntity seat))return;
        var c=seat.controller();if(c==null||!(c.body() instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel body))return;
        var s=pro.erez.interstice.vehicle.EitanData.SEATS.get(seat.seatIndex());var o=c.origin();var eye=(EitanCameraAccess)this;
        double height=net.minecraft.util.Mth.lerp(partial,eye.interstice$getOldEye(),eye.interstice$getEye());
        var point=body.renderPose(partial).transformPosition(new Vector3d(o.getX()+s.x(),o.getY()+s.y()-.62+height,o.getZ()+s.z()+(seat.seatIndex()==0?.16:0)));
        setPosition(point.x,point.y,point.z);
    }
}
