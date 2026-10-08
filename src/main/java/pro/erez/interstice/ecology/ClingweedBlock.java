package pro.erez.interstice.ecology;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A permeable plant curtain. Passage steals, destruction releases the stash and defensive gas. */
public final class ClingweedBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    public static final String COOLDOWN_TAG = "interstice_clingweed_until";
    public static final int COOLDOWN_TICKS = 400;
    public ClingweedBlock() {
        this(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).noCollission().noOcclusion().strength(.6F)
                .sound(SoundType.VINE).lightLevel(state->2).pushReaction(PushReaction.DESTROY));
    }
    private ClingweedBlock(BlockBehaviour.Properties properties) { super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getClickedFace());}
    @Override public MapCodec<ClingweedBlock> codec() { return simpleCodec(ClingweedBlock::new); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ClingweedBlockEntity(pos, state); }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(80) == 0)
            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + random.nextDouble(),
                    pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, -.005, 0);
    }
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player) || !player.isAlive()
                || player.isCreative() || player.isSpectator() || !(level.getBlockEntity(pos) instanceof ClingweedBlockEntity plant)
                || plant.freePocket() < 0) return;
        long now = server.getServer().overworld().getGameTime();
        CompoundTag persistent = player.getPersistentData();
        CompoundTag data = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        if (data.getLong(COOLDOWN_TAG) > now) return;
        var eligible = new ArrayList<Integer>();
        var inventory = player.getInventory();
        // items contains hotbar and main inventory; worn armor and offhand are separate lists.
        for (int i = 0; i < inventory.items.size(); i++) if (!inventory.items.get(i).isEmpty()) eligible.add(i);
        if (eligible.isEmpty()) return;
        int index = eligible.get(server.random.nextInt(eligible.size()));
        ItemStack source = inventory.items.get(index);
        ItemStack taken = source.copyWithCount(1);
        if (!plant.keepOne(taken)) return;
        source.shrink(1);
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
        data.putLong(COOLDOWN_TAG, now + COOLDOWN_TICKS);
        persistent.put(Player.PERSISTED_NBT_TAG, data);
        player.displayClientMessage(Component.translatable("message.interstice.clingweed.stolen", taken.getHoverName()), true);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        arm(level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        arm(level, pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        wasExploded(level, pos, explosion);
    }
    private static void arm(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ClingweedBlockEntity plant)
            plant.armDestruction(server.getGameTime());
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof ClingweedBlockEntity plant) {
            boolean gas = plant.gasArmed(server.getGameTime());
            plant.release(server);
            if (gas) ClingweedGas.emit(server, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
