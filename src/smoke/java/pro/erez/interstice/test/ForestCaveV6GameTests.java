package pro.erez.interstice.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.terrain.NativeColumnSamplerV6;
import pro.erez.interstice.worldgen.terrain.TensionTerrainV6;

@GameTestHolder("interstice_forest_caves")
@PrefixGameTestTemplate(false)
public final class ForestCaveV6GameTests {
    static final long[] SEEDS={0,1,-1,20261006,76198123,4294967297L};
    static RandomState state(GameTestHelper h,long seed){
        var level=h.getLevel().getServer().getLevel(IslandWorld.TENSION_WORLD);
        h.assertTrue(level!=null&&level.dimension().location().toString().equals("interstice:islands_v6"),"Actual dimension is not V6");
        var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();
        h.assertTrue(generator.terrainRevision()==6,"Actual generator is not V6");
        return RandomState.create(generator.generatorSettings().value(),h.getLevel().registryAccess().registryOrThrow(Registries.NOISE).asLookup(),seed);
    }
    static pro.erez.interstice.worldgen.terrain.V6ForestCavePlanner.Network firstNetwork(NativeColumnSamplerV6 sampler){
        for(int cx=-4;cx<=3;cx++)for(int cz=-4;cz<=3;cz++){
            var plans=sampler.forestCaves().plansInCell(cx,cz);if(!plans.isEmpty())return plans.getFirst();
        }
        throw new IllegalStateException("No forest network in fixed radius512");
    }
    @GameTest(template="empty",batch="forest_caves_baseline",timeoutTicks=2400)
    public static void fixedLowerForestGridReportsExistingCaves(GameTestHelper h){
        var report=new JsonArray();
        for(long seed:SEEDS){
            var random=state(h,seed);var sampler=NativeColumnSamplerV6.of(random);var root=TensionTerrainV6.root(random);
            int forests=0,withCaves=0,cells=0,standing=0;var heights=new JsonObject();
            // Fixed grid, never expanded on missing results: radius512, spacing64.
            for(int x=-512;x<=512;x+=64)for(int z=-512;z<=512;z+=64){
                var w=root.morphology(x,z);if(w.gardens()+w.crimson()<.75)continue;
                int top=-1;for(int y=80;y>root.dryCaveCorner();y--)if(sampler.density(x,y,z,true)>0){top=y;break;}
                if(top<root.dryCaveCorner()+4||top>72)continue;
                forests++;heights.addProperty(top+"",heights.has(top+"")?heights.get(top+"").getAsInt()+1:1);boolean cave=false;
                for(int y=root.dryCaveCorner()+1;y<=top-3;y++)if(sampler.nativeDensity(x,y,z,true)>0&&sampler.nativeDensity(x,y,z,false)<=0){
                    cells++;cave=true;if(sampler.nativeDensity(x,y+1,z,false)<=0&&sampler.nativeDensity(x,y-1,z,false)>0)standing++;
                }
                if(cave)withCaves++;
            }
            var row=new JsonObject();row.addProperty("seed",seed);row.addProperty("dimension","interstice:islands_v6");row.addProperty("forest_columns",forests);
            row.addProperty("columns_with_internal_caves",withCaves);row.addProperty("removed_internal_cells",cells);row.addProperty("standing_nodes",standing);row.add("surface_heights",heights);report.add(row);
        }
        System.out.println("V6_LOWER_FOREST_GRID "+report);h.succeed();
    }

    @GameTest(template="empty",batch="forest_caves_plans",timeoutTicks=2400)
    public static void sixFixedSeedsHaveConnectedForestNetworks(GameTestHelper h){
        var report=new JsonArray();
        for(long seed:SEEDS){
            var sampler=NativeColumnSamplerV6.of(state(h,seed));int found=0,removed=0,openings=0,branches=0;var locations=new JsonArray();
            for(int cx=-4;cx<=3;cx++)for(int cz=-4;cz<=3;cz++)for(var network:sampler.forestCaves().plansInCell(cx,cz)){
                found++;removed+=network.removed().size();openings+=network.surfaceOpenings().size();branches+=network.branches().size();locations.add(network.hub().toShortString());
                var paths=new java.util.ArrayList<java.util.List<BlockPos>>(network.branches());paths.add(network.entrance());
                for(var path:paths){BlockPos previous=null;for(var p:path){
                    h.assertTrue(sampler.density(p.getX(),p.getY()-1,p.getZ())>0&&sampler.density(p.getX(),p.getY(),p.getZ())<=0&&sampler.density(p.getX(),p.getY()+1,p.getZ())<=0,"Network floor/headroom missing at "+p);
                    if(previous!=null&&previous.getY()!=p.getY()){var extra=(p.getY()>previous.getY()?previous:p).above(2);
                        h.assertTrue(sampler.density(extra.getX(),extra.getY(),extra.getZ())<=0,"Network stair has no headroom at "+p);}
                    previous=p;
                }}
                for(long packed:network.removed()){var p=BlockPos.of(packed);
                    h.assertTrue(p.getY()>40&&sampler.nativeDensity(p.getX(),p.getY(),p.getZ(),false)>0,"Carver changed sea band or added rock");
                }
            }
            var row=new JsonObject();row.addProperty("seed",seed);row.addProperty("networks",found);row.addProperty("branches",branches);row.addProperty("removed_cells",removed);row.addProperty("surface_openings",openings);row.add("hubs",locations);report.add(row);
            System.out.println("V6_FOREST_CAVE_PLANS_SEED "+row);
            h.assertTrue(found>0,"No connected forest network in fixed radius512 seed="+seed);
        }
        System.out.println("V6_FOREST_CAVE_PLANS "+report);h.succeed();
    }

    @GameTest(template="empty",batch="forest_caves_native",timeoutTicks=2400)
    public static void actualNoiseMatchesSamplerPreservesSurfaceAndChunkOrder(GameTestHelper h){
        for(long seed:SEEDS){
            var f=NativeTerrainV6GameTests.fixture(h,seed);var sampler=NativeColumnSamplerV6.of(f.random());var network=firstNetwork(sampler);
            var positions=new java.util.TreeSet<net.minecraft.world.level.ChunkPos>(java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));
            for(long packed:network.removed())positions.add(new net.minecraft.world.level.ChunkPos(BlockPos.of(packed)));
            h.assertTrue(positions.size()<=16,"Forest network exceeded declared16 NOISE chunk budget");
            var order=java.util.List.copyOf(positions);
            var actual=NativeTerrainV6GameTests.await(h,NativeTerrainV6GameTests.serial(h,f,order),180);
            var reverse=new java.util.ArrayList<>(order);java.util.Collections.reverse(reverse);
            var b=NativeTerrainV6GameTests.index(NativeTerrainV6GameTests.await(h,NativeTerrainV6GameTests.serial(h,NativeTerrainV6GameTests.fixture(h,seed),reverse),180));
            var shuffled=new java.util.ArrayList<>(order);java.util.Collections.shuffle(shuffled,new java.util.Random(5636));
            var g=NativeTerrainV6GameTests.fixture(h,seed);var pending=shuffled.stream().map(p->NativeTerrainV6GameTests.noise(h,g,p)).toList();
            var c=NativeTerrainV6GameTests.index(NativeTerrainV6GameTests.await(h,java.util.concurrent.CompletableFuture.allOf(pending.toArray(java.util.concurrent.CompletableFuture[]::new)).thenApply(ignored->pending.stream().map(java.util.concurrent.CompletableFuture::join).toList()),180));
            int changes=0;long digest=0;
            for(var chunk:actual){
                long hash=NativeTerrainV6GameTests.hash(chunk);digest^=hash;
                h.assertTrue(hash==NativeTerrainV6GameTests.hash(b.get(chunk.getPos().toLong()))&&hash==NativeTerrainV6GameTests.hash(c.get(chunk.getPos().toLong())),"Forest cave changed with independent reverse/shuffled parallel generation seed="+seed);
                for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++){
                    int oldTop=-1,newTop=-1;
                    for(int y=0;y<256;y++){
                        boolean before=sampler.nativeDensity(x,y,z,false)>0,after=!chunk.getBlockState(new BlockPos(x,y,z)).isAir();
                        h.assertTrue(after==(sampler.density(x,y,z)>0),"Forest cave native/sampler mismatch at "+x+","+y+","+z);
                        h.assertTrue(!after||before,"Forest cave added rock");
                        if(y<=40)h.assertTrue(before==after,"Forest cave altered protected sea band");
                        if(before)oldTop=y;if(after)newTop=y;if(before!=after)changes++;
                    }
                    if(oldTop!=newTop)h.assertTrue(surfaceOpening(sampler,x,oldTop,z),"Surface changed outside an explicit entrance lip");
                }
            }
            h.assertTrue(changes>100,"Actual NOISE contained no substantial network");
            System.out.println("V6_FOREST_CAVE_NATIVE seed="+seed+" chunks_per_order="+order.size()+" removed="+changes+" hash="+Long.toUnsignedString(digest));
        }
        h.succeed();
    }
    private static boolean surfaceOpening(NativeColumnSamplerV6 sampler,int x,int y,int z){
        for(var network:sampler.forestCaves().plansInCell(Math.floorDiv(x,128),Math.floorDiv(z,128)))if(network.surfaceOpenings().contains(BlockPos.asLong(x,y,z)))return true;return false;
    }

    @GameTest(template="empty",batch="forest_caves_full",timeoutTicks=2400)
    public static void naturalFullForestHasWalkableEntranceAndBranches(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(IslandWorld.TENSION_WORLD);var sampler=NativeColumnSamplerV6.of(level.getChunkSource().randomState());
        var network=firstNetwork(sampler);int nodes=0;
        var paths=new java.util.ArrayList<java.util.List<BlockPos>>(network.branches());paths.add(network.entrance());
        for(var path:paths)for(var p:path){
            var chunk=level.getChunk(p);h.assertTrue(chunk.getPersistedStatus().isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.FULL),"Not a FULL chunk");
            h.assertTrue(!level.getBlockState(p.below()).getCollisionShape(level,p.below()).isEmpty(),"FULL lost natural floor at "+p);
            h.assertTrue(level.getBlockState(p).getCollisionShape(level,p).isEmpty()&&level.getBlockState(p.above()).getCollisionShape(level,p.above()).isEmpty(),"FULL decoration blocked planned route at "+p+" feet="+level.getBlockState(p)+" head="+level.getBlockState(p.above()));
            h.assertTrue(level.getFluidState(p).isEmpty()&&level.getFluidState(p.above()).isEmpty(),"FULL flooded dry forest route at "+p);nodes++;
        }
        var inspection=ForestCaveInspection.inspect(level,network);
        System.out.println("V6_FOREST_CAVE_FULL "+inspection);h.succeed();
    }
}
