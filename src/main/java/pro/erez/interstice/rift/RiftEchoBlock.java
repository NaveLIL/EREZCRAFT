package pro.erez.interstice.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The visible return endpoint is generated beside the landing shelter, with no harvestable portal materials. */
public final class RiftEchoBlock extends Block {
    public RiftEchoBlock() { super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(4, 1200).noOcclusion().noLootTable().lightLevel(s -> 12).sound(SoundType.AMETHYST)); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(3, 0, 3, 13, 14, 13); }
    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) RiftTravel.returnThroughEcho(serverPlayer, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.GLOW, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5, (random.nextDouble() - .5) * .03, .04, (random.nextDouble() - .5) * .03);
    }
}
