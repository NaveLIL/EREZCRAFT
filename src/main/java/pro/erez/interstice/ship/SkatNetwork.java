package pro.erez.interstice.ship;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pro.erez.interstice.Interstice;
public final class SkatNetwork {
    /** Optional independent channel: existing skat-v1 controls remain wire compatible. */
    public record Speed(BlockPos helm,float metersPerSecond) implements CustomPacketPayload {
        public static final Type<Speed> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"skat_speed"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Speed> STREAM=StreamCodec.of((b,p)->{b.writeBlockPos(p.helm);b.writeFloat(p.metersPerSecond);},b->new Speed(b.readBlockPos(),b.readFloat()));
        public Type<Speed> type(){return TYPE;}
    }
    public record Lighting(BlockPos helm,int channel,boolean enabled) implements CustomPacketPayload {
        public static final Type<Lighting> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"skat_lighting"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Lighting> STREAM=StreamCodec.of((b,p)->{b.writeBlockPos(p.helm);b.writeByte(p.channel);b.writeBoolean(p.enabled);},b->new Lighting(b.readBlockPos(),b.readUnsignedByte(),b.readBoolean()));
        public Type<Lighting> type(){return TYPE;}
    }
    public record LightingPanel(BlockPos helm,int lighting,boolean open) implements CustomPacketPayload {
        public static final Type<LightingPanel> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"skat_lighting_panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf,LightingPanel> STREAM=StreamCodec.of((b,p)->{b.writeBlockPos(p.helm);b.writeByte(p.lighting);b.writeBoolean(p.open);},b->new LightingPanel(b.readBlockPos(),b.readUnsignedByte(),b.readBoolean()));
        public Type<LightingPanel> type(){return TYPE;}
    }
    public record Input(BlockPos helm,int forward,int turn) implements CustomPacketPayload {
        public static final Type<Input> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"skat_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> STREAM=StreamCodec.of((b,p)->{b.writeBlockPos(p.helm);b.writeByte(p.forward);b.writeByte(p.turn);},b->new Input(b.readBlockPos(),b.readByte(),b.readByte()));
        public Type<Input> type(){return TYPE;}
    }
    public record Status(BlockPos helm,boolean active,int rpm,int turn,int leftFuel,int rightFuel) implements CustomPacketPayload {
        public static final Type<Status> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"skat_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Status> STREAM=StreamCodec.of((b,p)->{b.writeBlockPos(p.helm);b.writeBoolean(p.active);b.writeVarInt(p.rpm);b.writeVarInt(p.turn);b.writeVarInt(p.leftFuel);b.writeVarInt(p.rightFuel);},b->new Status(b.readBlockPos(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
        public Type<Status> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("skat-v1")
        .playToServer(Lighting.TYPE,Lighting.STREAM,(packet,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer p&&p.serverLevel().hasChunkAt(packet.helm)&&p.serverLevel().getBlockEntity(packet.helm) instanceof SkatHelmEntity helm)helm.setLighting(p,packet.channel,packet.enabled);
        }))
        .playToClient(LightingPanel.TYPE,LightingPanel.STREAM,(packet,context)->context.enqueueWork(()->pro.erez.interstice.client.SkatClient.lightingPanel(packet)))
        .playToServer(Input.TYPE,Input.STREAM,(packet,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player&&player.serverLevel().hasChunkAt(packet.helm)&&player.serverLevel().getBlockEntity(packet.helm) instanceof SkatHelmEntity helm)helm.input(player,packet.forward,packet.turn);
        }))
        .playToClient(Status.TYPE,Status.STREAM,(packet,context)->context.enqueueWork(()->pro.erez.interstice.client.SkatClient.status(packet)));
        event.registrar("skat-speed-v1").optional().playToClient(Speed.TYPE,Speed.STREAM,(packet,context)->context.enqueueWork(()->pro.erez.interstice.client.SkatClient.speed(packet)));
    }
    private SkatNetwork(){}
}
