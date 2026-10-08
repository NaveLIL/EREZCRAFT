package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.cave.CaveDensity;
import pro.erez.interstice.worldgen.cave.CaveFeatures;
import pro.erez.interstice.worldgen.cave.CaveMaterials;
import pro.erez.interstice.worldgen.terrain.TerrainV2;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class CaveGameTests {
    private static final long SEED=20261006L;
    private CaveGameTests() {}
    @GameTest(template="empty",timeoutTicks=200)
    public static void carvingNeverCreatesTerrainOrDamagesFoundationAndUpperShell(GameTestHelper h) {
        for(var profile:new GeometryProfile[]{GeometryProfile.LEGACY,GeometryProfile.TALL}) {
            for(int x=-64;x<=64;x+=8)for(int z=-64;z<=64;z+=8) {
                var column=CaveDensity.column(SEED,profile,x,z,.5,.5,null);
                for(int y=profile.minY();y<=profile.roof();y+=2) {
                    h.assertTrue(column.carve(y,-.3)==-.3,"Cave field created rock inside air");
                    double carved=column.carve(y,1);
                    h.assertTrue(carved<=1,"Cave field increased terrain density");
                    if(y<=profile.minY()+5||y>=profile.maxLand()-4)
                        h.assertTrue(carved==1,"Cave carved the foundation or the upper safety shell");
                }
            }
        }
        var roof=CaveDensity.column(SEED,GeometryProfile.TALL,0,0,.5,.5,y->y>=50?-1:1);
        for(int y=46;y<50;y++)h.assertTrue(roof.carve(y,1)==1,"Cave broke its four-block solid roof");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void undergroundVoidVolumeDiffersByBiome(GameTestHelper h) {
        int ash=0,garden=0,vault=0;
        for(int x=-256;x<=256;x+=8)for(int z=-256;z<=256;z+=8) {
            var a=CaveDensity.column(SEED,GeometryProfile.TALL,x,z,-.5,-.5,null);
            var g=CaveDensity.column(SEED,GeometryProfile.TALL,x,z,-.5,.5,null);
            var v=CaveDensity.column(SEED,GeometryProfile.TALL,x,z,.5,-.5,null);
            for(int y=10;y<190;y+=4) {
                if(a.carve(y,1)<0)ash++;
                if(g.carve(y,1)<0)garden++;
                if(v.carve(y,1)<0)vault++;
            }
        }
        h.assertTrue(ash>50,"Detached islands have no cave network");
        h.assertTrue(garden>ash*2,"Garden passages are not larger/more connected than ash caves");
        h.assertTrue(vault>garden*2,"Mountain chambers are not substantially larger than garden grottos");
        System.out.println("CAVE_DENSITY_VOLUMES ash="+ash+" gardens="+garden+" vaults="+vault);
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void caveFieldsAreIndependentOfChunkOrderAndRetainHighSeedBits(GameTestHelper h) {
        int different=0;
        for(int x:new int[]{-29999900,-257,-16,-1,0,15,16,257,29999900}) {
            var first=CaveDensity.column(SEED,GeometryProfile.TALL,x,71,.5,.5,null);
            for(int k=0;k<7;k++)CaveDensity.column(SEED+k+1,GeometryProfile.TALL,0,0,.5,.5,null).carve(70,1);
            var second=CaveDensity.column(SEED,GeometryProfile.TALL,x,71,.5,.5,null);
            var other=CaveDensity.column(SEED+(1L<<60),GeometryProfile.TALL,x,71,.5,.5,null);
            for(int y=10;y<190;y++) {
                h.assertTrue(Double.doubleToLongBits(first.carve(y,1))==Double.doubleToLongBits(second.carve(y,1)),"Cache eviction or another chunk changed cave geometry");
                if(first.carve(y,1)!=other.carve(y,1))different++;
            }
        }
        h.assertTrue(different>20,"Caves ignore the high world seed bits");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void naturalEntrancesHaveWalkableRampsAndRemainStableAcrossContexts(GameTestHelper h) {
        int found=0;
        for(double[] climate:new double[][]{{-.5,-.5},{-.5,.5},{.5,-.5}}) {
            java.util.function.BiFunction<Integer,Integer,TerrainV2.Column> raw=(x,z)->TerrainV2.column(SEED,GeometryProfile.TALL,x,z,climate[0],climate[1]);
            var context=CaveDensity.context(SEED,GeometryProfile.TALL,raw);
            CaveDensity.Entrance entrance=null;int chosenX=0,chosenZ=0;
            search:for(int cx=-6;cx<=6;cx++)for(int cz=-6;cz<=6;cz++) {
                var candidate=context.entrance(cx,cz);
                if(candidate.isPresent()){entrance=candidate.get();chosenX=cx;chosenZ=cz;break search;}
            }
            h.assertTrue(entrance!=null,"Biome has no discoverable walk-in cave in the sampled 1664-block region");
            var reordered=CaveDensity.context(SEED,GeometryProfile.TALL,raw);
            reordered.entrance(chosenX+1,chosenZ+1);
            h.assertTrue(reordered.entrance(chosenX,chosenZ).orElseThrow().equals(entrance),"Mouth plan depends on the context footprint/order");
            BlockPos previous=null;
            for(var pos:entrance.route()) {
                var terrain=raw.apply(pos.getX(),pos.getZ());
                var column=context.column(pos.getX(),pos.getZ(),climate[0],climate[1],terrain::density);
                h.assertTrue(column.carve(pos.getY()-1,terrain.density(pos.getY()-1))>0,"Walk-in mouth has a bottomless floor");
                for(int dy=0;dy<3;dy++)h.assertTrue(column.carve(pos.getY()+dy,terrain.density(pos.getY()+dy))<=0,"Walk-in ramp does not provide player headroom");
                if(previous!=null)h.assertTrue(Math.abs(pos.getY()-previous.getY())<=1,"Walk-in passage requires jumping multiple blocks");
                previous=pos;
            }
            System.out.println("CAVE_NATURAL_ENTRANCE climate="+climate[0]+","+climate[1]+" mouth="+entrance.mouth()+" inner="+entrance.inner()+" route="+entrance.route().size());
            found++;
        }
        h.assertTrue(found==3,"Not all terrain forms have exploratory cave mouths");
        h.succeed();
    }
    private static ProtoChunk empty(GameTestHelper h) {
        return new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),
                h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void roofDetectionRejectsOpenAirAndToxicFluidBeforeDecorating(GameTestHelper h) {
        var chunk=empty(h);var floor=new BlockPos(7,51,7);
        chunk.setBlockState(floor.below(),Interstice.RIFTSTONE.get().defaultBlockState(),false);
        h.assertTrue(CaveFeatures.roof(GeometryProfile.TALL,chunk,floor,64)==-1,"Open surface was mistaken for a cave");
        chunk.setBlockState(floor.atY(60),Interstice.RIFTSTONE.get().defaultBlockState(),false);
        h.assertTrue(CaveFeatures.roof(GeometryProfile.TALL,chunk,floor,64)==60,"Enclosed cavity has no detected ceiling");
        chunk.setBlockState(floor.atY(55),Interstice.HEAVY_BLOCK.get().defaultBlockState(),false);
        h.assertTrue(CaveFeatures.roof(GeometryProfile.TALL,chunk,floor,64)==-1,"A flooded cavity was decorated as dry air");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void spiresValidateWholeRunAndHaveSupportedDirectedTips(GameTestHelper h) {
        var chunk=empty(h);var root=new BlockPos(0,51,0);
        chunk.setBlockState(root.below(),Interstice.RIFTSTONE.get().defaultBlockState(),false);
        chunk.setBlockState(root.above(2),Blocks.STONE.defaultBlockState(),false);
        h.assertTrue(CaveFeatures.placeSpire(chunk,root,Direction.UP,3,CaveMaterials.ASH_SPIRE.get())==0,"Spire ignored an occupied tip");
        h.assertTrue(chunk.getBlockState(root).isAir(),"Failed spire placement left a partial base");
        chunk.setBlockState(root.above(2),Blocks.AIR.defaultBlockState(),false);
        h.assertTrue(CaveFeatures.placeSpire(chunk,root,Direction.UP,3,CaveMaterials.ASH_SPIRE.get())==3,"Safe edge-column spire was rejected");
        h.assertTrue(chunk.getBlockState(root).getValue(CaveMaterials.CaveSpireBlock.THICKNESS)==DripstoneThickness.BASE,"Spire lacks a stone base");
        h.assertTrue(chunk.getBlockState(root.above(2)).getValue(CaveMaterials.CaveSpireBlock.THICKNESS)==DripstoneThickness.TIP,"Spire lacks a narrow terminal tip");
        var ceiling=new BlockPos(15,60,15);
        chunk.setBlockState(ceiling.above(),Interstice.RIFTSTONE.get().defaultBlockState(),false);
        h.assertTrue(CaveFeatures.placeSpire(chunk,ceiling,Direction.DOWN,4,CaveMaterials.VAULT_SPIRE.get())==4,"Safe stalactite placement failed");
        h.assertTrue(chunk.getBlockState(ceiling.below(3)).getValue(CaveMaterials.CaveSpireBlock.TIP_DIRECTION)==Direction.DOWN,"Stalactite tip is inverted");
        h.succeed();
    }
}
