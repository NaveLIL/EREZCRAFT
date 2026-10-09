package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.function.Consumer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.EchoRiftEntity;
import pro.erez.interstice.tether.RiftTethers;
import pro.erez.interstice.tether.WinchLinks;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.SeaSurface;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class EchoRiftGameTests {
    private EchoRiftGameTests() {}

    private static void cleanup(GameTestHelper h,Entity... entities){h.testInfo.addListener(new GameTestListener(){
        private void close(){for(var entity:entities){if(entity instanceof ServerPlayer player){WinchLinks.detach(player);TestPlayers.remove(player);}else entity.discard();}}
        @Override public void testStructureLoaded(GameTestInfo info){}
        @Override public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testAddedForRerun(GameTestInfo original,GameTestInfo rerun,GameTestRunner runner){close();}
    });}
    private static void room(GameTestHelper h){for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<8;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);}}
    private static EchoRiftEntity rift(GameTestHelper h){var rift=new EchoRiftEntity(Interstice.ECHO_RIFT.get(),h.getLevel());rift.setPos(h.absolutePos(new BlockPos(3,2,3)).getBottomCenter());h.getLevel().addFreshEntity(rift);cleanup(h,rift);return rift;}
    private static Zombie subject(GameTestHelper h){var mob=new Zombie(EntityType.ZOMBIE,h.getLevel());mob.setNoAi(true);mob.setInvulnerable(true);mob.setPos(h.absolutePos(new BlockPos(5,2,3)).getBottomCenter());h.getLevel().addFreshEntity(mob);cleanup(h,mob);return mob;}
    private static EchoRiftEntity.TrajectoryPoint point(Vec3 position){return new EchoRiftEntity.TrajectoryPoint(position,Vec3.ZERO,0,0,0,0);}

    @GameTest(template="empty",batch="echo_radius_nbt_bounds",timeoutTicks=100)
    public static void echoRadiusAndStabilityRejectNonfiniteOrUnboundedNbt(GameTestHelper h){
        var echo=new EchoRiftEntity(Interstice.ECHO_RIFT.get(),h.getLevel());echo.setRadius(Float.MAX_VALUE);h.assertTrue(echo.getRadius()==EchoRiftEntity.MAX_RADIUS,"Radius setter remained unbounded");echo.setRadius(-100);h.assertTrue(echo.getRadius()==EchoRiftEntity.MIN_RADIUS,"Negative radius remained invalid");echo.setStability(4);h.assertTrue(echo.getStability()==1,"Stability escaped its maximum");echo.setStability(-1);h.assertTrue(echo.getStability()==0,"Stability escaped its minimum");
        var saved=echo.saveWithoutId(new CompoundTag());saved.putFloat("Radius",Float.NaN);saved.putFloat("Stability",Float.POSITIVE_INFINITY);var restored=new EchoRiftEntity(Interstice.ECHO_RIFT.get(),h.getLevel());restored.load(saved);
        h.assertTrue(Float.isFinite(restored.getRadius())&&restored.getRadius()>=EchoRiftEntity.MIN_RADIUS&&restored.getRadius()<=EchoRiftEntity.MAX_RADIUS&&Float.isFinite(restored.getStability())&&restored.getStability()>=0&&restored.getStability()<=1,"Malformed saved float leaked into query/render state");h.succeed();
    }
    @GameTest(template="empty",batch="echo_live_collision_guard",timeoutTicks=100)
    public static void oldSnapshotNowBlockedByStoneCannotRewindLivingBody(GameTestHelper h){room(h);var echo=rift(h);var mob=subject(h);var original=mob.position();var target=h.absolutePos(new BlockPos(3,2,3)).getBottomCenter();h.setBlock(new BlockPos(3,2,3),Blocks.STONE);h.setBlock(new BlockPos(3,3,3),Blocks.STONE);mob.fallDistance=9;echo.performRewind(mob,point(target),h.getLevel());h.assertTrue(mob.position().equals(original)&&mob.fallDistance==9,"Unsafe stale solid snapshot teleported/reset the entity");h.succeed();}
    @GameTest(template="empty",batch="echo_loaded_fluid_border_guard",timeoutTicks=100)
    public static void fluidBorderAndUnloadedSnapshotsAreRejectedWithoutLoading(GameTestHelper h){room(h);var echo=rift(h);var mob=subject(h);var original=mob.position();var target=h.absolutePos(new BlockPos(3,2,3)).getBottomCenter();h.setBlock(new BlockPos(3,2,3),Blocks.WATER);echo.performRewind(mob,point(target),h.getLevel());h.assertTrue(mob.position().equals(original),"Fluid snapshot accepted");echo.performRewind(mob,point(new Vec3(1.0E20,2,1.0E20)),h.getLevel());h.assertTrue(mob.position().equals(original),"World-border snapshot accepted");
        var remote=original.add(4096,0,4096);int before=h.getLevel().getChunkSource().getLoadedChunksCount();echo.performRewind(mob,point(remote),h.getLevel());h.assertTrue(mob.position().equals(original)&&h.getLevel().getChunkSource().getLoadedChunksCount()==before,"Unloaded stale snapshot moved the body or synchronously loaded chunks");h.succeed();}
    @GameTest(template="empty",batch="echo_native_safe_history",timeoutTicks=100)
    public static void nativeSixtyTickLoopSkipsBlockedOldHistoryAndKeepsSafeLaterPoint(GameTestHelper h){room(h);var echo=rift(h);var mob=subject(h);var unsafe=h.absolutePos(new BlockPos(3,2,3)).getBottomCenter();mob.setPos(unsafe);
        h.runAtTickTime(4,()->{mob.setPos(unsafe.add(1.5,0,0));h.setBlock(new BlockPos(3,2,3),Blocks.STONE);h.setBlock(new BlockPos(3,3,3),Blocks.STONE);});
        h.runAtTickTime(65,()->{h.assertTrue(mob.distanceToSqr(unsafe)>1&&!mob.isInWall(),"Actual60-tick history rewound into the now-blocked oldest point");h.succeed();});}
    @GameTest(template="empty",batch="echo_passenger_rope_guard",timeoutTicks=100)
    public static void echoCannotRewindRealPassengerOrOwnedWinchPlayer(GameTestHelper h){room(h);var echo=rift(h);var player=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);var boat=EntityType.BOAT.create(h.getLevel());h.assertTrue(boat!=null,"Native boat fixture unavailable");boat.setPos(player.position());h.getLevel().addFreshEntity(boat);cleanup(h,player,boat);h.assertTrue(player.startRiding(boat,true),"Actual passenger fixture failed");var riding=player.position();echo.performRewind(player,point(riding.add(1,0,0)),h.getLevel());h.assertTrue(player.position().equals(riding)&&player.getVehicle()==boat,"Echo displaced an actual rider");player.stopRiding();boat.discard();
        var approach=h.absolutePos(new BlockPos(3,2,1));player.teleportTo(h.getLevel(),approach.getX()+.5,approach.getY()+.01,approach.getZ()+.5,java.util.Set.of(),0,0);player.hasChangedDimension();var anchor=h.absolutePos(new BlockPos(3,4,3));h.getLevel().setBlock(anchor,RiftTethers.WINCH.get().defaultBlockState(),3);h.assertTrue(WinchLinks.attach(player,h.getLevel(),anchor,32),"Owned rope fixture failed near-anchor validation");var tied=player.position();echo.performRewind(player,point(tied.add(1,0,0)),h.getLevel());h.assertTrue(player.position().equals(tied)&&WinchLinks.link(player)!=null,"Echo displaced/detached an owned rope player");h.succeed();}
    @GameTest(template="empty",batch="echo_owner_velocity_sync",timeoutTicks=100)
    public static void rewindAndShardMarkVelocityForTheActualPlayerOwner(GameTestHelper h){room(h);var echo=rift(h);var player=TestPlayers.create(h,new BlockPos(5,2,3),GameType.SURVIVAL);cleanup(h,player);player.hurtMarked=false;player.hasImpulse=false;var target=player.position().add(-1,0,0);var velocity=new Vec3(.1,.2,.3);echo.performRewind(player,new EchoRiftEntity.TrajectoryPoint(target,velocity,0,0,8,0),h.getLevel());h.assertTrue(player.position().distanceToSqr(target)<.00001&&player.getDeltaMovement().equals(velocity)&&player.hurtMarked&&player.hasImpulse,"Rewind motion lacks the vanilla owner-synchronization marker");
        player.hurtMarked=false;player.hasImpulse=false;player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Interstice.ECHO_SHARD.get()));player.gameMode.useItem(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND);h.assertTrue(player.hurtMarked&&player.hasImpulse&&player.getDeltaMovement().y==.4,"Native serverGameMode shard use did not mark its own-player impulse");h.succeed();}
    @GameTest(template="empty",batch="echo_cancelled_anchor_transaction",timeoutTicks=100)
    public static void cancelledEntityJoinRejectsAnchorAndPreservesItsStack(GameTestHelper h){room(h);var player=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);cleanup(h,player);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Interstice.ECHO_RIFT_ANCHOR.get(),2));var clicked=h.absolutePos(new BlockPos(3,1,3));Consumer<EntityJoinLevelEvent> cancel=event->{if(event.getLevel()==h.getLevel()&&event.getEntity() instanceof EchoRiftEntity&&event.getEntity().distanceToSqr(Vec3.atCenterOf(clicked.above()))<4)event.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
        try{var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(clicked),Direction.UP,clicked,false));var result=Interstice.ECHO_RIFT_ANCHOR.get().useOn(context);h.assertTrue(result==net.minecraft.world.InteractionResult.FAIL&&player.getMainHandItem().getCount()==2,"Cancelled actual entity spawn consumed/reported a successful anchor");}finally{NeoForge.EVENT_BUS.unregister(cancel);}h.succeed();}
    @GameTest(template="empty",batch="echo_actual_upper_sea_clearance",timeoutTicks=200)
    public static void actualRealmAirSnapshotStillRequiresUpperSeaClearance(GameTestHelper h){var level=h.getLevel().getServer().getLevel(IslandWorld.TENSION_WORLD);h.assertTrue(level!=null,"Actual V6 ServerLevel required");level.getChunk(0,0);var profile=GeometryProfiles.get(level);int x=8,z=8;double sea=SeaSurface.cellMinimum(profile,x,z,true);var target=new Vec3(x+.5,Math.floor(sea)-4,z+.5);var echo=new EchoRiftEntity(Interstice.ECHO_RIFT.get(),level);echo.setPos(x+.5,100,z+.5);var mob=new Zombie(EntityType.ZOMBIE,level);mob.setPos(x+.5,90,z+.5);var original=mob.position();
        h.assertTrue(level.getBlockState(BlockPos.containing(target)).isAir()&&level.getBlockState(BlockPos.containing(target).above()).isAir(),"Actual upper-clearance candidate is not air; fixture must exercise clearance rather than fluid/collision rejection");echo.performRewind(mob,point(target),level);h.assertTrue(mob.position().equals(original),"Air snapshot ignored actual toxic upper-sea clearance");h.succeed();}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void echoRiftRewindsPositionAndResetsFallDistance(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(3, 2, 3)).getBottomCenter();
        var rift = new EchoRiftEntity(Interstice.ECHO_RIFT.get(), h.getLevel());
        rift.setPos(center);
        h.getLevel().addFreshEntity(rift);cleanup(h,rift);

        // Spawn a living entity inside the anomaly
        var zombie = new Zombie(EntityType.ZOMBIE, h.getLevel());
        var startPos = center.add(0.5, 0, 0.5);
        zombie.setPos(startPos);
        h.getLevel().addFreshEntity(zombie);cleanup(h,zombie);

        // Record initial state in rift
        rift.tick();

        // Move entity forward and simulate fall distance
        var movedPos = startPos.add(1.5, 0, 1.5);
        zombie.setPos(movedPos);
        zombie.fallDistance = 18.0F;

        // Simulate loop threshold by running ticks to trigger rewind
        for (int i = 0; i < EchoRiftEntity.BUFFER_SIZE; i++) {
            rift.tick();
        }

        // Entity must have been snapped back to startPos and fall distance reset
        h.assertTrue(zombie.distanceToSqr(startPos) < 0.25, "Entity should be rewound to start position");
        h.assertTrue(zombie.fallDistance == 0.0F, "Fall distance must be reset to 0 after spatial rewind");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void echoRiftCooldownPreventsImmediateReLoop(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(3, 2, 3)).getBottomCenter();
        var rift = new EchoRiftEntity(Interstice.ECHO_RIFT.get(), h.getLevel());
        rift.setPos(center);
        h.getLevel().addFreshEntity(rift);cleanup(h,rift);

        var zombie = new Zombie(EntityType.ZOMBIE, h.getLevel());
        zombie.setPos(center);
        h.getLevel().addFreshEntity(zombie);cleanup(h,zombie);

        // Complete 1 loop
        for (int i = 0; i < EchoRiftEntity.BUFFER_SIZE + 2; i++) {
            rift.tick();
        }

        // Entity is now under immunity cooldown
        var midPos = center.add(1.0, 0, 1.0);
        zombie.setPos(midPos);

        // Next 5 ticks should NOT rewind immediately
        for (int i = 0; i < 5; i++) {
            rift.tick();
        }

        h.assertTrue(zombie.distanceToSqr(midPos) < 0.1, "Entity under immunity cooldown must not be rewound immediately");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void echoRiftDeflectsProjectilesWithReversedVelocity(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(3, 2, 3)).getBottomCenter();
        var rift = new EchoRiftEntity(Interstice.ECHO_RIFT.get(), h.getLevel());
        rift.setPos(center);
        h.getLevel().addFreshEntity(rift);cleanup(h,rift);

        // Shoot arrow towards rift
        var arrow = new Arrow(h.getLevel(), center.x - 2.0, center.y + 0.8, center.z, new ItemStack(Items.ARROW), null);
        arrow.setDeltaMovement(1.0, 0, 0); // Flying in +X direction
        h.getLevel().addFreshEntity(arrow);cleanup(h,arrow);

        // Rift ticks and detects projectile
        rift.tick();

        // Projectile velocity must be reversed (-X)
        Vec3 vel = arrow.getDeltaMovement();
        h.assertTrue(vel.x < 0.0, "Arrow velocity X must be reversed into negative direction upon rift entry");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void echoRiftHarvestingWithBottleYieldsShard(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(3, 2, 3)).getBottomCenter();
        var rift = new EchoRiftEntity(Interstice.ECHO_RIFT.get(), h.getLevel());
        rift.setPos(center);
        rift.setStability(1.0F);
        h.getLevel().addFreshEntity(rift);cleanup(h,rift);

        var player = TestPlayers.create(h, new BlockPos(3, 2, 3), GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));

        var result = rift.interact(player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.consumesAction(), "Interacting with bottle must consume action");

        // Player must have received Echo Shard
        boolean hasShard = player.getInventory().contains(new ItemStack(Interstice.ECHO_SHARD.get()));
        h.assertTrue(hasShard, "Player must receive Echo Shard in inventory");
        h.assertTrue(rift.getStability() < 0.5F, "Rift stability must deplete after harvesting");

        TestPlayers.remove(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void echoShardItemUsageResetsFallAndAppliesSlip(GameTestHelper h) {
        var player = TestPlayers.create(h, new BlockPos(2, 2, 2), GameType.SURVIVAL);
        var shardStack = new ItemStack(Interstice.ECHO_SHARD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, shardStack);
        player.fallDistance = 25.0F;

        // Use echo shard
        var shardItem = Interstice.ECHO_SHARD.get();
        shardItem.use(h.getLevel(), player, InteractionHand.MAIN_HAND);

        h.assertTrue(player.fallDistance == 0.0F, "Using Echo Shard must reset fall distance to 0");
        h.assertTrue(player.getCooldowns().isOnCooldown(shardItem), "Echo Shard must enter cooldown after usage");

        TestPlayers.remove(player);
        h.succeed();
    }
}
