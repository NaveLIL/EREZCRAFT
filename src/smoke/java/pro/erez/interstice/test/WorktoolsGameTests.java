package pro.erez.interstice.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.gear.*;

@GameTestHolder("interstice_progression")
@PrefixGameTestTemplate(false)
public final class WorktoolsGameTests {
    private static ItemStack tool(String tier,String kind){return new ItemStack(RealmArmaments.item(tier+"_"+kind));}
    private static ServerPlayer player(GameTestHelper h,ItemStack tool){
        var p=TestPlayers.create(h,new BlockPos(4,2,2),GameType.SURVIVAL);p.setNoGravity(true);p.setInvulnerable(true);p.setItemSlot(EquipmentSlot.MAINHAND,tool);p.setYRot(0);p.setXRot(0);return p;
    }
    private static BlockPos center(GameTestHelper h){return h.absolutePos(new BlockPos(4,3,4));}
    private static void wall(GameTestHelper h,Block block){var c=center(h);h.getLevel().setBlock(c,block.defaultBlockState(),3);for(var pos:RealmWorktools.plane(c,Direction.Axis.Z))h.getLevel().setBlock(pos,block.defaultBlockState(),3);}
    private static void aim(ServerPlayer p,BlockPos c,Direction face){RealmWorktools.face(new PlayerInteractEvent.LeftClickBlock(p,c,face,PlayerInteractEvent.LeftClickBlock.Action.START));}
    private static long air(GameTestHelper h){var c=center(h);return (h.getLevel().getBlockState(c).isAir()?1:0)+RealmWorktools.plane(c,Direction.Axis.Z).stream().filter(p->h.getLevel().getBlockState(p).isAir()).count();}

    @GameTest(template="empty") public static void toggleUsesRealItemUseAndSurvivesSaveWithoutLosingData(GameTestHelper h){
        var s=tool("riftsteel","pickaxe");s.setDamageValue(37);s.set(DataComponents.CUSTOM_NAME,Component.literal("Mine"));
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString("unrelated","keep");s.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));var p=player(h,s);
        try{
            h.assertTrue(!RealmWorktools.area(s),"New tools default to single block");p.setShiftKeyDown(true);
            p.gameMode.useItem(p,h.getLevel(),s,InteractionHand.MAIN_HAND);h.assertTrue(RealmWorktools.area(s),"Sneak use must enable area mode");
            var saved=ItemStack.parse(h.getLevel().registryAccess(),s.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(RealmWorktools.area(saved)&&saved.getDamageValue()==37&&saved.getHoverName().getString().equals("Mine"),"Mode, name and wear must survive a real item save");
            h.assertTrue(saved.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated").equals("keep"),"Do not replace unrelated custom data");
            p.gameMode.useItem(p,h.getLevel(),s,InteractionHand.MAIN_HAND);h.assertTrue(!RealmWorktools.area(s),"Second sneak use returns to single");
            p.setShiftKeyDown(false);p.gameMode.useItem(p,h.getLevel(),s,InteractionHand.MAIN_HAND);h.assertTrue(!RealmWorktools.area(s),"Ordinary use must not toggle");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80) public static void pickaxeBreaksExactlyOnePlaneAndPaysNineDurability(GameTestHelper h){
        var s=tool("riftsteel","pickaxe");RealmWorktools.setArea(s,true);var p=player(h,s);wall(h,Blocks.STONE);var c=center(h);h.getLevel().setBlock(c.south(),Blocks.STONE.defaultBlockState(),3);aim(p,c,Direction.NORTH);
        h.assertTrue(p.gameMode.destroyBlock(c),"Original break must succeed");
        h.runAfterDelay(3,()->{try{h.assertTrue(air(h)==9,"Exactly 3x3 stone should be removed, got "+air(h));h.assertTrue(s.getDamageValue()==9,"One durability per block: "+s.getDamageValue());h.assertTrue(h.getLevel().getBlockState(c.south()).is(Blocks.STONE),"Do not excavate a second depth layer");}finally{TestPlayers.remove(p);}h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void cancelledNeighborOreContainerAndHardBlockRemain(GameTestHelper h){
        var s=tool("riftsteel","pickaxe");RealmWorktools.setArea(s,true);var p=player(h,s);wall(h,Blocks.STONE);var c=center(h);var denied=c.west();
        h.getLevel().setBlock(c.east(),Blocks.DIAMOND_ORE.defaultBlockState(),3);h.getLevel().setBlock(c.above(),Blocks.CHEST.defaultBlockState(),3);h.getLevel().setBlock(c.below(),Blocks.BEDROCK.defaultBlockState(),3);
        Consumer<BlockEvent.BreakEvent> guard=e->{if(e.getPlayer()==p&&e.getPos().equals(denied))e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(guard);aim(p,c,Direction.NORTH);p.gameMode.destroyBlock(c);
        h.runAfterDelay(3,()->{try{h.assertTrue(air(h)==5,"Only five eligible unprotected blocks may break");h.assertTrue(s.getDamageValue()==5,"Denied blocks must spend no durability");h.assertTrue(h.getLevel().getBlockState(denied).is(Blocks.STONE)&&h.getLevel().getBlockState(c.east()).is(Blocks.DIAMOND_ORE)&&h.getLevel().getBlockState(c.above()).is(Blocks.CHEST)&&h.getLevel().getBlockState(c.below()).is(Blocks.BEDROCK),"Exclusions must stay intact");}finally{NeoForge.EVENT_BUS.unregister(guard);TestPlayers.remove(p);}h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void cancelledOriginalDoesNotScheduleNeighbors(GameTestHelper h){
        var s=tool("riftsteel","pickaxe");RealmWorktools.setArea(s,true);var p=player(h,s);wall(h,Blocks.STONE);var c=center(h);
        Consumer<BlockEvent.BreakEvent> guard=e->{if(e.getPlayer()==p&&e.getPos().equals(c))e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(guard);h.assertTrue(!p.gameMode.destroyBlock(c),"Protection must reject original");
        h.runAfterDelay(3,()->{try{h.assertTrue(air(h)==0&&s.getDamageValue()==0,"Cancellation must leave the whole wall and durability intact");}finally{NeoForge.EVENT_BUS.unregister(guard);TestPlayers.remove(p);}h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void failedActualRemovalCannotTriggerPendingWork(GameTestHelper h){
        var s=tool("riftsteel","pickaxe");RealmWorktools.setArea(s,true);var p=player(h,s);wall(h,Blocks.STONE);var c=center(h);
        s.mineBlock(h.getLevel(),Blocks.STONE.defaultBlockState(),c,p); // mirrors mineBlock called before a custom block refuses removal
        h.runAfterDelay(3,()->{try{h.assertTrue(air(h)==0,"A pre-removal callback alone cannot destroy neighbors");}finally{TestPlayers.remove(p);}h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void singleModeSneakingAndVanillaToolsStaySingle(GameTestHelper h){
        var s=tool("riftsteel","pickaxe");var p=player(h,s);wall(h,Blocks.STONE);p.gameMode.destroyBlock(center(h));
        h.runAfterDelay(3,()->{h.assertTrue(air(h)==1,"Default mode is single");wall(h,Blocks.STONE);RealmWorktools.setArea(s,true);p.setShiftKeyDown(true);p.gameMode.destroyBlock(center(h));});
        h.runAfterDelay(6,()->{try{h.assertTrue(air(h)==1,"Crouching suppresses area work");h.assertTrue(RealmWorktools.area(s),"Temporary precision must retain the saved area mode");}finally{TestPlayers.remove(p);}h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void toolSwapAndBreakageStopQueuedAreaWork(GameTestHelper h){
        var s=tool("riftsteel","pickaxe");RealmWorktools.setArea(s,true);s.setDamageValue(s.getMaxDamage()-1);var p=player(h,s);wall(h,Blocks.STONE);p.gameMode.destroyBlock(center(h));
        h.runAfterDelay(3,()->{try{h.assertTrue(air(h)==1&&p.getMainHandItem().isEmpty(),"Last durability may break the center only");}finally{TestPlayers.remove(p);}h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void shovelClearsSoilButLeavesStone(GameTestHelper h){
        var s=tool("riftsteel","shovel");RealmWorktools.setArea(s,true);var p=player(h,s);wall(h,Blocks.DIRT);var c=center(h);h.getLevel().setBlock(c.east(),Blocks.STONE.defaultBlockState(),3);aim(p,c,Direction.NORTH);p.gameMode.destroyBlock(c);
        h.runAfterDelay(3,()->{try{h.assertTrue(air(h)==8&&s.getDamageValue()==8,"Shovel must clear eight soil blocks and spend eight wear");h.assertTrue(h.getLevel().getBlockState(c.east()).is(Blocks.STONE),"Shovel must not mine stone");}finally{TestPlayers.remove(p);}h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void axeFellsConnectedSameSpeciesButNotBranchesPastProtection(GameTestHelper h){
        var s=tool("riftsteel","axe");RealmWorktools.setArea(s,true);var p=player(h,s);var c=center(h);
        for(int y=0;y<4;y++)h.getLevel().setBlock(c.above(y),Blocks.OAK_LOG.defaultBlockState(),3);
        h.getLevel().setBlock(c.below(),Blocks.OAK_LOG.defaultBlockState(),3);h.getLevel().setBlock(c.east(),Blocks.BIRCH_LOG.defaultBlockState(),3);h.getLevel().setBlock(c.west(),Blocks.OAK_PLANKS.defaultBlockState(),3);
        var denied=c.above(2);Consumer<BlockEvent.BreakEvent> guard=e->{if(e.getPlayer()==p&&e.getPos().equals(denied))e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(guard);p.gameMode.destroyBlock(c);
        h.runAfterDelay(3,()->{try{h.assertTrue(h.getLevel().getBlockState(c.above()).isAir()&&h.getLevel().getBlockState(denied).is(Blocks.OAK_LOG)&&h.getLevel().getBlockState(c.above(3)).is(Blocks.OAK_LOG),"Do not traverse through protected logs");h.assertTrue(h.getLevel().getBlockState(c.below()).is(Blocks.OAK_LOG)&&h.getLevel().getBlockState(c.east()).is(Blocks.BIRCH_LOG)&&h.getLevel().getBlockState(c.west()).is(Blocks.OAK_PLANKS),"No downward, other-species or plank felling");h.assertTrue(s.getDamageValue()==2,"Exactly two logs paid");}finally{NeoForge.EVENT_BUS.unregister(guard);TestPlayers.remove(p);}h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void axeStopsAtSixteenRealLogsIncludingOriginal(GameTestHelper h){
        var s=tool("riftsteel","axe");RealmWorktools.setArea(s,true);var p=player(h,s);var c=center(h);
        for(int y=0;y<17;y++)h.getLevel().setBlock(c.above(y),Blocks.OAK_LOG.defaultBlockState(),3);
        p.gameMode.destroyBlock(c);
        h.runAfterDelay(3,()->{try{for(int y=0;y<16;y++)h.assertTrue(h.getLevel().getBlockState(c.above(y)).isAir(),"Connected log "+y+" should fell");h.assertTrue(h.getLevel().getBlockState(c.above(16)).is(Blocks.OAK_LOG)&&s.getDamageValue()==16,"Sixteen logs maximum including the first cut");}finally{TestPlayers.remove(p);}h.succeed();});
    }
    private static void farm(GameTestHelper h,Block block){var c=h.absolutePos(new BlockPos(4,1,4));h.getLevel().setBlock(c,block.defaultBlockState(),3);for(var pos:RealmWorktools.plane(c,Direction.Axis.Y))h.getLevel().setBlock(pos,block.defaultBlockState(),3);h.getLevel().setBlock(c.offset(2,0,0),Blocks.WATER.defaultBlockState(),3);}
    private static void till(ServerPlayer p,GameTestHelper h,BlockPos pos){p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false));}
    @GameTest(template="empty") public static void hoeTillsNativeSoilAndRespectsPerBlockUseProtection(GameTestHelper h){
        var s=tool("riftsteel","hoe");RealmWorktools.setArea(s,true);var p=player(h,s);farm(h,Interstice.ABYSSAL_TURF.get());var c=h.absolutePos(new BlockPos(4,1,4));var denied=c.east();
        Consumer<PlayerInteractEvent.RightClickBlock> guard=e->{if(e.getEntity()==p&&e.getPos().equals(denied))e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(guard);
        try{till(p,h,c);long changed=RealmWorktools.plane(c,Direction.Axis.Y).stream().filter(pos->h.getLevel().getBlockState(pos).is(RealmAgriculture.FARMLAND.get())).count();h.assertTrue(changed==7&&h.getLevel().getBlockState(c).is(RealmAgriculture.FARMLAND.get()),"Eight native farmland blocks excluding protected neighbor, got "+changed);h.assertTrue(h.getLevel().getBlockState(denied).is(Interstice.ABYSSAL_TURF.get())&&s.getDamageValue()==8,"Protected soil and per-block durability");}finally{NeoForge.EVENT_BUS.unregister(guard);TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void hoeTillsNineVanillaBlocksAndCannotTillCoveredSoil(GameTestHelper h){
        var s=tool("riftsteel","hoe");RealmWorktools.setArea(s,true);var p=player(h,s);farm(h,Blocks.DIRT);var c=h.absolutePos(new BlockPos(4,1,4));h.getLevel().setBlock(c.east().above(),Blocks.STONE.defaultBlockState(),3);
        try{till(p,h,c);long changed=RealmWorktools.plane(c,Direction.Axis.Y).stream().filter(pos->h.getLevel().getBlockState(pos).is(Blocks.FARMLAND)).count();h.assertTrue(changed==7&&h.getLevel().getBlockState(c).is(Blocks.FARMLAND),"Covered soil stays unchanged");h.assertTrue(s.getDamageValue()==8,"Eight actual till operations paid");}finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void silverResonatesOnlyOnSuitableNativeMaterialsAndKeepsHarvestTier(GameTestHelper h){
        var pick=tool("riftsilver","pickaxe");h.assertTrue(Math.abs(pick.getDestroySpeed(Interstice.RIFTSTONE.get().defaultBlockState())-8.125)<.001,"6.5 x 1.25 native resonance");
        h.assertTrue(Math.abs(pick.getDestroySpeed(Blocks.STONE.defaultBlockState())-6.5)<.001,"No speed bonus on ordinary stone");
        h.assertTrue(!pick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()),"Resonance cannot upgrade iron harvest tier");
        RealmWorktools.setArea(pick,true);h.assertTrue(!RealmWorktools.area(pick),"Silver remains a precise single-block tool");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80) public static void silkTouchUsesActualPerBlockLootAndRetainsEnchantment(GameTestHelper h){
        var s=tool("riftsteel","pickaxe");RealmWorktools.setArea(s,true);s.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);var p=player(h,s);wall(h,Blocks.STONE);aim(p,center(h),Direction.NORTH);p.gameMode.destroyBlock(center(h));
        h.runAfterDelay(3,()->{try{int stones=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(center(h)).inflate(3)).stream().filter(e->e.getItem().is(Items.STONE)).mapToInt(e->e.getItem().getCount()).sum();h.assertTrue(stones==9,"Silk Touch must produce nine real stone drops, got "+stones);h.assertTrue(s.isEnchanted()&&s.getDamageValue()==9,"Enchantments stay and every block wears tool");}finally{TestPlayers.remove(p);}h.succeed();});
    }
}
