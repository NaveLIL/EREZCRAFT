package pro.erez.interstice.test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.ForestV5;
import pro.erez.interstice.worldgen.GardenMaterials;

@GameTestHolder("interstice_living")
@PrefixGameTestTemplate(false)
public final class ForestV5GameTests {
    private static final long SEED=20261006L;
    private static final GeometryProfile PROFILE=GeometryProfile.TALL;
    private static final ForestV5.GroundProbe FLAT=new ForestV5.GroundProbe() {
        public int surface(int x,int z){return 60;}
        public boolean solid(int x,int y,int z){return y<=60;}
    };
    private ForestV5GameTests() {}

    @GameTest(template="empty",timeoutTicks=200)
    public static void rootsOccupyChunkEdgesWithoutCentralAnchorRows(GameTestHelper h) {
        int[] residues=new int[16];int total=0,edges=0;var unique=new HashSet<Long>();
        for(int x=-24;x<24;x++)for(int z=-24;z<24;z++)for(var c:ForestV5.candidates(SEED,x,z)) {
            if((c.x()>>4)!=x||(c.z()>>4)!=z||!unique.add(c.key()))continue;
            residues[Math.floorMod(c.x(),16)]++;total++;
            int rx=Math.floorMod(c.x(),16),rz=Math.floorMod(c.z(),16);
            if(rx<=1||rx>=14||rz<=1||rz>=14)edges++;
        }
        h.assertTrue(total>2500,"The global forest discarded most roots");
        for(int count:residues)h.assertTrue(count>total*.04&&count<total*.09,"Root distribution still favors a Minecraft chunk column");
        h.assertTrue(edges>total*.35,"Large canopy footprints still push roots away from chunk edges");
        var first=ForestV5.candidates(SEED,-3,7);
        ForestV5.candidates(SEED+1,8,-6);
        h.assertTrue(first.equals(ForestV5.candidates(SEED,-3,7)),"Another seed/order changed the candidate list");
        System.out.println("FOREST_V5_ROOTS count="+total+" edges="+edges+" residues="+java.util.Arrays.toString(residues));
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=600)
    public static void entireNativeCanopiesCrossPositiveAndNegativeChunkSeams(GameTestHelper h) {
        var pieces=new HashMap<String,ForestV5.Plan>();var cells=new HashMap<BlockPos,BlockState>();
        var roots=new HashMap<Long,ForestV5.Root>();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            var plan=ForestV5.plan(PROFILE,x,z,SEED,h.getLevel(),FLAT);pieces.put(x+":"+z,plan);
            for(var e:plan.cells().entrySet()) {
                h.assertTrue((e.getKey().getX()>>4)==x&&(e.getKey().getZ()>>4)==z,"Forest wrote a neighbor chunk");
                h.assertTrue(e.getKey().getY()>60,"Forest replaced its geological ground");
                cells.put(e.getKey(),e.getValue());
            }
            plan.roots().forEach(r->roots.put(r.key(),r));
        }
        int crossing=0,negativeCrossing=0,positiveCrossing=0;
        for(var root:roots.values()) {
            Set<Integer> xs=new HashSet<>(),zs=new HashSet<>();
            for(var e:pieces.entrySet())if(e.getValue().roots().stream().anyMatch(r->r.key()==root.key())) {
                var coordinates=e.getKey().split(":");xs.add(Integer.parseInt(coordinates[0]));zs.add(Integer.parseInt(coordinates[1]));
            }
            if(xs.size()>1||zs.size()>1)crossing++;
            if(xs.contains(-1)&&xs.contains(0)||zs.contains(-1)&&zs.contains(0))negativeCrossing++;
            if(xs.contains(0)&&xs.contains(1)||zs.contains(0)&&zs.contains(1))positiveCrossing++;
        }
        h.assertTrue(crossing>=12&&negativeCrossing>0&&positiveCrossing>0,"Whole trees still stop at chunk boundaries");
        // Re-evaluate every seam in reverse order, including colliding branches/leaves.
        var reversed=new HashMap<BlockPos,BlockState>();
        for(int x=2;x>=-2;x--)for(int z=2;z>=-2;z--) {
            var again=ForestV5.plan(PROFILE,x,z,SEED,h.getLevel(),FLAT);
            h.assertTrue(again.equals(pieces.get(x+":"+z)),"Planning depends on a previously generated neighboring chunk");
            reversed.putAll(again.cells());
        }
        h.assertTrue(cells.equals(reversed),"Reverse chunk generation changed forest cells");
        System.out.println("FOREST_V5_SEAMS roots="+roots.size()+" crossing="+crossing+" cells="+cells.size());h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=600)
    public static void mixedForestRetainsThreeVerticalStrataAndHighFood(GameTestHelper h) {
        var roots=new HashMap<Long,ForestV5.Root>();var columns=new HashSet<Long>();int fruits=0;
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++) {
            var plan=ForestV5.plan(PROFILE,x,z,SEED,h.getLevel(),FLAT);
            for(var r:plan.roots())if((r.pos().getX()>>4)==x&&(r.pos().getZ()>>4)==z)roots.put(r.key(),r);
            for(var e:plan.cells().entrySet()) {
                var p=e.getKey();var s=e.getValue();
                if(s.getBlock() instanceof LeavesBlock) {
                    columns.add(((long)p.getX()<<32)^(p.getZ()&0xffffffffL));
                    h.assertTrue(s.getValue(LeavesBlock.DISTANCE)<7&&!s.getValue(LeavesBlock.PERSISTENT),"V5 foliage needs fake permanent leaves");
                }
                if(s.is(GardenMaterials.CROWN_FRUIT.get())) {
                    fruits++;h.assertTrue(p.getY()>=79,"Forest made climbing food available at shrub height");
                }
            }
        }
        long low=roots.values().stream().filter(r->r.kind()==ForestV5.Kind.GLOOMCROWN&&r.height()<=9).count();
        long medium=roots.values().stream().filter(r->r.kind()==ForestV5.Kind.PALEHEART&&r.height()>=9&&r.height()<20).count();
        long high=roots.values().stream().filter(r->r.kind()==ForestV5.Kind.CROWN&&r.height()>=20).count();
        double coverage=columns.size()/(49.0*256);
        System.out.println("FOREST_V5_STRATA roots="+roots.size()+" low="+low+" mid="+medium+" high="+high+" canopy_coverage="+coverage+" fruits="+fruits);
        h.assertTrue(low>=8&&medium>=12&&high>=2&&fruits>=4,"Forest lost a native tree stratum or its climbing fruit");
        h.assertTrue(coverage>.55&&coverage<.95,"Forest canopy has no usable dense cover/clearings: "+coverage);
        h.assertTrue(roots.size()<49*4,"Forest density comes from packing too many stems per chunk");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=600)
    public static void crimsonForestIsDarkLayeredDenseAndNotAnEmptyFourthBiome(GameTestHelper h){
        var crimson=new ForestV5.GroundProbe(){
            public int surface(int x,int z){return 60;}
            public boolean solid(int x,int y,int z){return y<=60;}
            public boolean garden(int x,int z){return false;}
            public boolean crimson(int x,int z){return true;}
        };
        var roots=new HashMap<Long,ForestV5.Root>();var columns=new HashSet<Long>();int darkLeaves=0,paleLeaves=0;
        var heights=new HashSet<Integer>();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
            var plan=ForestV5.plan(PROFILE,x,z,SEED,h.getLevel(),crimson);
            for(var r:plan.roots())if((r.pos().getX()>>4)==x&&(r.pos().getZ()>>4)==z){roots.put(r.key(),r);heights.add(r.height());}
            for(var e:plan.cells().entrySet())if(e.getValue().getBlock() instanceof LeavesBlock){
                var p=e.getKey();columns.add(((long)p.getX()<<32)^(p.getZ()&0xffffffffL));
                if(e.getValue().is(Interstice.GLOOMCROWN_LEAVES.get()))darkLeaves++;else paleLeaves++;
                h.assertTrue(e.getValue().getValue(LeavesBlock.DISTANCE)<7&&!e.getValue().getValue(LeavesBlock.PERSISTENT),"Crimson forest has disconnected or permanent leaves");
            }
        }
        double coverage=columns.size()/(49.0*256);
        long low=roots.values().stream().filter(r->r.height()<=9).count();
        long medium=roots.values().stream().filter(r->r.kind()==ForestV5.Kind.GLOOMCROWN&&r.height()>=10&&r.height()<20).count();
        System.out.println("FOREST_V5_CRIMSON roots="+roots.size()+" canopy_coverage="+coverage+" dark_leaves="+darkLeaves+" other_leaves="+paleLeaves+" low="+low+" dark_middle="+medium+" heights="+heights);
        h.assertTrue(coverage>.55&&coverage<.95&&roots.size()<49*4,"Crimson woodland is sparse or just a packed grid: "+coverage);
        h.assertTrue(darkLeaves>paleLeaves&&low>=8&&medium>=20&&heights.size()>=6,"Crimson woodland lost its distinct dark trees and vertical variation");
        var garden=ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),FLAT);
        h.assertTrue(!garden.cells().equals(ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),crimson).cells()),"Both forest biomes have identical tree composition");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=600)
    public static void immutableBiomeAndSeaMasksRejectWholeTrees(GameTestHelper h) {
        var water=new ForestV5.GroundProbe() {
            public int surface(int x,int z){return PROFILE.lowerSeaTop();}
            public boolean solid(int x,int y,int z){return y<=PROFILE.lowerSeaTop();}
        };
        var otherBiome=new ForestV5.GroundProbe() {
            public int surface(int x,int z){return 60;}
            public boolean solid(int x,int y,int z){return y<=60;}
            public boolean garden(int x,int z){return false;}
        };
        h.assertTrue(ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),water).cells().isEmpty(),"Trees root in the toxic sea");
        h.assertTrue(ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),otherBiome).cells().isEmpty(),"Garden forest leaked into a different biome");
        var first=ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),FLAT);
        h.assertTrue(!first.roots().isEmpty(),"The collision fixture has no tree");
        var selected=first.roots().get(0);
        var obstacle=new ForestV5.GroundProbe() {
            public int surface(int x,int z){return 60;}
            public boolean solid(int x,int y,int z){return y<=60||x==selected.pos().getX()&&z==selected.pos().getZ()&&y==selected.pos().getY()+1;}
        };
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            var blocked=ForestV5.plan(PROFILE,x,z,SEED,h.getLevel(),obstacle);
            h.assertTrue(blocked.roots().stream().noneMatch(r->r.key()==selected.key()),"A collision rejected only one chunk fragment of a tree");
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=600)
    public static void hillsideFoliageClipsWithoutRejectingSupportedWoodOrKeepingOrphanLeaves(GameTestHelper h){
        var all=ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),FLAT);
        var root=all.roots().stream().filter(r->r.pos().getX()>=0&&r.pos().getX()<16&&r.pos().getZ()>=0&&r.pos().getZ()<16).findFirst().orElseThrow();
        ForestV5.GroundProbe isolated=new ForestV5.GroundProbe(){
            public int surface(int x,int z){return 60;}
            public boolean solid(int x,int y,int z){return y<=60;}
            public boolean garden(int x,int z){return x==root.pos().getX()&&z==root.pos().getZ();}
        };
        var original=ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),isolated);
        var collision=original.cells().entrySet().stream().filter(e->e.getValue().getBlock() instanceof LeavesBlock)
                .map(Map.Entry::getKey).filter(p->p.distManhattan(root.pos())>=4).findFirst().orElseThrow();
        ForestV5.GroundProbe hill=new ForestV5.GroundProbe(){
            public int surface(int x,int z){return 60;}
            public boolean solid(int x,int y,int z){return y<=60||collision.equals(new BlockPos(x,y,z));}
            public boolean garden(int x,int z){return isolated.garden(x,z);}
        };
        var clipped=ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),hill);
        h.assertTrue(clipped.roots().stream().anyMatch(r->r.key()==root.key())&&!clipped.cells().containsKey(collision),"One hillside leaf still rejects the entire supported tree or overwrites geology");
        for(var entry:original.cells().entrySet())if(!(entry.getValue().getBlock() instanceof LeavesBlock)
                &&!entry.getValue().is(GardenMaterials.PALE_VINE.get())&&!entry.getValue().is(GardenMaterials.CROWN_FRUIT.get()))
            h.assertTrue(entry.getValue().equals(clipped.cells().get(entry.getKey())),"Clipping foliage changed a supporting wood cell");
        for(var state:clipped.cells().values())if(state.getBlock() instanceof LeavesBlock)
            h.assertTrue(state.getValue(LeavesBlock.DISTANCE)<7&&!state.getValue(LeavesBlock.PERSISTENT),"Pruned crown kept disconnected permanent leaves");
        ForestV5.GroundProbe woodCollision=new ForestV5.GroundProbe(){
            public int surface(int x,int z){return 60;}
            public boolean solid(int x,int y,int z){return y<=60||root.pos().above().equals(new BlockPos(x,y,z));}
            public boolean garden(int x,int z){return isolated.garden(x,z);}
        };
        h.assertTrue(ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),woodCollision).roots().isEmpty(),"The relaxed foliage fit now permits wood inside natural terrain");h.succeed();
    }
}
