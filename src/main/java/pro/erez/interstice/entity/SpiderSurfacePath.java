package pro.erez.interstice.entity;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Local, collision-checked surface graph. Never requests chunks or moves through solid corners. */
public final class SpiderSurfacePath {
    public record Step(BlockPos support, Direction face, Vec3 center) {}
    private final CaveRiftSpiderEntity spider;
    private final Map<BlockPos, List<Step>> cells = new HashMap<>();
    public SpiderSurfacePath(CaveRiftSpiderEntity spider) { this.spider = spider; }
    private AABB box(Vec3 center) {
        return AABB.ofSize(center, spider.getBbWidth(), spider.getBbHeight(), spider.getBbWidth()).deflate(.001);
    }
    public boolean clear(Vec3 center) {
        AABB box = box(center);
        for (int x = (int)Math.floor(box.minX) >> 4; x <= (int)Math.floor(box.maxX) >> 4; x++)
            for (int z = (int)Math.floor(box.minZ) >> 4; z <= (int)Math.floor(box.maxZ) >> 4; z++)
                if (!spider.level().hasChunk(x, z)) return false;
        return spider.level().noCollision(spider, box) && !spider.level().containsAnyLiquid(box);
    }
    public boolean clearSegment(Vec3 from, Vec3 to) {
        int samples = Math.max(1, (int)Math.ceil(from.distanceTo(to) / .18));
        for (int i = 0; i <= samples; i++) if (!clear(from.lerp(to, (double)i / samples))) return false;
        return true;
    }
    private List<Step> at(BlockPos block) {
        return cells.computeIfAbsent(block.immutable(), pos -> {
            if (!spider.level().hasChunkAt(pos)) return List.of();
            var state = spider.level().getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return List.of();
            List<Step> result = new ArrayList<>();
            for (Direction face : Direction.values()) {
                if (!state.isFaceSturdy(spider.level(), pos, face.getOpposite())) continue;
                double r = spider.getBbWidth() / 2.0;
                Vec3 center = Vec3.atCenterOf(pos).subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(.5 + r));
                if (clear(center)) result.add(new Step(pos, face, center));
            }
            return result;
        });
    }
    private List<Step> nearby(Vec3 center) {
        List<Step> result = new ArrayList<>();
        BlockPos base = BlockPos.containing(center);
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++)
            for (Step step : at(base.offset(x, y, z)))
                if (step.center.distanceToSqr(center) <= 2.05) result.add(step);
        return result;
    }
    public List<Step> find(Vec3 goal) {
        Vec3 start = spider.getBoundingBox().getCenter();
        record Entry(Step step, double score) {}
        var open = new PriorityQueue<Entry>(Comparator.comparingDouble(Entry::score));
        Map<Step, Double> costs = new HashMap<>();
        Map<Step, Step> parents = new HashMap<>();
        Set<Step> closed = new HashSet<>();
        for (Step first : nearby(start)) {
            if (!clearSegment(start, first.center)) continue;
            double cost = start.distanceTo(first.center);
            costs.put(first, cost); open.add(new Entry(first, cost + first.center.distanceTo(goal)));
        }
        Step best = null;
        while (!open.isEmpty() && closed.size() < 384) {
            Step current = open.remove().step;
            if (!closed.add(current)) continue;
            if (best == null || current.center.distanceToSqr(goal) < best.center.distanceToSqr(goal)) best = current;
            if (current.center.distanceToSqr(goal) < .65) break;
            for (Step next : nearby(current.center)) {
                if (closed.contains(next) || next.center.distanceToSqr(start) > 16 * 16) continue;
                // Adjacent full faces only. No shortcuts across unsupported air or through a corner.
                if (current.support.distManhattan(next.support) > 4 || !clearSegment(current.center, next.center)) continue;
                double cost = costs.get(current) + current.center.distanceTo(next.center) + (current.face == next.face ? 0 : .15);
                if (cost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                costs.put(next, cost); parents.put(next, current);
                open.add(new Entry(next, cost + next.center.distanceTo(goal)));
            }
        }
        if (best == null || best.center.distanceTo(goal) > 1.6) return List.of();
        LinkedList<Step> path = new LinkedList<>();
        for (Step step = best; step != null; step = parents.get(step)) path.addFirst(step);
        return List.copyOf(path);
    }
}
