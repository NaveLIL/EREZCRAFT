package pro.erez.interstice.test;

import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.cave.CaveDensity;
import pro.erez.interstice.worldgen.terrain.TerrainColumn;
import pro.erez.interstice.worldgen.terrain.TerrainV2;
import pro.erez.interstice.worldgen.terrain.TerrainV3;

/** Numeric hydrology candidates and walkable entries; FULL chunk sea-fill has separate integration tests. */
@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class CaveV3GameTests {
    private static final long SEED=20261006L;
    private static final GeometryProfile PROFILE=GeometryProfile.TALL;
    private CaveV3GameTests() {}
    private static BiFunction<Integer,Integer,TerrainColumn> gardens(long seed) {
        return (x,z)->TerrainV3.column(seed,PROFILE,x,z,-.5,.5);
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void lowGardenGrottosFitOriginalSolidBandBelowTheSea(GameTestHelper h) {
        var raw=gardens(SEED);var context=CaveDensity.context(SEED,PROFILE,raw,3);
        int underground=0;
        for(int x=-128;x<=128;x+=4)for(int z=-128;z<=128;z+=4) {
            var terrain=raw.apply(x,z);var cave=context.column(x,z,-.5,.5,terrain::density);
            for(int y=1;y<5;y++)h.assertTrue(cave.carve(y,terrain.density(y))==terrain.density(y),"V3 grottos damage the solid foundation");
            for(int y=8;y<=30;y++) {
                double original=terrain.density(y),carved=cave.carve(y,original);
                h.assertTrue(carved<=original,"V3 cave created a new positive density");
                if(carved<=0) {
                    underground++;
                    h.assertTrue(original>0&&terrain.density(y+4)>0,"Dry interior classifier encountered exterior/raw ocean void or missing rock roof");
                }
            }
            for(int y=40;y<=48;y++)h.assertTrue(cave.carve(y,terrain.density(y))==terrain.density(y),"An exterior void was treated as a new dry cavern");
        }
        h.assertTrue(underground>1000,"Sea-level gardens lost their below-ground cave ecosystem: "+underground);
        System.out.println("CAVE_V3_SUBSEA_GARDENS interior_air_samples="+underground);
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void lowGardenSurfaceEntranceDescendsOnSupportedStepsIntoRealWorm(GameTestHelper h) {
        var raw=gardens(SEED);var context=CaveDensity.context(SEED,PROFILE,raw,3);
        CaveDensity.Entrance entrance=null;
        search:for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++) {
            var found=context.entrance(x,z);if(found.isPresent()){entrance=found.get();break search;}
        }
        h.assertTrue(entrance!=null,"Flat Y35..39 gardens have no discoverable cave entry");
        h.assertTrue(entrance.mouth().getY()>=PROFILE.lowerSeaTop()+1&&entrance.inner().getY()<=26,"Garden entrance is submerged or does not reach its root grottos");
        var surface=raw.apply(entrance.mouth().getX(),entrance.mouth().getZ());
        h.assertTrue(surface.density(entrance.mouth().getY())<=0&&surface.density(entrance.mouth().getY()-1)>0,"Entrance does not start on the natural surface");
        BlockPos previous=null;
        for(var pos:entrance.route()) {
            var terrain=raw.apply(pos.getX(),pos.getZ());var cave=context.column(pos.getX(),pos.getZ(),-.5,.5,terrain::density);
            for(int dy=1;dy<=2;dy++)h.assertTrue(cave.carve(pos.getY()-dy,terrain.density(pos.getY()-dy))>0,"Garden entry lost its two-block sealed floor");
            for(int dy=0;dy<3;dy++)h.assertTrue(cave.carve(pos.getY()+dy,terrain.density(pos.getY()+dy))<=0,"Garden adit lost player headroom");
            if(previous!=null)h.assertTrue(Math.abs(previous.getY()-pos.getY())<=1,"Descending adit contains a death shaft / unwalkable step");
            if(pos.getY()<=PROFILE.lowerSeaTop())for(int dx:new int[]{-4,0,4})for(int dz:new int[]{-4,0,4})
                h.assertTrue(raw.apply(pos.getX()+dx,pos.getZ()+dz).density(pos.getY())>0,"Subsea adit opens directly into exterior ocean");
            previous=pos;
        }
        System.out.println("CAVE_V3_GARDEN_ADIT mouth="+entrance.mouth()+" inner="+entrance.inner()+" steps="+entrance.route().size());
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void revisionsPreserveV2AndV3UsesCanonicalWeightsIndependentOfFootprint(GameTestHelper h) {
        BiFunction<Integer,Integer,TerrainColumn> old=(x,z)->TerrainV2.column(SEED,PROFILE,x,z,-.5,.5);
        var implicit=CaveDensity.context(SEED,PROFILE,old);var explicit=CaveDensity.context(SEED,PROFILE,old,2);
        var raw=gardens(SEED);var first=CaveDensity.context(SEED,PROFILE,raw,3);var second=CaveDensity.context(SEED,PROFILE,raw,3);
        for(int cell=10;cell<80;cell++)second.entrance(cell,-cell); // Force a different cache footprint / eviction.
        int different=0;
        var otherRaw=gardens(SEED+(1L<<60));var other=CaveDensity.context(SEED+(1L<<60),PROFILE,otherRaw,3);
        for(int x:new int[]{-129,-128,-65,-64,-1,0,15,16,63,64,127,128})for(int z:new int[]{-65,-1,0,63,64}) {
            var oldTerrain=old.apply(x,z);var a=implicit.column(x,z,-.5,.5,oldTerrain::density);var b=explicit.column(x,z,-.5,.5,oldTerrain::density);
            for(int y=6;y<100;y++)h.assertTrue(a.carve(y,oldTerrain.density(y))==b.carve(y,oldTerrain.density(y)),"Revision-three changes leaked into the default V2 cave path");
            var terrain=raw.apply(x,z);var c=first.column(x,z,-.5,.5,terrain::density);
            // Deliberately misleading climate: V3 must use raw column weights, not V2 thresholds.
            var d=second.column(x,z,.5,-.5,terrain::density);
            var otherTerrain=otherRaw.apply(x,z);var e=other.column(x,z,-.5,.5,otherTerrain::density);
            for(int y=8;y<34;y++) {
                h.assertTrue(c.carve(y,terrain.density(y))==d.carve(y,terrain.density(y)),"V3 room centers/weights depend on passed legacy climate or the caller's cache footprint");
                if((c.carve(y,terrain.density(y))<=0)!=(e.carve(y,otherTerrain.density(y))<=0))different++;
            }
        }
        h.assertTrue(different>10,"Revision-three caves ignore the high world-seed bits");
        h.succeed();
    }
}
