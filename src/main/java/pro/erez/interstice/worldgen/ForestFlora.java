package pro.erez.interstice.worldgen;

import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ecology.*;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.worldgen.terrain.FreeTerraNoise;
import java.util.HashSet;

/** V5-native understory. Global point suppression/cluster fields have no chunk-center anchor rows. */
public final class ForestFlora {
    public record Counts(int reeds,int pods){}
    private static final ResourceLocation FOREST=ResourceLocation.fromNamespaceAndPath(Interstice.ID,"crimson_thickets");
    public static final TagKey<Block> FLOOR=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"forest_floor"));
    private ForestFlora(){}
    private static long key(long seed,int x,int z,long salt){return FreeTerraNoise.mix(seed^x*0x9E3779B97F4A7C15L^z*0xC2B2AE3D27D4EB4FL^salt);}
    public static boolean mineAnchor(long seed,int x,int z){
        long rank=key(seed,x,z,0x53504F5245504F44L);if(Math.floorMod(rank,90)!=0)return false;
        for(int dx=-18;dx<=18;dx++)for(int dz=-18;dz<=18;dz++)if((dx!=0||dz!=0)&&dx*dx+dz*dz<=324){
            long other=key(seed,x+dx,z+dz,0x53504F5245504F44L);if(Math.floorMod(other,90)==0&&Long.compareUnsigned(other,rank)<0)return false;
        }return true;
    }
    private static boolean inside(ChunkAccess c,int x,int z){return x>=c.getPos().getMinBlockX()&&x<=c.getPos().getMaxBlockX()&&z>=c.getPos().getMinBlockZ()&&z<=c.getPos().getMaxBlockZ();}
    private static boolean eligibleBiome(ChunkAccess c,BlockPos p){var biome=c.getNoiseBiome(QuartPos.fromBlock(p.getX()),QuartPos.fromBlock(p.getY()),QuartPos.fromBlock(p.getZ()));return biome.is(RealmBiomes.PALE_GARDENS)||biome.unwrapKey().map(k->k.location().equals(FOREST)).orElse(false);}
    private static boolean forest(ChunkAccess c,BlockPos p){return c.getNoiseBiome(QuartPos.fromBlock(p.getX()),QuartPos.fromBlock(p.getY()),QuartPos.fromBlock(p.getZ())).unwrapKey().map(k->k.location().equals(FOREST)).orElse(false);}
    private static BlockPos surface(GeometryProfile profile,ChunkAccess c,int x,int z){
        for(int y=profile.maxLand();y>=profile.lowerSeaTop();y--){var p=new BlockPos(x,y,z);var state=c.getBlockState(p);
            if(state.isAir()||vegetation(state))continue;
            if(!state.getFluidState().isEmpty())return null;
            // Only the first genuine terrain surface counts. Do not tunnel through a stone
            // roof/structure to find a buried soil layer that happens to have air above it.
            if(state.is(FLOOR))return p.above();
            return null;
        }return null;
    }
    private static boolean vegetation(BlockState s){return leaves(s)||s.is(Interstice.GLOOMCROWN_LOG.get())||s.is(GardenMaterials.PALEHEART_LOG.get())||s.is(GardenMaterials.CROWN_LOG.get())
            ||s.is(GardenMaterials.PALE_VINE.get())||s.is(GardenMaterials.CROWN_FRUIT.get())||s.is(GardenMaterials.PALE_FERN.get())||s.is(GardenMaterials.PALE_LITTER.get())
            ||s.is(RealmEcology.VENOM_REED.get())||s.is(RealmEcology.SPORE_POD.get())||s.getBlock() instanceof pro.erez.interstice.TideSproutBlock;}
    private static boolean leaves(net.minecraft.world.level.block.state.BlockState state){return state.is(Interstice.GLOOMCROWN_LEAVES.get())||state.is(GardenMaterials.PALEHEART_LEAVES.get())||state.is(GardenMaterials.CROWN_LEAVES.get());}
    private static boolean canopy(GeometryProfile profile,ChunkAccess c,BlockPos p){
        for(int dx=-3;dx<=3;dx+=3)for(int dz=-3;dz<=3;dz+=3){int x=p.getX()+dx,z=p.getZ()+dz;if(!inside(c,x,z))continue;
            for(int dy=3;dy<=32&&p.getY()+dy<=profile.maxLand();dy++)if(leaves(c.getBlockState(new BlockPos(x,p.getY()+dy,z))))return true;
        }return false;
    }
    private static boolean space(GeometryProfile profile,ChunkAccess c,BlockPos p){return p!=null&&p.getY()>=profile.lowerSeaTop()+1&&IslandChunkGenerator.featureAllowed(profile,p.getX(),p.getY(),p.getZ(),profile.lowerSeaTop()+1)
            &&eligibleBiome(c,p)&&c.getBlockState(p).isAir()&&c.getBlockState(p.above()).isAir()&&c.getBlockState(p.below()).getFluidState().isEmpty();}
    public static Counts decorate(GeometryProfile profile,ChunkAccess chunk,long seed,WorldGenRegion region,int revision){return decorate(profile,chunk,seed,(LevelReader)region,revision);}
    public static Counts decorate(GeometryProfile profile,ChunkAccess chunk,long seed,LevelReader region,int revision){
        return decorate(profile,chunk,seed,region,revision,p->false);
    }
    public static Counts decorate(GeometryProfile profile,ChunkAccess chunk,long seed,LevelReader region,int revision,java.util.function.Predicate<BlockPos> walkway){
        if(revision!=5)return new Counts(0,0);int reeds=0,pods=0,minX=chunk.getPos().getMinBlockX(),minZ=chunk.getPos().getMinBlockZ();
        var visiblePods=new HashSet<Long>();
        // Expanded anchor search writes only current cells; clusters do not become empty border stripes.
        for(int x=minX-3;x<=minX+18;x++)for(int z=minZ-3;z<=minZ+18;z++)if(mineAnchor(seed,x,z)){
            if(FreeTerraNoise.fractal(seed,0x504F444649454C44L,x,z,43,3)<.45)continue;
            long plan=key(seed,x,z,0x434C5553544552L);int count=1+Math.floorMod(plan,3);
            for(int i=0;i<count;i++){
                long offset=FreeTerraNoise.mix(plan+i*0x9E3779B97F4A7C15L);int px=x+Math.floorMod(offset,5)-2,pz=z+Math.floorMod(offset>>>16,5)-2;
                // Predict the complete plan's quiet halo even over a chunk edge. This avoids
                // reading an already-decorated neighbor or hiding its mine under tall cover.
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)visiblePods.add(pack(px+dx,pz+dz));
                if(!inside(chunk,px,pz))continue;var p=surface(profile,chunk,px,pz);
                if(!space(profile,chunk,p)||walkway.test(p)||!canopy(profile,chunk,p)||!forest(chunk,p)&&Math.floorMod(key(seed,x,z,0x47415244454EL),3)!=0)continue;
                chunk.setBlockState(p,RealmEcology.SPORE_POD.get().defaultBlockState(),false);pods++;
            }
        }
        for(int x=minX;x<minX+16;x++)for(int z=minZ;z<minZ+16;z++){
            var p=surface(profile,chunk,x,z);
            if(!space(profile,chunk,p)||walkway.test(p)||!canopy(profile,chunk,p))continue;
            if(visiblePods.contains(pack(x,z)))continue;
            if(Math.floorMod(key(seed,x,z,0x52454544504C414EL),forest(chunk,p)?7:11)!=0)continue;
            double field=FreeTerraNoise.fractal(seed,0x554E44455253544FL,x,z,31,3);if(field<(forest(chunk,p)?.49:.52))continue;
            chunk.setBlockState(p,RealmEcology.VENOM_REED.get().defaultBlockState(),false);reeds++;
        }
        // Safe ground layer is separate from hazards and remains walkable. Broad coherent
        // patches and empty intervals replace sparse, canopy-blind random single stems.
        for(int x=minX;x<minX+16;x++)for(int z=minZ;z<minZ+16;z++){
            if(visiblePods.contains(pack(x,z)))continue;var p=surface(profile,chunk,x,z);
            if(!space(profile,chunk,p)||walkway.test(p)||!chunk.getBlockState(p.below()).is(Interstice.ABYSSAL_TURF.get()))continue;
            boolean shade=canopy(profile,chunk,p);double patch=FreeTerraNoise.fractal(seed,0x434F564552504154L,x,z,37,3);
            double chance=shade?.22+.33*patch:.05+.12*patch;
            double roll=(key(seed,x,z,0x434F564552524F4CL)>>>11)*0x1.0p-53;if(roll>=chance)continue;
            var state=(Math.floorMod(key(seed,x,z,0x434F564552545950L),3)==0?GardenMaterials.PALE_FERN:GardenMaterials.PALE_LITTER).get().defaultBlockState();
            chunk.setBlockState(p,state,false);
        }return new Counts(reeds,pods);
    }
    private static long pack(int x,int z){return((long)x<<32)^(z&0xffffffffL);}
}
