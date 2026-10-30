package pro.erez.interstice.ship;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** A Create seat anchored in a plot, with actor and passengers in world space.
 * The explicit transform also covers Create's own positionRider override;
 * vanilla tracking never receives virtual storage coordinates.
 */
public final class SkatSeatEntity extends SeatEntity {
    private UUID shipId;private BlockPos relative=BlockPos.ZERO;private int missing;
    public SkatSeatEntity(EntityType<?> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    private dev.ryanhcode.sable.sublevel.SubLevel ship(){var c=SubLevelContainer.getContainer(level());return c==null||shipId==null?null:c.getSubLevel(shipId);}
    private BlockPos anchor(dev.ryanhcode.sable.sublevel.SubLevel ship){return ship.getPlot().getCenterBlock().offset(relative);}
    public boolean controls(SkatHelmEntity helm){var body=ship();return body!=null&&helm.getLevel()==level()&&anchor(body).equals(helm.origin().offset(-2,4,-5));}
    private Vec3 world(dev.ryanhcode.sable.sublevel.SubLevel ship,double dx,double dy,double dz){var a=anchor(ship);var p=ship.logicalPose().transformPosition(new Vector3d(a.getX()+dx,a.getY()+dy,a.getZ()+dz),new Vector3d());return new Vec3(p.x,p.y,p.z);}
    @Override public void tick(){
        // Both actor and passenger remain in ordinary world space. Vanilla entity
        // tracking must never request chunks around the plot's 20-million origin.
        baseTick();setDeltaMovement(Vec3.ZERO);var body=ship();
        if(body==null||body.isRemoved()){if(!level().isClientSide&&++missing>100)discard();return;}missing=0;
        if(!level().isClientSide&&(!isVehicle()||!(level().getBlockState(anchor(body)).getBlock() instanceof SkatSeatBlock))){ejectPassengers();discard();return;}
        var p=world(body,.5,.30,.5);setPos(p.x,p.y,p.z);
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger){var body=ship();return body==null?position().add(0,1,0):world(body,.5,.1,1.4);}
    public static boolean mount(Level level,BlockPos pos,Entity rider){
        if(level.isClientSide||rider.isPassenger()||rider instanceof net.minecraft.world.entity.player.Player p&&!pro.erez.interstice.vehicle.VehicleAccess.require(p,pro.erez.interstice.vehicle.VehicleAccess.anchor(level,pos)))return false;
        var c=SubLevelContainer.getContainer(level);if(c==null||!c.inBounds(pos))return false;var plot=c.getPlot(new net.minecraft.world.level.ChunkPos(pos));if(plot==null)return false;var body=plot.getSubLevel();
        var rel=pos.subtract(plot.getCenterBlock());var point=body.logicalPose().transformPosition(new Vector3d(pos.getX()+.5,pos.getY()+.30,pos.getZ()+.5),new Vector3d());
        if(!level.getEntitiesOfClass(SkatSeatEntity.class,new net.minecraft.world.phys.AABB(point.x-1,point.y-1,point.z-1,point.x+1,point.y+1,point.z+1),s->body.getUniqueId().equals(s.shipId)&&rel.equals(s.relative)&&s.isVehicle()).isEmpty())return false;
        var seat=new SkatSeatEntity(SkatShips.SEAT_ACTOR.get(),level);seat.shipId=body.getUniqueId();seat.relative=rel;seat.setPos(point.x,point.y,point.z);
        if(!level.addFreshEntity(seat))return false;
        if(!rider.startRiding(seat,true)){seat.discard();return false;}return true;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(shipId!=null)tag.putUUID("Ship",shipId);tag.putInt("SeatX",relative.getX());tag.putInt("SeatY",relative.getY());tag.putInt("SeatZ",relative.getZ());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);shipId=tag.hasUUID("Ship")?tag.getUUID("Ship"):null;relative=new BlockPos(tag.getInt("SeatX"),tag.getInt("SeatY"),tag.getInt("SeatZ"));}
    @Override public void writeSpawnData(RegistryFriendlyByteBuf buf){super.writeSpawnData(buf);buf.writeUUID(shipId);buf.writeBlockPos(relative);}
    @Override public void readSpawnData(RegistryFriendlyByteBuf buf){super.readSpawnData(buf);shipId=buf.readUUID();relative=buf.readBlockPos();}
}
