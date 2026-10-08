package pro.erez.interstice.equipment;

import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.block.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.RealmAgriculture;

/** Portable storage uses the native immutable container component, never exposed mutable component references. */
public final class BackpackStorage {
    public static final int OFF=0,MATCHING=1,MATERIALS=2;
    private static final String ID="interstice_pack_id",MODE="interstice_pack_mode";
    private BackpackStorage(){}
    public static boolean isPack(ItemStack stack){return stack.getItem() instanceof BackpackItem;}
    public static ItemStack stack(Player player,int slot){return slot==BackpackHarness.SLOT?BackpackHarness.get(player):player.getInventory().getItem(slot);}
    public static int capacity(ItemStack stack){return stack.getItem() instanceof BackpackItem pack?pack.capacity():0;}
    public static UUID id(ItemStack stack){var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();return tag.hasUUID(ID)?tag.getUUID(ID):null;}
    public static UUID ensureId(ItemStack stack){var id=id(stack);if(id==null){id=UUID.randomUUID();var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putUUID(ID,id);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}return id;}
    public static void renewId(ItemStack stack){var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putUUID(ID,UUID.randomUUID());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
    public static int mode(ItemStack stack){int max=capacity(stack)>54?MATERIALS:MATCHING;return Math.max(0,Math.min(max,stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt(MODE)));}
    public static void mode(ItemStack stack,int value){var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putInt(MODE,Math.max(0,Math.min(capacity(stack)>54?MATERIALS:MATCHING,value)));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
    public static boolean allowed(ItemStack stack){
        if(stack.isEmpty())return true;
        if(isPack(stack)||stack.getItem() instanceof BundleItem||Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock||!stack.canFitInsideContainerItems())return false;
        if(stack.has(DataComponents.CONTAINER)||stack.has(DataComponents.BUNDLE_CONTENTS))return false;
        if(stack.has(DataComponents.BLOCK_ENTITY_DATA)&&!stack.is(RealmAgriculture.RESERVOIR_ITEM.get()))return false;
        // These ordinary buckets corrode through inventoryTick. Storage must not bypass silver containment.
        return !stack.is(Interstice.HEAVY_BUCKET.get())&&!stack.is(Interstice.LIGHT_BUCKET.get());
    }
    public static boolean valid(ItemStack stack){var contents=stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);return isPack(stack)&&stack.getCount()==1&&contents.getSlots()<=capacity(stack)&&contents.nonEmptyStream().allMatch(s->allowed(s)&&s.getCount()<=s.getMaxStackSize());}
    public static NonNullList<ItemStack> read(ItemStack stack){if(!valid(stack))throw new IllegalArgumentException("Invalid backpack contents must not be truncated");var list=NonNullList.withSize(capacity(stack),ItemStack.EMPTY);stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyInto(list);return list;}
    public static void write(ItemStack stack,List<ItemStack> items){if(items.size()!=capacity(stack)||items.stream().anyMatch(s->!allowed(s)||s.getCount()>s.getMaxStackSize()))throw new IllegalArgumentException("Invalid backpack contents");stack.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(items));}
    /** Inserts copies into a mutable transaction list. Does not alter the source item. */
    public static int insert(List<ItemStack> target,ItemStack source){
        if(source.isEmpty()||!allowed(source))return 0;int left=source.getCount();
        for(var at:target)if(!at.isEmpty()&&ItemStack.isSameItemSameComponents(at,source)){int take=Math.min(left,Math.max(0,at.getMaxStackSize()-at.getCount()));at.grow(take);left-=take;if(left==0)return source.getCount();}
        for(int i=0;i<target.size()&&left>0;i++)if(target.get(i).isEmpty()){int take=Math.min(left,source.getMaxStackSize());target.set(i,source.copyWithCount(take));left-=take;}
        return source.getCount()-left;
    }
    public static int activeSlot(Player player){
        if(player.containerMenu instanceof BackpackMenu menu&&menu.stillValid(player))return menu.sourceSlot;
        if(isPack(BackpackHarness.get(player)))return BackpackHarness.SLOT;
        int selected=player.getInventory().selected;
        if(isPack(player.getInventory().getItem(selected)))return selected;
        if(isPack(player.getInventory().getItem(40)))return 40;
        for(int i=0;i<36;i++)if(isPack(player.getInventory().getItem(i)))return i;return -1;
    }
}
