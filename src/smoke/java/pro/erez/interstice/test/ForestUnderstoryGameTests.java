package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ecology.RealmEcology;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.*;
import pro.erez.interstice.worldgen.terrain.NativeColumnSampler;

@GameTestHolder("interstice_ecology") @PrefixGameTestTemplate(false)
public final class ForestUnderstoryGameTests {
    private static final GeometryProfile PROFILE=GeometryProfile.TALL;
    private static boolean cover(BlockState s){return s.is(GardenMaterials.PALE_FERN.get())||s.is(GardenMaterials.PALE_LITTER.get());}
    private static boolean hazard(BlockState s){return s.is(RealmEcology.VENOM_REED.get())||s.is(RealmEcology.SPORE_POD.get());}
    private static boolean plant(BlockState s){return cover(s)||hazard(s);}
    private static ProtoChunk prepared(GameTestHelper h){
        var c=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null){
            @Override public BlockState setBlockState(BlockPos pos,BlockState state,boolean moved){if(pos.getX()<0||pos.getX()>15||pos.getZ()<0||pos.getZ()>15)throw new IllegalStateException("Understory writes outside its owned chunk: "+pos);return super.setBlockState(pos,state,moved);}
        };
        var biome=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(RealmBiomes.CRIMSON_THICKETS);c.fillBiomesFromNoise((x,y,z,s)->biome,Climate.empty());c.setPersistedStatus(ChunkStatus.BIOMES);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){c.setBlockState(new BlockPos(x,50,z),Interstice.ABYSSAL_TURF.get().defaultBlockState(),false);c.setBlockState(new BlockPos(x,68,z),GardenMaterials.PALEHEART_LEAVES.get().defaultBlockState(),false);}
        for(int y=51;y<68;y++)c.setBlockState(new BlockPos(3,y,3),GardenMaterials.PALEHEART_LOG.get().defaultBlockState(),false);
        c.setBlockState(new BlockPos(5,55,5),Blocks.STONE.defaultBlockState(),false);return c;
    }
    private static boolean safeRoute(ProtoChunk c){
        var queue=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();
        for(int x=0;x<16;x++){var at=new BlockPos(x,51,0);if(!hazard(c.getBlockState(at))&&(c.getBlockState(at).isAir()||cover(c.getBlockState(at)))){seen.add(at);queue.add(at);}}
        while(!queue.isEmpty()){var at=queue.remove();if(at.getZ()==15)return true;for(var d:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST}){
            var next=at.relative(d);if(next.getX()<0||next.getX()>15||next.getZ()<0||next.getZ()>15||seen.contains(next))continue;var state=c.getBlockState(next);
            if(state.isAir()||cover(state)){seen.add(next);queue.add(next);}
        }}return false;
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void canopyDoesNotHideSoilAndLayeredCoverKeepsRealWalkGaps(GameTestHelper h){
        int totalCover=0,totalReeds=0,totalPods=0;
        for(long seed:new long[]{1,2,3,17,73,20261006,0x100000001L}){
            var c=prepared(h);ForestFlora.decorate(PROFILE,c,seed,h.getLevel(),5);int occupied=0;
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                var at=new BlockPos(x,51,z);var state=c.getBlockState(at);
                if(plant(state)){occupied++;h.assertTrue(c.getBlockState(at.below()).is(ForestFlora.FLOOR),"Ground cover is on a leaf, structure or invented soil");}
                if(cover(state))totalCover++;else if(state.is(RealmEcology.VENOM_REED.get()))totalReeds++;else if(state.is(RealmEcology.SPORE_POD.get())){
                    totalPods++;for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(x+dx>=0&&x+dx<16&&z+dz>=0&&z+dz<16)h.assertTrue(!c.getBlockState(at.offset(dx,0,dz)).is(GardenMaterials.PALE_FERN.get()),"Tall safe cover hides a telegraphed mine");
                }
            }
            h.assertTrue(occupied>=24&&occupied<210&&safeRoute(c),"Prepared real soil/canopy yields barren ground, a carpet or no hazard-free walking route");
            h.assertTrue(c.getBlockState(new BlockPos(3,51,3)).is(GardenMaterials.PALEHEART_LOG.get())&&c.getBlockState(new BlockPos(5,51,5)).isAir(),"Understory overwrote a trunk or searched below an opaque stone roof");
            var old=prepared(h);ForestFlora.decorate(PROFILE,old,seed,h.getLevel(),4);h.assertTrue(old.getBlockState(new BlockPos(2,51,2)).isAir(),"New undergrowth mutated an archived revision");
        }
        h.assertTrue(totalCover>=150&&totalReeds>5,"Eligible shaded forest lacks its actual safe/dangerous understory layers");System.out.println("V5_UNDERSTORY_PREPARED cover="+totalCover+" reeds="+totalReeds+" pods="+totalPods);h.succeed();
    }
    @GameTest(template="empty",batch="native_v5_ecology",timeoutTicks=1600)
    public static void actualNativeFullForestChunksContainSupportedVisibleUnderstory(GameTestHelper h){
        var level=Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.VANILLA_WORLD));var gen=(IslandChunkGenerator)level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        h.assertTrue(gen.terrainRevision()==5,"Native ecology acceptance is not in the new world");
        var selected=new ArrayList<ChunkPos>();var groups=new HashMap<String,Integer>();
        for(int z=-128;z<=128&&selected.size()<8;z+=8)for(int x=-128;x<=128&&selected.size()<8;x+=8){
            int bx=x*16+8,bz=z*16+8;var biome=gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(bx),12,QuartPos.fromBlock(bz),random.sampler());
            if(!biome.is(RealmBiomes.PALE_GARDENS)&&!biome.is(RealmBiomes.CRIMSON_THICKETS))continue;String id=biome.unwrapKey().orElseThrow().location().toString();if(groups.getOrDefault(id,0)>=4)continue;
            var column=NativeColumnSampler.of(random).column(bx,bz);int floor=0;for(int y=PROFILE.maxLand();y>PROFILE.lowerSeaTop()+1;y--)if(!column.getBlock(y).isAir()){floor=y;break;}
            if(floor<=PROFILE.lowerSeaTop()+1||floor>125)continue;
            groups.merge(id,1,Integer::sum);selected.add(new ChunkPos(x,z));
        }
        h.assertTrue(groups.size()==2&&selected.size()==8,"Bounded native climate sample lacks both live forest habitats");
        int covers=0,reeds=0,pods=0,wood=0;var supportKinds=new HashSet<Block>();var sampled=new HashSet<ChunkPos>();
        for(var center:selected)for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++){
            var pos=new ChunkPos(center.x+dx,center.z+dz);if(!sampled.add(pos))continue;var chunk=level.getChunk(pos.x,pos.z);
            for(int x=pos.getMinBlockX();x<=pos.getMaxBlockX();x++)for(int z=pos.getMinBlockZ();z<=pos.getMaxBlockZ();z++)for(int y=35;y<160;y++){
                var at=new BlockPos(x,y,z);var state=chunk.getBlockState(at);
                if(state.is(GardenMaterials.PALEHEART_LOG.get())||state.is(GardenMaterials.CROWN_LOG.get())||state.is(Interstice.GLOOMCROWN_LOG.get()))wood++;
                if(!plant(state))continue;var support=chunk.getBlockState(at.below());supportKinds.add(support.getBlock());
                h.assertTrue(support.is(ForestFlora.FLOOR)&&state.getFluidState().isEmpty(),"Installed FULL forest put ground flora on foliage/fluid/non-ground at "+at);
                var biome=chunk.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(y),QuartPos.fromBlock(z));h.assertTrue(biome.is(RealmBiomes.PALE_GARDENS)||biome.is(RealmBiomes.CRIMSON_THICKETS),"New ecology is outside its two selected habitats");
                if(cover(state))covers++;else if(state.is(RealmEcology.VENOM_REED.get()))reeds++;else pods++;
            }
        }
        h.assertTrue(wood>100&&covers>200&&reeds>4&&pods>0,"Actual installed forest is visually bare or lacks native hazard pockets: wood="+wood+" cover="+covers+" reed="+reeds+" pod="+pods);
        System.out.println("V5_NATIVE_FULL_ECOLOGY chunks="+sampled.size()+" biomes="+groups+" wood="+wood+" cover="+covers+" reed="+reeds+" pod="+pods+" supports="+supportKinds);h.succeed();
    }
}
