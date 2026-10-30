package pro.erez.interstice.test;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.*;
import pro.erez.interstice.ecology.*;
import pro.erez.interstice.fauna.*;
@GameTestHolder("interstice_lozhnokoren") @PrefixGameTestTemplate(false)
public final class LozhnokorenGameTests {
    @GameTest(template="empty",batch="lk_debug_bounds",timeoutTicks=30) public static void debugBoxesInterpolateLocallyAndNeverSweepFromOrigin(GameTestHelper h){
        var m=root(h,false);
        for(var p:m.getParts())h.assertTrue(new Vec3(p.xOld,p.yOld,p.zOld).distanceToSqr(p.position())<.000001,"Spawn left debug history at world origin");
        var before=Arrays.stream(m.getParts()).map(Entity::position).toList();
        m.setPos(m.position().add(.25,.1,-.2));m.refreshSections();
        for(int i=0;i<m.getParts().length;i++){
            var p=m.getParts()[i];h.assertTrue(new Vec3(p.xOld,p.yOld,p.zOld).distanceToSqr(before.get(i))<.000001,"Wrong previous render position");
            for(double partial:new double[]{0,.25,.5,.75,1}){
                var rendered=new Vec3(net.minecraft.util.Mth.lerp(partial,p.xOld,p.getX()),net.minecraft.util.Mth.lerp(partial,p.yOld,p.getY()),net.minecraft.util.Mth.lerp(partial,p.zOld,p.getZ()));
                h.assertTrue(rendered.distanceToSqr(p.position())<.2,"Debug frame escaped actual hitbox neighbourhood");
            }
        }
        m.setPos(m.position().add(0,40,0));m.refreshSections();
        for(var p:m.getParts())h.assertTrue(new Vec3(p.xOld,p.yOld,p.zOld).distanceToSqr(p.position())<.000001,"Teleport left a cross-world debug sweep");
        h.succeed();
    }
    @GameTest(template="empty",batch="lk_curve_long",timeoutTicks=220) public static void sustainedTurnBendsSeveralJointsInsteadOfHingingAtNeck(GameTestHelper h){
        var m=root(h,false);
        for(int t=3;t<=62;t++){final int step=t;h.runAtTickTime(t,()->m.setYRot((step-2)*3));}
        h.runAtTickTime(61,()->{int curved=0;float sum=0;for(int i=1;i<11;i++){float a=m.bodyJointAngle(i,1);sum+=a;if(Math.abs(a)>1)curved++;h.assertTrue(Math.abs(a)<8,"Turn collapsed into one sharp hinge: "+i+"="+a);}h.assertTrue(curved>=5&&sum<-20&&sum>=-35.1,"Turn must spread along several segments: "+curved+" total="+sum);});
        h.runAtTickTime(200,()->{float bend=0;for(int i=1;i<11;i++)bend+=Math.abs(m.bodyJointAngle(i,1));h.assertTrue(bend<.1,"Body did not settle after sustained turn");h.succeed();});
    }
    @GameTest(template="empty",batch="lk_curve",timeoutTicks=160) public static void turningPropagatesDownBodyAndHitSectionsFollow(GameTestHelper h){
        var m=root(h,false);var initial=m.getParts()[10].position();
        for(int t=3;t<=12;t++){final int step=t;h.runAtTickTime(t,()->m.setYRot((step-2)*3));}
        h.runAtTickTime(15,()->{
            float bend=0;for(int i=1;i<11;i++)bend+=m.bodyJointAngle(i,1);
            h.assertTrue(bend<-5&&bend>=-35.1,"Tail must lag turn smoothly: "+bend);
            h.assertTrue(m.getParts()[10].position().distanceToSqr(initial)>.01,"Tail hitbox did not follow bend");
            for(var part:m.getParts())h.assertTrue(Double.isFinite(part.getX()+part.getZ()),"Invalid curved hitbox");
        });
        h.runAtTickTime(120,()->{float bend=0;for(int i=1;i<11;i++)bend+=Math.abs(m.bodyJointAngle(i,1));h.assertTrue(bend<.1,"Body did not settle after turn: "+bend);h.succeed();});
    }
    private static void room(GameTestHelper h){for(int x=0;x<=16;x++)for(int z=0;z<=16;z++)for(int y=1;y<=8;y++)h.setBlock(new BlockPos(x,y,z),y==1?Interstice.ABYSSAL_TURF.get():Blocks.AIR);}
    private static void clean(GameTestHelper h,Runnable r){h.testInfo.addListener(new GameTestListener(){void close(){r.run();}public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner runner){close();}public void testFailed(GameTestInfo i,GameTestRunner runner){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo r,GameTestRunner runner){close();}});}
    private static Lozhnokoren root(GameTestHelper h,boolean ai){room(h);var m=LozhnokorenFauna.TYPE.get().create(h.getLevel());m.moveTo(h.absoluteVec(new Vec3(8,2,8)));m.setYRot(0);m.setNoAi(!ai);m.setNoGravity(true);m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);h.getLevel().addFreshEntity(m);m.refreshSections();clean(h,m::discard);return m;}
    private static Zombie target(GameTestHelper h,Lozhnokoren m){var z=new Zombie(EntityType.ZOMBIE,h.getLevel());z.setNoAi(true);z.setNoGravity(true);z.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CARVED_PUMPKIN));z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);z.setHealth(1000);z.moveTo(m.position().add(0,0,5));h.getLevel().addFreshEntity(z);m.setTarget(z);clean(h,z::discard);return z;}
    @GameTest(template="empty",batch="lk_attributes",timeoutTicks=30) public static void authoredScaleHasFifteenPickableSectionsAndHeavyAttributes(GameTestHelper h){var m=root(h,false);h.assertTrue(m.getMaxHealth()==240&&m.getAttributeValue(Attributes.ATTACK_DAMAGE)==22,"Heavy creature attributes missing");var ids=new HashSet<Integer>();for(var p:m.getParts()){h.assertTrue(p.isPickable()&&p.getBbWidth()>0&&p.getBbHeight()>0&&ids.add(p.getId()),"Invalid section or duplicate network id");}h.assertTrue(ids.size()==15&&ids.contains(m.getId()+15),"Section ids must follow parent id");h.succeed();}
    @GameTest(template="empty",batch="lk_immunity",timeoutTicks=40) public static void localFloraAndBothSeaToxinsCannotHurtButCombatStillCan(GameTestHelper h){var m=root(h,false);float health=m.getHealth();h.assertTrue(!m.hurt(new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(ToxicLiquidBlock.TOXIN)),30)&&!m.hurt(h.getLevel().damageSources().drown(),10),"Sea immunity missing");
        var p=m.blockPosition();CaveEcology.STING_FROND.get().entityInside(CaveEcology.STING_FROND.get().defaultBlockState(),h.getLevel(),p,m);RealmEcology.VENOM_REED.get().defaultBlockState().entityInside(h.getLevel(),p,m);h.assertTrue(m.getHealth()==health&&m.getActiveEffects().isEmpty(),"Local plant bypassed adaptation");h.assertTrue(m.hurt(h.getLevel().damageSources().generic(),10)&&m.getHealth()<health,"Combat was accidentally made invulnerable");h.succeed();}
    @GameTest(template="empty",batch="lk_warning",timeoutTicks=140) public static void realAIWarnsBeforeDeliveringOneStrongFrontalStrike(GameTestHelper h){var m=root(h,true);var z=target(h,m);h.runAtTickTime(25,()->h.assertTrue(z.getHealth()==1000&&m.phase()==Lozhnokoren.Phase.WARNING,"Damage before warning finished"));h.runAtTickTime(70,()->{h.assertTrue(m.landedStrikes==1&&z.getHealth()<985&&z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Real heavy frontal impact missing "+m.phase()+" hits="+m.landedStrikes+" health="+z.getHealth());h.succeed();});}
    @GameTest(template="empty",batch="lk_evade",timeoutTicks=100) public static void leavingLockedStrikeArcEvadesDamage(GameTestHelper h){var m=root(h,true);var z=target(h,m);h.runAtTickTime(36,()->z.moveTo(m.position().add(0,0,-3)));h.runAtTickTime(70,()->{h.assertTrue(z.getHealth()==1000&&m.landedStrikes==0,"Rear target was hit by locked frontal strike");h.succeed();});}
    @GameTest(template="empty",batch="lk_wall",timeoutTicks=100) public static void stoneOccludesStrikeAndSurvives(GameTestHelper h){var m=root(h,true);var z=target(h,m);z.moveTo(m.position().add(0,0,5.5));for(int x=5;x<=11;x++)for(int y=2;y<=5;y++)h.setBlock(new BlockPos(x,y,12),Blocks.STONE);h.runAtTickTime(70,()->{h.assertTrue(z.getHealth()==1000&&h.getBlockState(new BlockPos(8,3,12)).is(Blocks.STONE),"Wall guard: health="+z.getHealth()+" hits="+m.landedStrikes+" wall="+h.getBlockState(new BlockPos(8,3,12)));h.succeed();});}
    private static void tree(GameTestHelper h){for(int y=2;y<=4;y++)h.setBlock(new BlockPos(8,y,14),Interstice.GLOOMCROWN_LOG.get());h.setBlock(new BlockPos(8,5,14),Interstice.GLOOMCROWN_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT,false));h.setBlock(new BlockPos(10,2,13),Blocks.OAK_PLANKS);}
    @GameTest(template="empty",batch="lk_tree",timeoutTicks=110) public static void realImpactSnapsTreeButPreservesPlanks(GameTestHelper h){var m=root(h,true);var z=target(h,m);tree(h);h.runAtTickTime(70,()->{h.assertTrue(m.brokenVegetation>0&&h.getBlockState(new BlockPos(8,2,14)).isAir()&&h.getBlockState(new BlockPos(10,2,13)).is(Blocks.OAK_PLANKS),"Tree sweep missing or damaged construction: "+m.brokenVegetation);h.succeed();});}
    @GameTest(template="empty",batch="lk_grief",timeoutTicks=110) public static void mobGriefingFalsePreventsTreeDamage(GameTestHelper h){var m=root(h,true);target(h,m);tree(h);var rule=h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);boolean old=rule.get();rule.set(false,h.getLevel().getServer());clean(h,()->rule.set(old,h.getLevel().getServer()));h.runAtTickTime(70,()->{h.assertTrue(m.brokenVegetation==0&&h.getBlockState(new BlockPos(8,2,14)).is(Interstice.GLOOMCROWN_LOG.get()),"Ignored mobGriefing");h.succeed();});}
    @GameTest(template="empty",batch="lk_protect",timeoutTicks=110) public static void protectionEventCanCancelIndividualTreeBlocks(GameTestHelper h){var m=root(h,true);target(h,m);tree(h);int[] events={0};java.util.function.Consumer<LivingDestroyBlockEvent> guard=e->{if(e.getEntity()==m){events[0]++;e.setCanceled(true);}};NeoForge.EVENT_BUS.addListener(guard);clean(h,()->NeoForge.EVENT_BUS.unregister(guard));h.runAtTickTime(70,()->{h.assertTrue(events[0]>0&&m.brokenVegetation==0&&h.getBlockState(new BlockPos(8,2,14)).is(Interstice.GLOOMCROWN_LOG.get()),"Protection event bypassed");h.succeed();});}
    @GameTest(template="empty",batch="lk_parts",timeoutTicks=30) public static void tailAndHeadHitsReachSharedHealthAndCannotProduceSideBites(GameTestHelper h){var m=root(h,false);var z=target(h,m);float old=m.getHealth();h.assertTrue(m.getParts()[14].hurt(h.getLevel().damageSources().mobAttack(z),10)&&m.getHealth()<old,"Tail not damageable");m.invulnerableTime=0;old=m.getHealth();h.assertTrue(m.getParts()[11].hurt(h.getLevel().damageSources().mobAttack(z),10)&&m.getHealth()<old,"Head not damageable");h.assertTrue(!m.doHurtTarget(z)&&LozhnokorenVegetation.sweep(m)==0,"Idle API performed an untelegraphed attack");h.succeed();}
    @GameTest(template="empty",batch="lk_space",timeoutTicks=40) public static void longBodyGuardSeesObstaclesOutsideRootBox(GameTestHelper h){var m=root(h,false);h.assertTrue(m.canOccupy(m.position(),0),"Open fixture not passable");h.setBlock(new BlockPos(8,3,4),Blocks.STONE);h.assertTrue(!m.canOccupy(m.position(),0),"Tail obstacle ignored");h.succeed();}
    @GameTest(template="empty",batch="lk_save",timeoutTicks=60) public static void nbtRestoresHealthAndHomeWithoutReplayingAnImpact(GameTestHelper h){var m=root(h,true);target(h,m);h.runAtTickTime(12,()->{m.setHealth(137);var n=new CompoundTag();m.saveWithoutId(n);var copy=LozhnokorenFauna.TYPE.get().create(h.getLevel());copy.load(n);h.assertTrue(copy.getHealth()==137&&copy.home().equals(m.home())&&copy.phase()==Lozhnokoren.Phase.CAMOUFLAGED&&copy.getTarget()==null,"Unsafe or incomplete cold state");copy.discard();h.succeed();});}
    @GameTest(template="empty",batch="lk_habitat",timeoutTicks=30) public static void ordinaryWorldCannotReceiveNaturalPopulation(GameTestHelper h){h.assertTrue(!LozhnokorenFauna.habitat(h.getLevel(),h.absolutePos(new BlockPos(8,2,8)))&&!LozhnokorenFauna.trySpawn(h.getLevel(),h.absolutePos(new BlockPos(8,2,8))),"Spawns outside actual V6");h.succeed();}

    @GameTest(template="empty",batch="lk_spores",timeoutTicks=50) public static void actualTaggedSporeCloudDoesNotApplyDebuffs(GameTestHelper h){var m=root(h,false);h.assertTrue(SporePodGas.emit(h.getLevel(),m.blockPosition(),0),"Cloud emitted");h.runAtTickTime(25,()->{h.assertTrue(m.getActiveEffects().isEmpty()&&m.getHealth()==240,"Spore cloud hurt or debuffed adapted root");for(var cloud:h.getLevel().getEntitiesOfClass(AreaEffectCloud.class,m.getBoundingBox().inflate(6)))if(cloud.getTags().contains(SporePodGas.ENTITY_TAG))cloud.discard();h.succeed();});}
    @GameTest(template="empty",batch="lk_observation",timeoutTicks=80) public static void occludedTargetDoesNotUpdateLastSeenPosition(GameTestHelper h){var m=root(h,true);var z=target(h,m);Vec3[] seen={null};h.runAtTickTime(10,()->{seen[0]=m.lastObservedPosition();h.assertTrue(seen[0]!=null,"No actual visual observation");for(int x=0;x<=16;x++)for(int y=2;y<=6;y++)h.setBlock(new BlockPos(x,y,12),Blocks.STONE);z.moveTo(m.position().add(3,0,6));});h.runAtTickTime(22,()->{h.assertTrue(m.lastObservedPosition().distanceToSqr(seen[0])<.0001,"Tracks unseen target through wall");h.succeed();});}
    @GameTest(template="empty",batch="lk_decor",timeoutTicks=30) public static void decorativeLeavesAndContainersAreNotTrees(GameTestHelper h){var m=root(h,false);var base=h.absolutePos(new BlockPos(8,2,14));h.getLevel().setBlock(base,Interstice.GLOOMCROWN_LOG.get().defaultBlockState(),2);h.getLevel().setBlock(base.above(3),Interstice.GLOOMCROWN_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);h.assertTrue(!LozhnokorenVegetation.vegetation(h.getLevel(),base)&&!LozhnokorenVegetation.vegetation(h.getLevel(),base.above(3)),"Persistent decorative canopy misidentified as natural");h.getLevel().setBlock(base,Blocks.CHEST.defaultBlockState(),2);h.assertTrue(!LozhnokorenVegetation.vegetation(h.getLevel(),base),"Container breakable");h.succeed();}
}
