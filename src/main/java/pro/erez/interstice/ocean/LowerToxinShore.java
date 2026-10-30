package pro.erez.interstice.ocean;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import pro.erez.interstice.Interstice;
/** Four-block bank fade, rebuilt on terrain changes, never on the wave animation tick. */
@EventBusSubscriber(modid=Interstice.ID)
public final class LowerToxinShore {
    public static final int WIDTH=4,MAX_TILES=512;
    private static final Map<Level,LinkedHashMap<Long,float[]>> WORLDS=Collections.synchronizedMap(new WeakHashMap<>());
    public static long builds;
    public static void invalidate(Level w,int cx,int cz){var m=WORLDS.get(w);if(m!=null)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)m.remove(ChunkPos.asLong(cx+x,cz+z));}
    public static void block(Level w,BlockPos p){if(LowerToxinSurface.active(w)&&Math.abs(p.getY()-pro.erez.interstice.geometry.GeometryProfiles.get(w).lowerSeaTop())<=3)invalidate(w,p.getX()>>4,p.getZ()>>4);}
    @SubscribeEvent public static void load(ChunkEvent.Load e){if(e.getLevel() instanceof Level w)invalidate(w,e.getChunk().getPos().x,e.getChunk().getPos().z);}
    @SubscribeEvent public static void unload(ChunkEvent.Unload e){if(e.getLevel() instanceof Level w)invalidate(w,e.getChunk().getPos().x,e.getChunk().getPos().z);}
    public static double corner(Level w,int x,int z){
        if(!LowerToxinSurface.active(w))return 0;var m=WORLDS.computeIfAbsent(w,k->new LinkedHashMap<>(32,.75f,true));long key=ChunkPos.asLong(x>>4,z>>4);var t=m.get(key);
        if(t==null){t=build(w,x>>4,z>>4);m.put(key,t);while(m.size()>MAX_TILES)m.remove(m.keySet().iterator().next());}return t[Math.floorMod(z,16)*17+Math.floorMod(x,16)];
    }
    private static float[] build(Level w,int cx,int cz){
        int n=16+2*WIDTH+2;boolean[] cell=new boolean[n*n];int ox=cx*16-WIDTH-1,oz=cz*16-WIDTH-1;
        for(int z=0;z<n;z++)for(int x=0;x<n;x++)cell[z*n+x]=LowerToxinSurface.shoreCell(w,ox+x,oz+z);
        boolean[] open=new boolean[(n-1)*(n-1)];for(int z=0;z<n-1;z++)for(int x=0;x<n-1;x++)open[z*(n-1)+x]=cell[z*n+x]&&cell[z*n+x+1]&&cell[(z+1)*n+x]&&cell[(z+1)*n+x+1];
        float[] out=new float[17*17];for(int z=0;z<=16;z++)for(int x=0;x<=16;x++){int ax=x+WIDTH,az=z+WIDTH;double distance=WIDTH;
            for(int dz=-WIDTH;dz<=WIDTH;dz++)for(int dx=-WIDTH;dx<=WIDTH;dx++){int xx=ax+dx,zz=az+dz;if(xx<0||zz<0||xx>=n-1||zz>=n-1)continue;if(!open[zz*(n-1)+xx])distance=Math.min(distance,Math.sqrt(dx*dx+dz*dz));}
            double t=distance/WIDTH;out[z*17+x]=(float)(t*t*(3-2*t));
        }builds++;return out;
    }
    private LowerToxinShore(){}
}
