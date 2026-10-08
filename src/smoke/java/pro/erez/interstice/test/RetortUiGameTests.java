package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.*;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.worldgen.GardenMaterials;

@GameTestHolder("interstice_agriculture") @PrefixGameTestTemplate(false)
public final class RetortUiGameTests {
    private static final BlockPos MACHINE=new BlockPos(3,1,3);
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath(Interstice.ID,name);}
    private static RetortBlockEntity machine(GameTestHelper h){h.setBlock(MACHINE,RealmAgriculture.RETORT.get());return (RetortBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(MACHINE));}
    private static RetortMenu menu(RetortBlockEntity entity,ServerPlayer player){var menu=(RetortMenu)entity.createMenu(31,player.getInventory(),player);player.containerMenu=menu;return menu;}
    private static List<ItemStack> snapshot(List<ItemStack> items){return items.stream().map(ItemStack::copy).toList();}
    private static boolean same(List<ItemStack> a,List<ItemStack> b){return a.size()==b.size()&&java.util.stream.IntStream.range(0,a.size()).allMatch(i->a.get(i).getCount()==b.get(i).getCount()&&ItemStack.isSameItemSameComponents(a.get(i),b.get(i)));}

    @GameTest(template="empty",timeoutTicks=100)
    public static void oneBatchFillTopsUpRealInputComponentsAndRejectsReplay(GameTestHelper h){
        var entity=machine(h);var p=TestPlayers.create(h,new BlockPos(3,2,2),GameType.SURVIVAL);
        try{
            p.getInventory().clearContent();var stack=new ItemStack(RealmAgriculture.GRAIN.get(),3);stack.set(DataComponents.CUSTOM_NAME,Component.literal("fill component proof"));
            entity.setItem(0,stack.copyWithCount(1));p.getInventory().items.set(0,stack);var menu=menu(entity,p);
            h.assertTrue(menu.clickMenuButton(p,RetortMenu.fillButton(id("retort_grain_separation"))),"Valid server fill action was rejected");
            h.assertTrue(entity.getItem(0).getCount()==2&&entity.getItem(0).getHoverName().getString().equals("fill component proof")
                    &&p.getInventory().items.get(0).getCount()==2&&entity.getItem(6).isEmpty()&&entity.selectedRecipe().equals(id("retort_grain_separation")),
                    "One-batch fill duplicated items, lost components, moved fuel or failed to pin its target");
            var before=snapshot(p.getInventory().items);h.assertTrue(!menu.clickMenuButton(p,RetortMenu.BUTTON_FILL)&&same(before,p.getInventory().items)&&entity.getItem(0).getCount()==2,
                    "Repeated fill transferred a second batch despite a ready first one");h.succeed();
        }finally{TestPlayers.remove(p);}
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void missingSpaceAndBlockedOutputsRollbackTheEntireTransfer(GameTestHelper h){
        var entity=machine(h);var p=TestPlayers.create(h,new BlockPos(3,2,2),GameType.SURVIVAL);
        try{
            p.getInventory().clearContent();p.getInventory().items.set(0,new ItemStack(RealmAgriculture.GRAIN.get()));var menu=menu(entity,p);int button=RetortMenu.fillButton(id("retort_grain_separation"));
            var original=snapshot(p.getInventory().items);h.assertTrue(!menu.clickMenuButton(p,button)&&same(original,p.getInventory().items)&&entity.getItem(0).isEmpty(),"Missing ingredients were partially moved");
            p.getInventory().items.set(0,new ItemStack(RealmAgriculture.GRAIN.get(),2));for(int i=0;i<6;i++)entity.setItem(i,new ItemStack(Items.COBBLESTONE,64));original=snapshot(p.getInventory().items);
            var inputs=menu.currentInputs().items();h.assertTrue(!menu.clickMenuButton(p,button)&&same(original,p.getInventory().items)&&same(inputs,menu.currentInputs().items())&&entity.selectedRecipe()==null,
                    "Full input space mutated an inventory or pinned a failed transfer");
            for(int i=0;i<6;i++)entity.setItem(i,ItemStack.EMPTY);for(int i=7;i<10;i++)entity.setItem(i,new ItemStack(Items.COBBLESTONE,64));
            h.assertTrue(!menu.clickMenuButton(p,button)&&same(original,p.getInventory().items)&&entity.getItem(0).isEmpty()&&entity.selectedRecipe()==null,
                    "Full multi-result output accepted an input transfer");h.succeed();
        }finally{TestPlayers.remove(p);}
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void overlappingTagsAndTooManyComponentStacksRemainConservative(GameTestHelper h){
        var p=GardenMaterials.PALEHEART_PLANKS.get().asItem();var other=Interstice.GLOOMCROWN_PLANKS.get().asItem();
        var tag=TagKey.create(Registries.ITEM,id("world_planks"));
        var recipe=new RetortRecipe(List.of(new RetortRecipe.Input(Ingredient.of(tag),1),new RetortRecipe.Input(Ingredient.of(p),1)),List.of(new ItemStack(RealmAgriculture.WIRE.get())),20);
        var inputs=new ArrayList<ItemStack>(Collections.nCopies(6,ItemStack.EMPTY));var inventory=new ArrayList<ItemStack>(Collections.nCopies(36,ItemStack.EMPTY));
        inventory.set(0,new ItemStack(p));inventory.set(1,new ItemStack(other));var result=RetortMenu.fillPlan(recipe,inputs,inventory);
        h.assertTrue(result.reason()==RetortMenu.FEEDBACK_FILLED&&recipe.matches(new RetortInput(result.inputs()),h.getLevel())&&result.playerItems().get(0).isEmpty()&&result.playerItems().get(1).isEmpty(),
                "Shared ingredient allocation greedily spent a unique ingredient twice");
        var crowded=new RetortRecipe(List.of(new RetortRecipe.Input(Ingredient.of(RealmAgriculture.GRAIN.get()),7)),List.of(new ItemStack(RealmAgriculture.FLOUR.get())),20);
        for(int i=0;i<7;i++){var grain=new ItemStack(RealmAgriculture.GRAIN.get());grain.set(DataComponents.CUSTOM_NAME,Component.literal("distinct component "+i));inventory.set(i,grain);}
        var before=snapshot(inventory);result=RetortMenu.fillPlan(crowded,inputs,inventory);
        h.assertTrue(result.reason()==RetortMenu.FEEDBACK_INPUT_SPACE&&same(before,inventory)&&inputs.stream().allMatch(ItemStack::isEmpty),
                "Unpackable seven-component transfer lost items or merged distinct stacks");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void pinIsSavedOldDataDefaultsAutoAndPendingBatchesRejectControls(GameTestHelper h){
        var entity=machine(h);var p=TestPlayers.create(h,new BlockPos(3,2,2),GameType.SURVIVAL);
        try{
            var menu=menu(entity,p);var selected=id("retort_grain_separation");h.assertTrue(menu.clickMenuButton(p,RetortMenu.recipeToken(selected)),"Valid recipe pin refused");
            var saved=entity.saveWithoutMetadata(h.getLevel().registryAccess());var restored=new RetortBlockEntity(h.absolutePos(MACHINE),h.getLevel().getBlockState(h.absolutePos(MACHINE)));
            restored.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(selected.equals(restored.selectedRecipe()),"Recipe pin lost after loading");
            saved.remove("SelectedRecipe");restored.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(restored.selectedRecipe()==null,"Old block-entity data did not default to AUTO");
            entity.setItem(0,new ItemStack(RealmAgriculture.GRAIN.get(),2));entity.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get()));entity.process();
            h.assertTrue(entity.hasBatch()&&!menu.clickMenuButton(p,RetortMenu.BUTTON_AUTO)&&!menu.clickMenuButton(p,RetortMenu.recipeToken(id("retort_phosphorite_paste")))
                    &&!menu.clickMenuButton(p,RetortMenu.fillButton(id("retort_phosphorite_paste")))&&selected.equals(entity.selectedRecipe())&&entity.preview().getFirst().is(RealmAgriculture.FLOUR.get()),
                    "Controls changed a reserved running recipe or manufactured a new output");h.succeed();
        }finally{TestPlayers.remove(p);}
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void forgedOrRemoteMenuActionsCannotMoveInventory(GameTestHelper h){
        var entity=machine(h);var p=TestPlayers.create(h,new BlockPos(3,2,2),GameType.SURVIVAL);
        try{
            p.getInventory().clearContent();p.getInventory().items.set(0,new ItemStack(RealmAgriculture.GRAIN.get(),2));var menu=menu(entity,p);var before=snapshot(p.getInventory().items);
            int unknown=256;while(RetortMenu.recipeForToken(h.getLevel(),unknown)!=null)unknown++;
            h.assertTrue(!menu.clickMenuButton(p,unknown)&&!menu.clickMenuButton(p,RetortMenu.FILL_BASE+unknown)&&same(before,p.getInventory().items)&&entity.selectedRecipe()==null,
                    "Forged recipe token was accepted or moved inventory");
            p.teleportTo(h.getLevel(),h.absolutePos(MACHINE).getX()+20,h.absolutePos(MACHINE).getY()+1,h.absolutePos(MACHINE).getZ(),Set.of(),0,0);
            h.assertTrue(!menu.clickMenuButton(p,RetortMenu.fillButton(id("retort_grain_separation")))&&same(before,p.getInventory().items)&&entity.getItem(0).isEmpty(),
                    "Out-of-range stale menu can fill a machine");h.succeed();
        }finally{TestPlayers.remove(p);}
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void ingredientFillDoesNotCountOrTakeCoalFromFuelSlot(GameTestHelper h){
        var entity=machine(h);var p=TestPlayers.create(h,new BlockPos(3,2,2),GameType.SURVIVAL);
        try{
            p.getInventory().clearContent();p.getInventory().items.set(0,new ItemStack(Interstice.AEROLITE.get()));entity.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get()));var menu=menu(entity,p);
            h.assertTrue(!menu.clickMenuButton(p,RetortMenu.fillButton(id("retort_umbral_sorbent")))&&entity.getItem(6).getCount()==1&&p.getInventory().items.get(0).getCount()==1,
                    "Automatic ingredient fill reused the fuel item as a chemical reagent");
            p.getInventory().items.set(1,new ItemStack(MineralEcology.UMBRAL_COAL.get()));
            h.assertTrue(menu.clickMenuButton(p,RetortMenu.fillButton(id("retort_umbral_sorbent")))&&entity.getItem(6).getCount()==1,"Ingredient transfer consumed the separate fuel coal");
            for(int i=0;i<400;i++)entity.process();h.assertTrue(entity.getItem(7).is(RealmAgriculture.SORBENT.get())&&entity.getItem(7).getCount()==2&&entity.data.get(2)==2800,"Filled reagent batch used the wrong real quantities");h.succeed();
        }finally{TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void partialRetortDoesNotHideTheNeighboringFloorOrWallFaces(GameTestHelper h){
        var at=h.absolutePos(MACHINE);var level=h.getLevel();
        for(boolean lit:new boolean[]{false,true}){
            h.setBlock(MACHINE,RealmAgriculture.RETORT.get().defaultBlockState().setValue(RetortBlock.LIT,lit));
            var retort=level.getBlockState(at);var stone=net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            level.setBlock(at.below(),stone,3);level.setBlock(at.east(),stone,3);level.setBlock(at.west(),stone,3);
            h.assertTrue(net.minecraft.world.level.block.Block.shouldRenderFace(stone,level,at.below(),net.minecraft.core.Direction.UP,at)
                    &&net.minecraft.world.level.block.Block.shouldRenderFace(stone,level,at.east(),net.minecraft.core.Direction.WEST,at)
                    &&net.minecraft.world.level.block.Block.shouldRenderFace(stone,level,at.west(),net.minecraft.core.Direction.EAST,at),
                    "Retort suppresses solid neighbor faces around its visibly partial model, lit="+lit);
            h.assertTrue(!retort.isCollisionShapeFullBlock(level,at)&&!retort.getShape(level,at).isEmpty(),"Retort shape is a whole invisible cube or lacks its physical machine");
        }h.succeed();
    }
}
