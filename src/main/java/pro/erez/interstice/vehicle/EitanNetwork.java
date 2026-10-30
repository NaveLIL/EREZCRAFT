package pro.erez.interstice.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pro.erez.interstice.Interstice;

public final class EitanNetwork {
    public record Input(BlockPos controller,int forward,int turn,boolean brake,boolean boost) implements CustomPacketPayload {
        public static final Type<Input> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"eitan_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> STREAM=StreamCodec.of((b,p)->{b.writeBlockPos(p.controller);b.writeByte(p.forward);b.writeByte(p.turn);b.writeBoolean(p.brake);b.writeBoolean(p.boost);},b->new Input(b.readBlockPos(),b.readByte(),b.readByte(),b.readBoolean(),b.readBoolean()));
        public Type<Input> type(){return TYPE;}
    }
    public record Action(BlockPos controller,int action,int index) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"eitan_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> STREAM=StreamCodec.of((b,p)->{b.writeBlockPos(p.controller);b.writeByte(p.action);b.writeByte(p.index);},b->new Action(b.readBlockPos(),b.readUnsignedByte(),b.readUnsignedByte()));
        public Type<Action> type(){return TYPE;}
    }
    public record Panel(BlockPos controller,int monitor) implements CustomPacketPayload {
        public static final Type<Panel> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"eitan_panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Panel> STREAM=StreamCodec.of((b,p)->{b.writeBlockPos(p.controller);b.writeByte(p.monitor);},b->new Panel(b.readBlockPos(),b.readByte()));
        public Type<Panel> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("eitan-v1")
        .playToServer(Input.TYPE,Input.STREAM,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player&&player.serverLevel().hasChunkAt(p.controller)&&player.serverLevel().getBlockEntity(p.controller) instanceof EitanController vehicle)vehicle.input(player,p.forward,p.turn,p.brake,p.boost);}))
        .playToServer(Action.TYPE,Action.STREAM,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player&&player.serverLevel().hasChunkAt(p.controller)&&player.serverLevel().getBlockEntity(p.controller) instanceof EitanController vehicle)vehicle.action(player,p.action,p.index);}))
        .playToClient(Panel.TYPE,Panel.STREAM,(p,c)->c.enqueueWork(()->pro.erez.interstice.client.EitanClient.open(p)));
    }
    public static void open(ServerPlayer p,EitanController vehicle,int monitor){vehicle.sync();PacketDistributor.sendToPlayer(p,new Panel(vehicle.getBlockPos(),monitor));}
    private EitanNetwork(){}
}
