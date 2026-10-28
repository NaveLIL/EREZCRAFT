package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.RealmArmaments;
import pro.erez.interstice.minerals.MineralEcology;

@GameTestHolder("interstice_progression")
@PrefixGameTestTemplate(false)
public final class LongswordGameTests {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Interstice.ID,path); }
    private static Item original() { return RealmArmaments.item("riftsteel_sword"); }
    private static Item longer() { return RealmArmaments.item("riftsteel_longsword"); }

    @GameTest(template="empty", timeoutTicks=100)
    public static void distinctWeaponsHaveSevenAndElevenDamageAndNormalChargedHits(GameTestHelper h) {
        var oldPlayer=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        var longPlayer=TestPlayers.create(h,new BlockPos(8,2,3),GameType.SURVIVAL);
        oldPlayer.setNoGravity(true);longPlayer.setNoGravity(true);
        oldPlayer.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(original()));
        longPlayer.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(longer()));
        var first=h.spawn(EntityType.COW,new BlockPos(3,2,5));
        var second=h.spawn(EntityType.COW,new BlockPos(8,2,5));
        for(var target:List.of(first,second)) {
            target.setNoAi(true);target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);target.setHealth(40);
        }
        h.runAtTickTime(35,()->{
            try {
                // Embedded test connections are not in the server's socket tick list.
                // Drive the ordinary connection tick, including living equipment/cooldown updates.
                for(int tick=0;tick<25;tick++){oldPlayer.connection.tick();longPlayer.connection.tick();}
                System.out.println("LONGSWORD_DAMAGE old="+oldPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    +" long="+longPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE));
                h.assertTrue(original()!=longer(),"Longsword must have a distinct item identity");
                h.assertTrue(Math.abs(oldPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE)-7)<.001,"Original sword damage changed: "+oldPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE));
                h.assertTrue(Math.abs(longPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE)-11)<.001,"Longsword must add exactly four damage");
                h.assertTrue(Math.abs(oldPlayer.getAttributeValue(Attributes.ATTACK_SPEED)-1.6)<.001
                    && Math.abs(longPlayer.getAttributeValue(Attributes.ATTACK_SPEED)-1.6)<.001,"Attack speed must stay unchanged");
                h.assertTrue(oldPlayer.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)==longPlayer.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),"Visual length must not modify reach");
                oldPlayer.fallDistance=0;longPlayer.fallDistance=0;
                oldPlayer.setSprinting(false);longPlayer.setSprinting(false);
                oldPlayer.attack(first);longPlayer.attack(second);
                h.assertTrue(Math.abs(first.getHealth()-33)<.01,"Original charged hit must deal seven damage");
                h.assertTrue(Math.abs(second.getHealth()-29)<.01,"Longsword charged hit must deal eleven damage");
                h.assertTrue(oldPlayer.getMainHandItem().getDamageValue()==1
                    && longPlayer.getMainHandItem().getDamageValue()==1,"Both hits must spend ordinary sword durability");
            } finally {TestPlayers.remove(oldPlayer);TestPlayers.remove(longPlayer);first.discard();second.discard();}
            h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void oldCraftStillCostsTwoAndLongCraftConsumesFourRealIngots(GameTestHelper h) {
        var level=h.getLevel();
        var slots=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));
        slots.set(1,new ItemStack(RealmArmaments.RIFTSTEEL.get()));
        slots.set(4,new ItemStack(RealmArmaments.RIFTSTEEL.get()));
        slots.set(7,new ItemStack(MineralEcology.WORLD_STICK.get()));
        var cheap=CraftingInput.of(3,3,slots);
        var cheapRecipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,cheap,level).orElseThrow();
        h.assertTrue(cheapRecipe.id().equals(id("riftsteel_sword"))
            && cheapRecipe.value().assemble(cheap,level.registryAccess()).is(original()),"Old two-ingot recipe must remain the original sword");
        var longRecipe=(ShapedRecipe)level.getRecipeManager().byKey(id("riftsteel_longsword")).orElseThrow().value();
        h.assertTrue(!longRecipe.matches(cheap,level),"Two ingots cannot buy the stronger weapon");
        var player=TestPlayers.create(h,new BlockPos(4,2,4),GameType.SURVIVAL);
        try {
            var table=h.absolutePos(new BlockPos(6,1,5));level.setBlock(table,Blocks.CRAFTING_TABLE.defaultBlockState(),3);
            var menu=new CraftingMenu(121,player.getInventory(),ContainerLevelAccess.create(level,table));player.containerMenu=menu;
            for(int at:new int[]{1,3,4,5})menu.getSlot(at+1).setByPlayer(new ItemStack(RealmArmaments.RIFTSTEEL.get()));
            menu.getSlot(8).setByPlayer(new ItemStack(MineralEcology.WORLD_STICK.get()));
            h.assertTrue(menu.getSlot(0).getItem().is(longer()),"Real crafting table must show the long sword");
            menu.quickMoveStack(player,0);
            h.assertTrue(player.getInventory().countItem(longer())==1,"Result slot must give exactly one long sword");
            for(int i=1;i<=9;i++)h.assertTrue(menu.getSlot(i).getItem().isEmpty(),"Craft must consume all four ingots and the native stick");
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(),"Empty table cannot produce a duplicate weapon");
        } finally {player.closeContainer();TestPlayers.remove(player);}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void bothSwordsRemainRepairableEnchantableAndSaveAsDistinctItems(GameTestHelper h) {
        for(Item item:List.of(original(),longer())) {
            var stack=new ItemStack(item);
            h.assertTrue(stack.getMaxDamage()==1792&&stack.getMaxStackSize()==1,"Steel durability and stack limit must remain intact");
            h.assertTrue(stack.is(ItemTags.SWORDS),"Both swords need the native sword classification");
            for(String path:List.of("enchantable/sword","enchantable/weapon","enchantable/durability"))
                h.assertTrue(stack.is(TagKey.create(Registries.ITEM,ResourceLocation.withDefaultNamespace(path))),"Missing enchantment family: "+path);
            h.assertTrue(item.isValidRepairItem(stack,new ItemStack(RealmArmaments.RIFTSTEEL.get())),"Repair must use riftsteel");
            h.assertTrue(!item.isValidRepairItem(stack,new ItemStack(Items.IRON_INGOT)),"Ordinary iron cannot repair native steel");
            stack.setDamageValue(37);
            var restored=ItemStack.parse(h.getLevel().registryAccess(),stack.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(restored.is(item)&&restored.getDamageValue()==37,"Serialization changed item identity or wear");
        }
        h.succeed();
    }
}
