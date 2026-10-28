package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.tags.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.gear.*;

@GameTestHolder("interstice_progression")
@PrefixGameTestTemplate(false)
public final class RealmArmorGameTests {
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("interstice",name);}
    private static String kind(EquipmentSlot slot){return switch(slot){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    private static void equip(LivingEntity entity,boolean steel){for(var slot:RealmArmor.SLOTS)entity.setItemSlot(slot,new ItemStack(RealmArmaments.item((steel?"riftsteel_":"riftsilver_")+kind(slot))));}
    private static void tick(net.minecraft.server.level.ServerPlayer player){for(int i=0;i<3;i++)player.connection.tick();}
    private static Holder<Enchantment> enchant(GameTestHelper h,String name){return h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT,id(name)));}
    @GameTest(template="empty") public static void armorAttributesExceedVanillaAndRemovalRestoresThem(GameTestHelper h){
        var player=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);player.setNoGravity(true);
        try{
            equip(player,true);tick(player);
            h.assertTrue(player.getArmorValue()==24,"Heavy alloy must provide 24 armor");
            h.assertTrue(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)==16,"Heavy alloy toughness must exceed netherite's 12");
            h.assertTrue(player.getMaxHealth()==24,"Cuirass must add exactly two hearts");
            h.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)-.5)<.001,"Heavy knockback resistance");
            h.assertTrue(Math.abs(player.getAttributeValue(Attributes.STEP_HEIGHT)-1.1)<.001,"Boots must climb a full block");
            h.assertTrue(Math.abs(player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)-.6)<.001,"Heavy fall multiplier");
            equip(player,false);tick(player);
            h.assertTrue(player.getArmorValue()==20&&player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)==12,"Silver must reach netherite defense");
            h.assertTrue(player.getMaxHealth()==20,"Removing steel chest must remove bonus hearts");
            h.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED)-.11)<.001,"Silver legs give ten percent speed");
            h.assertTrue(Math.abs(player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)-1.15)<.001,"Full silver set must improve actual mining attribute");
            player.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);tick(player);
            h.assertTrue(player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)==1,"Broken set cannot retain mining bonus");
            for(var slot:RealmArmor.SLOTS)player.setItemSlot(slot,ItemStack.EMPTY);tick(player);
            h.assertTrue(player.getArmorValue()==0&&player.getMaxHealth()==20&&Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED)-.1)<.001,"Removing armor restores ordinary values");
        }finally{TestPlayers.remove(player);}h.succeed();
    }
    @GameTest(template="empty") public static void actualCombatOutperformsUnenchantedNetherite(GameTestHelper h){
        var heavy=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);var vanilla=TestPlayers.create(h,new BlockPos(8,2,3),GameType.SURVIVAL);
        try{
            equip(heavy,true);int i=0;for(var slot:RealmArmor.SLOTS)vanilla.setItemSlot(slot,new ItemStack(List.of(Items.NETHERITE_HELMET,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_LEGGINGS,Items.NETHERITE_BOOTS).get(i++)));
            tick(heavy);tick(vanilla);heavy.setHealth(heavy.getMaxHealth());vanilla.setHealth(20);
            for(var p:List.of(heavy,vanilla)){p.setNoGravity(true);p.setInvulnerable(false);for(int n=0;n<80;n++){p.tick();p.connection.tick();}p.invulnerableTime=0;}
            var source=h.getLevel().damageSources().mobAttack(h.spawn(EntityType.ZOMBIE,new BlockPos(4,2,5)));
            float before=heavy.getHealth();heavy.hurt(source,20);float steelDamage=before-heavy.getHealth();
            vanilla.hurt(source,20);float netheriteDamage=20-vanilla.getHealth();
            h.assertTrue(steelDamage>0&&steelDamage<netheriteDamage,"Actual 20-point melee hit must favor steel: "+steelDamage+" / "+netheriteDamage);
            h.assertTrue(heavy.getItemBySlot(EquipmentSlot.CHEST).getDamageValue()>0,"Combat must spend ordinary durability");
        }finally{TestPlayers.remove(heavy);TestPlayers.remove(vanilla);}h.succeed();
    }
    @GameTest(template="empty") public static void venomToxinWardAndLiningUseRealDamagePath(GameTestHelper h){
        var victim=h.spawn(EntityType.COW,new BlockPos(3,2,3));victim.setNoAi(true);victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);equip(victim,true);
        var registry=h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for(String type:List.of("spider_bite","spider_spit","toxin")){
            var source=new DamageSource(registry.getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,id(type))));
            victim.invulnerableTime=0;victim.setHealth(100);victim.hurt(source,10);
            h.assertTrue(Math.abs(100-victim.getHealth()-6.8)<.01,"Native hazard must retain 32% lining reduction: "+type+" / "+(100-victim.getHealth()));
        }
        victim.getItemBySlot(EquipmentSlot.CHEST).enchant(enchant(h,"rift_ward"),3);
        victim.invulnerableTime=0;victim.setHealth(100);
        victim.hurt(new DamageSource(registry.getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,id("toxin")))),10);
        h.assertTrue(Math.abs(100-victim.getHealth()-5)<.01,"Ward III plus four steel pieces must reduce toxin by 50%");
        for(var slot:RealmArmor.SLOTS)h.assertTrue(victim.getItemBySlot(slot).getDamageValue()>=4,"Every protected contact must wear the lining");
        victim.discard();h.succeed();
    }
    @GameTest(template="empty") public static void mixedPiecesAndVoidDoNotProduceAnImmortalSet(GameTestHelper h){
        var player=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        try{
            equip(player,true);player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(RealmArmaments.item("riftsilver_helmet")));tick(player);
            h.assertTrue(RealmArmor.pieces(player,true)==3&&RealmArmor.pieces(player,false)==1,"Mixed armor counts each valid slot only");
            h.assertTrue(player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)==1,"Mixed set cannot gain a full-set bonus");
            player.setHealth(player.getMaxHealth());player.invulnerableTime=0;
            player.hurt(player.damageSources().fellOutOfWorld(),100);
            h.assertTrue(!player.isAlive(),"Void must remain lethal");
        }finally{TestPlayers.remove(player);}h.succeed();
    }
    @GameTest(template="empty") public static void nativeEnchantmentsRepairAndSerializationRemainAvailable(GameTestHelper h){
        for(boolean steel:new boolean[]{false,true})for(var slot:RealmArmor.SLOTS){
            var item=RealmArmaments.item((steel?"riftsteel_":"riftsilver_")+kind(slot));var stack=new ItemStack(item);
            for(String tag:List.of("enchantable/armor","enchantable/durability","enchantable/equippable","trimmable_armor"))
                h.assertTrue(stack.is(TagKey.create(Registries.ITEM,ResourceLocation.withDefaultNamespace(tag))),"Armor classification missing: "+tag);
            var registry=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            h.assertTrue(registry.getHolderOrThrow(Enchantments.PROTECTION).value().isSupportedItem(stack),"Protection must support native armor");
            h.assertTrue(registry.getHolderOrThrow(Enchantments.MENDING).value().isSupportedItem(stack),"Mending must support native armor");
            h.assertTrue(item.isValidRepairItem(stack,new ItemStack(steel?RealmArmaments.RIFTSTEEL.get():pro.erez.interstice.Interstice.RIFTSILVER_INGOT.get())),"Repair ingredient changed");
            stack.enchant(registry.getHolderOrThrow(Enchantments.PROTECTION),4);stack.setDamageValue(30);
            var restored=ItemStack.parse(h.getLevel().registryAccess(),stack.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(restored.is(item)&&restored.getDamageValue()==30&&EnchantmentHelper.getItemEnchantmentLevel(registry.getHolderOrThrow(Enchantments.PROTECTION),restored)==4,"Saved armor must preserve identity, wear and enchantment");
        }h.succeed();
    }
    @GameTest(template="empty") public static void newEnchantmentsHaveNormalAcquisitionAndEquipmentEffects(GameTestHelper h){
        var player=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        try{
            var boots=new ItemStack(RealmArmaments.item("riftsteel_boots"));var chest=new ItemStack(RealmArmaments.item("riftsteel_chestplate"));
            var ward=enchant(h,"rift_ward");var foot=enchant(h,"surefooted");
            h.assertTrue(ward.value().isSupportedItem(chest)&&!ward.value().isSupportedItem(boots),"Ward is chest-only");
            h.assertTrue(foot.value().isSupportedItem(boots)&&!foot.value().isSupportedItem(chest),"Surefooted is boots-only");
            for(var enchant:List.of(ward,foot))for(String pool:List.of("in_enchanting_table","tradeable","on_random_loot"))
                h.assertTrue(enchant.is(TagKey.create(Registries.ENCHANTMENT,ResourceLocation.withDefaultNamespace(pool))),"Normal acquisition pool missing: "+pool);
            boots.enchant(foot,2);boots.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FEATHER_FALLING),4);
            player.setItemSlot(EquipmentSlot.FEET,boots);tick(player);
            h.assertTrue(player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)==5,"Surefooted II must increase the native safe fall distance to five");
            player.setItemSlot(EquipmentSlot.FEET,ItemStack.EMPTY);tick(player);
            h.assertTrue(player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)==3,"Removing boots must remove enchantment attributes");
        }finally{TestPlayers.remove(player);}h.succeed();
    }
}
