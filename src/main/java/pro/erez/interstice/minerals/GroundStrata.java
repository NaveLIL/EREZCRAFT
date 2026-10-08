package pro.erez.interstice.minerals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.RealmBiomes;
import pro.erez.interstice.worldgen.terrain.FreeTerraNoise;

/** Small connected soil/stone layers, applied to new V3 terrain before ores and structures. */
public final class GroundStrata {
    private GroundStrata(){}
    public static int generate(GeometryProfile profile,ChunkAccess chunk,long seed){
        int changes=0,startX=chunk.getPos().getMinBlockX(),startZ=chunk.getPos().getMinBlockZ();
        for(int x=startX;x<startX+16;x++)for(int z=startZ;z<startZ+16;z++){
            int surface=-1;
            for(int y=profile.maxLand();y>=profile.minY()+7;y--){var p=new BlockPos(x,y,z);var s=chunk.getBlockState(p);if(s.is(Interstice.ABYSSAL_TURF.get())||s.is(MineralEcology.TOXIC_SAND.get())||s.is(MineralEcology.MINERAL_POWDER.get())||RiftOreBlock.Host.from(s)!=null){surface=y;break;}}
            if(surface<0)continue;
            var probe=new BlockPos(x,surface,z);var top=chunk.getBlockState(probe);
            boolean garden=surface<115&&!top.is(MineralEcology.TOXIC_SAND.get())&&!top.is(MineralEcology.MINERAL_POWDER.get())&&!chunk.getBlockState(probe.above()).is(MineralEcology.MINERAL_FROST.get())&&RealmBiomes.isGarden(chunk,probe),vault=RealmBiomes.isVault(chunk,probe);
            int soil=1+Math.min(2,(int)(FreeTerraNoise.fractal(seed,0x524F4F544C4F414DL,x,z,42,2)*3));
            double pocket=FreeTerraNoise.fractal(seed,0x5348414C45504F43L,x,z,110,2);
            int offset=(int)(FreeTerraNoise.fractal(seed,0x5348414C45574152L,x,z,90,2)*6);
            for(int y=profile.minY()+7;y<surface;y++){
                var pos=new BlockPos(x,y,z);var state=chunk.getBlockState(pos);var host=RiftOreBlock.Host.from(state);
                if(host==null||host==RiftOreBlock.Host.RIFT_SHALE)continue;
                if(garden&&surface-y<=soil){chunk.setBlockState(pos,MineralEcology.ROOT_LOAM.get().defaultBlockState(),false);changes++;continue;}
                boolean deep=y<=profile.minY()+36,band=Math.floorMod(y+offset,24)<2;
                if(pocket>.56&&band&&(deep||vault)&&surface-y>=5){chunk.setBlockState(pos,MineralEcology.RIFT_SHALE.get().defaultBlockState(),false);changes++;}
            }
        }
        return changes;
    }
}
