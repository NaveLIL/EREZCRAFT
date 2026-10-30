package pro.erez.interstice.ship;
import java.util.UUID;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server validates the actual seated pilot. Inputs expire; throttle is never saved. */
public final class SkatHelmEntity extends BlockEntity {
    public final pro.erez.interstice.vehicle.VehicleSecurity security=new pro.erez.interstice.vehicle.VehicleSecurity();private boolean accessRegistered,stopped;private int layoutVersion;
    private UUID pilot;private long inputAt;private float forward,turn;
    private final SkatSpeedometer speedometer=new SkatSpeedometer();
    public float speedMetersPerSecond(){return speedometer.value();}
    private float door,doorPrev,hatch,hatchPrev;
    private volatile int lighting=SkatLighting.ALL;private long lightingAt=Long.MIN_VALUE;
    public int lighting(){return lighting;}
    public SkatHelmEntity(BlockPos p,BlockState s){super(SkatShips.HELM_ENTITY.get(),p,s);}
    public BlockPos origin(){return worldPosition.subtract(SkatData.cell(getBlockState().getValue(SkatData.CELL)).pos());}
    public UUID pilot(){return pilot;}
    private boolean lightingReach(ServerPlayer p){
        if(p.level()!=level||isRemoved())return false;
        var at=new org.joml.Vector3d(worldPosition.getX()+.5,worldPosition.getY()+1.2,worldPosition.getZ()+.5);
        var container=SubLevelContainer.getContainer(level);
        if(container!=null&&container.inBounds(worldPosition)){var plot=container.getPlot(new net.minecraft.world.level.ChunkPos(worldPosition));if(plot==null)return false;plot.getSubLevel().logicalPose().transformPosition(at);}
        return p.distanceToSqr(at.x,at.y,at.z)<=64&&pro.erez.interstice.vehicle.VehicleAccess.require(p,this);
    }
    public void openLighting(ServerPlayer p){if(lightingReach(p))PacketDistributor.sendToPlayer(p,new SkatNetwork.LightingPanel(worldPosition,lighting,true));}
    public boolean setLighting(ServerPlayer p,int channel,boolean enabled){
        if(!(level instanceof ServerLevel w)||!lightingReach(p)||(channel!=1&&channel!=2&&channel!=4)||lightingAt!=Long.MIN_VALUE&&w.getGameTime()-lightingAt<4)return false;
        int next=enabled?lighting|channel:lighting&~channel;if(next==lighting)return true;
        lighting=next;lightingAt=w.getGameTime();setChanged();SkatLighting.apply(w,origin(),lighting);
        w.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        for(var crew:w.players())if(lightingReach(crew))PacketDistributor.sendToPlayer(crew,new SkatNetwork.LightingPanel(worldPosition,lighting,false));return true;
    }
    @Override protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.put("VehicleAccess",security.save());tag.putInt("Lighting",lighting);tag.putInt("LayoutVersion",layoutVersion);}
    @Override protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);security.load(tag.getCompound("VehicleAccess"));layoutVersion=tag.getInt("LayoutVersion");lighting=tag.contains("Lighting")?tag.getInt("Lighting")&7:SkatLighting.ALL;}
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=saveCustomOnly(lookup);tag.put("VehicleAccess",security.publicTag(level==null?0:level.getGameTime()));return tag;}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    public float door(float dt){return net.minecraft.util.Mth.lerp(dt,doorPrev,door);}
    public float hatch(float dt){return net.minecraft.util.Mth.lerp(dt,hatchPrev,hatch);}
    public void animate(){doorPrev=door;hatchPrev=hatch;door=net.minecraft.util.Mth.approach(door,SkatOpeningBlock.isOpen(level,origin(),"doors")?1:0,1F/12);hatch=net.minecraft.util.Mth.approach(hatch,SkatOpeningBlock.isOpen(level,origin(),"hatch")?1:0,1F/12);}
    public void interact(ServerPlayer player){
        if(!(level instanceof ServerLevel w)||!pro.erez.interstice.vehicle.VehicleAccess.require(player,this)||!(player.getVehicle() instanceof SkatSeatEntity seat)||!seat.controls(this))return;
        if(pilot!=null&&!pilot.equals(player.getUUID())){player.displayClientMessage(Component.translatable("message.interstice.skat_occupied"),true);return;}
        pilot=player.getUUID();inputAt=w.getGameTime();
        PacketDistributor.sendToPlayer(player,new SkatNetwork.Status(worldPosition,true,0,0,0,0));
    }
    public void input(ServerPlayer p,int f,int t){
        if(!p.getUUID().equals(pilot)||!pro.erez.interstice.vehicle.VehicleAccess.require(p,this)||p.level()!=level||!(p.getVehicle() instanceof SkatSeatEntity seatActor)||!seatActor.controls(this)||Math.abs(f)>1||Math.abs(t)>1)return;
        var container=SubLevelContainer.getContainer(level);if(!container.inBounds(worldPosition))return;
        var ship=container.getPlot(new net.minecraft.world.level.ChunkPos(worldPosition)).getSubLevel();
        var seat=ship.logicalPose().transformPosition(new org.joml.Vector3d(origin().getX()-1.5,origin().getY()+4.6,origin().getZ()-4.5),new org.joml.Vector3d());
        if(p.distanceToSqr(seat.x,seat.y,seat.z)>16)return;
        forward=f;turn=t;inputAt=level.getGameTime();
    }
    private void upgradeDeckCargo(){if(layoutVersion>=4||level.isClientSide)return;for(var cargo:SkatData.CARGO){if(!cargo.deck())continue;var p=origin().offset(cargo.anchor());var state=level.getBlockState(p);if(state.getBlock()==SkatShips.PART.get()&&state.hasProperty(SkatData.CELL)&&level.getBlockEntity(p)==null){var c=SkatData.cell(state.getValue(SkatData.CELL));if(c.role().equals("cargo"))level.setBlock(p,SkatShips.state(c),3);}}layoutVersion=4;setChanged();}
    private void refuel(SkatEngineEntity engine){var cell=SkatData.CARGO.getFirst();if(level.getBlockEntity(origin().offset(cell.anchor())) instanceof SkatCargoEntity bunker)bunker.feed(engine);}
    public void process(){
        if(!(level instanceof ServerLevel w))return;if(!accessRegistered&&dev.ryanhcode.sable.Sable.HELPER.getContaining(this)!=null){pro.erez.interstice.vehicle.VehicleAccess.register(this);accessRegistered=true;upgradeDeckCargo();}
        if(pilot==null&&stopped)return;long perf=pro.erez.interstice.vehicle.VehicleMetrics.begin();
        var p=pilot==null?null:w.getServer().getPlayerList().getPlayer(pilot);
        boolean seated=p!=null&&p.level()==w&&p.getVehicle() instanceof SkatSeatEntity seat&&seat.controls(this);
        boolean valid=seated&&security.allowed(p)&&w.getGameTime()-inputAt<=12;
        // A lost input stops propulsion, but the actual seated pilot must be able
        // to resume after network/spawn latency without remounting the chair.
        if(!valid){forward=turn=0;if(!seated){if(p!=null)PacketDistributor.sendToPlayer(p,new SkatNetwork.Status(worldPosition,false,0,0,0,0));pilot=null;}}
        var origin=origin();int left=0,right=0;
        for(int x:new int[]{-3,3})if(w.getBlockEntity(origin.offset(x,6,9)) instanceof SkatEngineEntity engine){
            float rpm=valid?Math.clamp(forward*96+(x<0?1:-1)*turn*SkatPropulsion.turningRpm(speedometer.value()),-128,128):0;if(rpm!=0&&engine.fuelTicks()<80)refuel(engine);engine.throttle(rpm);
            if(x<0)left=engine.fuelTicks();else right=engine.fuelTicks();
        }
        if(seated){
            var container=SubLevelContainer.getContainer(w);var plot=container.getPlot(new net.minecraft.world.level.ChunkPos(worldPosition));
            if(plot!=null){var body=plot.getSubLevel();var at=body.logicalPose().transformPosition(new org.joml.Vector3d(origin.getX()+.5,origin.getY()+2.5,origin.getZ()+.5),new org.joml.Vector3d());speedometer.sample(w.getGameTime(),body.getUniqueId(),at.x,at.z);}
            else speedometer.reset();
            if(w.getGameTime()%10==0){
                PacketDistributor.sendToPlayer(p,new SkatNetwork.Status(worldPosition,true,(int)(forward*96),(int)(turn*SkatPropulsion.turningRpm(speedometer.value())),left,right));
                if(p.connection.hasChannel(SkatNetwork.Speed.TYPE))PacketDistributor.sendToPlayer(p,new SkatNetwork.Speed(worldPosition,speedometer.value()));
            }
        }else speedometer.reset();stopped=pilot==null;pro.erez.interstice.vehicle.VehicleMetrics.end("skat_helm",perf);
    }
}
