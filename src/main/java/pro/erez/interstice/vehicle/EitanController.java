package pro.erez.interstice.vehicle;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;
import java.util.*;

/** One authoritative controller; no world scan and no tick per mesh component. */
public final class EitanController extends BlockEntity {
    public final VehicleSecurity security=new VehicleSecurity();private boolean accessRegistered;
    public final float[] progress=new float[15],previous=new float[15],target=new float[15];
    public volatile boolean engine,lights=true,brake=true;
    public volatile float steer,driveEffort,speed;
    public volatile int suspensionStrength=5;
    public volatile float clearanceLift;private float clearanceTarget;
    public volatile double suspensionAnchor=1.1;
    public int fuelTicks,occupiedMask,blockedMask;
    private record Boarding(int seat,long expires){}
    private final Map<UUID,Boarding> boarding=new HashMap<>();
    private final EitanWheelEntity[] wheels=new EitanWheelEntity[8];
    public EitanWheelEntity wheel(int slot){var w=wheels[slot];if(w!=null&&!w.isRemoved()&&w.getLevel()==level)return w;return wheels[slot]=level.getBlockEntity(origin().offset(EitanData.MOUNTS.get(slot).pos())) instanceof EitanWheelEntity e?e:null;}
    private final Map<Integer,EitanSeatEntity> seatActors=new HashMap<>();private int[] collisionCache;private boolean rideActive,seatDirty;
    private int forward,turn,ticks;private boolean brakePedal,boost,parking=true;
    private long lastInput=-1000,lastSpeedTick=-1;private Vector3d lastPosition;
    private int[] lastCollision=new int[EitanData.GROUPS.size()];
    private boolean lastLit,lightingDirty=true;private double mass;
    public double damping(){return EitanDynamics.damping(mass,suspensionStrength); }
    public EitanController(BlockPos p,BlockState s){super(EitanVehicles.CONTROL_ENTITY.get(),p,s);}
    public BlockPos origin(){return worldPosition.subtract(EitanData.CONTROL);}
    public SubLevel body(){return level==null?null:Sable.HELPER.getContaining(this);}
    public Vec3 world(double x,double y,double z){var o=origin();var b=body();var p=new Vector3d(o.getX()+x,o.getY()+y,o.getZ()+z);if(b!=null)b.logicalPose().transformPosition(p);return new Vec3(p.x,p.y,p.z);}
    public AABB worldBounds(AABB local){AABB out=null;for(double x:new double[]{local.minX,local.maxX})for(double y:new double[]{local.minY,local.maxY})for(double z:new double[]{local.minZ,local.maxZ}){var p=world(x,y,z);var a=new AABB(p,p);out=out==null?a:out.minmax(a);}return out;}
    public AABB bounds(){return worldBounds(new AABB(-2,-1,-5,2,4,6));}
    public boolean nearby(Player p){return p.level()==level&&p.distanceToSqr(world(0,1,0))<100;}
    public boolean pilot(Player p){return p.getVehicle() instanceof EitanSeatEntity s&&s.seatIndex()==0&&s.matches(this);}
    public void registerActor(EitanSeatEntity actor){var old=seatActors.get(actor.seatIndex());if(old==null||old.isRemoved()||!old.isVehicle()){seatActors.put(actor.seatIndex(),actor);seatDirty=true;}}
    private java.util.Collection<EitanSeatEntity> actors(){seatActors.values().removeIf(e->e.isRemoved());return seatActors.values();}
    public boolean seatOccupied(int index){var actor=seatActors.get(index);return actor!=null&&!actor.isRemoved()&&actor.isVehicle();}
    public boolean readySeat(int index,UUID rider){if(seatOccupied(index)||boarding.entrySet().stream().anyMatch(e->e.getValue().seat()==index&&!e.getKey().equals(rider)))return false;int channel=EitanData.CHANNELS.indexOf(EitanData.SEATS.get(index).id());return channel<0||progress[channel]<.001&&target[channel]<.001;}
    public String brakeStatus(){return progress[0]>.001||progress[1]>.001||target[0]>.001||target[1]>.001?"rear_open":!powered()?"engine_off":parking?"parked":brake?"braking":"ready";}
    public boolean powered(){return engine&&fuelTicks>0;}
    public void input(ServerPlayer p,int f,int t,boolean braking,boolean sprint){if(!pilot(p)||!VehicleAccess.require(p,this)||f< -1||f>1||t< -1||t>1)return;forward=f;turn=t;brakePedal=braking;boost=sprint;lastInput=level.getGameTime();if(f!=0&&fuelTicks>0){engine=true;parking=false;}}
    public int[] collisionStates(){if(collisionCache!=null)return collisionCache;var result=new int[EitanData.GROUPS.size()];int n=EitanData.STEPS;
        if(progress[0]>.0001||target[0]>.0001){int v=EitanDynamics.pose(progress[0],target[0],n);result[0]=v<=n?v:2*n+1+v-(n+1);}
        else if(progress[1]>.0001||target[1]>.0001){int v=EitanDynamics.pose(progress[1],target[1],n);result[0]=v<=n?(v==0?0:n+v):3*n+1+v-(n+1);}
        for(int i=1;i<result.length;i++)result[i]=EitanDynamics.pose(progress[i+1],target[i+1],n);
        return collisionCache=result;
    }
    private boolean occupiedSweep(int channel){int group=channel<2?0:channel-1;var area=worldBounds(EitanData.GROUPS.get(group).sweep()).deflate(.005);return !level.getEntitiesOfClass(LivingEntity.class,area,Entity::isAlive).isEmpty();}
    public boolean mechanism(Player player,int channel,boolean open){if(channel<0||channel>=target.length||!nearby(player)||!VehicleAccess.require(player,this))return false;
        if(channel<6&&speed>.3){message(player,"interstice.eitan.stop_first");return false;}
        if(open&&channel==0&&(progress[1]>.001||target[1]>.001)||open&&channel==1&&(progress[0]>.001||target[0]>.001)){message(player,"interstice.eitan.close_rear_first");return false;}
        if(channel>=6){var id=EitanData.CHANNELS.get(channel);var seat=EitanData.SEATS.stream().filter(s->s.id().equals(id)).findFirst().orElseThrow();if(seatOccupied(seat.index())){message(player,"interstice.eitan.seat_occupied");return false;}}
        if(occupiedSweep(channel)){message(player,"interstice.eitan.access_blocked");return false;}
        target[channel]=open?1:0;collisionCache=null;setChanged();sync();return true;
    }
    public void action(ServerPlayer p,int action,int index){if(!nearby(p)||action!=3&&!VehicleAccess.require(p,this))return;boolean crew=p.getVehicle() instanceof EitanSeatEntity s&&s.matches(this)&&s.seatIndex()<3;
        if(action==0){if(!crew)return;engine=!engine;if(fuelTicks<=0)message(p,"interstice.eitan.no_fuel");}
        else if(action==1){if(!crew)return;lights=!lights;}
        else if(action==2){if(index>=0&&index<15)mechanism(p,index,target[index]<.5);return;}
        else if(action==3){if(p.getVehicle() instanceof EitanSeatEntity s&&s.matches(this))p.stopRiding();return;}
        else if(action==4){if(!crew)return;parking=!parking;forward=0;lastInput=level.getGameTime();}
        else return;setChanged();sync();
    }
    public InteractionResult interact(Player p,Vec3 point){if(!(p instanceof ServerPlayer player)||!nearby(p)||!VehicleAccess.require(p,this))return InteractionResult.FAIL;
        com.google.gson.JsonObject chosen=null;double monitorDistance=Double.POSITIVE_INFINITY;
        for(var value:EitanData.metadata("monitors")){var m=value.getAsJsonObject();var a=EitanData.vector(m.getAsJsonArray("min"));var b=EitanData.vector(m.getAsJsonArray("max"));if(point.x<a[0]-.03||point.x>b[0]+.03||point.y<a[1]-.03||point.y>b[1]+.03||point.z<a[2]-.14||point.z>b[2]+.14)continue;double d=Math.pow(point.x-(a[0]+b[0])*.5,2)+Math.pow(point.y-(a[1]+b[1])*.5,2);if(d<monitorDistance){monitorDistance=d;chosen=m;}}
        if(chosen!=null){String name=chosen.get("name").getAsString();EitanNetwork.open(player,this,name.startsWith("driver display")?Integer.parseInt(name.substring(name.length()-1)):-1);return InteractionResult.CONSUME;}
        for(var seat:EitanData.SEATS){double d=point.distanceToSqr(new Vec3(seat.x(),seat.y()+.15,seat.z()));if(d<.42*.42){if(p.isShiftKeyDown()&&seat.index()>=3){mechanism(p,EitanData.CHANNELS.indexOf(seat.id()),target[EitanData.CHANNELS.indexOf(seat.id())]<.5);return InteractionResult.CONSUME;}int channel=EitanData.CHANNELS.indexOf(seat.id());if(channel>=0&&(progress[channel]>.03||target[channel]>.03)){if(mechanism(p,channel,false)){boarding.put(p.getUUID(),new Boarding(seat.index(),level.getGameTime()+80));message(p,"interstice.eitan.seat_unfolding");}sync();return InteractionResult.CONSUME;}if(EitanSeatEntity.mount(this,seat.index(),p)){if(seat.index()==0&&fuelTicks>0)engine=true;sync();}return InteractionResult.CONSUME;}}
        if(point.z>3.1&&point.y>.8&&point.y<2.6&&Math.abs(point.x)<.85){int channel=p.isShiftKeyDown()?0:1;mechanism(p,channel,target[channel]<.5);return InteractionResult.CONSUME;}
        if(point.y>2.4){int nearest=-1;double distance=.85*.85;var gs=EitanData.metadata("groups");for(var v:gs){var g=v.getAsJsonObject();int channel=EitanData.CHANNELS.indexOf(g.get("id").getAsString());if(channel<2||channel>5)continue;var a=EitanData.vector(g.getAsJsonArray("pivot"));double d=point.distanceToSqr(new Vec3(a[0],a[2],a[1]));if(d<distance){distance=d;nearest=channel;}}if(nearest>=0){mechanism(p,nearest,target[nearest]<.5);return InteractionResult.CONSUME;}}
        EitanNetwork.open(player,this,-1);return InteractionResult.CONSUME;
    }
    public boolean fuelClick(Player p,ItemStack stack,Vec3 at){var point=at.subtract(origin().getX(),origin().getY(),origin().getZ());if(point.z> -1.1||point.z< -4.1||point.y<.5||point.x<.15)return false;int burn=stack.getBurnTime(null);if(burn<=0)return false;if(level.isClientSide)return true;if(!nearby(p)||!VehicleAccess.require(p,this))return false;int count=p.isShiftKeyDown()?stack.getCount():1;count=Math.min(count,Math.max(0,(240000-fuelTicks)/burn));if(count<=0)return true;fuelTicks+=count*burn;if(!p.isCreative())stack.shrink(count);setChanged();sync();return true;}
    private static void message(Player p,String key){p.displayClientMessage(net.minecraft.network.chat.Component.translatable(key),true);}
    public void tick(){long perf=level.isClientSide?0:VehicleMetrics.begin();if(!accessRegistered&&body()!=null&&!level.isClientSide){VehicleAccess.register(this);accessRegistered=true;}ticks++;System.arraycopy(progress,0,previous,0,progress.length);for(int i=0;i<progress.length;i++){
            boolean moving=Math.abs(progress[i]-target[i])>.001;
            if(moving&&level.isClientSide&&(blockedMask&(1<<i))!=0)continue;
            if(moving)collisionCache=null;
            if(moving&&!level.isClientSide&&occupiedSweep(i)){blockedMask|=1<<i;continue;}
            blockedMask&=~(1<<i);progress[i]=EitanDynamics.approach(progress[i],target[i],i<2?.025f:.04f);
        }
        var body=body();if(body!=null){var point=world(0,1,0);long now=level.getGameTime();rideActive=lastPosition==null||lastPosition.distanceSquared(point.x,point.y,point.z)>1e-6;if(lastPosition!=null&&now>lastSpeedTick){double raw=Math.hypot(point.x-lastPosition.x,point.z-lastPosition.z)*20/(now-lastSpeedTick);if(raw<60)speed=(float)(speed*.65+raw*.35);}lastPosition=new Vector3d(point.x,point.y,point.z);lastSpeedTick=now;}
        if(level.isClientSide)return;
        if(body instanceof ServerSubLevel server&&(mass<=0||ticks%20==0)){mass=server.getMassTracker().getMass();if(Double.isFinite(mass)&&mass>0){suspensionStrength=EitanDynamics.strength(mass);suspensionAnchor=EitanDynamics.anchor(mass,suspensionStrength);}}
        if(ticks%4==0&&(rideActive||ticks<100||clearanceLift!=clearanceTarget))clearanceTarget=EitanClearance.target(this);
        clearanceLift=EitanDynamics.approach(clearanceLift,clearanceTarget,clearanceTarget>clearanceLift?.02f:.006f);
        suspensionAnchor=EitanDynamics.anchor(mass,suspensionStrength)-clearanceLift;
        for(var it=boarding.entrySet().iterator();it.hasNext();){var request=it.next();var player=level.getPlayerByUUID(request.getKey());var seat=EitanData.SEATS.get(request.getValue().seat());if(player==null||player.isPassenger()||level.getGameTime()>request.getValue().expires()||player.distanceToSqr(world(seat.x(),seat.y(),seat.z()))>16){it.remove();continue;}if(readySeat(seat.index(),player.getUUID())){EitanSeatEntity.mount(this,seat.index(),player);it.remove();}}
        int oldMask=occupiedMask;occupiedMask=0;boolean driver=false;for(var actor:actors())if(actor.isVehicle()){occupiedMask|=1<<actor.seatIndex();driver|=actor.seatIndex()==0&&actor.getFirstPassenger() instanceof Player p&&security.allowed(p);}
        if(oldMask!=occupiedMask)seatDirty=true;
        boolean fresh=driver&&level.getGameTime()-lastInput<=15;
        if(!fresh){forward=0;turn=0;}
        brake=parking||!fresh||brakePedal||!powered()||progress[0]>.001||progress[1]>.001||target[0]>.001||target[1]>.001;
        steer=EitanDynamics.approach(steer,fresh?turn:0,.12f);
        double desired=forward>0?(boost?EitanDynamics.BOOST:EitanDynamics.CRUISE)*forward:EitanDynamics.REVERSE*forward;
        float effort=brake?0:(float)(desired*suspensionStrength*(1.5/1.75));driveEffort=EitanDynamics.approach(driveEffort,effort,Math.max(.15f,suspensionStrength*.25f));
        if(powered()){fuelTicks--;if(fuelTicks==0)engine=false;}
        var indices=collisionStates();boolean lit=powered()&&lights;
        if(lightingDirty||lit!=lastLit||!java.util.Arrays.equals(indices,lastCollision))for(var cell:EitanData.CELLS){boolean changed=false;for(var g:cell.groups())if(indices[g.index()]!=lastCollision[g.index()]){changed=true;break;}boolean lightChange=cell.light()>0&&(lightingDirty||lit!=lastLit);if(!changed&&!lightChange)continue;VehicleMetrics.hit("eitan_state_read");var p=origin().offset(cell.pos());var state=level.getBlockState(p);if(changed&&state.hasProperty(EitanBlock.MOTION))state=state.cycle(EitanBlock.MOTION);if(cell.light()>0&&(lightingDirty||lit!=lastLit)&&state.hasProperty(EitanBlock.LIT))state=state.setValue(EitanBlock.LIT,lit);if(state!=level.getBlockState(p))level.setBlock(p,state,3);}
        lastCollision=indices;lastLit=lit;lightingDirty=false;
        boolean moving=rideActive||Math.abs(driveEffort)>.01;for(int i=0;i<15&&!moving;i++)moving=Math.abs(progress[i]-target[i])>.001;
        if(ticks%20==0&&powered())setChanged();
        if(seatDirty||moving&&ticks%5==0||powered()&&ticks%20==0){sync();seatDirty=false;}
        VehicleMetrics.end("eitan_controller",perf);
    }
    public void sync(){VehicleMetrics.hit("eitan_sync");if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);tag.put("VehicleAccess",security.save());tag.putInt("EitanSchema",1);tag.putInt("Fuel",fuelTicks);tag.putBoolean("Engine",engine);tag.putBoolean("Lights",lights);tag.putFloat("Steer",steer);tag.putFloat("Speed",speed);tag.putFloat("Drive",driveEffort);tag.putBoolean("Brake",brake);tag.putBoolean("Parking",parking);tag.putInt("Occupants",occupiedMask);tag.putInt("Blocked",blockedMask);tag.putInt("Spring",suspensionStrength);tag.putDouble("Anchor",suspensionAnchor);tag.putFloat("ClearanceLift",clearanceLift);for(int i=0;i<15;i++){tag.putFloat("Motion"+i,progress[i]);tag.putFloat("Target"+i,target[i]);}}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){super.loadAdditional(tag,provider);security.load(tag.getCompound("VehicleAccess"));collisionCache=null;fuelTicks=Math.max(0,Math.min(240000,tag.getInt("Fuel")));engine=tag.getBoolean("Engine");lights=!tag.contains("Lights")||tag.getBoolean("Lights");steer=tag.getFloat("Steer");speed=tag.getFloat("Speed");driveEffort=tag.getFloat("Drive");brake=tag.getBoolean("Brake");parking=!tag.contains("Parking")||tag.getBoolean("Parking");occupiedMask=tag.getInt("Occupants");blockedMask=tag.getInt("Blocked");suspensionStrength=Math.max(5,Math.min(180,tag.getInt("Spring")));suspensionAnchor=tag.contains("Anchor")?tag.getDouble("Anchor"):1.1;for(int i=0;i<15;i++){progress[i]=previous[i]=net.minecraft.util.Mth.clamp(tag.getFloat("Motion"+i),0,1);target[i]=net.minecraft.util.Mth.clamp(tag.getFloat("Target"+i),0,1);}clearanceTarget=clearanceLift=net.minecraft.util.Mth.clamp(tag.getFloat("ClearanceLift"),0,.60f);lastInput=-1000;lightingDirty=true;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider){var tag=saveWithoutMetadata(provider);tag.put("VehicleAccess",security.publicTag(level==null?0:level.getGameTime()));return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
