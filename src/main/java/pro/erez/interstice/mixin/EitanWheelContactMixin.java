package pro.erez.interstice.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import pro.erez.interstice.vehicle.EitanWheelEntity;

/** Scoped to our subclass; ordinary Offroad vehicles keep their original paths. */
@Pseudo
@Mixin(targets="dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity",remap=false)
public abstract class EitanWheelContactMixin {
    @ModifyExpressionValue(method={"sable$physicsTick","computeMaxExtensionToTerrain"},at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;getCenter()Lnet/minecraft/world/phys/Vec3;",remap=true))
    private Vec3 interstice$physicalHub(Vec3 original){return (Object)this instanceof EitanWheelEntity wheel?wheel.physicalAnchor():original;}
    @ModifyExpressionValue(method="tick",at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/companion/math/JOMLConversion;atCenterOf(Lnet/minecraft/core/Vec3i;)Lorg/joml/Vector3d;"))
    private Vector3d interstice$hubVelocity(Vector3d original){if((Object)this instanceof EitanWheelEntity wheel){var p=wheel.physicalAnchor();return original.set(p.x,p.y,p.z);}return original;}
    @ModifyArg(method="computeMaxExtensionToTerrain",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;",remap=true),index=0)
    private double interstice$smallTyreProbes(double distance){return (Object)this instanceof EitanWheelEntity?distance*pro.erez.interstice.vehicle.EitanDynamics.RADIUS:distance;}
    @ModifyExpressionValue(method="sable$physicsTick",at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"))
    private Vector3d interstice$chassisDamping(Vector3d velocity){if((Object)this instanceof EitanWheelEntity wheel)velocity.y*=wheel.dampingMultiplier();return velocity;}
    @ModifyExpressionValue(method="computeMaxExtensionToTerrain",at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/companion/math/Pose3dc;transformPositionInverse(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 interstice$roundTerrain(Vec3 raw,@Local(argsOnly=true)org.joml.Vector3dc rolling,@Local(argsOnly=true)dev.ryanhcode.sable.companion.math.Pose3dc pose,@Local net.minecraft.world.phys.BlockHitResult hit,@Local(ordinal=0)Vec3 anchor){return (Object)this instanceof EitanWheelEntity wheel?pro.erez.interstice.vehicle.EitanTerrainContact.support(wheel,raw,rolling,pose,hit,anchor):raw;}
    @Redirect(method={"sable$physicsTick","tick"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",remap=true))
    private int interstice$pedalBrake(Level level,BlockPos pos,Direction direction){return (Object)this instanceof EitanWheelEntity wheel?wheel.brakeSignal():level.getSignal(pos,direction);}
}
