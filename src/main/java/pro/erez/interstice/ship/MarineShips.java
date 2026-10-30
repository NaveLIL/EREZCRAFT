package pro.erez.interstice.ship;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** NE01 is opt-in until native whole-vessel acceptance; ordinary releases stay unchanged. */
public final class MarineShips {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,Interstice.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE,Interstice.ID);
    public static final DeferredHolder<Block,MarineBlock> PART=BLOCKS.register("ne01_part",MarineBlock::new);
    public static final DeferredHolder<Block,MarineBlock.Opening> OPENING=BLOCKS.register("ne01_opening",MarineBlock.Opening::new);
    public static final DeferredHolder<Block,MarineMeBlock> TERMINAL=BLOCKS.register("ne01_terminal",()->new MarineMeBlock(MarineMeBlock.Kind.TERMINAL));
    public static final DeferredHolder<Block,MarineMeBlock> DRIVE=BLOCKS.register("ne01_drive",()->new MarineMeBlock(MarineMeBlock.Kind.DRIVE));
    public static final DeferredHolder<Block,MarineMeBlock> GENERATOR=BLOCKS.register("ne01_generator",()->new MarineMeBlock(MarineMeBlock.Kind.GENERATOR));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MarineTerminalEntity>> TERMINAL_ENTITY=ENTITIES.register("ne01_terminal",()->BlockEntityType.Builder.of(MarineTerminalEntity::new,TERMINAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MarineDriveEntity>> DRIVE_ENTITY=ENTITIES.register("ne01_drive",()->BlockEntityType.Builder.of(MarineDriveEntity::new,DRIVE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MarineGeneratorEntity>> GENERATOR_ENTITY=ENTITIES.register("ne01_generator",()->BlockEntityType.Builder.of(MarineGeneratorEntity::new,GENERATOR.get()).build(null));
    public static void register(IEventBus bus){BLOCKS.register(bus);ENTITIES.register(bus);if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())bus.addListener(pro.erez.interstice.client.MarineModels::geometry);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e)->e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,GENERATOR_ENTITY.get(),(entity,side)->entity.getEnergyStorage(side)));
    }
    public static net.minecraft.world.level.block.state.BlockState state(MarineData.Cell c){
        Block block=c.mechanisms().isEmpty()?PART.get():OPENING.get();
        if(Boolean.getBoolean("interstice.ne01MePreview")){
            if(c.pos().equals(MarineMe.CONTROLLER))return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("ae2:controller")).defaultBlockState();
            if(c.pos().equals(MarineMe.GENERATOR))block=GENERATOR.get();
            else if(c.pos().equals(MarineMe.SECOND_DRIVE)||c.pos().equals(MarineMe.TERMINALS.get(5)))block=DRIVE.get();
            else if(MarineMe.TERMINALS.contains(c.pos()))block=TERMINAL.get();
        }
        return block.defaultBlockState().setValue(MarineData.CELL,c.id());
    }
    private MarineShips(){}
}
