package pro.erez.interstice.ecology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Safe glow has no contact effects; stings are bounded per victim, never per plant cell. */
public final class CavePlantBlock extends Block {
    public static final String STING_COOLDOWN_TAG = "interstice_cave_sting_until";
    private final boolean hanging;
    private final boolean stinging;
    public CavePlantBlock(boolean hanging, boolean stinging) {
        this(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).noCollission().noOcclusion().instabreak()
                .sound(SoundType.ROOTS).lightLevel(state -> stinging ? 0 : 7), hanging, stinging);
    }
    private CavePlantBlock(BlockBehaviour.Properties properties, boolean hanging, boolean stinging) {
        super(properties);
        this.hanging = hanging;
        this.stinging = stinging;
    }
    @Override public MapCodec<CavePlantBlock> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(propertiesCodec(),
                Codec.BOOL.fieldOf("hanging").forGetter(block -> block.hanging),
                Codec.BOOL.fieldOf("stinging").forGetter(block -> block.stinging)).apply(instance, CavePlantBlock::new));
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return hanging ? box(3, 3, 3, 13, 16, 13) : box(2, 0, 2, 14, 13, 14);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos anchor = hanging ? pos.above() : pos.below();
        BlockState support = level.getBlockState(anchor);
        return !support.is(Blocks.BEDROCK) && support.getFluidState().isEmpty()
                && support.isFaceSturdy(level, anchor, hanging ? Direction.DOWN : Direction.UP);
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos other) {
        return direction == (hanging ? Direction.UP : Direction.DOWN) && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!stinging && random.nextInt(24) == 0)
            level.addParticle(ParticleTypes.GLOW, pos.getX() + .25 + random.nextDouble() * .5,
                    pos.getY() + (hanging ? .35 : .65), pos.getZ() + .25 + random.nextDouble() * .5, 0, .006, 0);
    }
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!stinging || level.isClientSide || !(entity instanceof LivingEntity victim) || !victim.isAlive()
                || victim instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        long now = level.getServer().overworld().getGameTime();
        CompoundTag data = victim.getPersistentData();
        if (data.getLong(STING_COOLDOWN_TAG) > now) return;
        data.putLong(STING_COOLDOWN_TAG, now + 40);
        victim.hurt(level.damageSources().sweetBerryBush(), 2F);
        victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60));
    }
}
