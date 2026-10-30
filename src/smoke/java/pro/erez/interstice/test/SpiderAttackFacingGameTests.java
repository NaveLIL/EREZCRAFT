package pro.erez.interstice.test;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.*;

@GameTestHolder("interstice_spider_facing") @PrefixGameTestTemplate(false)
public final class SpiderAttackFacingGameTests {
    private static void room(GameTestHelper h){for(int x=0;x<=16;x++)for(int z=0;z<=16;z++)for(int y=1;y<=12;y++)h.setBlock(new BlockPos(x,y,z),(x==0||x==16||z==0||z==16||y==1||y==12)?Blocks.STONE:Blocks.AIR);}
    private static void cleanup(GameTestHelper h,Entity... entities){h.testInfo.addListener(new GameTestListener(){
        void close(){for(var e:entities)e.discard();}public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo r,GameTestRunner runner){close();}
    });}
    private static CaveRiftSpiderEntity actor(GameTestHelper h,CaveRiftSpiderEntity.Variant variant){var s=Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());s.setVariant(variant);s.setNoAi(true);s.setNoGravity(true);s.setPersistenceRequired();s.moveTo(h.absoluteVec(new Vec3(8,4,8)));s.yBodyRot=0;cleanup(h,s);return s;}
    private static Zombie prey(GameTestHelper h){var p=new Zombie(EntityType.ZOMBIE,h.getLevel());p.setNoAi(true);p.setNoGravity(true);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CARVED_PUMPKIN));cleanup(h,p);return p;}
    private static void atCenter(LivingEntity e,Vec3 center){e.setPos(center.x,center.y-e.getBbHeight()/2,center.z);}
    @GameTest(template="empty",batch="facing_direct_bite",timeoutTicks=30)
    public static void directBiteRejectsSideAndRearBeforeDamageAndEffects(GameTestHelper h){room(h);for(var variant:List.of(CaveRiftSpiderEntity.Variant.SKIRMISHER,CaveRiftSpiderEntity.Variant.LURKER)){
        var s=actor(h,variant);var p=prey(h);for(var offset:List.of(new Vec3(1.5,0,0),new Vec3(0,0,-1.5))){atCenter(p,s.getBoundingBox().getCenter().add(offset));h.assertTrue(!s.doHurtTarget(p)&&p.getHealth()==20&&p.getActiveEffects().isEmpty(),"Side/rear bite changed health or effects: "+variant);}
        atCenter(p,s.getBoundingBox().getCenter().add(0,0,1.5));h.assertTrue(s.doHurtTarget(p)&&p.getHealth()<20,"Frontal bite positive control failed: "+variant);
    }h.succeed();}
    @GameTest(template="empty",batch="facing_direct_spit",timeoutTicks=30)
    public static void rejectedSpitConsumesNeitherProjectileNorCooldown(GameTestHelper h){room(h);var s=actor(h,CaveRiftSpiderEntity.Variant.SPITTER);h.getLevel().addFreshEntity(s);var p=prey(h);h.getLevel().addFreshEntity(p);
        for(var offset:List.of(new Vec3(2,0,0),new Vec3(0,0,-2))){atCenter(p,s.getBoundingBox().getCenter().add(offset));s.getSensing().tick();s.performRangedAttack(p,1);h.assertTrue(s.spitCooldownRemaining()==0&&h.getLevel().getEntitiesOfClass(ToxinSpitEntity.class,s.getBoundingBox().inflate(8),e->e.getOwner()==s).isEmpty(),"Rejected spit consumed cooldown or spawned a projectile");}
        atCenter(p,s.getBoundingBox().getCenter().add(0,0,2));s.getSensing().tick();s.performRangedAttack(p,1);var emitted=h.getLevel().getEntitiesOfClass(ToxinSpitEntity.class,s.getBoundingBox().inflate(8),e->e.getOwner()==s);cleanup(h,emitted.toArray(Entity[]::new));h.assertTrue(s.spitCooldownRemaining()==38&&emitted.size()==1,"Frontal shot positive control failed");h.succeed();
    }
    @GameTest(template="empty",batch="facing_planes",timeoutTicks=30)
    public static void allSupportPlanesDistinguishFrontSidesRearAndNormalAxis(GameTestHelper h){
        var s=actor(h,CaveRiftSpiderEntity.Variant.SKIRMISHER);var p=prey(h);
        for(var face:Direction.values()){
            Vec3 up=Vec3.atLowerCornerOf(face.getOpposite().getNormal());Vec3 f=up.y==0?new Vec3(0,1,0):new Vec3(0,0,1);Vec3 side=up.cross(f);
            s.setCrawlOrientation(f,up,0);s.yBodyRot=0;
            atCenter(p,s.getBoundingBox().getCenter().add(f.scale(2)));h.assertTrue(s.attackAlignment(p)>.9999,"Wrong front on "+face);
            atCenter(p,s.getBoundingBox().getCenter().add(side.scale(2)));h.assertTrue(Math.abs(s.attackAlignment(p))<.0001,"Wrong side on "+face);
            atCenter(p,s.getBoundingBox().getCenter().subtract(f.scale(2)));h.assertTrue(s.attackAlignment(p)<-.9999,"Wrong rear on "+face);
            atCenter(p,s.getBoundingBox().getCenter().add(up.scale(2)));h.assertTrue(s.attackAlignment(p)<0,"Normal-axis target treated as frontal on "+face);
        }h.succeed();
    }
    private static void surface(GameTestHelper h,Direction face,CaveRiftSpiderEntity.Variant variant){
        room(h);var s=actor(h,variant);s.setNoAi(false);s.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);var p=prey(h);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);
        Vec3 up=Vec3.atLowerCornerOf(face.getOpposite().getNormal()),f=up.y==0?new Vec3(0,1,0):new Vec3(0,0,1);double half=s.getBbHeight()/2;
        Vec3 center=h.absoluteVec(switch(face){case DOWN->new Vec3(8,2+half,8);case UP->new Vec3(8,12-half,8);case WEST->new Vec3(1+half,6,8);case EAST->new Vec3(16-half,6,8);case NORTH->new Vec3(8,6,1+half);case SOUTH->new Vec3(8,6,16-half);});
        Vec3 preyCenter=center.subtract(f.scale(1.5)).add(up.scale(Math.max(0,(up.y==0?p.getBbWidth()/2:p.getBbHeight()/2)-half+.05)));
        atCenter(s,center);s.setCrawlOrientation(f,up,0);atCenter(p,preyCenter);
        h.assertTrue(h.getLevel().noCollision(s,s.getBoundingBox().deflate(.001))&&h.getLevel().noCollision(p,p.getBoundingBox().deflate(.001)),"Surface fixture intersects its support: "+face);h.getLevel().addFreshEntity(s);h.getLevel().addFreshEntity(p);int[] hits={0};boolean[] valid={true};
        java.util.function.Consumer<LivingDamageEvent.Post> damage=event->{if(event.getEntity()==p&&event.getSource().getEntity()==s&&event.getNewDamage()>0){hits[0]++;valid[0]&=s.attackAlignment(p)>=Math.cos(Math.toRadians(45))-.00001;}};
        NeoForge.EVENT_BUS.addListener(damage);h.testInfo.addListener(new GameTestListener(){void close(){NeoForge.EVENT_BUS.unregister(damage);}public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo r,GameTestRunner runner){close();}});
        h.onEachTick(()->{atCenter(s,center);s.setDeltaMovement(Vec3.ZERO);s.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);s.setCrawlOrientation(f,up,0);atCenter(p,preyCenter);p.setDeltaMovement(Vec3.ZERO);});
        h.runAtTickTime(120,()->{h.assertTrue(hits[0]>0&&valid[0],"Real AI failed to turn and attack on "+face+" / "+variant+" hits="+hits[0]+" valid="+valid[0]+" facing="+s.getAttackForward()+" align="+s.attackAlignment(p)+" surface="+s.getSurface());h.succeed();});
    }
    @GameTest(template="empty",batch="facing_surface_down_skirmisher",timeoutTicks=160) public static void skirmisherTurnsOnDOWN(GameTestHelper h){surface(h,Direction.DOWN,CaveRiftSpiderEntity.Variant.SKIRMISHER);}
    @GameTest(template="empty",batch="facing_surface_down_spitter",timeoutTicks=160) public static void spitterTurnsOnDOWN(GameTestHelper h){surface(h,Direction.DOWN,CaveRiftSpiderEntity.Variant.SPITTER);}
    @GameTest(template="empty",batch="facing_surface_up_skirmisher",timeoutTicks=160) public static void skirmisherTurnsOnUP(GameTestHelper h){surface(h,Direction.UP,CaveRiftSpiderEntity.Variant.SKIRMISHER);}
    @GameTest(template="empty",batch="facing_surface_up_spitter",timeoutTicks=160) public static void spitterTurnsOnUP(GameTestHelper h){surface(h,Direction.UP,CaveRiftSpiderEntity.Variant.SPITTER);}
    @GameTest(template="empty",batch="facing_surface_west_skirmisher",timeoutTicks=160) public static void skirmisherTurnsOnWEST(GameTestHelper h){surface(h,Direction.WEST,CaveRiftSpiderEntity.Variant.SKIRMISHER);}
    @GameTest(template="empty",batch="facing_surface_west_spitter",timeoutTicks=160) public static void spitterTurnsOnWEST(GameTestHelper h){surface(h,Direction.WEST,CaveRiftSpiderEntity.Variant.SPITTER);}
    @GameTest(template="empty",batch="facing_surface_east_skirmisher",timeoutTicks=160) public static void skirmisherTurnsOnEAST(GameTestHelper h){surface(h,Direction.EAST,CaveRiftSpiderEntity.Variant.SKIRMISHER);}
    @GameTest(template="empty",batch="facing_surface_east_spitter",timeoutTicks=160) public static void spitterTurnsOnEAST(GameTestHelper h){surface(h,Direction.EAST,CaveRiftSpiderEntity.Variant.SPITTER);}
    @GameTest(template="empty",batch="facing_surface_north_skirmisher",timeoutTicks=160) public static void skirmisherTurnsOnNORTH(GameTestHelper h){surface(h,Direction.NORTH,CaveRiftSpiderEntity.Variant.SKIRMISHER);}
    @GameTest(template="empty",batch="facing_surface_north_spitter",timeoutTicks=160) public static void spitterTurnsOnNORTH(GameTestHelper h){surface(h,Direction.NORTH,CaveRiftSpiderEntity.Variant.SPITTER);}
    @GameTest(template="empty",batch="facing_surface_south_skirmisher",timeoutTicks=160) public static void skirmisherTurnsOnSOUTH(GameTestHelper h){surface(h,Direction.SOUTH,CaveRiftSpiderEntity.Variant.SKIRMISHER);}
    @GameTest(template="empty",batch="facing_surface_south_spitter",timeoutTicks=160) public static void spitterTurnsOnSOUTH(GameTestHelper h){surface(h,Direction.SOUTH,CaveRiftSpiderEntity.Variant.SPITTER);}
}
