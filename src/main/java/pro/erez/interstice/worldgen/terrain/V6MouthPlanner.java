package pro.erez.interstice.worldgen.terrain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Bounded, seed-owner-local entrances. Plans only subtract short passages from existing rock.
 * Every accepted route is checked against the same native-cell interpolation used by NOISE.
 * Planning probes explicitly exclude mouths; no planner can depend on another pending plan.
 */
public final class V6MouthPlanner {
    public static final int CELL_SIZE=128,ATTEMPTS=8,ENDPOINT_RADIUS=8,MAX_REACH=24,MAX_CONNECTOR_STEPS=32;
    public static final int MAX_VERTICAL_CHANGE=8,MAX_EXTERIOR_STEPS=12,MIN_INTERNAL_NODES=6,CACHE_LIMIT=256;
    public static final int MAX_CORNER_EVALUATIONS=16000;
    public static final double HALF_WIDTH=2.0;
    private static final int[][] DIRECTIONS={{1,0},{0,1},{-1,0},{0,-1}};
    private static final int[][] RAYS={{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
    public record Bounds(int minX,int maxX,int minY,int maxY,int minZ,int maxZ) {
        static Bounds of(List<BlockPos> path){
            int x0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,y0=Integer.MAX_VALUE,y1=Integer.MIN_VALUE,z0=Integer.MAX_VALUE,z1=Integer.MIN_VALUE;
            for(var p:path){x0=Math.min(x0,p.getX());x1=Math.max(x1,p.getX());y0=Math.min(y0,p.getY());y1=Math.max(y1,p.getY());z0=Math.min(z0,p.getZ());z1=Math.max(z1,p.getZ());}
            return new Bounds(x0-4,x1+4,y0-3,y1+5,z0-4,z1+4);
        }
    }
    public record Mouth(int cellX,int cellZ,BlockPos surface,BlockPos entry,BlockPos interior,
                        List<BlockPos> connector,List<BlockPos> route,int originalInternalNodes,Bounds bounds) {
        Mouth(int cx,int cz,BlockPos surface,BlockPos entry,BlockPos interior,List<BlockPos> connector,List<BlockPos> route,int nodes){
            this(cx,cz,surface,entry,interior,connector,route,nodes,Bounds.of(connector));
        }
        /** Preserves existing floor; this operation cannot create rock or a landing platform. */
        double carve(int x,int y,int z,double density){
            if(density<=0||y<bounds.minY||y>bounds.maxY||x<bounds.minX||x>bounds.maxX||z<bounds.minZ||z>bounds.maxZ)return density;
            double opening=-100;boolean floor=false;
            for(int i=1;i<connector.size();i++){
                var a=connector.get(i-1);var b=connector.get(i);double dx=b.getX()-a.getX(),dz=b.getZ()-a.getZ(),length=dx*dx+dz*dz;
                double t=length==0?0:Math.max(0,Math.min(1,((x-a.getX())*dx+(z-a.getZ())*dz)/length));
                double px=a.getX()+dx*t,pz=a.getZ()+dz*t,floorY=a.getY()-1+(b.getY()-a.getY())*t;
                double across=Math.hypot(x-px,z-pz);
                if(across<=HALF_WIDTH+.15&&y<=floorY+.20&&y>=floorY-1.25)floor=true;
                double vertical=Math.min(y-floorY-.15,floorY+4.10-y);
                opening=Math.max(opening,Math.min(HALF_WIDTH-across,vertical));
            }
            // Keep the positive signed-distance collar too. Dropping it and retaining the
            // large base density outside a 4-wide cut collapses the native 4-block cell.
            // Positive collar values remain rock; only negative cutter values remove it.
            return floor?density:Math.min(density,-opening/12.0);
        }
    }
    private record Corner(int x,int y,int z,boolean carved) {}
    private final TensionRealmDensity owner;
    private final LinkedHashMap<Long,List<Mouth>> plans=new LinkedHashMap<>(64,.75F,true);
    private final ConcurrentHashMap<Long,CompletableFuture<List<Mouth>>> pending=new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> planning=ThreadLocal.withInitial(()->false);
    private final AtomicLong computedCells=new AtomicLong(),acceptedCells=new AtomicLong(),exhaustedCells=new AtomicLong();
    private static final class BudgetReached extends RuntimeException {
        @Override public synchronized Throwable fillInStackTrace(){return this;}
    }
    private static final class Budget {int evaluations;}
    V6MouthPlanner(TensionRealmDensity owner){this.owner=owner;}
    public List<Mouth> plansInCell(int cellX,int cellZ){
        if(owner.rooms().noise()==null)return List.of();
        if(planning.get())throw new IllegalStateException("A V6 entrance planner recursively queried mouths");
        long key=((long)cellX<<32)^(cellZ&0xffffffffL);
        synchronized(plans){var found=plans.get(key);if(found!=null)return found;}
        var future=new CompletableFuture<List<Mouth>>();var other=pending.putIfAbsent(key,future);if(other!=null)return other.join();
        planning.set(true);
        try{
            List<Mouth> result;
            try{result=plan(cellX,cellZ);}catch(BudgetReached exhausted){exhaustedCells.incrementAndGet();result=List.of();}
            computedCells.incrementAndGet();if(!result.isEmpty())acceptedCells.incrementAndGet();
            synchronized(plans){plans.put(key,result);if(plans.size()>CACHE_LIMIT)plans.remove(plans.keySet().iterator().next());}
            future.complete(result);return result;
        }catch(RuntimeException|Error failure){future.completeExceptionally(failure);throw failure;}
        finally{planning.set(false);pending.remove(key,future);}
    }
    public int cachedCells(){synchronized(plans){return plans.size();}}
    public long computedCells(){return computedCells.get();}
    public long acceptedCells(){return acceptedCells.get();}
    public long exhaustedCells(){return exhaustedCells.get();}
    public double carve(int x,int y,int z,double density){
        if(density<=0||y<=owner.dryCaveCorner())return density;
        for(var mouth:plansInCell(Math.floorDiv(x,CELL_SIZE),Math.floorDiv(z,CELL_SIZE)))density=mouth.carve(x,y,z,density);
        return density;
    }
    private static double unit(long key){return (FreeTerraNoise.mix(key)>>>11)*0x1.0p-53;}
    private List<Mouth> plan(int cellX,int cellZ){
        var sampler=new Sampler(null,new Budget());long salt=Double.doubleToLongBits(owner.rooms().getValue(cellX*17.719+43,0,cellZ*31.411-9));
        for(int attempt=0;attempt<ATTEMPTS;attempt++){
            long key=FreeTerraNoise.mix(salt^attempt*0x9e3779b97f4a7c15L^0x56364d4f555448L);
            int ax=cellX*CELL_SIZE+16+(int)(unit(key^0x58L)*96),az=cellZ*CELL_SIZE+16+(int)(unit(key^0x5aL)*96);
            // Cheap coherent ridge tests precede the complete canonical standing-node probes.
            for(int dx=-ENDPOINT_RADIUS;dx<=ENDPOINT_RADIUS;dx+=4)for(int dz=-ENDPOINT_RADIUS;dz<=ENDPOINT_RADIUS;dz+=4){
                int x=ax+dx,z=az+dz;if(!inside(cellX,cellZ,x,z))continue;var shape=owner.shape(x,z);
                if(shape.tunnelDistanceA()>-.20&&shape.tunnelDistanceB()>-.20&&shape.chambers().isEmpty())continue;
                for(double band:new double[]{shape.caveCenter(),shape.secondCaveCenter()})for(int dy=-5;dy<=2;dy++){
                    var interior=new BlockPos(x,(int)Math.round(band)+dy,z);
                    if(!sampler.internal(interior))continue;int component=internalNodes(sampler,interior);if(component<MIN_INTERNAL_NODES)continue;
                    var mouth=findEntry(sampler,cellX,cellZ,interior,key,component);if(mouth!=null)return List.of(mouth);
                }
            }
        }return List.of();
    }
    private Mouth findEntry(Sampler sampler,int cx,int cz,BlockPos interior,long key,int component){
        int offset=(int)Math.floorMod(key,8);
        for(int distance=4;distance<=MAX_REACH;distance+=4)for(int ray=0;ray<RAYS.length;ray++){
            var d=RAYS[(ray+offset)%RAYS.length];double norm=Math.hypot(d[0],d[1]);
            int x=interior.getX()+(int)Math.round(d[0]*distance/norm),z=interior.getZ()+(int)Math.round(d[1]*distance/norm);
            if(!inside(cx,cz,x,z))continue;
            for(int delta=0;delta<=MAX_VERTICAL_CHANGE;delta++)for(int sign:delta==0?new int[]{1}:new int[]{-1,1}){
                var entry=new BlockPos(x,interior.getY()+delta*sign,z);if(!sampler.exterior(entry))continue;
                var exterior=surfaceRoute(sampler,entry,d,cx,cz);if(exterior.isEmpty())continue;
                var connector=connector(entry,interior,(unit(key^0x42454e44L)-.5)*4,key);
                if(connector.size()<5||connector.size()>MAX_CONNECTOR_STEPS+1||connector.stream().anyMatch(p->!inside(cx,cz,p.getX(),p.getZ())))continue;
                var route=new ArrayList<BlockPos>();for(int i=exterior.size()-1;i>=0;i--)route.add(exterior.get(i));
                route.addAll(connector.subList(1,connector.size()));
                var candidate=new Mouth(cx,cz,route.getFirst(),entry,interior,List.copyOf(connector),List.copyOf(route),component);
                if(validate(sampler,candidate))return candidate;
            }
        }return null;
    }
    private static boolean inside(int cx,int cz,int x,int z){return x>=cx*CELL_SIZE+6&&x<=cx*CELL_SIZE+CELL_SIZE-7&&z>=cz*CELL_SIZE+6&&z<=cz*CELL_SIZE+CELL_SIZE-7;}
    private List<BlockPos> surfaceRoute(Sampler sampler,BlockPos entry,int[] outward,int cx,int cz){
        var result=new ArrayList<BlockPos>();result.add(entry);var at=entry;
        if(sampler.sky(entry))return List.copyOf(result);
        for(int step=0;step<MAX_EXTERIOR_STEPS;step++){
            // Diagonal rays alternate cardinal moves so every recorded edge is a real player step.
            int dx=outward[0],dz=outward[1];if(dx!=0&&dz!=0){if((step&1)==0)dz=0;else dx=0;}
            BlockPos next=null;
            for(int dy:new int[]{0,-1,1}){var proposed=at.offset(dx,dy,dz);
                if(inside(cx,cz,proposed.getX(),proposed.getZ())&&sampler.exterior(proposed)&&sampler.edge(at,proposed,false)){next=proposed;break;}}
            if(next==null)break;result.add(next);at=next;if(sampler.sky(at))return List.copyOf(result);
        }return List.of();
    }
    private static List<BlockPos> connector(BlockPos start,BlockPos end,double bend,long key){
        double dx=end.getX()-start.getX(),dz=end.getZ()-start.getZ(),length=Math.hypot(dx,dz);int samples=Math.max(1,(int)Math.ceil(length*2));
        var route=new ArrayList<BlockPos>();route.add(start);
        for(int step=1;step<=samples;step++){
            double t=step/(double)samples;int x=(int)Math.round(start.getX()+dx*t-dz/length*Math.sin(Math.PI*t)*bend);
            int z=(int)Math.round(start.getZ()+dz*t+dx/length*Math.sin(Math.PI*t)*bend);
            int y=(int)Math.round(start.getY()+(end.getY()-start.getY())*t);var previous=route.getLast();
            if(x==previous.getX()&&z==previous.getZ())continue;
            if(x!=previous.getX()&&z!=previous.getZ()){
                boolean xFirst=(key&1)==0;route.add(new BlockPos(xFirst?x:previous.getX(),previous.getY(),xFirst?previous.getZ():z));
            }
            route.add(new BlockPos(x,y,z));
        }
        if(!route.getLast().equals(end))route.add(end);return List.copyOf(route);
    }
    private int internalNodes(Sampler sampler,BlockPos start){
        var queue=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();queue.add(start);seen.add(start);
        while(!queue.isEmpty()&&seen.size()<MIN_INTERNAL_NODES){var at=queue.remove();
            for(var d:DIRECTIONS)for(int dy=-1;dy<=1;dy++){var next=at.offset(d[0],dy,d[1]);
                if(Math.abs(next.getX()-start.getX())>8||Math.abs(next.getZ()-start.getZ())>8||Math.abs(next.getY()-start.getY())>3||seen.contains(next))continue;
                if(sampler.internal(next)&&sampler.edge(at,next,true)){seen.add(next);queue.add(next);}}
        }return seen.size();
    }
    private boolean validate(Sampler original,Mouth candidate){
        var withMouth=new Sampler(candidate,original.budget);int newTunnelNodes=0;
        for(int i=0;i<candidate.route.size();i++){
            var point=candidate.route.get(i);if(!withMouth.standing(point,true))return false;
            if(i>0&&!withMouth.edge(candidate.route.get(i-1),point,true))return false;
            if(original.density(point.getX(),point.getY(),point.getZ(),true)>0)newTunnelNodes++;
        }
        return newTunnelNodes>=4&&original.internal(candidate.interior)&&original.sky(candidate.surface);
    }
    private final class Sampler {
        final Mouth candidate;
        final LinkedHashMap<Corner,Double> corners=new LinkedHashMap<>(1024,.75F,true);
        final Map<Long,Integer> tops=new LinkedHashMap<>();
        final Budget budget;
        Sampler(Mouth candidate,Budget budget){this.candidate=candidate;this.budget=budget;}
        double corner(int x,int y,int z,boolean carved){
            var key=new Corner(x,y,z,carved);var found=corners.get(key);if(found!=null)return found;
            if(++budget.evaluations>MAX_CORNER_EVALUATIONS)throw new BudgetReached();
            double value=owner.densityWithoutMouths(x,y,z,carved);
            if(carved&&candidate!=null&&y>owner.dryCaveCorner())value=candidate.carve(x,y,z,value);
            corners.put(key,value);if(corners.size()>16384)corners.remove(corners.keySet().iterator().next());return value;
        }
        double density(int x,int y,int z,boolean carved){
            int w=owner.samplingSettings().getCellWidth(),h=owner.samplingSettings().getCellHeight();
            int xx=Math.floorDiv(x,w)*w,yy=Math.floorDiv(y,h)*h,zz=Math.floorDiv(z,w)*w;
            return Mth.lerp3((x-xx)/(double)w,(y-yy)/(double)h,(z-zz)/(double)w,
                    corner(xx,yy,zz,carved),corner(xx+w,yy,zz,carved),corner(xx,yy+h,zz,carved),corner(xx+w,yy+h,zz,carved),
                    corner(xx,yy,zz+w,carved),corner(xx+w,yy,zz+w,carved),corner(xx,yy+h,zz+w,carved),corner(xx+w,yy+h,zz+w,carved));
        }
        boolean standing(BlockPos p,boolean carved){return p.getY()>owner.geometry().lowerSeaTop()+6&&p.getY()<owner.geometry().maxLand()-5
                &&density(p.getX(),p.getY()-1,p.getZ(),carved)>.02&&density(p.getX(),p.getY(),p.getZ(),carved)<-.00001&&density(p.getX(),p.getY()+1,p.getZ(),carved)<-.00001;}
        boolean internal(BlockPos p){return standing(p,true)&&density(p.getX(),p.getY(),p.getZ(),false)>.02&&density(p.getX(),p.getY()+1,p.getZ(),false)>.02;}
        boolean exterior(BlockPos p){return standing(p,false);}
        boolean edge(BlockPos from,BlockPos to,boolean carved){
            int horizontal=Math.abs(from.getX()-to.getX())+Math.abs(from.getZ()-to.getZ()),dy=to.getY()-from.getY();if(horizontal!=1||Math.abs(dy)>1)return false;
            var probe=dy>0?from:to;return dy==0||density(probe.getX(),probe.getY()+2,probe.getZ(),carved)<-.00001;
        }
        boolean sky(BlockPos p){
            long key=((long)p.getX()<<32)^(p.getZ()&0xffffffffL);var top=tops.get(key);
            if(top==null){
                top=owner.geometry().lowerSeaTop();int h=owner.samplingSettings().getCellHeight();
                for(int y=Math.floorDiv(owner.geometry().maxLand(),h)*h;y>=owner.geometry().lowerSeaTop();y-=h){
                    if(density(p.getX(),y,p.getZ(),false)<=0)continue;
                    int ceiling=Math.min(owner.geometry().maxLand(),y+h-1);for(int at=ceiling;at>=y;at--)if(density(p.getX(),at,p.getZ(),false)>0){top=at;break;}break;
                }tops.put(key,top);
            }return top<p.getY();
        }
    }
}
