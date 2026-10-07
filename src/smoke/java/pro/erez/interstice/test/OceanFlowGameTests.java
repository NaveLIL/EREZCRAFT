package pro.erez.interstice.test;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.OceanLiquidBlock;

/** Real scheduled fluid ticks and bucket entry points, in disposable test structures. */
@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class OceanFlowGameTests {
    private static final BlockPos SOURCE = new BlockPos(6, 6, 6);
    private static void joiningStream(GameTestHelper h, boolean stepped) {
        var world = h.getLevel();
        Map<BlockPos, BlockState> held = new HashMap<>();
        for (int x=2;x<=12;x++) for (int z=2;z<=12;z++) {
            int bottom = stepped && x>6 ? 9 : 10;
            for (int y=bottom;y<=12;y++) {
                BlockPos pos = h.absolutePos(new BlockPos(x,y,z));
                BlockState state = Interstice.LIGHT_SEA.get().defaultBlockState().setValue(OceanLiquidBlock.CHAOTIC, stepped);
                world.setBlock(pos,state,3); held.put(pos,state);
            }
        }
        for(int y=5;y<=8;y++) for(int x=5;x<=7;x++) for(int z=5;z<=7;z++)
            if(x!=6 || z!=6 || y==5) h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        h.assertTrue(Interstice.LIGHT_BUCKET.get().emptyContents(null,world,h.absolutePos(SOURCE),null,
                Interstice.LIGHT_BUCKET.get().getDefaultInstance()),"Bucket placement failed");
        for (int tick : new int[]{90,180}) h.runAtTickTime(tick,()->{
            h.assertTrue(!world.getFluidState(h.absolutePos(new BlockPos(6,9,6))).isEmpty(),"Stream never reached ocean");
            for(int x=2;x<=12;x++) for(int z=2;z<=12;z++) {
                BlockPos pos=h.absolutePos(new BlockPos(x,9,z));
                if(!held.containsKey(pos) && (x!=6 || z!=6))
                    h.assertTrue(world.getFluidState(pos).isEmpty(),"Secondary layer grew at "+pos);
            }
            held.forEach((pos,state)->h.assertTrue(world.getBlockState(pos).equals(state),"Ocean relief was replaced at "+pos));
        });
        h.runAtTickTime(190,()->{
            var pos=h.absolutePos(SOURCE);
            h.assertTrue(Interstice.LIGHT_BLOCK.get().pickupBlock(null,world,pos,world.getBlockState(pos))
                    .is(Interstice.LIGHT_BUCKET.get()),"Source pickup failed");
        });
        h.runAtTickTime(320,()->{
            for(int y=6;y<=9;y++) h.assertTrue(world.getFluidState(h.absolutePos(new BlockPos(6,y,6))).isEmpty(),
                    "Held ocean kept orphaned stream alive");
            held.forEach((pos,state)->h.assertTrue(world.getBlockState(pos).equals(state),"Ocean changed after drainage"));
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=350)
    public static void risingBucketJoinsOceanWithoutSecondaryLayer(GameTestHelper h) { joiningStream(h,false); }
    @GameTest(template="empty",timeoutTicks=350)
    public static void risingBucketJoinsChaoticOceanStepWithoutSecondaryLayer(GameTestHelper h) { joiningStream(h,true); }

    @GameTest(template="empty",timeoutTicks=240)
    public static void heldOceanDoesNotSustainOldSideOrUpwardFlow(GameTestHelper h) {
        var world=h.getLevel();
        var sea=Interstice.LIGHT_SEA.get().defaultBlockState().setValue(OceanLiquidBlock.CHAOTIC,true);
        for(int x=3;x<=11;x++) for(int z=3;z<=11;z++) {
            h.setBlock(new BlockPos(x,9,z),sea);
            h.setBlock(new BlockPos(x,12,z),Blocks.STONE);
        }
        BlockPos side=new BlockPos(2,9,6), above=new BlockPos(6,10,6);
        for(var relative:new BlockPos[]{side,above}) {
            var flow=Interstice.LIGHT.get().getFlowing(7,false);
            h.setBlock(relative,flow.createLegacyBlock());
            world.scheduleTick(h.absolutePos(relative),flow.getType(),5);
        }
        h.runAtTickTime(180,()->{
            for(int x=1;x<=12;x++) for(int z=1;z<=12;z++) for(int y=9;y<=11;y++) {
                BlockPos pos=h.absolutePos(new BlockPos(x,y,z));
                if(y==9 && x>=3 && x<=11 && z>=3 && z<=11)
                    h.assertTrue(world.getBlockState(pos).equals(sea),"Old-flow cleanup changed held sea");
                else h.assertTrue(world.getFluidState(pos).isEmpty(),"Ocean supplied orphaned flow at "+pos);
            }
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void actualBucketMergesIntoOceanWithoutReplacingRelief(GameTestHelper h) {
        var world=h.getLevel();var pos=h.absolutePos(SOURCE);
        var sea=Interstice.LIGHT_SEA.get().defaultBlockState().setValue(OceanLiquidBlock.CHAOTIC,true);
        h.setBlock(SOURCE,sea);
        h.assertTrue(Interstice.LIGHT_BUCKET.get().emptyContents(null,world,pos,null,
                Interstice.LIGHT_BUCKET.get().getDefaultInstance()),"Bucket did not merge into ocean");
        h.assertTrue(world.getBlockState(pos).equals(sea),"Stack-sensitive bucket overwrote sea relief");
        h.assertTrue(Interstice.LIGHT_BUCKET.get().emptyContents(null,world,pos,null),"Legacy bucket call did not merge");
        h.assertTrue(world.getBlockState(pos).equals(sea),"Legacy bucket overwrote sea relief");
        h.assertTrue(Interstice.LIGHT_SEA.get().pickupBlock(null,world,pos,sea).is(Interstice.LIGHT_BUCKET.get()),
                "Ordinary source collection should remain available");
        h.succeed();
    }
}
