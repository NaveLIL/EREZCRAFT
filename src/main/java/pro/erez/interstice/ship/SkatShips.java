package pro.erez.interstice.ship;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** Loaded only with the pinned Create/Aeronautics/Sable stack. */
public final class SkatShips {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,Interstice.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE,Interstice.ID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(BuiltInRegistries.MENU,Interstice.ID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<SkatCargoMenu>> CARGO_MENU=MENUS.register("skat_cargo",()->new net.minecraft.world.inventory.MenuType<>(SkatCargoMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    private static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ACTORS=DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE,Interstice.ID);
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<SkatSeatEntity>> SEAT_ACTOR=ACTORS.register("skat_seat",()->net.minecraft.world.entity.EntityType.Builder.<SkatSeatEntity>of(SkatSeatEntity::new,net.minecraft.world.entity.MobCategory.MISC).sized(.5F,.5F).clientTrackingRange(10).updateInterval(1).build("skat_seat"));
    public static final DeferredHolder<Block,SkatBlock> PART=BLOCKS.register("skat_part",SkatBlock::new);
    public static final DeferredHolder<Block,SkatLampBlock> BOARDING=BLOCKS.register("skat_boarding",SkatLampBlock::new);
    public static final DeferredHolder<Block,SkatLampBlock> LAMP=BLOCKS.register("skat_lamp",SkatLampBlock::new);
    public static final DeferredHolder<Block,SkatContainerBlock> CARGO=BLOCKS.register("skat_cargo",()->new SkatContainerBlock(false));
    public static final DeferredHolder<Block,SkatContainerBlock> HELM=BLOCKS.register("skat_helm",()->new SkatContainerBlock(true));
    public static final DeferredHolder<Block,SkatSeatBlock> SEAT=BLOCKS.register("skat_seat",SkatSeatBlock::new);
    public static final DeferredHolder<Block,SkatOpeningBlock> OPENING=BLOCKS.register("skat_opening",SkatOpeningBlock::new);
    public static final DeferredHolder<Block,SkatPropellerBlock> PROPELLER=BLOCKS.register("skat_propeller",SkatPropellerBlock::new);
    public static final DeferredHolder<Block,SkatEngineBlock> ENGINE=BLOCKS.register("skat_engine",SkatEngineBlock::new);
    public static final DeferredHolder<Item,SkatBlueprintItem> BLUEPRINT=ITEMS.register("skat_blueprint",SkatBlueprintItem::new);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<SkatCargoEntity>> CARGO_ENTITY=ENTITIES.register("skat_cargo",()->BlockEntityType.Builder.of(SkatCargoEntity::new,CARGO.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<SkatHelmEntity>> HELM_ENTITY=ENTITIES.register("skat_helm",()->BlockEntityType.Builder.of(SkatHelmEntity::new,HELM.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<SkatPropellerEntity>> PROPELLER_ENTITY=ENTITIES.register("skat_propeller",()->BlockEntityType.Builder.of(SkatPropellerEntity::new,PROPELLER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<SkatEngineEntity>> ENGINE_ENTITY=ENTITIES.register("skat_engine",()->BlockEntityType.Builder.of(SkatEngineEntity::new,ENGINE.get()).build(null));
    public static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);ACTORS.register(bus);MENUS.register(bus);bus.addListener(SkatNetwork::register);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e)->e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,CARGO_ENTITY.get(),(entity,side)->new net.neoforged.neoforge.items.wrapper.InvWrapper(entity)));
        if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())pro.erez.interstice.client.SkatClient.register(bus);
    }
    public static BlockState state(SkatData.Cell c){
        var block=switch(c.role()){case "cargo"->CARGO.get();case "helm"->HELM.get();case "opening"->OPENING.get();case "seat"->SEAT.get();case "boarding"->BOARDING.get();case "lamp"->LAMP.get();case "propeller"->PROPELLER.get();case "engine"->ENGINE.get();default->PART.get();};
        var s=block.defaultBlockState();if(s.hasProperty(SkatData.CELL))s=s.setValue(SkatData.CELL,c.id());
        if(s.hasProperty(SkatPropellerBlock.PORT))s=s.setValue(SkatPropellerBlock.PORT,c.pos().getX()<0);
        if(s.hasProperty(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING))s=s.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING,net.minecraft.core.Direction.SOUTH);
        return s;
    }
    private SkatShips(){}
}
