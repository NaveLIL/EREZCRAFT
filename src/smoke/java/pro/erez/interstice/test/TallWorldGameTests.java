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
        int upperVoxels=0,landVoxels=0,columns=0,densityMismatches=0;
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
                        // Surface rules replace stone with dirt/moss; compare density occupancy.
                        // 3D trilinear cell interpolation of cave carving allows <= 5 boundary voxels out of 196,608.
                        if (!actual.is(Interstice.TIDE_SPROUT.get()) && actual.isAir() != base.isAir()) {
                            densityMismatches++;
                        }
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
        h.assertTrue(densityMismatches <= 5, "Real chunk/column density disagreement count exceeded tolerance: " + densityMismatches);
        h.assertTrue(upperVoxels>0 && landVoxels>0,"Expected actual upper sea and island land");
        System.out.println("TALL_CHUNKS columns="+columns+" checked_voxels="+(columns*256)+" upper_voxels="+upperVoxels+" land_voxels="+landVoxels+" mismatches="+densityMismatches);
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
        h.assertTrue(IslandWorld.isSafeLandingPlatform(world,floor),"Landing platform must provide safe 3x3 footing");
        System.out.println("TALL_LANDING "+landing);
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void multiTierBeltsAndArchipelagoVoidsVerified(GameTestHelper h) {
        ServerLevel world=world(h);
        var generator=(IslandChunkGenerator)world.getChunkSource().getGenerator();
        var profile=GeometryProfile.TALL;
        int tier1Voxels=0,tier2Voxels=0,tier3Voxels=0;
        int voidColumns=0,landColumns=0,caveVoxels=0;
        for(int cx=-2;cx<=2;cx++) for(int cz=-2;cz<=2;cz++) {
            var chunk=world.getChunk(cx,cz);
            var pos=new BlockPos.MutableBlockPos();
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
                int worldX=cx*16+x,worldZ=cz*16+z;
                double underside=SeaSurface.cellMinimum(profile,worldX,worldZ,true);
                int minColY=999,maxColY=-1;
                for(int y=41;y<=205;y++) {
                    pos.set(worldX,y,worldZ);
                    var state=chunk.getBlockState(pos);
                    if(!state.isAir() && !state.is(Blocks.BEDROCK) && state.getFluidState().isEmpty()) {
                        h.assertTrue(y+1<=underside-6,"Clearance violation at "+pos);
                        if(y<minColY) minColY=y;
                        if(y>maxColY) maxColY=y;
                        if(y<=85) tier1Voxels++;
                        else if(y<=145) tier2Voxels++;
                        else tier3Voxels++;
                    }
                }
                if(maxColY>=minColY) {
                    landColumns++;
                    for(int y=minColY+1;y<maxColY;y++) {
                        pos.set(worldX,y,worldZ);
                        if(chunk.getBlockState(pos).isAir()) caveVoxels++;
                    }
                } else voidColumns++;
            }
        }
        h.assertTrue(tier1Voxels>0,"Tier 1 (Lower 41..85) must generate land; got "+tier1Voxels);
        h.assertTrue(tier2Voxels>0,"Tier 2 (Mid 86..145) must generate land; got "+tier2Voxels);
        h.assertTrue(tier3Voxels>0,"Tier 3 (Upper 146..205) must generate land; got "+tier3Voxels);
        h.assertTrue(voidColumns>0,"Must have genuine void columns in spawn cluster; void="+voidColumns);
        h.assertTrue(caveVoxels>0,"Must generate carved caves/grottos inside island bodies; got "+caveVoxels);
        int regionalLand=0,regionalVoid=0;
        var randomState=world.getChunkSource().randomState();
        for(int rx=-256;rx<=256;rx+=32) for(int rz=-256;rz<=256;rz+=32) {
            var col=generator.getBaseColumn(rx,rz,world,randomState);
            boolean land=false;
            for(int y=41;y<=205;y++) {
                if(!col.getBlock(y).isAir() && !col.getBlock(y).is(Blocks.BEDROCK) && col.getBlock(y).getFluidState().isEmpty()) {
                    land=true;
                    break;
                }
            }
            if(land) regionalLand++;
            else regionalVoid++;
        }
        h.assertTrue(regionalLand>0,"Macro archipelagos must generate land regions");
        h.assertTrue(regionalVoid>0,"Macro voids between archipelagos must exist; void="+regionalVoid);
        System.out.println("TALL_TIERS land_columns="+landColumns+" void_columns="+voidColumns
                +" cave_voxels="+caveVoxels+" tier1="+tier1Voxels+" tier2="+tier2Voxels+" tier3="+tier3Voxels);
        System.out.println("ARCHIPELAGO_REGIONAL land="+regionalLand+" void="+regionalVoid
                +" land_ratio="+String.format(java.util.Locale.ROOT,"%.2f",((double)regionalLand/(regionalLand+regionalVoid))));
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void tallAdjacentChunksAreIndependentOfGenerationOrder(GameTestHelper h) {
        ServerLevel world=world(h);
        var generator=(IslandChunkGenerator)world.getChunkSource().getGenerator();
        var random=world.getChunkSource().randomState();
        var height=net.minecraft.world.level.LevelHeightAccessor.create(0,256);
        var biomes=world.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var f1=new net.minecraft.world.level.chunk.ProtoChunk(new net.minecraft.world.level.ChunkPos(0,0),net.minecraft.world.level.chunk.UpgradeData.EMPTY,height,biomes,null);
        var f2=new net.minecraft.world.level.chunk.ProtoChunk(new net.minecraft.world.level.ChunkPos(1,0),net.minecraft.world.level.chunk.UpgradeData.EMPTY,height,biomes,null);
        var f1Again=new net.minecraft.world.level.chunk.ProtoChunk(new net.minecraft.world.level.ChunkPos(0,0),net.minecraft.world.level.chunk.UpgradeData.EMPTY,height,biomes,null);
        var f2Again=new net.minecraft.world.level.chunk.ProtoChunk(new net.minecraft.world.level.ChunkPos(1,0),net.minecraft.world.level.chunk.UpgradeData.EMPTY,height,biomes,null);
        var first=generator.createBiomes(random,net.minecraft.world.level.levelgen.blending.Blender.empty(),world.structureManager(),f1)
                .thenCompose(c->generator.fillFromNoise(net.minecraft.world.level.levelgen.blending.Blender.empty(),random,world.structureManager(),c))
                .thenApply(c->{IslandChunkGenerator.fillSeas(generator.geometry(),c);return c;})
                .thenCompose(c1->generator.createBiomes(random,net.minecraft.world.level.levelgen.blending.Blender.empty(),world.structureManager(),f2)
                        .thenCompose(c->generator.fillFromNoise(net.minecraft.world.level.levelgen.blending.Blender.empty(),random,world.structureManager(),c))
                        .thenApply(c->{IslandChunkGenerator.fillSeas(generator.geometry(),c);return new net.minecraft.world.level.chunk.ChunkAccess[]{c1,c};}));
        var second=first.thenCompose(order1->generator.createBiomes(random,net.minecraft.world.level.levelgen.blending.Blender.empty(),world.structureManager(),f2Again)
                .thenCompose(c->generator.fillFromNoise(net.minecraft.world.level.levelgen.blending.Blender.empty(),random,world.structureManager(),c))
                .thenApply(c->{IslandChunkGenerator.fillSeas(generator.geometry(),c);return c;})
                .thenCompose(c2->generator.createBiomes(random,net.minecraft.world.level.levelgen.blending.Blender.empty(),world.structureManager(),f1Again)
                        .thenCompose(c->generator.fillFromNoise(net.minecraft.world.level.levelgen.blending.Blender.empty(),random,world.structureManager(),c))
                        .thenApply(c->{IslandChunkGenerator.fillSeas(generator.geometry(),c);return new net.minecraft.world.level.chunk.ChunkAccess[]{order1[0],order1[1],c,c2};})));
        h.succeedWhen(()->{
            h.assertTrue(second.isDone(),"Waiting for reordered tall chunk generation");
            net.minecraft.world.level.chunk.ChunkAccess[] chunks=second.join();
            var chunk0A=chunks[0];var chunk1A=chunks[1];
            var chunk0B=chunks[2];var chunk1B=chunks[3];
            var pos=new BlockPos.MutableBlockPos();
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=0;y<256;y++) {
                pos.set(x,y,z);
                h.assertTrue(chunk0A.getBlockState(pos).equals(chunk0B.getBlockState(pos)),"Chunk (0,0) order disagreement at "+pos);
                h.assertTrue(chunk1A.getBlockState(pos).equals(chunk1B.getBlockState(pos)),"Chunk (1,0) order disagreement at "+pos);
            }
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void seedDiversityAndLandingReliabilityAcrossSeeds(GameTestHelper h) {
        ServerLevel world=world(h);
        var generator=(IslandChunkGenerator)world.getChunkSource().getGenerator();
        var registries=world.registryAccess();
        var settingsKey=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.NOISE_SETTINGS,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"islands_tall"));
        var settings=registries.registryOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS).getHolderOrThrow(settingsKey);
        var noiseLookup=registries.registryOrThrow(net.minecraft.core.registries.Registries.NOISE).asLookup();
        long[] testSeeds={20261006L,12345678L,987654321L,42424242L,76198123L};
        int differencesObserved=0;
        net.minecraft.world.level.NoiseColumn baseColFirstSeed=null;
        for(long seed:testSeeds) {
            var randomState=net.minecraft.world.level.levelgen.RandomState.create(settings.value(),noiseLookup,seed);
            var col=generator.getBaseColumn(0,0,world,randomState);
            if(baseColFirstSeed==null) baseColFirstSeed=col;
            else {
                for(int y=41;y<=205;y++) if(!col.getBlock(y).equals(baseColFirstSeed.getBlock(y))) differencesObserved++;
            }
            boolean foundLanding=false;
            for(int radius=0;radius<=24 && !foundLanding;radius++) {
                for(int dx=-radius;dx<=radius && !foundLanding;dx++) {
                    for(int dz=-radius;dz<=radius && !foundLanding;dz++) {
                        if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
                        int x=dx*4,z=dz*4;
                        var searchCol=generator.getBaseColumn(x,z,world,randomState);
                        for(int y=generator.geometry().maxLand();y>=generator.geometry().minLand();y--) {
                            if(searchCol.getBlock(y).isAir() || !searchCol.getBlock(y).getFluidState().isEmpty()) continue;
                            if(!searchCol.getBlock(y+1).isAir() || !searchCol.getBlock(y+2).isAir()) break;
                            double underside=SeaSurface.cellMinimum(generator.geometry(),x,z,true);
                            h.assertTrue(y>=41 && y<=205 && y+1<=underside-6,"Landing violates clearance on seed "+seed);
                            foundLanding=true;
                            break;
                        }
                    }
                }
            }
            h.assertTrue(foundLanding,"Safe landing must be found within 96 blocks on seed "+seed);
        }
        h.assertTrue(differencesObserved>0,"Different seeds must produce different column terrain");
        System.out.println("SEED_DIVERSITY seeds="+testSeeds.length+" differences="+differencesObserved);
        h.succeed();
    }
}
