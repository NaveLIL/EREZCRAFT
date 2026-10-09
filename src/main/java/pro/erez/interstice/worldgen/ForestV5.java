package pro.erez.interstice.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.terrain.FreeTerraNoise;

/** V5 global forest plans. A chunk writes its own part of complete trees rooted on either side. */
public final class ForestV5 {
    private static final int ROOT_CELL = 9;
    private static final int SHRUB_CELL = 15;
    private static final int REACH = 8;
    private static final ResourceLocation FOREST = ResourceLocation.fromNamespaceAndPath(Interstice.ID,"paleheart_forest");
    private static final ResourceLocation CROWN = ResourceLocation.fromNamespaceAndPath(Interstice.ID,"paleheart_crown_forest");
    public enum Kind { PALEHEART, GLOOMCROWN, CROWN }
    /** The probe must read the immutable geological field, not already decorated neighboring chunks. */
    public interface GroundProbe {
        int surface(int x,int z);
        boolean solid(int x,int y,int z);
        default boolean garden(int x,int z) { return true; }
        default boolean crimson(int x,int z) { return false; }
        default boolean reserved(int x,int y,int z) { return false; }
    }
    public record Candidate(int x,int z,Kind kind,long key,boolean understory) {}
    public record Root(BlockPos pos,Kind kind,long key,int height) {}
    public record Plan(Map<BlockPos,BlockState> cells,List<Root> roots) {}
    public record Counts(int wood,int leaves,int fruits,int vines) {}
    private record Cell(BlockState state,long key) {}
    private ForestV5() {}

    private static double unit(long value) { return (FreeTerraNoise.mix(value)>>>11)*0x1.0p-53; }
    private static Candidate candidate(long seed,int cellX,int cellZ,boolean understory) {
        int size=understory?SHRUB_CELL:ROOT_CELL;
        long key=FreeTerraNoise.mix(seed^cellX*0x9E3779B97F4A7C15L^cellZ*0xC2B2AE3D27D4EB4FL
                ^(understory?0x5635554E444552L:0x563543414E4F50L));
        int x=cellX*size+(int)(unit(key^0x585858L)*size);
        int z=cellZ*size+(int)(unit(key^0x5A5A5AL)*size);
        // Grove variation keeps paths/clearings while retaining a layered forest throughout the biome.
        double grove=FreeTerraNoise.fractal(seed,0x563547524F5645L,x,z,150,2);
        double acceptance=understory?.24+.18*grove:.76+.20*grove;
        if(unit(key^0x414343455054L)>=acceptance)return null;
        double choice=unit(key^0x4B494E44L);
        // The ground layer already has its own sparse shrub field. Spend more of the SAME
        // overstory root budget on spreading high crowns instead of repeating short trees.
        var kind=understory?Kind.GLOOMCROWN:choice<.24?Kind.CROWN:choice<.32?Kind.GLOOMCROWN:Kind.PALEHEART;
        return new Candidate(x,z,kind,key,understory);
    }
    /** No root coordinates are clamped or shifted to fit a Minecraft chunk. */
    public static List<Candidate> candidates(long seed,int chunkX,int chunkZ) {
        var out=new ArrayList<Candidate>();
        for(boolean understory:new boolean[]{false,true}) {
            int size=understory?SHRUB_CELL:ROOT_CELL;
            int minX=Math.floorDiv(chunkX*16-REACH,size),maxX=Math.floorDiv(chunkX*16+15+REACH,size);
            int minZ=Math.floorDiv(chunkZ*16-REACH,size),maxZ=Math.floorDiv(chunkZ*16+15+REACH,size);
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
                var c=candidate(seed,x,z,understory);
                if(c!=null&&separated(seed,c))out.add(c);
            }
        }
        out.sort((a,b)->Long.compareUnsigned(a.key,b.key));
        return List.copyOf(out);
    }
    private static boolean separated(long seed,Candidate at) {
        // Only very close stems compete; broad, asymmetric crowns may overlap freely.
        for(boolean understory:new boolean[]{false,true}) {
            int size=understory?SHRUB_CELL:ROOT_CELL;
            int cx=Math.floorDiv(at.x,size),cz=Math.floorDiv(at.z,size);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                var other=candidate(seed,cx+dx,cz+dz,understory);
                if(other==null||other.key==at.key)continue;
                double distance=Math.hypot(other.x-at.x,other.z-at.z);
                if(distance<4.0&&Long.compareUnsigned(other.key,at.key)<0)return false;
            }
        }
        return true;
    }

    public static Plan plan(GeometryProfile profile,int chunkX,int chunkZ,long seed,LevelReader level,GroundProbe probe) {
        var cells=new LinkedHashMap<BlockPos,Cell>();var roots=new ArrayList<Root>();
        // Local caches have no lifetime across generation calls or worlds.
        var heights=new HashMap<Long,Integer>();var solids=new HashMap<BlockPos,Boolean>();
        java.util.function.BiFunction<Integer,Integer,Integer> height=(x,z)->heights.computeIfAbsent(pack(x,z),k->probe.surface(x,z));
        java.util.function.Predicate<BlockPos> solid=p->solids.computeIfAbsent(p.immutable(),q->probe.solid(q.getX(),q.getY(),q.getZ()));
        for(var candidate:candidates(seed,chunkX,chunkZ)) {
            boolean crimson=probe.crimson(candidate.x,candidate.z);
            if(!crimson&&!probe.garden(candidate.x,candidate.z))continue;
            Kind kind=candidate.kind;
            if(crimson&&!candidate.understory){double species=unit(candidate.key^0x4352494D534F4EL);kind=species<.06?Kind.CROWN:species<.70?Kind.GLOOMCROWN:Kind.PALEHEART;}
            int floor=height.apply(candidate.x,candidate.z);
            if(floor<=profile.lowerSeaTop()+1||floor>130)continue;
            var root=new BlockPos(candidate.x,floor+1,candidate.z);
            if(probe.reserved(root.getX(),root.getY(),root.getZ()))continue;
            if(!solid.test(root.below())||solid.test(root)||solid.test(root.above()))continue;
            // Roots need a stable patch; the canopy still follows individual branches across steps.
            if(Math.abs(height.apply(candidate.x+2,candidate.z)-floor)>6
                    ||Math.abs(height.apply(candidate.x-2,candidate.z)-floor)>6
                    ||Math.abs(height.apply(candidate.x,candidate.z+2)-floor)>6
                    ||Math.abs(height.apply(candidate.x,candidate.z-2)-floor)>6)continue;
            var random=RandomSource.create(candidate.key^0x5348415045L);
            Map<BlockPos,BlockState> tree;
            if(kind==Kind.GLOOMCROWN&&(!crimson||candidate.understory))tree=GloomcrownTree.plan(root,5+random.nextInt(3),random.nextBoolean(),random.nextInt(3));
            else {
                var definition=GardenTreeDefinitions.get(kind==Kind.CROWN?CROWN:FOREST);
                var variantRandom=RandomSource.create(candidate.key^0x56415249414E54L);
                // Crimson's main dark trees use the authored spreading/forked middle forms;
                // its separate shrub layer already provides the two smallest silhouettes.
                var variant=crimson&&kind==Kind.GLOOMCROWN&&definition.variants().size()>2
                        ?definition.variants().get(2+variantRandom.nextInt(definition.variants().size()-2))
                        :definition.select(variantRandom);
                tree=GardenTrees.plan(level,p->solid.test(p)?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),root,variant,random);
                if(crimson&&kind==Kind.GLOOMCROWN)tree=gloomMaterial(tree);
            }
            if(tree.isEmpty())continue;
            tree=broaderCrown(tree,root,candidate.key);
            boolean fits=true;
            for(var entry:tree.entrySet()) {
                var p=entry.getKey();
                if(probe.reserved(p.getX(),p.getY(),p.getZ())){fits=false;break;}
                if(wood(entry.getValue())&&(p.getY()<root.getY()||p.getY()>profile.maxLand()
                        ||p.getY()+1>SeaSurface.cellMinimum(profile,p.getX(),p.getZ(),true)-profile.clearance()
                        ||solid.test(p)||p.getY()==root.getY()&&!solid.test(p.below()))) {fits=false;break;}
            }
            if(!fits)continue;
            tree=fitLivingCrown(tree,profile,root,solid);
            boolean intersects=false;
            for(var entry:tree.entrySet()) {
                var p=entry.getKey();
                if((p.getX()>>4)!=chunkX||(p.getZ()>>4)!=chunkZ)continue;
                intersects=true;
                var previous=cells.get(p);var state=entry.getValue();
                if(previous==null||priority(state)>priority(previous.state)
                        ||priority(state)==priority(previous.state)&&Long.compareUnsigned(candidate.key,previous.key)<0)
                    cells.put(p,new Cell(state,candidate.key));
            }
            if(intersects)roots.add(new Root(root,kind,candidate.key,
                    tree.keySet().stream().mapToInt(p->p.getY()-root.getY()+1).max().orElse(0)));
        }
        var result=new LinkedHashMap<BlockPos,BlockState>();cells.forEach((p,c)->result.put(p,c.state));
        return new Plan(Map.copyOf(result),List.copyOf(roots));
    }
    private static long pack(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static boolean wood(BlockState s){return s.is(GardenMaterials.PALEHEART_LOG.get())||s.is(GardenMaterials.CROWN_LOG.get())||s.is(Interstice.GLOOMCROWN_LOG.get());}
    private static int priority(BlockState s){return wood(s)?4:s.is(GardenMaterials.CROWN_FRUIT.get())?3:s.getBlock() instanceof LeavesBlock?2:1;}
    /** Existing branch silhouettes gain a scalloped one-block canopy edge, never new trunks or a plate. */
    private static Map<BlockPos,BlockState> broaderCrown(Map<BlockPos,BlockState> source,BlockPos root,long key){
        var result=new LinkedHashMap<>(source);
        for(var entry:source.entrySet())if(entry.getValue().getBlock() instanceof LeavesBlock){
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
                if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)>2)continue;
                var p=entry.getKey().offset(dx,dy,dz);
                if(p.getY()<root.getY()+2||Math.abs(p.getX()-root.getX())>REACH||Math.abs(p.getZ()-root.getZ())>REACH)continue;
                // Horizontal lobes cover ground; vertical detail stays irregular instead of a flat lid.
                if(unit(key^p.asLong()^0x43414E4F50594CL)>(dy==0?.86:.62))continue;
                result.putIfAbsent(p,entry.getValue());
            }
        }
        return result;
    }
    /** Leaves may meet a hillside; wood is already checked atomically. Keep only actual live attachments. */
    private static Map<BlockPos,BlockState> fitLivingCrown(Map<BlockPos,BlockState> source,GeometryProfile profile,BlockPos root,java.util.function.Predicate<BlockPos> solid){
        var result=new LinkedHashMap<>(source);
        result.entrySet().removeIf(e->!wood(e.getValue())&&(solid.test(e.getKey())||e.getKey().getY()<root.getY()
                ||e.getKey().getY()>profile.maxLand()||e.getKey().getY()+1>SeaSurface.cellMinimum(profile,e.getKey().getX(),e.getKey().getZ(),true)-profile.clearance()));
        var distances=new HashMap<BlockPos,Integer>();var queue=new java.util.ArrayDeque<BlockPos>();
        result.forEach((p,s)->{if(wood(s)){distances.put(p,0);queue.add(p);}});
        while(!queue.isEmpty()){
            var p=queue.remove();int distance=distances.get(p)+1;if(distance>6)continue;
            for(var direction:net.minecraft.core.Direction.values()){
                var next=p.relative(direction);var state=result.get(next);
                if(state!=null&&state.getBlock() instanceof LeavesBlock&&!distances.containsKey(next)){distances.put(next,distance);queue.add(next);}
            }
        }
        result.entrySet().removeIf(e->e.getValue().getBlock() instanceof LeavesBlock&&!distances.containsKey(e.getKey()));
        result.replaceAll((p,s)->s.getBlock() instanceof LeavesBlock?s.setValue(LeavesBlock.DISTANCE,distances.get(p)).setValue(LeavesBlock.PERSISTENT,false):s);
        result.entrySet().removeIf(e->e.getValue().is(GardenMaterials.CROWN_FRUIT.get())
                &&!(result.get(e.getKey().below())!=null&&result.get(e.getKey().below()).is(GardenMaterials.CROWN_LEAVES.get())));
        var vines=result.entrySet().stream().filter(e->e.getValue().is(GardenMaterials.PALE_VINE.get())).map(Map.Entry::getKey)
                .sorted((a,b)->Integer.compare(b.getY(),a.getY())).toList();
        for(var p:vines){var above=result.get(p.above());if(above==null||!GardenVineBlock.anchor(above)&&!above.is(GardenMaterials.PALE_VINE.get()))result.remove(p);}
        for(var p:vines)if(result.containsKey(p)){
            var above=result.get(p.above());int part=above!=null&&above.is(GardenMaterials.PALE_VINE.get())?1-(above.getValue(GardenVineBlock.SECTION)&1):0;
            boolean below=result.get(p.below())!=null&&result.get(p.below()).is(GardenMaterials.PALE_VINE.get());
            result.put(p,result.get(p).setValue(GardenVineBlock.SECTION,!below&&part==0?4:part+(below?0:2)));
        }
        return result;
    }
    /** Reuse authored branch/leaf silhouettes, with existing dark-tree material and vanilla leaf state. */
    private static Map<BlockPos,BlockState> gloomMaterial(Map<BlockPos,BlockState> source){
        var result=new LinkedHashMap<BlockPos,BlockState>();
        source.forEach((p,s)->{
            if(s.is(GardenMaterials.PALEHEART_LOG.get()))s=Interstice.GLOOMCROWN_LOG.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,s.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS));
            else if(s.is(GardenMaterials.PALEHEART_LEAVES.get()))s=Interstice.GLOOMCROWN_LEAVES.get().defaultBlockState()
                    .setValue(LeavesBlock.DISTANCE,s.getValue(LeavesBlock.DISTANCE)).setValue(LeavesBlock.PERSISTENT,false);
            result.put(p,s);
        });return result;
    }

    public static Counts generate(GeometryProfile profile,ChunkAccess chunk,long seed,WorldGenRegion region,GroundProbe probe) {
        var server=region.getLevel();var generator=server.getChunkSource().getGenerator();
        var climate=server.getChunkSource().randomState().sampler();
        GroundProbe biomeProbe=new GroundProbe() {
            public int surface(int x,int z){return probe.surface(x,z);}
            public boolean solid(int x,int y,int z){return probe.solid(x,y,z);}
            public boolean reserved(int x,int y,int z){return probe.reserved(x,y,z);}
            public boolean garden(int x,int z){return probe.garden(x,z)&&generator.getBiomeSource()
                    .getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(probe.surface(x,z)),QuartPos.fromBlock(z),climate).is(RealmBiomes.PALE_GARDENS);}
            public boolean crimson(int x,int z){return(probe.garden(x,z)||probe.crimson(x,z))&&generator.getBiomeSource()
                    .getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(probe.surface(x,z)),QuartPos.fromBlock(z),climate).is(RealmBiomes.CRIMSON_THICKETS);}
        };
        var forest=plan(profile,chunk.getPos().x,chunk.getPos().z,seed,region,biomeProbe);
        int logs=0,leaves=0,fruits=0,vines=0;
        for(var entry:forest.cells.entrySet()) {
            var before=chunk.getBlockState(entry.getKey());
            // Geological/structure blocks win. Forest never overwrites a ruin or cave ceiling.
            if(!before.isAir())continue;
            var state=entry.getValue();chunk.setBlockState(entry.getKey(),state,false);
            // Native FULL postprocessing schedules LeavesBlock's normal distance updates
            // against the finished neighboring chunks; provisional plan distances are not permanent.
            if(state.getBlock() instanceof LeavesBlock)chunk.markPosForPostprocessing(entry.getKey());
            if(wood(state))logs++;else if(state.getBlock() instanceof LeavesBlock)leaves++;
            else if(state.is(GardenMaterials.CROWN_FRUIT.get()))fruits++;else vines++;
        }
        return new Counts(logs,leaves,fruits,vines);
    }
}
