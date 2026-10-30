package pro.erez.interstice.ocean;
import net.minecraft.world.phys.AABB;
/** Bounded quarter-block geometry raster, built only when hull blocks change. */
public final class HullRaster {
    public static void fill(boolean[] target,int nx,int ny,int nz,int bx,int by,int bz,int scale,AABB box){
        int x0=Math.max(0,bx*scale+(int)Math.floor(box.minX*scale+1e-7)),x1=Math.min(nx,bx*scale+(int)Math.ceil(box.maxX*scale-1e-7));
        int y0=Math.max(0,by*scale+(int)Math.floor(box.minY*scale+1e-7)),y1=Math.min(ny,by*scale+(int)Math.ceil(box.maxY*scale-1e-7));
        int z0=Math.max(0,bz*scale+(int)Math.floor(box.minZ*scale+1e-7)),z1=Math.min(nz,bz*scale+(int)Math.ceil(box.maxZ*scale-1e-7));
        for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)for(int x=x0;x<x1;x++)target[(y*nz+z)*nx+x]=true;
    }
    private HullRaster(){}
}
