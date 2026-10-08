package pro.erez.interstice.equipment;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import pro.erez.interstice.Interstice;

public final class BackpackPickup {
    public static final TagKey<Item> MATERIALS=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"backpack_materials"));
    public static void collect(ItemEntityPickupEvent.Pre event){
        var player=event.getPlayer();var entity=event.getItemEntity();
        if(event.canPickup()==TriState.FALSE||!player.isAlive()||player.isSpectator()||!entity.isAlive()||entity.hasPickUpDelay()||entity.getTarget()!=null&&!entity.getTarget().equals(player.getUUID()))return;
        int slot=BackpackStorage.activeSlot(player);if(slot<0)return;var pack=BackpackStorage.stack(player,slot);if(!BackpackStorage.valid(pack))return;
        int mode=BackpackStorage.mode(pack);if(mode==BackpackStorage.OFF)return;var source=entity.getItem();if(source.isEmpty()||!BackpackStorage.allowed(source))return;
        var items=BackpackStorage.read(pack);boolean matching=items.stream().anyMatch(at->!at.isEmpty()&&ItemStack.isSameItemSameComponents(at,source));
        if(!matching&&(mode!=BackpackStorage.MATERIALS||!source.is(MATERIALS)))return;
        int accepted;
        if(player.containerMenu instanceof BackpackMenu menu&&menu.container instanceof BackpackInventory live&&live.bound())accepted=live.insert(source);
        else{accepted=BackpackStorage.insert(items,source);if(accepted>0)BackpackStorage.write(pack,items);}
        if(accepted==0)return;if(slot==BackpackHarness.SLOT)BackpackHarness.changed(player);var picked=source.getItem();player.take(entity,accepted);player.awardStat(Stats.ITEM_PICKED_UP.get(picked),accepted);player.onItemPickup(entity);source.shrink(accepted);player.getInventory().setChanged();
        if(source.isEmpty()){entity.discard();event.setCanPickup(TriState.FALSE);}
    }
    private BackpackPickup(){}
}
