package pro.erez.interstice.vehicle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

public final class EitanVehicles {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,Interstice.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE,Interstice.ID);
    private static final DeferredRegister<EntityType<?>> ACTORS=DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE,Interstice.ID);
    public static final DeferredHolder<Block,EitanBlock> PART=BLOCKS.register("eitan_part",EitanBlock::new);
    public static final DeferredHolder<Block,EitanBlock.Control> CONTROL=BLOCKS.register("eitan_controller",EitanBlock.Control::new);
    public static final DeferredHolder<Block,EitanWheelBlock> WHEEL=BLOCKS.register("eitan_wheel_mount",EitanWheelBlock::new);
    public static final DeferredHolder<Item,Item> TIRE=ITEMS.register("eitan_tire",()->new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item,EitanBlueprintItem> BLUEPRINT=ITEMS.register("eitan_blueprint",EitanBlueprintItem::new);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<EitanController>> CONTROL_ENTITY=ENTITIES.register("eitan_controller",()->BlockEntityType.Builder.of(EitanController::new,CONTROL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<EitanWheelEntity>> WHEEL_ENTITY=ENTITIES.register("eitan_wheel",()->BlockEntityType.Builder.of(EitanWheelEntity::new,WHEEL.get()).build(null));
    public static final DeferredHolder<EntityType<?>,EntityType<EitanSeatEntity>> SEAT_ENTITY=ACTORS.register("eitan_seat",()->EntityType.Builder.<EitanSeatEntity>of(EitanSeatEntity::new,MobCategory.MISC).sized(.5f,.5f).clientTrackingRange(10).updateInterval(1).build("eitan_seat"));
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);ACTORS.register(bus);bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey().location().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"fluids")))e.accept(BLUEPRINT.get());});bus.addListener(EitanNetwork::register);if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())pro.erez.interstice.client.EitanClient.register(bus);}
    public static net.minecraft.world.level.block.state.BlockState state(EitanData.Cell c){if(c.pos().equals(EitanData.CONTROL))return CONTROL.get().defaultBlockState();for(var m:EitanData.MOUNTS)if(c.pos().equals(m.pos()))return WHEEL.get().defaultBlockState().setValue(EitanWheelBlock.SLOT,m.slot()).setValue(dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlock.HORIZONTAL_FACING,m.side()<0?net.minecraft.core.Direction.WEST:net.minecraft.core.Direction.EAST);return PART.get().defaultBlockState().setValue(EitanBlock.CELL,c.id());}
    private EitanVehicles(){}
}
