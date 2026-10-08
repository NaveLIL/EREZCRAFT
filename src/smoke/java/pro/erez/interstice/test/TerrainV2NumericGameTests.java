package pro.erez.interstice.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.terrain.TerrainV2;

/** Numeric generator contracts; runtime chunk/column and native-image acceptance are separate checks. */
@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class TerrainV2NumericGameTests {
    private static final long SEED=20261006L;
    private TerrainV2NumericGameTests() {}

    @GameTest(template="empty",timeoutTicks=200)
    public static void weightsFollowNativeClimateAndHaveContinuousBoundaries(GameTestHelper h) {
        h.assertTrue(TerrainV2.weights(-.5,-.5).ash()==1,"Dry non-vault climate is not pure ash");
        h.assertTrue(TerrainV2.weights(-.5,.5).gardens()==1,"Humid non-vault climate is not pure garden");
        h.assertTrue(TerrainV2.weights(.5,.5).vaults()==1,"Vault climate must override humidity");
        for(double c:new double[]{-.5,.055,.08,.105,.5})for(double humidity:new double[]{-.5,.11,.14,.17,.5}) {
            var w=TerrainV2.weights(c,humidity);
            h.assertTrue(w.ash()>=0 && w.gardens()>=0 && w.vaults()>=0
                    && Math.abs(w.ash()+w.gardens()+w.vaults()-1)<1e-12,"Density weights must form a convex blend");
        }
        var left=TerrainV2.weights(.08-1e-8,.14-1e-8);
        var right=TerrainV2.weights(.08+1e-8,.14+1e-8);
        h.assertTrue(Math.abs(left.ash()-right.ash())<1e-5 && Math.abs(left.vaults()-right.vaults())<1e-5,
                "A discrete biome boundary leaked into density weights");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void groundedLandHasConnectedFoundationAndDifferentRelief(GameTestHelper h) {
        double minGarden=999,maxGarden=-999,minVault=999,maxVault=-999;
        for(int x=-2048;x<=2048;x+=64)for(int z=-2048;z<=2048;z+=64) {
            var garden=TerrainV2.column(SEED,GeometryProfile.TALL,x,z,-.5,.5);
            var vault=TerrainV2.column(SEED,GeometryProfile.TALL,x,z,.5,-.5);
            minGarden=Math.min(minGarden,garden.gardenHeight());maxGarden=Math.max(maxGarden,garden.gardenHeight());
            minVault=Math.min(minVault,vault.vaultHeight());maxVault=Math.max(maxVault,vault.vaultHeight());
            for(int y=1;y<garden.gardenHeight();y++)h.assertTrue(garden.density(y)>0,"Garden column has a floating foundation");
            for(int y=1;y<vault.vaultHeight();y++)h.assertTrue(vault.density(y)>0,"Mountain column has a floating foundation");
            h.assertTrue(vault.density(198)<0 && garden.density(198)<0,"Raw terrain reaches the upper clearance band");
        }
        h.assertTrue(minGarden>35 && maxGarden<100,"Garden terrain no longer leaves tree headroom");
        h.assertTrue(maxVault>185 && maxVault<198 && maxVault-minVault>90,"Mountains lack a visibly different height distribution");
        h.assertTrue(maxGarden-minGarden>20,"Gardens became a constant flat platform");
        System.out.println("TERRAIN_V2_NUMERIC garden="+minGarden+".."+maxGarden+" vault="+minVault+".."+maxVault);
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void ashBodiesAreSparseLargeAndDisconnectedFromBedrock(GameTestHelper h) {
        int occupied=0,empty=0; boolean broadBody=false;
        for(int x=-2048;x<=2048;x+=32)for(int z=-2048;z<=2048;z+=32) {
            var column=TerrainV2.column(SEED,GeometryProfile.TALL,x,z,-.5,-.5);
            boolean found=false;
            for(int y=1;y<198;y++) {
                if(column.density(y)>0) {
                    h.assertTrue(y>=GeometryProfile.TALL.minLand(),"An ash island acquired a bedrock-connected pillar");
                    found=true;
                }
            }
            if(found) {
                occupied++;
                var adjacent=TerrainV2.column(SEED,GeometryProfile.TALL,x+64,z,-.5,-.5);
                for(int y=60;y<=170;y++)if(column.density(y)>0 && adjacent.density(y)>0){broadBody=true;break;}
            } else empty++;
        }
        double coverage=occupied/(double)(occupied+empty);
        h.assertTrue(coverage>.08 && coverage<.38,"Ash coverage lost its rare-body / open-void character: "+coverage);
        h.assertTrue(broadBody,"No floating body spans at least 64 blocks");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void columnsAreDeterministicAcrossOrderSeedsAndFarCoordinates(GameTestHelper h) {
        int seedDifferences=0,farSteps=0;
        for(int x:new int[]{-29999900,-101,-1,0,1,101,29999900}) {
            var first=TerrainV2.column(SEED,GeometryProfile.TALL,x,71,-.5,.5);
            TerrainV2.column(SEED+1,GeometryProfile.TALL,-x,-71,.5,-.5);
            var again=TerrainV2.column(SEED,GeometryProfile.TALL,x,71,-.5,.5);
            var other=TerrainV2.column(SEED+(1L<<32),GeometryProfile.TALL,x,71,-.5,.5);
            var step=TerrainV2.column(SEED,GeometryProfile.TALL,x+1,71,-.5,.5);
            for(int y=1;y<198;y++)h.assertTrue(Double.doubleToLongBits(first.density(y))==Double.doubleToLongBits(again.density(y)),
                    "Another world or evaluation order changed a column");
            if(Math.abs(first.gardenHeight()-other.gardenHeight())>1e-8)seedDifferences++;
            if(Math.abs(x)>16000000 && Math.abs(first.gardenHeight()-step.gardenHeight())>1e-8)farSteps++;
        }
        h.assertTrue(seedDifferences>0,"High 32 seed bits do not affect generation");
        h.assertTrue(farSteps==2,"Far-coordinate float rounding merged adjacent columns");
        h.succeed();
    }
}
