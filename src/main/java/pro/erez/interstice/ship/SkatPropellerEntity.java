package pro.erez.interstice.ship;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.andesite.AndesitePropellerBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
public final class SkatPropellerEntity extends AndesitePropellerBlockEntity {
    private float loadedKineticSpeed;
    private long wetTick=Long.MIN_VALUE;
    private boolean wettedHull;
    private double throttleRamp;
    private int thrustSign;
    private long lastForceTick=Long.MIN_VALUE;
    private double lastAppliedImpulse;
    public float loadedKineticSpeed(){return loadedKineticSpeed;}
    public SkatPropellerEntity(BlockPos p,BlockState s){super(SkatShips.PROPELLER_ENTITY.get(),p,s);}
    @Override protected void read(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider lookup,boolean packet){
        super.read(tag,lookup,packet);
        if(!packet){if(Math.abs(tag.getFloat("Speed"))>Math.abs(loadedKineticSpeed))loadedKineticSpeed=tag.getFloat("Speed");clearKineticInformation();updateSpeed=true;preventSpeedUpdate=0;}
    }
    @Override public float getRadius(){return 2.18F;}
    @Override public float getOffset(){return .45F;}
    // Native Aeronautics remains the only source of propeller impulses.
    @Override public double getConfigThrust(){return super.getConfigThrust()*2.18*2.18*SkatPropulsion.THRUST_MULTIPLIER;}
    @Override public double getConfigAirflow(){return Math.max(super.getConfigAirflow(),SkatPropulsion.MIN_AIRFLOW_PER_RPM);}
    @Override public double getScaledThrust(){
        double force=super.getScaledThrust();
        if(!Double.isFinite(force))return 0;
        if(level==null||level.isClientSide||force==0)return force;
        var container=SubLevelContainer.getContainer(level);
        if(container==null||!container.inBounds(worldPosition))return 0;
        var plot=container.getPlot(new ChunkPos(worldPosition));
        if(plot==null||!(plot.getSubLevel() instanceof ServerSubLevel ship))return 0;
        var handle=RigidBodyHandle.of(ship);if(!handle.isValid())return 0;
        var facing=getBlockDirection().getNormal();
        var axis=ship.logicalPose().transformNormal(new Vector3d(facing.getX(),facing.getY(),facing.getZ()));
        // Native scaled force carries the sign, including reversed propellers.
        double along=handle.getLinearVelocity().dot(axis)*Math.signum(force);
        return force*SkatPropulsion.governor(along,getSpeed()<0);
    }
    @Override public void applyForces(ServerSubLevel ship,Vec3 direction,double dt){
        double force=getScaledThrust();if(!Double.isFinite(force)||!(dt>0))return;
        int sign=(int)Math.signum(getSpeed());
        long now=level.getGameTime();
        if(sign!=thrustSign||lastForceTick==Long.MIN_VALUE||now-lastForceTick>2)throttleRamp=0;
        thrustSign=sign;lastForceTick=now;
        throttleRamp=sign==0?Math.max(0,throttleRamp-dt*2):Math.min(1,throttleRamp+dt/5);
        force*=throttleRamp;
        if(force==0){lastAppliedImpulse=0;return;}
        var handle=RigidBodyHandle.of(ship);if(!handle.isValid())return;
        var axis=ship.logicalPose().transformNormal(new Vector3d(direction.x,direction.y,direction.z));
        double along=handle.getLinearVelocity().dot(axis)*Math.signum(force);
        double magnitude=Math.min(Math.abs(force*dt),SkatPropulsion.maximumImpulse(along,ship.getMassTracker().getMass(),getSpeed(),sign<0));
        lastAppliedImpulse=magnitude;
        var point=new Vector3d(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5);
        if(wetTick!=level.getGameTime()){
            wetTick=level.getGameTime();
            var origin=worldPosition.subtract(SkatData.cell(SkatPropellerBlock.index(getBlockState(),"propeller")).pos());
            var keel=ship.logicalPose().transformPosition(new Vector3d(origin.getX()+.5,origin.getY()+.05,origin.getZ()+.5));
            wettedHull=false;
            // Thin authored keels can ride above the static fluid block at a
            // wave crest. Detect water up to 2m below, stopping at solid ground.
            for(int i=0;i<=4;i++){
                var p=BlockPos.containing(keel.x,keel.y-i*.5,keel.z);
                if(!level.getFluidState(p).isEmpty()){wettedHull=true;break;}
                if(!level.getBlockState(p).getCollisionShape(level,p).isEmpty())break;
            }
        }
        // Game approximation of hydrodynamic trim: the effective thrust line
        // follows the wetted keel, countering underwater drag's pitching moment.
        // Port/starboard lever arms and yaw remain physical. Dry propellers
        // retain their real hub point; no world buoyancy/drag setting changes.
        if(wettedHull)point.y=worldPosition.getY()-SkatData.cell(SkatPropellerBlock.index(getBlockState(),"propeller")).pos().getY()+.5;
        var impulse=new Vector3d(direction.x,direction.y,direction.z).mul(Math.copySign(magnitude,force));
        ship.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get()).applyAndRecordPointForce(point,impulse);
    }
}
