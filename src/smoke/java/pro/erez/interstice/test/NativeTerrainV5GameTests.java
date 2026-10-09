package pro.erez.interstice.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;

/** Real native NOISE chunks for six seed values; FULL chunks are checked separately in the installed world. */
@GameTestHolder("interstice_living")
@PrefixGameTestTemplate(false)
public final class NativeTerrainV5GameTests {
    private static final long[] SEEDS={0,1,-1,20261006L,76198123L,0x100000001L};
    private static final GeometryProfile PROFILE=GeometryProfile.TALL;
    private static final LevelHeightAccessor HEIGHT=LevelHeightAccessor.create(0,256);
    private record Fixture(IslandChunkGenerator generator,RandomState random,long seed) {}
    private record Sample(Fixture fixture,ProtoChunk west,ProtoChunk east,ProtoChunk westAgain,ProtoChunk eastAgain) {}
    private NativeTerrainV5GameTests() {}
    private static ServerLevel world(GameTestHelper h){return Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.VANILLA_WORLD),"V5 dimension is absent");}
    private static Fixture fixture(GameTestHelper h,long seed){
        var template=(IslandChunkGenerator)world(h).getChunkSource().getGenerator();
        var generator=new IslandChunkGenerator(template.getBiomeSource(),template.generatorSettings(),PROFILE,5);
        var random=RandomState.create(template.generatorSettings().value(),h.getLevel().registryAccess().registryOrThrow(Registries.NOISE).asLookup(),seed);
        generator.createState(h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup(),random,seed);
        return new Fixture(generator,random,seed);
    }
    private static CompletableFuture<ProtoChunk> noise(GameTestHelper h,Fixture f,int cx,int cz){
        System.out.println("NATIVE_V5_NOISE_STAGE seed="+f.seed+" chunk="+cx+","+cz+" stage=REQUEST_BIOMES thread="+Thread.currentThread().getName());
        var chunk=new ProtoChunk(new ChunkPos(cx,cz),UpgradeData.EMPTY,HEIGHT,h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        return f.generator.createBiomes(f.random,Blender.empty(),world(h).structureManager(),chunk)
                .thenApply(c->{((ProtoChunk)c).setPersistedStatus(ChunkStatus.BIOMES);System.out.println("NATIVE_V5_NOISE_STAGE seed="+f.seed+" chunk="+cx+","+cz+" stage=BIOMES_READY thread="+Thread.currentThread().getName());return c;})
                .thenCompose(c->{System.out.println("NATIVE_V5_NOISE_STAGE seed="+f.seed+" chunk="+cx+","+cz+" stage=REQUEST_NOISE thread="+Thread.currentThread().getName());return f.generator.fillFromNoise(Blender.empty(),f.random,world(h).structureManager(),c);})
                .thenApply(c->{((ProtoChunk)c).setPersistedStatus(ChunkStatus.NOISE);System.out.println("NATIVE_V5_NOISE_STAGE seed="+f.seed+" chunk="+cx+","+cz+" stage=NOISE_READY thread="+Thread.currentThread().getName());return (ProtoChunk)c;});
    }

    @GameTest(template="empty",batch="native_v5_noise",timeoutTicks=1200)
    public static void sixNativeSeedsHaveIdenticalCanonicalColumnsAndReverseOrder(GameTestHelper h){
        long started=System.nanoTime();
        CompletableFuture<List<Sample>> future=CompletableFuture.completedFuture(new ArrayList<>());
        for(long seed:SEEDS){var f=fixture(h,seed);
            future=future.thenCompose(list->noise(h,f,-1,0).thenCompose(w->noise(h,f,0,0).thenCompose(e->noise(h,f,0,0)
                    .thenCompose(e2->noise(h,f,-1,0).thenApply(w2->{list.add(new Sample(f,w,e,w2,e2));System.out.println("NATIVE_V5_NOISE_SEED_COMPLETE seed="+seed+" completed_seeds="+list.size()+" elapsed_wall_ms="+(System.nanoTime()-started)/1000000);return list;})))));
        }
        // Dedicated GameTestServer can advance 1200 test ticks in less than a second. A real
        // asynchronous worldgen worker receives the same bounded 60-second wall budget.
        // Native Beardifier requests structure-reference chunks from the live dimension.
        // managedBlock pumps the server queue, but MinecraftServer only pumps chunk queues
        // when haveTime() is true. Explicitly service this fixture's public chunk queue too.
        var pending=future.orTimeout(60,java.util.concurrent.TimeUnit.SECONDS);
        var v5=world(h);boolean[] diagnosed={false};
        h.getLevel().getServer().managedBlock(()->{
            if(pending.isDone())return true;
            v5.getChunkSource().pollTask();
            if(!pending.isDone()&&!diagnosed[0]&&System.nanoTime()-started>=10_000_000_000L){
                diagnosed[0]=true;
                System.out.println("NATIVE_V5_PENDING_THREAD_DUMP elapsed_wall_ms="+(System.nanoTime()-started)/1000000);
                for(var thread:java.lang.management.ManagementFactory.getThreadMXBean().dumpAllThreads(true,true)){
                    if(thread.getThreadName().equals("Server thread")||thread.getThreadName().startsWith("Worker-Main-"))System.out.println(thread);
                }
            }
            return pending.isDone();
        });
        {
            h.assertTrue(pending.isDone(),"Native NOISE promise did not complete within its bounded wall-clock wait");
            var report=new JsonArray();var hashes=new HashSet<Long>();
            for(var sample:pending.join()){
                long hash=0xCBF29CE484222325L;int dry=0,sea=0,rock=0;
                for(int x=-16;x<16;x+=3)for(int z=0;z<16;z+=3){
                    var first=x<0?sample.west:sample.east;var again=x<0?sample.westAgain:sample.eastAgain;
                    var column=sample.fixture.generator.getBaseColumn(x,z,HEIGHT,sample.fixture.random);
                    var uncarved=sample.fixture.generator.terrainColumn(sample.fixture.random,x,z);
                    for(int y=0;y<256;y++){
                        var p=new BlockPos(x,y,z);var raw=first.getBlockState(p);
                        h.assertTrue(raw.equals(again.getBlockState(p)),"Native chunk order changes seed "+sample.fixture.seed+" at "+p);
                        var mapped=sample.fixture.generator.terrainMaterialAt(x,y,z,raw,uncarved);
                        h.assertTrue(mapped.equals(column.getBlock(y)),"Native interpolation/public column disagree for seed "+sample.fixture.seed+" at "+p);
                        hash=(hash^net.minecraft.world.level.block.Block.getId(raw))*0x100000001B3L;
                        if(y>5&&y<=34){if(mapped.isAir())dry++;else if(mapped.is(Interstice.HEAVY_BLOCK.get()))sea++;else rock++;}
                    }
                    var a=first.getNoiseBiome(QuartPos.fromBlock(x),10,QuartPos.fromBlock(z));
                    h.assertTrue(a.equals(again.getNoiseBiome(QuartPos.fromBlock(x),10,QuartPos.fromBlock(z))),"Replay changed the actual chunk biome");
                }
                hashes.add(hash);var out=new JsonObject();out.addProperty("seed",sample.fixture.seed);out.addProperty("native_noise_hash",Long.toUnsignedString(hash));
                out.addProperty("subsea_dry_samples",dry);out.addProperty("subsea_ocean_samples",sea);out.addProperty("subsea_stone_samples",rock);report.add(out);
            }
            h.assertTrue(hashes.size()==SEEDS.length,"Native terrain ignores some seed values, including signed/high bits");
            System.out.println("NATIVE_V5_SIX_SEED_NOISE wall_ms="+(System.nanoTime()-started)/1000000+" "+report);
        }
        h.succeed();
    }

    @GameTest(template="empty",batch="native_v5_noise",timeoutTicks=600)
    public static void nativeClimateReportUsesSixWorldSeedsAndAllFourBiomes(GameTestHelper h){
        var report=new JsonArray();var observed=new HashSet<String>();
        for(long seed:SEEDS){var f=fixture(h,seed);var lengths=new ArrayList<Integer>();var counts=new java.util.TreeMap<String,Integer>();
            for(int z:new int[]{-1536,-512,512,1536}){
                String previous=null;int run=0;
                for(int x=-4096;x<=4096;x+=16){
                    var biome=f.generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),12,QuartPos.fromBlock(z),f.random.sampler());
                    String id=biome.unwrapKey().orElseThrow().location().toString();observed.add(id);counts.merge(id,1,Integer::sum);
                    if(id.equals(previous))run+=16;else{if(previous!=null)lengths.add(run);previous=id;run=16;}
                }
                lengths.add(run);
            }
            java.util.Collections.sort(lengths);int median=lengths.get(lengths.size()/2),p90=lengths.get((int)((lengths.size()-1)*.9));
            var out=new JsonObject();out.addProperty("seed",seed);out.addProperty("transitions",lengths.size()-4);out.addProperty("median_run_blocks",median);out.addProperty("p90_run_blocks",p90);
            var distribution=new JsonObject();counts.forEach(distribution::addProperty);out.add("biome_samples",distribution);report.add(out);
            h.assertTrue(lengths.size()>20&&median<1536,"The actual native climate still produces giant regions for seed "+seed+": "+out);
        }
        h.assertTrue(observed.size()==4,"Installed V5 climate does not contain the four planned biomes");
        System.out.println("NATIVE_V5_CLIMATE_REPORT "+report);h.succeed();
    }

    @GameTest(template="empty",batch="native_v5_full",timeoutTicks=1200)
    public static void actualFullChunksKeepToxicOceansAndTheOldGeometry(GameTestHelper h){
        var level=world(h);var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();
        var archive=Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.LIVING_WORLD));
        h.assertTrue(generator.terrainRevision()==5&&((IslandChunkGenerator)archive.getChunkSource().getGenerator()).terrainRevision()==4,"The new dimension replaced an archived revision");
        h.assertTrue(generator.geometry().equals(PROFILE)&&IslandWorld.isIsland(level.dimension()),"V5 lost the existing sea/tide geometry registration");
        long started=System.nanoTime();int stone=0,fluid=0;
        for(int cx:new int[]{-1,0}){
            var chunk=level.getChunk(cx,0);
            for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=0;z<16;z++){
                h.assertTrue(chunk.getBlockState(new BlockPos(x,0,z)).is(Blocks.BEDROCK)&&chunk.getBlockState(new BlockPos(x,255,z)).is(Blocks.BEDROCK),"FULL world shell is missing");
                double ceiling=SeaSurface.cellMinimum(PROFILE,x,z,true);
                for(int y=1;y<255;y++){
                    var p=new BlockPos(x,y,z);var state=chunk.getBlockState(p);
                    h.assertTrue(!state.is(Blocks.WATER)&&!state.is(Blocks.LAVA),"The native adapter introduced a vanilla sea/fluid");
                    if(y>=Math.floor(ceiling))h.assertTrue(state.is(Interstice.LIGHT_SEA.get()),"V5 changed the upper sea profile");
                    else if(y>PROFILE.maxLand())h.assertTrue(state.isAir(),"Natural terrain breaches the upper ocean clearance");
                    else if(state.is(Interstice.RIFTSTONE.get()))stone++;
                    if(state.is(Interstice.HEAVY_BLOCK.get()))fluid++;
                }
            }
        }
        h.assertTrue(stone>100,"FULL chunks did not contain real generated geology");
        System.out.println("NATIVE_V5_FULL_CHUNKS elapsed_ms="+(System.nanoTime()-started)/1000000+" riftstone="+stone+" heavy_fluid="+fluid);h.succeed();
    }

    @GameTest(template="empty",batch="native_v5_noise",timeoutTicks=200)
    public static void installedV5GeneratorSurvivesCodecWithoutChangingArchive(GameTestHelper h){
        var f=fixture(h,0x100000001L);var ops=RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess());
        var encoded=IslandChunkGenerator.CODEC.codec().encodeStart(ops,f.generator).getOrThrow();
        var restored=IslandChunkGenerator.CODEC.codec().parse(ops,encoded).getOrThrow();
        restored.createState(h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup(),f.random,f.seed);
        h.assertTrue(restored.terrainRevision()==5&&restored.geometry().equals(PROFILE),"Codec changed the separate terrain revision");
        for(int x:new int[]{-255,-1,0,119,751}){
            var a=f.generator.getBaseColumn(x,53,HEIGHT,f.random);var b=restored.getBaseColumn(x,53,HEIGHT,f.random);
            for(int y=0;y<256;y++)h.assertTrue(a.getBlock(y).equals(b.getBlock(y)),"Codec changed native terrain at "+x+","+y);
        }
        h.succeed();
    }
}
