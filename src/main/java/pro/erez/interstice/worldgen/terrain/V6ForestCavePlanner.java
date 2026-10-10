package pro.erez.interstice.worldgen.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Small connected networks inside low grounded forests. This is a block carver AFTER the
 * native 4x4x4 interpolator: narrow stairs cannot reliably survive corner interpolation.
 * Both NOISE writes and public columns use this one immutable seed-local plan. Planning
 * reads only the pre-existing native field, never chunks, decorations or other cave plans.
 */
public final class V6ForestCavePlanner {
    public static final int CELL_SIZE=128, CACHE_LIMIT=256, ATTEMPTS=4, MAX_BRANCH=28, MAX_ENTRANCE=36;
    private static final int[][] DIRECTIONS={{1,0},{0,1},{-1,0},{0,-1}};
    public record Network(BlockPos hub,List<BlockPos> entrance,List<List<BlockPos>> branches,
                          Set<Long> removed,Set<Long> surfaceOpenings,Set<Long> walkway,Set<Long> surfaceColumns) {}
    private final NativeColumnSamplerV6 sampler;
    private final TensionRealmDensity root;
    private final LinkedHashMap<Long,List<Network>> cache=new LinkedHashMap<>(32,.75F,true);
    V6ForestCavePlanner(NativeColumnSamplerV6 sampler,TensionRealmDensity root){this.sampler=sampler;this.root=root;}
    private static double unit(long key){return (FreeTerraNoise.mix(key)>>>11)*0x1.0p-53;}
    public synchronized List<Network> plansInCell(int cx,int cz){
        long key=((long)cx<<32)^(cz&0xffffffffL);var existing=cache.get(key);if(existing!=null)return existing;
        var result=plan(cx,cz);cache.put(key,result);if(cache.size()>CACHE_LIMIT)cache.remove(cache.keySet().iterator().next());return result;
    }
    public synchronized int cachedCells(){return cache.size();}
    public boolean removes(int x,int y,int z){
        if(y<=root.dryCaveCorner()||y>80)return false;
        for(var network:plansInCell(Math.floorDiv(x,CELL_SIZE),Math.floorDiv(z,CELL_SIZE)))
            if(network.removed.contains(BlockPos.asLong(x,y,z)))return true;
        return false;
    }
    public boolean reservedWalkway(BlockPos p){
        if(p.getY()<=root.dryCaveCorner()||p.getY()>80)return false;
        for(var network:plansInCell(Math.floorDiv(p.getX(),CELL_SIZE),Math.floorDiv(p.getZ(),CELL_SIZE)))if(network.walkway.contains(p.asLong()))return true;
        return false;
    }
    public boolean entranceColumn(int x,int z){
        for(var network:plansInCell(Math.floorDiv(x,CELL_SIZE),Math.floorDiv(z,CELL_SIZE)))if(network.surfaceColumns.contains(BlockPos.asLong(x,0,z)))return true;
        return false;
    }
    public void carve(ChunkAccess chunk){
        int x0=chunk.getPos().getMinBlockX(),z0=chunk.getPos().getMinBlockZ();
        for(int cx=Math.floorDiv(x0,CELL_SIZE);cx<=Math.floorDiv(x0+15,CELL_SIZE);cx++)
            for(int cz=Math.floorDiv(z0,CELL_SIZE);cz<=Math.floorDiv(z0+15,CELL_SIZE);cz++)
                for(var network:plansInCell(cx,cz))for(long packed:network.removed){
                    var p=BlockPos.of(packed);if(p.getX()<x0||p.getX()>x0+15||p.getZ()<z0||p.getZ()>z0+15)continue;
                    if(!chunk.getBlockState(p).isAir())chunk.setBlockState(p,Blocks.AIR.defaultBlockState(),false);
                }
    }
    private boolean rock(int x,int y,int z){return sampler.nativeDensity(x,y,z,false)>0;}
    private boolean originalRock(int x,int y,int z){return sampler.nativeDensity(x,y,z,true)>0;}
    private final class Probe {
        final Map<Long,Integer> tops=new HashMap<>();
        int top(int x,int z){return tops.computeIfAbsent(((long)x<<32)^(z&0xffffffffL),ignored->{
            // Uncarved uppermost mass: never choose an underground terrace as the surface.
            for(int y=root.geometry().maxLand();y>root.dryCaveCorner();y--)if(originalRock(x,y,z))return y;
            return root.dryCaveCorner();
        });}
        boolean floor(BlockPos p){return p.getY()>root.dryCaveCorner()+1&&rock(p.getX(),p.getY()-1,p.getZ())&&rock(p.getX(),p.getY()-2,p.getZ());}
        boolean internal(BlockPos p){
            if(!floor(p)||top(p.getX(),p.getZ())>72)return false;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                int x=p.getX()+dx,z=p.getZ()+dz,y=p.getY();
                // Full 3-wide dry gallery, two original roof blocks and two floor blocks.
                if(!rock(x,y-1,z)||!rock(x,y-2,z)||!originalRock(x,y+3,z)||!originalRock(x,y+4,z))return false;
                for(int dy=0;dy<3;dy++)if(!originalRock(x,y+dy,z))return false;
            }
            return true;
        }
    }
    private List<Network> plan(int cx,int cz){
        var probe=new Probe();long salt=Double.doubleToLongBits(root.rooms().getValue(cx*19.17+7,0,cz*27.31-13));
        for(int attempt=0;attempt<ATTEMPTS;attempt++){
            long key=FreeTerraNoise.mix(salt^attempt*0x9e3779b97f4a7c15L^0x5636464f52455354L);
            int x=cx*CELL_SIZE+40+(int)(unit(key^0x58)*48),z=cz*CELL_SIZE+40+(int)(unit(key^0x5a)*48);
            var w=root.morphology(x,z);if(w.gardens()+w.crimson()<.75)continue;
            int top=probe.top(x,z);if(top<root.dryCaveCorner()+7||top>72)continue;
            var hub=new BlockPos(x,Math.max(root.dryCaveCorner()+2,top-8),z);if(!probe.internal(hub))continue;
            var branches=new ArrayList<List<BlockPos>>();int offset=(int)(key&3);
            for(int i=0;i<3;i++){
                var path=new ArrayList<BlockPos>();path.add(hub);var at=hub;int direction=(offset+i)&3;
                int length=18+(int)(unit(key^(i+1)*0x4252414e4348L)*(MAX_BRANCH-18+1));
                int bendSpacing=5+(int)(unit(key^i^0x42454e44L)*5);
                for(int step=0;step<length;step++){
                    int turn=(step%bendSpacing==bendSpacing-2)?((unit(key^step^i)>.5)?1:3):0;
                    var d=DIRECTIONS[(direction+turn)&3];var next=at.offset(d[0],0,d[1]);
                    if(!inside(cx,cz,next)||path.contains(next)||!probe.internal(next))break;
                    path.add(next);at=next;
                }
                if(path.size()>=13)branches.add(List.copyOf(path));
            }
            if(branches.size()<2)continue;
            for(int i=0;i<4;i++){
                var entry=entrance(probe,cx,cz,hub,(offset+3+i)&3);if(entry.isEmpty())continue;
                var removed=new HashSet<Long>();var floors=new HashSet<Long>();var openings=new HashSet<Long>();
                for(var path:branches)for(var p:path)addSection(p,probe,false,removed,floors,openings);
                for(var p:entry)addSection(p,probe,true,removed,floors,openings);
                removed.removeAll(floors);
                // Existing traversable cave/mouth floors are never broken by a new network.
                removed.removeIf(p->{var at=BlockPos.of(p);return rock(at.getX(),at.getY(),at.getZ())
                        &&originalRock(at.getX(),at.getY()+2,at.getZ())
                        &&!rock(at.getX(),at.getY()+1,at.getZ())&&!rock(at.getX(),at.getY()+2,at.getZ());});
                if(openings.size()>48||!validate(entry,removed))continue;
                boolean valid=true;for(var path:branches)if(!validate(path,removed)){valid=false;break;}
                if(valid){
                    var walkway=new HashSet<Long>();var surfaceColumns=new HashSet<Long>();
                    var paths=new ArrayList<List<BlockPos>>(branches);paths.add(entry);
                    for(var path:paths)for(var p:path)for(int dy=0;dy<3;dy++)walkway.add(p.above(dy).asLong());
                    for(long packed:openings){var p=BlockPos.of(packed);surfaceColumns.add(BlockPos.asLong(p.getX(),0,p.getZ()));}
                    return List.of(new Network(hub,List.copyOf(entry),List.copyOf(branches),Set.copyOf(removed),Set.copyOf(openings),Set.copyOf(walkway),Set.copyOf(surfaceColumns)));
                }
            }
        }
        return List.of();
    }
    private static boolean inside(int cx,int cz,BlockPos p){return p.getX()>cx*CELL_SIZE+3&&p.getX()<cx*CELL_SIZE+CELL_SIZE-4
            &&p.getZ()>cz*CELL_SIZE+3&&p.getZ()<cz*CELL_SIZE+CELL_SIZE-4;}
    private List<BlockPos> entrance(Probe probe,int cx,int cz,BlockPos hub,int direction){
        var path=new ArrayList<BlockPos>();path.add(hub);var at=hub;int outside=0;
        for(int step=0;step<MAX_ENTRANCE;step++){
            var d=DIRECTIONS[direction];int x=at.getX()+d[0],z=at.getZ()+d[1],top=probe.top(x,z);
            // Follow a gently ascending bed until it meets the actual hillside. No ladders.
            int desired=Math.min(hub.getY()+step/3+1,top+1),y=Math.max(at.getY()-1,Math.min(at.getY()+1,desired));
            var next=new BlockPos(x,y,z);if(!inside(cx,cz,next)||!probe.floor(next))return List.of();
            int depth=top-y;
            if(depth>=4&&!probe.internal(next))return List.of();
            if(depth<4){
                // A compact sloped lip, never a vertical shaft through the whole forest.
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                    if(!rock(x+dx,y-1,z+dz)||!rock(x+dx,y-2,z+dz))return List.of();
            }
            path.add(next);at=next;
            if(y>top&& !rock(x,y,z)&&!rock(x,y+1,z)){if(++outside>=3)return List.copyOf(path);}else outside=0;
        }
        return List.of();
    }
    private void addSection(BlockPos p,Probe probe,boolean entry,Set<Long> removed,Set<Long> floors,Set<Long> openings){
        floors.add(p.below().asLong());
        long variation=Double.doubleToLongBits(root.rooms().getValue(17,0,-31));
        int radius=!entry&&FreeTerraNoise.fractal(variation,0x56365749445448L,p.getX(),p.getZ(),13,2)>.46?2:1;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(dx*dx+dz*dz>radius*radius+1)continue;
            if((Math.abs(dx)>1||Math.abs(dz)>1)&&!probe.internal(p.offset(dx,0,dz)))continue;
            int x=p.getX()+dx,z=p.getZ()+dz,y=p.getY();
            // A raised central seam and lower shoulders make an irregular arched section,
            // rather than the flat ceiling and rectangular walls of an excavated mine.
            int height=!entry&&(dx!=0||dz!=0)?2:3;
            if(!entry&&dx==0&&dz==0&&FreeTerraNoise.fractal(variation,0x5636484549474854L,x,z,17,2)>.62&&probe.internal(p.above()))height=4;
            for(int dy=0;dy<height;dy++){
                long at=BlockPos.asLong(x,y+dy,z);if(rock(x,y+dy,z))removed.add(at);
                if(entry&&y+dy>=probe.top(x,z)&&rock(x,y+dy,z))openings.add(at);
            }
        }
    }
    private boolean solid(BlockPos p,Set<Long> removed){return !removed.contains(p.asLong())&&rock(p.getX(),p.getY(),p.getZ());}
    private boolean validate(List<BlockPos> route,Set<Long> removed){
        BlockPos previous=null;
        for(var p:route){
            if(!solid(p.below(),removed)||solid(p,removed)||solid(p.above(),removed))return false;
            if(previous!=null){int dy=p.getY()-previous.getY();
                if(Math.abs(dy)>1||p.distManhattan(previous)!=1+Math.abs(dy))return false;
                if(dy!=0&&solid((dy>0?previous:p).above(2),removed))return false;
            }previous=p;
        }
        return true;
    }
}
