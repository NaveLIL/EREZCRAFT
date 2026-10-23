package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.*;

@GameTestHolder("interstice_hunters")
@PrefixGameTestTemplate(false)
public final class SpiderBrainGameTests {
    @GameTest(template="empty",batch="spider_surface_frame",timeoutTicks=20)
    public static void surfaceHeadingStaysTangentAndIgnoresGroundLookYaw(GameTestHelper h){
        for(var face:new net.minecraft.core.Direction[]{net.minecraft.core.Direction.UP,net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.WEST}){
            var normal=Vec3.atLowerCornerOf(face.getOpposite().getNormal());
            for(Vec3 forward:new Vec3[]{new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(1,0,0),new Vec3(0,0,1)}){
                if(Math.abs(normal.dot(forward))>.5)continue;
                var rotation=SpiderSurfaceFrame.rotation(face,face,1);
                for(float look:new float[]{0,45,123,270}){
                    float yaw=SpiderSurfaceFrame.bodyYaw(rotation,forward,look);
                    var actual=rotation.transform(new org.joml.Vector3f(-(float)Math.sin(Math.toRadians(yaw)),0,(float)Math.cos(Math.toRadians(yaw))),new org.joml.Vector3f());
                    h.assertTrue(actual.dot(forward.toVector3f())>.9999&&Math.abs(actual.dot(normal.toVector3f()))<.0001,"Wall frame tilts heading with ground look: "+face+" / "+look);
                }
            }
        }h.succeed();
    }
    private static void cleanup(GameTestHelper h,Entity e){h.testInfo.addListener(new GameTestListener(){
        void close(){if(e instanceof net.minecraft.server.level.ServerPlayer p)TestPlayers.remove(p);else e.discard();}
        public void testStructureLoaded(GameTestInfo i){}
        public void testPassed(GameTestInfo i,GameTestRunner r){close();}
        public void testFailed(GameTestInfo i,GameTestRunner r){close();}
        public void testAddedForRerun(GameTestInfo i,GameTestInfo r,GameTestRunner runner){close();}
    });}
    private static void room(GameTestHelper h){
        var center=h.absolutePos(new BlockPos(8,2,5));
        for(int x=(center.getX()-48)>>4;x<=(center.getX()+48)>>4;x++)for(int z=(center.getZ()-48)>>4;z<=(center.getZ()+48)>>4;z++)h.getLevel().getChunk(x,z);
        for(int x=0;x<=16;x++)for(int z=0;z<=10;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            for(int y=2;y<=12;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
    }
    private static CaveRiftSpiderEntity spider(GameTestHelper h,CaveRiftSpiderEntity.Variant variant){
        var s=Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());s.setVariant(variant);s.setPersistenceRequired();
        s.moveTo(h.absoluteVec(new Vec3(3.5,2,5.5)));h.getLevel().addFreshEntity(s);cleanup(h,s);return s;
    }
    private static Zombie prey(GameTestHelper h){
        var p=new Zombie(EntityType.ZOMBIE,h.getLevel());p.setNoAi(true);p.setNoGravity(true);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);
        p.moveTo(h.absoluteVec(new Vec3(11.5,2,5.5)));h.getLevel().addFreshEntity(p);cleanup(h,p);return p;
    }
    @GameTest(template="empty",batch="spider_sbl_sight",timeoutTicks=260)
    public static void smartBrainErasesHiddenIdentityAndExpiresSnapshot(GameTestHelper h){
        room(h);var s=spider(h,CaveRiftSpiderEntity.Variant.SKIRMISHER);s.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var p=prey(h);Vec3[] seen={null};
        h.runAtTickTime(10,()->{
            h.assertTrue(s.getBrain() instanceof net.tslat.smartbrainlib.api.core.SmartBrain&&s.getTarget()==p,"SmartBrain did not acquire prey");seen[0]=SpiderBrain.observed(s);
            for(int z=0;z<=10;z++)for(int y=2;y<=12;y++)h.setBlock(new BlockPos(8,y,z),Blocks.STONE);
            p.setPos(h.absoluteVec(new Vec3(13.5,2,7.5)));
        });
        h.runAtTickTime(22,()->{h.assertTrue(s.getTarget()==null&&SpiderBrain.observed(s).equals(seen[0]),"Hidden prey identity/coordinates leaked into search");});
        h.runAtTickTime(230,()->{h.assertTrue(s.getTarget()==null&&SpiderBrain.observed(s)==null&&s.combatTactic().equals("WAIT"),"Spider retained infinite combat memory");h.succeed();});
    }
    @GameTest(template="empty",batch="spider_sbl_reposition",timeoutTicks=130)
    public static void spitterShootsThenActuallyMovesToAnotherFiringPosition(GameTestHelper h){
        room(h);var s=spider(h,CaveRiftSpiderEntity.Variant.SPITTER);var p=prey(h);
        boolean[] fired={false},reposition={false};double[] lateral={0};
        h.onEachTick(()->{fired[0]|=s.spitCooldownRemaining()>0;reposition[0]|=s.combatTactic().equals("REPOSITION");lateral[0]=Math.max(lateral[0],Math.abs(s.getZ()-h.absoluteVec(new Vec3(0,0,5.5)).z));});
        h.runAtTickTime(90,()->{h.assertTrue(fired[0]&&reposition[0]&&lateral[0]>1,"No real post-shot relocation: fired="+fired[0]+" action="+reposition[0]+" lateral="+lateral[0]);h.succeed();});
    }
    @GameTest(template="empty",batch="spider_sbl_retreat",timeoutTicks=100)
    public static void spitterCreatesRealDistanceFromClosePrey(GameTestHelper h){
        room(h);var s=spider(h,CaveRiftSpiderEntity.Variant.SPITTER);s.setPos(h.absoluteVec(new Vec3(8.5,2,5.5)));var p=prey(h);
        double before=s.distanceTo(p);boolean[] retreat={false};h.onEachTick(()->retreat[0]|=s.combatTactic().equals("RETREAT"));
        h.runAtTickTime(80,()->{h.assertTrue(retreat[0]&&s.distanceTo(p)>before+1.5,"Spitter did not actually retreat: "+s.position()+" tactic="+s.combatTactic()+" target="+(s.getTarget()==p)+" walk="+s.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET));h.succeed();});
    }
    @GameTest(template="empty",batch="spider_sbl_flank",timeoutTicks=160)
    public static void skirmisherFlanksAndClosesInsteadOfCirclingForever(GameTestHelper h){
        room(h);var s=spider(h,CaveRiftSpiderEntity.Variant.SKIRMISHER);var p=prey(h);double before=s.distanceTo(p);
        boolean[] flank={false};double[] side={0};h.onEachTick(()->{flank[0]|=s.combatTactic().equals("FLANK");side[0]=Math.max(side[0],Math.abs(s.getZ()-p.getZ()));});
        h.runAtTickTime(125,()->{h.assertTrue(flank[0]&&side[0]>1&&s.distanceTo(p)<before-2,"Flank did not move sideways and close: "+s.position()+" distance="+s.distanceTo(p));h.succeed();});
    }
    @GameTest(template="empty",batch="spider_sbl_ambush",timeoutTicks=300)
    public static void lurkerChoosesCeilingAndDropsAtApproachingPlayer(GameTestHelper h){
        room(h);for(int y=2;y<=8;y++)for(int z=1;z<=9;z++)h.setBlock(new BlockPos(1,y,z),Blocks.STONE);
        for(int x=1;x<=14;x++)for(int z=1;z<=9;z++)h.setBlock(new BlockPos(x,8,z),Blocks.STONE);
        var s=spider(h,CaveRiftSpiderEntity.Variant.LURKER);var p=TestPlayers.create(h,new BlockPos(13,2,5),GameType.SURVIVAL);cleanup(h,p);
        boolean[] dropped={false};
        h.runAtTickTime(210,()->{
            h.assertTrue(s.isClimbingCeiling()&&!s.isBursting()&&s.getTarget()==null,"Lurker did not wait on ceiling without detecting a distant player: "+s.position());
            p.teleportTo(s.getX()+1,h.absolutePos(new BlockPos(0,2,0)).getY(),s.getZ());
        });
        h.onEachTick(()->{if(s.tickCount>210&&!s.isClimbingCeiling()&&s.isBursting())dropped[0]=true;});
        h.runAtTickTime(250,()->{h.assertTrue(dropped[0]&&s.getY()<h.absolutePos(new BlockPos(0,5,0)).getY(),"Lurker failed to drop from its chosen ambush");h.succeed();});
    }
    @GameTest(template="empty",batch="azure_full_body",timeoutTicks=60)
    public static void azureRouteCannotSqueezeLargeCombatBoxThroughOneBlockGate(GameTestHelper h){
        room(h);
        for(int x=0;x<=16;x++)for(int z=0;z<=10;z++)for(int y=2;y<=12;y++)
            if(x==0||x==16||z==0||z==10||y==12||x==8&&z!=5)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var s=spider(h,CaveRiftSpiderEntity.Variant.LURKER);s.setNoAi(true);
        Vec3 goal=h.absoluteVec(new Vec3(12.5,12-s.getBbHeight()/2,5.5));
        h.assertTrue(new AzureSpiderNavigation(s).find(goal).isEmpty(),"Azure slim route allowed the real 1.7-block body through a one-block gate");
        for(int y=2;y<=11;y++)for(int z=4;z<=6;z++)h.setBlock(new BlockPos(8,y,z),Blocks.AIR);
        h.assertTrue(!new AzureSpiderNavigation(s).find(goal).isEmpty(),"Widened control gate did not produce a real Azure route");h.succeed();
    }
    @GameTest(template="empty",batch="azure_unloaded",timeoutTicks=30)
    public static void azureRejectsUnloadedDestinationWithoutLoadingIt(GameTestHelper h){
        room(h);var s=spider(h,CaveRiftSpiderEntity.Variant.SPITTER);s.setNoAi(true);
        BlockPos far=s.blockPosition().offset(1024,0,1024);
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Unloaded fixture already present");
        h.assertTrue(!s.crawlTo(Vec3.atCenterOf(far)),"Accepted unloaded Azure destination");
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Azure request loaded a new chunk");h.succeed();
    }
    @GameTest(template="empty",batch="spider_burst_rest",timeoutTicks=290)
    public static void continuousSightDoesNotRenewLurkerBurstWithoutRest(GameTestHelper h){
        room(h);var s=spider(h,CaveRiftSpiderEntity.Variant.LURKER);var p=prey(h);
        s.triggerAmbushBurst();s.setTarget(p);
        // Isolate the timing policy from pursuit/knockback; locomotion is tested separately.
        h.onEachTick(()->{s.setPos(h.absoluteVec(new Vec3(3.5,2,5.5)));s.setDeltaMovement(Vec3.ZERO);s.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);});
        h.runAtTickTime(175,()->{h.assertTrue(s.getTarget()==p&&!s.isBursting()&&!SpiderBrain.canBurst(s),"Continuous sight renewed an infinite sprint instead of the 80-tick rest");});
        h.runAtTickTime(260,()->{h.assertTrue(s.getTarget()==p&&s.isBursting(),"Lurker never resumed combat after finite rest");h.succeed();});
    }
}
