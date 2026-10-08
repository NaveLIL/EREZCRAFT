package pro.erez.interstice.worldgen.terrain;

import java.util.*;
import java.util.function.BiFunction;
import pro.erez.interstice.geometry.GeometryProfile;

/** Seeded drainage plans shared by density, cave sampling and actual fluid placement. No chunk reads. */
public final class HydrologyV4 {
    private static final int CELL=256,STEP=8,MAX_STEPS=48;
    public enum Kind { NONE, PLAIN_RIVER, MOUNTAIN_STREAM, ISLAND_FALL }
    public record Sample(Kind kind,double bed,int water,double strength,boolean falling) {
        public boolean wet(){return kind!=Kind.NONE&&strength>.5;}
        public boolean contains(int y){return wet()&&y>=Math.ceil(bed)&&y<=water;}
    }
    private record Point(double x,double z,double water) {}
    private record Plan(List<Point> points,Kind kind,double width,Point fall,int fallBottom,double minX,double maxX,double minZ,double maxZ) {}
    public static final class Column implements TerrainColumn {
        private final TerrainV4.Column raw,ground;
        private final Sample sample;
        Column(TerrainV4.Column raw,Sample sample){
            this.raw=raw;this.sample=sample;
            ground=sample.kind!=Kind.ISLAND_FALL&&sample.kind!=Kind.NONE?raw.withGroundHeight(Math.min(raw.groundHeight(),sample.bed)):raw;
        }
        public Sample water(){return sample;}
        public TerrainV4.Column raw(){return raw;}
        public double groundHeight(){return ground.groundHeight();}
        @Override public TerrainV2.Weights weights(){return raw.weights();}
        @Override public TerrainV2.Kind dominant(){return raw.dominant();}
        @Override public double gardenHeight(){return raw.gardenHeight();}
        @Override public double vaultHeight(){return raw.vaultHeight();}
        @Override public double maximumSurfaceY(){return raw.maximumSurfaceY();}
        @Override public double density(double y){
            double density=ground.density(y);
            if(sample.kind==Kind.ISLAND_FALL&&sample.strength>0&&y>=sample.bed&&y<=sample.water+8)
                density=Math.min(density,(sample.bed-y)/12);
            return density;
        }
    }
    public static final class Context {
        private final long seed;
        private final GeometryProfile profile;
        private final BiFunction<Integer,Integer,TerrainV4.Column> columns;
        private final Map<Long,Plan> plans=new LinkedHashMap<>(128,.75F,true);
        public Context(long seed,GeometryProfile profile,BiFunction<Integer,Integer,TerrainV4.Column> columns){this.seed=seed;this.profile=profile;this.columns=columns;}
        private synchronized Plan plan(int cx,int cz){
            long key=((long)cx<<32)^(cz&0xffffffffL);
            if(plans.containsKey(key))return plans.get(key);
            var result=makePlan(cx,cz);plans.put(key,result);
            if(plans.size()>512)plans.remove(plans.keySet().iterator().next());
            return result;
        }
        public Column column(int x,int z){return column(x,z,columns.apply(x,z));}
        public Column column(int x,int z,TerrainV4.Column raw){
            Sample best=plain(seed,profile,x,z,raw);
            int cx=Math.floorDiv(x,CELL),cz=Math.floorDiv(z,CELL);
            // Sources can travel at most384 blocks; no source outside this bounded neighborhood contributes.
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                var route=plan(cx+dx,cz+dz);if(route==null)continue;
                var found=sample(route,x,z,raw);
                if(found.strength>best.strength)best=found;
            }
            return new Column(raw,best);
        }
        private Plan makePlan(int cx,int cz){
            long key=FreeTerraNoise.mix(seed^cx*0x9E3779B97F4A7C15L^cz*0xC2B2AE3D27D4EB4FL^0x445241494E56344CL);
            if(unit(key)>.5)return null;
            int sx=0,sz=0;TerrainV4.Column source=null;Kind kind=Kind.NONE;
            for(int probe=0;probe<4;probe++){
                sx=cx*CELL+24+(int)(unit(key+probe*3+1)*208);sz=cz*CELL+24+(int)(unit(key+probe*3+2)*208);
                var col=columns.apply(sx,sz);
                if(col.weights().vaults()>.65&&col.groundHeight()>65){source=col;kind=Kind.MOUNTAIN_STREAM;break;}
                if(col.weights().ash()>.98&&col.highestFloatingSolidY()>=85&&col.highestFloatingSolidY()-col.lowestFloatingSolidY()>30){
                    source=col;kind=Kind.ISLAND_FALL;break;
                }
            }
            if(source==null)return null;
            int x=sx,z=sz;double surface=surface(source,kind),water=surface-1.1;
            var points=new ArrayList<Point>();var visited=new HashSet<Long>();Point fall=null;
            for(int step=0;step<MAX_STEPS;step++){
                points.add(new Point(x+.5,z+.5,Math.max(profile.lowerSeaTop(),water)));
                if(surface<=profile.lowerSeaTop()+1)break;
                visited.add(((long)x<<32)^(z&0xffffffffL));
                int nx=x,nz=z;double next=surface;
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                    if(dx==0&&dz==0)continue;
                    int ax=x+dx*STEP,az=z+dz*STEP;long packed=((long)ax<<32)^(az&0xffffffffL);
                    if(visited.contains(packed))continue;
                    double height=surface(columns.apply(ax,az),kind);
                    if(height<next-.15){next=height;nx=ax;nz=az;}
                }
                if(nx==x&&nz==z)break; // A genuine drainage basin retains a small spring pool.
                if(kind==Kind.ISLAND_FALL&&next<profile.lowerSeaTop()){
                    fall=new Point(nx+.5,nz+.5,water);points.add(fall);break;
                }
                x=nx;z=nz;surface=next;water=Math.min(water,surface-1.1);
            }
            double minX=points.stream().mapToDouble(Point::x).min().orElse(sx)-6,maxX=points.stream().mapToDouble(Point::x).max().orElse(sx)+6;
            double minZ=points.stream().mapToDouble(Point::z).min().orElse(sz)-6,maxZ=points.stream().mapToDouble(Point::z).max().orElse(sz)+6;
            return new Plan(List.copyOf(points),kind,kind==Kind.ISLAND_FALL?1.5:2.4,fall,profile.lowerSeaTop(),minX,maxX,minZ,maxZ);
        }
        private Sample sample(Plan plan,int x,int z,TerrainV4.Column raw){
            if(x+.5<plan.minX||x+.5>plan.maxX||z+.5<plan.minZ||z+.5>plan.maxZ)return empty(raw.groundHeight());
            double best=Double.POSITIVE_INFINITY,water=0;
            for(int i=0;i<plan.points.size();i++){
                var a=plan.points.get(i);var b=plan.points.get(Math.min(i+1,plan.points.size()-1));
                double dx=b.x-a.x,dz=b.z-a.z,length=dx*dx+dz*dz;
                double t=length==0?0:FreeTerraNoise.clamp(((x+.5-a.x)*dx+(z+.5-a.z)*dz)/length,0,1);
                double distance=Math.hypot(x+.5-a.x-dx*t,z+.5-a.z-dz*t);
                if(distance<best){best=distance;water=FreeTerraNoise.lerp(a.water,b.water,t);}
            }
            double width=plan.width,strength=1-FreeTerraNoise.smoothBetween(width,width+2.5,best);
            if(strength<=0)return empty(raw.groundHeight());
            if(plan.kind==Kind.ISLAND_FALL){
                int top=raw.highestFloatingSolidY();
                boolean falling=plan.fall!=null&&Math.hypot(x+.5-plan.fall.x,z+.5-plan.fall.z)<=1.1;
                if(falling)return new Sample(Kind.ISLAND_FALL,plan.fallBottom+1,(int)Math.floor(plan.fall.water),1,true);
                if(top<profile.lowerSeaTop())return empty(raw.groundHeight());
                double bed=FreeTerraNoise.lerp(top+.75,Math.min(top-1.1,water-2.2),strength);
                return new Sample(Kind.ISLAND_FALL,bed,(int)Math.floor(Math.min(top,water)),strength,false);
            }
            if(raw.weights().grounded()<.4)return empty(raw.groundHeight());
            water=Math.min(water,raw.groundHeight()-1.1);
            double bed=FreeTerraNoise.lerp(raw.groundHeight(),Math.min(raw.groundHeight(),water-2.7),strength);
            return new Sample(Kind.MOUNTAIN_STREAM,bed,(int)Math.floor(water),strength,false);
        }
    }
    private static double surface(TerrainV4.Column col,Kind kind){return kind==Kind.ISLAND_FALL?col.highestFloatingSolidY()+1:col.groundHeight();}
    private static Sample empty(double height){return new Sample(Kind.NONE,height,0,0,false);}
    /** Meandering sea-level channels cut only garden/coastal lowlands, never widen the whole plain. */
    public static Sample plain(long seed,GeometryProfile p,int x,int z,TerrainV4.Column col){
        if(col.weights().gardens()<.65||col.groundHeight()>p.lowerSeaTop()+12||col.groundHeight()<p.lowerSeaTop()-5)return empty(col.groundHeight());
        int field=FreeTerraNoise.fieldSeed(seed,0x504C41494E524956L);
        double wx=x+FreeTerraNoise.warpOffset(field+1,x,z,240,2,52),wz=z+FreeTerraNoise.warpOffset(field+2,x,z,240,2,52);
        double distance=Math.abs(FreeTerraNoise.perlin(wx/240,wz/240,field)) * 180;
        double strength=1-FreeTerraNoise.smoothBetween(2.2,5.7,distance);
        if(strength<=0)return empty(col.groundHeight());
        return new Sample(Kind.PLAIN_RIVER,FreeTerraNoise.lerp(col.groundHeight(),p.lowerSeaTop()-2.2,strength),p.lowerSeaTop(),strength,false);
    }
    private static double unit(long value){return (FreeTerraNoise.mix(value)>>>11)*0x1.0p-53;}
    private HydrologyV4(){}
}
