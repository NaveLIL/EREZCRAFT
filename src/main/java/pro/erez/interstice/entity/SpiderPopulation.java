package pro.erez.interstice.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Bounds both natural arrivals and nest defenders, without deleting existing mobs. */
public final class SpiderPopulation {
    public static final int LOCAL_CAP = 6, LOADED_CAP = 32, LOCAL_RADIUS = 48;
    public static boolean hasRoom(ServerLevel level, BlockPos pos) {
        if (level.getEntitiesOfClass(CaveRiftSpiderEntity.class,
                new AABB(pos).inflate(LOCAL_RADIUS), e -> e.isAlive() && !e.isRemoved()).size() >= LOCAL_CAP)
            return false;
        int loaded = 0;
        for (var entity : level.getAllEntities())
            if (entity instanceof CaveRiftSpiderEntity && entity.isAlive() && !entity.isRemoved()
                    && ++loaded >= LOADED_CAP) return false;
        return true;
    }
    private SpiderPopulation() {}
}
