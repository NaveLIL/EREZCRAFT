package pro.erez.interstice.ecology;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** Additional native forest species. Existing tide sprouts, caves and clingweed keep their contracts. */
public final class RealmEcology {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,Interstice.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(BuiltInRegistries.SOUND_EVENT,Interstice.ID);
    public static final DeferredHolder<Block,VenomReedBlock> VENOM_REED=BLOCKS.register("venom_reed",VenomReedBlock::new);
    public static final DeferredHolder<Block,SporePodBlock> SPORE_POD=BLOCKS.register("spore_pod",SporePodBlock::new);
    public static final DeferredHolder<Item,Item> VENOM_FIBER=ITEMS.register("venom_fiber",()->new Item(new Item.Properties()));
    public static final DeferredHolder<Item,BlockItem> VENOM_REED_ITEM=ITEMS.register("venom_reed",()->new BlockItem(VENOM_REED.get(),new Item.Properties()));
    public static final DeferredHolder<Item,BlockItem> POD_SHELL=ITEMS.register("spore_pod_shell",()->new BlockItem(SPORE_POD.get(),new Item.Properties()){
        @Override public String getDescriptionId(){return "item.interstice.spore_pod_shell";}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,java.util.List<Component> text,TooltipFlag flags){text.add(Component.translatable("tooltip.interstice.spore_pod_shell"));}
    });
    public static final DeferredHolder<SoundEvent,SoundEvent> POD_WARNING=SOUNDS.register("spore_pod_warning",()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"spore_pod_warning")));
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);SOUNDS.register(bus);}
    public static void displayItems(CreativeModeTab.Output output){output.accept(VENOM_REED_ITEM.get());output.accept(VENOM_FIBER.get());output.accept(POD_SHELL.get());}
    private RealmEcology(){}
}
