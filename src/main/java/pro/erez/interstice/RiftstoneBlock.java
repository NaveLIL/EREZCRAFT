package pro.erez.interstice;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * The base stone of Interstice islands — dark graphite riftstone that replaces
 * vanilla stone throughout the island body. Harder to mine than ordinary stone,
 * visually matches the abyssal colour palette.
 */
public final class RiftstoneBlock extends Block {
    public RiftstoneBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DEEPSLATE)
                .strength(2.0F, 6.0F)
                .sound(SoundType.DEEPSLATE)
                .requiresCorrectToolForDrops());
    }
}
