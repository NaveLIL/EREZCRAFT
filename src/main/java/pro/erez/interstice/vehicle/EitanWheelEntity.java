package pro.erez.interstice.vehicle;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Offroad owns contact, spring and traction solving; this supplies the chassis geometry. */
public final class EitanWheelEntity extends WheelMountBlockEntity {
    private ScrollValueBehaviour suspension;private EitanController controllerCache;
    private final Map<net.minecraft.world.phys.shapes.VoxelShape,List<net.minecraft.world.phys.AABB>> terrainShapes=new IdentityHashMap<>();
    public List<net.minecraft.world.phys.AABB> terrainBoxes(net.minecraft.world.phys.shapes.VoxelShape shape){var old=terrainShapes.get(shape);if(old!=null)return old;if(terrainShapes.size()>=32)terrainShapes.clear();var boxes=shape.toAabbs();terrainShapes.put(shape,boxes);return boxes;}
    private ItemStack tire;
    private double rotation,lastRotation;
    public volatile int remappedContacts;
    public volatile Vec3 lastPhysicalAnchor=Vec3.ZERO;
    public EitanWheelEntity(BlockPos p,BlockState s){super(EitanVehicles.WHEEL_ENTITY.get(),p,s);}
    public EitanData.Mount mount(){return EitanData.MOUNTS.get(getBlockState().getValue(EitanWheelBlock.SLOT));}
    public BlockPos origin(){return worldPosition.subtract(mount().pos());}
    public EitanController controller(){if(controllerCache!=null&&!controllerCache.isRemoved()&&controllerCache.getLevel()==level)return controllerCache;VehicleMetrics.hit("eitan_controller_lookup");return controllerCache=level!=null&&level.getBlockEntity(origin().offset(EitanData.CONTROL)) instanceof EitanController c?c:null;}
    @Override public void addBehaviours(List<BlockEntityBehaviour> b){super.addBehaviours(b);for(var v:b)if(v instanceof ScrollValueBehaviour s)suspension=s;}
    @Override public ItemStack getHeldItem(){if(tire==null){tire=new ItemStack(EitanVehicles.TIRE.get());tire.set(OffroadDataComponents.TIRE,new TireLike((float)EitanDynamics.RADIUS));}return tire;}
    @Override public float getSpeed(){var c=controller();return c==null?0:-c.driveEffort;}
    @Override protected double computeYaw(){var c=controller();var m=mount();return c==null?0:EitanDynamics.steering(c.steer,c.speed,m.hub()[0],m.axle(),m.hub()[2]);}
    public double yaw(float partial){return getLerpedYaw(partial);}
    public int brakeSignal(){var c=controller();return c==null||c.brake?15:0;}
    public Vec3 physicalAnchor(){var c=controller();var o=origin();var m=mount();var v=new Vec3(o.getX()+m.hub()[0],o.getY()+(c==null?1.1:c.suspensionAnchor),o.getZ()+m.hub()[2]);lastPhysicalAnchor=v;remappedContacts++;return v;}
    public double dampingMultiplier(){var c=controller();return c==null?1:c.damping();}
    @Override public void tick(){super.tick();var c=controller();if(c==null)return;if(suspension!=null)suspension.value=c.suspensionStrength;
        if(level.isClientSide){lastRotation=rotation;var body=c.body();double velocity=0;if(body!=null){var p=physicalAnchor();var v=dev.ryanhcode.sable.Sable.HELPER.getVelocity(level,new org.joml.Vector3d(p.x,p.y,p.z));body.logicalPose().transformNormalInverse(v);double yaw=computeYaw();velocity=v.x*Math.sin(yaw)+v.z*Math.cos(yaw);}rotation+=velocity/EitanDynamics.RADIUS/20.;}
    }
    @Override public void sable$physicsTick(dev.ryanhcode.sable.sublevel.ServerSubLevel body,dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle,double step){long t=VehicleMetrics.begin();super.sable$physicsTick(body,handle,step);VehicleMetrics.end("eitan_wheel_physics",t);}
    @Override public float getLerpedAngle(float partial){return (float)(lastRotation+(rotation-lastRotation)*partial);}
}
