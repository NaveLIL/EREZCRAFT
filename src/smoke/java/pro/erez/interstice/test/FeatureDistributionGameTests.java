package pro.erez.interstice.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ecology.CaveEcology;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.FeatureDistribution;
import pro.erez.interstice.worldgen.RealmBiomes;
import pro.erez.interstice.worldgen.StoneVaults;
import pro.erez.interstice.worldgen.WatchpostRuins;
import pro.erez.interstice.worldgen.cave.CaveDensity;
import pro.erez.interstice.worldgen.cave.CaveFeatures;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class FeatureDistributionGameTests {
    private static final long[] SEEDS={0,20261006L,20261006L+(1L<<40)};
    private FeatureDistributionGameTests(){}
    @GameTest(template="empty",timeoutTicks=200)
    public static void sparseStructureSlotsHaveSharedSpacingAndRetainLegacyCandidates(GameTestHelper h){
        for(long seed:SEEDS){
            var accepted=new ArrayList<FeatureDistribution.Slot>();int garden=0,all=0;
            for(int cx=-8;cx<8;cx++)for(int cz=-8;cz<8;cz++){
                var slot=FeatureDistribution.slot(seed,cx,cz);if(slot==null)continue;
                h.assertTrue((slot.structure()==FeatureDistribution.Structure.WATCHPOST?WatchpostRuins.candidate(seed,slot.chunk().x,slot.chunk().z):StoneVaults.candidate(seed,slot.chunk().x,slot.chunk().z)),"Rarity gate selected a chunk the old generator will reject");
                var repeat=FeatureDistribution.slot(seed,cx,cz);
                FeatureDistribution.slot(seed+(1L<<32),-cx-1,-cz-1);
                h.assertTrue(slot.equals(repeat)&&slot.equals(FeatureDistribution.slot(seed,cx,cz)),"Another world or evaluation order changed a structure slot");
                int count=0;
                for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
                    boolean watcher=FeatureDistribution.watchpostAllowed(4,seed,x,z,false),arch=FeatureDistribution.stoneVaultAllowed(4,seed,x,z,false);
                    h.assertTrue(!(watcher&&arch),"Two structure families occupied one shared slot");
                    if(watcher||arch){count++;h.assertTrue(slot.chunk().equals(new ChunkPos(x,z)),"One cell admitted a second structure anchor");}
                }
                h.assertTrue(count<=1,"A global cell contains multiple small shelters");
                boolean plains=FeatureDistribution.structureAllowed(4,seed,slot.chunk().x,slot.chunk().z,slot.structure(),true);
                boolean ordinary=FeatureDistribution.structureAllowed(4,seed,slot.chunk().x,slot.chunk().z,slot.structure(),false);
                if(plains){garden++;h.assertTrue(ordinary,"Garden thinning introduced a new anchor instead of retaining a subset");}
                if(ordinary){accepted.add(slot);all++;}
            }
            for(int a=0;a<accepted.size();a++)for(int b=a+1;b<accepted.size();b++){
                var first=accepted.get(a).chunk();var second=accepted.get(b).chunk();
                h.assertTrue(Math.max(Math.abs(first.x-second.x),Math.abs(first.z-second.z))>=FeatureDistribution.MIN_STRUCTURE_SEPARATION_CHUNKS,"Small structures of different types cluster at a shared cell boundary");
            }
            h.assertTrue(all>150&&garden>30&&garden<all*.5,"Flat-garden shelter density was not substantially reduced: "+garden+"/"+all);
            System.out.println("V4_STRUCTURE_SLOTS seed="+seed+" ordinary="+all+" flatGardens="+garden+" cells=256 minChunkGap="+FeatureDistribution.MIN_STRUCTURE_SEPARATION_CHUNKS);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void olderFeatureBudgetsRemainUnchangedAndV4SproutsAreSevenTimesRarer(GameTestHelper h){
        for(long seed:SEEDS){int retained=0,total=0;
            for(int x=-96;x<96;x++)for(int z=-96;z<96;z++){
                for(int revision=1;revision<=3;revision++)h.assertTrue(FeatureDistribution.tideSproutsAllowed(revision,seed,x,z)
                        &&FeatureDistribution.watchpostAllowed(revision,seed,x,z,true)&&FeatureDistribution.stoneVaultAllowed(revision,seed,x,z,true),"New budgets leaked into an existing generator revision");
                boolean chosen=FeatureDistribution.tideSproutsAllowed(4,seed,x,z);
                FeatureDistribution.tideSproutsAllowed(4,seed+1,-x,-z);
                h.assertTrue(chosen==FeatureDistribution.tideSproutsAllowed(4,seed,x,z),"Sprout gate depends on prior queries");
                if(chosen)retained++;total++;
            }
            double fraction=retained/(double)total;
            h.assertTrue(fraction>1.0/8&&fraction<1.0/6,"Tide sprouts are not6–8 times rarer: "+fraction);
            System.out.println("V4_SPROUT_BUDGET seed="+seed+" retained="+retained+" total="+total+" fraction="+fraction);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void caveFloorAndHangingPlantsHaveIndependentFourfoldBudgets(GameTestHelper h){
        for(long seed:SEEDS){int floor=0,hanging=0,total=0;
            for(int x=-128;x<128;x+=8)for(int z=-128;z<128;z+=8)for(int y=12;y<140;y+=16){
                var pos=new BlockPos(x,y,z);boolean f=FeatureDistribution.cavePlantAllowed(4,seed,pos,false),p=FeatureDistribution.cavePlantAllowed(4,seed,pos,true);
                if(f)floor++;if(p)hanging++;total++;
                for(int revision=1;revision<=3;revision++)h.assertTrue(FeatureDistribution.cavePlantAllowed(revision,seed,pos,false)&&FeatureDistribution.cavePlantAllowed(revision,seed,pos,true),"Earlier cave populations changed");
            }
            h.assertTrue(floor/(double)total>.2&&floor/(double)total<1.0/3&&hanging/(double)total>.2&&hanging/(double)total<1.0/3,"Cave flowers did not become3–5 times rarer");
            System.out.println("V4_CAVE_BUDGET seed="+seed+" floor="+floor+" hanging="+hanging+" positions="+total);
        }h.succeed();
    }
    private static ProtoChunk room(GameTestHelper h,ChunkPos pos){
        var chunk=new ProtoChunk(pos,UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        chunk.fillBiomesFromNoise(new FixedBiomeSource(h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(RealmBiomes.PALE_GARDENS)),null);
        chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
        for(int x=pos.getMinBlockX();x<=pos.getMaxBlockX();x++)for(int z=pos.getMinBlockZ();z<=pos.getMaxBlockZ();z++)for(int floor:new int[]{20,70,130}){
            chunk.setBlockState(new BlockPos(x,floor-1,z),Interstice.RIFTSTONE.get().defaultBlockState(),false);
            chunk.setBlockState(new BlockPos(x,floor+9,z),Interstice.RIFTSTONE.get().defaultBlockState(),false);
            if(x==pos.getMinBlockX()||x==pos.getMaxBlockX()||z==pos.getMinBlockZ()||z==pos.getMaxBlockZ())for(int y=floor;y<floor+9;y++)chunk.setBlockState(new BlockPos(x,y,z),Interstice.RIFTSTONE.get().defaultBlockState(),false);
        }return chunk;
    }
    private static LevelReader reader(ProtoChunk chunk){
        return (LevelReader)Proxy.newProxyInstance(LevelReader.class.getClassLoader(),new Class<?>[]{LevelReader.class},(proxy,method,args)->{
            if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);
            return switch(method.getName()){
                case "getBlockState"->chunk.getBlockState((BlockPos)args[0]);
                case "getFluidState"->chunk.getBlockState((BlockPos)args[0]).getFluidState();
                case "getBlockEntity"->null;
                case "getMinBuildHeight"->0;
                case "getHeight"->256;
                case "getChunk"->chunk;
                case "hasChunk"->true;
                default->throw new UnsupportedOperationException("Unexpected cave fixture read: "+method);
            };
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void actualCaveThinningPreservesEveryClingweedCellAndEarlierPlacement(GameTestHelper h){
        int originalFlowers=0,newFlowers=0,colonies=0;
        for(long seed:SEEDS)for(int phase=0;phase<2;phase++){
            ChunkPos chosen=null;
            search:for(int x=-1024;x<=1024;x+=64)for(int z=-1024;z<=1024;z+=64){
                if(phase==0?CaveDensity.lush(seed,x,70,z):CaveDensity.colonized(seed,x,70,z)){chosen=new ChunkPos(x>>4,z>>4);break search;}
            }
            h.assertTrue(chosen!=null,"No bounded cave habitat fixture");
            var old=room(h,chosen);var modern=room(h,chosen);var legacy=room(h,chosen);
            var first=CaveFeatures.decorate(GeometryProfile.TALL,old,seed,reader(old),3);
            var sparse=CaveFeatures.decorate(GeometryProfile.TALL,modern,seed,reader(modern),4);
            var unchanged=CaveFeatures.decorate(GeometryProfile.TALL,legacy,seed,reader(legacy),2);
            h.assertTrue(first.equals(unchanged),"New overload changed an earlier cave population");
            for(int x=chosen.getMinBlockX();x<=chosen.getMaxBlockX();x++)for(int z=chosen.getMinBlockZ();z<=chosen.getMaxBlockZ();z++)for(int y=7;y<205;y++){
                var p=new BlockPos(x,y,z);var before=old.getBlockState(p);var after=modern.getBlockState(p);
                h.assertTrue(before.equals(legacy.getBlockState(p)),"Old-revision decoration changed a block at "+p);
                if(before.is(CaveEcology.CLINGWEED.get())||after.is(CaveEcology.CLINGWEED.get()))h.assertTrue(before.equals(after),"Flower thinning altered clingweed position/orientation at "+p);
            }
            h.assertTrue(first.clingweed()==sparse.clingweed()&&first.spireBlocks()==sparse.spireBlocks(),"V4 flower budget affected other cave ecology");
            originalFlowers+=first.floorPlants()+first.hangingPlants();newFlowers+=sparse.floorPlants()+sparse.hangingPlants();colonies+=first.clingweed();
        }
        double fraction=newFlowers/(double)originalFlowers;
        h.assertTrue(originalFlowers>100&&colonies>0&&fraction>.2&&fraction<1.0/3,"Actual cave fixtures do not show3–5-fold plant thinning: "+newFlowers+"/"+originalFlowers);
        System.out.println("V4_ACTUAL_CAVE_FLOWERS before="+originalFlowers+" after="+newFlowers+" clingweedUnchanged="+colonies);h.succeed();
    }
}
