package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.*;
import pro.erez.interstice.agriculture.*;
import pro.erez.interstice.equipment.ExpeditionEquipment;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.tide.*;

@GameTestHolder("interstice_progression")
@PrefixGameTestTemplate(false)
public final class ProgressionGameTests {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Interstice.ID, path); }
    private static ServerPlayer player(GameTestHelper h) { return TestPlayers.create(h, new BlockPos(4, 2, 4), GameType.SURVIVAL); }
    private static void equip(ServerPlayer p, String tier) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(RealmArmaments.item(tier + "_helmet")));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(RealmArmaments.item(tier + "_chestplate")));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RealmArmaments.item(tier + "_leggings")));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(RealmArmaments.item(tier + "_boots")));
    }
    @GameTest(template="empty") public static void allEquipmentCraftsThroughLoadedRecipesAndRepairs(GameTestHelper h) {
        for (var entry : RealmArmaments.EQUIPMENT.entrySet()) {
            var holder = h.getLevel().getRecipeManager().byKey(id(entry.getKey())).orElseThrow();
            var recipe = (ShapedRecipe)holder.value(); var ingredients = recipe.getIngredients();
            List<ItemStack> slots = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
            for (int y=0;y<recipe.getHeight();y++) for (int x=0;x<recipe.getWidth();x++) {
                var choices = ingredients.get(y*recipe.getWidth()+x).getItems();
                if (choices.length>0) slots.set(y*3+x, choices[0].copy());
            }
            var input = CraftingInput.of(3,3,slots);
            h.assertTrue(recipe.matches(input,h.getLevel()), "Loaded craft must match: " + entry.getKey());
            var output = recipe.assemble(input,h.getLevel().registryAccess());
            h.assertTrue(output.is(entry.getValue().get()) && output.getCount()==1 && output.isDamageableItem(), "Must craft one durable item");
            Item ingot = entry.getKey().startsWith("riftsteel") ? RealmArmaments.RIFTSTEEL.get() : Interstice.RIFTSILVER_INGOT.get();
            h.assertTrue(output.getItem().isValidRepairItem(output,new ItemStack(ingot)), "Native repair ingredient must match tier");
            h.assertTrue(!output.getItem().isValidRepairItem(output,new ItemStack(Items.IRON_INGOT)), "Wrong ingot cannot repair native gear");
        }
        var silver = new ItemStack(RealmArmaments.item("riftsilver_pickaxe"));
        var steel = new ItemStack(RealmArmaments.item("riftsteel_pickaxe"));
        h.assertTrue(!silver.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()) && steel.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "Only the advanced pick may mine diamond-tier blocks");
        h.assertTrue(steel.isCorrectToolForDrops(Interstice.RIFTSILVER_ORE.get().defaultBlockState()), "Native ore must recognise the advanced pick");
        h.succeed();
    }
    @GameTest(template="empty") public static void retortPaysAllIngredientsAndProducesExactlyFourIngots(GameTestHelper h) {
        BlockPos pos=new BlockPos(4,2,4);h.setBlock(pos,RealmAgriculture.RETORT.get());
        var retort=(RetortBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        retort.setItem(0,new ItemStack(Interstice.RIFTSILVER_INGOT.get(),4));
        retort.setItem(1,new ItemStack(Interstice.PYROLITH.get(),2));
        retort.setItem(2,new ItemStack(RealmAgriculture.PURE_LINING.get(),2));
        retort.setItem(3,new ItemStack(ExpeditionEquipment.CHEMOTROPHIC_FABRIC.get(),2));
        retort.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get()));
        h.assertTrue(retort.selectRecipe(id("retort_riftsteel")),"Real machine must select the registered alloy recipe");
        for(int i=0;i<1200;i++)retort.process();
        h.assertTrue(retort.getItem(7).is(RealmArmaments.RIFTSTEEL.get())&&retort.getItem(7).getCount()==4,"Machine must yield exactly four ingots");
        for(int i=0;i<4;i++)h.assertTrue(retort.getItem(i).isEmpty(),"Every reagent must be paid");
        for(int i=0;i<1200;i++)retort.process();
        h.assertTrue(retort.getItem(7).getCount()==4,"Empty inputs cannot duplicate ingots");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=160) public static void spiderProtectionIsFiniteAndOrdinarySilverStillGetsPierced(GameTestHelper h) {
        var silver=player(h);var steel=player(h);var partial=player(h);var vanilla=player(h);
        for(var p:List.of(silver,steel,partial,vanilla)){p.setNoGravity(true);p.setInvulnerable(false);}
        equip(silver,"riftsilver");equip(steel,"riftsteel");partial.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RealmArmaments.item("riftsteel_chestplate")));
        vanilla.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.NETHERITE_CHESTPLATE));
        h.runAtTickTime(75,()->{
            try {
                var type=h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,id("spider_bite")));
                for(var p:List.of(silver,steel,partial,vanilla)){p.invulnerableTime=0;p.hurt(new net.minecraft.world.damagesource.DamageSource(type),8);}
                h.assertTrue(Math.abs(silver.getHealth()-12)<.01 && Math.abs(vanilla.getHealth()-12)<.01,"Silver and vanilla armour must still be pierced");
                h.assertTrue(Math.abs(steel.getHealth()-14.56)<.01,"Four native pieces must reduce only 32 percent");
                h.assertTrue(Math.abs(partial.getHealth()-12.64)<.01,"One piece must reduce exactly eight percent");
                h.assertTrue(steel.getItemBySlot(EquipmentSlot.CHEST).getDamageValue()>0,"Specialised armour must spend durability on bypass damage");
                steel.invulnerableTime=0;steel.setHealth(20);steel.hurt(steel.damageSources().magic(),8);
                h.assertTrue(Math.abs(steel.getHealth()-12)<.01,"Unrelated bypass damage must not gain spider resistance");
            } finally {for(var p:List.of(silver,steel,partial,vanilla))TestPlayers.remove(p);}
            h.succeed();
        });
    }
    @GameTest(template="empty") public static void advancementTreeAndJournalAreRealAndIdempotent(GameTestHelper h) {
        var p=player(h);
        try {
            for(String path:FieldJournal.MILESTONES)h.assertTrue(p.server.getAdvancements().get(id(path))!=null,"Native advancement must load: "+path);
            ExpeditionTrials.ensureJournal(p);ExpeditionTrials.ensureJournal(p);
            h.assertTrue(p.getInventory().countItem(FieldJournal.JOURNAL.get())==1,"Repeated arrival cannot duplicate the journal");
            var book=FieldJournal.create(p);var before=book.get(DataComponents.WRITTEN_BOOK_CONTENT);
            h.assertTrue(before!=null && before.pages().size()==16,"Journal must contain route and complete checklist");
            ExpeditionTrials.award(p,"trials/return_home","returned");FieldJournal.refresh(book,p);
            h.assertTrue(ExpeditionTrials.completed(p,"trials/return_home"),"Native criterion must own progress");
            h.assertTrue(!book.get(DataComponents.WRITTEN_BOOK_CONTENT).pages().getFirst().equals(before.pages().getFirst()),"Opening must refresh the player's checklist");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void spiderVariantCriteriaCannotBeCompletedByOneVariant(GameTestHelper h) {
        var p=player(h);
        try {
            var spider=Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());spider.setPos(p.position());
            for(var variant:CaveRiftSpiderEntity.Variant.values()) {
                spider.setVariant(variant);
                net.minecraft.advancements.CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(p,spider,p.damageSources().playerAttack(p));
                h.assertTrue(ExpeditionTrials.completed(p,"trials/all_spiders")== (variant==CaveRiftSpiderEntity.Variant.SPITTER),"Each variant must be counted separately");
            }
            spider.discard();
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void ordinaryWorldAndCreativeCannotEarnTideTrial(GameTestHelper h) {
        var p=player(h);
        try {
            var surge=new TideState(TidePhase.SURGE,0,1200,0);var ebb=new TideState(TidePhase.EBB,0,1200,0);
            for(int i=0;i<20;i++)ExpeditionTrials.observeTide(p,surge,true);
            ExpeditionTrials.observeTide(p,ebb,true);
            h.assertTrue(!ExpeditionTrials.completed(p,"trials/survive_tide"),"Overworld observations cannot earn a realm survival trial");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=340) public static void realTicksEarnShelterTrialButLogoutAndExposureResetIt(GameTestHelper h) {
        var safe=player(h);var exposed=player(h);var logout=player(h);var creative=player(h);
        var players=List.of(safe,exposed,logout,creative);
        var world=h.getLevel().getServer().getLevel(IslandWorld.CURRENT_WORLD);
        h.assertTrue(world!=null,"Actual current realm must exist");
        int x=2048,z=2048;
        var geometry=pro.erez.interstice.geometry.GeometryProfiles.get(world);
        for(int i=0;i<players.size();i++){
            int at=x+i*4;world.getChunk(at>>4,z>>4);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                int top=(int)Math.floor(pro.erez.interstice.SeaSurface.cellMinimum(geometry,at+dx,z+dz,true))-geometry.clearance();
                for(int y=98;y<=top;y++)world.setBlock(new BlockPos(at+dx,y,z+dz),Blocks.AIR.defaultBlockState(),3);
            }
            world.setBlock(new BlockPos(at,99,z),Blocks.STONE.defaultBlockState(),3);
            if(i!=1)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)world.setBlock(new BlockPos(at+dx,103,z+dz),Blocks.STONE.defaultBlockState(),3);
            var p=players.get(i);p.teleportTo(world,at+.5,100,z+.5,Set.of(),0,0);p.hasChangedDimension();p.setNoGravity(true);
        }
        creative.setGameMode(GameType.CREATIVE);
        TideManager.getSavedData(safe.server).setPhase(TidePhase.SURGE,12000);
        h.runAtTickTime(210,()->{
            System.out.println("PROGRESSION_TIDE position="+safe.position()+" dimension="+safe.level().dimension().location()+" alive="+safe.isAlive()+" playerTicks="+safe.tickCount+" roof="+ShelterDetector.isSheltered(safe.level(),safe)+" tide="+TideManager.getState(safe.server));
            try{var field=ExpeditionTrials.class.getDeclaredField("TIDES");field.setAccessible(true);System.out.println("PROGRESSION_ATTEMPTS "+field.get(null));}catch(Exception e){throw new IllegalStateException(e);}
        });
        h.runAtTickTime(220,()->net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(logout)));
        h.runAtTickTime(245,()->TideManager.getSavedData(safe.server).setPhase(TidePhase.EBB,12000));
        h.runAtTickTime(275,()->{
            try{
                h.assertTrue(ExpeditionTrials.completed(safe,"trials/survive_tide"),"A real sheltered Survival player must earn the trial after ebb begins");
                for(var p:List.of(exposed,logout,creative))h.assertTrue(!ExpeditionTrials.completed(p,"trials/survive_tide"),"Exposure, logout or Creative must not satisfy shelter survival: "+p.getGameProfile().getName()+" roof="+ShelterDetector.isSheltered(p.level(),p));
                equip(safe,"riftsteel");
            }catch(Throwable e){for(var p:players)TestPlayers.remove(p);TideManager.getSavedData(safe.server).setPhase(TidePhase.CALM,12000);throw e;}
        });
        h.runAtTickTime(310,()->{
            try{h.assertTrue(ExpeditionTrials.completed(safe,"trials/riftsteel_suit"),"Equipping all four correct-slot pieces in the realm must count");}
            finally{for(var p:players)TestPlayers.remove(p);TideManager.getSavedData(safe.server).setPhase(TidePhase.CALM,12000);}h.succeed();
        });
    }
}
