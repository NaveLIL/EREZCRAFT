package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.FluidContact;
import pro.erez.interstice.Interstice;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class FluidGameTests {
    private static final BlockPos SOURCE = new BlockPos(6, 6, 6);
    private static void tube(GameTestHelper h) {
        for(int y=2;y<=11;y++) for(int x=5;x<=7;x++) for(int z=5;z<=7;z++) {
            h.setBlock(new BlockPos(x,y,z), x==6 && z==6 && y>2 && y<11 ? Blocks.AIR : Blocks.STONE);
        }
    }
    private static void assertFluid(GameTestHelper h,BlockPos relative,Fluid expected,String message) {
        h.assertTrue(h.getLevel().getFluidState(h.absolutePos(relative)).getType().isSame(expected), message);
    }
    @GameTest(template="empty",timeoutTicks=130)
    public static void lightRisesAndNeverFalls(GameTestHelper h) {
        tube(h);h.setBlock(SOURCE,Interstice.LIGHT_BLOCK.get());
        h.runAtTickTime(90,() -> {
            assertFluid(h,new BlockPos(6,10,6),Interstice.LIGHT.get(),"Light fluid must rise four blocks");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(SOURCE.below())).isEmpty(),"Light fluid must never fall below its source");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=130)
    public static void heavyFallsAndNeverRises(GameTestHelper h) {
        tube(h);h.setBlock(SOURCE,Interstice.HEAVY_BLOCK.get());
        h.runAtTickTime(90,() -> {
            assertFluid(h,new BlockPos(6,3,6),Interstice.HEAVY.get(),"Heavy fluid must fall three blocks");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(SOURCE.above())).isEmpty(),"Heavy fluid must not rise");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=170)
    public static void lightSpreadsUnderCeiling(GameTestHelper h) {
        for(int x=2;x<=12;x++) for(int z=2;z<=12;z++) {
            h.setBlock(new BlockPos(x,10,z),Blocks.STONE);
            if(x==2||x==12||z==2||z==12) for(int y=3;y<10;y++) h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        }
        h.setBlock(SOURCE,Interstice.LIGHT_BLOCK.get());
        h.runAtTickTime(120,() -> {
            assertFluid(h,new BlockPos(9,9,6),Interstice.LIGHT.get(),"Rising stream must spread sideways beneath the ceiling");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(6,10,6))).isEmpty(),"Liquid must not cross a solid ceiling");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void solidBarrierStopsAscent(GameTestHelper h) {
        tube(h);h.setBlock(SOURCE.above(),Blocks.STONE);h.setBlock(SOURCE,Interstice.LIGHT_BLOCK.get());
        h.runAtTickTime(60,() -> {
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(SOURCE.above(2))).isEmpty(),"Light must not pass through a solid barrier");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=260)
    public static void removingSourceDrainsRisingStream(GameTestHelper h) {
        tube(h);h.setBlock(SOURCE,Interstice.LIGHT_BLOCK.get());
        h.runAtTickTime(60,() -> {
            assertFluid(h,SOURCE.above(3),Interstice.LIGHT.get(),"Stream must exist before removing source");
            h.setBlock(SOURCE,Blocks.AIR);
        });
        h.runAtTickTime(210,() -> {
            for(int y=3;y<=10;y++) h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(6,y,6))).isEmpty(),"Orphaned rising flow must drain after source removal");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void sourcesDoNotMultiply(GameTestHelper h) {
        tube(h);h.setBlock(SOURCE,Interstice.LIGHT_BLOCK.get());
        h.runAtTickTime(70,() -> {
            FluidState flow=h.getLevel().getFluidState(h.absolutePos(SOURCE.above()));
            h.assertTrue(!flow.isEmpty()&&!flow.isSource(),"Rising flow must not create permanent sources");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void ordinaryWaterStillFlowsDown(GameTestHelper h) {
        tube(h);h.setBlock(SOURCE,Blocks.WATER);
        h.runAtTickTime(60,() -> {
            assertFluid(h,SOURCE.below(2),net.minecraft.world.level.material.Fluids.WATER,"Vanilla water must retain downward flow");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(SOURCE.above())).isEmpty(),"Vanilla water must not inherit light-fluid behavior");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void bucketPlacesAndCollectsSource(GameTestHelper h) {
        tube(h);BlockPos absolute=h.absolutePos(SOURCE);
        h.assertTrue(Interstice.LIGHT_BUCKET.get().emptyContents(null,h.getLevel(),absolute,null),"Bucket must place light fluid");
        assertFluid(h,SOURCE,Interstice.LIGHT.get(),"Bucket must place registered source");
        var bucket=Interstice.LIGHT_BLOCK.get().pickupBlock(null,h.getLevel(),absolute,h.getLevel().getBlockState(absolute));
        h.assertTrue(bucket.is(Interstice.LIGHT_BUCKET.get()),"Picking up source must return its custom bucket");
        h.assertTrue(h.getLevel().getFluidState(absolute).isEmpty(),"Picking up must remove source");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void thinCeilingLayerHasCorrectContact(GameTestHelper h) {
        BlockPos pos=h.absolutePos(SOURCE);
        FluidState thin=Interstice.LIGHT.get().getFlowing(1,false);
        h.setBlock(SOURCE,thin.createLegacyBlock());
        AABB dry=new AABB(pos.getX()+0.2,pos.getY()+0.1,pos.getZ()+0.2,pos.getX()+0.8,pos.getY()+0.7,pos.getZ()+0.8);
        AABB wet=new AABB(pos.getX()+0.2,pos.getY()+0.90,pos.getZ()+0.2,pos.getX()+0.8,pos.getY()+0.99,pos.getZ()+0.8);
        h.assertTrue(FluidContact.overlap(h.getLevel(),pos,thin,dry)==0,"Air below ceiling liquid must be dry");
        h.assertTrue(FluidContact.overlap(h.getLevel(),pos,thin,wet)>0,"Top of voxel must be wet");
        var pig=h.spawn(EntityType.PIG,SOURCE);pig.setNoAi(true);pig.setNoGravity(true);
        pig.setBoundingBox(dry);pig.updateFluidHeightAndDoFluidPushing();
        h.assertTrue(pig.getFluidTypeHeight(Interstice.LIGHT_TYPE.get())==0,"Native fluid movement must ignore dry area below thin liquid");
        pig.setBoundingBox(wet);pig.updateFluidHeightAndDoFluidPushing();
        h.assertTrue(pig.getFluidTypeHeight(Interstice.LIGHT_TYPE.get())>0,"Native fluid movement must detect the actual ceiling layer");
        pig.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void heavyLiquidDamagesOnContact(GameTestHelper h) {
        for(int x=4;x<=8;x++) for(int z=4;z<=8;z++) {
            h.setBlock(new BlockPos(x,5,z),Blocks.STONE);
            h.setBlock(new BlockPos(x,6,z),Interstice.HEAVY_BLOCK.get());
        }
        var pig=h.spawn(EntityType.PIG,SOURCE);pig.setNoAi(true);pig.setNoGravity(true);
        h.runAtTickTime(60,() -> {h.assertTrue(pig.getHealth()<pig.getMaxHealth(),"Heavy toxin must damage a living entity on contact; ticks="+pig.tickCount+", pos="+pig.position()+", data="+pig.getPersistentData());h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void lightLiquidDamagesOnContact(GameTestHelper h) {
        tube(h);h.setBlock(SOURCE,Interstice.LIGHT_BLOCK.get());
        var pig=h.spawn(EntityType.PIG,SOURCE);pig.setNoAi(true);pig.setNoGravity(true);
        h.runAtTickTime(60,() -> {h.assertTrue(pig.getHealth()<pig.getMaxHealth(),"Light toxin must damage a living entity on contact; ticks="+pig.tickCount+", pos="+pig.position()+", data="+pig.getPersistentData());h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void toxinBypassesArmorAndDoesNotStackPerCell(GameTestHelper h) {
        for(int x=4;x<=8;x++) for(int z=4;z<=8;z++) {
            h.setBlock(new BlockPos(x,5,z),Blocks.STONE);
            for(int y=6;y<=8;y++) h.setBlock(new BlockPos(x,y,z),Interstice.HEAVY_BLOCK.get());
        }
        var golem=h.spawn(EntityType.IRON_GOLEM,SOURCE);golem.setNoAi(true);golem.setNoGravity(true);
        golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(20);
        h.runAtTickTime(10,() -> h.assertTrue(Math.abs(golem.getHealth()-94)<0.01,"One contact pulse must deal 6 damage through armor, regardless of occupied liquid cells; health="+golem.getHealth()));
        h.runAtTickTime(45,() -> {h.assertTrue(Math.abs(golem.getHealth()-82)<0.01,"Continuous contact must deal one pulse per 20 ticks; health="+golem.getHealth());h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void airBelowThinLiquidDoesNotDamage(GameTestHelper h) {
        BlockPos pos=h.absolutePos(SOURCE);
        FluidState thin=Interstice.LIGHT.get().getFlowing(1,false);
        h.setBlock(SOURCE,thin.createLegacyBlock());
        var pig=h.spawn(EntityType.PIG,SOURCE);pig.setNoAi(true);pig.setNoGravity(true);
        pig.setBoundingBox(new AABB(pos.getX()+0.2,pos.getY()+0.1,pos.getZ()+0.2,pos.getX()+0.8,pos.getY()+0.7,pos.getZ()+0.8));
        h.runAtTickTime(4,() -> {
            h.assertTrue(!h.getLevel().getFluidState(pos).isEmpty(),"Ceiling layer must still exist during dry-contact check");
            h.assertTrue(pig.getHealth()==pig.getMaxHealth(),"An entity in the dry part of the liquid voxel must take no toxin damage");h.succeed();
        });
    }

    private static BlockPos oceanPatch(GameTestHelper h) {
        BlockPos center=h.absolutePos(new BlockPos(6,0,6));
        for(int x=center.getX()-2;x<=center.getX()+2;x++)
            for(int z=center.getZ()-2;z<=center.getZ()+2;z++) pro.erez.interstice.SeaSurface.fillColumn(h.getLevel(),x,z);
        return center;
    }
    @GameTest(template="empty",timeoutTicks=130)
    public static void generatedOceanKeepsReliefInsteadOfSpreadingFlat(GameTestHelper h) {
        BlockPos center=oceanPatch(h);
        for(int x=center.getX()-2;x<=center.getX()+2;x++) for(int z=center.getZ()-2;z<=center.getZ()+2;z++) {
            int bottom=(int)Math.floor(pro.erez.interstice.SeaSurface.cellMinimum(x,z));
            h.getLevel().scheduleTick(new BlockPos(x,bottom,z),Interstice.LIGHT.get(),5);
        }
        h.runAtTickTime(90,() -> {
            for(int x=center.getX()-2;x<=center.getX()+2;x++) for(int z=center.getZ()-2;z<=center.getZ()+2;z++) {
                int bottom=(int)Math.floor(pro.erez.interstice.SeaSurface.cellMinimum(x,z));
                for(int y=bottom;y<pro.erez.interstice.SeaSurface.CEILING;y++)
                    h.assertTrue(h.getLevel().getBlockState(new BlockPos(x,y,z)).getBlock() instanceof pro.erez.interstice.OceanLiquidBlock,"Generated ocean must retain its full real volume after liquid ticks");
                h.assertTrue(h.getLevel().getFluidState(new BlockPos(x,bottom-1,z)).isEmpty(),"Generated ocean must not leak beneath its intended surface");
            }
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void slopedOceanContactAndEyeImmersionMatchVisibleSurface(GameTestHelper h) {
        BlockPos center=oceanPatch(h);
        double x=center.getX()+0.5,z=center.getZ()+0.5;
        double lower=pro.erez.interstice.SeaSurface.minimumUnderBox(new BlockPos(center.getX(),0,center.getZ()),x-0.46,x+0.46,z-0.46,z+0.46);
        var dry=h.spawn(EntityType.PIG,new BlockPos(6,0,6));dry.setNoAi(true);dry.setNoGravity(true);
        dry.moveTo(x,lower-dry.getBbHeight()-0.2,z,0,0);
        int wx=center.getX()+2,wz=center.getZ();
        double eyeTarget=pro.erez.interstice.SeaSurface.heightAt(wx+0.5,wz+0.5)+0.03;
        var wet=h.spawn(EntityType.PIG,new BlockPos(8,0,6));wet.setNoAi(true);wet.setNoGravity(true);
        wet.moveTo(wx+0.5,eyeTarget-(wet.getEyeY()-wet.getY()),wz+0.5,0,0);
        h.runAtTickTime(8,() -> {
            h.assertTrue(dry.getHealth()==dry.getMaxHealth(),"Air below a hill must be safe; health="+dry.getHealth());
            h.assertTrue(dry.getFluidTypeHeight(Interstice.LIGHT_TYPE.get())==0,"Dry space below the real surface must not enable swimming");
            h.assertTrue(wet.getHealth()<wet.getMaxHealth(),"Real hill volume must inflict toxin damage");
            h.assertTrue(wet.getFluidTypeHeight(Interstice.LIGHT_TYPE.get())>0,"Swimming must detect sloped ocean volume");
            h.assertTrue(wet.getEyeInFluidType()==Interstice.LIGHT_TYPE.get(),"Eye immersion must agree with the displayed sloped surface");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void dryCornerInsideOceanVoxelIsNotToxic(GameTestHelper h) {
        BlockPos center=oceanPatch(h);
        double x=center.getX()+0.55,z=center.getZ()+0.65;
        double y=pro.erez.interstice.SeaSurface.heightAt(x,z);
        // Find a genuine partial voxel rather than occasionally choosing an exact integer-height edge.
        for(int i=0;i<25 && y-Math.floor(y)<0.05;i++) {
            x=center.getX()-2+(i%5)+0.55;z=center.getZ()-2+(i/5)+0.65;
            y=pro.erez.interstice.SeaSurface.heightAt(x,z);
        }
        BlockPos cell=BlockPos.containing(x,y-0.02,z);
        h.assertTrue(h.getLevel().getBlockState(cell).getBlock() instanceof pro.erez.interstice.OceanLiquidBlock,"Fixture must be an occupied sea voxel");
        AABB dry=new AABB(x-0.001,y-0.03,z-0.001,x+0.001,y-0.02,z+0.001);
        h.assertTrue(FluidContact.overlap(h.getLevel(),cell,h.getLevel().getFluidState(cell),dry)==0,"Partially filled voxel must not damage the air below its actual triangular surface");
        h.assertFalse(FluidContact.pointInLight(h.getLevel(),x,y-0.02,z),"Eye below the slope must be dry");
        h.assertTrue(FluidContact.pointInLight(h.getLevel(),x,y+0.02,z),"Eye above the slope must be wet");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void legacyReliefBlocksKeepTheirOriginalSurface(GameTestHelper h) {
        BlockPos base=h.absolutePos(new BlockPos(6,0,6));
        int x=base.getX(),z=base.getZ();
        int bottom=(int)Math.floor(pro.erez.interstice.SeaSurface.cellMinimum(x,z,false));
        for(int y=bottom;y<pro.erez.interstice.SeaSurface.CEILING;y++)
            h.getLevel().setBlock(new BlockPos(x,y,z),Interstice.LIGHT_SEA.get().defaultBlockState(),2);
        double pointX=x+0.5,pointZ=z+0.5;
        double surface=pro.erez.interstice.SeaSurface.heightAt(pointX,pointZ,false);
        h.assertFalse(FluidContact.pointInLight(h.getLevel(),pointX,surface-0.05,pointZ),"Older saved ocean blocks must retain their dry region");
        h.assertTrue(FluidContact.pointInLight(h.getLevel(),pointX,surface+0.05,pointZ),"Older saved ocean blocks must retain their original wet surface");
        h.succeed();
    }

}
