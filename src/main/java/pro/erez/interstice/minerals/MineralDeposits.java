package pro.erez.interstice.minerals;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.RealmBiomes;

/** Fixed vein budgets per chunk/biome: mountains do not get ore multiplied by their rock volume. */
public final class MineralDeposits {
    public enum Type { SILVER, COAL, PHOSPHORITE, VITRIOLITE }
    public enum Habitat { ASH, GARDENS, VAULTS }
    public record Counts(int silver,int coal,int phosphorite,int vitriolite){public int total(){return silver+coal+phosphorite+vitriolite;}}
    public record Rule(int attempts,int minY,int maxY,int minDepth,int maxDepth,int minVein,int maxVein,int chance){}
    private MineralDeposits(){}
    public static Rule rule(Type type,Habitat habitat,GeometryProfile p){
        int base=p.minY();
        return switch(type){
            case COAL->new Rule(habitat==Habitat.GARDENS?3:2,base+7,base+180,4,habitat==Habitat.VAULTS?64:28,5,8,1);
            case SILVER->new Rule(habitat==Habitat.VAULTS?3:habitat==Habitat.GARDENS?2:1,base+10,base+170,8,habitat==Habitat.VAULTS?100:40,3,6,1);
            case PHOSPHORITE->new Rule(habitat==Habitat.GARDENS?2:1,base+8,base+(habitat==Habitat.GARDENS?30:habitat==Habitat.VAULTS?90:120),5,64,3,5,1);
            case VITRIOLITE->new Rule(habitat==Habitat.VAULTS?1:0,base+7,base+42,20,256,2,4,2);
        };
    }
    private static long mix(long value){value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;return value^(value>>>31);}
    private static Habitat habitat(ChunkAccess chunk,BlockPos pos){return RealmBiomes.isVault(chunk,pos)?Habitat.VAULTS:RealmBiomes.isGarden(chunk,pos)?Habitat.GARDENS:Habitat.ASH;}
    private static Block block(Type type){return switch(type){case SILVER->MineralEcology.RIFTSILVER_SEAM.get();case COAL->MineralEcology.UMBRAL_COAL_ORE.get();case PHOSPHORITE->MineralEcology.PHOSPHORITE_ORE.get();case VITRIOLITE->MineralEcology.VITRIOLITE_ORE.get();};}
    private static int top(GeometryProfile p,ChunkAccess chunk,int x,int z,int[][] cache){
        int lx=x-chunk.getPos().getMinBlockX(),lz=z-chunk.getPos().getMinBlockZ();
        if(cache[lx][lz]!=Integer.MIN_VALUE)return cache[lx][lz];
        for(int y=p.maxLand();y>=p.minY()+6;y--){
            var state=chunk.getBlockState(new BlockPos(x,y,z));
            if(state.is(Interstice.ABYSSAL_TURF.get())||state.is(MineralEcology.ROOT_LOAM.get())||state.is(MineralEcology.TOXIC_SAND.get())||state.is(MineralEcology.MINERAL_POWDER.get())||RiftOreBlock.Host.from(state)!=null)return y;
        }
        return -1;
    }
    private static boolean eligible(GeometryProfile p,ChunkAccess chunk,BlockPos pos,Rule rule,Habitat habitat,int[][] cache){
        if(pos.getX()<chunk.getPos().getMinBlockX()||pos.getX()>chunk.getPos().getMaxBlockX()||pos.getZ()<chunk.getPos().getMinBlockZ()||pos.getZ()>chunk.getPos().getMaxBlockZ())return false;
        if(pos.getY()<rule.minY||pos.getY()>Math.min(rule.maxY,p.maxLand()-4)||habitat(chunk,pos)!=habitat)return false;
        if(RiftOreBlock.Host.from(chunk.getBlockState(pos))==null)return false;
        int lx=pos.getX()-chunk.getPos().getMinBlockX(),lz=pos.getZ()-chunk.getPos().getMinBlockZ();
        int surface=top(p,chunk,pos.getX(),pos.getZ(),cache);cache[lx][lz]=surface;
        int depth=surface-pos.getY();
        return depth>=rule.minDepth&&depth<=rule.maxDepth;
    }
    public static Counts generate(GeometryProfile profile,ChunkAccess chunk,long seed,int revision){
        if(revision<3)return new Counts(0,0,0,0);
        int[] count=new int[4];int startX=chunk.getPos().getMinBlockX(),startZ=chunk.getPos().getMinBlockZ();
        int[][] cache=new int[16][16];for(int[] row:cache)java.util.Arrays.fill(row,Integer.MIN_VALUE);
        Habitat biome=habitat(chunk,new BlockPos(startX+7,profile.lowerSeaTop()+8,startZ+7));
        for(Type type:Type.values()){
            var rule=rule(type,biome,profile);if(rule.attempts==0)continue;
            var random=RandomSource.create(mix(seed^chunk.getPos().x*341873128712L^chunk.getPos().z*132897987541L^((type.ordinal()+1)*0x71E53B0DL)));
            for(int vein=0;vein<rule.attempts;vein++){
                if(random.nextInt(rule.chance)!=0)continue;
                int x=startX+random.nextInt(16),z=startZ+random.nextInt(16);
                var depths=new ArrayList<Integer>();
                for(int y=rule.minY;y<=Math.min(rule.maxY,profile.maxLand()-4);y++)if(eligible(profile,chunk,new BlockPos(x,y,z),rule,biome,cache))depths.add(y);
                if(depths.isEmpty())continue;
                int y=depths.get(random.nextInt(depths.size())),size=rule.minVein+random.nextInt(rule.maxVein-rule.minVein+1);
                for(int i=0;i<size;i++){
                    var pos=new BlockPos(x,y,z);
                    if(eligible(profile,chunk,pos,rule,biome,cache)){
                        var host=RiftOreBlock.Host.from(chunk.getBlockState(pos));
                        chunk.setBlockState(pos,block(type).defaultBlockState().setValue(RiftOreBlock.HOST,host),false);count[type.ordinal()]++;
                    }
                    // Bounded connected walk; crossing a chunk border never reads or writes its neighbor.
                    switch(random.nextInt(6)){case 0->x++;case 1->x--;case 2->z++;case 3->z--;case 4->y++;default->y--;}
                }
            }
        }
        return new Counts(count[0],count[1],count[2],count[3]);
    }
}
