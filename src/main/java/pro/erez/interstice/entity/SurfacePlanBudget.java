package pro.erez.interstice.entity;

import java.util.LinkedHashMap;

/** One surface planner per level tick, with FIFO admission instead of entity-order starvation. */
public final class SurfacePlanBudget {
    private final LinkedHashMap<Integer, Long> waiting = new LinkedHashMap<>();
    private long usedTick = Long.MIN_VALUE;

    public boolean tryAcquire(int entityId, long tick) {
        // Requests from dead/unloaded/finished goals cannot hold the queue indefinitely.
        waiting.entrySet().removeIf(entry -> entry.getValue() < tick - 4);
        if (!waiting.containsKey(entityId) && waiting.size() >= 512) return false;
        waiting.put(entityId, tick); // Replacing a value keeps its original FIFO position.
        if (usedTick == tick || waiting.keySet().iterator().next() != entityId) return false;
        waiting.remove(entityId);
        usedTick = tick;
        return true;
    }
}
