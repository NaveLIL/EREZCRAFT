package pro.erez.interstice.tide;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import pro.erez.interstice.Interstice;

/**
 * Network synchronization for the Interstice tide state.
 */
public final class TideSync {
    private TideSync() {}

    public record Payload(
            byte phaseId,
            long phaseTicksElapsed,
            long phaseDurationTicks,
            long totalCycles,
            float intensity
    ) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Interstice.ID, "tide_sync"));

        public static final StreamCodec<FriendlyByteBuf, Payload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Payload decode(FriendlyByteBuf buffer) {
                byte phase = buffer.readByte();
                long elapsed = buffer.readVarLong();
                long duration = buffer.readVarLong();
                long cycles = buffer.readVarLong();
                float intensity = buffer.readFloat();
                return new Payload(phase, elapsed, duration, cycles, intensity);
            }

            @Override
            public void encode(FriendlyByteBuf buffer, Payload payload) {
                buffer.writeByte(payload.phaseId);
                buffer.writeVarLong(payload.phaseTicksElapsed);
                buffer.writeVarLong(payload.phaseDurationTicks);
                buffer.writeVarLong(payload.totalCycles);
                buffer.writeFloat(payload.intensity);
            }
        };

        public static Payload from(TideState state) {
            return new Payload(
                    (byte) state.phase().id(),
                    state.phaseTicksElapsed(),
                    state.phaseDurationTicks(),
                    state.totalCycles(),
                    state.intensity()
            );
        }

        public TideState toState() {
            return new TideState(TidePhase.byId(phaseId), phaseTicksElapsed, phaseDurationTicks, totalCycles);
        }

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("tide-v1");
        registrar.playToClient(Payload.TYPE, Payload.STREAM_CODEC, TideSync::handleClientPayload);
    }

    private static void handleClientPayload(Payload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientTideState.update(payload.toState()));
    }

    public static void sendTo(ServerPlayer player, TideState state) {
        PacketDistributor.sendToPlayer(player, Payload.from(state));
    }

    public static void broadcast(TideState state) {
        PacketDistributor.sendToAllPlayers(Payload.from(state));
    }
}
