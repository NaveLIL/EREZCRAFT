package pro.erez.interstice.ship;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Whole-site preflight before any write; source lighting finishes before relocation. */
public final class MarineAssembly {
    public static CompletableFuture<ServerSubLevel> create(ServerLevel level,BlockPos origin){
        for(var c:MarineData.CELLS){var p=origin.offset(c.pos());level.getChunkAt(p);if(p.getY()<level.getMinBuildHeight()||p.getY()>=level.getMaxBuildHeight()||!level.getBlockState(p).isAir()||level.getBlockEntity(p)!=null)throw new IllegalArgumentException("NE01 needs clear drydock: "+p);}
        var positions=new ArrayList<BlockPos>();var chunks=new HashSet<net.minecraft.world.level.ChunkPos>();
        for(var c:MarineData.CELLS){var p=origin.offset(c.pos());level.setBlock(p,MarineShips.state(c),3);positions.add(p);chunks.add(new net.minecraft.world.level.ChunkPos(p));}
        var barriers=chunks.stream().map(p->level.getChunkSource().getLightEngine().waitForPendingTasks(p.x,p.z)).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(barriers).thenApplyAsync(unused->{
            int x0=positions.stream().mapToInt(BlockPos::getX).min().orElseThrow(),x1=positions.stream().mapToInt(BlockPos::getX).max().orElseThrow();
            int y0=positions.stream().mapToInt(BlockPos::getY).min().orElseThrow(),y1=positions.stream().mapToInt(BlockPos::getY).max().orElseThrow();
            int z0=positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow(),z1=positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
            var ship=SubLevelAssemblyHelper.assembleBlocks(level,origin,positions,new BoundingBox3i(x0,y0,z0,x1,y1,z1));
            if(ship==null)throw new IllegalStateException("Sable rejected NE01 assembly");ship.setName("NE01 expedition preview");return ship;
        },level.getServer());
    }
    private MarineAssembly(){}
}
