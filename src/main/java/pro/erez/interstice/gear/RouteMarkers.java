package pro.erez.interstice.gear;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import pro.erez.interstice.Interstice;

/** Public named waypoints live in the owned indicator; looking up a point never requests its chunk. */
@EventBusSubscriber(modid = Interstice.ID)
public final class RouteMarkers {
    public static final String KEY = "interstice_route_marker";
    public enum Status { LIVE, OFF, UNLOADED, LOST, OTHER_DIMENSION }
    public record Marker(ResourceLocation dimension, BlockPos pos, UUID id, String name) {
        public Marker { pos = pos.immutable(); name = boundedName(name); }
        public Component label() { return name.isEmpty() ? Component.translatable("block.interstice.route_beacon") : Component.literal(name); }
    }
    private static final String[] DIRECTIONS = { "north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest" };
    private RouteMarkers() {}

    static String boundedName(String input) {
        if (input == null) return "";
        String name = input.strip();
        return name.substring(0, name.offsetByCodePoints(0, Math.min(64, name.codePointCount(0, name.length()))));
    }

    public static Marker marker(ItemStack stack) {
        if (!stack.is(Interstice.TIDE_INDICATOR.get())) return null;
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(KEY);
        var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null || !tag.contains("Pos", net.minecraft.nbt.Tag.TAG_LONG) || !tag.hasUUID("MarkerId")) return null;
        return new Marker(dimension, BlockPos.of(tag.getLong("Pos")), tag.getUUID("MarkerId"), tag.getString("Name"));
    }

    public static boolean bind(ServerPlayer player, ItemStack stack, RouteBeaconEntity beacon) {
        if (!player.isAlive() || player.isSpectator() || player.serverLevel() != beacon.getLevel()
                || !stack.is(Interstice.TIDE_INDICATOR.get()) || stack.getCount() != 1
                || (player.getMainHandItem() != stack && player.getOffhandItem() != stack)
                || !player.canInteractWithBlock(beacon.getBlockPos(), 0) || !player.serverLevel().mayInteract(player, beacon.getBlockPos())) return false;
        var chunk = player.serverLevel().getChunkSource().getChunkNow(beacon.getBlockPos().getX() >> 4, beacon.getBlockPos().getZ() >> 4);
        if (chunk == null || chunk.getBlockEntity(beacon.getBlockPos()) != beacon
                || !chunk.getBlockState(beacon.getBlockPos()).is(RealmGear.BEACON.get())) return false;
        var saved = new CompoundTag();
        saved.putString("Dimension", player.level().dimension().location().toString());
        saved.putLong("Pos", beacon.getBlockPos().asLong());
        saved.putUUID("MarkerId", beacon.markerId());
        saved.putString("Name", beacon.markerName());
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.put(KEY, saved);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        beacon.setChanged(); // A marker UUID added to an old unfuelled save must persist after its first binding.
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.interstice.marker.bound", marker(stack).label()), true);
        return true;
    }

    public static Status status(Level level, Marker marker) {
        if (!level.dimension().location().equals(marker.dimension())) return Status.OTHER_DIMENSION;
        if (!(level instanceof ServerLevel server)) return Status.UNLOADED;
        var chunk = server.getChunkSource().getChunkNow(marker.pos().getX() >> 4, marker.pos().getZ() >> 4);
        if (chunk == null) return Status.UNLOADED;
        if (!(chunk.getBlockEntity(marker.pos()) instanceof RouteBeaconEntity beacon)
                || !chunk.getBlockState(marker.pos()).is(RealmGear.BEACON.get()) || !beacon.markerId().equals(marker.id())) return Status.LOST;
        return beacon.fuelTicks() > 0 ? Status.LIVE : Status.OFF;
    }

    private static Component direction(Player player, Marker marker) {
        double dx = marker.pos().getX() + .5 - player.getX(), dz = marker.pos().getZ() + .5 - player.getZ();
        if (Math.hypot(dx, dz) < .75) return Component.translatable("route.interstice.direction.here");
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int index = Math.floorMod((int) Math.floor((angle + 22.5) / 45), DIRECTIONS.length);
        return Component.translatable("route.interstice.direction." + DIRECTIONS[index]);
    }

    private static long distance(Player player, Marker marker) {
        return Math.round(Math.hypot(marker.pos().getX() + .5 - player.getX(), marker.pos().getZ() + .5 - player.getZ()));
    }

    private static String elevation(Player player, Marker marker) {
        long difference = Math.round(marker.pos().getY() - player.getY());
        return difference > 0 ? "+" + difference : Long.toString(difference);
    }

    public static Component describe(Level level, Player player, ItemStack stack) {
        var marker = marker(stack);
        if (marker == null) return null;
        if (!level.dimension().location().equals(marker.dimension()))
            return Component.translatable("message.interstice.marker.other_dimension", marker.label(), marker.dimension().toString());
        var status = status(level, marker);
        return Component.translatable("message.interstice.marker.navigation", marker.label(), direction(player, marker), distance(player, marker),
                elevation(player, marker), Component.translatable("route.interstice.status." + status.name().toLowerCase(java.util.Locale.ROOT)));
    }

    public static void appendTooltip(ItemStack stack, List<Component> text) {
        text.add(Component.translatable("tooltip.interstice.marker.bind_hint"));
        var marker = marker(stack);
        if (marker == null) return;
        text.add(Component.translatable("tooltip.interstice.marker.position", marker.label(), marker.pos().getX(), marker.pos().getY(), marker.pos().getZ()));
        text.add(Component.translatable("tooltip.interstice.marker.dimension", marker.dimension().toString()));
        text.add(Component.translatable("tooltip.interstice.marker.permanent"));
    }

    @SubscribeEvent public static void relativeTooltip(ItemTooltipEvent event) {
        var player = event.getEntity();
        if (player == null) return;
        var marker = marker(event.getItemStack());
        if (marker == null || !player.level().dimension().location().equals(marker.dimension())) return;
        // Tooltip direction is arithmetic over the saved point; the actionbar performs server validation.
        event.getToolTip().add(Component.translatable("tooltip.interstice.marker.navigation", direction(player, marker), distance(player, marker), elevation(player, marker)));
    }
}
