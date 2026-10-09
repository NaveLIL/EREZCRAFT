package pro.erez.interstice.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EchoShardItem extends Item {
    public EchoShardItem(Properties properties) {
        super(properties.rarity(Rarity.RARE).stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            // Shatter shard to reset fall distance and apply spatial slip
            player.resetFallDistance();

            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(look.x * 0.5, 0.4, look.z * 0.5);
            player.hasImpulse = true;
            player.hurtMarked = true; // Send the slip velocity to this actual player, not only observers.

            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                        20, 0.3, 0.3, 0.3, 0.1);
                sl.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(),
                        1, 0, 0, 0, 0);
                sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4F, 1.8F);
                sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.6F);
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(this, 60); // 3 seconds cooldown
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
