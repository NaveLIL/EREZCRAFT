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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.block.SpiderEggSacBlock;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;
import pro.erez.interstice.entity.ToxinSpitEntity;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.cave.CaveFeatures;
import java.util.*;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class CaveSpiderGameTests {
    private CaveSpiderGameTests() {}

    private static void cleanup(GameTestHelper h, Entity... entities) {
        h.testInfo.addListener(new GameTestListener() {
            private void close(){for(var entity:entities){if(entity instanceof ServerPlayer player)TestPlayers.remove(player);else entity.discard();}}
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
            @Override public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
            @Override public void testAddedForRerun(GameTestInfo original,GameTestInfo rerun,GameTestRunner runner){close();}
        });
    }
    private static void corridor(GameTestHelper h) {
        for(int x=0;x<=25;x++)for(int z=1;z<=9;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            for(int y=2;y<=7;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
    }
    private static CaveRiftSpiderEntity spider(GameTestHelper h,CaveRiftSpiderEntity.Variant variant,BlockPos local) {
        var mob=new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(),h.getLevel());
        mob.setVariant(variant);mob.setPersistenceRequired();mob.moveTo(h.absolutePos(local).getBottomCenter());return mob;
    }

    @GameTest(template="empty",batch="spider_nbt_health",timeoutTicks=100)
    public static void damagedHealthSurvivesFullEntityNbtForEveryVariant(GameTestHelper h) {
        for(var variant:CaveRiftSpiderEntity.Variant.values()){
            var original=spider(h,variant,new BlockPos(3,2,5));original.setHealth(7);
            var saved=original.saveWithoutId(new CompoundTag());
            var restored=new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(),h.getLevel());restored.load(saved);
            h.assertTrue(restored.getVariant()==variant&&restored.getHealth()==7,"Saved wounded "+variant+" healed or changed subtype");
            h.assertTrue(restored.getMaxHealth()==variant.maxHealth&&restored.getAttributeValue(Attributes.ATTACK_DAMAGE)==variant.attackDamage,"Saved subtype attributes changed");
        }
        h.succeed();
    }

    @GameTest(template="empty",batch="spider_nbt_burst",timeoutTicks=100)
    public static void savedBurstRestoresSpeedAndBoundsOldOrMalformedTimers(GameTestHelper h) {
        var original=spider(h,CaveRiftSpiderEntity.Variant.LURKER,new BlockPos(3,2,5));original.setHealth(11);original.triggerAmbushBurst();
        var saved=original.saveWithoutId(new CompoundTag());saved.putInt("BurstTicks",67);
        var restored=new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(),h.getLevel());restored.load(saved);
        h.assertTrue(restored.getHealth()==11&&restored.isBursting()&&Math.abs(restored.getAttributeValue(Attributes.MOVEMENT_SPEED)-.54)<.00001,"Saved finite wounded burst lost HP/state/speed");
        h.assertTrue(restored.saveWithoutId(new CompoundTag()).getInt("BurstTicks")==67,"Remaining burst timer restarted");
        saved.remove("SpiderBursting");restored.load(saved);h.assertTrue(restored.isBursting(),"Older positive BurstTicks save lost its remaining burst");
        saved.putInt("BurstTicks",Integer.MAX_VALUE);restored.load(saved);h.assertTrue(restored.saveWithoutId(new CompoundTag()).getInt("BurstTicks")==160,"Malformed timer became unbounded");
        saved.putInt("BurstTicks",-1);restored.load(saved);h.assertTrue(!restored.isBursting()&&restored.saveWithoutId(new CompoundTag()).getInt("BurstTicks")==0,"Negative timer activated a burst");
        saved.putInt("BurstTicks",80);saved.putBoolean("SpiderBursting",false);restored.load(saved);h.assertTrue(!restored.isBursting(),"Explicit saved inactive burst was ignored");
        saved.putByte("SpiderVariant",(byte)CaveRiftSpiderEntity.Variant.SKIRMISHER.id);saved.putBoolean("SpiderBursting",true);restored.load(saved);h.assertTrue(!restored.isBursting(),"Another subtype acquired a Lurker burst");h.succeed();
    }

    @GameTest(template="empty",batch="spider_native_burst_end",timeoutTicks=100)
    public static void restoredBurstExpiresThroughActualServerEntityTicks(GameTestHelper h) {
        corridor(h);var original=spider(h,CaveRiftSpiderEntity.Variant.LURKER,new BlockPos(3,2,5));original.setHealth(11);original.triggerAmbushBurst();
        var saved=original.saveWithoutId(new CompoundTag());saved.putInt("BurstTicks",37);
        var restored=new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(),h.getLevel());restored.load(saved);restored.setNoAi(true);
        cleanup(h,restored);h.assertTrue(h.getLevel().addFreshEntity(restored),"Restored native entity could not be added");
        h.runAtTickTime(5,()->h.assertTrue(restored.isBursting(),"Remaining burst ended before its real timer"));
        h.runAtTickTime(42,()->{h.assertTrue(!restored.isBursting()&&Math.abs(restored.getAttributeValue(Attributes.MOVEMENT_SPEED)-.20)<.00001,"Actual server ticks failed to end restored finite burst");h.assertTrue(restored.getHealth()==11,"Native saved burst healed its wounds");h.succeed();});
    }

    @GameTest(template="empty",batch="spider_native_spit_siblings",timeoutTicks=100)
    public static void actualMovingSpitPassesSiblingAndHitsPreyBehindIt(GameTestHelper h) {
        corridor(h);var owner=spider(h,CaveRiftSpiderEntity.Variant.SPITTER,new BlockPos(4,2,5));var brother=spider(h,CaveRiftSpiderEntity.Variant.SKIRMISHER,new BlockPos(7,2,5));owner.setNoAi(true);brother.setNoAi(true);
        var prey=TestPlayers.create(h,new BlockPos(9,2,5),GameType.SURVIVAL);var spit=new ToxinSpitEntity(h.getLevel(),owner,1,.09,0);
        cleanup(h,owner,brother,prey,spit);h.getLevel().addFreshEntity(owner);h.getLevel().addFreshEntity(brother);
        // Public impact callbacks must reject a sibling too; the moving test then exercises native collision filtering.
        spit.onHitEntity(new net.minecraft.world.phys.EntityHitResult(brother));h.assertTrue(!spit.isRemoved()&&brother.getHealth()==16&&!brother.hasEffect(MobEffects.POISON),"Direct sibling impact caused friendly fire");
        h.getLevel().addFreshEntity(spit);
        h.runAtTickTime(10,()->{h.assertTrue(brother.getHealth()==16&&!brother.hasEffect(MobEffects.POISON)&&!brother.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Native spit collision injured its sibling");h.assertTrue(prey.hasEffect(MobEffects.POISON)&&prey.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&spit.isRemoved(),"Native moving projectile failed to pass sibling and impact real server prey");h.succeed();});
    }

    @GameTest(template="empty",batch="spider_ranged_geometry",timeoutTicks=100)
    public static void serverRangedAttackRequiresActualRangeAndUnobstructedSight(GameTestHelper h) {
        corridor(h);var owner=spider(h,CaveRiftSpiderEntity.Variant.SPITTER,new BlockPos(3,2,5));owner.setNoAi(true);h.getLevel().addFreshEntity(owner);var prey=TestPlayers.create(h,new BlockPos(23,2,5),GameType.SURVIVAL);cleanup(h,owner,prey);
        var bounds=new AABB(Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(0,1,0))),Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(26,8,10))));
        owner.performRangedAttack(prey,1);h.assertTrue(h.getLevel().getEntitiesOfClass(ToxinSpitEntity.class,bounds,e->e.getOwner()==owner).isEmpty(),"Out-of-range target received a server shot");
        var near=h.absolutePos(new BlockPos(10,2,5));prey.teleportTo(h.getLevel(),near.getX()+.5,near.getY(),near.getZ()+.5,Set.of(),0,0);prey.hasChangedDimension();
        for(int z=1;z<=9;z++)for(int y=2;y<=7;y++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);owner.getSensing().tick();owner.performRangedAttack(prey,1);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ToxinSpitEntity.class,bounds,e->e.getOwner()==owner).isEmpty(),"Opaque wall failed to block the ranged shot");
        for(int z=1;z<=9;z++)for(int y=2;y<=7;y++)h.setBlock(new BlockPos(7,y,z),Blocks.AIR);owner.getSensing().tick();owner.performRangedAttack(prey,1);
        var actual=h.getLevel().getEntitiesOfClass(ToxinSpitEntity.class,bounds,e->e.getOwner()==owner);h.assertTrue(actual.size()==1,"In-range visible prey failed to receive exactly one real server projectile");cleanup(h,actual.toArray(Entity[]::new));h.succeed();
    }

    @GameTest(template="empty",batch="spider_native_goal_exclusion",timeoutTicks=100)
    public static void actualRangedGoalExcludesMeleeAndFiresNeitherOutOfRangeNorThroughWall(GameTestHelper h) {
        corridor(h);var owner=spider(h,CaveRiftSpiderEntity.Variant.SPITTER,new BlockPos(3,2,5));var prey=TestPlayers.create(h,new BlockPos(23,2,5),GameType.SURVIVAL);
        h.getLevel().addFreshEntity(owner);owner.setTarget(prey);cleanup(h,owner,prey);var bounds=new AABB(Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(0,1,-20))),Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(40,10,30))));
        h.runAtTickTime(4,()->{
            try{
                var field=Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);var selector=(GoalSelector)field.get(owner);
                var running=selector.getAvailableGoals().stream().filter(goal->goal.isRunning()).map(goal->goal.getGoal().getClass().getSimpleName()).toList();
                h.assertTrue(running.contains("SpitterRangedAttackGoal")&&!running.contains("MeleeAttackGoal"),"Actual AI failed ranged/moving-melee exclusivity: "+running);
            }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("Cannot inspect actual native goal selection",unavailable);}
            h.assertTrue(owner.distanceToSqr(prey)>196&&h.getLevel().getEntitiesOfClass(ToxinSpitEntity.class,bounds,e->e.getOwner()==owner).isEmpty(),"Native AI shot outside its14-block range");
            prey.teleportTo(h.getLevel(),owner.getX()+8,owner.getY(),owner.getZ(),Set.of(),0,0);prey.hasChangedDimension();
            int wallX=owner.blockPosition().getX()+4;for(int z=owner.blockPosition().getZ()-16;z<=owner.blockPosition().getZ()+16;z++)for(int y=owner.blockPosition().getY();y<=owner.blockPosition().getY()+8;y++)h.getLevel().setBlock(new BlockPos(wallX,y,z),Blocks.STONE.defaultBlockState(),3);
        });
        h.runAtTickTime(16,()->{h.assertTrue(h.getLevel().getEntitiesOfClass(ToxinSpitEntity.class,bounds,e->e.getOwner()==owner).isEmpty(),"Native ranged goal emitted a projectile through an opaque wall");h.succeed();});
    }

    private static void sealedRoom(ServerLevel level,BlockPos floor) {
        level.getChunk(floor.getX()>>4,floor.getZ()>>4);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=6;y++)
            level.setBlock(floor.offset(x,y,z),(y==0||y==6||Math.abs(x)==3||Math.abs(z)==3?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
    }
    @GameTest(template="empty",batch="spider_native_spawn_revision",timeoutTicks=300)
    public static void naturalRulesRejectRealDarkLegacyRoomAndKeepCurrentV6Eligible(GameTestHelper h) {
        var legacy=h.getLevel().getServer().getLevel(IslandWorld.VANILLA_WORLD);var current=h.getLevel().getServer().getLevel(IslandWorld.TENSION_WORLD);
        h.assertTrue(legacy!=null&&current!=null,"Actual legacyV5 and V6 dimensions are required");
        var floor=new BlockPos(4096+8,80,4096+8);sealedRoom(legacy,floor);sealedRoom(current,floor);
        h.runAtTickTime(60,()->{
            var feet=floor.above();h.assertTrue(legacy.getBrightness(net.minecraft.world.level.LightLayer.SKY,feet)==0&&legacy.getRawBrightness(feet,0)<=7&&current.getBrightness(net.minecraft.world.level.LightLayer.SKY,feet)==0&&current.getRawBrightness(feet,0)<=7,"Prepared loaded rooms did not attain actual dark cave conditions");
            h.assertTrue(current.getDifficulty()!=net.minecraft.world.Difficulty.PEACEFUL,"Spawn-positive fixture requires actual non-Peaceful difficulty");
            boolean eligible=false;for(int trial=0;trial<64;trial++){
                h.assertTrue(!CaveRiftSpiderEntity.checkCaveSpiderSpawnRules(Interstice.CAVE_RIFT_SPIDER.get(),legacy,net.minecraft.world.entity.MobSpawnType.NATURAL,feet,legacy.getRandom()),"A real dark archivedV5 cave became naturally eligible for the new spider");
                eligible|=CaveRiftSpiderEntity.checkCaveSpiderSpawnRules(Interstice.CAVE_RIFT_SPIDER.get(),current,net.minecraft.world.entity.MobSpawnType.NATURAL,feet,current.getRandom());
            }h.assertTrue(eligible,"Fixed64 actual predicate attempts in the prepared loaded darkV6 room never became eligible");System.out.println("SPIDER_SPAWN_REVISION_GUARD scope=actual_FULL_prepared_dark_rooms_rules_only_not_autonomous_spawn");h.succeed();
        });
    }

    private static ProtoChunk preparedCave(GameTestHelper h,ChunkPos position) {
        var chunk=new ProtoChunk(position,UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        var biome=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(pro.erez.interstice.worldgen.RealmBiomes.STONE_VAULTS);
        chunk.fillBiomesFromNoise((x,y,z,s)->biome,net.minecraft.world.level.biome.Climate.empty());
        chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
        for(int x=position.getMinBlockX();x<=position.getMaxBlockX();x++)for(int z=position.getMinBlockZ();z<=position.getMaxBlockZ();z++){
            chunk.setBlockState(new BlockPos(x,49,z),Interstice.RIFTSTONE.get().defaultBlockState(),false);
            for(int y=55;y<=60;y++)chunk.setBlockState(new BlockPos(x,y,z),Interstice.RIFTSTONE.get().defaultBlockState(),false);
        }return chunk;
    }
    private static int nests(ProtoChunk chunk) {int result=0;for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)for(int y=50;y<55;y++){var state=chunk.getBlockState(new BlockPos(x,y,z));if(state.is(Interstice.RIFT_COBWEB.get())||state.is(Interstice.SPIDER_EGG_SAC.get()))result++;}return result;}
    @GameTest(template="empty",batch="spider_legacy_decorations",timeoutTicks=200)
    public static void preparedNativeDecorationPassAddsNestsOnlyForV6(GameTestHelper h) {
        int current=0;for(long seed:new long[]{0,1,-1,20261006L,76198123L,4294967297L})for(var position:List.of(new ChunkPos(0,0),new ChunkPos(1,-1)))for(int revision=2;revision<=6;revision++){
            var chunk=preparedCave(h,position);CaveFeatures.decorate(GeometryProfile.TALL,chunk,seed,h.getLevel(),revision);int count=nests(chunk);
            if(revision<6)h.assertTrue(count==0,"New spider decoration changed archived revision "+revision+" seed="+seed+" chunk="+position);else current+=count;
        }h.assertTrue(current>0,"Fixed six-seed prepared cave sample never exercised current nest placement");System.out.println("SPIDER_DECORATION_REVISION_GUARD v6_nest_cells="+current+" scope=prepared_ProtoChunk_decorator_not_natural_FULL");h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spiderVariantsAndAttributesInitializedProperly(GameTestHelper h) {
        var spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        
        spider.setVariant(CaveRiftSpiderEntity.Variant.SKIRMISHER);
        h.assertTrue(spider.getMaxHealth() == 16.0, "Skirmisher health mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.36, "Skirmisher speed mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.ATTACK_DAMAGE) == 5.0, "Skirmisher damage mismatch");

        spider.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        h.assertTrue(spider.getMaxHealth() == 30.0, "Lurker health mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.20, "Lurker base speed mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8.0, "Lurker damage mismatch");

        spider.setVariant(CaveRiftSpiderEntity.Variant.SPITTER);
        h.assertTrue(spider.getMaxHealth() == 14.0, "Spitter health mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.28, "Spitter speed mismatch");
        h.assertTrue(spider.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3.0, "Spitter damage mismatch");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lurkerCalmAtDistanceAndBurstsInProximity(GameTestHelper h) {
        var lurker = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        lurker.setPos(h.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        lurker.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        h.getLevel().addFreshEntity(lurker);

        h.assertTrue(!lurker.isBursting(), "Lurker should spawn calm");

        // Distance > 3.5: remains calm
        lurker.tick();
        h.assertTrue(!lurker.isBursting(), "Lurker should not burst without close prey or provoke");

        // Trigger burst ambush
        lurker.triggerAmbushBurst();
        h.assertTrue(lurker.isBursting(), "Lurker must enter burst state");
        h.assertTrue(lurker.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.50, "Lurker sprint speed should be boosted above 0.50");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void packCoordinatesAndAlertsComrades(GameTestHelper h) {
        var s1 = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        s1.setPos(h.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        s1.setVariant(CaveRiftSpiderEntity.Variant.SKIRMISHER);
        h.getLevel().addFreshEntity(s1);

        var s2 = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        s2.setPos(h.absolutePos(new BlockPos(5, 2, 5)).getBottomCenter());
        s2.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        h.getLevel().addFreshEntity(s2);

        var player = TestPlayers.create(h, new BlockPos(1, 2, 1), GameType.SURVIVAL);

        s1.alertPack(player);
        h.assertTrue(s2.getTarget() == player, "Comrade spider should acquire alerted pack target");
        h.assertTrue(s2.isBursting(), "Lurker comrade should trigger burst sprint on pack alert");
        TestPlayers.remove(player);

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void toxinSpitInflictsCorrosiveEffects(GameTestHelper h) {
        var spitter = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        spitter.setPos(h.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        spitter.setVariant(CaveRiftSpiderEntity.Variant.SPITTER);
        h.getLevel().addFreshEntity(spitter);

        var spit = new ToxinSpitEntity(h.getLevel(), spitter, 0.5, 0.0, 0.5);
        h.getLevel().addFreshEntity(spit);

        var player = TestPlayers.create(h, new BlockPos(3, 2, 3), GameType.SURVIVAL);

        // Hit entity directly
        spit.onHitEntity(new net.minecraft.world.phys.EntityHitResult(player));

        h.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Toxin spit should apply slowness");
        h.assertTrue(player.hasEffect(MobEffects.POISON), "Toxin spit should apply poison");
        TestPlayers.remove(player);

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void riftCobwebAllowsSpidersAndSlowsOthers(GameTestHelper h) {
        var spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        spider.setDeltaMovement(1.0, 0.0, 1.0);

        var web = Interstice.RIFT_COBWEB.get();
        web.entityInside(web.defaultBlockState(), h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)), spider);

        // Delta movement of native spider must NOT be reduced to stuck (0.2, 0.05, 0.2)
        h.assertTrue(spider.getDeltaMovement().x > 0.5, "Cave spider should move freely through rift web");

        var victim = TestPlayers.create(h, new BlockPos(2, 2, 2), GameType.SURVIVAL);
        victim.setDeltaMovement(1.0, 0.0, 1.0);
        web.entityInside(web.defaultBlockState(), h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)), victim);
        h.assertTrue(victim.getDeltaMovement().x <= 0.25, "Non-spider entity must be slowed by rift web");
        TestPlayers.remove(victim);

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spiderEggSacBurstReleasesDefenders(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        h.setBlock(new BlockPos(3, 2, 3), Interstice.SPIDER_EGG_SAC.get());

        var player = TestPlayers.create(h, new BlockPos(4, 2, 4), GameType.SURVIVAL);

        SpiderEggSacBlock.burstEggSac(h.getLevel(), pos, player);

        // Check spawned defenders
        var defenders = h.getLevel().getEntitiesOfClass(CaveRiftSpiderEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(4.0));
        h.assertTrue(!defenders.isEmpty(), "Egg sac burst should spawn defenders");
        h.assertTrue(defenders.get(0).getTarget() == player, "Spawned defenders should target the egg sac destroyer");
        TestPlayers.remove(player);

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void caveSpawnRulesStrictlyEnforced(GameTestHelper h) {
        var groundPos = new BlockPos(2, 2, 2);
        h.setBlock(groundPos, net.minecraft.world.level.block.Blocks.STONE);
        var spawnPos = h.absolutePos(groundPos.above());

        // 1. Without solid roof, spawn must be rejected
        h.assertTrue(!CaveRiftSpiderEntity.checkCaveSpiderSpawnRules(
                Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL, spawnPos, h.getLevel().getRandom()),
                "Spawn without cave roof must be rejected");

        // 2. Under tree leaves, spawn must be rejected
        h.setBlock(groundPos.above(3), net.minecraft.world.level.block.Blocks.OAK_LEAVES);
        h.assertTrue(!CaveRiftSpiderEntity.checkCaveSpiderSpawnRules(
                Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL, spawnPos, h.getLevel().getRandom()),
                "Spawn under leaves must be rejected");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void spiderAttackBypassesArmorAndDealsTrueDamage(GameTestHelper h) {
        var spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        spider.setVariant(CaveRiftSpiderEntity.Variant.SKIRMISHER); // 5.0 damage
        h.getLevel().addFreshEntity(spider);

        // Player 1: completely unarmored
        var nakedPlayer = TestPlayers.create(h, new BlockPos(2, 2, 2), GameType.SURVIVAL);
        nakedPlayer.setHealth(20.0F);

        // Player 2: heavily armored with Netherite Armor equivalent (20 armor points)
        var armoredPlayer = TestPlayers.create(h, new BlockPos(4, 2, 4), GameType.SURVIVAL);
        armoredPlayer.setHealth(20.0F);
        var armorAttr = armoredPlayer.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) armorAttr.setBaseValue(20.0);

        // Let the 60-tick native ServerPlayer spawn invulnerability guard expire
        h.runAtTickTime(70, () -> {
            try {
                nakedPlayer.invulnerableTime = 0;
                armoredPlayer.invulnerableTime = 0;

                // Attack both players
                spider.doHurtTarget(nakedPlayer);
                spider.doHurtTarget(armoredPlayer);

                float damageToNaked = 20.0F - nakedPlayer.getHealth();
                float damageToArmored = 20.0F - armoredPlayer.getHealth();

                // Normally, 20 armor reduces 5.0 damage by 80% (damage taken = 1.0)
                // With armor-piercing damage, both take full 5.0 damage!
                h.assertTrue(damageToNaked == 5.0F, "Unarmored player must take full 5.0 true damage, but took: " + damageToNaked);
                h.assertTrue(damageToArmored == 5.0F, "Armored player must take full 5.0 true damage (ignoring armor), but took: " + damageToArmored);
            } finally {
                TestPlayers.remove(nakedPlayer);
                TestPlayers.remove(armoredPlayer);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lurkerPerformsAmbushLungeWhenTriggered(GameTestHelper h) {
        var groundPos = new BlockPos(2, 1, 2);
        h.setBlock(groundPos, net.minecraft.world.level.block.Blocks.STONE);
        var lurker = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        lurker.setPos(h.absolutePos(groundPos.above()).getBottomCenter());
        lurker.setOnGround(true);
        lurker.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        h.getLevel().addFreshEntity(lurker);

        var player = TestPlayers.create(h, new BlockPos(5, 2, 2), GameType.SURVIVAL);
        lurker.setTarget(player);

        // Trigger ambush burst
        lurker.triggerAmbushBurst();

        // Check that lunge impulse was applied
        Vec3 vel = lurker.getDeltaMovement();
        h.assertTrue(vel.horizontalDistance() > 0.4, "Lurker must lunge forward with velocity on ambush trigger");
        h.assertTrue(vel.y > 0.1, "Lurker must have upward leap velocity on ambush trigger");

        TestPlayers.remove(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spidersHuntPreyAndRefuseFriendlyFire(GameTestHelper h) {
        var spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        var brother = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        var bat = new Bat(EntityType.BAT, h.getLevel());
        var zombie = new Zombie(EntityType.ZOMBIE, h.getLevel());
        var golem = new IronGolem(EntityType.IRON_GOLEM, h.getLevel());

        // Validate prey targeting
        h.assertTrue(spider.canTargetEntity(bat), "Cave rift spiders must target bats");
        h.assertTrue(spider.canTargetEntity(zombie), "Cave rift spiders must target zombies");
        h.assertTrue(spider.canTargetEntity(golem), "Cave rift spiders must target iron golems");

        // Validate complete immunity to friendly fire
        h.assertTrue(!spider.canTargetEntity(brother), "Cave rift spiders must NEVER target fellow cave rift spiders");
        h.assertTrue(!spider.canAttack(brother), "canAttack must return false for fellow spiders");

        spider.setTarget(brother);
        h.assertTrue(spider.getTarget() == null, "setTarget must reject targeting fellow spiders");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ceilingClimbingAndAdhesionHoldsEntity(GameTestHelper h) {
        // Build a stone ceiling at y=4
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
        }

        var spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        // Position directly under ceiling (y=3.25, bb height is 0.7, top is 3.95, stone ceiling is at y=4.0)
        spider.setPos(h.absolutePos(new BlockPos(2, 0, 2)).getX() + 0.5,
                      h.absolutePos(new BlockPos(2, 0, 2)).getY() + 3.25,
                      h.absolutePos(new BlockPos(2, 0, 2)).getZ() + 0.5);
        h.getLevel().addFreshEntity(spider);

        h.assertTrue(spider.hasCeilingAbove(), "Spider must detect stone ceiling above");

        // Tick spider to engage ceiling clinging
        spider.tick();
        h.assertTrue(spider.isClimbingCeiling(), "Spider must enter isClimbingCeiling state");

        // Test travel physics counteracts gravity on ceiling
        spider.travel(Vec3.ZERO);
        h.assertTrue(spider.getDeltaMovement().y >= 0.0, "Delta movement Y must stay sticky (+0.02) to hold to ceiling without dropping");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lurkerPerformsCeilingDropAmbushOnPreyBeneath(GameTestHelper h) {
        // Ceiling at y=5
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                h.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
            }
        }

        var lurker = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), h.getLevel());
        lurker.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        lurker.setPos(h.absolutePos(new BlockPos(2, 0, 2)).getX() + 0.5,
                      h.absolutePos(new BlockPos(2, 0, 2)).getY() + 4.25,
                      h.absolutePos(new BlockPos(2, 0, 2)).getZ() + 0.5);
        h.getLevel().addFreshEntity(lurker);
        lurker.setClimbingCeiling(true);

        // Prey directly underneath at y=1
        var player = TestPlayers.create(h, new BlockPos(2, 1, 2), GameType.SURVIVAL);
        lurker.setTarget(player);

        // Tick lurker to trigger ceiling drop ambush
        lurker.tick();

        h.assertTrue(!lurker.isClimbingCeiling(), "Lurker must detach from ceiling to ambush");
        h.assertTrue(lurker.isBursting(), "Lurker must enter burst sprint during ambush drop");
        h.assertTrue(lurker.getDeltaMovement().y < -0.5, "Lurker must plunge downwards towards prey with high downward velocity");

        TestPlayers.remove(player);
        h.succeed();
    }
}
