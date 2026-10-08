package pro.erez.interstice.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;

/** Living ash turf is retained; pale rock is a shallow buried resource and low flora grows on actual ground. */
public final class PaleGardens {
    private PaleGardens() {}
    public static void geology(GeometryProfile profile,ChunkAccess chunk,long seed) {
        geology(profile,chunk,seed,profile.minLand());
    }
    public static void geology(GeometryProfile profile,ChunkAccess chunk,long seed,int minimum) {
        var p=new BlockPos.MutableBlockPos();
        for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++) {
            int depth=0;
            for(int y=profile.maxLand();y>=minimum;y--) {
                p.set(x,y,z);var state=chunk.getBlockState(p);
                if(state.isAir()){depth=0;continue;}
                if(!StoneVaults.isGround(state)){depth=99;continue;}
                if(depth>=1 && depth<=3 && state.is(Interstice.RIFTSTONE.get()) && RealmBiomes.isGarden(chunk,p)
                        && Math.sin(x*.13+(seed&127))+Math.cos(z*.12-(seed&63))>.1)
                    chunk.setBlockState(p,GardenMaterials.PALESTONE.get().defaultBlockState(),false);
                depth++;
            }
        }
    }
    public static void undergrowth(GeometryProfile profile,ChunkAccess chunk,long seed) {
        undergrowth(profile,chunk,seed,profile.minLand());
    }
    public static void undergrowth(GeometryProfile profile,ChunkAccess chunk,long seed,int minimum) {
        var random=RandomSource.create(seed^chunk.getPos().toLong()^0x14CA5L);
        for(int attempt=0;attempt<14;attempt++) {
            int x=chunk.getPos().getMinBlockX()+random.nextInt(16),z=chunk.getPos().getMinBlockZ()+random.nextInt(16);
            for(int y=profile.maxLand();y>=minimum;y--) {
                var ground=new BlockPos(x,y,z);var state=chunk.getBlockState(ground);
                if(state.isAir())continue;
                var target=ground.above();
                if(state.is(Interstice.ABYSSAL_TURF.get()) && RealmBiomes.isGarden(chunk,ground)
                        && chunk.getBlockState(target).isAir() && IslandChunkGenerator.featureAllowed(profile,x,y+1,z,minimum))
                    chunk.setBlockState(target,(random.nextBoolean()?GardenMaterials.PALE_FERN:GardenMaterials.PALE_LITTER).get().defaultBlockState(),false);
                break;
            }
        }
    }
}
