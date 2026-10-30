package pro.erez.interstice.ocean;
import java.util.BitSet;
/** Empty cells sealed against lateral/bottom entry at their own water level. Open tops remain open. */
public final class HullSeal {
    public static BitSet enclosed(boolean[] solid,int nx,int ny,int nz){
        return enclosed(spillLevels(solid,nx,ny,nz),nx,ny,nz);
    }
    /** Minimum outside water height needed to reach each air cell. Solid=-1; sealed=127. */
    public static byte[] spillLevels(boolean[] solid,int nx,int ny,int nz){
        if(nx<1||ny<1||nz<1||solid.length!=nx*ny*nz||solid.length>262144||ny>32)throw new IllegalArgumentException("Bounded hull grid required");
        byte[] spill=new byte[solid.length];java.util.Arrays.fill(spill,(byte)127);int plane=nx*nz;
        var buckets=new it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue[ny+1];for(int i=0;i<=ny;i++)buckets[i]=new it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue();
        for(int y=0;y<ny;y++)for(int z=0;z<nz;z++)for(int x=0;x<nx;x++){int p=y*plane+z*nx+x;if(solid[p]){spill[p]=-1;continue;}
            if(y==0||x==0||z==0||x==nx-1||z==nz-1){spill[p]=(byte)y;buckets[y].enqueue(p);}else if(y==ny-1){spill[p]=(byte)ny;buckets[ny].enqueue(p);}}
        for(int cap=0;cap<=ny;cap++)while(!buckets[cap].isEmpty()){
            int p=buckets[cap].dequeueInt();if(spill[p]!=cap)continue;int y=p/plane,z=(p%plane)/nx,x=p%nx;
            if(x>0)relax(p-1,cap,spill,buckets);if(x+1<nx)relax(p+1,cap,spill,buckets);if(z>0)relax(p-nx,cap,spill,buckets);if(z+1<nz)relax(p+nx,cap,spill,buckets);
            if(y>0)relax(p-plane,cap,spill,buckets);if(y+1<ny)relax(p+plane,Math.max(cap,y+1),spill,buckets);
        }return spill;
    }
    private static void relax(int p,int cap,byte[] spill,it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue[] buckets){if(spill[p]>=0&&cap<spill[p]){spill[p]=(byte)cap;buckets[cap].enqueue(p);}}
    public static BitSet enclosed(byte[] spill,int nx,int ny,int nz){var result=new BitSet(spill.length);int plane=nx*nz;for(int p=0;p<spill.length;p++)if(spill[p]>p/plane)result.set(p);return result;}
    private HullSeal(){}
}
