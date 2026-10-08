package pro.erez.interstice.worldgen.cave;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.IntToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.terrain.TerrainV2;
import pro.erez.interstice.worldgen.terrain.TerrainColumn;

/** World-coordinate cave fields. No random state depends on a chunk or generation order. */
public final class CaveDensity {
    private CaveDensity() {}
    private record Fields(NormalNoise first, NormalNoise second, NormalNoise colonies) {}
    // NormalNoise is immutable after creation. Keep a small seed cache, never shared mutable RNG.
    private static final LinkedHashMap<Long,Fields> FIELDS = new LinkedHashMap<>();
    private static synchronized Fields fields(long seed) {
        var found=FIELDS.get(seed);
        if(found!=null)return found;
        var created=new Fields(noise(seed^0x723BC541L,-5),noise(seed^0x51A9BD33L,-5),noise(seed^0xC01A735L,-7));
        FIELDS.put(seed,created);
        if(FIELDS.size()>4)FIELDS.remove(FIELDS.keySet().iterator().next());
        return created;
    }
    private static NormalNoise noise(long seed,int octave) {
        // The legacy RNG keeps 48 bits; mix first so every world-seed bit contributes.
        return NormalNoise.create(RandomSource.create(mix(seed)),octave,1.0);
    }
    private static long mix(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    private static double unit(long value) {return (mix(value)>>>11)*0x1.0p-53;}
    private record Room(double horizontal,double y,double height) {
        double mask(int atY) {double dy=(atY-y)/height;return 1-horizontal-dy*dy;}
    }
    private record RoomPlan(double x,double z,double y,double radius,double height) {
        Room at(int sampleX,int sampleZ) {
            double dx=(sampleX-x)/radius,dz=(sampleZ-z)/radius;
            return dx*dx+dz*dz>=1?null:new Room(dx*dx+dz*dz,y,height);
        }
    }
    private record Band(int bottom,int top) {int span(){return top-bottom+1;}}
    public record Entrance(BlockPos mouth,BlockPos inner,List<BlockPos> route) {}
    private record Mouth(double startX,double startZ,double endX,double endZ,int startFloor,int endFloor,double bend) {
        double length() {return Math.hypot(endX-startX,endZ-startZ);}
        double x(double t) {return startX+(endX-startX)*t-(endZ-startZ)/length()*Math.sin(Math.PI*t)*bend;}
        double z(double t) {return startZ+(endZ-startZ)*t+(endX-startX)/length()*Math.sin(Math.PI*t)*bend;}
        int floor(double t) {return (int)Math.round(startFloor+(endFloor-startFloor)*t);}
        double along(int x,int z) {
            double dx=endX-startX,dz=endZ-startZ;
            return Math.max(0,Math.min(1,((x-startX)*dx+(z-startZ)*dz)/(dx*dx+dz*dz)));
        }
        int floorAt(int x,int z) {return floor(along(x,z));}
        double mask(int x,int y,int z) {
            double t=along(x,z);
            double across=Math.hypot(x-x(t),z-z(t));
            int floor=floor(t);
            // Four blocks of headroom above a stepped floor; only the mouth breaches the shell.
            double vertical=Math.min(y-floor+.25,floor+3.75-y);
            double tunnel=Math.min(2.5-across,vertical);
            // The interior alcove connects to a verified point of the shared worm field.
            double alcove=1-((x-endX)*(x-endX)+(z-endZ)*(z-endZ))/25.0-Math.pow((y-endFloor-2)/2.8,2);
            return Math.max(tunnel,alcove);
        }
        boolean near(int x,int z) {
            return x>=Math.min(startX,endX)-8&&x<=Math.max(startX,endX)+8
                    &&z>=Math.min(startZ,endZ)-8&&z<=Math.max(startZ,endZ)+8;
        }
        boolean floorProtected(int x,int y,int z) {
            double t=along(x,z);
            int floor=floor(t);
            return y>=floor-2&&y<floor&&Math.hypot(x-x(t),z-z(t))<2.8;
        }
        Entrance entrance() {
            var route=new ArrayList<BlockPos>();
            int steps=(int)Math.ceil(length());
            for(int step=0;step<=steps;step++) {
                double t=step/(double)steps;
                int x=(int)Math.round(x(t)),z=(int)Math.round(z(t));
                var pos=new BlockPos(x,floorAt(x,z),z);
                if(route.isEmpty()||!route.getLast().equals(pos))route.add(pos);
            }
            return new Entrance(route.getFirst(),route.getLast(),List.copyOf(route));
        }
    }
    /** Per-evaluation cache: geometry is anchored to world cells, never the chunk footprint. */
    public static final class Context {
        private final long seed;
        private final GeometryProfile profile;
        private final int revision;
        private final BiFunction<Integer,Integer,? extends TerrainColumn> columns;
        private final Map<Long,Optional<Mouth>> plans=new HashMap<>();
        private final Map<Long,List<RoomPlan>> roomPlans=new HashMap<>();
        private Context(long seed,GeometryProfile profile,BiFunction<Integer,Integer,? extends TerrainColumn> columns,int revision) {
            if(revision<2||revision>4)throw new IllegalArgumentException("Unsupported cave terrain revision "+revision);
            this.seed=seed;this.profile=profile;this.columns=columns;this.revision=revision;
        }
        private Optional<Mouth> mouth(int cx,int cz) {
            long key=((long)cx<<32)^(cz&0xffffffffL);
            var found=plans.get(key);
            if(found!=null)return found;
            var plan=revision>=3?planMouthV3(seed,profile,cx,cz,columns):planMouth(seed,profile,cx,cz,columns);
            // A normal chunk context uses <=9 entries. Bound diagnostic/wide sampling contexts too.
            if(plans.size()>=64)plans.clear();
            plans.put(key,plan);
            return plan;
        }
        private List<RoomPlan> rooms(int cx,int cz) {
            long key=((long)cx<<32)^(cz&0xffffffffL);
            var found=roomPlans.get(key);
            if(found!=null)return found;
            var result=planRoomsV3(seed,profile,cx,cz,columns);
            if(roomPlans.size()>=64)roomPlans.clear();
            roomPlans.put(key,result);
            return result;
        }
        public Optional<Entrance> entrance(int cellX,int cellZ) {return mouth(cellX,cellZ).map(Mouth::entrance);}
        public Column column(int x,int z,double c,double h,IntToDoubleFunction terrain) {
            var mouths=new ArrayList<Mouth>();
            int cx=Math.floorDiv(x,128),cz=Math.floorDiv(z,128);
            for(int ax=cx-1;ax<=cx+1;ax++)for(int az=cz-1;az<=cz+1;az++) {
                long key=seed^ax*0x723BC541L^az*132897987541L^0xA17E12L;
                int anchorX=ax*128+32+(int)(unit(key+1)*64),anchorZ=az*128+32+(int)(unit(key+2)*64);
                int reach=revision>=3?96:70;
                if(Math.abs(x-anchorX)>reach||Math.abs(z-anchorZ)>reach)continue;
                mouth(ax,az).filter(m->m.near(x,z)).ifPresent(mouths::add);
            }
            if(revision==2)return new Column(seed,profile,x,z,c,h,terrain,List.copyOf(mouths));
            var selected=new ArrayList<Room>();
            int roomX=Math.floorDiv(x,64),roomZ=Math.floorDiv(z,64);
            for(int ax=roomX-1;ax<=roomX+1;ax++)for(int az=roomZ-1;az<=roomZ+1;az++)for(RoomPlan room:rooms(ax,az)) {
                Room sample=room.at(x,z);if(sample!=null)selected.add(sample);
            }
            return new Column(seed,profile,x,z,c,h,terrain,List.copyOf(mouths),columns.apply(x,z).weights(),List.copyOf(selected));
        }
    }
    public static Context context(long seed,GeometryProfile profile,BiFunction<Integer,Integer,? extends TerrainColumn> rawColumns) {
        return new Context(seed,profile,rawColumns,2);
    }
    /** Opt-in new morphology/hydrology. Default overload deliberately retains every V2 cave sample. */
    public static Context context(long seed,GeometryProfile profile,BiFunction<Integer,Integer,? extends TerrainColumn> rawColumns,int revision) {
        return new Context(seed,profile,rawColumns,revision);
    }
    private static int surfaceV3(GeometryProfile profile,TerrainColumn column) {
        for(int y=profile.maxLand()-3;y>=profile.lowerSeaTop();y--)if(column.density(y)>0)return y;
        return -1;
    }
    private static Band largestBand(GeometryProfile profile,TerrainColumn column) {
        Band largest=null;int bottom=-1;
        for(int y=profile.minY()+6;y<=profile.maxLand()-4;y++) {
            if(column.density(y)>0) {if(bottom<0)bottom=y;}
            else if(bottom>=0) {
                Band band=new Band(bottom,y-1);
                if(largest==null||band.span()>largest.span())largest=band;
                bottom=-1;
            }
        }
        if(bottom>=0) {
            Band band=new Band(bottom,profile.maxLand()-4);
            if(largest==null||band.span()>largest.span())largest=band;
        }
        return largest;
    }
    private static List<RoomPlan> planRoomsV3(long seed,GeometryProfile profile,int cellX,int cellZ,BiFunction<Integer,Integer,? extends TerrainColumn> columns) {
        var plans=new ArrayList<RoomPlan>();
        for(int tier=0;tier<3;tier++) {
            long key=seed^cellX*341873128712L^cellZ*132897987541L^tier*0x57B7239L;
            double x=cellX*64+12+unit(key+1)*40,z=cellZ*64+12+unit(key+2)*40;
            TerrainColumn raw=columns.apply((int)Math.round(x),(int)Math.round(z));
            var w=raw.weights();
            double chance=.20*w.ash()+.52*w.gardens()+.82*w.vaults();
            double activity=Math.max(0,Math.min(1,(chance-unit(key))*10+.5));
            if(activity<=0)continue;
            Band band=largestBand(profile,raw);
            if(band==null||band.span()<13)continue;
            double low=Math.max(profile.minY()+8,band.bottom()+2),high=band.top()-4;
            if(high-low<6)continue;
            double center=low+(high-low)*(.25+.25*tier);
            double height=Math.min(3.5*w.ash()+7*w.gardens()+15*w.vaults(),Math.min(center-low,high-center));
            double radius=(5*w.ash()+12*w.gardens()+26*w.vaults())*Math.sqrt(activity);
            height*=Math.sqrt(activity);
            if(height<2.5||radius<3)continue;
            plans.add(new RoomPlan(x,z,center,radius,height));
        }
        return List.copyOf(plans);
    }
    private static Optional<Mouth> planMouthV3(long seed,GeometryProfile profile,int cellX,int cellZ,BiFunction<Integer,Integer,? extends TerrainColumn> sourceColumns) {
        // High relief retains a hillside entrance. Low plain cores use a stepped descending adit.
        var hillside=planMouth(seed,profile,cellX,cellZ,sourceColumns);
        if(hillside.isPresent())return hillside;
        long key=seed^cellX*0x723BC541L^cellZ*132897987541L^0xA17E12L;
        if(unit(key)>.78)return Optional.empty();
        var samples=new HashMap<Long,TerrainColumn>();
        BiFunction<Integer,Integer,TerrainColumn> columns=(x,z)->samples.computeIfAbsent(((long)x<<32)^(z&0xffffffffL),k->sourceColumns.apply(x,z));
        int entryX=cellX*128+32+(int)(unit(key+1)*64),entryZ=cellZ*128+32+(int)(unit(key+2)*64);
        var entranceRaw=columns.apply(entryX,entryZ);
        int high=surfaceV3(profile,entranceRaw);
        if(high<profile.lowerSeaTop()||high>profile.lowerSeaTop()+22||entranceRaw.weights().gardens()<.5)return Optional.empty();
        int startFloor=Math.max(profile.lowerSeaTop()+1,high+1);
        int[][] directions={{1,0},{0,1},{-1,0},{0,-1}};
        int rotation=(int)(unit(key+3)*4);Fields field=fields(seed);
        for(int direction=0;direction<4;direction++) {
            int[] axis=directions[(direction+rotation)%4];
            for(int distance:new int[]{48,60,72}) {
                int targetX=entryX+axis[0]*distance,targetZ=entryZ+axis[1]*distance;
                int bestX=0,bestZ=0,bestFloor=0;double best=Double.POSITIVE_INFINITY;
                for(int dx=-12;dx<=12;dx+=6)for(int dz=-12;dz<=12;dz+=6) {
                    int tx=targetX+dx,tz=targetZ+dz;
                    var raw=columns.apply(tx,tz);
                    if(raw.weights().grounded()<.8)continue;
                    int length=(int)Math.hypot(tx-entryX,tz-entryZ);
                    for(int floor=profile.minY()+12;floor<=profile.minY()+26;floor+=2) {
                        if(Math.abs(floor-startFloor)>length/3||raw.density(floor+2)<.4||raw.density(floor+9)<=0)continue;
                        double a=Math.abs(field.first.getValue(tx*1.18,(floor+2)*.92,tz*1.18));
                        double b=Math.abs(field.second.getValue(tx*1.18,(floor+2)*.92,tz*1.18));
                        if(Math.max(a,b)>.045)continue;
                        double score=dx*dx+dz*dz+(floor-profile.minY()-20)*(floor-profile.minY()-20)*2;
                        if(score<best){best=score;bestX=tx;bestZ=tz;bestFloor=floor;}
                    }
                }
                if(!Double.isFinite(best))continue;
                Mouth mouth=new Mouth(entryX,entryZ,bestX,bestZ,startFloor,bestFloor,(unit(key+4)-.5)*4);
                boolean sealed=true;
                int steps=(int)Math.ceil(mouth.length());
                for(int step=0;step<=steps&&sealed;step++) {
                    double t=step/(double)steps;
                    int x=(int)Math.round(mouth.x(t)),z=(int)Math.round(mouth.z(t)),floor=mouth.floorAt(x,z);
                    if(columns.apply(x,z).density(floor-2)<=0){sealed=false;break;}
                    // No lateral breach to the exterior ocean below its surface. The shallow
                    // throat is above a continuous mainland foundation; the inner adit has 4+ roof.
                    for(int ox:new int[]{-4,0,4})for(int oz:new int[]{-4,0,4}) {
                        var side=columns.apply(x+ox,z+oz);
                        for(int y=floor;y<=Math.min(profile.lowerSeaTop(),floor+3);y++)if(side.density(y)<=0){sealed=false;break;}
                    }
                }
                if(!sealed)continue;
                for(int ox:new int[]{-7,0,7})for(int oz:new int[]{-7,0,7}) {
                    var wall=columns.apply(bestX+ox,bestZ+oz);
                    if(wall.density(bestFloor-2)<=0||wall.density(bestFloor+9)<=0)sealed=false;
                }
                if(sealed)return Optional.of(mouth);
            }
        }
        return Optional.empty();
    }
    private static int surface(GeometryProfile profile,TerrainColumn column) {
        for(int y=profile.maxLand()-3;y>profile.lowerSeaTop()+2;y--)if(column.density(y)>0)return y;
        return -1;
    }
    private static Optional<Mouth> planMouth(long seed,GeometryProfile profile,int cellX,int cellZ,BiFunction<Integer,Integer,? extends TerrainColumn> sourceColumns) {
        var samples=new HashMap<Long,TerrainColumn>();
        BiFunction<Integer,Integer,TerrainColumn> columns=(x,z)->samples.computeIfAbsent(((long)x<<32)^(z&0xffffffffL),key->sourceColumns.apply(x,z));
        long key=seed^cellX*0x723BC541L^cellZ*132897987541L^0xA17E12L;
        if(unit(key)>.78)return Optional.empty();
        int centerX=cellX*128+32+(int)(unit(key+1)*64),centerZ=cellZ*128+32+(int)(unit(key+2)*64);
        int high=surface(profile,columns.apply(centerX,centerZ));
        if(high<profile.lowerSeaTop()+14)return Optional.empty();
        Fields field=fields(seed);
        int[][] directions={{1,0},{0,1},{-1,0},{0,-1}};
        int rotation=(int)(unit(key+3)*4);
        for(int direction=0;direction<4;direction++) {
            int[] axis=directions[(direction+rotation)%4];
            for(int distance:new int[]{24,36,48}) {
                int entryX=centerX+axis[0]*distance,entryZ=centerZ+axis[1]*distance;
                int low=surface(profile,columns.apply(entryX,entryZ));
                if(low<profile.lowerSeaTop()+2||high-low<7)continue;
                int startFloor=low+1;
                int desired=Math.min(high-9,startFloor+distance/4);
                if(desired<=profile.lowerSeaTop()+2)continue;
                int bestX=0,bestZ=0,bestFloor=0;double best=Double.POSITIVE_INFINITY;
                // Find a real shared tunnel to join; do not create an isolated exhibit alcove.
                for(int dx=-12;dx<=12;dx+=6)for(int dz=-12;dz<=12;dz+=6) {
                    int tx=centerX+dx,tz=centerZ+dz;
                    var raw=columns.apply(tx,tz);
                    int length=(int)Math.hypot(tx-entryX,tz-entryZ);
                    for(int floor=desired-6;floor<=desired+6;floor+=2) {
                        if(floor<=profile.lowerSeaTop()+2||Math.abs(floor-startFloor)>length/3||floor>=profile.maxLand()-9)continue;
                        if(raw.density(floor+2)<.40||raw.density(floor+6)<=0)continue;
                        double a=Math.abs(field.first.getValue(tx*1.18,(floor+2)*.92,tz*1.18));
                        double b=Math.abs(field.second.getValue(tx*1.18,(floor+2)*.92,tz*1.18));
                        if(Math.max(a,b)>.045)continue;
                        double score=dx*dx+dz*dz+(floor-desired)*(floor-desired)*2;
                        if(score<best){best=score;bestX=tx;bestZ=tz;bestFloor=floor;}
                    }
                }
                if(!Double.isFinite(best))continue;
                Mouth mouth=new Mouth(entryX,entryZ,bestX,bestZ,startFloor,bestFloor,(unit(key+4)-.5)*4);
                boolean supported=true;
                for(int step=0;step<=Math.ceil(mouth.length());step++) {
                    double t=step/Math.ceil(mouth.length());
                    int x=(int)Math.round(mouth.x(t)),z=(int)Math.round(mouth.z(t));
                    if(columns.apply(x,z).density(mouth.floorAt(x,z)-1)<=0){supported=false;break;}
                }
                if(supported)return Optional.of(mouth);
            }
        }
        return Optional.empty();
    }
    public static final class Column {
        private final GeometryProfile profile;
        private final int x,z;
        private final Fields fields;
        private final double tunnelWidth,ash;
        private final List<Room> rooms;
        private final List<Mouth> mouths;
        private final IntToDoubleFunction terrain;
        private Column(long seed,GeometryProfile profile,int x,int z,double c,double h,IntToDoubleFunction terrain) {
            this(seed,profile,x,z,c,h,terrain,List.of());
        }
        private Column(long seed,GeometryProfile profile,int x,int z,double c,double h,IntToDoubleFunction terrain,List<Mouth> mouths) {
            this(seed,profile,x,z,c,h,terrain,mouths,null,null);
        }
        private Column(long seed,GeometryProfile profile,int x,int z,double c,double h,IntToDoubleFunction terrain,List<Mouth> mouths,TerrainV2.Weights selectedWeights,List<Room> selectedRooms) {
            this.profile=profile;this.x=x;this.z=z;this.fields=fields(seed);this.terrain=terrain;
            this.mouths=mouths;
            var weights=selectedWeights==null?TerrainV2.weights(c,h):selectedWeights;
            ash=weights.ash();
            tunnelWidth=.060*weights.ash()+.125*weights.gardens()+.19*weights.vaults();
            if(selectedRooms!=null){rooms=selectedRooms;return;}
            double radius=5*weights.ash()+12*weights.gardens()+26*weights.vaults();
            double height=3.5*weights.ash()+7*weights.gardens()+15*weights.vaults();
            double chance=.20*weights.ash()+.52*weights.gardens()+.82*weights.vaults();
            var list=new ArrayList<Room>();
            int cellX=Math.floorDiv(x,64),cellZ=Math.floorDiv(z,64);
            int span=profile.maxLand()-profile.lowerSeaTop()-12;
            for(int cx=cellX-1;cx<=cellX+1;cx++)for(int cz=cellZ-1;cz<=cellZ+1;cz++)for(int tier=0;tier<3;tier++) {
                long key=seed^cx*341873128712L^cz*132897987541L^tier*0x57B7239L;
                // Fade an occasional room in/out across biome transitions, rather than an
                // abrupt probability threshold producing a wall along a climate boundary.
                double activity=Math.max(0,Math.min(1,(chance-unit(key))*10+.5));
                if(activity<=0)continue;
                double activeRadius=radius*Math.sqrt(activity),activeHeight=height*Math.sqrt(activity);
                double centerX=cx*64+12+unit(key+1)*40,centerZ=cz*64+12+unit(key+2)*40;
                double dx=(x-centerX)/activeRadius,dz=(z-centerZ)/activeRadius;
                if(dx*dx+dz*dz>=1)continue;
                double cy=profile.lowerSeaTop()+9+span*(.15+.28*tier)+unit(key+3)*10;
                list.add(new Room(dx*dx+dz*dz,cy,activeHeight));
            }
            rooms=List.copyOf(list);
        }
        public double carve(int y,double density) {
            if(density<=0 || y<=profile.minY()+5 || y>=profile.maxLand()-4)return density;
            for(Mouth mouth:mouths) {
                if(mouth.floorProtected(x,y,z))return density;
                double opening=mouth.mask(x,y,z);
                if(opening>.02)return Math.min(density,-Math.min(1,opening));
            }
            // A closed four-block ceiling shell prevents broad chambers from destroying the surface.
            // The two-block side shell matters especially for detached islands.
            double shell=.16+.13*ash;
            if(density<shell)return density;
            if(terrain!=null && terrain.applyAsDouble(y+4)<=0)return density;
            double a=Math.abs(fields.first.getValue(x*1.18,y*.92,z*1.18));
            double b=Math.abs(fields.second.getValue(x*1.18,y*.92,z*1.18));
            double mask=tunnelWidth-Math.max(a,b);
            for(Room room:rooms)mask=Math.max(mask,room.mask(y)*.20);
            // At the boundary, a small density taper avoids needle-shaped noise cavities.
            if(mask<=.012)return density;
            return Math.min(density,-mask*4);
        }
    }
    /** Preferred integration: construct once per X/Z column and pass the uncarved terrain function. */
    public static Column column(long seed,GeometryProfile profile,int x,int z,double c,double h,IntToDoubleFunction terrain) {
        return new Column(seed,profile,x,z,c,h,terrain);
    }
    /** Stateless sampling for fixtures. It can only remove terrain, never create terrain. */
    public static double carve(long seed,GeometryProfile profile,int x,int y,int z,double c,double h,double baseDensity) {
        return column(seed,profile,x,z,c,h,null).carve(y,baseDensity);
    }
    /** Broad continuous fields make colonies whole habitats rather than independent random plants. */
    public static boolean colonized(long seed,int x,int y,int z) {
        return fields(seed).colonies.getValue(x,y*.65,z)>.44;
    }
    public static boolean lush(long seed,int x,int y,int z) {
        double value=fields(seed).colonies.getValue(x,y*.65,z);
        return value>-.12 && value<.36;
    }
}
