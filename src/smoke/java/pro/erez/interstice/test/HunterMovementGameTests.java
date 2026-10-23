package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.*;
import pro.erez.interstice.fauna.*;

@GameTestHolder("interstice_hunters")
@PrefixGameTestTemplate(false)
public final class HunterMovementGameTests {
    private static void cleanup(GameTestHelper h, Entity... entities) {
        h.testInfo.addListener(new GameTestListener() {
            void close() { for (var e : entities) { if (e instanceof net.minecraft.server.level.ServerPlayer p) TestPlayers.remove(p); else e.discard(); } }
            public void testStructureLoaded(GameTestInfo i) {}
            public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
            public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
            public void testAddedForRerun(GameTestInfo i, GameTestInfo r, GameTestRunner runner) { close(); }
        });
    }
    private static void room(GameTestHelper h) {
        // Disposable flat test arena: explicitly prepare vanilla's complete A* cache.
        BlockPos center=h.absolutePos(new BlockPos(8,2,5));
        for(int x=(center.getX()-48)>>4;x<=(center.getX()+48)>>4;x++)for(int z=(center.getZ()-48)>>4;z<=(center.getZ()+48)>>4;z++)h.getLevel().getChunk(x,z);
        for (int x=0;x<=16;x++) for (int z=0;z<=10;z++) {
            h.setBlock(new BlockPos(x,1,z), Blocks.STONE);
            for (int y=2;y<=11;y++) h.setBlock(new BlockPos(x,y,z), Blocks.AIR);
        }
    }
    private static CaveRiftSpiderEntity spider(GameTestHelper h, CaveRiftSpiderEntity.Variant variant) {
        var spider = Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());
        spider.setVariant(variant); spider.setPersistenceRequired(); spider.moveTo(h.absoluteVec(new Vec3(3.5,2,4.5)));
        h.getLevel().addFreshEntity(spider); cleanup(h,spider); return spider;
    }
    private static void chamber(GameTestHelper h) {
        room(h);
        for (int y=2;y<=9;y++) for (int z=1;z<=8;z++) h.setBlock(new BlockPos(1,y,z),Blocks.STONE);
        for (int x=1;x<=12;x++) for (int z=1;z<=8;z++) h.setBlock(new BlockPos(x,9,z),Blocks.STONE);
    }
    private static void traverse(GameTestHelper h, CaveRiftSpiderEntity.Variant variant) {
        chamber(h); var s=spider(h,variant);
        Vec3 goal=h.absoluteVec(new Vec3(9.5,9-s.getBbHeight()/2,4.5));
        h.assertTrue(s.crawlTo(goal),"No continuous ground-wall-ceiling route for "+variant);
        boolean[] wall={false}; double initialY=s.getY();
        h.onEachTick(()->{ if(s.isClimbingWall() && s.getY()>initialY+1) wall[0]=true;
            h.assertTrue(h.getLevel().noCollision(s,s.getBoundingBox().deflate(.01)),"Body entered blocks at "+s.position()); });
        h.runAtTickTime(200,()->{
            h.assertTrue(wall[0],"Spider did not climb the wall");
            h.assertTrue(s.isClimbingCeiling() && s.getBoundingBox().getCenter().distanceTo(goal)<.3,"Spider did not walk across ceiling: "+s.position());
            h.succeed();
        });
    }
    @GameTest(template="empty",batch="crawl_small",timeoutTicks=230) public static void skirmisherWalksFromFloorViaWallAcrossCeiling(GameTestHelper h){traverse(h,CaveRiftSpiderEntity.Variant.SKIRMISHER);}
    @GameTest(template="empty",batch="crawl_large",timeoutTicks=230) public static void lurkerWalksFromFloorViaWallAcrossCeiling(GameTestHelper h){traverse(h,CaveRiftSpiderEntity.Variant.LURKER);}
    @GameTest(template="empty",batch="crawl_spitter",timeoutTicks=230) public static void spitterWalksFromFloorViaWallAcrossCeiling(GameTestHelper h){traverse(h,CaveRiftSpiderEntity.Variant.SPITTER);}
    @GameTest(template="empty",batch="crawl_autonomous",timeoutTicks=300) public static void spitterFindsWallAndCeilingWithoutManualRoute(GameTestHelper h){
        chamber(h);var s=spider(h,CaveRiftSpiderEntity.Variant.SPITTER);
        for(int x=8;x<=11;x++)for(int z=3;z<=6;z++)h.setBlock(new BlockPos(x,6,z),Blocks.STONE);
        var prey=new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE,h.getLevel());
        prey.setNoAi(true);prey.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);prey.setHealth(1000);prey.moveTo(h.absoluteVec(new Vec3(9.5,7,4.5)));h.getLevel().addFreshEntity(prey);cleanup(h,prey);s.setTarget(prey);
        h.runAtTickTime(260,()->{h.assertTrue(s.isClimbingCeiling()&&s.getY()>h.absolutePos(new BlockPos(0,7,0)).getY(),"Natural AI never climbed: "+s.position());h.succeed();});
    }
    @GameTest(template="empty",batch="crawl_gap",timeoutTicks=100) public static void unsupportedAirIsNotAClimbRoute(GameTestHelper h){
        room(h);var s=spider(h,CaveRiftSpiderEntity.Variant.SKIRMISHER);
        h.setBlock(new BlockPos(3,9,4),Blocks.STONE);
        h.assertTrue(!s.crawlTo(h.absoluteVec(new Vec3(3.5,8.4,4.5))),"Spider flew to disconnected ceiling");h.succeed();
    }
    @GameTest(template="empty",batch="crawl_support",timeoutTicks=90) public static void removedCeilingReleasesAdhesion(GameTestHelper h){
        chamber(h);var s=spider(h,CaveRiftSpiderEntity.Variant.SPITTER);s.moveTo(h.absoluteVec(new Vec3(5.5,9-s.getBbHeight(),4.5)));
        h.runAtTickTime(8,()->{
            h.assertTrue(s.isClimbingCeiling(),"Spider never attached");
            for(int x=1;x<=12;x++)for(int z=1;z<=8;z++)h.setBlock(new BlockPos(x,9,z),Blocks.AIR);
        });
        h.runAtTickTime(25,()->{h.assertTrue(!s.isClimbingCeiling()&&s.getY()<h.absolutePos(new BlockPos(0,6,0)).getY(),"Spider hung in air after support removed");h.succeed();});
    }
    @GameTest(template="empty",batch="crawl_hit",timeoutTicks=40) public static void upperBodyHitAndSavedVariantDimensions(GameTestHelper h){
        for(var variant:CaveRiftSpiderEntity.Variant.values()){
            var s=spider(h,variant);var saved=s.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            var restored=Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());restored.load(saved);
            h.assertTrue(Math.abs(restored.getBbHeight()-2*variant.scale)<.001,"Variant dimensions not restored");
            var box=s.getBoundingBox();double y=s.getY()+1.4*variant.scale;
            h.assertTrue(box.clip(new Vec3(box.minX-1,y,s.getZ()),new Vec3(box.maxX+1,y,s.getZ())).isPresent(),"Visible upper body cannot be hit");
        }h.succeed();
    }
    @GameTest(template="empty",batch="crawl_upper_arrow",timeoutTicks=60) public static void actualArrowHitsLurkerUpperBody(GameTestHelper h){
        room(h);var s=spider(h,CaveRiftSpiderEntity.Variant.LURKER);s.setNoAi(true);s.setNoGravity(true);
        var arrow=new net.minecraft.world.entity.projectile.Arrow(net.minecraft.world.entity.EntityType.ARROW,h.getLevel());
        // This height is above the former 0.7 block box, even with the arrow's collision margin.
        arrow.setPos(s.getX()-3,s.getY()+1.4*s.getVariant().scale,s.getZ());arrow.setNoGravity(true);arrow.setDeltaMovement(1,0,0);
        h.getLevel().addFreshEntity(arrow);cleanup(h,arrow);
        h.runAtTickTime(8,()->{h.assertTrue(s.getHealth()<s.getMaxHealth(),"Real arrow passed through the visible upper body");h.succeed();});
    }
    @GameTest(template="empty",batch="crawl_ceiling_fire",timeoutTicks=100) public static void attachedSpitterStillFiresAndHitsRealPrey(GameTestHelper h){
        room(h);for(int x=1;x<=14;x++)for(int z=1;z<=9;z++)h.setBlock(new BlockPos(x,7,z),Blocks.STONE);
        var s=spider(h,CaveRiftSpiderEntity.Variant.SPITTER);s.moveTo(h.absoluteVec(new Vec3(3.5,7-s.getBbHeight(),5.5)));
        var prey=new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE,h.getLevel());
        prey.setNoAi(true);prey.setNoGravity(true);prey.moveTo(h.absoluteVec(new Vec3(11.5,2,5.5)));h.getLevel().addFreshEntity(prey);cleanup(h,prey);s.setTarget(prey);
        h.runAtTickTime(60,()->{h.assertTrue(s.isClimbingCeiling()&&prey.getHealth()<20,"Surface movement suppressed ceiling ranged combat: hp="+prey.getHealth());h.succeed();});
    }
    private static VaultHunter hunter(GameTestHelper h){
        var e=StrongRealmFauna.VAULT_HUNTER.get().create(h.getLevel());e.setPersistenceRequired();e.moveTo(h.absoluteVec(new Vec3(3.5,2,5.5)));h.getLevel().addFreshEntity(e);cleanup(h,e);return e;
    }
    @GameTest(template="empty",batch="hunter_sbl",timeoutTicks=80) public static void actualSmartBrainOwnsActivitiesAndExpiringMemory(GameTestHelper h){
        room(h);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(11,2,5),GameType.SURVIVAL);cleanup(h,p);
        h.runAtTickTime(8,()->{
            h.assertTrue(e.getBrain() instanceof net.tslat.smartbrainlib.api.core.SmartBrain,"Hunter uses a vanilla brain");
            h.assertTrue(e.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.FIGHT),"SmartBrain did not select fight activity");
            h.assertTrue(e.getBrain().getMemory(HunterBrain.OBSERVATION.get()).isPresent()&&e.memoryRemaining()>180,"Observation is not an expiring brain memory");
            wall(h,true);
        });
        h.runAtTickTime(25,()->{h.assertTrue(!e.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET),"Hidden identity retained in brain");h.assertTrue(e.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.IDLE),"SmartBrain did not leave fight");h.succeed();});
    }
    @GameTest(template="empty",batch="hunter_range_escape",timeoutTicks=140) public static void steppingOutOfReachDuringWindupAvoidsHit(GameTestHelper h){
        room(h);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(4,2,5),GameType.SURVIVAL);cleanup(h,p);
        e.setNoAi(true);h.runAtTickTime(65,()->e.setNoAi(false));boolean[] moved={false};
        h.onEachTick(()->{
            if(!moved[0]&&e.isPreparingStrike()){
                moved[0]=true;Vec3 at=h.absoluteVec(new Vec3(10.5,2,5.5));p.teleportTo(at.x,at.y,at.z);
                h.runAfterDelay(9,()->{h.assertTrue(p.getHealth()==20,"Delayed strike hit outside melee range");h.succeed();});
            }
        });
    }
    @GameTest(template="empty",batch="hunter_invisible",timeoutTicks=100) public static void invisibilityAndCrouchingRespectDetectionRange(GameTestHelper h){
        room(h);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(13,2,5),GameType.SURVIVAL);cleanup(h,p);
        e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        p.setInvisible(true);
        h.runAtTickTime(20,()->{h.assertTrue(e.lastKnownPosition()==null,"Invisible quiet player detected");p.setInvisible(false);p.setShiftKeyDown(true);});
        h.runAtTickTime(40,()->{h.assertTrue(e.lastKnownPosition()==null,"Crouching player outside 8 blocks detected");p.setShiftKeyDown(false);});
        h.runAtTickTime(55,()->{h.assertTrue(e.getTarget()==p,"Standing player in sight was not detected");h.succeed();});
    }
    @GameTest(template="empty",batch="hunter_two_players",timeoutTicks=100) public static void visibleAlternativeReplacesHiddenPlayerWithoutTracking(GameTestHelper h){
        room(h);var e=hunter(h);var first=TestPlayers.create(h,new BlockPos(11,2,5),GameType.SURVIVAL);
        var second=TestPlayers.create(h,new BlockPos(4,2,8),GameType.SURVIVAL);second.setInvisible(true);cleanup(h,first,second);
        h.runAtTickTime(8,()->{h.assertTrue(e.getTarget()==first,"First visible player not selected");wall(h,true);second.setInvisible(false);});
        h.runAtTickTime(30,()->{h.assertTrue(e.getTarget()==second,"Visible alternative not acquired");h.assertTrue(e.lastKnownPosition().distanceTo(second.position())<.5,"Brain retained hidden player's coordinates");h.succeed();});
    }
    @GameTest(template="empty",batch="hunter_unloaded",timeoutTicks=40) public static void pathQueryCannotLoadRemoteChunk(GameTestHelper h){
        room(h);var e=hunter(h);e.setNoAi(true);BlockPos far=e.blockPosition().offset(1024,0,1024);
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Remote fixture chunk was already loaded");
        h.assertTrue(e.getNavigation().createPath(far,0)==null,"Unloaded destination accepted");
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Hunter path query loaded a remote chunk");h.succeed();
    }
    @GameTest(template="empty",batch="hunter_strike",timeoutTicks=140) public static void strikeHasWindupThenRealDamage(GameTestHelper h){
        room(h);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(4,2,5),GameType.SURVIVAL);cleanup(h,p);
        // Let the real player's normal 60-tick login protection expire before combat.
        e.setNoAi(true);h.runAtTickTime(65,()->e.setNoAi(false));
        long[] began={-1};
        h.onEachTick(()->{
            if(began[0]<0&&e.isPreparingStrike()) {began[0]=h.getLevel().getGameTime();h.assertTrue(p.getHealth()==20,"Strike dealt damage before wind-up");}
            if(began[0]>=0){
                long elapsed=h.getLevel().getGameTime()-began[0];
                if(elapsed<5)h.assertTrue(p.getHealth()==20,"Strike hit before its readable wind-up");
                if(elapsed==9){h.assertTrue(p.getHealth()<20&&p.getHealth()>0,"Timed strike did not damage real player");h.succeed();}
            }
        });
    }
    @GameTest(template="empty",batch="hunter_strike_cancel",timeoutTicks=140) public static void hidingDuringWindupCancelsStrike(GameTestHelper h){
        room(h);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(4,2,5),GameType.SURVIVAL);cleanup(h,p);
        e.setNoAi(true);h.runAtTickTime(65,()->e.setNoAi(false));
        boolean[] moved={false};
        h.onEachTick(()->{
            if(!moved[0]&&e.isPreparingStrike()) {
                moved[0]=true;wall(h,true);p.teleportTo(h.absoluteVec(new Vec3(11.5,2,5.5)).x,h.absoluteVec(new Vec3(11.5,2,5.5)).y,h.absoluteVec(new Vec3(11.5,2,5.5)).z);
                h.runAfterDelay(15,()->{h.assertTrue(p.getHealth()==20,"Strike hit player who escaped behind cover");h.assertTrue(!e.isPreparingStrike(),"Hidden-player strike was not cancelled");h.succeed();});
            }
        });
    }
    @GameTest(template="empty",batch="hunter_dimensions",timeoutTicks=40) public static void wendigoUpperBodyHasRealHitbox(GameTestHelper h){
        room(h);var e=hunter(h);e.setNoAi(true);
        h.assertTrue(Math.abs(e.getBbHeight()-2.7)<.001&&Math.abs(e.getBbWidth()-.95)<.001&&Math.abs(e.getEyeHeight()-1.9)<.001,"Wendigo dimensions not registered");
        Vec3 at=e.position().add(0,2.5,0);
        h.assertTrue(e.getBoundingBox().clip(at.add(0,0,-2),at.add(0,0,2)).isPresent(),"Upper body cannot be targeted");h.succeed();
    }
    @GameTest(template="empty",batch="hunter_strike_stop",timeoutTicks=140) public static void disablingAiDuringWindupCancelsDamage(GameTestHelper h){
        cancelledStrike(h,false);
    }
    @GameTest(template="empty",batch="hunter_strike_death",timeoutTicks=140) public static void dyingDuringWindupCancelsDamage(GameTestHelper h){
        cancelledStrike(h,true);
    }
    private static void cancelledStrike(GameTestHelper h,boolean kill){
        room(h);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(4,2,5),GameType.SURVIVAL);cleanup(h,p);
        e.setNoAi(true);h.runAtTickTime(65,()->e.setNoAi(false));boolean[] stopped={false};
        h.onEachTick(()->{
            if(!stopped[0]&&e.isPreparingStrike()){
                stopped[0]=true;if(kill)e.kill();else e.setNoAi(true);
                h.runAfterDelay(15,()->{h.assertTrue(p.getHealth()==20,"Inactive/dead hunter completed a pending strike");h.succeed();});
            }
        });
    }
    private static void wall(GameTestHelper h,boolean enabled){for(int y=2;y<=6;y++)for(int z=0;z<=10;z++)h.setBlock(new BlockPos(7,y,z),enabled?Blocks.STONE:Blocks.AIR);}
    @GameTest(template="empty",batch="hunter_hidden",timeoutTicks=100) public static void hiddenQuietPlayerIsNotKnown(GameTestHelper h){
        room(h);wall(h,true);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(11,2,5),GameType.SURVIVAL);p.setShiftKeyDown(true);cleanup(h,p);
        h.runAtTickTime(60,()->{h.assertTrue(e.getTarget()==null&&e.lastKnownPosition()==null,"Hunter saw through wall");h.succeed();});
    }
    @GameTest(template="empty",batch="hunter_memory",timeoutTicks=290) public static void lostSightDoesNotTrackLivePlayerAndEventuallyForgets(GameTestHelper h){
        room(h);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(11,2,5),GameType.SURVIVAL);cleanup(h,p);
        Vec3[] remembered={null};
        h.runAtTickTime(8,()->{h.assertTrue(e.phase()==VaultHunter.Phase.CHASE,"Visible player not detected");wall(h,true);p.setShiftKeyDown(true);remembered[0]=e.lastKnownPosition();p.setPos(h.absoluteVec(new Vec3(13.5,2,8.5)));});
        h.runAtTickTime(30,()->{h.assertTrue(e.getTarget()==null&&e.lastKnownPosition().equals(remembered[0]),"Hidden live coordinates leaked into memory");
            h.assertTrue(e.phase()==VaultHunter.Phase.AMBUSH,"Hunter did not choose a reachable covered ambush after losing sight");});
        h.runAtTickTime(240,()->{h.assertTrue(e.lastKnownPosition()==null,"Memory did not expire");h.succeed();});
    }
    @GameTest(template="empty",batch="hunter_sound",timeoutTicks=100) public static void projectileNoiseDistractsWithoutGivingPlayerIdentity(GameTestHelper h){
        room(h);wall(h,true);var e=hunter(h);e.setNoAi(true);var p=TestPlayers.create(h,new BlockPos(11,2,5),GameType.SURVIVAL);cleanup(h,p);
        h.runAtTickTime(5,()->{
            var snowball=new Snowball(h.getLevel(),p);Vec3 impact=h.absoluteVec(new Vec3(4.5,2,8.5));snowball.setPos(impact);
            h.getLevel().gameEvent(GameEvent.PROJECTILE_LAND,impact,GameEvent.Context.of(snowball));
            h.assertTrue(e.phase()==VaultHunter.Phase.INVESTIGATE&&e.getTarget()==null,"Projectile did not provide anonymous sound cue");
            h.assertTrue(e.lastKnownPosition().distanceTo(impact)<1,"Sound used player position rather than impact");h.succeed();
        });
    }
    @GameTest(template="empty",batch="hunter_retreat",timeoutTicks=100) public static void woundedHunterRetreatsWithoutHealing(GameTestHelper h){
        room(h);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(11,2,5),GameType.SURVIVAL);cleanup(h,p);
        h.runAtTickTime(8,()->e.setHealth(8));
        h.runAtTickTime(35,()->{h.assertTrue(e.phase()==VaultHunter.Phase.RETREAT||e.phase()==VaultHunter.Phase.COOLDOWN,"Wounded hunter kept attacking");h.assertTrue(e.getTarget()==null&&e.getHealth()==8,"Retreat retained target or healed");h.succeed();});
    }
    @GameTest(template="empty",batch="hunter_creative",timeoutTicks=100) public static void creativeAndSpectatorDoNotTriggerHunter(GameTestHelper h){
        room(h);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(6,2,5),GameType.CREATIVE);cleanup(h,p);
        h.runAtTickTime(25,()->{h.assertTrue(e.lastKnownPosition()==null,"Creative player triggered hunter");p.setGameMode(GameType.SPECTATOR);});
        h.runAtTickTime(50,()->{h.assertTrue(e.lastKnownPosition()==null,"Spectator triggered hunter");h.succeed();});
    }
    @GameTest(template="empty",batch="hunter_route",timeoutTicks=230) public static void hunterUsesOpeningAroundFence(GameTestHelper h){
        room(h);
        for(int z=0;z<=8;z++)h.setBlock(new BlockPos(7,2,z),Blocks.OAK_FENCE);
        var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(11,2,5),GameType.SURVIVAL);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,1000,4));cleanup(h,p);
        boolean[] opening={false};
        h.onEachTick(()->{if(e.getZ()>h.absoluteVec(new Vec3(0,0,9)).z)opening[0]=true;});
        h.runAtTickTime(180,()->{h.assertTrue(opening[0]&&e.getX()>h.absolutePos(new BlockPos(8,0,0)).getX(),"Hunter pushed into fence instead of using opening: "+e.position());h.succeed();});
    }
    @GameTest(template="empty",batch="hunter_ally",timeoutTicks=80) public static void allyReceivesOnlyFinitePositionCue(GameTestHelper h){
        room(h);var first=hunter(h);var second=hunter(h);second.moveTo(h.absoluteVec(new Vec3(3.5,2,8.5)));second.setNoAi(true);
        var p=TestPlayers.create(h,new BlockPos(10,2,5),GameType.SURVIVAL);cleanup(h,p);
        h.runAtTickTime(12,()->{h.assertTrue(first.getTarget()==p,"First hunter failed to see player");
            h.assertTrue(second.phase()==VaultHunter.Phase.INVESTIGATE&&second.getTarget()==null&&second.memoryRemaining()<=100,"Ally was given an omniscient target or no finite cue");h.succeed();});
    }
    @GameTest(template="empty",batch="hunter_wool",timeoutTicks=80) public static void woolAndCrouchingSuppressFootstepCue(GameTestHelper h){
        room(h);wall(h,true);var e=hunter(h);e.setNoAi(true);var p=TestPlayers.create(h,new BlockPos(9,2,5),GameType.SURVIVAL);cleanup(h,p);
        h.runAtTickTime(6,()->{
            Vec3 place=h.absoluteVec(new Vec3(4.5,2,5.5));h.setBlock(new BlockPos(4,1,5),Blocks.WHITE_WOOL);
            h.getLevel().gameEvent(GameEvent.STEP,place,GameEvent.Context.of(p));h.assertTrue(e.lastKnownPosition()==null,"Wool footstep was heard");
            h.setBlock(new BlockPos(4,1,5),Blocks.STONE);p.setShiftKeyDown(true);
            h.getLevel().gameEvent(GameEvent.STEP,place,GameEvent.Context.of(p));h.assertTrue(e.lastKnownPosition()==null,"Crouching footstep was heard");
            p.setShiftKeyDown(false);h.getLevel().gameEvent(GameEvent.BLOCK_DESTROY,place,GameEvent.Context.of(p));
            h.assertTrue(e.phase()==VaultHunter.Phase.INVESTIGATE,"A real nearby loud cue was not heard");h.succeed();
        });
    }
    @GameTest(template="empty",batch="hunter_cold",timeoutTicks=80) public static void coldLoadKeepsWoundsAndDropsEphemeralCombatMemory(GameTestHelper h){
        room(h);var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(10,2,5),GameType.SURVIVAL);cleanup(h,p);
        h.runAtTickTime(8,()->{
            h.assertTrue(e.lastKnownPosition()!=null,"No active memory to save");e.setHealth(7);
            var saved=e.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            var restored=StrongRealmFauna.VAULT_HUNTER.get().create(h.getLevel());restored.load(saved);
            h.assertTrue(restored.getHealth()==7&&restored.phase()==VaultHunter.Phase.IDLE&&restored.getTarget()==null&&restored.lastKnownPosition()==null,"Cold load healed or retained stale combat identity");h.succeed();
        });
    }

    @GameTest(template="empty",batch="hunter_return",timeoutTicks=400) public static void injuredHunterReallyRetreatsThenReturnsWithoutHealing(GameTestHelper h){
        room(h);
        for(int x=-12;x<=24;x++)for(int z=-12;z<=24;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var e=hunter(h);var p=TestPlayers.create(h,new BlockPos(11,2,5),GameType.SURVIVAL);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,1000,4));cleanup(h,p);
        double[] original={0};
        h.runAtTickTime(8,()->{original[0]=e.distanceToSqr(p);e.setHealth(8);});
        h.runAtTickTime(75,()->{h.assertTrue(e.distanceToSqr(p)>original[0]+20,"Retreat state did not actually move away from threat");});
        h.runAtTickTime(330,()->{h.assertTrue(e.getHealth()==8&&e.phase()==VaultHunter.Phase.CHASE&&e.getTarget()==p,"Injured hunter did not return after finite retreat/cooldown");h.succeed();});
    }
    @GameTest(template="empty",batch="hunter_spawn_scope",timeoutTicks=40) public static void naturalHunterDoesNotEnterOverworldOrArchivedRealm(GameTestHelper h){
        h.assertTrue(!StrongRealmFauna.trySpawnHunter(h.getLevel(),h.absolutePos(new BlockPos(2,2,2))),"Hunter naturally spawned in Overworld");
        var archived=h.getLevel().getServer().getLevel(pro.erez.interstice.worldgen.IslandWorld.VANILLA_WORLD);
        h.assertTrue(!StrongRealmFauna.trySpawnHunter(archived,new BlockPos(0,60,0)),"Hunter naturally spawned in archived V5");h.succeed();
    }

    @GameTest(template="empty",batch="crawl_fair_budget",timeoutTicks=40) public static void surfacePlannerBudgetDoesNotStarveLaterEntities(GameTestHelper h){
        var budget=new SurfacePlanBudget();int[] first=new int[81],last=new int[81];java.util.Arrays.fill(first,-1);java.util.Arrays.fill(last,-100);
        for(int tick=0;tick<170;tick++){
            int accepted=0;
            for(int id=1;id<=80;id++)if(tick-last[id]>=35&&budget.tryAcquire(id,tick)){
                accepted++;last[id]=tick;if(first[id]<0)first[id]=tick;
            }
            h.assertTrue(accepted<=1,"Surface planning budget exceeded one actor per level tick");
        }
        for(int id=1;id<=80;id++)h.assertTrue(first[id]>=0&&first[id]<90,"Later entity starved behind earlier entities: "+id);
        var stale=new SurfacePlanBudget();h.assertTrue(stale.tryAcquire(1,0),"First request denied");stale.tryAcquire(2,0);
        h.assertTrue(stale.tryAcquire(3,10),"Inactive queued entity blocked future planning");h.succeed();
    }

    @GameTest(template="empty",batch="crawl_legacy_fit",timeoutTicks=60) public static void legacyCeilingSaveFitsLargerBodyWithoutHealingOrTerrainChanges(GameTestHelper h){
        room(h);for(int x=1;x<=8;x++)for(int z=1;z<=8;z++)h.setBlock(new BlockPos(x,7,z),Blocks.STONE);
        for(int y=2;y<=7;y++)for(int z=1;z<=8;z++)h.setBlock(new BlockPos(1,y,z),Blocks.STONE);
        var old=Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());old.setVariant(CaveRiftSpiderEntity.Variant.LURKER);old.setHealth(7);
        old.moveTo(h.absoluteVec(new Vec3(2.6,6.3,4.5)));var tag=old.saveWithoutId(new net.minecraft.nbt.CompoundTag());tag.remove("SpiderBodyRevision");
        var restored=Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());restored.load(tag);restored.setNoAi(true);h.getLevel().addFreshEntity(restored);cleanup(h,restored);
        h.assertTrue(!h.getLevel().noCollision(restored,restored.getBoundingBox()),"Legacy fixture did not overlap the old ceiling/wall corner");
        h.runAtTickTime(5,()->{
            h.assertTrue(h.getLevel().noCollision(restored,restored.getBoundingBox().deflate(.001)),"Legacy resized body stayed inside terrain");
            h.assertTrue(restored.getHealth()==7&&restored.isClimbingCeiling(),"Legacy resize healed/hurt the spider or lost ceiling support");
            h.assertTrue(h.getBlockState(new BlockPos(1,6,4)).is(Blocks.STONE)&&h.getBlockState(new BlockPos(3,7,4)).is(Blocks.STONE),"Resize removed terrain");h.succeed();
        });
    }

}
