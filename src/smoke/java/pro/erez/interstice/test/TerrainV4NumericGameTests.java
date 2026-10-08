package pro.erez.interstice.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.terrain.FloatingLandformsV4;
import pro.erez.interstice.worldgen.terrain.TerrainV4;

/** Morphology and copy/continuity contracts; native hydrology and visible scenes are checked separately. */
@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class TerrainV4NumericGameTests {
    private static final long SEED=20261006L;
    private static final GeometryProfile PROFILE=GeometryProfile.TALL;
    private TerrainV4NumericGameTests() {}

    @GameTest(template="empty",timeoutTicks=200)
    public static void nativeClimateBiomeRunsHaveWalkableScale(GameTestHelper h) {
        var key=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"living_realm_v4"));
        var settings=h.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(key);
        var fields=RandomState.create(settings.value(),h.getLevel().registryAccess().registryOrThrow(Registries.NOISE).asLookup(),SEED);
        var runs=new java.util.ArrayList<Integer>();
        for(int z=-2048;z<=2048;z+=256) {
            int previous=-1,start=-4096;boolean first=true;
            for(int x=-4096;x<=4096;x+=8) {
                var point=new DensityFunction.SinglePointContext(x,0,z);
                double c=fields.router().continents().compute(point),humidity=fields.router().vegetation().compute(point);
                int biome=c>.08?2:humidity>=.14?1:0;
                if(previous!=-1&&previous!=biome) {
                    if(!first)runs.add(x-start);first=false;start=x;
                }
                previous=biome;
            }
        }
        java.util.Collections.sort(runs);h.assertTrue(runs.size()>50,"The actual V4 climate produces too few biome transitions");
        int median=runs.get(runs.size()/2),p90=runs.get(runs.size()*9/10);
        h.assertTrue(median<=512&&p90<=1536,"The actual climate still makes giant unwalkable biome spans: median="+median+" p90="+p90);
        System.out.println("TERRAIN_V4_NATIVE_BIOME_RUNS count="+runs.size()+" median_blocks="+median+" p90_blocks="+p90);
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void seaLevelPlainsKeepFiveBlockRangeAndClimateBoundariesAreContinuous(GameTestHelper h) {
        double min=999,max=-999;
        for(int x=-1024;x<=1024;x+=16)for(int z=-1024;z<=1024;z+=16) {
            var plain=TerrainV4.column(SEED,PROFILE,x,z,-.5,1);min=Math.min(min,plain.groundHeight());max=Math.max(max,plain.groundHeight());
            h.assertTrue(plain.groundHeight()>=35&&plain.groundHeight()<40&&plain.density(1)>0,"A plain ceased to be sea-level grounded terrain");
            for(double edge:new double[]{TerrainV4.MOUNTAIN_START,TerrainV4.MOUNTAIN_CORE}) {
                double left=TerrainV4.column(SEED,PROFILE,x,z,edge-1e-7,1).groundHeight();
                double right=TerrainV4.column(SEED,PROFILE,x,z,edge+1e-7,1).groundHeight();
                h.assertTrue(Math.abs(left-right)<.001,"Biome threshold created a vertical terrain seam");
            }
        }
        h.assertTrue(max-min<5,"Plain variation exceeds the accepted five-block range");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void mountainsHaveSharpCragsVariedPeaksAndConnectedLowValleys(GameTestHelper h) {
        double min=999,max=-999,maxGrade=0,sum=0;int count=0,steep=0,low=0;
        for(int x=-1024;x<=1024;x+=8)for(int z=-1024;z<=1024;z+=8) {
            var a=TerrainV4.column(SEED,PROFILE,x,z,1,1);double height=a.groundHeight();
            double grade=Math.abs(height-TerrainV4.column(SEED,PROFILE,x+1,z,1,1).groundHeight());
            min=Math.min(min,height);max=Math.max(max,height);maxGrade=Math.max(maxGrade,grade);sum+=grade;count++;
            if(grade>1.3)steep++;if(height<55)low++;
            h.assertTrue(height<190&&a.density(1)>0&&a.density(190)<0,"Sharp mountains lost the foundation or upper gap");
        }
        h.assertTrue(min<45&&max>180&&max<190,"Mountain height range lost its low valleys or varied high peaks");
        h.assertTrue(maxGrade>3&&maxGrade<16&&steep/(double)count>.08&&sum/count>.4,"Crags still behave like broad rounded hills");
        h.assertTrue(low/(double)count>.2,"Smaller mountain regions erased broad low valley corridors");
        System.out.println("TERRAIN_V4_CRAGS height="+min+".."+max+" max_grade="+maxGrade+" steep_fraction="+steep/(double)count+" valley_fraction="+low/(double)count);
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void floatingMassesAreThickAndFaultSignCrossingsHaveNoHardRemovalJump(GameTestHelper h) {
        int occupied=0,thick=0,crossings=0;double maxCrossing=0;
        for(int x=-1024;x<=1024;x+=32)for(int z=-1024;z<=1024;z+=32) {
            var island=TerrainV4.column(SEED,PROFILE,x,z,-.5,-.5);int solid=0;
            for(int y=1;y<194;y++)if(island.density(y)>0){h.assertTrue(y>=PROFILE.minLand(),"Floating mass acquired a pillar below its lower gap");solid++;}
            if(solid>0)occupied++;if(solid>=30)thick++;
        }
        for(int x=-640;x<=640;x+=4)for(int z=-640;z<=640;z+=4) {
            var left=FloatingLandformsV4.column(SEED,PROFILE,x,z);var right=FloatingLandformsV4.column(SEED,PROFILE,x+1,z);
            for(int y=45;y<194;y+=12) {
                double a=left.density(y),b=right.density(y);
                if((a>0)!=(b>0)){crossings++;maxCrossing=Math.max(maxCrossing,Math.abs(a-b));}
            }
        }
        h.assertTrue(occupied>300&&thick>occupied*.7,"Island showcase still consists mostly of thin coastal strips");
        h.assertTrue(crossings>30&&maxCrossing<1.2,"A floating fault still performs a hard column-removal discontinuity: "+maxCrossing);
        System.out.println("TERRAIN_V4_FLOAT occupied="+occupied+" thick="+thick+" sign_crossings="+crossings+" largest_density_jump="+maxCrossing);
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void hydrologySurfaceCopyIsImmutableAndDoesNotRebuildFloatingFields(GameTestHelper h) {
        var original=TerrainV4.column(SEED,PROFILE,81,-173,.6,.5);var cut=original.withGroundHeight(original.groundHeight()-3);
        h.assertTrue(Math.abs(cut.groundHeight()+3-original.groundHeight())<1e-12&&cut.density(cut.groundHeight())==0,"Hydrology did not adjust the canonical density surface");
        h.assertTrue(original.groundHeight()==TerrainV4.column(SEED,PROFILE,81,-173,.6,.5).groundHeight(),"Hydrology changed the original column");
        var ash=TerrainV4.column(SEED,PROFILE,-321,487,-.5,-.5);var ashCut=ash.withGroundHeight(-20);
        for(int y=1;y<194;y++)h.assertTrue(Double.doubleToLongBits(ash.floatingDensity(y))==Double.doubleToLongBits(ashCut.floatingDensity(y)),"A continental river update altered the suspended field");
        var far=TerrainV4.column(SEED,PROFILE,29999800,29999800,1,1);var next=TerrainV4.column(SEED,PROFILE,29999801,29999800,1,1);
        h.assertTrue(Double.isFinite(far.groundHeight())&&Math.abs(far.groundHeight()-next.groundHeight())>1e-8,"Far-coordinate float rounding merged adjacent crags");
        h.succeed();
    }
}
