package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;

/** Physical, nonstacking modules in the native immutable container component. */
public final class EngineerModules {
    public static final int UNIT=1000,CAPACITY=1800*UNIT,AIR_TICKS=600*20;
    public static final String ENERGY="interstice_engineer_energy_milli",AIR="interstice_engineer_air_ticks";
    private EngineerModules(){}
    public static CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    public static void data(ItemStack stack,CompoundTag tag){stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
    public static boolean cell(ItemStack stack){return stack.is(EngineerArmor.CELL.get())&&stack.getCount()==1;}
    public static boolean cassette(ItemStack stack){return stack.is(EngineerArmor.CASSETTE.get())&&stack.getCount()==1;}
    public static int energy(ItemStack stack){return cell(stack)?Math.clamp(data(stack).getInt(ENERGY),0,CAPACITY):0;}
    public static void energy(ItemStack stack,int value){if(!cell(stack))throw new IllegalArgumentException("One physical cell required");var tag=data(stack);tag.putInt(ENERGY,Math.clamp(value,0,CAPACITY));data(stack,tag);}
    public static int reserve(ItemStack stack){if(!cassette(stack))return 0;var tag=data(stack);return tag.contains(AIR)?Math.clamp(tag.getInt(AIR),0,AIR_TICKS):AIR_TICKS;}
    public static void reserve(ItemStack stack,int value){if(!cassette(stack))throw new IllegalArgumentException("One physical cassette required");var tag=data(stack);tag.putInt(AIR,Math.clamp(value,0,AIR_TICKS));data(stack,tag);}
    public static int slots(ItemStack armor){return armor.getItem() instanceof EngineerArmor.Piece p?switch(p.getEquipmentSlot()){case HEAD->1;case CHEST->2;default->0;}:0;}
    public static boolean allowed(ItemStack armor,int slot,ItemStack module){
        if(slot<0||slot>=slots(armor))return false;if(module.isEmpty())return true;
        if(module.getCount()!=1||module.has(DataComponents.CONTAINER)||module.has(DataComponents.BUNDLE_CONTENTS))return false;
        var p=(EngineerArmor.Piece)armor.getItem();return p.getEquipmentSlot()==EquipmentSlot.HEAD?slot==0&&cell(module):slot==0?cassette(module):module.is(RealmGear.BALLAST_BELT.get());
    }
    public static boolean valid(ItemStack armor){
        int count=slots(armor);if(count==0||armor.getCount()!=1)return false;var contents=armor.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);
        if(contents.getSlots()>count)return false;var list=NonNullList.withSize(count,ItemStack.EMPTY);contents.copyInto(list);
        for(int i=0;i<count;i++)if(!allowed(armor,i,list.get(i)))return false;return true;
    }
    public static ItemStack get(ItemStack armor,int slot){
        if(!valid(armor)||slot<0||slot>=slots(armor))return ItemStack.EMPTY;
        var list=NonNullList.withSize(slots(armor),ItemStack.EMPTY);armor.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyInto(list);return list.get(slot);
    }
    public static void set(ItemStack armor,int slot,ItemStack module){
        if(!valid(armor)||!allowed(armor,slot,module))throw new IllegalArgumentException("Invalid engineer socket contents");
        var list=NonNullList.withSize(slots(armor),ItemStack.EMPTY);armor.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyInto(list);
        list.set(slot,module.copy());armor.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(list));
    }
    public static boolean install(Player player,ItemStack module){
        boolean power=cell(module);if(!power&&!cassette(module))return false;
        var slot=power?EquipmentSlot.HEAD:EquipmentSlot.CHEST;var armor=player.getItemBySlot(slot);
        if(!EngineerArmor.worn(armor,slot)||!valid(armor)||!get(armor,0).isEmpty())return false;
        // Move one real object, including all its data, without copying a free spare into inventory.
        set(armor,0,module.copyWithCount(1));module.shrink(1);player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();return true;
    }
    public static final class ModuleItem extends Item {
        private final boolean power;
        ModuleItem(boolean power){super(new Properties().stacksTo(1));this.power=power;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var stack=player.getItemInHand(hand);if(!level.isClientSide&&!install(player,stack)){player.displayClientMessage(Component.translatable("message.interstice.engineer.socket"),true);return InteractionResultHolder.fail(stack);}
            return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flag){text.add(Component.translatable(power?"tooltip.interstice.engineer.cell":"tooltip.interstice.engineer.air",power?energy(stack)/UNIT:reserve(stack)/20));text.add(Component.translatable("tooltip.interstice.engineer.install"));}
    }
}
