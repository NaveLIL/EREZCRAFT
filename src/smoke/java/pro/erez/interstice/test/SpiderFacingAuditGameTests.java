package pro.erez.interstice.test;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;

/** Diagnostic observation, deliberately separate from the required regression suite. */
@GameTestHolder("interstice_spider_facing")
@PrefixGameTestTemplate(false)
public final class SpiderFacingAuditGameTests {
    @GameTest(template="empty",batch="facing_skirmisher",timeoutTicks=160)
    public static void skirmisherFrontSideAndRearAudit(GameTestHelper h){audit(h,CaveRiftSpiderEntity.Variant.SKIRMISHER);}
    @GameTest(template="empty",batch="facing_lurker",timeoutTicks=160)
    public static void lurkerFrontSideAndRearAudit(GameTestHelper h){audit(h,CaveRiftSpiderEntity.Variant.LURKER);}
    @GameTest(template="empty",batch="facing_spitter",timeoutTicks=160)
    public static void spitterRangedFrontSideAndRearAudit(GameTestHelper h){audit(h,CaveRiftSpiderEntity.Variant.SPITTER);}
    private static void audit(GameTestHelper h,CaveRiftSpiderEntity.Variant variant){
        for(int x=0;x<=14;x++)for(int z=0;z<=14;z++)for(int y=1;y<=9;y++)h.setBlock(new BlockPos(x,y,z),(y==1?Blocks.STONE:Blocks.AIR));
        var s=Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());s.setVariant(variant);s.setPersistenceRequired();s.setNoGravity(true);s.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var prey=new Zombie(EntityType.ZOMBIE,h.getLevel());prey.setNoAi(true);prey.setNoGravity(true);prey.setPersistenceRequired();prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);prey.setHealth(1000);prey.setItemSlot(EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
        Vec3 origin=h.absoluteVec(new Vec3(7.5,2,7.5));s.moveTo(origin);prey.moveTo(origin.add(0,0,1.5));h.getLevel().addFreshEntity(s);h.getLevel().addFreshEntity(prey);
        int[] tick={0};var impacts=new JsonArray();int[] counts=new int[3];
        java.util.function.Consumer<LivingDamageEvent.Post> damage=event->{
            if(event.getEntity()!=prey||event.getSource().getEntity()!=s||event.getNewDamage()<=0)return;
            int section=Math.min(2,tick[0]/40);counts[section]++;
            Vec3 delta=prey.position().subtract(s.position()).normalize();double dot=s.attackAlignment(prey);
            var row=new JsonObject();row.addProperty("tick",tick[0]);row.addProperty("position",new String[]{"FRONT","SIDE","REAR"}[section]);row.addProperty("body_yaw",s.yBodyRot);row.addProperty("head_yaw",s.yHeadRot);row.addProperty("forward_dot_target",dot);row.addProperty("damage",event.getNewDamage());impacts.add(row);
        };
        NeoForge.EVENT_BUS.addListener(damage);
        h.testInfo.addListener(new GameTestListener(){
            private void close(){NeoForge.EVENT_BUS.unregister(damage);s.discard();prey.discard();}
            public void testStructureLoaded(GameTestInfo i){}
            public void testPassed(GameTestInfo i,GameTestRunner r){close();}
            public void testFailed(GameTestInfo i,GameTestRunner r){close();}
            public void testAddedForRerun(GameTestInfo i,GameTestInfo r,GameTestRunner runner){close();}
        });
        h.onEachTick(()->{
            int section=Math.min(2,tick[0]/40);s.setPos(origin);s.setDeltaMovement(Vec3.ZERO);
            Vec3 offset=switch(section){case 0->new Vec3(0,0,1.5);case 1->new Vec3(1.5,0,0);default->new Vec3(0,0,-1.5);};
            prey.setPos(origin.add(offset));prey.setDeltaMovement(Vec3.ZERO);prey.setHealth(1000);tick[0]++;
        });
        h.runAtTickTime(125,()->{
            var report=new JsonObject();report.addProperty("variant",variant.name());report.addProperty("combat_kind",variant==CaveRiftSpiderEntity.Variant.SPITTER?"RANGED":"MELEE");report.addProperty("live_ai",!s.isNoAi());report.addProperty("target_injected",false);report.addProperty("attacks_called_by_fixture",false);report.addProperty("position_pinned_for_diagnostic",true);report.addProperty("orientation_pinned",false);report.addProperty("front_hits",counts[0]);report.addProperty("side_hits",counts[1]);report.addProperty("rear_hits",counts[2]);
            report.addProperty("front_only_contract_passed",impacts.asList().stream().allMatch(x->x.getAsJsonObject().get("forward_dot_target").getAsDouble()>=Math.cos(Math.toRadians(45))-.00001));report.add("impacts",impacts);
            try {
                Path root=Path.of(System.getProperty("interstice.facingAuditOutput")).toAbsolutePath().normalize();h.assertTrue(root.toString().replace('\\','/').contains("/.verification/"),"Owned audit output only");Files.createDirectories(root);Files.writeString(root.resolve(variant.name().toLowerCase(Locale.ROOT)+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
            h.assertTrue(counts[0]>0&&counts[1]>0&&counts[2]>0,"Real AI failed to turn and attack one of the three target positions");
            h.assertTrue(report.get("front_only_contract_passed").getAsBoolean(),"Real AI damage escaped the forward cone");h.succeed();
        });
    }
}
