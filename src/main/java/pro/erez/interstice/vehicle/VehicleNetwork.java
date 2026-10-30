package pro.erez.interstice.vehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pro.erez.interstice.Interstice;
public final class VehicleNetwork {
    public record Request(BlockPos anchor,int action,String pin) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"vehicle_access"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> STREAM=StreamCodec.of((b,p)->{b.writeBlockPos(p.anchor);b.writeByte(p.action);b.writeUtf(p.pin,6);},b->new Request(b.readBlockPos(),b.readUnsignedByte(),b.readUtf(6)));
        public Type<Request> type(){return TYPE;}
    }
    public record Panel(BlockPos anchor,boolean owner,boolean configured,boolean admitted,boolean maintenance) implements CustomPacketPayload {
        public static final Type<Panel> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"vehicle_access_panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Panel> STREAM=StreamCodec.of((b,p)->{b.writeBlockPos(p.anchor);b.writeBoolean(p.owner);b.writeBoolean(p.configured);b.writeBoolean(p.admitted);b.writeBoolean(p.maintenance);},b->new Panel(b.readBlockPos(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean()));
        public Type<Panel> type(){return TYPE;}
    }
    public static void open(ServerPlayer p,BlockEntity e){if(!VehicleAccess.reach(p,e))return;var s=VehicleAccess.security(e);if(s==null)return;PacketDistributor.sendToPlayer(p,new Panel(e.getBlockPos(),s.owner(p),s.configured(),s.allowed(p),s.maintenance(p,p.level().getGameTime())));}
    public static void handle(ServerPlayer p,Request r){if(!p.serverLevel().hasChunkAt(r.anchor))return;var e=p.serverLevel().getBlockEntity(r.anchor);var s=VehicleAccess.security(e);if(s==null||!VehicleAccess.reach(p,e))return;
        if(r.action==0){open(p,e);return;}boolean ok=r.action==1?s.unlock(p,r.pin,p.level().getGameTime()):r.action==2?s.changePin(p,r.pin):r.action==3?s.toggleMaintenance(p,p.level().getGameTime()):false;
        if(ok)VehicleAccess.changed(e);p.displayClientMessage(net.minecraft.network.chat.Component.translatable(ok?"interstice.vehicle.access_ok":"interstice.vehicle.access_denied"),true);open(p,e);
    }
    public static void register(RegisterPayloadHandlersEvent e){e.registrar("vehicle-access-v1").playToServer(Request.TYPE,Request.STREAM,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer sp)handle(sp,p);})).playToClient(Panel.TYPE,Panel.STREAM,(p,c)->pro.erez.interstice.client.VehicleClientAccess.receive(p,c));}
    private VehicleNetwork(){}
}
