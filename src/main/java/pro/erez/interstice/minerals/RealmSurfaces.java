package pro.erez.interstice.minerals;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.VaultMaterials;
import pro.erez.interstice.worldgen.terrain.FreeTerraNoise;
import pro.erez.interstice.worldgen.terrain.TerrainColumn;

/** V4-only coastal and alpine veneers. Own-chunk writes; ungenerated neighbor columns are numeric samples. */
public final class RealmSurfaces {
    public record Counts(int sand,int bareRock,int frost,int powder){}
    private RealmSurfaces(){}
    private static boolean natural(BlockState state){return RiftOreBlock.Host.from(state)!=null||state.is(Interstice.ABYSSAL_TURF.get())||state.is(MineralEcology.ROOT_LOAM.get());}
    private static boolean rock(BlockState state){return RiftOreBlock.Host.from(state)!=null;}
    private static int surface(GeometryProfile p,ChunkAccess chunk,int x,int z){for(int y=p.maxLand();y>p.minY()+6;y--)if(natural(chunk.getBlockState(new BlockPos(x,y,z))))return y;return -1;}
    private static int rawTop(GeometryProfile p,TerrainColumn c){for(int y=p.maxLand();y>p.minY()+6;y--)if(c.density(y)>0)return y;return p.minY();}
    public static Counts decorate(GeometryProfile profile,ChunkAccess chunk,long seed,BiFunction<Integer,Integer,? extends TerrainColumn> sourceColumns){
        var cache=new java.util.HashMap<Long,TerrainColumn>();var heights=new java.util.HashMap<Long,Integer>();
        BiFunction<Integer,Integer,TerrainColumn> columns=(x,z)->cache.computeIfAbsent(((long)x<<32)^(z&0xffffffffL),key->sourceColumns.apply(x,z));
        int sand=0,bare=0,frost=0,powder=0;
        for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++){
            int y=surface(profile,chunk,x,z);if(y<0)continue;var pos=new BlockPos(x,y,z);var raw=columns.apply(x,z);
            boolean shore=false;double slope=0;
            for(int[] offset:new int[][]{{4,0},{-4,0},{0,4},{0,-4},{8,0},{-8,0},{0,8},{0,-8}}){
                var other=columns.apply(x+offset[0],z+offset[1]);
                if(other.density(profile.lowerSeaTop())<=0)shore=true;
                long key=((long)(x+offset[0])<<32)^((z+offset[1])&0xffffffffL);
                int otherTop=heights.computeIfAbsent(key,k->rawTop(profile,other));
                slope=Math.max(slope,Math.abs(otherTop-y));
            }
            if(y>=profile.lowerSeaTop()-4&&y<=profile.lowerSeaTop()+5&&shore){
                int depth=1+(int)(FreeTerraNoise.fractal(seed,0x424541434853414EL,x,z,38,2)*2);
                boolean supported=true;
                for(int below=depth;below<=depth+1;below++)if(!rock(chunk.getBlockState(pos.below(below))))supported=false;
                if(supported)for(int d=0;d<depth;d++){var at=pos.below(d);if(natural(chunk.getBlockState(at))){chunk.setBlockState(at,MineralEcology.TOXIC_SAND.get().defaultBlockState(),false);sand++;}}
                continue;
            }
            boolean alpine=y>=115&&raw.weights().vaults()>.25;
            if((alpine||slope>=14)&&chunk.getBlockState(pos).is(Interstice.ABYSSAL_TURF.get())){chunk.setBlockState(pos,VaultMaterials.WEATHERED_VAULTSTONE.get().defaultBlockState(),false);bare++;}
            double threshold=145+(FreeTerraNoise.fractal(seed,0x46524F53544C494EL,x,z,120,2)-.5)*14;
            if(!alpine||y<threshold||slope>14||!chunk.getBlockState(pos.above()).isAir())continue;
            boolean floor=true;for(int d=1;d<=5;d++)if(!rock(chunk.getBlockState(pos.below(d))))floor=false;
            double pocket=FreeTerraNoise.fractal(seed,0x504F57444552504FL,x,z,34,2);
            if(slope<=6&&floor&&pocket>.62){
                chunk.setBlockState(pos,MineralEcology.MINERAL_POWDER.get().defaultBlockState(),false);
                chunk.setBlockState(pos.below(),MineralEcology.MINERAL_POWDER.get().defaultBlockState(),false);powder+=2;continue;
            }
            var at=pos.above();
            if(at.getY()>profile.maxLand()||at.getY()+1>SeaSurface.cellMinimum(profile,x,z,true)-profile.clearance())continue;
            int layers=Math.min(4,1+(int)Math.max(0,(y-threshold)/12));
            chunk.setBlockState(at,MineralEcology.MINERAL_FROST.get().defaultBlockState().setValue(SnowLayerBlock.LAYERS,layers),false);frost++;
        }
        return new Counts(sand,bare,frost,powder);
    }
}
