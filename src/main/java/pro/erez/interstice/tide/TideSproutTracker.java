package pro.erez.interstice.tide;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.TideSproutBlock;

/** A derived index of roots in loaded chunks; never saved or used to load new chunks. */
@EventBusSubscriber(modid = Interstice.ID)
public final class TideSproutTracker {
    private static final Map<ServerLevel, Map<Long, Set<BlockPos>>> ROOTS = new WeakHashMap<>();

    private TideSproutTracker() {}

    public static synchronized void addRoot(ServerLevel level, BlockPos pos) {
        ROOTS.computeIfAbsent(level, key -> new HashMap<>())
                .computeIfAbsent(ChunkPos.asLong(pos), key -> new HashSet<>()).add(pos.immutable());
    }

    public static synchronized void removeRoot(ServerLevel level, BlockPos pos) {
        var chunks = ROOTS.get(level);
        if (chunks == null) return;
        var roots = chunks.get(ChunkPos.asLong(pos));
        if (roots == null) return;
        roots.remove(pos);
        if (roots.isEmpty()) chunks.remove(ChunkPos.asLong(pos));
        if (chunks.isEmpty()) ROOTS.remove(level);
    }

    @SubscribeEvent
    public static synchronized void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        Set<BlockPos> roots = new HashSet<>();
        var sections = chunk.getSections();
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            var section = sections[i];
            // Most sections do not contain plants; examine their palettes only once on load.
            if (!section.maybeHas(state -> state.is(Interstice.TIDE_SPROUT.get())
                    && state.getValue(TideSproutBlock.SECTION) == 0)) continue;
            int startY = chunk.getMinBuildHeight() + i * 16;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) {
                var state = section.getBlockState(x, y, z);
                if (state.is(Interstice.TIDE_SPROUT.get()) && state.getValue(TideSproutBlock.SECTION) == 0) {
                    roots.add(new BlockPos(startX + x, startY + y, startZ + z));
                }
            }
        }
        if (roots.isEmpty()) {
            removeChunk(level, chunk.getPos().toLong());
        } else {
            ROOTS.computeIfAbsent(level, key -> new HashMap<>()).put(chunk.getPos().toLong(), roots);
        }
    }

    @SubscribeEvent
    public static synchronized void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) removeChunk(level, event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static synchronized void onLevelUnload(LevelEvent.Unload event) {
        ROOTS.remove(event.getLevel());
    }

    private static void removeChunk(ServerLevel level, long key) {
        var chunks = ROOTS.get(level);
        if (chunks == null) return;
        chunks.remove(key);
        if (chunks.isEmpty()) ROOTS.remove(level);
    }

    /** Schedule only indexed roots in already loaded chunks inside the original search volume. */
    public static synchronized int wakeNearby(ServerLevel level, BlockPos center, int radius) {
        var chunks = ROOTS.get(level);
        if (chunks == null || radius < 0) return 0;
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - 6);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 8);
        int scheduled = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            var roots = chunks.get(ChunkPos.asLong(cx, cz));
            if (roots == null) continue;
            var iterator = roots.iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next();
                if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ
                        || pos.getY() < minY || pos.getY() > maxY) continue;
                var state = chunk.getBlockState(pos);
                if (!state.is(Interstice.TIDE_SPROUT.get()) || state.getValue(TideSproutBlock.SECTION) != 0) {
                    iterator.remove();
                    continue;
                }
                level.scheduleTick(pos, state.getBlock(), 1 + level.random.nextInt(6));
                scheduled++;
            }
        }
        return scheduled;
    }
}
