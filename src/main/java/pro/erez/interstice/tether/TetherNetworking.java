package pro.erez.interstice.tether;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pro.erez.interstice.Interstice;

/** Only authoritative visual state goes over the network. Attaching uses validated ordinary block interaction. */
public final class TetherNetworking {
    public record State(int entityId,UUID player,ResourceLocation dimension,BlockPos anchor,int facing,int length,boolean active) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"winch_link_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> STREAM=StreamCodec.of((buf,value)->{
            buf.writeVarInt(value.entityId);buf.writeUUID(value.player);buf.writeResourceLocation(value.dimension);buf.writeBlockPos(value.anchor);buf.writeVarInt(value.facing);buf.writeVarInt(value.length);buf.writeBoolean(value.active);
        },buf->new State(buf.readVarInt(),buf.readUUID(),buf.readResourceLocation(),buf.readBlockPos(),buf.readVarInt(),buf.readVarInt(),buf.readBoolean()));
        public Type<State> type(){return TYPE;}
    }
    public static State state(ServerPlayer player,WinchBlockEntity node){var link=WinchLinks.link(player);return new State(player.getId(),player.getUUID(),player.level().dimension().location(),node==null?BlockPos.ZERO:node.getBlockPos(),node==null?0:node.getBlockState().getValue(WinchBlock.FACING).get3DDataValue(),node==null||link==null?0:link.length(),node!=null&&link!=null);}
    public static void broadcast(ServerPlayer player,WinchBlockEntity node){PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,state(player,node));}
    public static void send(ServerPlayer viewer,ServerPlayer player,WinchBlockEntity node){PacketDistributor.sendToPlayer(viewer,state(player,node));}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("winch-v1").playToClient(State.TYPE,State.STREAM,(packet,context)->context.enqueueWork(()->ClientTetherState.apply(packet)));}
    private TetherNetworking(){}
}
