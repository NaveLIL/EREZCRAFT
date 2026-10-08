package pro.erez.interstice.minerals;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.GardenMaterials;
import pro.erez.interstice.worldgen.VaultMaterials;

/** A mineral's host is the exact natural stone it replaced, and survives silk-touch via loot. */
public final class RiftOreBlock extends DropExperienceBlock {
    public enum Host implements StringRepresentable {
        RIFTSTONE("riftstone"), PALESTONE("palestone"), VAULTSTONE("vaultstone"), WEATHERED("weathered_vaultstone"), RIFT_SHALE("rift_shale");
        private final String name;
        Host(String name){this.name=name;}
        @Override public String getSerializedName(){return name;}
        public BlockState stone(){return switch(this){
            case RIFTSTONE->Interstice.RIFTSTONE.get().defaultBlockState();
            case PALESTONE->GardenMaterials.PALESTONE.get().defaultBlockState();
            case VAULTSTONE->VaultMaterials.VAULTSTONE.get().defaultBlockState();
            case RIFT_SHALE->MineralEcology.RIFT_SHALE.get().defaultBlockState();
            case WEATHERED->VaultMaterials.WEATHERED_VAULTSTONE.get().defaultBlockState();};}
        public static Host from(BlockState state){for(Host host:values())if(state.is(host.stone().getBlock()))return host;return null;}
    }
    public static final EnumProperty<Host> HOST=EnumProperty.create("host",Host.class);
    private final IntProvider experience;
    private static final MapCodec<RiftOreBlock> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            IntProvider.codec(0,10).fieldOf("experience").forGetter(b->b.experience),propertiesCodec()).apply(i,RiftOreBlock::new));
    public RiftOreBlock(IntProvider experience,BlockBehaviour.Properties properties){
        super(experience,properties);this.experience=experience;registerDefaultState(stateDefinition.any().setValue(HOST,Host.RIFTSTONE));
    }
    @Override public MapCodec<RiftOreBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HOST);}
}
