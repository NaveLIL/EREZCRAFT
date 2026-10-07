package pro.erez.interstice.geometry;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

public final class GeometrySync {
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type(id("geometry"));
    private GeometrySync() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Interstice.ID, path); }

    public record Profiles(Map<ResourceLocation, GeometryProfile> values) implements CustomPacketPayload {
        public static final Type<Profiles> TYPE = new Type<>(id("geometry_profiles"));
        public static final StreamCodec<FriendlyByteBuf, Profiles> STREAM_CODEC = new StreamCodec<>() {
            @Override public Profiles decode(FriendlyByteBuf buffer) {
                int count = buffer.readVarInt();
                if (count < 0 || count > 1024) throw new IllegalArgumentException("Invalid geometry profile count: " + count);
                Map<ResourceLocation, GeometryProfile> values = new HashMap<>();
                for (int i = 0; i < count; i++) {
                    ResourceLocation key = buffer.readResourceLocation();
                    if (values.put(key, GeometryProfile.read(buffer)) != null)
                        throw new IllegalArgumentException("Duplicate geometry dimension: " + key);
                }
                return new Profiles(values);
            }
            @Override public void encode(FriendlyByteBuf buffer, Profiles payload) {
                buffer.writeVarInt(payload.values.size());
                payload.values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    buffer.writeResourceLocation(entry.getKey()); entry.getValue().write(buffer);
                });
            }
        };
        public Profiles {
            if (values.size() > 1024) throw new IllegalArgumentException("Too many geometry profiles");
            values = Map.copyOf(values);
        }
        @Override public Type<Profiles> type() { return TYPE; }
    }
    private record Ack() implements CustomPacketPayload {
        private static final Type<Ack> TYPE = new Type<>(id("geometry_ack"));
        private static final StreamCodec<ByteBuf, Ack> STREAM_CODEC = StreamCodec.unit(new Ack());
        @Override public Type<Ack> type() { return TYPE; }
    }
    private record SendProfiles(Profiles profiles) implements ICustomConfigurationTask {
        @Override public void run(Consumer<CustomPacketPayload> sender) { sender.accept(profiles); }
        @Override public ConfigurationTask.Type type() { return TASK; }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("geometry-v1");
        registrar.configurationToClient(Profiles.TYPE, Profiles.STREAM_CODEC, (payload, context) -> {
            GeometryProfiles.configure(context.connection(), payload.values());
            context.reply(new Ack());
        });
        registrar.configurationToServer(Ack.TYPE, Ack.STREAM_CODEC,
                (payload, context) -> context.finishCurrentTask(TASK));
    }
    public static void registerTask(RegisterConfigurationTasksEvent event) {
        var server = java.util.Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer(), "Server unavailable during geometry configuration");
        Map<ResourceLocation, GeometryProfile> profiles = new HashMap<>();
        for (var level : server.getAllLevels()) {
            if (level.getChunkSource().getGenerator() instanceof IslandChunkGenerator islands) {
                islands.geometry().checkHeight(level);
                profiles.put(level.dimension().location(), islands.geometry());
            }
        }
        event.register(new SendProfiles(new Profiles(profiles)));
    }
}
