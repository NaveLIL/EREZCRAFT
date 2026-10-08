package pro.erez.interstice.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.*;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.minerals.*;
import pro.erez.interstice.worldgen.terrain.FreeTerraNoise;

/** Rare shore seed colonies; own-chunk writes and the same actual chemical soil/water as a player farm. */
public final class NativeCropPatches {
    private NativeCropPatches(){}
    public static int generate(GeometryProfile p,ChunkAccess chunk,long seed,int revision){
        if(revision<4)return 0;
        long key=FreeTerraNoise.mix(seed^chunk.getPos().x*0x9E3779B97F4A7C15L^chunk.getPos().z*0xC2B2AE3D27D4EB4FL^0x4147524950415443L);
        if(Math.floorMod(key,12)!=0)return 0;
        var random=RandomSource.create(key);int baseX=chunk.getPos().getMinBlockX(),baseZ=chunk.getPos().getMinBlockZ(),y=p.lowerSeaTop();
        for(int attempt=0;attempt<10;attempt++){
            int x=baseX+2+random.nextInt(12),z=baseZ+2+random.nextInt(12);var soil=new BlockPos(x,y,z);
            if(!RealmBiomes.isGarden(chunk,soil)||!natural(chunk.getBlockState(soil))||!chunk.getBlockState(soil.above()).isAir()||!water(chunk,x,y,z))continue;
            var positions=new java.util.ArrayList<BlockPos>();
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
                var at=soil.offset(dx,0,dz);
                if(!natural(chunk.getBlockState(at))||!chunk.getBlockState(at.above()).isAir()||!water(chunk,at.getX(),y,at.getZ()))continue;
                positions.add(at);
            }
            if(positions.size()<2)continue;
            for(int i=positions.size()-1;i>0;i--){int other=random.nextInt(i+1);var temp=positions.get(i);positions.set(i,positions.get(other));positions.set(other,temp);}
            int placed=0,target=Math.min(positions.size(),2+random.nextInt(4));
            for(var at:positions){if(placed>=target)break;
                chunk.setBlockState(at,RealmAgriculture.FARMLAND.get().defaultBlockState().setValue(ToxicFarmlandBlock.MOISTURE,7),false);
                var crop=placed%2==0?RealmAgriculture.GRAIN_CROP.get():RealmAgriculture.ROOT_CROP.get();
                chunk.setBlockState(at.above(),crop.getStateForAge(crop.getMaxAge()),false);placed++;
            }return placed;
        }return 0;
    }
    private static boolean natural(net.minecraft.world.level.block.state.BlockState state){return state.is(Interstice.ABYSSAL_TURF.get())||state.is(MineralEcology.ROOT_LOAM.get())||state.is(MineralEcology.TOXIC_SAND.get())||RiftOreBlock.Host.from(state)!=null;}
    private static boolean water(ChunkAccess chunk,int x,int y,int z){
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
            int px=x+dx,pz=z+dz;if(px<chunk.getPos().getMinBlockX()||px>chunk.getPos().getMaxBlockX()||pz<chunk.getPos().getMinBlockZ()||pz>chunk.getPos().getMaxBlockZ())continue;
            for(int dy=0;dy<=1;dy++){var fluid=chunk.getFluidState(new BlockPos(px,y+dy,pz));if(fluid.is(Interstice.HEAVY.get())||fluid.is(Interstice.HEAVY_FLOW.get()))return true;}
        }return false;
    }
}
