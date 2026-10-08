package pro.erez.interstice.test;

import java.util.HashSet;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.ForestDistribution;
import pro.erez.interstice.worldgen.GardenMaterials;
import pro.erez.interstice.worldgen.GardenTreeDefinitions;
import pro.erez.interstice.worldgen.GardenTrees;

@GameTestHolder("interstice_gardens")
@PrefixGameTestTemplate(false)
public final class ForestDistributionGameTests {
    private static final long SEED=20261006L;
    private static final ResourceLocation FOREST=ResourceLocation.fromNamespaceAndPath("interstice","paleheart_forest");
    private ForestDistributionGameTests() {}

    @GameTest(template="empty",timeoutTicks=200)
    public static void mixedFirstOutputsRemoveChunkRowStripes(GameTestHelper h) {
        int legacySame=0,mixedSame=0,edges=0,crowns=0,ordinary=0;
        int[] anchors=new int[12];
        for(int x=-48;x<48;x++)for(int z=-48;z<48;z++) {
            long packed=((long)z<<32)|(x&0xffffffffL),neighbor=((long)z<<32)|((x+1)&0xffffffffL);
            if(new Random(SEED^packed^0xCA015L).nextInt(256)==new Random(SEED^neighbor^0xCA015L).nextInt(256))legacySame++;
            if(new Random(ForestDistribution.mixedSeed(SEED,x,z,ForestDistribution.Kind.CROWN,0)).nextInt(256)
                    ==new Random(ForestDistribution.mixedSeed(SEED,x+1,z,ForestDistribution.Kind.CROWN,0)).nextInt(256))mixedSame++;
            edges++;
            crowns+=ForestDistribution.candidates(SEED,x,z,ForestDistribution.Kind.CROWN,24,1).size();
            var candidates=ForestDistribution.candidates(SEED,x,z,ForestDistribution.Kind.PALEHEART,1,1);
            ordinary+=candidates.size();for(var c:candidates)anchors[c.localX(2,13)-2]++;
        }
        double oldCorrelation=legacySame/(double)edges,newCorrelation=mixedSame/(double)edges;
        h.assertTrue(oldCorrelation>.8&&newCorrelation<.02,"First LCG outputs still correlate between adjacent chunk rows: "+oldCorrelation+" / "+newCorrelation);
        h.assertTrue(crowns/(double)edges>.015&&crowns/(double)edges<.05,"Giant rarity left the intended rare-tree range");
        h.assertTrue(ordinary/(double)edges<1,"Canopy density was implemented by packing more trunks into each chunk");
        for(int count:anchors)h.assertTrue(count>ordinary*.05&&count<ordinary*.12,"Raw anchor jitter has a favored chunk residue");
        System.out.println("FOREST_ROWS legacy_same="+oldCorrelation+" mixed_same="+newCorrelation+" crown_fraction="+crowns/(double)edges+" medium_fraction="+ordinary/(double)edges);
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void grovesAreSpatiallyCoherentAndIndependentOfEvaluationOrder(GameTestHelper h) {
        double nearby=0,distant=0;int comparisons=0;
        for(int x=-1024;x<=1024;x+=32)for(int z=-1024;z<=1024;z+=32) {
            double a=ForestDistribution.grove(SEED,x,z);
            nearby+=Math.abs(a-ForestDistribution.grove(SEED,x+16,z));
            distant+=Math.abs(a-ForestDistribution.grove(SEED,x+512,z+512));comparisons++;
            var first=ForestDistribution.candidates(SEED,x>>4,z>>4,ForestDistribution.Kind.PALEHEART,1,1);
            ForestDistribution.candidates(SEED+1,-x>>4,-z>>4,ForestDistribution.Kind.CROWN,24,1);
            h.assertTrue(first.equals(ForestDistribution.candidates(SEED,x>>4,z>>4,ForestDistribution.Kind.PALEHEART,1,1)),"Another seed/order changed a grove's anchors");
        }
        h.assertTrue(nearby<distant*.3,"Grove/clearing fields behave like independent per-chunk coin flips");
        System.out.println("FOREST_GROVES near_delta="+nearby/comparisons+" far_delta="+distant/comparisons);
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void eightForestFormsHaveDistinctLivingCanopiesAndAsymmetricFootprints(GameTestHelper h) {
        var definition=GardenTreeDefinitions.get(FOREST);var root=new BlockPos(0,36,0);
        h.assertTrue(definition.variants().size()==8&&definition.attempts()==1,"Forest variety relies on extra stem attempts");
        var silhouettes=new HashSet<java.util.Set<BlockPos>>();var dimensions=new HashSet<String>();int wide=0,low=0;
        for(int index=0;index<definition.variants().size();index++) {
            var variant=definition.variants().get(index);
            var first=GardenTrees.plan(h.getLevel(),p->p.getY()<root.getY()?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),root,variant,RandomSource.create(SEED+index));
            var again=GardenTrees.plan(h.getLevel(),p->p.getY()<root.getY()?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),root,variant,RandomSource.create(SEED+index));
            h.assertTrue(!first.isEmpty()&&first.equals(again),"New forest form is empty or unstable: "+index);
            int minX=first.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow(),maxX=first.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow();
            int minZ=first.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow(),maxZ=first.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow();
            int height=first.keySet().stream().mapToInt(p->p.getY()-root.getY()+1).max().orElseThrow();
            h.assertTrue(maxX-minX<=15&&maxZ-minZ<=15,"New form cannot fit its own chunk");
            if(Math.max(maxX-minX,maxZ-minZ)>=10)wide++;if(height<=9)low++;
            for(var s:first.values())if(s.is(GardenMaterials.PALEHEART_LEAVES.get()))h.assertTrue(s.getValue(LeavesBlock.DISTANCE)<7&&!s.getValue(LeavesBlock.PERSISTENT),"New canopy relies on forced persistent leaves");
            h.assertTrue(first.values().stream().noneMatch(s->s.is(GardenMaterials.CROWN_FRUIT.get())),"Ordinary forest forms became an easy fruit source");
            silhouettes.add(first.keySet());dimensions.add((maxX-minX)+":"+(maxZ-minZ)+":"+height);
        }
        h.assertTrue(silhouettes.size()==8&&dimensions.size()>=5&&wide>=4&&low>=1,"Forest forms do not provide distinct widths and vertical strata");
        h.succeed();
    }
}
