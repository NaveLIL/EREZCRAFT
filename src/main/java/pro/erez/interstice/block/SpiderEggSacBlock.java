package pro.erez.interstice.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;

public final class SpiderEggSacBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape SHAPE_UP = Block.box(1.0, 0.0, 1.0, 15.0, 12.0, 15.0);
    private static final VoxelShape SHAPE_DOWN = Block.box(1.0, 4.0, 1.0, 15.0, 16.0, 15.0);
    private static final VoxelShape SHAPE_NORTH = Block.box(1.0, 1.0, 4.0, 15.0, 15.0, 16.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(1.0, 1.0, 0.0, 15.0, 15.0, 12.0);
    private static final VoxelShape SHAPE_WEST = Block.box(4.0, 1.0, 1.0, 16.0, 15.0, 15.0);
    private static final VoxelShape SHAPE_EAST = Block.box(0.0, 1.0, 1.0, 12.0, 15.0, 15.0);

    public SpiderEggSacBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(0.8F)
                .sound(SoundType.SLIME_BLOCK));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_UP;
        };
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            boolean silkTouch = EnchantmentHelper.hasTag(player.getMainHandItem(), net.minecraft.tags.EnchantmentTags.PREVENTS_DECORATED_POT_SHATTERING);
            if (!silkTouch) {
                burstEggSac((ServerLevel) level, pos, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public static void burstEggSac(ServerLevel level, BlockPos pos, Player cause) {
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
        level.playSound(null, pos, SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 1.2F, 1.6F);

        level.sendParticles(ParticleTypes.ITEM_SLIME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                24, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.SNEEZE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                16, 0.2, 0.2, 0.2, 0.05);

        // Toxic effect to surrounding entities
        var box = new AABB(pos).inflate(3.0);
        var victims = level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof CaveRiftSpiderEntity));
        for (var victim : victims) {
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), cause);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), cause);
        }

        // Spawn 1 to 2 skirmisher spider defenders
        int count = 1 + level.getRandom().nextInt(2);
        for (int i = 0; i < count; i++) {
            CaveRiftSpiderEntity spider = new CaveRiftSpiderEntity(Interstice.CAVE_RIFT_SPIDER.get(), level);
            spider.moveTo(pos.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.4,
                    pos.getY() + 0.1,
                    pos.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.4,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            spider.setVariant(CaveRiftSpiderEntity.Variant.SKIRMISHER);
            if (cause != null && !cause.isCreative() && !cause.isSpectator()) {
                spider.setTarget(cause);
            }
            level.addFreshEntity(spider);
        }

        // Alert entire hive nearby
        var packBox = new AABB(pos).inflate(24.0);
        var hive = level.getEntitiesOfClass(CaveRiftSpiderEntity.class, packBox, s -> s.isAlive());
        for (var spider : hive) {
            if (cause != null && !cause.isCreative() && !cause.isSpectator()) {
                spider.setTarget(cause);
                if (spider.getVariant() == CaveRiftSpiderEntity.Variant.LURKER) {
                    spider.triggerAmbushBurst();
                }
            }
        }
    }
}
