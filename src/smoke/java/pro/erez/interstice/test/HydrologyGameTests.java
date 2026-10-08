package pro.erez.interstice.test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.terrain.HydrologyV4;
import pro.erez.interstice.worldgen.terrain.TerrainV4;

/** Canonical water/terrain contracts; naturally visible streams and fluid flow are native smoke acceptance. */
@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class HydrologyGameTests {
    private static final long SEED=20261006L;
    private static final GeometryProfile PROFILE=GeometryProfile.TALL;
    private HydrologyGameTests() {}
    private static HydrologyV4.Context plains(){return new HydrologyV4.Context(SEED,PROFILE,(x,z)->TerrainV4.column(SEED,PROFILE,x,z,-.5,1));}

    @GameTest(template="empty",timeoutTicks=200)
    public static void queryOrderNegativeChunkBordersAndCacheEvictionKeepIdenticalDensity(GameTestHelper h) {
        var first=plains();int[][] points={{-17,-1},{-16,0},{-1,15},{0,16},{15,31},{16,32}};
        var original=new java.util.ArrayList<HydrologyV4.Column>();for(var p:points)original.add(first.column(p[0],p[1]));
        // More than the512-entry watershed cache; cached null plans must be safe to recreate too.
        for(int i=0;i<650;i++)first.column(i*256-8192,Math.floorMod(i,17)*256);
        var reversed=plains();for(int i=points.length-1;i>=0;i--)reversed.column(points[i][0],points[i][1]);
        for(int i=0;i<points.length;i++) {
            var a=original.get(i);var b=first.column(points[i][0],points[i][1]);var c=reversed.column(points[i][0],points[i][1]);
            h.assertTrue(a.water().equals(b.water())&&a.water().equals(c.water()),"Water plans depend on query/cache/chunk order");
            for(int y=1;y<190;y++)h.assertTrue(Double.doubleToLongBits(a.density(y))==Double.doubleToLongBits(b.density(y))
                    &&Double.doubleToLongBits(a.density(y))==Double.doubleToLongBits(c.density(y)),"Water/base-density disagreement across a chunk edge");
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void waterOccupiesCarvedAirAndLeavesUncutPlainsWithinAcceptedRange(GameTestHelper h) {
        var context=plains();int wet=0,dry=0;
        for(int x=-512;x<=512;x+=8)for(int z=-512;z<=512;z+=8) {
            var column=context.column(x,z);var water=column.water();
            h.assertTrue(column.groundHeight()<=column.raw().groundHeight(),"A river creates an elevated wall instead of cutting a bed");
            if(water.wet()) {
                wet++;h.assertTrue(water.kind()==HydrologyV4.Kind.PLAIN_RIVER&&water.water()==PROFILE.lowerSeaTop(),"A plain river moved the established lower sea");
                for(int y=PROFILE.minY();y<PROFILE.maxYExclusive();y++)if(water.contains(y)) {
                    h.assertTrue(y>PROFILE.minY()&&y<190&&column.density(y)<=0,"Fluid placement would overwrite stone or a world boundary");
                }
                h.assertTrue(!water.contains(water.water()+1),"Channel water leaks above its actual top cell");
            } else if(water.kind()==HydrologyV4.Kind.NONE) {
                dry++;h.assertTrue(column.groundHeight()>=35&&column.groundHeight()<40,"Hydrology changed nonchannel plains");
            }
        }
        h.assertTrue(wet>200&&dry>wet*4,"Drainage either disappeared or replaced the entire plain with water");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void sourcePlanningIsBoundedAndWarmSamplingDoesNotRepeatWatershedSearch(GameTestHelper h) {
        var calls=new AtomicInteger();
        var context=new HydrologyV4.Context(SEED,PROFILE,(x,z)->{calls.incrementAndGet();return TerrainV4.column(SEED,PROFILE,x,z,1,1);});
        var original=context.column(173,-291);int initial=calls.get();
        h.assertTrue(initial>1&&initial<10000,"A single column launches an unbounded raw-terrain drainage search");
        for(int i=0;i<20;i++) {
            int before=calls.get();var again=context.column(173,-291);
            h.assertTrue(calls.get()-before==1,"Cached watershed plans still rerun all source and downhill probes");
            h.assertTrue(original.water().equals(again.water()),"A warm hydrology cache changes water samples");
        }
        h.succeed();
    }
}
