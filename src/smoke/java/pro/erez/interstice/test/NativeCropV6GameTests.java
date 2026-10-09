package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.NativeCropBlock;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.worldgen.NativeCropPatches;
import pro.erez.interstice.worldgen.RealmBiomes;

@GameTestHolder("interstice_agriculture")
@PrefixGameTestTemplate(false)
public final class NativeCropV6GameTests {
    private static ProtoChunk shore(GameTestHelper h,ChunkPos pos,ResourceKey<Biome> biome,boolean toxin){
        var registry=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        var chunk=new ProtoChunk(pos,UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),registry,null);
        chunk.fillBiomesFromNoise((x,y,z,s)->registry.getHolderOrThrow(biome),Climate.empty());
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
        for(int x=pos.getMinBlockX();x<=pos.getMaxBlockX();x++)for(int z=pos.getMinBlockZ();z<=pos.getMaxBlockZ();z++){
            var state=z==pos.getMinBlockZ()+8?(toxin?Interstice.HEAVY_BLOCK.get():Blocks.WATER):MineralEcology.ROOT_LOAM.get();
            chunk.setBlockState(new BlockPos(x,34,z),state.defaultBlockState(),false);
        }
        return chunk;
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void bothForestBanksHaveRareReproducibleSmallSeedColonies(GameTestHelper h){
        for(var biome:java.util.List.of(RealmBiomes.PALE_GARDENS,RealmBiomes.CRIMSON_THICKETS)){
            int found=0;
            for(long seed=0;seed<80&&found==0;seed++){
                var position=new ChunkPos(-2,3);
                var a=shore(h,position,biome,true);var b=shore(h,position,biome,true);
                found=NativeCropPatches.generate(GeometryProfile.TALL,a,seed,6);
                h.assertTrue(found==NativeCropPatches.generate(GeometryProfile.TALL,b,seed,6),"Same shore seed gives different colony count");
                int grain=0,root=0;
                for(int x=position.getMinBlockX();x<=position.getMaxBlockX();x++)for(int z=position.getMinBlockZ();z<=position.getMaxBlockZ();z++){
                    var at=new BlockPos(x,35,z);var state=a.getBlockState(at);
                    h.assertTrue(state.equals(b.getBlockState(at))&&a.getBlockState(at.below()).equals(b.getBlockState(at.below())),"Colony changes with repeated generation");
                    if(state.getBlock() instanceof NativeCropBlock){
                        h.assertTrue(a.getBlockState(at.below()).is(RealmAgriculture.FARMLAND.get()),"Wild planting lacks own soil");
                        h.assertTrue(x>position.getMinBlockX()&&x<position.getMaxBlockX()&&z>position.getMinBlockZ()&&z<position.getMaxBlockZ(),"Colony crosses its owner boundary");
                    }
                    if(state.is(RealmAgriculture.GRAIN_CROP.get()))grain++;
                    if(state.is(RealmAgriculture.ROOT_CROP.get()))root++;
                }
                if(found>0)h.assertTrue(found<=5&&grain>0&&root>0&&grain+root==found,"Wild colony loses one seed type or forms a crop carpet");
            }
            h.assertTrue(found>0,"Bounded wet forest bank fixture has no seed colony");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void ordinaryWaterAndArchivedCrimsonBanksDoNotProduceV6Colonies(GameTestHelper h){
        var position=new ChunkPos(1,-4);
        for(long seed=0;seed<40;seed++){
            var water=shore(h,position,RealmBiomes.CRIMSON_THICKETS,false);
            h.assertTrue(NativeCropPatches.generate(GeometryProfile.TALL,water,seed,6)==0,"Terrestrial water feeds a chemical seed colony");
            var archive=shore(h,position,RealmBiomes.CRIMSON_THICKETS,true);
            h.assertTrue(NativeCropPatches.generate(GeometryProfile.TALL,archive,seed,5)==0,"V6 changes archived crimson-bank placement");
        }
        h.succeed();
    }
}
