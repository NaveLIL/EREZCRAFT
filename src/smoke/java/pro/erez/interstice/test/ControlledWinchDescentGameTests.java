package pro.erez.interstice.test;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.tether.RiftTethers;
import pro.erez.interstice.tether.WinchBlock;
import pro.erez.interstice.tether.WinchBlockEntity;
import pro.erez.interstice.tether.WinchLinks;

/** Native block ticks and collision movement; mock connections are not a dedicated-client network proof. */
@GameTestHolder("interstice_tethers")
@PrefixGameTestTemplate(false)
public final class ControlledWinchDescentGameTests {
    private static final BlockPos ANCHOR=new BlockPos(6,64,6);
    private record Fixture(ServerPlayer player,WinchBlockEntity node) {}
    private ControlledWinchDescentGameTests() {}
    private static Fixture fixture(GameTestHelper h){
        // This disposable vertical shaft removes only this test's barrier/terrain columns.
        for(int x=4;x<=8;x++)for(int z=4;z<=8;z++)for(int y=1;y<=67;y++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),(y==1?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
        h.getLevel().setBlock(h.absolutePos(ANCHOR),RiftTethers.WINCH.get().defaultBlockState().setValue(WinchBlock.FACING,Direction.NORTH),3);
        var node=(WinchBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(ANCHOR));
        var p=TestPlayers.create(h,ANCHOR.offset(0,0,-2),GameType.SURVIVAL);
        h.assertTrue(WinchLinks.attach(p,h.getLevel(),node.getBlockPos(),8),"Controlled descent fixture cannot attach through ordinary near-anchor validation");
        var source=WinchLinks.source(node);p.teleportTo(h.getLevel(),source.x,source.y-8.15-p.getBbHeight()*.55,source.z,Set.of(),0,0);p.hasChangedDimension();
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RiftTethers.TETHER_SPOOL.get()));p.setShiftKeyDown(true);
        p.setOnGround(false);p.setDeltaMovement(new Vec3(0,-.10,0));p.setKnownMovement(new Vec3(0,-.18,0));
        return new Fixture(p,node);
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void nativeTicksProduceTwoHundredMovingControlledTicksWithoutFlightOrTeleport(GameTestHelper h){
        var f=fixture(h);var p=f.player;double initial=p.getY();int[] controlled={0};double[] largest={0};
        for(int tick=1;tick<=240;tick++)h.runAtTickTime(tick,()->{
            if(!p.isAlive())throw new IllegalStateException("Controlled fixture died before its landing");
            Vec3 before=p.position();Vec3 velocity=p.getDeltaMovement();p.move(MoverType.SELF,velocity);
            Vec3 moved=p.position().subtract(before);largest[0]=Math.max(largest[0],moved.length());
            // The harness supplies ordinary gravity input; the real block ticker chooses the
            // resulting impulse. Entity.move performs real collision, never teleportation.
            p.setKnownMovement(velocity.add(0,-.08,0));
            var hook=f.node.hook(p.getUUID());if(hook!=null&&hook.descending()){
                controlled[0]++;h.assertTrue(moved.y<-.03125,"A claimed controlled tick is static/vanilla-floating movement");
                h.assertTrue(hook.paidLength()<=48&&WinchLinks.link(p)!=null,"Controlled payout became infinite or lost its owner");
            }
        });
        h.runAtTickTime(245,()->{try{
            var hook=f.node.hook(p.getUUID());
            h.assertTrue(hook!=null&&hook.descentTicks()>=200&&controlled[0]>=200,"Real block ticks did not maintain two hundred controlled descending ticks: "+hook+", moving="+controlled[0]);
            h.assertTrue(initial-p.getY()>=8&&largest[0]<.5,"The descent did not move eight blocks smoothly without position jumps");
            h.assertTrue(!p.getAbilities().mayfly&&!p.getAbilities().flying&&!p.noPhysics,"Controlled descent altered flight/collision abilities");
            var encoded=f.node.saveWithoutMetadata(h.getLevel().registryAccess());var restored=new WinchBlockEntity(f.node.getBlockPos(),f.node.getBlockState());
            restored.loadWithComponents(encoded,h.getLevel().registryAccess());
            h.assertTrue(restored.anchorId().equals(f.node.anchorId())&&restored.hooks().size()==1
                    &&restored.hook(p.getUUID()).paidLength()==hook.paidLength()&&!restored.hook(p.getUUID()).descending(),"Cold-compatible payout or owned hook identity was lost/active force persisted unvalidated");
            System.out.println("V6_WINCH_CONTROLLED_TICKS ticks="+controlled[0]+" descent_ticks="+hook.descentTicks()+" actual_drop="+(initial-p.getY())+" max_move="+largest[0]+" paid_length="+hook.paidLength()+" scope=native_block_ticks_mock_connection_collision_motion");h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void reachingFiniteRopeEndReleasesWithoutInventingFallProtection(GameTestHelper h){
        var f=fixture(h);var p=f.player;
        try{
            var saved=f.node.saveWithoutMetadata(h.getLevel().registryAccess());var hook=saved.getList("Hooks",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            hook.putInt("Length",48);hook.putDouble("PaidLength",48);f.node.loadWithComponents(saved,h.getLevel().registryAccess());
            var persisted=p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);persisted.getCompound(WinchLinks.KEY).putInt("Length",48);
            p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,persisted);
            var source=WinchLinks.source(f.node);p.teleportTo(h.getLevel(),source.x,source.y-48.15-p.getBbHeight()*.55,source.z,Set.of(),0,0);p.hasChangedDimension();
            p.setOnGround(false);p.fallDistance=17;p.setKnownMovement(new Vec3(0,-.3,0));p.setDeltaMovement(new Vec3(0,-.3,0));f.node.process();
            h.assertTrue(WinchLinks.link(p)==null&&f.node.hooks().isEmpty()&&p.fallDistance==17&&p.getDeltaMovement().y==-.3,"Finite rope end grants phantom support or resets a disconnected fall");h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void toolSneakSupportAndObstructionAreRequiredForFallProtection(GameTestHelper h){
        var f=fixture(h);var p=f.player;
        try{
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.fallDistance=17;f.node.process();
            h.assertTrue(p.fallDistance==17&&!f.node.hook(p.getUUID()).descending(),"An untyped held item received controlled fall protection");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RiftTethers.TETHER_SPOOL.get()));p.setShiftKeyDown(false);p.fallDistance=17;f.node.process();
            h.assertTrue(p.fallDistance==17&&!f.node.hook(p.getUUID()).descending(),"Absent server sneak intent received controlled fall protection");
            p.setShiftKeyDown(true);p.setOnGround(true);p.fallDistance=17;f.node.process();
            h.assertTrue(p.fallDistance==17&&!f.node.hook(p.getUUID()).descending(),"Ground contact failed to cancel controlled descent");
            p.setOnGround(false);h.getLevel().setBlock(BlockPos.containing(WinchLinks.source(f.node).lerp(WinchLinks.endpoint(p),.5)),Blocks.STONE.defaultBlockState(),3);
            p.fallDistance=17;f.node.process();h.assertTrue(WinchLinks.link(p)==null&&f.node.hooks().isEmpty()&&p.fallDistance==17,"Obstructed rope continues fall protection/control");h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void actualWaterLadderAndCollisionCancelControlledProtection(GameTestHelper h){
        var f=fixture(h);var p=f.player;
        try{
            var feet=p.blockPosition();h.getLevel().setBlock(feet,Blocks.WATER.defaultBlockState(),3);p.baseTick();
            h.assertTrue(p.isInWater(),"Water cancellation fixture is not actually in water");
            p.fallDistance=17;f.node.process();h.assertTrue(p.fallDistance==17&&!f.node.hook(p.getUUID()).descending(),"Water keeps controlled winch protection active");
            h.getLevel().setBlock(feet,Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING,Direction.NORTH),3);p.baseTick();
            h.assertTrue(p.onClimbable(),"Ladder cancellation fixture is not actually climbable");
            p.fallDistance=17;f.node.process();h.assertTrue(p.fallDistance==17&&!f.node.hook(p.getUUID()).descending(),"A real ladder keeps controlled winch protection active");
            h.getLevel().setBlock(feet,Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(feet.east(),Blocks.STONE.defaultBlockState(),3);p.baseTick();
            p.move(MoverType.SELF,new Vec3(1,0,0));h.assertTrue(p.horizontalCollision,"Horizontal cancellation fixture did not physically collide");
            p.fallDistance=17;f.node.process();h.assertTrue(p.fallDistance==17&&(f.node.hook(p.getUUID())==null||!f.node.hook(p.getUUID()).descending()),"Real horizontal collision retains controlled winch protection");
            h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void actualVehiclePassengerReleasesItsLoadedOwnedHook(GameTestHelper h){
        var f=fixture(h);var p=f.player;net.minecraft.world.entity.vehicle.Boat boat=null;
        try{
            h.assertTrue(WinchLinks.matches(p,f.node)&&f.node.hook(p.getUUID())!=null,"Vehicle fixture lacks an owned loaded hook");
            boat=net.minecraft.world.entity.EntityType.BOAT.create(h.getLevel());h.assertTrue(boat!=null,"Boat fixture unavailable");boat.setPos(p.position());h.getLevel().addFreshEntity(boat);
            h.assertTrue(p.startRiding(boat,true)&&p.isPassenger(),"Passenger cancellation fixture did not enter its real vehicle");p.fallDistance=17;f.node.process();
            h.assertTrue(WinchLinks.link(p)==null&&f.node.hooks().isEmpty()&&p.fallDistance==17,"Vehicle passenger retains controlled winch ownership/protection");h.succeed();
        }finally{WinchLinks.detach(p);p.stopRiding();if(boat!=null)boat.discard();TestPlayers.remove(p);}
    }
}
