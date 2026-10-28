package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pro.erez.interstice.Interstice;

/** No client positions or target claims. The server selects LOS targets, energy and cooldown. */
public final class EngineerNetworking {
    public record Action(int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"engineer_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> STREAM=StreamCodec.of((b,p)->b.writeVarInt(p.action),b->new Action(b.readVarInt()));
        public Type<Action> type(){return TYPE;}
    }
    public record Target(int entity,UUID uuid){}
    public record Pulse(ResourceLocation dimension,List<Target> targets) implements CustomPacketPayload {
        public Pulse{targets=List.copyOf(targets);if(targets.size()>256)throw new IllegalArgumentException("Too many pulse targets");}
        public static final Type<Pulse> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"engineer_pulse"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Pulse> STREAM=StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension);b.writeVarInt(p.targets.size());for(var t:p.targets){b.writeVarInt(t.entity);b.writeUUID(t.uuid);}},b->{var dimension=b.readResourceLocation();int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("Pulse bounds");var targets=new ArrayList<Target>();for(int i=0;i<n;i++)targets.add(new Target(b.readVarInt(),b.readUUID()));return new Pulse(dimension,targets);});
        public Type<Pulse> type(){return TYPE;}
    }
    public record Status(ResourceLocation dimension,Component diagnostic,Component route,boolean breathing) implements CustomPacketPayload {
        public static final Type<Status> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"engineer_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Status> STREAM=StreamCodec.composite(ResourceLocation.STREAM_CODEC,Status::dimension,net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC,Status::diagnostic,net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC,Status::route,ByteBufCodecs.BOOL,Status::breathing,Status::new);
        public Type<Status> type(){return TYPE;}
    }
    public static void pulse(ServerPlayer player,List<LivingEntity> targets){PacketDistributor.sendToPlayer(player,new Pulse(player.level().dimension().location(),targets.stream().limit(256).map(e->new Target(e.getId(),e.getUUID())).toList()));}
    public static void status(ServerPlayer player,Component diagnostic,Component route,boolean breathing){PacketDistributor.sendToPlayer(player,new Status(player.level().dimension().location(),diagnostic,route,breathing));}
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("engineer-d02-v1");
        registrar.playToServer(Action.TYPE,Action.STREAM,(packet,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player){if(packet.action()==0)EngineerSystems.open(player);else if(packet.action()==1)EngineerSystems.pulse(player);}
        }));
        registrar.playToClient(Pulse.TYPE,Pulse.STREAM,(packet,context)->context.enqueueWork(()->pro.erez.interstice.client.EngineerClient.receive(packet)));
        registrar.playToClient(Status.TYPE,Status.STREAM,(packet,context)->context.enqueueWork(()->pro.erez.interstice.client.EngineerClient.receive(packet)));
    }
    private EngineerNetworking(){}
}
