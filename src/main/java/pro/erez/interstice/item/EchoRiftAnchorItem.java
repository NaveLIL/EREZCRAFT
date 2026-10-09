package pro.erez.interstice.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.EchoRiftEntity;

public class EchoRiftAnchorItem extends Item {
    public EchoRiftAnchorItem(Properties properties) {
        super(properties.rarity(Rarity.EPIC).stacksTo(4));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos spawnPos = clickedPos.relative(face);

        EchoRiftEntity rift = new EchoRiftEntity(Interstice.ECHO_RIFT.get(), level);
        rift.setPos(spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5);
        level.addFreshEntity(rift);

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player != null && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        level.playSound(null, rift.getX(), rift.getY(), rift.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2F, 1.7F);

        return InteractionResult.CONSUME;
    }
}
