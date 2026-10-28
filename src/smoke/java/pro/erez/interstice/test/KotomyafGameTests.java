package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.gear.*;

@GameTestHolder("interstice_progression")
@PrefixGameTestTemplate(false)
public final class KotomyafGameTests {
    private static ResourceLocation id(String n){return ResourceLocation.fromNamespaceAndPath("interstice",n);}
    private static String kind(EquipmentSlot s){return switch(s){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    private static void equip(LivingEntity p){for(var slot:RealmArmor.SLOTS)p.setItemSlot(slot,new ItemStack(KotomyafArmor.item("kotomyaf_"+kind(slot))));}
    private static void warm(net.minecraft.server.level.ServerPlayer p){p.setNoGravity(true);p.setInvulnerable(false);for(int i=0;i<80;i++){p.tick();p.connection.tick();}p.invulnerableTime=0;}
    @GameTest(template="empty") public static void fourRealRecipesRequireFishAndAcceptWoolColors(GameTestHelper h){
        for(var kind:List.of("helmet","chestplate","leggings","boots")){
            var recipe=(ShapedRecipe)h.getLevel().getRecipeManager().byKey(id("kotomyaf_"+kind)).orElseThrow().value();
            var slots=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));int fishAt=-1;
            for(int y=0;y<recipe.getHeight();y++)for(int x=0;x<recipe.getWidth();x++){
                var ingredient=recipe.getIngredients().get(y*recipe.getWidth()+x);int at=y*3+x;
                if(ingredient.isEmpty())continue;
                if(ingredient.test(new ItemStack(Items.SALMON))){slots.set(at,new ItemStack(Items.SALMON));fishAt=at;}
                else {h.assertTrue(ingredient.test(new ItemStack(Items.BLUE_WOOL)),"Any wool color must work");slots.set(at,new ItemStack(Items.BLUE_WOOL));}
            }
            h.assertTrue(fishAt>=0,"Fish is mandatory");var input=CraftingInput.of(3,3,slots);
            h.assertTrue(recipe.matches(input,h.getLevel()),"Real recipe must match "+kind);
            var result=recipe.assemble(input,h.getLevel().registryAccess());
            h.assertTrue(result.is(KotomyafArmor.item("kotomyaf_"+kind))&&result.getCount()==1,"Recipe result identity");
            slots.set(fishAt,ItemStack.EMPTY);h.assertTrue(!recipe.matches(CraftingInput.of(3,3,slots),h.getLevel()),"No fish, no cat armor");
        }h.succeed();
    }
    @GameTest(template="empty") public static void fullSetNegatesRealFallButOneMissingPieceDoesNot(GameTestHelper h){
        var p=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        try{warm(p);equip(p);p.setHealth(20);p.hurt(p.damageSources().fall(),12);
            h.assertTrue(p.getHealth()==20,"Full set must cancel fall damage");
            p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.invulnerableTime=0;p.hurt(p.damageSources().fall(),12);
            h.assertTrue(p.getHealth()<20,"Removing one piece must remove fall immunity");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void woolDoesNotGrantToxinLavaOrVoidImmunity(GameTestHelper h){
        var p=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        try{warm(p);equip(p);
            for(var source:List.of(p.damageSources().lava(),new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,id("toxin")))))){
                p.setHealth(20);p.invulnerableTime=0;p.hurt(source,6);h.assertTrue(p.getHealth()<20,"Wool must retain environmental risks");
            }
            p.invulnerableTime=0;p.hurt(p.damageSources().fellOutOfWorld(),100);h.assertTrue(!p.isAlive(),"Void remains lethal");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void onlyCreepersAndBabyZombiesFearTheFullCostume(GameTestHelper h){
        var p=TestPlayers.create(h,new BlockPos(5,2,5),GameType.SURVIVAL);
        var creeper=h.spawn(EntityType.CREEPER,new BlockPos(6,2,5));var baby=h.spawn(EntityType.ZOMBIE,new BlockPos(5,2,6));var adult=h.spawn(EntityType.ZOMBIE,new BlockPos(4,2,5));
        try{equip(p);baby.setBaby(true);
            for(var mob:List.of(creeper,baby,adult))mob.setNoAi(true);
            creeper.setTarget(p);baby.setTarget(p);adult.setTarget(p);
            h.assertTrue(creeper.getTarget()==null&&baby.getTarget()==null,"Native target events must reject protected player");
            h.assertTrue(adult.getTarget()==p,"Adult zombies remain dangerous");
            for(var mob:List.of(creeper,baby))h.assertTrue(mob.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal() instanceof KotomyafArmor.CatAvoidGoal).count()==1,"Exactly one real pathfinding avoidance goal");
            p.setItemSlot(EquipmentSlot.FEET,ItemStack.EMPTY);creeper.setTarget(p);baby.setTarget(p);
            h.assertTrue(creeper.getTarget()==p&&baby.getTarget()==p,"Incomplete outfit must not protect targeting");
            equip(p);baby.setBaby(false);baby.setTarget(p);h.assertTrue(baby.getTarget()==p,"Growing up removes baby fear");
        }finally{TestPlayers.remove(p);creeper.discard();baby.discard();adult.discard();}h.succeed();
    }
    @GameTest(template="empty") public static void repairEnchantAndSavePreserveEveryPiece(GameTestHelper h){
        var ench=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);var protection=ench.getHolderOrThrow(Enchantments.PROTECTION);var mending=ench.getHolderOrThrow(Enchantments.MENDING);
        for(var slot:RealmArmor.SLOTS){var item=KotomyafArmor.item("kotomyaf_"+kind(slot));var stack=new ItemStack(item);
            h.assertTrue(protection.value().isSupportedItem(stack)&&mending.value().isSupportedItem(stack),"Normal enchantments must work");
            h.assertTrue(item.isValidRepairItem(stack,new ItemStack(Items.RED_WOOL)),"Wool repairs every piece");
            stack.enchant(protection,3);stack.setDamageValue(17);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Котомяф"));
            var restored=ItemStack.parse(h.getLevel().registryAccess(),stack.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(restored.is(item)&&restored.getDamageValue()==17&&EnchantmentHelper.getItemEnchantmentLevel(protection,restored)==3&&restored.getHoverName().getString().equals("Котомяф"),"Save must preserve identity, name, wear and enchantment");
        }h.succeed();
    }
    @GameTest(template="empty") public static void damagedWrongSlotAndMixedArmorCannotCompleteSet(GameTestHelper h){
        var p=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        try{equip(p);h.assertTrue(KotomyafArmor.full(p),"Correct full set");
            p.getItemBySlot(EquipmentSlot.FEET).setDamageValue(p.getItemBySlot(EquipmentSlot.FEET).getMaxDamage());h.assertTrue(!KotomyafArmor.full(p),"Broken boots cannot grant abilities");
            equip(p);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(KotomyafArmor.item("kotomyaf_boots")));h.assertTrue(!KotomyafArmor.full(p),"Wrong-slot item is invalid");
            equip(p);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AegisArmor.item("aegis_helmet")));h.assertTrue(!KotomyafArmor.full(p),"Magen plus three cat pieces is not full");
            h.assertTrue(RealmArmor.pieces(p,true)==0&&RealmArmor.pieces(p,false)==0,"Cat armor must not count as expedition steel or silver");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
}
