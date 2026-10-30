package pro.erez.interstice.vehicle;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;
import java.util.UUID;

/** Real passenger entities always remain in ordinary world coordinates. */
public final class EitanSeatEntity extends SeatEntity {
    private UUID bodyId;private BlockPos relativeControl=BlockPos.ZERO;private int seat,missing;private float lastHeading;private boolean hasHeading;
    public EitanSeatEntity(EntityType<?> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public int seatIndex(){return seat;}
    private SubLevel body(){var c=SubLevelContainer.getContainer(level());return c==null||bodyId==null?null:c.getSubLevel(bodyId);}
    public EitanController controller(){var b=body();if(b==null)return null;var p=b.getPlot().getCenterBlock().offset(relativeControl);return level().getBlockEntity(p) instanceof EitanController c?c:null;}
    public boolean matches(EitanController c){var b=c.body();return b!=null&&b.getUniqueId().equals(bodyId)&&c.getBlockPos().subtract(b.getPlot().getCenterBlock()).equals(relativeControl);}
    private Vec3 feet(EitanController c){var s=EitanData.SEATS.get(seat);return c.world(s.x(),s.y()-.62,s.z()+(seat==0?.16:0));}
    private float heading(){var b=body();if(b==null)return 180;var f=b.logicalPose().transformNormal(new Vector3d(0,0,-1));return (float)Math.toDegrees(Math.atan2(-f.x,f.z));}
    public float initialYaw(){var s=EitanData.SEATS.get(seat);return heading()+(s.inward()?(s.x()<0?90:-90):0);}
    @Override protected void positionRider(Entity passenger,Entity.MoveFunction move){if(hasPassenger(passenger)){var p=getPassengerRidingPosition(passenger);move.accept(passenger,p.x,p.y,p.z);}}
    @Override public void tick(){baseTick();setDeltaMovement(Vec3.ZERO);var c=controller();if(c==null){if(!level().isClientSide&&++missing>200)discard();return;}missing=0;if(!level().isClientSide&&isVehicle())c.registerActor(this);var frame=c.body();var tracking=(dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)(Object)this;if(level().isClientSide&&frame!=null){tracking.sable$setTrackingSubLevel(frame);tracking.sable$setLastTrackingSubLevelID(frame.getUniqueId());}float yaw=heading();if(hasHeading){float delta=net.minecraft.util.Mth.wrapDegrees(yaw-lastHeading);for(var rider:getPassengers()){rider.setYRot(rider.getYRot()+delta);rider.yRotO+=delta;rider.setYHeadRot(rider.getYHeadRot()+delta);if(rider instanceof LivingEntity l){l.yBodyRot+=delta;l.yBodyRotO+=delta;}}}lastHeading=yaw;hasHeading=true;var p=feet(c);setPos(p.x,p.y,p.z);if(!level().isClientSide&&!isVehicle())discard();}
    @Override public Vec3 getPassengerRidingPosition(Entity passenger){var c=controller();return c==null?position():feet(c);}
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger){var c=controller();if(c==null)return position().add(0,1,0);var s=EitanData.SEATS.get(seat);double z=Math.max(-.5,Math.min(2.7,s.z()));passenger.setPose(Pose.CROUCHING);for(double dz:new double[]{0,.7,-.7,1.4,-1.4}){var p=c.world(0,.94,z+dz);var box=new AABB(p.x-.3,p.y,p.z-.3,p.x+.3,p.y+1.5,p.z+.3);if(level().noCollision(passenger,box))return p;}return c.world(0,.94,1.5);}
    public static boolean mount(EitanController c,int index,Entity rider){if(c.getLevel().isClientSide||index<0||index>=12||rider.isPassenger()||!c.readySeat(index,rider.getUUID())||rider instanceof net.minecraft.world.entity.player.Player p&&!VehicleAccess.require(p,c))return false;var b=c.body();if(b==null)return false;var s=new EitanSeatEntity(EitanVehicles.SEAT_ENTITY.get(),c.getLevel());s.bodyId=b.getUniqueId();s.relativeControl=c.getBlockPos().subtract(b.getPlot().getCenterBlock());s.seat=index;var p=s.feet(c);s.setPos(p.x,p.y,p.z);if(!c.getLevel().addFreshEntity(s)||!rider.startRiding(s,true)){s.discard();return false;}c.registerActor(s);rider.setYRot(s.initialYaw());rider.setYHeadRot(s.initialYaw());rider.setXRot(index==0?28:0);return true;}
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(bodyId!=null)tag.putUUID("EitanBody",bodyId);tag.putInt("EitanSeat",seat);tag.putInt("ControlX",relativeControl.getX());tag.putInt("ControlY",relativeControl.getY());tag.putInt("ControlZ",relativeControl.getZ());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);bodyId=tag.hasUUID("EitanBody")?tag.getUUID("EitanBody"):null;seat=Math.max(0,Math.min(11,tag.getInt("EitanSeat")));relativeControl=new BlockPos(tag.getInt("ControlX"),tag.getInt("ControlY"),tag.getInt("ControlZ"));}
    @Override public void writeSpawnData(RegistryFriendlyByteBuf b){super.writeSpawnData(b);b.writeUUID(bodyId);b.writeBlockPos(relativeControl);b.writeByte(seat);}
    @Override public void readSpawnData(RegistryFriendlyByteBuf b){super.readSpawnData(b);bodyId=b.readUUID();relativeControl=b.readBlockPos();seat=Math.min(11,b.readUnsignedByte());}
}
