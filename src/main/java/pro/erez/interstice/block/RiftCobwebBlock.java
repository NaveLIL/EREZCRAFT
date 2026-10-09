package pro.erez.interstice.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;

public final class RiftCobwebBlock extends Block {
    public RiftCobwebBlock() {
        super(BlockBehaviour.Properties.of()
                .noCollission()
                .strength(4.0F)
                .sound(SoundType.COBWEB)
                .forceSolidOn());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof CaveRiftSpiderEntity) {
            // Native cave spiders navigate their webs freely
            return;
        }

        entity.makeStuckInBlock(state, new Vec3(0.20, 0.05, 0.20));
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.20, 0.05, 0.20));

        if (!level.isClientSide && entity instanceof Player player && !player.isCreative() && !player.isSpectator()) {
            if (player.tickCount % 20 == 0) {
                // Sense prey vibration in web and alert nearby spiders
                var box = player.getBoundingBox().inflate(16.0);
                var spiders = level.getEntitiesOfClass(CaveRiftSpiderEntity.class, box, s -> s.isAlive() && s.getTarget() == null);
                for (var spider : spiders) {
                    spider.setTarget(player);
                    if (spider.getVariant() == CaveRiftSpiderEntity.Variant.LURKER) {
                        spider.triggerAmbushBurst();
                    }
                }
            }
        }
    }
}
