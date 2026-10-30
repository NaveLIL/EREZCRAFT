package pro.erez.interstice.ocean;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.worldgen.IslandWorld;

/** Bounded, deterministic ocean field. No chunk loads, block updates or mutable simulation grid. */
public final class LowerToxinSurface {
    public static final double PERIOD=1920,MAX_AMPLITUDE=.42;
    public static final int DISSOLVE_TICKS=200;
    public record Sample(double height,double dx,double dz,double velocity,double mask){}
    public static boolean active(Level level){return level.dimension().equals(IslandWorld.TENSION_WORLD);}
    public static double base(Level level){return GeometryProfiles.get(level).lowerSeaTop()+8.0/9.0;}
    public static double time(Level level,double partial){return Math.floorMod(level.getGameTime(),1920)+partial;}
    public static Sample wave(double x,double z,double tick,double base,double mask){
        double a=2*Math.PI*(x/64+tick/240),b=2*Math.PI*(z/128-tick/384),c=2*Math.PI*((x+z)/32+tick/640);
        return new Sample(base+mask*(.20*Math.sin(a)+.14*Math.sin(b)+.08*Math.sin(c)),mask*2*Math.PI*(.20*Math.cos(a)/64+.08*Math.cos(c)/32),mask*2*Math.PI*(.14*Math.cos(b)/128+.08*Math.cos(c)/32),mask*2*Math.PI*(.20*Math.cos(a)/240-.14*Math.cos(b)/384+.08*Math.cos(c)/640),mask);
    }
    private static net.minecraft.world.level.chunk.ChunkAccess loaded(Level level,int x,int z){return level instanceof net.minecraft.server.level.ServerLevel server?server.getChunkSource().getChunkNow(x>>4,z>>4):level.getChunkSource().getChunk(x>>4,z>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);}
    /** Missing neighbours are unknown, not a solid bank at the vanilla/DH boundary. */
    public static boolean shoreCell(Level level,int x,int z){return loaded(level,x,z)==null||cell(level,x,z);}
    public static boolean cell(Level level,int x,int z){
        int y=GeometryProfiles.get(level).lowerSeaTop();var p=new BlockPos(x,y,z);
        if(!active(level))return false;var chunk=loaded(level,x,z);
        return chunk!=null&&chunk.getBlockState(p).is(Interstice.HEAVY_BLOCK.get())&&chunk.getFluidState(p).isSource()&&chunk.getBlockState(p.below()).is(Interstice.HEAVY_BLOCK.get())&&chunk.getBlockState(p.above()).isAir()&&chunk.getBlockState(p.above(2)).isAir();
    }
    /** Shared corner masks preserve adjoining triangles and suppress waves at solid banks/low roofs. */
    public static double cornerMask(Level level,int x,int z){
        return LowerToxinShore.corner(level,x,z);
    }
    private record Mask(double value,double dx,double dz){}
    private static Mask gradient(Level level,double x,double z){
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);double fx=x-bx,fz=z-bz;
        double a=cornerMask(level,bx,bz),b=cornerMask(level,bx+1,bz),c=cornerMask(level,bx+1,bz+1),d=cornerMask(level,bx,bz+1);
        return fx>=fz?new Mask(a+fx*(b-a)+fz*(c-b),b-a,c-b):new Mask(a+fz*(d-a)+fx*(c-d),c-d,d-a);
    }
    public static double mask(Level level,double x,double z){return gradient(level,x,z).value;}
    public static Sample sample(Level level,double x,double z,double partial){double base=base(level);var raw=wave(x,z,time(level,partial),base,1);var m=gradient(level,x,z);double offset=raw.height-base;return new Sample(base+offset*m.value,raw.dx*m.value+offset*m.dx,raw.dz*m.value+offset*m.dz,raw.velocity*m.value,m.value);}
    public static double fluidTop(Level level,double x,double y,double z,double partial){
        var p=BlockPos.containing(x,y,z);if(!level.hasChunkAt(p))return Double.NaN;
        var fluid=level.getFluidState(p);if(fluid.getFluidType()!=Interstice.HEAVY_TYPE.get())return Double.NaN;
        if(p.getY()==GeometryProfiles.get(level).lowerSeaTop()&&cell(level,p.getX(),p.getZ()))return sample(level,x,z,partial).height();
        return p.getY()+fluid.getHeight(level,p);
    }
    public static double overlap(Level w,net.minecraft.world.phys.AABB b){
        double base=base(w);if(!active(w)||b.minY>base+MAX_AMPLITUDE||b.maxY<base-MAX_AMPLITUDE-1)return 0;
        double deepest=0;for(double x:new double[]{b.minX+.001,(b.minX+b.maxX)*.5,b.maxX-.001})for(double z:new double[]{b.minZ+.001,(b.minZ+b.maxZ)*.5,b.maxZ-.001}){
            if(!cell(w,(int)Math.floor(x),(int)Math.floor(z)))continue;double top=sample(w,x,z,0).height();if(ToxinHullOcclusion.dry(w,x,Math.min(b.maxY,top)-.001,z))continue;deepest=Math.max(deepest,Math.max(0,Math.min(b.maxY,top)-b.minY));
        }return deepest;
    }
    private LowerToxinSurface(){}
}
