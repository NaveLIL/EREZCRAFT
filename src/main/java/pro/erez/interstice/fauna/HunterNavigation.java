package pro.erez.interstice.fauna;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

/** Vanilla A* and collision, restricted to chunks that are already loaded.
 * Guards the full PathNavigationRegion before vanilla constructs its chunk cache. */
final class HunterNavigation extends GroundPathNavigation {
    HunterNavigation(Mob mob,Level level){super(mob,level);}
    static boolean loadedAround(Level level,BlockPos origin,int radius){
        if(!(level instanceof ServerLevel server))return false;
        for(int x=(origin.getX()-radius)>>4;x<=(origin.getX()+radius)>>4;x++)
            for(int z=(origin.getZ()-radius)>>4;z<=(origin.getZ()+radius)>>4;z++)
                if(server.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
    @Override public Path createPath(BlockPos target,int accuracy){
        return level.hasChunkAt(target)?super.createPath(target,accuracy):null;
    }
    @Override protected Path createPath(Set<BlockPos> targets,int padding,boolean above,int accuracy,float range){
        int radius=(int)(range+padding);BlockPos origin=above?mob.blockPosition().above():mob.blockPosition();
        if(!loadedAround(level,origin,radius))return null;
        return super.createPath(targets,padding,above,accuracy,range);
    }
}
