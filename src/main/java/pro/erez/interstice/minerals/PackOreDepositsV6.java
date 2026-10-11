package pro.erez.interstice.minerals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.IslandWorld;

/** Optional pack resources, applied once during V6 SURFACE generation after the four native minerals. */
public final class PackOreDepositsV6 {
    /** Heights are relative to geometry.minY(); depth is uninterrupted dry opaque cover above the cell. */
    public record Deposit(String name, int attempts, int minY, int maxY, int minDepth, int maxDepth,
                          int minVein, int maxVein, int chance, List<ResourceLocation> alternatives) {
        public Deposit { alternatives=List.copyOf(alternatives); }
        public int maximumBlocks() { return attempts*maxVein; }
    }
    public record Resolved(Deposit deposit, ResourceLocation id, Block block) {}
    public record Counts(Map<String,Integer> byResource) {
        public Counts { byResource=Map.copyOf(byResource); }
        public int total() { return byResource.values().stream().mapToInt(Integer::intValue).sum(); }
    }
    private static Deposit ore(String name,int attempts,int minY,int maxY,int minDepth,int maxDepth,
                               int minVein,int maxVein,int chance,String... alternatives) {
        return new Deposit(name,attempts,minY,maxY,minDepth,maxDepth,minVein,maxVein,chance,
                java.util.Arrays.stream(alternatives).map(ResourceLocation::parse).toList());
    }
    private static final List<Deposit> DEPOSITS=List.of(
            ore("iron",3,7,150,4,96,4,7,1,"minecraft:iron_ore"),
            ore("copper",2,7,150,4,80,4,7,1,"minecraft:copper_ore"),
            ore("gold",1,7,90,8,96,3,5,2,"minecraft:gold_ore"),
            ore("redstone",2,7,54,10,96,3,6,1,"minecraft:redstone_ore"),
            ore("lapis",1,7,80,8,96,2,4,2,"minecraft:lapis_ore"),
            ore("diamond",1,7,36,14,128,1,3,4,"minecraft:diamond_ore"),
            ore("emerald",1,70,180,8,64,1,2,4,"minecraft:emerald_ore"),
            ore("zinc",2,7,140,4,96,3,6,1,"create:zinc_ore"),
            ore("osmium",2,7,110,6,96,3,6,1,"mekanism:osmium_ore"),
            ore("tin",2,7,120,6,96,3,6,1,"modern_industrialization:tin_ore","mekanism:tin_ore"),
            ore("lead",1,7,100,8,96,3,5,1,"modern_industrialization:lead_ore","mekanism:lead_ore"),
            ore("uranium",1,7,42,18,128,2,4,3,"modern_industrialization:uranium_ore","mekanism:uranium_ore"),
            ore("fluorite",1,7,54,10,96,3,5,2,"mekanism:fluorite_ore"),
            ore("bauxite",1,35,150,6,64,3,5,1,"modern_industrialization:bauxite_ore"),
            ore("nickel",1,7,90,10,96,2,4,2,"modern_industrialization:nickel_ore"),
            ore("antimony",1,7,90,10,96,2,4,2,"modern_industrialization:antimony_ore"),
            ore("lignite",1,7,60,6,96,3,5,2,"modern_industrialization:lignite_coal_ore"),
            ore("salt",1,7,70,6,96,3,5,2,"modern_industrialization:salt_ore"),
            ore("monazite",1,7,48,12,128,2,4,3,"modern_industrialization:monazite_ore"),
            ore("tungsten",1,7,30,20,128,2,3,5,"modern_industrialization:tungsten_ore"),
            ore("draconium",1,7,24,20,128,1,2,32,"draconicevolution:overworld_draconium_ore"));
    private static final Counts EMPTY=new Counts(Map.of());
    private static final Set<ResourceLocation> ORE_IDS=DEPOSITS.stream().flatMap(d->d.alternatives().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private PackOreDepositsV6() {}
    public static List<Deposit> deposits() { return DEPOSITS; }
    /** Generated ore remains solid geology to cave support and read-only terrain surveys. */
    public static boolean isOre(BlockState state) { return ORE_IDS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock())); }

    /** One choice per resource: MI/Mekanism tin, lead and uranium cannot double their ore budgets. */
    public static List<Resolved> resolve(Function<ResourceLocation,Optional<Block>> blocks) {
        var result=new ArrayList<Resolved>();
        for(var deposit:DEPOSITS)for(var id:deposit.alternatives()) {
            var block=blocks.apply(id);
            if(block.isPresent()) { result.add(new Resolved(deposit,id,block.get()));break; }
        }
        return List.copyOf(result);
    }
    public static boolean allows(ResourceKey<Level> dimension,int revision,ChunkAccess chunk) {
        // Existing SURFACE/FULL chunks are never retrogen targets, even if called by a future integration.
        return IslandWorld.TENSION_WORLD.equals(dimension)&&revision==6
                &&!chunk.getPersistedStatus().isOrAfter(ChunkStatus.SURFACE);
    }
    public static Counts generate(GeometryProfile profile,ChunkAccess chunk,long seed,
                                  ResourceKey<Level> dimension,int revision) {
        if(!allows(dimension,revision,chunk))return EMPTY;
        return generateResolved(profile,chunk,seed,dimension,revision,resolve(id->BuiltInRegistries.BLOCK.containsKey(id)
                ?BuiltInRegistries.BLOCK.getOptional(id):Optional.empty()));
    }
    /** Same implementation for registered optional blocks and component fixtures; no external Java APIs. */
    public static Counts generateResolved(GeometryProfile profile,ChunkAccess chunk,long seed,
                                          ResourceKey<Level> dimension,int revision,List<Resolved> resources) {
        if(!allows(dimension,revision,chunk))return EMPTY;
        profile.checkHeight(chunk);
        int startX=chunk.getPos().getMinBlockX(),startZ=chunk.getPos().getMinBlockZ();
        // Preserve every layer's exposed roof, not merely the highest heightmap surface in a column.
        int[][][] cover=new int[16][16][profile.height()];
        for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
            int depth=0;
            for(int y=profile.maxLand();y>=profile.minY();y--) {
                cover[x][z][y-profile.minY()]=depth;
                var state=chunk.getBlockState(new BlockPos(startX+x,y,startZ+z));
                depth=state.canOcclude()&&state.getFluidState().isEmpty()?depth+1:0;
            }
        }
        var counts=new LinkedHashMap<String,Integer>();
        for(var resource:resources) {
            var rule=resource.deposit();int placed=0;
            var random=RandomSource.create(mix(seed^chunk.getPos().x*341873128712L
                    ^chunk.getPos().z*132897987541L^salt(rule.name())));
            int low=profile.minY()+rule.minY(),high=Math.min(profile.minY()+rule.maxY(),profile.maxLand()-4);
            for(int attempt=0;attempt<rule.attempts();attempt++) {
                if(random.nextInt(rule.chance())!=0)continue;
                int x=startX+random.nextInt(16),z=startZ+random.nextInt(16);
                var candidates=new ArrayList<Integer>();
                for(int y=low;y<=high;y++)if(eligible(profile,chunk,new BlockPos(x,y,z),rule,cover))candidates.add(y);
                if(candidates.isEmpty())continue;
                var pos=new BlockPos(x,candidates.get(random.nextInt(candidates.size())),z);
                int size=rule.minVein()+random.nextInt(rule.maxVein()-rule.minVein()+1);
                for(int i=0;i<size;i++) {
                    if(!eligible(profile,chunk,pos,rule,cover))break;
                    chunk.setBlockState(pos,resource.block().defaultBlockState(),false);placed++;
                    // Every next cell shares a face with this ore; do not leap over air or foreign minerals.
                    var next=new ArrayList<BlockPos>();
                    for(var direction:Direction.values()) {
                        var neighbor=pos.relative(direction);
                        if(eligible(profile,chunk,neighbor,rule,cover))next.add(neighbor);
                    }
                    if(next.isEmpty())break;
                    pos=next.get(random.nextInt(next.size()));
                }
            }
            counts.put(rule.name(),placed);
        }
        return new Counts(counts);
    }
    private static boolean eligible(GeometryProfile profile,ChunkAccess chunk,BlockPos pos,Deposit rule,int[][][] cover) {
        int x=pos.getX()-chunk.getPos().getMinBlockX(),z=pos.getZ()-chunk.getPos().getMinBlockZ(),y=pos.getY();
        if(x<0||x>=16||z<0||z>=16||y<profile.minY()+rule.minY()
                ||y>Math.min(profile.minY()+rule.maxY(),profile.maxLand()-4))return false;
        int depth=cover[x][z][y-profile.minY()];
        return depth>=rule.minDepth()&&depth<=rule.maxDepth()
                &&RiftOreBlock.Host.from(chunk.getBlockState(pos))!=null;
    }
    private static long salt(String name) {
        long hash=0xCBF29CE484222325L;for(int i=0;i<name.length();i++)hash=(hash^name.charAt(i))*0x100000001B3L;
        return hash^0x56364F524553L;
    }
    private static long mix(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
}
