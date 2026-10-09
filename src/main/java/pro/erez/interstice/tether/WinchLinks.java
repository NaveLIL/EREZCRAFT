package pro.erez.interstice.tether;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import pro.erez.interstice.Interstice;

/** Server-owned local elastic links. No teleports, forced chunks, flight abilities or floating-counter edits. */
@EventBusSubscriber(modid=Interstice.ID)
public final class WinchLinks {
    public static final String KEY="interstice_winch_link";
    public static final double MAX_ACCELERATION=.12;
    public static final int MAX_ROPE_LENGTH=48;
    public static final double DESCENT_SPEED=.10;
    public static final double MAX_DESCENT_BRAKE=.28;
    public record Link(ResourceLocation dimension,BlockPos anchor,UUID anchorId,int length){}
    private WinchLinks(){}
    public static Link link(Player player){
        var tag=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY);String dimension=tag.getString("Dimension");var id=dimension.isBlank()?null:ResourceLocation.tryParse(dimension);int length=tag.getInt("Length");
        return id==null||!tag.hasUUID("AnchorId")||length<8||length>MAX_ROPE_LENGTH?null:new Link(id,BlockPos.of(tag.getLong("Anchor")),tag.getUUID("AnchorId"),length);
    }
    private static void save(Player player,Link link){var persistent=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if(link==null)persistent.remove(KEY);else{var tag=new CompoundTag();tag.putString("Dimension",link.dimension.toString());tag.putLong("Anchor",link.anchor.asLong());tag.putUUID("AnchorId",link.anchorId);tag.putInt("Length",link.length);persistent.put(KEY,tag);}
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG,persistent);
    }
    public static WinchBlockEntity loaded(ServerLevel level,BlockPos pos){
        LevelChunk chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)return null;
        BlockEntity entity=chunk.getBlockEntity(pos);return entity instanceof WinchBlockEntity winch&&chunk.getBlockState(pos).is(RiftTethers.WINCH.get())?winch:null;
    }
    public static Vec3 source(WinchBlockEntity node){return source(node.getBlockPos(),node.getBlockState().getValue(WinchBlock.FACING));}
    public static Vec3 source(BlockPos pos,Direction facing){return new Vec3(pos.getX()+.5+facing.getStepX()*.55,pos.getY()+.65,pos.getZ()+.5+facing.getStepZ()*.55);}
    public static Vec3 endpoint(Player player){return player.position().add(0,player.getBbHeight()*.55,0);}
    public static boolean clear(ServerLevel level,Vec3 start,Vec3 end,Player player){
        double length=start.distanceTo(end);if(length>49)return false;int steps=Math.max(1,(int)Math.ceil(length*2));
        for(int i=0;i<=steps;i++){var p=BlockPos.containing(start.lerp(end,i/(double)steps));if(level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)==null)return false;}
        return level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS;
    }
    public static boolean attach(ServerPlayer player,ServerLevel level,BlockPos pos,int length){
        if(length<8||length>32||player.serverLevel()!=level||!player.isAlive()||player.isSpectator()||player.isPassenger()||player.isFallFlying()
                ||!player.canInteractWithBlock(pos,0)||!level.mayInteract(player,pos))return false;
        var node=loaded(level,pos);if(node==null||!node.hasRoom(player.getUUID())||!clear(level,source(node),endpoint(player),player))return false;
        var old=link(player);if(old!=null&&old.anchor.equals(pos)&&old.dimension.equals(level.dimension().location())&&old.anchorId.equals(node.anchorId())&&old.length==length&&node.hook(player.getUUID())!=null)return false;
        detach(player);if(!node.add(player,length))return false;save(player,new Link(level.dimension().location(),pos.immutable(),node.anchorId(),length));sync(player,node,true);
        player.displayClientMessage(Component.translatable("message.interstice.winch.attached",length),true);return true;
    }
    public static void detach(ServerPlayer player){var old=link(player);if(old==null){save(player,null);return;}if(old!=null){var level=player.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,old.dimension));if(level!=null){var node=loaded(level,old.anchor);if(node!=null&&node.anchorId().equals(old.anchorId))node.remove(player.getUUID());}}
        save(player,null);TetherNetworking.broadcast(player,null);}
    public static boolean matches(ServerPlayer player,WinchBlockEntity node){var link=link(player);return link!=null&&link.anchor.equals(node.getBlockPos())&&link.anchorId.equals(node.anchorId())&&node.getLevel()!=null&&link.dimension.equals(node.getLevel().dimension().location());}
    public static Vec3 acceleration(Vec3 source,Vec3 endpoint,Vec3 velocity,int length){
        var outward=endpoint.subtract(source);double distance=outward.length();if(distance<=length+.05||distance<.001)return Vec3.ZERO;
        var direction=outward.scale(1/distance);double force=Math.min(MAX_ACCELERATION,Math.max(0,(distance-length)*.04+Math.max(0,velocity.dot(direction))*.35));return direction.scale(-force);
    }
    public static boolean requestedDescent(ServerPlayer player){
        return player.isShiftKeyDown()&&(player.getMainHandItem().is(RiftTethers.TETHER_SPOOL.get())||player.getOffhandItem().is(RiftTethers.TETHER_SPOOL.get()));
    }
    private static void controlledState(ServerPlayer player,WinchBlockEntity node,double length,boolean descending){
        node.control(player.getUUID(),length,descending);
        var old=link(player);if(old!=null&&old.length!=(int)Math.ceil(length))save(player,new Link(old.dimension,old.anchor,old.anchorId,(int)Math.ceil(length)));
    }
    /** Returns the saved safety count; -1 means this hook was removed. */
    static int apply(WinchBlockEntity node,ServerPlayer player,WinchBlockEntity.Hook hook){
        if(!(node.getLevel() instanceof ServerLevel level))return -1;
        if(!matches(player,node)){node.remove(player.getUUID());return -1;}
        if(loaded(level,node.getBlockPos())!=node){detach(player);return -1;}
        if(player.serverLevel()!=level||!player.isAlive()||player.isSpectator()||player.isPassenger()||player.isFallFlying()){detach(player);return -1;}
        var end=endpoint(player);var start=source(node);double distance=start.distanceTo(end);
        if(distance>hook.paidLength()+16||!clear(level,start,end,player)){detach(player);player.displayClientMessage(Component.translatable("message.interstice.winch.broken"),true);return -1;}
        var before=player.getKnownMovement();var acceleration=acceleration(start,end,before,hook.length());
        var support=level.getBlockStatesIfLoaded(player.getBoundingBox().inflate(.0625).expandTowards(0,-.55,0)).iterator();
        boolean inspected=false,noSupport=true;
        while(support.hasNext()){inspected=true;if(!support.next().isAir()){noSupport=false;break;}}
        noSupport&=inspected;
        boolean requested=requestedDescent(player);
        boolean vertical=distance>0&&start.y-end.y>2&&(start.y-end.y)/distance>=.80;
        boolean controlled=requested&&noSupport&&vertical&&distance>=hook.paidLength()-.30
                &&!player.onGround()&&!player.horizontalCollision&&!player.verticalCollision
                &&!player.onClimbable()&&!player.isInWater()&&!player.isInFluidType()&&before.y<=.15;
        if(controlled){
            if(hook.paidLength()>=MAX_ROPE_LENGTH-.001){detach(player);player.displayClientMessage(Component.translatable("message.interstice.winch.rope_end"),true);return -1;}
            // Tension can brake a fall, never push the player downward or create a platform.
            // A bounded radial brake also catches a normal initial fall before settling near .10/tick.
            var inward=start.subtract(end).normalize();double brake=Math.min(MAX_DESCENT_BRAKE,Math.max(0,(-DESCENT_SPEED-before.y)/inward.y));
            var after=before.add(inward.scale(brake));
            double payout=Math.max(.08,Math.min(DESCENT_SPEED,-after.y));
            double paid=Math.min(MAX_ROPE_LENGTH,hook.paidLength()+payout);
            controlledState(player,node,paid,true);
            if(brake>0){player.setDeltaMovement(after);player.hurtMarked=true;player.hasImpulse=true;}
            // Only this proven loaded, unobstructed, taut owned rope pays for fall protection.
            player.resetFallDistance();
            if(!hook.descending()||level.getGameTime()%20==0){
                player.displayClientMessage(Component.translatable("message.interstice.winch.descending",Math.max(0,(int)Math.floor(MAX_ROPE_LENGTH-paid))),true);sync(player,node,true);
            }
            if(hook.paidLength()<MAX_ROPE_LENGTH-2&&paid>=MAX_ROPE_LENGTH-2)
                player.displayClientMessage(Component.translatable("message.interstice.winch.rope_warning"),true);
            return 0;
        }
        if(hook.descending()){
            controlledState(player,node,hook.paidLength(),false);
            player.displayClientMessage(Component.translatable("message.interstice.winch.descent_stopped"),true);
        }
        int air=noSupport&&!player.onClimbable()&&!player.isInWater()&&!player.isInFluidType()&&before.y>=-.03125?hook.airTicks()+1:0;
        if(air==40){player.displayClientMessage(Component.translatable("message.interstice.winch.release_warning"),true);level.playSound(null,player.blockPosition(),SoundEvents.CHAIN_STEP,SoundSource.PLAYERS,.7F,.7F);}
        if(air>=60){detach(player);player.displayClientMessage(Component.translatable("message.interstice.winch.safety_release"),true);level.playSound(null,player.blockPosition(),SoundEvents.CHAIN_BREAK,SoundSource.PLAYERS,.8F,.6F);return -1;}
        if(acceleration.lengthSqr()>0){var after=before.add(acceleration);player.setDeltaMovement(after);player.hurtMarked=true;player.hasImpulse=true;
        }
        if(level.getGameTime()%20==0)sync(player,node,true);return air;
    }
    public static void sync(ServerPlayer player,WinchBlockEntity node,boolean active){TetherNetworking.broadcast(player,active?node:null);}
    @SubscribeEvent public static void validate(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;var link=link(player);if(link==null)return;
        if(!link.dimension.equals(player.level().dimension().location())||!player.isAlive()){detach(player);return;}
        var chunk=player.serverLevel().getChunkSource().getChunkNow(link.anchor.getX()>>4,link.anchor.getZ()>>4);if(chunk==null)return;
        var node=loaded(player.serverLevel(),link.anchor);if(node==null||!node.anchorId().equals(link.anchorId)||node.hook(player.getUUID())==null)detach(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer player)detach(player);}
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer player)detach(player);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){save(event.getEntity(),null);save(event.getOriginal(),null);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player){var link=link(player);if(link!=null){var node=loaded(player.serverLevel(),link.anchor);if(node!=null&&matches(player,node))sync(player,node,true);}}}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){if(event.getEntity() instanceof ServerPlayer viewer&&event.getTarget() instanceof ServerPlayer player){var link=link(player);if(link!=null){var node=loaded(player.serverLevel(),link.anchor);if(node!=null&&matches(player,node))TetherNetworking.send(viewer,player,node);}}}
}
