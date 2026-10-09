package pro.erez.interstice.equipment;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

public final class BackpackItem extends Item {
    private final int capacity;

    public BackpackItem(int capacity) {
        super(capacity > 54 ? new Properties().stacksTo(1).fireResistant() : new Properties().stacksTo(1));
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public int moduleSlots() {
        return BackpackStorage.moduleSlots(capacity);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return false;
    }

    @Override public boolean canBeHurtBy(ItemStack stack,net.minecraft.world.damagesource.DamageSource source){
        return capacity<84||(!source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                &&!source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            int slot = hand == InteractionHand.OFF_HAND ? 40 : player.getInventory().selected;
            if (player.isShiftKeyDown()) BackpackHarness.equip(server, slot);
            else open(server, slot);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    public static boolean open(ServerPlayer player, int slot) {
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()
                || !player.isAlive() || player.isSpectator() || slot < 0 || slot >= 36 && slot != 40 && slot != BackpackHarness.SLOT) {
            return false;
        }
        var stack = BackpackStorage.stack(player, slot);
        if (!BackpackStorage.valid(stack)) {
            player.displayClientMessage(Component.translatable("message.interstice.backpack.invalid"), true);
            return false;
        }
        BackpackStorage.ensureId(stack);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (slot == BackpackHarness.SLOT) BackpackHarness.changed(player);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BackpackMenu(id, inventory, slot), stack.getHoverName()), buffer -> {
            buffer.writeVarInt(slot);
            buffer.writeVarInt(capacityOf(stack));
        });
        return true;
    }

    private static int capacityOf(ItemStack stack) {
        return BackpackStorage.capacity(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) {
        text.add(Component.translatable("tooltip.interstice.backpack.capacity", capacity));
        var contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        int occupied = (int) contents.nonEmptyStream().count();
        text.add(Component.translatable("tooltip.interstice.backpack.used", occupied));
        text.add(Component.translatable("tooltip.interstice.backpack.open"));
        text.add(Component.translatable("tooltip.interstice.backpack.mode." + BackpackStorage.mode(stack)));
        int modSlots = BackpackStorage.moduleSlots(capacity);
        var modules = stack.getOrDefault(ExpeditionEquipment.BACKPACK_MODULES.get(), ItemContainerContents.EMPTY);
        int installed = (int) modules.nonEmptyStream().count();
        text.add(Component.translatable("tooltip.interstice.backpack.modules", installed, modSlots));
        if (capacity >= 84) {
            text.add(Component.translatable("tooltip.interstice.backpack.void_resistant"));
            text.add(Component.translatable("tooltip.interstice.backpack.blast_resistant"));
        }
        if (capacity > 54) {
            text.add(Component.translatable("tooltip.interstice.backpack.fire"));
        }
        if (!BackpackStorage.valid(stack)) {
            text.add(Component.translatable("message.interstice.backpack.invalid"));
        }
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        if (entity.level().isClientSide) return;
        var stack = entity.getItem();
        var stored = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        var copies = new ArrayList<>(stored.nonEmptyStream().toList());
        var modules = stack.getOrDefault(ExpeditionEquipment.BACKPACK_MODULES.get(), ItemContainerContents.EMPTY);
        copies.addAll(modules.nonEmptyStream().toList());
        stack.remove(DataComponents.CONTAINER);
        stack.remove(ExpeditionEquipment.BACKPACK_MODULES.get());
        ItemUtils.onContainerDestroyed(entity, copies);
    }
}
