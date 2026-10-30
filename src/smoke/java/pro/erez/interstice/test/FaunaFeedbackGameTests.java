package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.*;
import pro.erez.interstice.fauna.*;
import pro.erez.interstice.ecology.*;

@GameTestHolder("interstice_hunters")
@PrefixGameTestTemplate(false)
public final class FaunaFeedbackGameTests {
    private static void cleanup(GameTestHelper h,Entity e){
        h.testInfo.addListener(new GameTestListener(){
            void close(){if(e instanceof net.minecraft.server.level.ServerPlayer p)TestPlayers.remove(p);else e.discard();}
            public void testStructureLoaded(GameTestInfo i){}
            public void testPassed(GameTestInfo i,GameTestRunner r){close();}
            public void testFailed(GameTestInfo i,GameTestRunner r){close();}
            public void testAddedForRerun(GameTestInfo i,GameTestInfo r,GameTestRunner runner){close();}
        });
    }
    private static CaveRiftSpiderEntity spider(GameTestHelper h){
        var e=Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());
        e.setNoAi(true);e.setNoGravity(true);e.setPersistenceRequired();
        e.moveTo(h.absoluteVec(new Vec3(3.5,3,3.5)));h.getLevel().addFreshEntity(e);cleanup(h,e);return e;
    }
    @GameTest(template="empty",batch="feedback_flora",timeoutTicks=30)
    public static void nativeHunterIgnoresLocalPlantsButKeepsCombatAndPotionDamage(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,3,3));
        var hunter=StrongRealmFauna.VAULT_HUNTER.get().create(level);hunter.setNoAi(true);hunter.setNoGravity(true);hunter.moveTo(pos.getCenter());level.addFreshEntity(hunter);cleanup(h,hunter);
        var victim=new net.minecraft.world.entity.npc.Villager(EntityType.VILLAGER,level);victim.setNoAi(true);victim.setNoGravity(true);victim.moveTo(pos.getCenter());level.addFreshEntity(victim);cleanup(h,victim);
        for(var block:List.of(CaveEcology.STING_FROND.get(),RealmEcology.VENOM_REED.get())){
            victim.setHealth(20);victim.invulnerableTime=0;victim.getPersistentData().remove(CavePlantBlock.STING_COOLDOWN_TAG);victim.removeAllEffects();
            var state=block.defaultBlockState();state.entityInside(level,pos,hunter);state.entityInside(level,pos,victim);
            h.assertTrue(hunter.getHealth()==36&&!hunter.hasEffect(MobEffects.POISON)&&!hunter.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Native hunter harmed by local plant");
            h.assertTrue(victim.getHealth()<20&&victim.hasEffect(MobEffects.POISON)&&victim.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Plant ceased affecting ordinary prey");
        }
        h.assertTrue(hunter.hurt(level.damageSources().mobAttack(victim),4)&&hunter.getHealth()==32,"Local adaptation made hunter immune to combat");
        h.assertTrue(hunter.addEffect(new MobEffectInstance(MobEffects.POISON,100)),"Local adaptation blocked an unrelated poison effect");h.succeed();
    }
    @GameTest(template="empty",batch="feedback_spore",timeoutTicks=35)
    public static void localSporeGasSkipsHunterAndStillPoisonsOrdinaryPrey(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,3,4));
        var hunter=StrongRealmFauna.VAULT_HUNTER.get().create(level);hunter.setNoAi(true);hunter.setNoGravity(true);hunter.moveTo(pos.getCenter());level.addFreshEntity(hunter);cleanup(h,hunter);
        var victim=new net.minecraft.world.entity.npc.Villager(EntityType.VILLAGER,level);victim.setNoAi(true);victim.setNoGravity(true);victim.moveTo(pos.getCenter());level.addFreshEntity(victim);cleanup(h,victim);
        h.assertTrue(SporePodGas.emit(level,pos,0),"No test gas emitted");
        var clouds=level.getEntitiesOfClass(AreaEffectCloud.class,new net.minecraft.world.phys.AABB(pos).inflate(4));clouds.forEach(e->cleanup(h,e));
        h.runAtTickTime(12,()->{
            h.assertTrue(!hunter.hasEffect(MobEffects.POISON)&&!hunter.hasEffect(MobEffects.WEAKNESS)&&!hunter.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Hunter inhaled local spore hazard");
            h.assertTrue(victim.hasEffect(MobEffects.POISON)&&victim.hasEffect(MobEffects.WEAKNESS),"Gas ceased affecting ordinary prey");h.succeed();
        });
    }
    @GameTest(template="empty",batch="feedback_local_cap",timeoutTicks=30)
    public static void localPopulationAndRepeatedNestBurstsCannotExceedSix(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(3,3,3));
        for(int i=0;i<5;i++)spider(h);
        h.assertTrue(SpiderPopulation.hasRoom(h.getLevel(),pos),"Sixth local slot unavailable");
        for(int i=0;i<8;i++)pro.erez.interstice.block.SpiderEggSacBlock.burstEggSac(h.getLevel(),pos,null);
        var all=h.getLevel().getEntitiesOfClass(CaveRiftSpiderEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(48));all.forEach(e->cleanup(h,e));
        h.assertTrue(all.size()==6&&!SpiderPopulation.hasRoom(h.getLevel(),pos),"Repeated nest bursts exceeded local population limit");
        all.getFirst().discard();h.assertTrue(SpiderPopulation.hasRoom(h.getLevel(),pos),"Freed local slot did not reopen");h.succeed();
    }
    @GameTest(template="empty",batch="feedback_loaded_cap",timeoutTicks=30)
    public static void loadedLimitCountsImmediateAdditionsAndReleasesRemovedSpiders(GameTestHelper h){
        var all=new ArrayList<CaveRiftSpiderEntity>();for(int i=0;i<32;i++)all.add(spider(h));
        var far=h.absolutePos(new BlockPos(120,3,3));
        h.assertTrue(!SpiderPopulation.hasRoom(h.getLevel(),far),"Loaded limit missed immediate entities outside local box");
        all.getFirst().discard();h.assertTrue(SpiderPopulation.hasRoom(h.getLevel(),far),"Removed spider still consumed loaded slot");h.succeed();
    }
    @GameTest(template="empty",batch="feedback_continuous_hunt",timeoutTicks=360)
    public static void longVisiblePursuitDoesNotPauseForBehaviourTimeout(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);
        for(int x=-48;x<=48;x+=16)for(int z=-48;z<=48;z+=16)level.getChunkAt(origin.offset(x,0,z));
        for(int x=0;x<=16;x++)for(int z=0;z<=16;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<=7;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        var hunter=StrongRealmFauna.VAULT_HUNTER.get().create(level);hunter.moveTo(h.absoluteVec(new Vec3(3.5,2,5.5)));hunter.setPersistenceRequired();level.addFreshEntity(hunter);cleanup(h,hunter);
        var player=TestPlayers.create(h,new BlockPos(12,2,5),GameType.SURVIVAL);cleanup(h,player);
        Vec3[] previous={hunter.position()};double[] travelled={0};int[] age={0},stalled={0},longest={0};
        h.onEachTick(()->{
            // Stay inside the template's entity-ticking area. A long straight
            // fixture exits GameTest's ticking tickets and freezes its actor.
            double angle=age[0]*.075;
            Vec3 target=h.absoluteVec(new Vec3(8+Math.cos(angle)*6,2,8+Math.sin(angle)*6));
            player.teleportTo(level,target.x,target.y,target.z,Set.of(),0,0);
            double step=hunter.position().subtract(previous[0]).horizontalDistance();
            if(++age[0]>30){
                travelled[0]+=step;stalled[0]=step<.005?stalled[0]+1:0;longest[0]=Math.max(longest[0],stalled[0]);
                h.assertTrue(hunter.phase()==VaultHunter.Phase.CHASE&&hunter.getTarget()==player,"Visible moving prey lost during pursuit: phase="+hunter.phase()+" hunter="+hunter.position()+" player="+player.position()+" player_health="+player.getHealth()+" player_alive="+player.isAlive()+" target="+(hunter.getTarget()==player)+" perceives="+hunter.getSensing().hasLineOfSight(player)+" age="+age[0]);
            }previous[0]=hunter.position();
        });
        h.runAtTickTime(320,()->{
            h.assertTrue(travelled[0]>30,"Pursuit distance="+travelled[0]+" pause="+longest[0]+" path="+hunter.getNavigation().getPath());
            h.assertTrue(longest[0]<=2,"Continuous visible pursuit inserted "+longest[0]+" idle ticks");h.succeed();
        });
    }

    @net.minecraft.gametest.framework.GameTest(template="empty")
    public static void restingSpidersHaveNoAmbientChatter(GameTestHelper h){
        var spider=Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());int[] sounds={0};
        java.util.function.Consumer<net.neoforged.neoforge.event.PlayLevelSoundEvent> listener=event->{if(emittedBy(event,spider))sounds[0]++;};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try{spider.playSound(net.minecraft.sounds.SoundEvents.SPIDER_AMBIENT,.15F,1);h.assertTrue(sounds[0]==1,"Sound interception control failed");sounds[0]=0;for(var variant:pro.erez.interstice.entity.CaveRiftSpiderEntity.Variant.values()){spider.setVariant(variant);for(int i=0;i<30;i++)spider.playAmbientSound();}h.assertTrue(sounds[0]==0,"Calm tarantulas cannot emit periodic vanilla monster chatter");}
        finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);spider.discard();}h.succeed();
    }

    @GameTest(template="empty",batch="feedback_spider_burst_sound",timeoutTicks=30)
    public static void lurkerBurstDoesNotRestoreLoudVanillaHiss(GameTestHelper h) {
        var s=spider(h);s.setVariant(CaveRiftSpiderEntity.Variant.LURKER);
        int[] count={0};float[] peak={0};
        java.util.function.Consumer<net.neoforged.neoforge.event.PlayLevelSoundEvent> sounds=event->{
            if(emittedBy(event,s)){count[0]++;peak[0]=Math.max(peak[0],event.getNewVolume());}
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(sounds);
        try {
            s.triggerAmbushBurst();
            h.assertTrue(s.isBursting()&&count[0]>0,"Burst did not execute its real sound path");
            h.assertTrue(peak[0]<=.15001F,"Burst restored loud spider sound: "+peak[0]);
        } finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(sounds);}
        h.succeed();
    }

    private static boolean emittedBy(net.neoforged.neoforge.event.PlayLevelSoundEvent event,CaveRiftSpiderEntity s) {
        return event.getLevel()==s.level() && (event instanceof net.neoforged.neoforge.event.PlayLevelSoundEvent.AtEntity e && e.getEntity()==s
                || event instanceof net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition pos && pos.getPosition().distanceToSqr(s.position())<.000001);
    }
}
