package pro.erez.interstice.gear;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pro.erez.interstice.Interstice;

/** One authoritative server event; the client lets every finite voice finish before another starts. */
@EventBusSubscriber(modid=Interstice.ID,bus=EventBusSubscriber.Bus.MOD)
public final class KotomyafSounds {
    private KotomyafSounds(){}
    public record Voice(int entityId,boolean jump) implements CustomPacketPayload {
        public static final Type<Voice> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"kotomyaf_voice"));
        public static final StreamCodec<FriendlyByteBuf,Voice> CODEC=new StreamCodec<>(){
            public Voice decode(FriendlyByteBuf b){return new Voice(b.readVarInt(),b.readBoolean());}
            public void encode(FriendlyByteBuf b,Voice value){b.writeVarInt(value.entityId);b.writeBoolean(value.jump);}
        };
        @Override public Type<Voice> type(){return TYPE;}
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("kotomyaf-v1").playToClient(Voice.TYPE,Voice.CODEC,(packet,context)->context.enqueueWork(()->pro.erez.interstice.client.KotomyafVoiceClient.request(packet)));
    }
    public static void send(ServerPlayer player,boolean jump){PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,new Voice(player.getId(),jump));}
}
