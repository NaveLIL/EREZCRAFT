package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ToxicLiquidBlock;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;
import pro.erez.interstice.ecology.CaveEcology;
import pro.erez.interstice.fauna.UpperSeaCharge;

@GameTestHolder("interstice_strong_fauna")
@PrefixGameTestTemplate(false)
public final class FaunaFeedbackGameTests {
    @GameTest(template="empty")
    public static void nativeSpiderIgnoresLocalPlantAndLowerSeaButKeepsCombatAndUpperSeaDamage(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));
        var spider=new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(),level);spider.setVariant(CaveRiftSpiderEntity.Variant.SKIRMISHER);spider.setNoAi(true);spider.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);level.addFreshEntity(spider);
        var plant=CaveEcology.STING_FROND.get();float health=spider.getHealth();
        plant.entityInside(plant.defaultBlockState(),level,pos,spider);
        h.assertTrue(spider.getHealth()==health&&!spider.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Local flora harms native spider");
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlock(pos.offset(x,0,z),Interstice.HEAVY_BLOCK.get().defaultBlockState(),3);
        ToxicLiquidBlock.onEntityTick(new EntityTickEvent.Post(spider));
        h.assertTrue(spider.getHealth()==health,"Lower sea harms native spider");
        var zombie=new Zombie(EntityType.ZOMBIE,level);zombie.setNoAi(true);zombie.moveTo(spider.position());level.addFreshEntity(zombie);
        ToxicLiquidBlock.onEntityTick(new EntityTickEvent.Post(zombie));h.assertTrue(zombie.getHealth()<20,"Lower-sea immunity leaked to other mobs");
        // Do not trigger the separate upper/lower mixing explosion in this contact test.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlock(pos.offset(x,0,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlock(pos.offset(x,0,z),Interstice.LIGHT_BLOCK.get().defaultBlockState(),3);
        ToxicLiquidBlock.onEntityTick(new EntityTickEvent.Post(spider));h.assertTrue(spider.getHealth()<health,"Upper-sea damage was globally disabled");
        var combatSpider=new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(),level);combatSpider.setVariant(CaveRiftSpiderEntity.Variant.SKIRMISHER);combatSpider.moveTo(pos.getX()+5,pos.getY()+2,pos.getZ());level.addFreshEntity(combatSpider);
        float before=combatSpider.getHealth();boolean hit=combatSpider.hurt(level.damageSources().generic(),3);
        System.out.println("SPIDER_ENVIRONMENT_CONTROL lower_immune=true upper_health="+spider.getHealth()+" combat_before="+before+" combat_after="+combatSpider.getHealth()+" hit="+hit);
        h.assertTrue(hit&&combatSpider.getHealth()<before,"Fresh healthy native spider ignores ordinary combat");combatSpider.discard();
        spider.discard();zombie.discard();h.succeed();
    }
    @GameTest(template="empty")
    public static void fastGhastChargeAndOriginalSoundEventsAreRegistered(GameTestHelper h){
        h.assertTrue(Math.abs(UpperSeaCharge.SPEED-5*1.1)<1e-9,"Requested projectile speed is not fivefold");
        for(var sound:java.util.List.of(pro.erez.interstice.sound.ModSounds.GHAST_AMBIENT,pro.erez.interstice.sound.ModSounds.GHAST_HURT,pro.erez.interstice.sound.ModSounds.GHAST_DEATH,pro.erez.interstice.sound.ModSounds.GHAST_WARN,pro.erez.interstice.sound.ModSounds.GHAST_SHOOT,pro.erez.interstice.sound.ModSounds.GHAST_SPLASH))
            h.assertTrue(sound.get().getLocation().getNamespace().equals(Interstice.ID),"Ghast still uses a vanilla sound event");
        h.succeed();
    }
}
