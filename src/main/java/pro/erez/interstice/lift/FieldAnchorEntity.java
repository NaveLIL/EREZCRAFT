package pro.erez.interstice.lift;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.worldgen.IslandWorld;

/** The block owns one persistent vehicle UUID; a missing loaded entity never means a new free vehicle. */
public final class FieldAnchorEntity extends BlockEntity {
    public enum Action { UP, DOWN, STOP }
    public enum Status { STOPPED, MOVING, OBSTRUCTED, EMPTY, BROKEN, WAITING }
    private UUID anchorId = UUID.randomUUID(), owner, liftId;
    private int fuel;
    private double target;
    private boolean commanded, broken;
    private Status status = Status.STOPPED;
    public FieldAnchorEntity(BlockPos pos, BlockState state) { super(RealmLift.ANCHOR_ENTITY.get(), pos, state); target = baseY(); }
    public UUID anchorId() { return anchorId; }
    public UUID liftId() { return liftId; }
    public UUID owner() { return owner; }
    public int fuel() { return fuel; }
    public double target() { return target; }
    public Status status() { return status; }
    public boolean commanded() { return commanded; }
    public double baseY() { return worldPosition.getY() + 1.01; }
    public double ceiling() {
        if (level == null) return baseY();
        double cap = level.getMaxBuildHeight() - 3;
        if (IslandWorld.isIsland(level.dimension())) cap = Math.min(cap, GeometryProfiles.get(level).maxLand() - 2.5);
        return Math.min(baseY() + RealmLift.MAX_TRAVEL, cap);
    }
    public void setOwner(UUID value) { owner = value; changed(); }
    public FieldLiftEntity lift() {
        if (!(level instanceof ServerLevel server) || liftId == null) return null;
        var entity = server.getEntity(liftId);
        return entity instanceof FieldLiftEntity lift && lift.boundTo(this) ? lift : null;
    }
    public boolean authorized(Player player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.level() != level) return false;
        var lift = lift(); return player.getUUID().equals(owner) || lift != null && player.getVehicle() == lift;
    }
    public boolean addFuel(int amount) {
        if (amount <= 0 || amount > RealmLift.MAX_FUEL - fuel) return false;
        fuel += amount; changed(); return true;
    }
    public boolean deploy(Player player) {
        if (!(level instanceof ServerLevel server) || !authorized(player) || broken) return false;
        if (liftId != null) { status = lift() == null ? Status.WAITING : status; changed(); return lift() != null; }
        int localX = worldPosition.getX() & 15, localZ = worldPosition.getZ() & 15;
        if (localX < 1 || localX > 14 || localZ < 1 || localZ > 14 || ceiling() <= baseY() + .5) return false;
        if (IslandWorld.isIsland(level.dimension())) {
            var geometry = GeometryProfiles.get(level);
            boolean groundedV5 = server.getChunkSource().getGenerator() instanceof pro.erez.interstice.worldgen.IslandChunkGenerator generator && generator.terrainRevision() >= 5;
            if (groundedV5 ? worldPosition.getY() <= geometry.lowerSeaTop() || baseY() <= geometry.lowerSeaTop() + 1
                    : baseY() < geometry.minLand()) return false;
        }
        var entity = RealmLift.LIFT.get().create(server); if (entity == null) return false;
        entity.bind(this); entity.setPos(worldPosition.getX() + .5, baseY(), worldPosition.getZ() + .5);
        if (!server.noCollision(entity, entity.getBoundingBox()) || server.containsAnyLiquid(entity.getBoundingBox())) return false;
        liftId = entity.getUUID(); target = baseY(); commanded = false; status = Status.STOPPED;
        if (!server.addFreshEntity(entity)) { liftId = null; return false; }
        changed(); return true;
    }
    public boolean control(Player player, Action action) {
        if (!authorized(player) || broken) return false;
        if (!deploy(player)) return false;
        if (action == Action.STOP || commanded) { stop(Status.STOPPED); return true; }
        if (fuel <= 0) { stop(Status.EMPTY); return false; }
        target = action == Action.UP ? ceiling() : baseY(); commanded = true; status = Status.MOVING; changed(); return true;
    }
    public boolean payMovingTick(FieldLiftEntity entity) {
        if (broken || !commanded || fuel <= 0 || lift() != entity) return false;
        fuel--; setChanged();
        if (fuel == 0) stop(Status.EMPTY);
        return true;
    }
    public void stop(Status state) { commanded = false; status = state; changed(); }
    public void destroyedLift(UUID vehicle) {
        if (vehicle.equals(liftId)) { broken = true; commanded = false; status = Status.BROKEN; changed(); }
    }
    public boolean repair(Player player) {
        if (!authorized(player) || !broken || lift() != null) return false;
        UUID previous = liftId; broken = false; liftId = null; target = baseY(); status = Status.STOPPED;
        if (deploy(player)) { changed(); return true; }
        liftId = previous; broken = true; status = Status.BROKEN; changed(); return false;
    }
    public void removeAnchor() {
        commanded = false; var vehicle = lift(); if (vehicle != null) vehicle.discard();
    }
    private void changed() {
        setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup); tag.putUUID("AnchorId", anchorId); if (owner != null) tag.putUUID("Owner", owner); if (liftId != null) tag.putUUID("LiftId", liftId);
        tag.putInt("Fuel", fuel); tag.putDouble("Target", target); tag.putBoolean("Commanded", commanded); tag.putBoolean("Broken", broken); tag.putString("Status", status.name());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup); anchorId = tag.hasUUID("AnchorId") ? tag.getUUID("AnchorId") : UUID.randomUUID();
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null; liftId = tag.hasUUID("LiftId") ? tag.getUUID("LiftId") : null;
        fuel = Math.clamp(tag.getInt("Fuel"), 0, RealmLift.MAX_FUEL); target = Double.isFinite(tag.getDouble("Target")) ? tag.getDouble("Target") : baseY();
        commanded = tag.getBoolean("Commanded"); broken = tag.getBoolean("Broken");
        try { status = Status.valueOf(tag.getString("Status")); } catch (IllegalArgumentException ignored) { status = Status.STOPPED; }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { return saveWithoutMetadata(lookup); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
