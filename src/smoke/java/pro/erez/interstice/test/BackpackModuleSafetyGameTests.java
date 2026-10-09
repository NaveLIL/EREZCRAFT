package pro.erez.interstice.test;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.equipment.*;
import pro.erez.interstice.food.TideHeart;

@GameTestHolder("interstice_equipment")
@PrefixGameTestTemplate(false)
public final class BackpackModuleSafetyGameTests {
    private static ServerPlayer player(GameTestHelper h){return TestPlayers.create(h,new BlockPos(6,2,6),GameType.SURVIVAL);}
    private static ItemStack bag(Item module){
        var bag=new ItemStack(ExpeditionEquipment.FIELD_BACKPACK.get());
        var modules=BackpackStorage.readModules(bag);modules.set(0,new ItemStack(module));BackpackStorage.writeModules(bag,modules);return bag;
    }
    private static BackpackMenu open(GameTestHelper h,ServerPlayer player,ItemStack bag){
        player.getInventory().setItem(0,bag);player.getInventory().selected=0;
        h.assertTrue(BackpackItem.open(player,0),"The regression requires the actual server-owned backpack menu");
        return (BackpackMenu)player.containerMenu;
    }
    private static int count(ItemStack bag,Item item){return BackpackStorage.read(bag).stream().filter(at->at.is(item)).mapToInt(ItemStack::getCount).sum();}
    private static ItemEntity drop(GameTestHelper h,ServerPlayer player,ItemStack stack){
        var entity=new ItemEntity(h.getLevel(),player.getX()+.3,player.getY()+.5,player.getZ(),stack);
        entity.setPickUpDelay(0);h.getLevel().addFreshEntity(entity);return entity;
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void magnetUpdatesTheExactOpenLeaseAndNeverStealsAnotherPlayersTargetedDrop(GameTestHelper h){
        var player=player(h);
        try{
            var bag=bag(ExpeditionEquipment.MAGNET_MODULE_TIER1.get());var menu=open(h,player,bag);
            var own=drop(h,player,new ItemStack(Items.DIAMOND,5));
            var foreign=drop(h,player,new ItemStack(Items.EMERALD,7));var foreignOwner=UUID.randomUUID();foreign.setTarget(foreignOwner);
            var foreignVelocity=foreign.getDeltaMovement();
            BackpackModules.tickMagnet(player,bag);
            h.assertTrue(own.isRemoved()&&count(bag,Items.DIAMOND)==5&&menu.container.getItem(0).getCount()==5,
                    "Magnet insertion must update both the immutable pack component and its live menu lease");
            h.assertTrue(!foreign.isRemoved()&&foreign.getItem().getCount()==7&&foreignOwner.equals(foreign.getTarget())
                    &&foreign.getDeltaMovement().equals(foreignVelocity)&&count(bag,Items.EMERALD)==0,
                    "A module cannot pull or deposit another player's protected target drop");
            menu.clicked(0,0,ClickType.PICKUP,player);
            h.assertTrue(menu.getCarried().is(Items.DIAMOND)&&menu.getCarried().getCount()==5&&count(bag,Items.DIAMOND)==0,
                    "The next real menu click must conserve the five inserted diamonds instead of overwriting them with an old snapshot");
            player.getInventory().setItem(9,menu.getCarried());menu.setCarried(ItemStack.EMPTY);foreign.discard();
        }finally{player.closeContainer();TestPlayers.remove(player);}
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void feedingAnOpenPackPaysOneFoodAndCannotRestoreItThroughTheOldMenuSnapshot(GameTestHelper h){
        var player=player(h);
        try{
            var bag=bag(ExpeditionEquipment.FEEDER_MODULE_TIER1.get());var contents=BackpackStorage.read(bag);
            contents.set(0,new ItemStack(Items.COOKED_BEEF,3));BackpackStorage.write(bag,contents);var menu=open(h,player,bag);
            player.getFoodData().setFoodLevel(14);BackpackModules.tickFeeder(player,bag);
            h.assertTrue(player.getFoodData().getFoodLevel()==20&&count(bag,Items.COOKED_BEEF)==2&&menu.container.getItem(0).getCount()==2,
                    "Native automatic eating must pay exactly one beef in the component and live lease");
            menu.clicked(0,0,ClickType.PICKUP,player);
            h.assertTrue(menu.getCarried().is(Items.COOKED_BEEF)&&menu.getCarried().getCount()==2&&count(bag,Items.COOKED_BEEF)==0,
                    "Picking up the remaining food after feeding must not resurrect the consumed third item");
            player.getInventory().setItem(9,menu.getCarried());menu.setCarried(ItemStack.EMPTY);
        }finally{player.closeContainer();TestPlayers.remove(player);}
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void feederPreservesNativeBowlRemaindersAndSkipsKnownManualHazardFoods(GameTestHelper h){
        var player=player(h);
        try{
            var bag=bag(ExpeditionEquipment.FEEDER_MODULE_TIER1.get());var contents=BackpackStorage.read(bag);
            contents.set(0,new ItemStack(RealmAgriculture.ROOT.get(),2));contents.set(1,new ItemStack(TideHeart.FRUIT.get()));
            contents.set(2,new ItemStack(Items.MUSHROOM_STEW));BackpackStorage.write(bag,contents);var menu=open(h,player,bag);
            player.getFoodData().setFoodLevel(14);BackpackModules.tickFeeder(player,bag);
            h.assertTrue(player.getFoodData().getFoodLevel()==20&&count(bag,Items.MUSHROOM_STEW)==0&&count(bag,Items.BOWL)==1
                    &&count(bag,RealmAgriculture.ROOT.get())==2&&count(bag,TideHeart.FRUIT.get())==1
                    &&!player.hasEffect(MobEffects.POISON)&&!player.hasEffect(TideHeart.REACTION),
                    "Feeding must use native consumption/byproducts while preserving the raw root and timed-risk fruit for explicit player choice");
            h.assertTrue(menu.container.getItem(2).is(Items.BOWL),"The converted bowl must be visible in the same live menu without a later overwrite");
        }finally{player.closeContainer();TestPlayers.remove(player);}
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void compressionPreservesSpecialComponentsAndPublishesItsPaidOutputToTheOpenLease(GameTestHelper h){
        var player=player(h);
        try{
            var bag=bag(ExpeditionEquipment.COMPRESSION_MODULE_TIER1.get());var contents=BackpackStorage.read(bag);
            var named=new ItemStack(Items.GOLD_INGOT,9);named.set(DataComponents.CUSTOM_NAME,Component.literal("Special assay sample"));
            contents.set(0,named);contents.set(1,new ItemStack(Items.IRON_INGOT,18));BackpackStorage.write(bag,contents);var menu=open(h,player,bag);
            BackpackModules.tickCompression(player,bag);
            h.assertTrue(ItemStack.matches(BackpackStorage.read(bag).get(0),named)&&count(bag,Items.GOLD_BLOCK)==0
                    &&count(bag,Items.IRON_INGOT)==9&&count(bag,Items.IRON_BLOCK)==1&&menu.container.getItem(2).is(Items.IRON_BLOCK),
                    "Only plain inputs may compact; the special nine named ingots remain intact and the live lease must receive the paid iron block");
            menu.quickMoveStack(player,2);
            h.assertTrue(player.getInventory().countItem(Items.IRON_BLOCK)==1&&count(bag,Items.IRON_BLOCK)==0&&count(bag,Items.IRON_INGOT)==9,
                    "Taking the compressed output must conserve eighteen ingot-equivalents without restoring the old snapshot");
        }finally{player.closeContainer();TestPlayers.remove(player);}
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void riftVoidDeathRetainsOneIndependentBagWithItsCargoAndModuleComponents(GameTestHelper h){
        var owner=player(h);var respawn=player(h);boolean old=h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        try{
            h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false,h.getLevel().getServer());
            var bag=new ItemStack(ExpeditionEquipment.RIFT_BACKPACK.get());var contents=BackpackStorage.read(bag);
            var tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.setDamageValue(37);tool.set(DataComponents.CUSTOM_NAME,Component.literal("Retained void cargo"));
            contents.set(83,tool);BackpackStorage.write(bag,contents);var modules=BackpackStorage.readModules(bag);
            modules.set(3,new ItemStack(ExpeditionEquipment.FEEDER_MODULE_TIER1.get()));BackpackModuleItem.setFeederMode(modules.get(3),BackpackModuleItem.FEEDER_FAST);
            BackpackStorage.writeModules(bag,modules);BackpackHarness.set(owner,bag);var id=BackpackStorage.id(bag);
            owner.setHealth(0);owner.die(h.getLevel().damageSources().fellOutOfWorld());
            h.assertTrue(BackpackHarness.get(owner)==bag&&h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(owner.blockPosition()).inflate(4),
                    entity->BackpackStorage.isPack(entity.getItem())&&id.equals(BackpackStorage.id(entity.getItem()))).isEmpty(),
                    "The actual worn Rift void-death path must retain its one bag rather than emit another owner copy");
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(respawn,owner,true));var kept=BackpackHarness.get(respawn);
            h.assertTrue(kept!=bag&&id.equals(BackpackStorage.id(kept))&&BackpackHarness.get(owner).isEmpty()
                    &&ItemStack.matches(BackpackStorage.read(kept).get(83),tool)
                    &&BackpackModuleItem.getFeederMode(BackpackStorage.getModule(kept,ModuleType.FEEDER))==BackpackModuleItem.FEEDER_FAST,
                    "Native attachment clone must conserve one independent complete bag, its final cargo slot and installed module state");
        }finally{
            h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(old,h.getLevel().getServer());
            owner.closeContainer();respawn.closeContainer();TestPlayers.remove(owner);TestPlayers.remove(respawn);
        }
        h.succeed();
    }
}
