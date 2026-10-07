package pro.erez.interstice.item;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import pro.erez.interstice.tide.ClientTideState;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideState;

/**
 * Barometer-like portable indicator measuring atmospheric pressure and buoyant tide cycles.
 * Operates reliably both inside the Interstice and across dimensions (e.g. Overworld base preparation).
 */
public final class TideIndicatorItem extends Item {
    public TideIndicatorItem(Properties properties) {
        super(properties);
    }

    public static TideState resolveState(Level level) {
        if (level.isClientSide()) {
            return ClientTideState.get();
        } else if (level.getServer() != null) {
            return TideManager.getState(level.getServer());
        }
        return new TideState(TidePhase.CALM, 0, TidePhase.CALM.minDurationTicks(), 0);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        ItemStack stack = context.getItemInHand();

        // Shift-right-click on a vertical wall face mounts the barometer directly on the wall!
        if (player != null && player.isShiftKeyDown() && face.getAxis().isHorizontal()) {
            BlockPos placePos = clickedPos.relative(face);
            if (level.getBlockState(placePos).canBeReplaced()) {
                if (!level.isClientSide()) {
                    ItemFrame frame = new ItemFrame(level, placePos, face);
                    if (frame.survives()) {
                        ItemStack mounted = stack.copy();
                        mounted.setCount(1);
                        frame.setItem(mounted);
                        level.addFreshEntity(frame);
                        level.playSound(null, placePos, SoundEvents.ITEM_FRAME_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                    } else {
                        frame.discard();
                        return InteractionResult.FAIL;
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        TideState state = resolveState(level);

        long ticksRemaining = Math.max(0, state.phaseDurationTicks() - state.phaseTicksElapsed());
        long secondsRemaining = ticksRemaining / 20;

        ChatFormatting phaseColor = switch (state.phase()) {
            case CALM -> ChatFormatting.GREEN;
            case WARNING -> ChatFormatting.YELLOW;
            case SURGE -> ChatFormatting.RED;
            case EBB -> ChatFormatting.AQUA;
        };

        Component phaseText = Component.translatable("tide.interstice.phase." + state.phase().name().toLowerCase(Locale.ROOT))
                .withStyle(phaseColor, ChatFormatting.BOLD);

        boolean sheltered = ShelterDetector.isSheltered(level, player);
        Component shelterText = sheltered
                ? Component.translatable("tide.interstice.sheltered").withStyle(ChatFormatting.GREEN)
                : Component.translatable("tide.interstice.exposed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        Component message = Component.translatable("tide.interstice.status", phaseText, secondsRemaining)
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(shelterText);

        player.displayClientMessage(message, true);
        player.playSound(SoundEvents.LEVER_CLICK, 0.4F, 1.4F);

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.interstice.tide_indicator.desc").withStyle(ChatFormatting.GRAY));

        TideState state = ClientTideState.get();
        ChatFormatting phaseColor = switch (state.phase()) {
            case CALM -> ChatFormatting.GREEN;
            case WARNING -> ChatFormatting.YELLOW;
            case SURGE -> ChatFormatting.RED;
            case EBB -> ChatFormatting.AQUA;
        };

        Component phaseName = Component.translatable("tide.interstice.phase." + state.phase().name().toLowerCase(Locale.ROOT))
                .withStyle(phaseColor);

        long ticksRemaining = Math.max(0, state.phaseDurationTicks() - state.phaseTicksElapsed());
        long secondsRemaining = ticksRemaining / 20;

        tooltip.add(Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("tide.interstice.tooltip.phase").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": "))
                .append(phaseName));

        tooltip.add(Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("tide.interstice.tooltip.remaining", secondsRemaining).withStyle(ChatFormatting.GRAY)));

        if (state.phase() == TidePhase.SURGE || state.phase() == TidePhase.EBB) {
            int liftPct = Math.round(state.buoyancyIntensity() * 100.0F);
            tooltip.add(Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable("tide.interstice.tooltip.lift", liftPct).withStyle(ChatFormatting.RED)));
        }
    }
}
