package pro.erez.interstice.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.terrain.V6ForestCavePlanner;

/** Independent bounded walking graph on naturally decorated FULL blocks. Never edits terrain. */
public final class ForestCaveInspection {
    public static boolean clear(ServerLevel level,BlockPos p){
        return level.getFluidState(p).isEmpty()&&level.getBlockState(p).getCollisionShape(level,p).isEmpty();
    }
    private static boolean standing(ServerLevel level,BlockPos p){
        return clear(level,p)&&clear(level,p.above())&&level.getFluidState(p.below()).isEmpty()
                &&level.getBlockState(p.below()).isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP);
    }
    public static List<ChunkPos> chunks(V6ForestCavePlanner.Network network){
        var positions=new java.util.TreeSet<ChunkPos>(java.util.Comparator.comparingLong(ChunkPos::toLong));
        for(long packed:network.removed())positions.add(new ChunkPos(BlockPos.of(packed)));
        for(var p:network.entrance())positions.add(new ChunkPos(p));
        return List.copyOf(positions);
    }
    public static JsonObject inspect(ServerLevel level,V6ForestCavePlanner.Network network){
        require(level.dimension().equals(IslandWorld.TENSION_WORLD)&&((IslandChunkGenerator)level.getChunkSource().getGenerator()).terrainRevision()==6,"Inspection did not enter actual V6");
        boolean component=level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer;
        var dh=net.neoforged.fml.ModList.get().getModContainerById("distanthorizons");
        if(!component){require(dh.isPresent(),"Mandatory Distant Horizons is not loaded in real game");require(dh.get().getModInfo().getVersion().toString().equals("3.3.3"),"Unexpected Distant Horizons version");}
        var chunks=chunks(network);require(chunks.size()<=16,"Exceeded fixed16 FULL chunks per network");
        for(var pos:chunks)require(level.getChunk(pos.x,pos.z).getPersistedStatus().isOrAfter(ChunkStatus.FULL),"Inspection received a non-FULL chunk");
        var paths=new ArrayList<List<BlockPos>>(network.branches());paths.add(network.entrance());int routeNodes=0;
        int x0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,z0=Integer.MAX_VALUE,z1=Integer.MIN_VALUE,y0=Integer.MAX_VALUE,y1=Integer.MIN_VALUE;
        for(var path:paths){BlockPos previous=null;for(var p:path){
            require(standing(level,p),"FULL blocked route at "+p+" feet="+level.getBlockState(p)+" head="+level.getBlockState(p.above()));
            if(previous!=null&&previous.getY()!=p.getY())require(clear(level,(p.getY()>previous.getY()?previous:p).above(2)),"FULL obstructed stair sweep");
            x0=Math.min(x0,p.getX()-2);x1=Math.max(x1,p.getX()+2);z0=Math.min(z0,p.getZ()-2);z1=Math.max(z1,p.getZ()+2);y0=Math.min(y0,p.getY()-1);y1=Math.max(y1,p.getY()+1);
            previous=p;routeNodes++;
        }}
        var loaded=new HashSet<Long>();for(var p:chunks)loaded.add(p.toLong());
        var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();queue.add(network.hub());seen.add(network.hub());
        while(!queue.isEmpty()){
            var at=queue.remove();
            for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy=-1;dy<=1;dy++){
                var next=at.offset(direction[0],dy,direction[1]);
                if(next.getX()<x0||next.getX()>x1||next.getZ()<z0||next.getZ()>z1||next.getY()<y0||next.getY()>y1||!loaded.contains(new ChunkPos(next).toLong())||seen.contains(next))continue;
                if(!standing(level,next)||dy!=0&&!clear(level,(dy>0?at:next).above(2)))continue;
                seen.add(next);queue.add(next);
            }
        }
        int reached=0;for(var path:network.branches()){require(seen.contains(path.getLast()),"Independent FULL graph disconnected a branch endpoint");reached++;}
        var exterior=network.entrance().getLast();require(seen.contains(exterior),"Independent FULL graph could not reach the surface");
        var row=new JsonObject();row.addProperty("seed",level.getSeed());row.addProperty("dimension",level.dimension().location().toString());row.addProperty("hub",network.hub().toShortString());
        row.addProperty("surface_entry",exterior.toShortString());row.addProperty("full_chunks",chunks.size());row.addProperty("planned_route_nodes",routeNodes);row.addProperty("independent_bfs_nodes",seen.size());row.addProperty("branch_endpoints_reached",reached);row.addProperty("surface_reached",true);
        row.addProperty("distant_horizons_loaded",dh.isPresent());row.addProperty("distant_horizons_version",dh.map(mod->mod.getModInfo().getVersion().toString()).orElse("absent_component_test"));
        row.addProperty("scope",component?"Interstice component GameTestServer; DH integration tested in real clients/dedicated only":"Actual assembled pack with Distant Horizons");
        var routes=new JsonArray();for(var path:paths){var route=new JsonArray();for(var p:path){var node=new JsonObject();node.addProperty("x",p.getX());node.addProperty("y",p.getY());node.addProperty("z",p.getZ());route.add(node);}routes.add(route);}row.add("routes",routes);row.addProperty("passed",true);return row;
    }
    public static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
}
