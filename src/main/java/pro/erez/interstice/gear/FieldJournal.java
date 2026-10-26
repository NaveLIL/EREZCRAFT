package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** Read-only, locally translated handbook. Opening refreshes server-owned advancement progress. */
public final class FieldJournal extends Item {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Interstice.ID);
    public static final DeferredHolder<Item, FieldJournal> JOURNAL = ITEMS.register("field_journal", FieldJournal::new);
    public static final List<String> MILESTONES = List.of("rift_discovery", "mine_riftsilver", "smelt_riftsilver",
            "trials/first_tools", "trials/silver_suit", "trials/return_home", "trials/stable_portal", "trials/tide_indicator",
            "trials/survive_tide", "trials/retort", "trials/clean_lining", "trials/clean_food", "trials/field_pack",
            "trials/surveyor", "trials/first_spider", "trials/all_spiders", "trials/vault_hunter", "trials/upper_ghast",
            "trials/tide_phantom", "trials/riftsteel", "trials/riftsteel_tools", "trials/riftsteel_suit", "trials/master");
    public FieldJournal() { super(new Item.Properties().stacksTo(1)); }
    public static void register(IEventBus bus) { ITEMS.register(bus); }
    public static ItemStack create(ServerPlayer player) { var stack = new ItemStack(JOURNAL.get()); refresh(stack, player); return stack; }
    public static void refresh(ItemStack stack, ServerPlayer player) {
        var pages = new ArrayList<Filterable<Component>>();
        long done = MILESTONES.stream().filter(id -> ExpeditionTrials.completed(player, id)).count();
        pages.add(Filterable.passThrough(Component.translatable("journal.interstice.overview", done, MILESTONES.size())));
        for (String key : List.of("arrival", "shelter", "silver", "portal", "chemistry", "equipment", "hunting", "steel", "armour", "completion"))
            pages.add(Filterable.passThrough(Component.translatable("journal.interstice." + key)));
        for (int start = 0; start < MILESTONES.size(); start += 5) {
            var page = Component.translatable("journal.interstice.checklist").append("\n\n");
            for (int i = start; i < Math.min(start + 5, MILESTONES.size()); i++) {
                String id = MILESTONES.get(i);
                page.append(ExpeditionTrials.completed(player, id) ? "✓ " : "□ ")
                        .append(Component.translatable("advancements.interstice." + id.replace("trials/", "") + ".title")).append("\n");
            }
            pages.add(Filterable.passThrough(page));
        }
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Междуморье"), "EREZ Dev", 0, pages, true));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            refresh(stack, serverPlayer); serverPlayer.inventoryMenu.broadcastChanges();
            serverPlayer.connection.send(new ClientboundOpenBookPacket(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flags) {
        text.add(Component.translatable("tooltip.interstice.field_journal"));
    }
}
