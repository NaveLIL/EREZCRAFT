package pro.erez.interstice.ship;
import java.util.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class SkatAssembly {
    /** Preflight every cell before writing; no terrain, fluids, inventories or existing ships replaced. */
    public static java.util.concurrent.CompletableFuture<ServerSubLevel> create(ServerLevel level,BlockPos origin){
        return create(level,origin,unused->{});
    }
    public static java.util.concurrent.CompletableFuture<ServerSubLevel> create(ServerLevel level,BlockPos origin,UUID owner){return create(level,origin,o->{if(owner!=null&&level.getBlockEntity(o.offset(0,4,-6)) instanceof SkatHelmEntity h){h.security.claim(owner);h.setChanged();}});}
    public static java.util.concurrent.CompletableFuture<ServerSubLevel> create(ServerLevel level,BlockPos origin,java.util.function.Consumer<BlockPos> beforeAssembly){
        if(origin.getY()<level.getMinBuildHeight()+1||origin.getY()+9>=level.getMaxBuildHeight())throw new IllegalArgumentException("Ship exceeds build height");
        var bounds=new net.minecraft.world.phys.AABB(origin).inflate(6,0,13).expandTowards(0,10,0);var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if(container!=null)for(var body:container.getAllSubLevels()){var b=body.getPlot().getBoundingBox();net.minecraft.world.phys.AABB box=null;for(double x:new double[]{b.minX(),b.maxX()+1.})for(double y:new double[]{b.minY(),b.maxY()+1.})for(double z:new double[]{b.minZ(),b.maxZ()+1.}){var point=body.logicalPose().transformPosition(new org.joml.Vector3d(x,y,z));var a=new net.minecraft.world.phys.AABB(point.x,point.y,point.z,point.x,point.y,point.z);box=box==null?a:box.minmax(a);}if(box!=null&&box.intersects(bounds))throw new IllegalArgumentException("Ship overlaps an existing vehicle");}
        for(var c:SkatData.CELLS){var p=origin.offset(c.pos());if(!level.hasChunkAt(p)||!level.getBlockState(p).isAir()||level.getBlockEntity(p)!=null)throw new IllegalArgumentException("Ship needs a clear, loaded drydock: "+p);}
        var positions=new ArrayList<BlockPos>();
        for(var c:SkatData.CELLS){var p=origin.offset(c.pos());level.setBlock(p,SkatShips.state(c),3);positions.add(p);}
        beforeAssembly.accept(origin);
        int minX=positions.stream().mapToInt(BlockPos::getX).min().orElseThrow(),minY=positions.stream().mapToInt(BlockPos::getY).min().orElseThrow(),minZ=positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxX=positions.stream().mapToInt(BlockPos::getX).max().orElseThrow(),maxY=positions.stream().mapToInt(BlockPos::getY).max().orElseThrow(),maxZ=positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        // Source lighting is asynchronous. Moving emissive sections before their queued
        // propagation completes can leave a vanilla light task referencing a removed layer.
        // Await actual light-queue barriers without blocking the server thread.
        var chunks=new HashSet<net.minecraft.world.level.ChunkPos>();
        for(var p:positions)chunks.add(new net.minecraft.world.level.ChunkPos(p));
        var barriers=chunks.stream().map(p->level.getChunkSource().getLightEngine().waitForPendingTasks(p.x,p.z)).toArray(java.util.concurrent.CompletableFuture[]::new);
        return java.util.concurrent.CompletableFuture.allOf(barriers).thenApplyAsync(unused->{
            var ship=SubLevelAssemblyHelper.assembleBlocks(level,origin,positions,new BoundingBox3i(minX,minY,minZ,maxX,maxY,maxZ));
            if(ship==null)throw new IllegalStateException("Native Sable assembly rejected SK03");ship.setName("Скат SK04");return ship;
        },level.getServer());
    }
    private SkatAssembly(){}
}
