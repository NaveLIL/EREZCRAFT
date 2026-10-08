package pro.erez.interstice.test;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.expedition.*;
import pro.erez.interstice.rift.*;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder("interstice_expeditions")
@PrefixGameTestTemplate(false)
public final class ExpeditionGameTests {
    private static ServerPlayer enter(GameTestHelper h) {
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 13; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        ServerPlayer player = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        var source = new RiftLinks.Endpoint(Level.OVERWORLD, player.blockPosition(), Direction.Axis.X);
        if (!RiftTravel.enter(player, source, RiftLinks.Kind.FISHING)) throw new AssertionError("Fixture discovery failed");
        player.hasChangedDimension(); // virtual client acknowledges the completed dimension switch
        if (ExpeditionLedger.get(player.server).journey(player.getUUID()) == null) throw new AssertionError("Real dimension event did not record the expedition");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Interstice.WAYFARER_KEY.get()));
        return player;
    }
    private static void use(GameTestHelper h, ServerPlayer player) {
        var result = player.getMainHandItem().use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult().consumesAction() && player.isUsingItem(), "Actual key use must start concentration");
    }
    @GameTest(template = "empty", timeoutTicks = 420)
    public static void emergencyReturnRequiresFullChannelAndSpendsOnlyOnce(GameTestHelper h) {
        ServerPlayer player = enter(h); use(h, player);
        var keyCopy = player.getMainHandItem().copy(); var journey = ExpeditionLedger.get(player.server).journey(player.getUUID());
        h.runAtTickTime(100, () -> h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD) && !journey.spent(), "Return cannot happen before ten seconds"));
        h.runAtTickTime(225, () -> {
            try {
                var ledger = ExpeditionLedger.get(player.server);
                h.assertTrue(player.level().dimension().equals(Level.OVERWORLD) && ledger.journey(player.getUUID()).spent(), "Completed concentration must return and spend the server entitlement");
                player.getPersistentData().remove(RiftTravel.COOLDOWN);
                h.assertTrue(RiftTravel.enter(player, new RiftLinks.Endpoint(Level.OVERWORLD, journey.origin().feet(), Direction.Axis.X), RiftLinks.Kind.FISHING), "Repeat entry must remain possible");
                h.assertTrue(ledger.journey(player.getUUID()).id().equals(journey.id()) && ledger.journey(player.getUUID()).spent(), "Repeat entry cannot renew the entitlement");
                player.setItemInHand(InteractionHand.MAIN_HAND, keyCopy);
                h.assertTrue(!WayfarerRecall.begin(player, InteractionHand.MAIN_HAND, keyCopy), "An item copy cannot renew the entitlement");
                h.succeed();
            } finally { TestPlayers.remove(player); }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 330)
    public static void motionInterruptsWithoutSpending(GameTestHelper h) {
        ServerPlayer player = enter(h); use(h, player);
        h.runAtTickTime(20, () -> player.teleportTo(player.getX() + .5, player.getY(), player.getZ()));
        h.runAtTickTime(225, () -> { try {
            h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD) && !player.isUsingItem(), "Motion must cancel concentration");
            h.assertTrue(!ExpeditionLedger.get(player.server).journey(player.getUUID()).spent(), "Interrupted concentration cannot consume the return"); h.succeed();
        } finally { TestPlayers.remove(player); } });
    }
    @GameTest(template = "empty", timeoutTicks = 330)
    public static void realDamageInterruptsWithoutSpending(GameTestHelper h) {
        ServerPlayer player = enter(h); use(h, player);
        h.runAtTickTime(80, () -> { player.invulnerableTime = 0; h.assertTrue(player.hurt(player.damageSources().generic(), 1), "Fixture must deal actual damage after arrival protection"); });
        h.runAtTickTime(225, () -> { try {
            h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD) && !player.isUsingItem(), "Real damage must interrupt key use");
            h.assertTrue(!ExpeditionLedger.get(player.server).journey(player.getUUID()).spent(), "Damage cannot consume a failed return"); h.succeed();
        } finally { TestPlayers.remove(player); } });
    }
    @GameTest(template = "empty", timeoutTicks = 330)
    public static void releasingButtonInterruptsWithoutSpending(GameTestHelper h) {
        ServerPlayer player = enter(h); use(h, player);
        h.runAtTickTime(20, player::releaseUsingItem);
        h.runAtTickTime(225, () -> { try {
            h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD) && !ExpeditionLedger.get(player.server).journey(player.getUUID()).spent(), "Releasing the key must abort the return"); h.succeed();
        } finally { TestPlayers.remove(player); } });
    }
    @GameTest(template = "empty", timeoutTicks = 330)
    public static void emergencyReturnWorksAfterPhysicalEchoIsDestroyed(GameTestHelper h) {
        ServerPlayer player = enter(h);
        var link = RiftLinks.get(player.server).byId(player.getPersistentData().getUUID(RiftTravel.ACTIVE)); player.serverLevel().removeBlock(link.echo().pos(), false);
        use(h, player);
        h.runAtTickTime(225, () -> { try {
            h.assertTrue(player.level().dimension().equals(Level.OVERWORLD) && ExpeditionLedger.get(player.server).journey(player.getUUID()).spent(), "Emergency return must use personal origin independently of the destroyed physical echo"); h.succeed();
        } finally { TestPlayers.remove(player); } });
    }
    @GameTest(template = "empty", timeoutTicks = 330)
    public static void unavailableDestinationRefusesWithoutSpending(GameTestHelper h) {
        ServerPlayer player = enter(h); var ledger = ExpeditionLedger.get(player.server);
        var unavailable = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Interstice.ID, "missing_return_test"));
        ledger.prepare(player.getUUID(), new ExpeditionLedger.Origin(unavailable, new BlockPos(0, 64, 0)));
        ledger.arrive(player.getUUID(), new ExpeditionLedger.Origin(unavailable, new BlockPos(0, 64, 0))); use(h, player);
        h.runAtTickTime(225, () -> { try {
            h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD) && !ledger.journey(player.getUUID()).spent(), "Unavailable destination must fail without losing the entitlement"); h.succeed();
        } finally { TestPlayers.remove(player); } });
    }
    @GameTest(template = "empty", timeoutTicks = 330)
    public static void blockedOriginalRegionCannotTeleportIntoSolidBlocks(GameTestHelper h) {
        ServerPlayer player = enter(h); var journey = ExpeditionLedger.get(player.server).journey(player.getUUID());
        BlockPos origin = journey.origin().feet();
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = -2; y <= 3; y++) h.getLevel().setBlock(origin.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
        use(h, player);
        h.runAtTickTime(225, () -> { try {
            h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD) && !ExpeditionLedger.get(player.server).journey(player.getUUID()).spent(), "Blocked original region must not receive a player or consume their return"); h.succeed();
        } finally { TestPlayers.remove(player); } });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void transferredKeyCannotBeClaimedByAnotherPlayer(GameTestHelper h) {
        ServerPlayer first = enter(h), second = enter(h);
        try {
            ItemStack key = first.getMainHandItem(); h.assertTrue(WayfarerRecall.begin(first, InteractionHand.MAIN_HAND, key), "Owner must bind their key");
            WayfarerRecall.cancel(first, "released"); second.setItemInHand(InteractionHand.MAIN_HAND, key.copy());
            h.assertTrue(!WayfarerRecall.begin(second, InteractionHand.MAIN_HAND, second.getMainHandItem()), "Giving away a bound key cannot transfer an entitlement"); h.succeed();
        } finally { TestPlayers.remove(first); TestPlayers.remove(second); }
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void serializationAndNewItemCannotRefillSpentJourney(GameTestHelper h) {
        UUID player = UUID.randomUUID(); var ledger = new ExpeditionLedger(); var origin = new ExpeditionLedger.Origin(Level.OVERWORLD, new BlockPos(20, 64, -6));
        var journey = ledger.arrive(player, origin); h.assertTrue(ledger.spend(player, journey.id()), "First use must succeed");
        var reload = ExpeditionLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(reload.arrive(player, origin).spent() && !reload.spend(player, journey.id()), "Reload and arrival must preserve exhausted rights");
        ItemStack key = new ItemStack(Interstice.WAYFARER_KEY.get()); WayfarerKeyItem.bind(key, player, journey.id());
        h.assertTrue(WayfarerKeyItem.binding(key.copy()).equals(WayfarerKeyItem.binding(key)), "Component-backed copies must keep the same binding"); h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deathAbandonsJourneyAndExplicitPreparationCostsOneShard(GameTestHelper h) {
        ServerPlayer player = enter(h); var ledger = ExpeditionLedger.get(player.server); UUID old = ledger.journey(player.getUUID()).id();
        try {
            player.invulnerableTime = 0; h.assertTrue(player.hurt(player.damageSources().genericKill(), 1000), "Fixture must deal lethal damage");
            h.assertTrue(ledger.journey(player.getUUID()).spent(), "Death must abandon the existing expedition entitlement");
            // Preparation policy is also checked with a live independent outside player.
            var outside = TestPlayers.create(h, new BlockPos(3, 2, 3), GameType.SURVIVAL);
            try {
                ItemStack key = new ItemStack(Interstice.WAYFARER_KEY.get()); outside.setItemInHand(InteractionHand.MAIN_HAND, key);
                h.assertTrue(!WayfarerRecall.prepareOrBind(outside, key, true), "Preparation must require a shard");
                outside.getInventory().add(new ItemStack(Items.AMETHYST_SHARD, 2));
                h.assertTrue(WayfarerRecall.prepareOrBind(outside, key, true), "Explicit outside preparation must work");
                h.assertTrue(outside.getInventory().countItem(Items.AMETHYST_SHARD) == 1 && !ledger.journey(outside.getUUID()).entered(), "Preparation must spend exactly one shard and wait for entry");
            } finally { TestPlayers.remove(outside); }
            h.succeed();
        } finally { TestPlayers.remove(player); }
    }
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void virtualSessionsSettleBeforeShutdown(GameTestHelper h) {
        h.runAtTickTime(300, () -> { h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayers().isEmpty(), "Test players must close before world saving"); h.succeed(); });
    }
    @GameTest(template = "empty", timeoutTicks = 340)
    public static void reconnectCancelsConcentrationAndPreservesEntitlement(GameTestHelper h) {
        ServerPlayer initial = enter(h); use(h, initial); UUID id = initial.getUUID();
        ServerPlayer[] current = {initial}; UUID journey = ExpeditionLedger.get(initial.server).journey(id).id();
        h.runAtTickTime(20, () -> { TestPlayers.remove(initial); current[0] = TestPlayers.reconnect(h, id); });
        h.runAtTickTime(225, () -> { try {
            var player = current[0]; var state = ExpeditionLedger.get(player.server).journey(id);
            h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD) && !player.isUsingItem(), "Reconnect must not resume the prior concentration");
            h.assertTrue(state.id().equals(journey) && !state.spent(), "Reconnect must preserve the same unspent server entitlement"); h.succeed();
        } finally { TestPlayers.remove(current[0]); } });
    }
    @GameTest(template = "empty", timeoutTicks = 340)
    public static void serverDeniedRecallDoesNotConsumeEntitlement(GameTestHelper h) {
        ServerPlayer player = enter(h);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent> deny = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny); use(h, player);
        h.runAtTickTime(225, () -> { try {
            h.assertTrue(player.level().dimension().equals(IslandWorld.TALL_WORLD) && !ExpeditionLedger.get(player.server).journey(player.getUUID()).spent(), "Denied server transfer must leave both player and entitlement unchanged"); h.succeed();
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); TestPlayers.remove(player); } });
    }
}
