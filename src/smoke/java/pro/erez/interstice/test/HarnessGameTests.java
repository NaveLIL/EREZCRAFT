package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import pro.erez.interstice.equipment.*;

@GameTestHolder("interstice_equipment") @PrefixGameTestTemplate(false)
public final class HarnessGameTests {
    private static ServerPlayer player(GameTestHelper h){return TestPlayers.create(h,new BlockPos(6,2,6),GameType.SURVIVAL);}
    private static ItemStack pack(){var pack=new ItemStack(ExpeditionEquipment.FIELD_BACKPACK.get());var slots=BackpackStorage.read(pack);var pick=new ItemStack(Items.DIAMOND_PICKAXE);pick.setDamageValue(17);pick.set(DataComponents.CUSTOM_NAME,Component.literal("worn saved tool"));slots.set(53,pick);slots.set(0,new ItemStack(Items.COAL,23));BackpackStorage.write(pack,slots);BackpackStorage.ensureId(pack);return pack;}

    @GameTest(template="empty",timeoutTicks=100)
    public static void equipSwapsRealOwnedObjectsWithoutReplacingArmorAndMenuCanRemove(GameTestHelper h){
        var p=player(h);
        try{
            var first=pack();var second=new ItemStack(ExpeditionEquipment.EXPEDITION_BACKPACK.get());p.getInventory().setItem(0,first);p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
            h.assertTrue(BackpackHarness.equip(p,0)&&BackpackHarness.get(p)==first&&p.getInventory().getItem(0).isEmpty()&&p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),"Equipping copied a bag, failed to remove its held source or replaced armor");
            p.getInventory().setItem(0,second);h.assertTrue(BackpackHarness.equip(p,0)&&BackpackHarness.get(p)==second&&p.getInventory().getItem(0)==first,"Replacing worn bag does not return the exact previous owned object");
            h.assertTrue(BackpackHarness.open(p)&&p.containerMenu instanceof HarnessMenu,"Own back equipment menu did not open");var menu=(HarnessMenu)p.containerMenu;
            menu.clicked(0,0,ClickType.PICKUP,p);h.assertTrue(BackpackHarness.get(p).isEmpty()&&menu.getCarried()==second,"Native equipment click duplicated or failed to detach worn item");menu.clicked(0,0,ClickType.PICKUP,p);
            h.assertTrue(!BackpackHarness.get(p).isEmpty()&&menu.getCarried().isEmpty()&&p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),"Replacing equipment slot loses cursor item or touches armor");h.succeed();
        }finally{p.closeContainer();TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void wornLeaseInvalidatesOnReplacementAndRejectsStaleWrite(GameTestHelper h){
        var p=player(h);
        try{var worn=pack();BackpackHarness.set(p,worn);h.assertTrue(BackpackItem.open(p,BackpackHarness.SLOT),"Worn bag did not open through standard backpack API");var menu=(BackpackMenu)p.containerMenu;
            var replacement=worn.copy();BackpackHarness.set(p,replacement);h.assertTrue(!menu.stillValid(p),"Same UUID copy leaves a stale worn lease live");
            ((BackpackInventory)menu.container).setItem(0,new ItemStack(Items.DIAMOND,64));h.assertTrue(BackpackStorage.read(replacement).getFirst().is(Items.COAL)&&BackpackStorage.read(replacement).getFirst().getCount()==23,"Invalidated worn storage writes its stale snapshot into another source");h.succeed();
        }finally{p.closeContainer();TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void serializedPlayerAttachmentAndOptionalSyncKeepFullContentsAndRemoval(GameTestHelper h){
        var p=player(h);
        try{
            var worn=pack();BackpackHarness.set(p,worn);var saved=p.serializeAttachments(h.getLevel().registryAccess());
            var encoded=saved.get("interstice:worn_backpack");var decoded=BackpackHarness.WORN_CODEC.parse(h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),encoded).getOrThrow();
            h.assertTrue(ItemStack.matches(worn,decoded)&&decoded!=worn&&BackpackStorage.read(decoded).get(53).getDamageValue()==17,"Actual attachment serializer lost object independence or sparse item components");
            var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try{BackpackNetworking.WearSync.STREAM.encode(buffer,new BackpackNetworking.WearSync(p.getId(),worn));var packet=BackpackNetworking.WearSync.STREAM.decode(buffer);
                h.assertTrue(packet.entityId()==p.getId()&&ItemStack.matches(packet.stack(),worn),"Worn synchronization loses owned item data");
                BackpackNetworking.WearSync.STREAM.encode(buffer,new BackpackNetworking.WearSync(p.getId(),ItemStack.EMPTY));h.assertTrue(BackpackNetworking.WearSync.STREAM.decode(buffer).stack().isEmpty(),"Optional synchronization cannot represent removing a bag");
            }finally{buffer.release();}h.succeed();
        }finally{p.closeContainer();TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void invalidNestedOvercountAndCursorEquipmentRequestsCannotChangeWear(GameTestHelper h){
        var p=player(h);
        try{
            var worn=pack();BackpackHarness.set(p,worn);p.getInventory().setItem(0,new ItemStack(Items.CHEST));h.assertTrue(!BackpackHarness.equip(p,0)&&BackpackHarness.get(p)==worn,"Foreign storage item can equip in the owned back slot");
            var over=new ItemStack(ExpeditionEquipment.FIELD_BACKPACK.get(),2);p.getInventory().setItem(0,over);h.assertTrue(!BackpackHarness.equip(p,0)&&p.getInventory().getItem(0).getCount()==2,"Overcount stack bypasses the one bag contract");
            var nested=new ItemStack(ExpeditionEquipment.FIELD_BACKPACK.get());var slots=new ArrayList<ItemStack>(Collections.nCopies(54,ItemStack.EMPTY));slots.set(0,new ItemStack(Items.SHULKER_BOX));nested.set(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.fromItems(slots));p.getInventory().setItem(0,nested);
            h.assertTrue(!BackpackHarness.equip(p,0)&&BackpackHarness.get(p)==worn,"Malformed nested backpack can enter the equipment slot");
            p.getInventory().setItem(0,new ItemStack(ExpeditionEquipment.EXPEDITION_BACKPACK.get()));p.containerMenu.setCarried(new ItemStack(Items.DIAMOND));
            h.assertTrue(!BackpackHarness.equip(p,0)&&!BackpackHarness.open(p)&&p.containerMenu.getCarried().is(Items.DIAMOND)&&BackpackHarness.get(p)==worn,"Equipment convenience overwrites a carried cursor item");p.containerMenu.setCarried(ItemStack.EMPTY);h.succeed();
        }finally{p.closeContainer();TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void actualNormalDeathDropsOneBagAndCloneCannotKeepAnother(GameTestHelper h){
        var p=player(h);var copy=player(h);boolean old=h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        try{
            h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false,h.getLevel().getServer());var worn=pack();var uuid=BackpackStorage.id(worn);BackpackHarness.set(p,worn);p.setHealth(0);p.die(h.getLevel().damageSources().genericKill());
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p.blockPosition()).inflate(4),e->BackpackStorage.isPack(e.getItem())&&uuid.equals(BackpackStorage.id(e.getItem())));
            h.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==1&&BackpackHarness.get(p).isEmpty()&&BackpackStorage.read(drops.getFirst().getItem()).getFirst().getCount()==23,"Actual death duplicated equipped storage or lost its contents");
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(copy,p,true));h.assertTrue(BackpackHarness.get(copy).isEmpty()&&BackpackHarness.get(p).isEmpty(),"Death clone retained a second worn item after its real drop");h.succeed();
        }finally{h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(old,h.getLevel().getServer());p.closeContainer();TestPlayers.remove(p);TestPlayers.remove(copy);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void keepInventoryDeathCopiesOnceAndRetiresOriginalReference(GameTestHelper h){
        var p=player(h);var copy=player(h);boolean old=h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        try{
            h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,h.getLevel().getServer());var worn=pack();var uuid=BackpackStorage.id(worn);BackpackHarness.set(p,worn);p.setHealth(0);p.die(h.getLevel().damageSources().genericKill());
            h.assertTrue(BackpackHarness.get(p)==worn&&h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p.blockPosition()).inflate(4),e->BackpackStorage.isPack(e.getItem())&&uuid.equals(BackpackStorage.id(e.getItem()))).isEmpty(),"keepInventory still dropped the attached bag");
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(copy,p,true));var kept=BackpackHarness.get(copy);
            h.assertTrue(!kept.isEmpty()&&kept!=worn&&uuid.equals(BackpackStorage.id(kept))&&BackpackHarness.get(p).isEmpty()&&BackpackStorage.read(kept).get(53).getDamageValue()==17,"Clone did not retain one independent complete item while retiring original");h.succeed();
        }finally{h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(old,h.getLevel().getServer());p.closeContainer();TestPlayers.remove(p);TestPlayers.remove(copy);}
    }
}
