package pro.erez.interstice.equipment;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pro.erez.interstice.Interstice;

public final class BackpackNetworking {
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"open_backpack"));
        public static final StreamCodec<FriendlyByteBuf,Open> STREAM=StreamCodec.unit(new Open());
        public Type<Open> type(){return TYPE;}
    }
    public record OpenHarness() implements CustomPacketPayload {
        public static final Type<OpenHarness> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"open_backpack_harness"));
        public static final StreamCodec<FriendlyByteBuf,OpenHarness> STREAM=StreamCodec.unit(new OpenHarness());
        public Type<OpenHarness> type(){return TYPE;}
    }
    public record WearSync(int entityId,ItemStack stack) implements CustomPacketPayload {
        public WearSync{stack=stack.copy();}
        public static final Type<WearSync> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"worn_backpack_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf,WearSync> STREAM=StreamCodec.composite(ByteBufCodecs.VAR_INT,WearSync::entityId,ItemStack.OPTIONAL_STREAM_CODEC,WearSync::stack,WearSync::new);
        public Type<WearSync> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("backpack-v1");
        registrar.playToServer(Open.TYPE,Open.STREAM,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player){int slot=BackpackStorage.activeSlot(player);if(slot>=0)BackpackItem.open(player,slot);}}));
        registrar.playToServer(OpenHarness.TYPE,OpenHarness.STREAM,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)BackpackHarness.open(player);}));
        registrar.playToClient(WearSync.TYPE,WearSync.STREAM,(packet,context)->context.enqueueWork(()->{
            var entity=context.player().level().getEntity(packet.entityId());if(entity instanceof Player player)BackpackHarness.applyClient(player,packet.stack());
        }));
    }
    private BackpackNetworking(){}
}
