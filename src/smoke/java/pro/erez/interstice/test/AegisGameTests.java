package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.*;
import pro.erez.interstice.gear.*;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.agriculture.*;
import pro.erez.interstice.minerals.MineralEcology;

@GameTestHolder("interstice_progression")
@PrefixGameTestTemplate(false)
public final class AegisGameTests {
    private static ResourceLocation id(String p){return ResourceLocation.fromNamespaceAndPath("interstice",p);}
    private static String kind(EquipmentSlot s){return switch(s){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    private static void equip(LivingEntity e,int charge){for(var s:RealmArmor.SLOTS){var item=new ItemStack(AegisArmor.item("aegis_"+kind(s)));AegisPower.charge(item,charge);e.setItemSlot(s,item);}}
    private static void tick(net.minecraft.server.level.ServerPlayer p){for(int i=0;i<3;i++)p.connection.tick();}
    @GameTest(template="empty") public static void maximumTierUsesNativeArmorLimitsAndDurability(GameTestHelper h){
        var p=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        try{
            equip(p,100);tick(p);
            h.assertTrue(p.getArmorValue()==30&&p.getAttributeValue(Attributes.ARMOR_TOUGHNESS)==20,"Magen must reach the native 30/20 armor and toughness limits");
            h.assertTrue(p.getMaxHealth()==28&&Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)-.8)<.001,"Powered chassis health and stability");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).getMaxDamage()==1200,"Native chest durability must be 16×75");
            h.assertTrue(p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)<.401,"Heavy shock absorption");
            h.assertTrue(RealmArmor.pieces(p,true)==0&&AegisArmor.pieces(p)==4,"Endgame armor cannot masquerade as a full lower-tier suit");
            for(var s:RealmArmor.SLOTS){var stack=p.getItemBySlot(s);h.assertTrue(stack.getItem().isValidRepairItem(stack,new ItemStack(AegisArmor.ALLOY.get())),"Repair must use the new composite");}
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void lavaFireDrowningToxinAndDischargeUseActualDamageEvents(GameTestHelper h){
        var p=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);p.setNoGravity(true);
        try{
            equip(p,100);tick(p);p.setHealth(p.getMaxHealth());
            p.setInvulnerable(false);for(int i=0;i<80;i++){p.tick();p.connection.tick();}p.invulnerableTime=0;
            var toxin=new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ToxicLiquidBlock.TOXIN));
            for(var source:List.of(p.damageSources().lava(),p.damageSources().onFire(),p.damageSources().drown(),toxin)){
                p.invulnerableTime=0;p.hurt(source,10);h.assertTrue(p.getHealth()==28,"Powered seals must stop environment damage: "+source);
            }
            int wear=p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();h.assertTrue(wear==1,"Multiple concurrent hazards must not multiply seal wear");
            pro.erez.interstice.toxin.OverworldToxinHazard.applyVaporHazard(p,h.getLevel());
            h.assertTrue(!p.hasEffect(net.minecraft.world.effect.MobEffects.POISON),"Sealed suit must also filter native toxin vapor");
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,200));
            h.assertTrue(!p.hasEffect(net.minecraft.world.effect.MobEffects.POISON),"Powered filters must stop ordinary poison too");
            for(var s:RealmArmor.SLOTS)AegisPower.charge(p.getItemBySlot(s),0);
            p.invulnerableTime=0;p.hurt(toxin,10);
            h.assertTrue(p.getHealth()<28,"Empty foreign-world batteries must lose active toxin immunity");
            h.assertTrue(p.getArmorValue()==30,"Passive composite defense must survive discharge");
            equip(p,100);p.setItemSlot(EquipmentSlot.FEET,ItemStack.EMPTY);p.invulnerableTime=0;p.setHealth(28);p.hurt(p.damageSources().drown(),2);
            h.assertTrue(p.getHealth()<28,"Removing one part must open the seal");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void armorNeverCancelsVoidOrKillDamage(GameTestHelper h){
        var p=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        try{equip(p,AegisPower.CAPACITY);tick(p);p.hurt(p.damageSources().fellOutOfWorld(),1000);h.assertTrue(!p.isAlive(),"Native invulnerability-bypassing damage remains fatal");}
        finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200) public static void realRealmFieldProtectsTideAndChargesWithoutConsumption(GameTestHelper h){
        var level=Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.CURRENT_WORLD));int x=5120,z=5120;
        var profile=pro.erez.interstice.geometry.GeometryProfiles.get(level);int y=(int)Math.floor(SeaSurface.cellMinimum(profile,x,z,true))-9;
        for(int xx=x-1;xx<=x+1;xx++)for(int zz=z-1;zz<=z+1;zz++)for(int yy=y;yy<y+10;yy++)level.setBlock(new BlockPos(xx,yy,zz),Blocks.AIR.defaultBlockState(),3);
        var cow=new Cow(EntityType.COW,level);cow.setPos(x+.5,y,z+.5);equip(cow,0);
        h.assertTrue(AegisPower.sealed(cow),"Realm field must energize an empty but complete suit");
        h.assertTrue(AegisPower.powered(cow,EquipmentSlot.HEAD),"Optics cannot go empty in Interstice");
        BuoyancyController.applyEntityBuoyancy(cow,1);
        h.assertTrue(!cow.getAttribute(Attributes.GRAVITY).hasModifier(BuoyancyController.BUOYANCY_ID),"Complete Magen must block actual tide capture");
        h.assertTrue(AegisPower.exposedSurge(cow,new TideState(TidePhase.SURGE,100,1000,1)),"Open capture zone must enable fast charging");
        cow.setItemSlot(EquipmentSlot.FEET,ItemStack.EMPTY);BuoyancyController.applyEntityBuoyancy(cow,1);
        h.assertTrue(cow.getAttributeValue(Attributes.GRAVITY)<0,"Removing a part restores the ordinary tidal pull");
        cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void chargeIsSavedBoundedAndDrainsOnlyWhileWornAbroad(GameTestHelper h){
        var p=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        try{
            equip(p,10);var helmet=p.getItemBySlot(EquipmentSlot.HEAD);helmet.setDamageValue(31);helmet.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Owner Magen"));
            p.getPersistentData().remove("interstice_aegis_power_second");AegisPower.tick(p);
            h.assertTrue(AegisPower.charge(helmet)==9,"A normal foreign-world second must cost one unit");
            AegisPower.tick(p);h.assertTrue(AegisPower.charge(helmet)==9,"Repeated hook in the same second cannot multiply drain");
            var restored=ItemStack.parse(h.getLevel().registryAccess(),helmet.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(AegisPower.charge(restored)==9&&restored.getDamageValue()==31&&restored.getHoverName().getString().equals("Owner Magen"),"Charge metadata must preserve unrelated item components");
            AegisPower.charge(restored,Integer.MAX_VALUE);h.assertTrue(AegisPower.charge(restored)==7200,"Capacity must be bounded");
            AegisPower.charge(restored,-5);h.assertTrue(AegisPower.charge(restored)==0,"Negative charge is invalid");
            h.assertTrue(AegisPower.nextCharge(7000,true,true)==7120&&AegisPower.nextCharge(7199,true,true)==7200,"Surge recharge is fast and capped");
            h.assertTrue(AegisPower.nextCharge(100,true,false)==104&&AegisPower.nextCharge(1,false,false)==0,"Calm field and discharge transitions");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void realRetortPaysRareAlloyInputs(GameTestHelper h){
        var local=new BlockPos(4,2,4);h.setBlock(local,RealmAgriculture.RETORT.get());var machine=(RetortBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(local));
        machine.setItem(0,new ItemStack(RealmArmaments.RIFTSTEEL.get(),8));machine.setItem(1,new ItemStack(Items.NETHERITE_INGOT));machine.setItem(2,new ItemStack(Items.ECHO_SHARD,2));machine.setItem(3,new ItemStack(RealmAgriculture.PURE_LINING.get(),4));
        machine.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get(),3));h.assertTrue(machine.selectRecipe(id("retort_abyssal_alloy")),"Composite process must load into actual retort");
        for(int i=0;i<2400;i++)machine.process();
        h.assertTrue(machine.getItem(7).is(AegisArmor.ALLOY.get())&&machine.getItem(7).getCount()==4,"Rare alloy output must be exactly four");
        for(int i=0;i<4;i++)h.assertTrue(machine.getItem(i).isEmpty(),"All rare reagents must be consumed");h.succeed();
    }
    @GameTest(template="empty") public static void smithingPreservesEnchantmentsAndConsumesControllerAndPlate(GameTestHelper h){
        var p=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        try{
            var table=h.absolutePos(new BlockPos(6,1,5));h.getLevel().setBlock(table,Blocks.SMITHING_TABLE.defaultBlockState(),3);
            var menu=new SmithingMenu(122,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),table));p.containerMenu=menu;
            var base=new ItemStack(RealmArmaments.item("riftsteel_chestplate"));base.setDamageValue(25);base.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Personal cuirass"));
            var protection=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);base.enchant(protection,4);
            menu.getSlot(0).setByPlayer(new ItemStack(AegisArmor.CONTROLLER.get(),2));menu.getSlot(1).setByPlayer(base);menu.getSlot(2).setByPlayer(new ItemStack(AegisArmor.PLATE.get(),2));
            var result=menu.getSlot(3).getItem();h.assertTrue(result.is(AegisArmor.item("aegis_chestplate")),"Actual smithing result must be Magen");
            h.assertTrue(result.getDamageValue()==25&&EnchantmentHelper.getItemEnchantmentLevel(protection,result)==4&&result.getHoverName().getString().equals("Personal cuirass"),"Upgrade must retain wear, enchantments and name");
            h.assertTrue(result.getMaxDamage()==1200,"Upgrade must use the new native durability");
            menu.quickMoveStack(p,3);
            h.assertTrue(menu.getSlot(0).getItem().getCount()==1&&menu.getSlot(1).getItem().isEmpty()&&menu.getSlot(2).getItem().getCount()==1,"Upgrade consumes one controller, one base and one plate");
        }finally{p.closeContainer();TestPlayers.remove(p);}h.succeed();
    }
}
