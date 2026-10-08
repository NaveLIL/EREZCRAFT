package pro.erez.interstice;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pro.erez.interstice.worldgen.GloomcrownTree;

/** Grows on abyssal turf without sunlight; bone meal triggers a checked, atomic placement. */
public final class GloomcrownSaplingBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<GloomcrownSaplingBlock> CODEC = simpleCodec(GloomcrownSaplingBlock::new);
    private static final VoxelShape SHAPE = box(4, 0, 4, 12, 13, 12);
    public GloomcrownSaplingBlock() {
        this(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollission()
                .instabreak().sound(SoundType.GRASS).randomTicks());
    }
    public GloomcrownSaplingBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public MapCodec<GloomcrownSaplingBlock> codec() { return CODEC; }
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Interstice.ABYSSAL_TURF.get());
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(7) == 0) GloomcrownTree.grow(level, pos, random);
    }
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) { return true; }
    @Override public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) { return true; }
    @Override public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) { GloomcrownTree.grow(level, pos, random); }
}
