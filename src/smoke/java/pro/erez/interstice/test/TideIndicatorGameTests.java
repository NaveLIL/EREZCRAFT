package pro.erez.interstice.test;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.item.TideIndicatorItem;
import pro.erez.interstice.sound.ModSounds;
import pro.erez.interstice.tide.ClientTideState;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideState;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class TideIndicatorGameTests {

    private static ServerLevel islandWorld(GameTestHelper h) {
        return java.util.Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD));
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void indicatorItemRegisteredAndValid(GameTestHelper h) {
        h.assertTrue(Interstice.TIDE_INDICATOR.get() != null, "Tide indicator item must be registered");
        ItemStack stack = new ItemStack(Interstice.TIDE_INDICATOR.get());
        h.assertTrue(stack.getMaxStackSize() == 1, "Tide indicator must be non-stackable tool");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void modSoundsRegisteredAndPlayable(GameTestHelper h) {
        h.assertTrue(ModSounds.TIDE_WARNING.get() != null, "TIDE_WARNING sound event must be registered");
        h.assertTrue(ModSounds.TIDE_SURGE.get() != null, "TIDE_SURGE sound event must be registered");

        ResourceLocation warningId = BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.TIDE_WARNING.get());
        h.assertTrue(warningId != null && warningId.equals(ResourceLocation.fromNamespaceAndPath(Interstice.ID, "tide.warning")),
                "Warning sound ID must match interstice:tide.warning");

        ResourceLocation surgeId = BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.TIDE_SURGE.get());
        h.assertTrue(surgeId != null && surgeId.equals(ResourceLocation.fromNamespaceAndPath(Interstice.ID, "tide.surge")),
                "Surge sound ID must match interstice:tide.surge");

        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void indicatorTooltipReflectsTideState(GameTestHelper h) {
        TideIndicatorItem indicator = (TideIndicatorItem) Interstice.TIDE_INDICATOR.get();
        ItemStack stack = new ItemStack(indicator);

        // Test in WARNING state
        TideState warningState = new TideState(TidePhase.WARNING, 200, 1200, 1);
        ClientTideState.update(warningState);

        List<Component> tooltip = new ArrayList<>();
        indicator.appendHoverText(stack, net.minecraft.world.item.Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);

        h.assertTrue(!tooltip.isEmpty(), "Tooltip must contain information lines");
        boolean hasRemaining = false;
        for (Component line : tooltip) {
            if (line.getString().contains("50") || line.getString().contains("Remaining") || line.getString().contains("Осталось")) {
                hasRemaining = true;
            }
        }
        h.assertTrue(hasRemaining, "Tooltip must indicate remaining time (50s left in warning)");

        // Test in SURGE state with lift percentage
        TideState surgeState = new TideState(TidePhase.SURGE, 900, 1800, 1);
        ClientTideState.update(surgeState);

        tooltip.clear();
        indicator.appendHoverText(stack, net.minecraft.world.item.Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);

        boolean hasLift = false;
        for (Component line : tooltip) {
            if (line.getString().contains("Lift") || line.getString().contains("Подъёмная") || line.getString().contains("%")) {
                hasLift = true;
            }
        }
        h.assertTrue(hasLift, "Tooltip during surge must display buoyant lift percentage");

        // Restore calm state
        ClientTideState.update(new TideState(TidePhase.CALM, 0, TidePhase.CALM.minDurationTicks(), 0));
        h.succeed();
    }
}
