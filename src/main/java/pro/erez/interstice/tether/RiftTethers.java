package pro.erez.interstice.tether;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

public final class RiftTethers {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,Interstice.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE,Interstice.ID);
    public static final DeferredHolder<Block,WinchBlock> WINCH=BLOCKS.register("rift_winch",WinchBlock::new);
    public static final DeferredHolder<Item,BlockItem> WINCH_ITEM=ITEMS.register("rift_winch",()->new BlockItem(WINCH.get(),new Item.Properties()));
    public static final DeferredHolder<Item,TetherSpoolItem> TETHER_SPOOL=ITEMS.register("tether_spool",TetherSpoolItem::new);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<WinchBlockEntity>> WINCH_ENTITY=ENTITIES.register("rift_winch",()->BlockEntityType.Builder.of(WinchBlockEntity::new,WINCH.get()).build(null));
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);bus.addListener(TetherNetworking::register);}
    public static void displayItems(CreativeModeTab.Output out){out.accept(WINCH_ITEM.get());out.accept(TETHER_SPOOL.get());}
    private RiftTethers(){}
}
