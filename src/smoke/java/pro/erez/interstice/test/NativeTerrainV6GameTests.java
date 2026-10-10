package pro.erez.interstice.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.terrain.NativeColumnSamplerV6;
import pro.erez.interstice.worldgen.terrain.TensionTerrainV6;
import pro.erez.interstice.worldgen.terrain.V6MouthPlanner;

/** Fixed, unselected native samples. Synthetic seed fixtures are NOISE, never described as FULL worlds. */
@GameTestHolder("interstice_living")
@PrefixGameTestTemplate(false)
public final class NativeTerrainV6GameTests {
    private static final long[] SEEDS={0,1,-1,20261006L,76198123L,4294967297L};
    private static final GeometryProfile PROFILE=GeometryProfile.TALL;
    private static final LevelHeightAccessor HEIGHT=LevelHeightAccessor.create(PROFILE.minY(),PROFILE.height());
    // Four adjacent origin chunks plus predetermined positive/negative distant samples.
    private static final List<ChunkPos> POSITIONS=List.of(new ChunkPos(-1,-1),new ChunkPos(0,-1),
            new ChunkPos(-1,0),new ChunkPos(0,0),new ChunkPos(47,-32),new ChunkPos(-48,31));
    record Fixture(IslandChunkGenerator generator,RandomState random,long seed) {}
    private record Orders(Fixture fixture,List<ProtoChunk> canonical,List<ProtoChunk> reverse,
                          List<ProtoChunk> shuffled,List<ProtoChunk> parallel) {}
    private NativeTerrainV6GameTests() {}
    private static ServerLevel world(GameTestHelper h){
        return Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.TENSION_WORLD),"V6 dimension is absent");
    }
    static Fixture fixture(GameTestHelper h,long seed){
        var template=(IslandChunkGenerator)world(h).getChunkSource().getGenerator();
        var generator=new IslandChunkGenerator(template.getBiomeSource(),template.generatorSettings(),PROFILE,6);
        var random=RandomState.create(template.generatorSettings().value(),h.getLevel().registryAccess().registryOrThrow(Registries.NOISE).asLookup(),seed);
        generator.createState(h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup(),random,seed);
        return new Fixture(generator,random,seed);
    }
    static CompletableFuture<ProtoChunk> noise(GameTestHelper h,Fixture f,ChunkPos position){
        var chunk=new ProtoChunk(position,UpgradeData.EMPTY,HEIGHT,h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        return f.generator.createBiomes(f.random,Blender.empty(),world(h).structureManager(),chunk)
                .thenApply(c->{((ProtoChunk)c).setPersistedStatus(ChunkStatus.BIOMES);return c;})
                .thenCompose(c->f.generator.fillFromNoise(Blender.empty(),f.random,world(h).structureManager(),c))
                .thenApply(c->{((ProtoChunk)c).setPersistedStatus(ChunkStatus.NOISE);return (ProtoChunk)c;});
    }
    static CompletableFuture<List<ProtoChunk>> serial(GameTestHelper h,Fixture f,List<ChunkPos> order){
        CompletableFuture<List<ProtoChunk>> result=CompletableFuture.completedFuture(new ArrayList<>());
        for(var p:order)result=result.thenCompose(chunks->noise(h,f,p).thenApply(c->{chunks.add(c);return chunks;}));
        return result;
    }
    private static CompletableFuture<List<ProtoChunk>> parallel(GameTestHelper h,Fixture f){
        var pending=POSITIONS.stream().map(p->noise(h,f,p)).toList();
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).thenApply(ignored->pending.stream().map(CompletableFuture::join).toList());
    }
    static <T> T await(GameTestHelper h,CompletableFuture<T> future,int seconds){
        long began=System.nanoTime();var pending=future.orTimeout(seconds,TimeUnit.SECONDS);var live=world(h);boolean[] diagnosed={false};
        // A fast GameTestServer can exhaust simulated ticks before async worldgen runs.
        // Pump both public queues; retain a separate bounded wall-clock deadline.
        h.getLevel().getServer().managedBlock(()->{
            if(pending.isDone())return true;
            live.getChunkSource().pollTask();
            if(!diagnosed[0]&&System.nanoTime()-began>15_000_000_000L){
                diagnosed[0]=true;System.out.println("NATIVE_V6_PENDING wall_ms="+(System.nanoTime()-began)/1000000);
                for(var thread:java.lang.management.ManagementFactory.getThreadMXBean().dumpAllThreads(true,true))
                    if(thread.getThreadName().equals("Server thread")||thread.getThreadName().startsWith("Worker-Main-"))System.out.println(thread);
            }
            return pending.isDone();
        });return pending.join();
    }
    static Map<Long,ProtoChunk> index(List<ProtoChunk> chunks){
        var map=new LinkedHashMap<Long,ProtoChunk>();for(var c:chunks)map.put(c.getPos().toLong(),c);return map;
    }
    static long hash(ProtoChunk chunk){
        long hash=0xCBF29CE484222325L;var p=new BlockPos.MutableBlockPos();
        for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)
            for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)for(int y=PROFILE.minY();y<PROFILE.maxYExclusive();y++)
                hash=(hash^Block.getId(chunk.getBlockState(p.set(x,y,z))))*0x100000001B3L;
        for(int x=0;x<4;x++)for(int z=0;z<4;z++)for(int y=0;y<64;y++)
            hash=(hash^chunk.getNoiseBiome(QuartPos.fromBlock(chunk.getPos().getMinBlockX())+x,y,
                    QuartPos.fromBlock(chunk.getPos().getMinBlockZ())+z).unwrapKey().orElseThrow().location().toString().hashCode())*0x100000001B3L;
        return hash;
    }

    @GameTest(template="empty",batch="native_v6_mouth_routes",timeoutTicks=2400)
    public static void sixSeedMouthRoutesHaveActualNativeFloorsAndHeadroomWithoutAddingTerrain(GameTestHelper h){
        long began=System.nanoTime();var report=new JsonArray();
        for(long seed:SEEDS){
            var f=fixture(h,seed);var root=TensionTerrainV6.root(f.random);V6MouthPlanner.Mouth mouth=null;int searched=0;
            // Fixed before execution: at most256 world cells in [-1024,1024), without radius growth.
            outer:for(int cx=-8;cx<=7;cx++)for(int cz=-8;cz<=7;cz++){
                searched++;var plans=root.mouthPlanner().plansInCell(cx,cz);if(!plans.isEmpty()){mouth=plans.getFirst();break outer;}
            }
            h.assertTrue(mouth!=null,"No accepted V6 mouth route on seed "+seed+" in fixed1024 window; computed="+root.mouthPlanner().computedCells()+", budget_exhausted="+root.mouthPlanner().exhaustedCells());
            var positions=new java.util.TreeSet<ChunkPos>(java.util.Comparator.comparingLong(ChunkPos::toLong));
            for(var p:mouth.route())positions.add(new ChunkPos(p));
            var chunks=index(await(h,serial(h,f,List.copyOf(positions)),120));int steps=0;BlockPos previous=null;
            for(var p:mouth.route()){
                var chunk=chunks.get(new ChunkPos(p).toLong());
                h.assertTrue(!chunk.getBlockState(p.below()).isAir()&&chunk.getBlockState(p).isAir()&&chunk.getBlockState(p.above()).isAir(),
                        "An accepted entrance route differs from actual native NOISE floor/headroom: seed="+seed+" at="+p);
                if(previous!=null){int horizontal=Math.abs(p.getX()-previous.getX())+Math.abs(p.getZ()-previous.getZ());int dy=p.getY()-previous.getY();
                    h.assertTrue(horizontal==1&&Math.abs(dy)<=1,"Entrance route requires a diagonal jump or fabricated ladder");
                    var extra=dy>0?previous:p;if(dy!=0)h.assertTrue(chunks.get(new ChunkPos(extra).toLong()).getBlockState(extra.above(2)).isAir(),"Native stair transition lacks player headroom");
                }previous=p;steps++;
            }
            // Every actual raw rock cell in these chunks must already exist in the uncarved
            // canonical field; mouth floor preservation cannot manufacture a platform.
            var sampler=NativeColumnSamplerV6.of(f.random);
            for(var chunk:chunks.values())for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)for(int y=1;y<=PROFILE.maxLand();y++)
                if(!chunk.getBlockState(new BlockPos(x,y,z)).isAir())h.assertTrue(sampler.density(x,y,z,true)>0,"Mouth planner added terrain outside the canonical original mass");
            h.assertTrue(root.mouthPlanner().cachedCells()<=V6MouthPlanner.CACHE_LIMIT,"Seed-scoped entrance cache exceeds its finite budget");
            var row=new JsonObject();row.addProperty("seed",seed);row.addProperty("searched_fixed_cells",searched);row.addProperty("computed_cells",root.mouthPlanner().computedCells());
            row.addProperty("exhausted_cell_plans",root.mouthPlanner().exhaustedCells());row.addProperty("actual_noise_chunks",chunks.size());row.addProperty("actual_walk_route_nodes",steps);
            row.addProperty("original_internal_component_nodes",mouth.originalInternalNodes());row.addProperty("surface",mouth.surface().toShortString());row.addProperty("interior",mouth.interior().toShortString());report.add(row);
        }
        System.out.println("NATIVE_V6_MOUTH_ROUTES wall_ms="+(System.nanoTime()-began)/1000000+" "+report);h.succeed();
    }

    @GameTest(template="empty",batch="native_v6_noise",timeoutTicks=2400)
    public static void sixSeedsMatchEveryNativeColumnAcrossReverseShuffleAndParallel(GameTestHelper h){
        long began=System.nanoTime();
        var reverse=new ArrayList<>(POSITIONS);Collections.reverse(reverse);
        var shuffled=new ArrayList<>(POSITIONS);Collections.shuffle(shuffled,new Random(0x56365045524DL));
        CompletableFuture<List<Orders>> pending=CompletableFuture.completedFuture(new ArrayList<>());
        for(long seed:SEEDS){var f=fixture(h,seed);
            pending=pending.thenCompose(report->serial(h,f,POSITIONS).thenCompose(a->serial(h,f,reverse)
                    .thenCompose(b->serial(h,f,shuffled).thenCompose(c->parallel(h,f).thenApply(d->{
                        report.add(new Orders(f,a,b,c,d));System.out.println("NATIVE_V6_SEED_READY seed="+seed+" chunks=24 wall_ms="+(System.nanoTime()-began)/1000000);return report;
                    })))));
        }
        var results=await(h,pending,120);var report=new JsonArray();var worldHashes=new TreeMap<Long,Long>();
        for(var result:results){
            var reverseChunks=index(result.reverse);var shuffledChunks=index(result.shuffled);var parallelChunks=index(result.parallel);
            long combined=0xCBF29CE484222325L;int caveCells=0,multilayer=0,columns=0;
            for(var chunk:result.canonical){
                long digest=hash(chunk),key=chunk.getPos().toLong();combined=(combined^digest)*0x100000001B3L;
                h.assertTrue(digest==hash(reverseChunks.get(key))&&digest==hash(shuffledChunks.get(key))&&digest==hash(parallelChunks.get(key)),
                        "Native V6 block/biome hash changed by order or parallel workers for seed "+result.fixture.seed+" chunk "+chunk.getPos());
                var p=new BlockPos.MutableBlockPos();var sampler=NativeColumnSamplerV6.of(result.fixture.random,result.fixture.generator.generatorSettings().value().noiseSettings());
                for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++){
                    var column=result.fixture.generator.getBaseColumn(x,z,HEIGHT,result.fixture.random);
                    var uncarved=result.fixture.generator.terrainColumn(result.fixture.random,x,z);int intervals=0;boolean previous=false;
                    for(int y=PROFILE.minY();y<PROFILE.maxYExclusive();y++){
                        var raw=chunk.getBlockState(p.set(x,y,z));boolean solid=!raw.isAir();
                        h.assertTrue(solid==(sampler.density(x,y,z)>0),"V6 sampler/native NOISE mismatch seed="+result.fixture.seed+" pos="+p);
                        var mapped=result.fixture.generator.terrainMaterialAt(x,y,z,raw,uncarved);
                        h.assertTrue(mapped.equals(column.getBlock(y)),"V6 public column/native material mismatch seed="+result.fixture.seed+" pos="+p);
                        if(y>=PROFILE.minLand()&&y<=PROFILE.maxLand()){if(solid&&!previous)intervals++;previous=solid;}
                        if(!solid&&sampler.density(x,y,z,true)>0){
                            caveCells++;h.assertTrue(y>PROFILE.lowerSeaTop()+5,"V6 subtractive cave enters protected lower sea band at "+p);
                        }
                    }
                    if(intervals>1)multilayer++;columns++;
                    for(var type:List.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG)){
                        int expected=PROFILE.minY();for(int y=PROFILE.maxYExclusive()-1;y>=PROFILE.minY();y--)if(type.isOpaque().test(column.getBlock(y))){expected=y+1;break;}
                        h.assertTrue(result.fixture.generator.getBaseHeight(x,z,type,HEIGHT,result.fixture.random)==expected,"V6 public height differs from its public column at "+x+","+z);
                    }
                }
            }
            worldHashes.put(result.fixture.seed,combined);var row=new JsonObject();row.addProperty("seed",result.fixture.seed);
            row.addProperty("raw_noise_and_biome_hash",Long.toUnsignedString(combined));row.addProperty("chunks_per_order",POSITIONS.size());
            row.addProperty("all_xyz_sampled",true);row.addProperty("sampled_columns",columns);row.addProperty("multilayer_columns",multilayer);row.addProperty("subtractive_cave_cells",caveCells);report.add(row);
        }
        h.assertTrue(!worldHashes.get(1L).equals(worldHashes.get(4294967297L)),"Native V6 aliases seeds with identical low 32 bits");
        h.assertTrue(worldHashes.values().stream().distinct().count()==SEEDS.length,"Fixed native V6 samples failed to distinguish the six seed worlds");
        System.out.println("NATIVE_V6_SIX_SEED_NOISE wall_ms="+(System.nanoTime()-began)/1000000+" "+report);h.succeed();
    }

    @GameTest(template="empty",batch="native_v6_numeric",timeoutTicks=2400)
    public static void fixedGridReportsEveryBiomeLayersAndActualCarvedHeights(GameTestHelper h){
        long began=System.nanoTime();var report=new JsonArray();
        // Fixed before inspecting output: 33x33 columns, spacing64, radius1024, all six seeds.
        for(long seed:SEEDS){var f=fixture(h,seed);var sampler=NativeColumnSamplerV6.of(f.random);var root=TensionTerrainV6.root(f.random);
            var biomes=new TreeMap<String,Integer>();var morphologies=new TreeMap<String,Integer>();var heights=new ArrayList<Integer>();var widths=new ArrayList<Integer>();
            int sampled=0,multi=0,caves=0;double grounded=0,ash=0,vault=0;
            for(int x=-1024;x<=1024;x+=64)for(int z=-1024;z<=1024;z+=64){
                var biome=f.generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),12,QuartPos.fromBlock(z),f.random.sampler());
                biomes.merge(biome.unwrapKey().orElseThrow().location().toString(),1,Integer::sum);
                var morphology=root.morphology(x,z);morphologies.merge(morphology.dominant(),1,Integer::sum);
                grounded+=morphology.gardens()+morphology.crimson();ash+=morphology.ash();vault+=morphology.vaults();
                int intervals=0,run=0;boolean lastRock=false;
                for(int y=PROFILE.lowerSeaTop()+1;y<=PROFILE.maxLand();y++){
                    boolean uncarved=sampler.density(x,y,z,true)>0,rock=sampler.density(x,y,z)>0;
                    if(uncarved&&!lastRock)intervals++;lastRock=uncarved;
                    if(uncarved&&!rock){run++;caves++;h.assertTrue(y>PROFILE.lowerSeaTop()+5,"Fixed-grid cave crossed the lower sea safety boundary");}
                    else if(run>0){heights.add(run);widths.add(caveCrossSection(sampler,x,y-1-run/2,z));run=0;}
                }
                if(run>0){heights.add(run);widths.add(caveCrossSection(sampler,x,PROFILE.maxLand()-run/2,z));}if(intervals>=2)multi++;sampled++;
            }
            Collections.sort(heights);Collections.sort(widths);var row=new JsonObject();row.addProperty("seed",seed);row.addProperty("sampling_scope","canonical interpolated numeric field, not FULL chunks");
            row.addProperty("radius_blocks",1024);row.addProperty("grid_spacing",64);row.addProperty("columns",sampled);row.addProperty("multilayer_columns",multi);
            row.addProperty("subtractive_cave_cells",caves);row.addProperty("cavity_vertical_runs",heights.size());
            row.addProperty("cavity_height_p50",percentile(heights,.50));row.addProperty("cavity_height_p95",percentile(heights,.95));row.addProperty("cavity_height_max",heights.isEmpty()?0:heights.getLast());
            row.addProperty("cavity_short_axis_width_p50",percentile(widths,.50));row.addProperty("cavity_short_axis_width_p95",percentile(widths,.95));
            row.addProperty("cavity_short_axis_width_max",widths.isEmpty()?0:widths.getLast());
            row.addProperty("width_measurement","shorter of two cardinal removed-rock runs at each sampled vertical cavity midpoint; radius32 cap; not connected-component diameter");
            row.addProperty("width_radius32_truncated_samples",widths.stream().filter(v->v>=65).count());
            row.addProperty("mean_grounded_weight",grounded/sampled);row.addProperty("mean_ash_weight",ash/sampled);row.addProperty("mean_vault_weight",vault/sampled);
            var biomeJson=new JsonObject();biomes.forEach(biomeJson::addProperty);row.add("biomes",biomeJson);
            var shapeJson=new JsonObject();morphologies.forEach(shapeJson::addProperty);row.add("morphologies",shapeJson);report.add(row);
            h.assertTrue(biomes.size()==4,"Fixed radius1024 grid did not find all four V6 biomes on seed "+seed+": "+row);
            h.assertTrue(morphologies.size()>=3&&multi>0,"Fixed V6 grid lacks distinct morphology/real separate rock intervals on seed "+seed+": "+row);
            h.assertTrue(!heights.isEmpty(),"Fixed V6 grid contains no real subtractive cave samples for seed "+seed+": "+row);
            h.assertTrue(percentile(widths,.95)<=14,"More than five percent of fixed-grid sampled V6 caves exceed the requested rare-room width limit: "+row);
        }
        System.out.println("NATIVE_V6_FIXED_GRID wall_ms="+(System.nanoTime()-began)/1000000+" "+report);h.succeed();
    }
    private static int percentile(List<Integer> values,double fraction){return values.isEmpty()?0:values.get((int)Math.floor((values.size()-1)*fraction));}
    private static boolean removedRock(NativeColumnSamplerV6 sampler,int x,int y,int z){return sampler.density(x,y,z,true)>0&&sampler.density(x,y,z)<=0;}
    private static int caveCrossSection(NativeColumnSamplerV6 sampler,int x,int y,int z){
        int alongX=1,alongZ=1;
        for(int direction:new int[]{-1,1}){
            for(int d=1;d<=32&&removedRock(sampler,x+direction*d,y,z);d++)alongX++;
            for(int d=1;d<=32&&removedRock(sampler,x,y,z+direction*d);d++)alongZ++;
        }
        return Math.min(alongX,alongZ);
    }

    @GameTest(template="empty",batch="native_v6_numeric",timeoutTicks=600)
    public static void codecAndExtremeSeedsKeepSettingsAndRejectCellMismatch(GameTestHelper h){
        var f=fixture(h,4294967297L);var ops=RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess());
        var encoded=IslandChunkGenerator.CODEC.codec().encodeStart(ops,f.generator).getOrThrow();
        var decoded=IslandChunkGenerator.CODEC.codec().parse(ops,encoded).getOrThrow();
        decoded.createState(h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup(),f.random,f.seed);
        h.assertTrue(decoded.terrainRevision()==6&&decoded.geometry().equals(PROFILE),"V6 codec lost revision or sea geometry");
        for(int x:new int[]{-513,-17,-1,0,15,16,511}){
            var before=f.generator.getBaseColumn(x,-17,HEIGHT,f.random);var after=decoded.getBaseColumn(x,-17,HEIGHT,f.random);
            for(int y=0;y<256;y++)h.assertTrue(before.getBlock(y).equals(after.getBlock(y)),"V6 codec changed column at "+x+","+y);
        }
        var wrong=encoded.deepCopy().getAsJsonObject();
        var wrongSettings=NoiseGeneratorSettings.DIRECT_CODEC.encodeStart(ops,f.generator.generatorSettings().value()).getOrThrow().getAsJsonObject();
        wrongSettings.getAsJsonObject("noise").addProperty("size_vertical",2);wrong.add("settings",wrongSettings);
        h.assertTrue(IslandChunkGenerator.CODEC.codec().parse(ops,wrong).error().isPresent(),"V6 codec accepted a different generator interpolation cell than its sampler");
        boolean rejected=false;try{NativeColumnSamplerV6.of(f.random,NoiseSettings.create(0,256,1,2));}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected,"V6 sampler accepted mismatched native cell sizes");
        var report=new JsonArray();
        for(long seed:new long[]{Long.MIN_VALUE,Long.MAX_VALUE}){var extreme=fixture(h,seed);var sampler=NativeColumnSamplerV6.of(extreme.random);long digest=0xCBF29CE484222325L;
            for(int x:new int[]{-30_000_000,-513,-17,-1,0,15,16,30_000_000})for(int z:new int[]{-30_000_000,-1,0,30_000_000})for(int y:new int[]{0,33,34,40,63,64,127,128,205,255}){
                double a=sampler.density(x,y,z),b=sampler.density(x,y,z,true);
                h.assertTrue(Double.isFinite(a)&&Double.isFinite(b),"Extreme V6 seed/coordinate produced a nonfinite density");
                h.assertTrue(a==sampler.density(x,y,z),"An intervening extreme sample changed a seeded V6 cached corner");
                digest=(digest^Double.doubleToLongBits(a))*0x100000001B3L;
            }
            var row=new JsonObject();row.addProperty("seed",seed);row.addProperty("numeric_hash",Long.toUnsignedString(digest));report.add(row);
        }
        for(var key:List.of(IslandWorld.WORLD,IslandWorld.TALL_WORLD,IslandWorld.DRAFT_WORLD,IslandWorld.PREVIOUS_WORLD,IslandWorld.LIVING_WORLD,IslandWorld.VANILLA_WORLD)){
            var archive=Objects.requireNonNull(h.getLevel().getServer().getLevel(key));
            h.assertTrue(((IslandChunkGenerator)archive.getChunkSource().getGenerator()).terrainRevision()<6,"V6 replaced an archived generator at "+key.location());
        }
        System.out.println("NATIVE_V6_CODEC_EXTREME "+report);h.succeed();
    }

    @GameTest(template="empty",batch="native_v6_full",timeoutTicks=2400)
    public static void liveSeedFullNeighborsContainFourBiomesAndKeepBothSeas(GameTestHelper h){
        var level=world(h);var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        var found=new TreeMap<String,BlockPos>();long began=System.nanoTime();
        // Declared bounded search. A missing biome/land sample is reported, never silently expanded.
        for(int x=-2048;x<=2048;x+=64)for(int z=-2048;z<=2048;z+=64){
            var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),12,QuartPos.fromBlock(z),random.sampler());
            String id=biome.unwrapKey().orElseThrow().location().toString();if(found.containsKey(id))continue;
            var column=generator.getBaseColumn(x,z,HEIGHT,random);int top=-1;
            for(int y=PROFILE.maxLand();y>PROFILE.lowerSeaTop()+1;y--)if(!column.getBlock(y).isAir()&&column.getBlock(y).getFluidState().isEmpty()){top=y;break;}
            if(top>PROFILE.lowerSeaTop()+1)found.put(id,new BlockPos(x,top,z));
        }
        System.out.println("NATIVE_V6_FULL_SEARCH seed="+level.getSeed()+" radius=2048 spacing=64 found="+found);
        h.assertTrue(found.size()==4,"Live seed "+level.getSeed()+" lacks four natural biome land points in fixed FULL search: "+found);
        var report=new JsonArray();int checked=0;
        for(var entry:found.entrySet()){
            var center=entry.getValue();var scene=new JsonObject();scene.addProperty("biome",entry.getKey());scene.addProperty("x",center.getX());scene.addProperty("z",center.getZ());
            var chunks=new JsonArray();
            for(int dx=0;dx<=1;dx++){
                var chunk=level.getChunk(Math.floorDiv(center.getX(),16)+dx,Math.floorDiv(center.getZ(),16));
                h.assertTrue(chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL),"V6 FULL test received an earlier chunk stage");
                int rock=0,heavy=0;var p=new BlockPos.MutableBlockPos();
                for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++){
                    h.assertTrue(chunk.getBlockState(p.set(x,0,z)).is(Blocks.BEDROCK)&&chunk.getBlockState(p.set(x,255,z)).is(Blocks.BEDROCK),"V6 FULL shell is missing");
                    double ceiling=SeaSurface.cellMinimum(PROFILE,x,z,true);
                    for(int y=1;y<255;y++){
                        var state=chunk.getBlockState(p.set(x,y,z));
                        h.assertTrue(!state.is(Blocks.WATER)&&!state.is(Blocks.LAVA),"V6 introduced a vanilla fluid");
                        if(y>=Math.floor(ceiling))h.assertTrue(state.is(Interstice.LIGHT_SEA.get()),"V6 FULL changed the upper sea surface");
                        else if(y>PROFILE.maxLand())h.assertTrue(state.isAir(),"V6 natural terrain breached upper clearance");
                        if(state.is(Interstice.RIFTSTONE.get()))rock++;if(state.is(Interstice.HEAVY_BLOCK.get()))heavy++;
                    }
                }
                var actual=chunk.getNoiseBiome(QuartPos.fromBlock(center.getX()),QuartPos.fromBlock(center.getY()),QuartPos.fromBlock(center.getZ()));
                if(dx==0)h.assertTrue(actual.unwrapKey().orElseThrow().location().toString().equals(entry.getKey()),"Real V6 FULL biome differs from the natural candidate");
                var row=new JsonObject();row.addProperty("chunk_x",chunk.getPos().x);row.addProperty("chunk_z",chunk.getPos().z);row.addProperty("riftstone",rock);row.addProperty("heavy_fluid",heavy);chunks.add(row);checked++;
            }
            scene.add("chunks",chunks);report.add(scene);
        }
        System.out.println("NATIVE_V6_LIVE_FULL seed="+level.getSeed()+" scope=one_actual_server_seed chunks="+checked+" wall_ms="+(System.nanoTime()-began)/1000000+" "+report);h.succeed();
    }
}
