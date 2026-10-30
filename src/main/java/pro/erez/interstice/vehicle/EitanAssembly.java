package pro.erez.interstice.vehicle;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

/** Whole-site and existing-body preflight before any write. No overlapping spawns. */
public final class EitanAssembly {
    public static CompletableFuture<ServerSubLevel> create(ServerLevel level,BlockPos origin){return create(level,origin,null);}
    public static CompletableFuture<ServerSubLevel> create(ServerLevel level,BlockPos origin,UUID owner){
        var occupied=new AABB(origin).inflate(0).expandTowards(0,3,0).inflate(2,0,5);
        var container=SubLevelContainer.getContainer(level);
        if(container!=null)for(var body:container.getAllSubLevels()){var b=body.getPlot().getBoundingBox();AABB world=null;for(double x:new double[]{b.minX(),b.maxX()+1.})for(double y:new double[]{b.minY(),b.maxY()+1.})for(double z:new double[]{b.minZ(),b.maxZ()+1.}){var p=body.logicalPose().transformPosition(new Vector3d(x,y,z));var point=new AABB(p.x,p.y,p.z,p.x,p.y,p.z);world=world==null?point:world.minmax(point);}if(world!=null&&world.intersects(occupied))throw new IllegalArgumentException("Eitan overlaps an existing vehicle");}
        if(!level.getEntities(null,occupied).isEmpty())throw new IllegalArgumentException("Eitan site contains entities");
        for(var c:EitanData.CELLS){if(deferred(c))continue;var p=origin.offset(c.pos());if(p.getY()<level.getMinBuildHeight()||p.getY()>=level.getMaxBuildHeight()||!level.hasChunkAt(p)||!level.getBlockState(p).isAir()||level.getBlockEntity(p)!=null)throw new IllegalArgumentException("Clear loaded Eitan site required: "+p);}
        var positions=new ArrayList<BlockPos>();var chunks=new HashSet<net.minecraft.world.level.ChunkPos>();
        try{for(var c:EitanData.CELLS){if(deferred(c))continue;var p=origin.offset(c.pos());if(!level.setBlock(p,EitanVehicles.state(c),3))throw new IllegalStateException("Eitan block placement rejected");positions.add(p);chunks.add(new net.minecraft.world.level.ChunkPos(p));}
            if(!(level.getBlockEntity(origin.offset(EitanData.CONTROL)) instanceof EitanController controller))throw new IllegalStateException("Eitan controller missing");if(owner!=null)controller.security.claim(owner);controller.fuelTicks=12800;controller.setChanged();
        }catch(RuntimeException e){rollback(level,positions);throw e;}
        var barrier=chunks.stream().map(p->level.getChunkSource().getLightEngine().waitForPendingTasks(p.x,p.z)).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(barrier).thenApplyAsync(unused->{
            try{int x0=positions.stream().mapToInt(BlockPos::getX).min().orElseThrow(),y0=positions.stream().mapToInt(BlockPos::getY).min().orElseThrow(),z0=positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow();int x1=positions.stream().mapToInt(BlockPos::getX).max().orElseThrow(),y1=positions.stream().mapToInt(BlockPos::getY).max().orElseThrow(),z1=positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow();var body=SubLevelAssemblyHelper.assembleBlocks(level,origin,positions,new BoundingBox3i(x0,y0,z0,x1,y1,z1));if(body==null)throw new IllegalStateException("Sable rejected Eitan chassis");body.setName("Эйтан E01");
                var b=body.getPlot().getBoundingBox();BlockPos moved=null;for(int x=b.minX();x<=b.maxX()&&moved==null;x++)for(int y=b.minY();y<=b.maxY()&&moved==null;y++)for(int z=b.minZ();z<=b.maxZ()&&moved==null;z++)if(level.getBlockEntity(new BlockPos(x,y,z)) instanceof EitanController c)moved=c.origin();if(moved==null)throw new IllegalStateException("Relocated Eitan controller missing");
                // Swept ramp clearance extends a few centimetres below the nominal
                // ground plane. Its empty closed cells are installed in storage,
                // never by replacing real terrain underneath the source vehicle.
                for(var c:EitanData.CELLS)if(deferred(c))level.setBlock(moved.offset(c.pos()),EitanVehicles.state(c),3);
                return body;}catch(RuntimeException e){rollback(level,positions);throw e;}
        },level.getServer());
    }
    private static void rollback(ServerLevel level,List<BlockPos> positions){for(var p:positions){var b=level.getBlockState(p).getBlock();if(b==EitanVehicles.PART.get()||b==EitanVehicles.CONTROL.get()||b==EitanVehicles.WHEEL.get())level.setBlock(p,Blocks.AIR.defaultBlockState(),3);}}
    private static boolean deferred(EitanData.Cell c){return c.pos().getY()<0&&c.boxes().isEmpty()&&c.groups().stream().allMatch(g->g.states().getFirst().isEmpty());}
    private EitanAssembly(){}
}
