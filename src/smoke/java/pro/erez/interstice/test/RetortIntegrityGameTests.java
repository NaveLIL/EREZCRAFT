package pro.erez.interstice.test;

import java.util.List;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.agriculture.RetortBlockEntity;
import pro.erez.interstice.agriculture.RetortInput;
import pro.erez.interstice.agriculture.RetortMenu;
import pro.erez.interstice.agriculture.RetortRecipe;
import pro.erez.interstice.minerals.MineralEcology;

/** Inventory-facing regression checks complement natural farming and normal retort processing. */
@GameTestHolder("interstice_agriculture") @PrefixGameTestTemplate(false)
public final class RetortIntegrityGameTests {
    private static final BlockPos MACHINE=new BlockPos(3,1,3);
    private static RetortBlockEntity retort(GameTestHelper h){
        h.setBlock(MACHINE,RealmAgriculture.RETORT.get());
        return (RetortBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(MACHINE));
    }
    private static void grain(RetortBlockEntity entity,int count){entity.setItem(0,new ItemStack(RealmAgriculture.GRAIN.get(),count));}
    private static int dropped(GameTestHelper h,net.minecraft.world.item.Item item){
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(MACHINE)).inflate(3),e->e.getItem().is(item))
                .stream().mapToInt(e->e.getItem().getCount()).sum();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void startingRequiresSpaceForEveryResultAndLeavesUnrelatedInputsUntouched(GameTestHelper h){
        var entity=retort(h);grain(entity,3);entity.setItem(5,new ItemStack(Items.DIAMOND,3));
        entity.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get()));
        for(int i=7;i<10;i++)entity.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        entity.process();entity.setItem(9,ItemStack.EMPTY);entity.process();
        h.assertTrue(entity.data.get(3)==3&&entity.data.get(0)==0&&entity.data.get(2)==0
                &&entity.getItem(0).getCount()==3&&entity.getItem(6).getCount()==1,"Full two-result output reserved ingredients or burned fuel before a possible batch");
        entity.setItem(8,ItemStack.EMPTY);
        for(int i=0;i<200;i++)entity.process();
        h.assertTrue(entity.getItem(0).getCount()==1&&entity.getItem(5).is(Items.DIAMOND)&&entity.getItem(5).getCount()==3,
                "Counted processing consumed leftover or unrelated inventory");
        h.assertTrue(entity.getItem(7).is(Items.COBBLESTONE)&&entity.getItem(7).getCount()==64
                &&entity.getItem(8).is(RealmAgriculture.FLOUR.get())&&entity.getItem(8).getCount()==2
                &&entity.getItem(9).is(RealmAgriculture.FIBER.get())&&entity.getItem(9).getCount()==1
                &&entity.data.get(2)==3000,"A fitting atomic batch disturbed previous output or spent the wrong heat");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void ordinaryFuelCannotReserveInputsOrMasqueradeAsSorbentFuel(GameTestHelper h){
        var entity=retort(h);grain(entity,2);
        for(var wrong:List.of(Items.COAL,Items.CHARCOAL,MineralEcology.WORLD_STICK.get())){
            var stack=new ItemStack(wrong);h.assertTrue(!entity.canPlaceItem(6,stack),"Fuel inventory admits an unsupported item");
            entity.setItem(6,stack);entity.process();
            h.assertTrue(entity.getItem(0).getCount()==2&&entity.getItem(6).getCount()==1&&entity.data.get(0)==0
                    &&entity.data.get(2)==0&&entity.data.get(3)==2,"Unsupported fuel spent inputs, progressed a batch or generated heat");
        }h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void previewCannotBeTakenSwappedShiftClickedOrCollected(GameTestHelper h){
        var entity=retort(h);grain(entity,2);
        var player=TestPlayers.create(h,new BlockPos(3,2,2),GameType.SURVIVAL);
        try{
            var menu=(RetortMenu)entity.createMenu(17,player.getInventory(),player);player.containerMenu=menu;menu.broadcastChanges();
            h.assertTrue(menu.getSlot(10).getItem().is(RealmAgriculture.FLOUR.get())&&menu.getSlot(10).getItem().getCount()==2,"Server preview is absent, so ghost extraction was not tested");
            menu.clicked(10,0,ClickType.PICKUP,player);
            h.assertTrue(menu.getCarried().isEmpty()&&menu.quickMoveStack(player,10).isEmpty(),"Preview leaked through pickup or shift-click");
            player.getInventory().setItem(2,new ItemStack(Items.DIAMOND));menu.clicked(10,2,ClickType.SWAP,player);
            h.assertTrue(player.getInventory().getItem(2).is(Items.DIAMOND)&&menu.getSlot(10).getItem().is(RealmAgriculture.FLOUR.get()),"Hotbar swap extracted or overwrote a preview");
            menu.setCarried(new ItemStack(RealmAgriculture.FLOUR.get()));menu.clicked(10,0,ClickType.PICKUP_ALL,player);
            h.assertTrue(menu.getCarried().getCount()==1&&menu.getSlot(10).getItem().getCount()==2
                    &&entity.getItem(0).getCount()==2&&entity.getItem(7).isEmpty(),"Double-click collection extracted preview or consumed an unstarted recipe");
            menu.setCarried(ItemStack.EMPTY);h.succeed();
        }finally{TestPlayers.remove(player);}
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void savedCompletedBlockedBatchDropsItsOutputsExactlyOnce(GameTestHelper h){
        var entity=retort(h);grain(entity,2);entity.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get(),2));entity.process();
        for(int i=7;i<10;i++)entity.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        for(int i=1;i<200;i++)entity.process();
        h.assertTrue(entity.data.get(0)==200&&entity.data.get(3)==3,"Fixture is not a completed output-blocked batch");
        var tag=entity.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored=new RetortBlockEntity(h.absolutePos(MACHINE),h.getLevel().getBlockState(h.absolutePos(MACHINE)));
        restored.loadWithComponents(tag,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);
        int heat=restored.data.get(2);restored.process();h.assertTrue(restored.data.get(2)==heat,"Restored blocked completion burns additional heat");
        h.getLevel().setBlock(h.absolutePos(MACHINE),Blocks.AIR.defaultBlockState(),3);restored.release();
        h.assertTrue(dropped(h,RealmAgriculture.GRAIN.get())==0&&dropped(h,RealmAgriculture.FLOUR.get())==2
                &&dropped(h,RealmAgriculture.FIBER.get())==1&&dropped(h,MineralEcology.UMBRAL_COAL.get())==1
                &&dropped(h,Items.COBBLESTONE)==192,"Completed saved removal duplicated inputs/fuel or lost pending results");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void completedOutputSnapshotKeepsComponentsInsteadOfRebuildingFromRecipe(GameTestHelper h){
        var entity=retort(h);grain(entity,2);entity.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get()));entity.process();
        var tag=entity.saveWithoutMetadata(h.getLevel().registryAccess());
        // A previously saved result may differ from today's recipe after a data-pack reload.
        var outputs=NonNullList.withSize(3,ItemStack.EMPTY);var named=new ItemStack(RealmAgriculture.FLOUR.get(),2);
        named.set(DataComponents.CUSTOM_NAME,Component.literal("previous recipe snapshot"));outputs.set(0,named);outputs.set(1,new ItemStack(RealmAgriculture.FIBER.get()));
        var pending=new net.minecraft.nbt.CompoundTag();ContainerHelper.saveAllItems(pending,outputs,h.getLevel().registryAccess());tag.put("Pending",pending);
        var restored=new RetortBlockEntity(h.absolutePos(MACHINE),h.getLevel().getBlockState(h.absolutePos(MACHINE)));
        restored.loadWithComponents(tag,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);
        for(int i=1;i<200;i++)restored.process();
        h.assertTrue(restored.getItem(7).getCount()==2&&restored.getItem(7).getHoverName().getString().equals("previous recipe snapshot")
                &&restored.getItem(8).getCount()==1&&restored.data.get(2)==3000,"Loaded batch reassembled current recipe or lost saved output components");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void coalAsIngredientAndFuelUsesTwoSeparateItems(GameTestHelper h){
        var entity=retort(h);entity.setItem(0,new ItemStack(MineralEcology.UMBRAL_COAL.get()));entity.setItem(1,new ItemStack(Interstice.AEROLITE.get()));
        entity.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get()));for(int i=0;i<400;i++)entity.process();
        h.assertTrue(entity.getItem(0).isEmpty()&&entity.getItem(1).isEmpty()&&entity.getItem(6).isEmpty()
                &&entity.getItem(7).is(RealmAgriculture.SORBENT.get())&&entity.getItem(7).getCount()==2&&entity.data.get(2)==2800,
                "Sorbent recipe reused the same coal for both consumed ingredient and furnace heat");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void registeredNetworkCodecPreservesCountedInputsAndComponentOutputs(GameTestHelper h){
        var output=new ItemStack(RealmAgriculture.FLOUR.get(),2);output.set(DataComponents.CUSTOM_NAME,Component.literal("recipe stream proof"));
        var recipe=new RetortRecipe(List.of(new RetortRecipe.Input(Ingredient.of(RealmAgriculture.GRAIN.get()),2)),List.of(output,new ItemStack(RealmAgriculture.FIBER.get())),200);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            var codec=RealmAgriculture.RETORT_SERIALIZER.get().streamCodec();codec.encode(buffer,recipe);var decoded=codec.decode(buffer);
            var input=new RetortInput(List.of(new ItemStack(RealmAgriculture.GRAIN.get()),new ItemStack(RealmAgriculture.GRAIN.get()),ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY));
            h.assertTrue(decoded.ticks()==200&&decoded.outputs().size()==2&&decoded.matches(input,h.getLevel())
                    &&ItemStack.isSameItemSameComponents(decoded.outputs().getFirst(),output)&&decoded.outputs().getFirst().getCount()==2
                    &&buffer.readableBytes()==0,"Actual recipe synchronization lost counts, components or payload boundaries");h.succeed();
        }finally{buffer.release();}
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void realNativeMaterialInventoryChangeUnlocksConstructionRecipes(GameTestHelper h){
        var names=List.of("reaction_retort","riftsilver_wire","riftsilver_mesh","reinforced_fence","reinforced_fence_gate",
                "nutrient_reservoir","bioluminescent_lantern","slicing_cultivator","mineral_fertilizer",
                "paleheart_fence_world_sticks","paleheart_fence_gate_world_sticks","crown_fence_world_sticks","crown_fence_gate_world_sticks");
        var player=TestPlayers.create(h,new BlockPos(3,2,2),GameType.SURVIVAL);
        try{
            for(var name:names)h.assertTrue(!player.getRecipeBook().contains(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,name)),
                    "Fresh survival player already knows construction recipe, so discovery was not tested");
            player.getInventory().setItem(2,new ItemStack(MineralEcology.WORLD_STICK.get()));player.inventoryMenu.broadcastChanges();
            for(var name:names)h.assertTrue(player.getRecipeBook().contains(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,name)),
                    "Native material inventory update did not unlock construction recipe: "+name);
            h.assertTrue(!player.getRecipeBook().contains(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"retort_grain_separation")),
                    "Special retort operation was incorrectly inserted into the vanilla recipe book");h.succeed();
        }finally{TestPlayers.remove(player);}
    }
}
