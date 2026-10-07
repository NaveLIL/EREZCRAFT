package pro.erez.interstice.test;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.OceanLiquidBlock;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;

/** Full runtime chunks, not a shortened or synthetic height accessor. */
@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class TallWorldGameTests {
    private static ServerLevel world(GameTestHelper h) {
        return java.util.Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD));
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void fullHeightChunksColumnsHeightmapsAndClearancesAgree(GameTestHelper h) {
        ServerLevel world=world(h);
        var generator=(IslandChunkGenerator)world.getChunkSource().getGenerator();
        var random=world.getChunkSource().randomState();
        var profile=GeometryProfile.TALL;
        h.assertTrue(GeometryProfiles.get(world).equals(profile) && world.getHeight()==256,"Tall world bounds/profile mismatch");
        int upperVoxels=0,landVoxels=0,columns=0;
        var pos=new BlockPos.MutableBlockPos();
        // Include both sides of a chunk seam and coordinates with negative X.
        for(int cx=-1;cx<=1;cx++) {
            var chunk=world.getChunk(cx,0);
            h.assertTrue(chunk.getHeight()==256 && GeometryProfiles.get(chunk).equals(profile),"Full chunk lost tall bounds");
            for(int x=cx*16;x<cx*16+16;x++) for(int z=0;z<16;z++) {
                var column=generator.getBaseColumn(x,z,world,random);
                double underside=SeaSurface.cellMinimum(profile,x,z,true);
                for(int y=0;y<256;y++) {
                    pos.set(x,y,z);var actual=chunk.getBlockState(pos);var base=column.getBlock(y);
                    if(y==0 || y==255) h.assertTrue(actual.is(Blocks.BEDROCK) && actual.equals(base),"Missing world shell at "+pos);
                    else if(y<=34) h.assertTrue(actual.is(Interstice.HEAVY_BLOCK.get()) && actual.equals(base),"Lower sea disagreement at "+pos);
                    else if(y>=Math.floor(underside)) {
                        h.assertTrue(actual.is(Interstice.LIGHT_SEA.get()) && actual.getValue(OceanLiquidBlock.CHAOTIC)
                                && actual.equals(base),"Tall sea missing/changed at "+pos);upperVoxels++;
                    } else {
                        // Surface rules replace stone with dirt/moss; compare density occupancy instead of material.
                        h.assertTrue(actual.isAir()==base.isAir(),"Real chunk/column density disagreement at "+pos);
                        h.assertTrue(actual.getFluidState().isEmpty(),"Fluid escaped into island band at "+pos);
                        if(!actual.isAir()) {
                            h.assertTrue(y>=41 && y<=205 && y+1<=underside-6,"Island violated sea clearance at "+pos);landVoxels++;
                        }
                    }
                }
                h.assertTrue(column.getBlock(-1).isAir() && column.getBlock(256).isAir(),"NoiseColumn bounds escaped");
                for(var type:new Heightmap.Types[]{Heightmap.Types.WORLD_SURFACE,Heightmap.Types.OCEAN_FLOOR,Heightmap.Types.MOTION_BLOCKING}) {
                    h.assertTrue(chunk.getHeight(type,x&15,z&15)==255,"Runtime heightmap omitted the roof");
                    h.assertTrue(generator.getBaseHeight(x,z,type,world,random)==256,"Base height was clipped to legacy bounds");
                }
                columns++;
            }
        }
        h.assertTrue(upperVoxels>0 && landVoxels>0,"Expected actual upper sea and island land");
        System.out.println("TALL_CHUNKS columns="+columns+" checked_voxels="+(columns*256)+" upper_voxels="+upperVoxels+" land_voxels="+landVoxels);
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void tallSerializationAndLegacyDimensionStaySeparate(GameTestHelper h) {
        var world=world(h);var generator=(IslandChunkGenerator)world.getChunkSource().getGenerator();
        var ops=RegistryOps.create(JsonOps.INSTANCE,world.registryAccess());
        var saved=IslandChunkGenerator.CODEC.codec().encodeStart(ops,generator).getOrThrow();
        var restored=IslandChunkGenerator.CODEC.codec().parse(ops,saved).getOrThrow();
        h.assertTrue(restored.geometry().equals(GeometryProfile.TALL),"Tall serialized profile lost");
        var absent=saved.getAsJsonObject().deepCopy();absent.remove("geometry");
        h.assertTrue(IslandChunkGenerator.CODEC.codec().parse(ops,absent).error().isPresent(),"Tall noise without geometry must not silently reinterpret old data");
        var legacy=java.util.Objects.requireNonNull(world.getServer().getLevel(IslandWorld.WORLD));
        h.assertTrue(legacy.getHeight()==128 && GeometryProfiles.get(legacy).equals(GeometryProfile.LEGACY),"Legacy dimension migrated unexpectedly");
        h.assertTrue(legacy.getChunk(0,0).getBlockState(new BlockPos(0,127,0)).is(Blocks.BEDROCK),"Legacy roof moved");
        h.assertTrue(Math.abs(SeaSurface.heightAt(GeometryProfile.TALL,7.375,9.625,true)-(0x1.6a424fd30a82fp6+128))<1e-12,"Translated relief differs from golden geometry");
        var random=world.getChunkSource().randomState();
        for(int x:new int[]{-33,0,17,100}) {
            var a=generator.getBaseColumn(x,7,world,random);var b=restored.getBaseColumn(x,7,world,random);
            for(int y=0;y<256;y++) h.assertTrue(a.getBlock(y).equals(b.getBlock(y)),"Tall codec changed generated column");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void landingSelectsRealIslandInsteadOfTheUpperSeaOrShell(GameTestHelper h) {
        var world=world(h);var landing=IslandWorld.findLanding(world);
        var floor=landing.below();var block=world.getBlockState(floor);
        h.assertTrue(floor.getY()>=41 && floor.getY()<=205,"Landing outside island band");
        h.assertTrue(!block.getCollisionShape(world,floor).isEmpty() && block.getFluidState().isEmpty()
                && !block.is(Blocks.BEDROCK),"Landing chose sea/shell instead of island ground");
        h.assertTrue(world.getBlockState(landing).isAir() && world.getBlockState(landing.above()).isAir(),"Landing lacks headroom");
        h.assertTrue(landing.getY()<=SeaSurface.cellMinimum(GeometryProfile.TALL,landing.getX(),landing.getZ(),true)-6,"Landing too close to upper sea");
        System.out.println("TALL_LANDING "+landing);
        h.succeed();
    }
}
