package pro.erez.interstice.test;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.RealmGear;
import pro.erez.interstice.gear.RouteBeaconBlock;
import pro.erez.interstice.gear.RouteBeaconEntity;
import pro.erez.interstice.gear.RouteMarkers;

@GameTestHolder("interstice_gear")
@PrefixGameTestTemplate(false)
public final class RouteMarkerGameTests {
    private static final BlockPos BEACON = new BlockPos(4, 2, 4);

    private static RouteBeaconEntity place(GameTestHelper h, ServerPlayer player) {
        h.setBlock(BEACON.below(), Blocks.STONE);
        var item = new ItemStack(RealmGear.BEACON_ITEM.get());
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Shared ore depot"));
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var floor = h.absolutePos(BEACON.below());
        h.assertTrue(player.gameMode.useItemOn(player, h.getLevel(), item, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)).consumesAction(),
                "The renamed beacon must be placed through native BlockItem use");
        var entity = h.getLevel().getBlockEntity(h.absolutePos(BEACON));
        h.assertTrue(entity instanceof RouteBeaconEntity beacon && beacon.markerName().equals("Shared ore depot")
                && player.getUUID().equals(beacon.owner()) && item.isEmpty(),
                "Native placement must consume one item and retain the player-given public name and owner");
        return (RouteBeaconEntity) entity;
    }

    private static ItemStack bind(GameTestHelper h, ServerPlayer player) {
        var indicator = new ItemStack(Interstice.TIDE_INDICATOR.get());
        indicator.set(DataComponents.CUSTOM_NAME, Component.literal("My barometer"));
        var data = new net.minecraft.nbt.CompoundTag();
        data.putString("route_note", "keep unrelated components");
        indicator.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        player.setItemInHand(InteractionHand.MAIN_HAND, indicator);
        player.setShiftKeyDown(true);
        var pos = h.absolutePos(BEACON);
        try {
            h.assertTrue(player.gameMode.useItemOn(player, h.getLevel(), indicator, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.EAST, pos, false)).consumesAction(),
                    "Shift-right-click with the actual indicator must bind the named beacon before wall mounting");
        } finally {
            player.setShiftKeyDown(false);
        }
        return indicator;
    }

    private static boolean hasTickets(ServerLevel level, ChunkPos chunk) {
        try {
            var field = DistanceManager.class.getDeclaredField("tickets");
            field.setAccessible(true);
            var tickets = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>) field.get(level.getChunkSource().chunkMap.getDistanceManager());
            return tickets.containsKey(chunk.toLong());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect the native ticket map for the no-load regression", failure);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void namedPublicBindingCopiesTheSamePointAndUsesNativeOwnedItemPackets(GameTestHelper h) {
        var owner = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        ServerPlayer teammate = null;
        try {
            var beacon = place(h, owner);
            var indicator = bind(h, owner);
            var marker = RouteMarkers.marker(indicator);
            h.assertTrue(marker != null && marker.id().equals(beacon.markerId()) && marker.pos().equals(beacon.getBlockPos())
                    && marker.name().equals("Shared ore depot") && RouteMarkers.status(h.getLevel(), marker) == RouteMarkers.Status.OFF,
                    "An unfuelled named beacon must still provide a permanent public navigation point");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(beacon.getBlockPos()).inflate(2)).isEmpty(),
                    "Binding on a vertical beacon face cannot accidentally consume the barometer into a wall frame");
            teammate = TestPlayers.create(h, new BlockPos(6, 2, 5), GameType.SURVIVAL);
            var other = bind(h, teammate);
            h.assertTrue(marker.equals(RouteMarkers.marker(other)) && !teammate.getUUID().equals(beacon.owner()),
                    "A teammate must bind the same public UUID and name without taking ownership of the beacon");
            var copied = indicator.copy();
            var restored = ItemStack.parse(h.getLevel().registryAccess(), copied.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(marker.equals(RouteMarkers.marker(restored)) && ItemStack.matches(indicator, restored)
                    && restored.getHoverName().getString().equals("My barometer")
                    && restored.get(DataComponents.CUSTOM_DATA).copyTag().getString("route_note").equals("keep unrelated components"),
                    "Copy and native item reload must retain the shared point, owner-held indicator name and unrelated components");
            var channel = (EmbeddedChannel) owner.connection.getConnection().channel();
            channel.runPendingTasks();
            var packet = channel.outboundMessages().stream().filter(value -> value instanceof ClientboundContainerSetSlotPacket slot
                            && marker.equals(RouteMarkers.marker(slot.getItem())))
                    .map(value -> (ClientboundContainerSetSlotPacket) value).findFirst().orElseThrow(() -> new AssertionError("No native inventory packet carried the bound marker"));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                ClientboundContainerSetSlotPacket.STREAM_CODEC.encode(buffer, packet);
                var received = ClientboundContainerSetSlotPacket.STREAM_CODEC.decode(buffer);
                h.assertTrue(marker.equals(RouteMarkers.marker(received.getItem())) && buffer.readableBytes() == 0,
                        "The actual native item-slot packet codec must carry the exact marker payload without a client-written inventory path");
            } finally {
                buffer.release();
            }
            var tooltip = indicator.getTooltipLines(Item.TooltipContext.of(h.getLevel()), owner, TooltipFlag.NORMAL);
            h.assertTrue(tooltip.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents t && t.getKey().equals("tooltip.interstice.marker.position"))
                    && tooltip.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents t && t.getKey().equals("tooltip.interstice.marker.navigation")),
                    "Native tooltips must include the saved name/position and current relative direction, distance and elevation");
        } finally {
            if (teammate != null) TestPlayers.remove(teammate);
            TestPlayers.remove(owner);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void beaconReloadPreservesMarkerIdentityAndOldFuelRemainderWithoutRefilling(GameTestHelper h) {
        var player = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        try {
            var beacon = place(h, player);
            var indicator = bind(h, player);
            var marker = RouteMarkers.marker(indicator);
            beacon.addFuel();
            for (int tick = 0; tick < 137; tick++) beacon.tick();
            var saved = beacon.saveWithFullMetadata(h.getLevel().registryAccess());
            // This remains valid old FuelTicks data: the new capacity must not inflate saved charge.
            saved.putInt("FuelTicks", 4321);
            var loaded = new RouteBeaconEntity(beacon.getBlockPos(), h.getLevel().getBlockState(beacon.getBlockPos()));
            loaded.loadWithComponents(saved, h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(loaded);
            h.assertTrue(loaded.fuelTicks() == 4321 && loaded.markerId().equals(beacon.markerId())
                    && loaded.markerName().equals("Shared ore depot") && marker.equals(RouteMarkers.marker(indicator))
                    && RouteMarkers.status(h.getLevel(), marker) == RouteMarkers.Status.LIVE,
                    "Native device reload must preserve the exact old remainder, public UUID and name while keeping the existing indicator valid");
            var legacy = saved.copy();
            legacy.remove("MarkerId");
            legacy.remove("MarkerName");
            legacy.putInt("FuelTicks", 317);
            var oldDevice = new RouteBeaconEntity(beacon.getBlockPos(), h.getLevel().getBlockState(beacon.getBlockPos()));
            oldDevice.loadWithComponents(legacy, h.getLevel().registryAccess());
            h.assertTrue(oldDevice.fuelTicks() == 317 && oldDevice.markerId() != null && oldDevice.markerName().isEmpty()
                    && oldDevice.owner().equals(player.getUUID()), "A pre-marker beacon save must acquire only an identity, retaining its real charge and owner");
        } finally {
            TestPlayers.remove(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void finiteFortyMinuteSignalExpiresWithoutErasingThePermanentPointAndReplacementIsReported(GameTestHelper h) {
        var player = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        try {
            var beacon = place(h, player);
            var indicator = bind(h, player);
            var marker = RouteMarkers.marker(indicator);
            h.assertTrue(RealmGear.BEACON_FUEL_TICKS == 10 * 60 * 20 && RealmGear.BEACON_MAX_FUEL == 40 * 60 * 20,
                    "One paid signal charge must span ten loaded minutes, with forty minutes as the finite total reservoir");
            for (int charge = 0; charge < 4; charge++) h.assertTrue(beacon.addFuel(), "Four real charges must fit the finite reservoir");
            h.assertTrue(!beacon.addFuel() && beacon.fuelTicks() == RealmGear.BEACON_MAX_FUEL, "A fifth charge cannot bypass the capacity");
            var saved = beacon.saveWithFullMetadata(h.getLevel().registryAccess());
            saved.putInt("FuelTicks", 1);
            var lastTick = new RouteBeaconEntity(beacon.getBlockPos(), h.getLevel().getBlockState(beacon.getBlockPos()));
            lastTick.loadWithComponents(saved, h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(lastTick);
            lastTick.tick();
            h.assertTrue(lastTick.fuelTicks() == 0 && !lastTick.getBlockState().getValue(RouteBeaconBlock.LIT)
                    && marker.equals(RouteMarkers.marker(indicator)) && RouteMarkers.status(h.getLevel(), marker) == RouteMarkers.Status.OFF,
                    "The actual last signal tick must extinguish the light while retaining the saved navigation point");
            h.setBlock(BEACON, Blocks.AIR);
            h.assertTrue(RouteMarkers.status(h.getLevel(), marker) == RouteMarkers.Status.LOST && marker.equals(RouteMarkers.marker(indicator)),
                    "A loaded missing beacon must be reported without silently deleting its permanent point");
            h.setBlock(BEACON, RealmGear.BEACON.get());
            h.assertTrue(!((RouteBeaconEntity) h.getLevel().getBlockEntity(h.absolutePos(BEACON))).markerId().equals(marker.id())
                    && RouteMarkers.status(h.getLevel(), marker) == RouteMarkers.Status.LOST,
                    "A different beacon at the same coordinates cannot impersonate the original public UUID");
        } finally {
            TestPlayers.remove(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void savedUnloadedPointReportsDirectionAndHeightWithoutCreatingChunksOrTickets(GameTestHelper h) {
        var player = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        try {
            place(h, player);
            var indicator = bind(h, player);
            // GameTest placement may be millions of blocks from the origin. Define the planned
            // southeast vector relative to the actual player chunk, not absolute world coordinates.
            var remoteChunk = new ChunkPos(Math.floorDiv(player.getBlockX(), 16) + 8192,
                    Math.floorDiv(player.getBlockZ(), 16) + 8192);
            var remote = new BlockPos(remoteChunk.getMiddleBlockX(), player.getBlockY() + 12, remoteChunk.getMiddleBlockZ());
            // A native imported item snapshot can refer to a point whose chunk is not present in this session.
            var data = indicator.get(DataComponents.CUSTOM_DATA).copyTag();
            data.getCompound(RouteMarkers.KEY).putLong("Pos", remote.asLong());
            indicator.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            var stored = ItemStack.parse(h.getLevel().registryAccess(), indicator.save(h.getLevel().registryAccess())).orElseThrow();
            var marker = RouteMarkers.marker(stored);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(remoteChunk.x, remoteChunk.z) == null && !hasTickets(h.getLevel(), remoteChunk),
                    "The imported saved point must start outside the actual loaded chunks and native ticket map");
            for (int query = 0; query < 100; query++) {
                var description = RouteMarkers.describe(h.getLevel(), player, stored);
                h.assertTrue(description.getContents() instanceof TranslatableContents, "A saved unloaded point must still produce navigation");
                var arguments = ((TranslatableContents) description.getContents()).getArgs();
                h.assertTrue(((Component) arguments[1]).getContents() instanceof TranslatableContents direction
                        && direction.getKey().equals("route.interstice.direction.southeast")
                        && ((Number) arguments[2]).longValue() > 185300 && ((Number) arguments[2]).longValue() < 185400
                        && arguments[3].equals("+12")
                        && RouteMarkers.status(h.getLevel(), marker) == RouteMarkers.Status.UNLOADED,
                        "The readout must derive a usable southeast direction, horizontal distance and elevation from the saved point");
            }
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(remoteChunk.x, remoteChunk.z) == null && !hasTickets(h.getLevel(), remoteChunk)
                    && marker.equals(RouteMarkers.marker(stored)), "Repeated navigation must leave the remote chunk absent, ticket map unchanged and marker intact");
        } finally {
            TestPlayers.remove(player);
        }
        h.succeed();
    }
}
