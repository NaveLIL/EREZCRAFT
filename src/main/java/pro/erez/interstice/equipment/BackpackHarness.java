package pro.erez.interstice.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pro.erez.interstice.Interstice;

/** One owned back item, separate from armor and native inventory indices. Server alone may change it. */
@EventBusSubscriber(modid=Interstice.ID)
public final class BackpackHarness {
    public static final int SLOT=41;
    public static final Codec<ItemStack> WORN_CODEC=ItemStack.OPTIONAL_CODEC.validate(stack->valid(stack)
            ?DataResult.success(stack):DataResult.error(()->"A back slot requires one valid own backpack"));
    private BackpackHarness(){}
    public static boolean valid(ItemStack stack){return stack.isEmpty()||BackpackStorage.valid(stack);}
    /** Live object: worn storage leases must invalidate when it is replaced, even by the same UUID. */
    public static ItemStack get(Player player){return player.getData(ExpeditionEquipment.WORN_BACKPACK.get());}
    public static void set(Player player,ItemStack stack){
        if(player.level().isClientSide)throw new IllegalStateException("Only the server may equip a backpack");
        if(!valid(stack))throw new IllegalArgumentException("Invalid worn backpack");
        if(!stack.isEmpty())BackpackStorage.ensureId(stack);
        player.setData(ExpeditionEquipment.WORN_BACKPACK.get(),stack.isEmpty()?ItemStack.EMPTY:stack);changed(player);
    }
    public static void changed(Player player){
        if(player instanceof ServerPlayer server){
            PacketDistributor.sendToPlayer(server,new BackpackNetworking.WearSync(server.getId(),get(server)));
            PacketDistributor.sendToPlayersTrackingEntity(server,new BackpackNetworking.WearSync(server.getId(),visual(get(server))));
        }
    }
    private static ItemStack visual(ItemStack stack){var copy=stack.copy();copy.remove(DataComponents.CONTAINER);copy.remove(DataComponents.CUSTOM_DATA);copy.remove(ExpeditionEquipment.BACKPACK_MODULES.get());return copy;}
    public static void applyClient(Player player,ItemStack stack){
        if(!player.level().isClientSide||!valid(stack))return;
        player.setData(ExpeditionEquipment.WORN_BACKPACK.get(),stack.copy());
    }
    private static boolean canChange(ServerPlayer player){
        return player.isAlive()&&!player.isSpectator()&&player.containerMenu.getCarried().isEmpty()
                &&(player.containerMenu==player.inventoryMenu||player.containerMenu instanceof BackpackMenu||player.containerMenu instanceof HarnessMenu);
    }
    public static boolean equip(ServerPlayer player,int inventorySlot){
        if(inventorySlot<0||inventorySlot>=36&&inventorySlot!=40||!canChange(player))return false;
        var incoming=player.getInventory().getItem(inventorySlot);var previous=get(player);
        if(incoming.isEmpty()||!valid(incoming)||incoming==previous)return false;
        if(player.containerMenu!=player.inventoryMenu)player.closeContainer();
        player.getInventory().setItem(inventorySlot,previous);set(player,incoming);player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();return true;
    }
    public static boolean open(ServerPlayer player){
        if(!canChange(player))return false;
        if(player.containerMenu!=player.inventoryMenu)player.closeContainer();
        player.openMenu(new SimpleMenuProvider((id,inventory,owner)->new HarnessMenu(id,inventory),Component.translatable("menu.interstice.harness.title")));
        changed(player);return player.containerMenu instanceof HarnessMenu;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void drops(LivingDropsEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player)||player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))return;
        var stack=get(player);if(stack.isEmpty())return;
        if(BackpackStorage.capacity(stack)>=84&&event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD))return;
        set(player,ItemStack.EMPTY); // Detach before collection/clone: the same bag has exactly one owner.
        if(EnchantmentHelper.has(stack,net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP))return;
        var dropped=new ItemEntity(player.level(),player.getX(),player.getY()+.1,player.getZ(),stack);dropped.setDefaultPickUpDelay();event.getDrops().add(dropped);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void clone(PlayerEvent.Clone event){
        // NeoForge's NORMAL listener already copied serializable copyOnDeath data into a fresh object.
        // The original entity is retired; removing its reference also invalidates old open storage leases.
        event.getOriginal().removeData(ExpeditionEquipment.WORN_BACKPACK.get());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){changed(event.getEntity());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){changed(event.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){changed(event.getEntity());}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){
        if(event.getEntity() instanceof ServerPlayer viewer&&event.getTarget() instanceof Player target&&viewer!=target)
            PacketDistributor.sendToPlayer(viewer,new BackpackNetworking.WearSync(target.getId(),visual(get(target))));
    }
}
