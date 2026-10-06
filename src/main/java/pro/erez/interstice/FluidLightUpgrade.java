package pro.erez.interstice;

import com.mojang.serialization.Codec;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Upgrade saved level-5 lighting once, without changing any blocks or regenerating terrain. */
@EventBusSubscriber(modid = Interstice.ID)
public final class FluidLightUpgrade {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Interstice.ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> VERSION = TYPES.register("fluid_light_version",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());
    private record Pending(ServerLevel level, LevelChunk chunk, int attempts) {}
    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();
    public static void register(IEventBus bus) { TYPES.register(bus); }

    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)
                || chunk.getData(VERSION) >= 1) return;
        if (event.isNewChunk()) markCurrent(chunk);
        else PENDING.add(new Pending(level, chunk, 0));
    }
    private static void markCurrent(LevelChunk chunk) {
        chunk.setData(VERSION, 1);
        chunk.setUnsaved(true);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        // Chunk load fires before FULL promotion. Defer interactions and bound work per tick.
        int count = Math.min(16, PENDING.size());
        for (int i=0; i<count; i++) {
            Pending pending=PENDING.poll();
            if (pending==null) break;
            var level=pending.level(); var chunk=pending.chunk(); var pos=chunk.getPos();
            if (level.getServer()!=event.getServer()) { PENDING.add(pending); continue; }
            if (level.getChunkSource().getChunkNow(pos.x,pos.z)!=chunk) {
                if (pending.attempts()<60) PENDING.add(new Pending(level,chunk,pending.attempts()+1));
                continue;
            }
            boolean hasLightFluid=false;
            for (var section:chunk.getSections()) {
                if (section.maybeHas(state -> state.is(Interstice.LIGHT_BLOCK.get()) || state.is(Interstice.LIGHT_SEA.get()))) {
                    hasLightFluid=true; break;
                }
            }
            if (!hasLightFluid) { markCurrent(chunk); continue; }
            level.getChunkSource().getLightEngine().lightChunk(chunk,false)
                    .thenRunAsync(() -> markCurrent(chunk),level.getServer());
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        PENDING.removeIf(pending -> pending.level()==event.getLevel());
    }
}
