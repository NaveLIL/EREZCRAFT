package pro.erez.interstice.lift;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A real passenger vehicle, moved only by its loaded anchor. Players never control its network motion. */
public final class FieldLiftEntity extends VehicleEntity {
    private static final EntityDataAccessor<BlockPos> ANCHOR_POS = SynchedEntityData.defineId(FieldLiftEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Optional<UUID>> ANCHOR_ID = SynchedEntityData.defineId(FieldLiftEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> MOVING = SynchedEntityData.defineId(FieldLiftEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(FieldLiftEntity.class, EntityDataSerializers.INT);
    private final LiftCargo cargo = new LiftCargo();
    private boolean released;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    public FieldLiftEntity(EntityType<? extends FieldLiftEntity> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(ANCHOR_POS, BlockPos.ZERO); builder.define(ANCHOR_ID, Optional.empty()); builder.define(MOVING, false); builder.define(FUEL, 0);
    }
    public BlockPos anchorPos() { return entityData.get(ANCHOR_POS); }
    public UUID anchorId() { return entityData.get(ANCHOR_ID).orElse(null); }
    public boolean moving() { return entityData.get(MOVING); }
    public int fuel() { return entityData.get(FUEL); }
    public SimpleContainer cargo() { return cargo; }
    public void bind(FieldAnchorEntity anchor) {
        entityData.set(ANCHOR_POS, anchor.getBlockPos()); entityData.set(ANCHOR_ID, Optional.of(anchor.anchorId())); entityData.set(FUEL, anchor.fuel());
    }
    public boolean boundTo(FieldAnchorEntity anchor) {
        return !isRemoved() && anchor.getBlockPos().equals(anchorPos()) && anchor.anchorId().equals(anchorId()) && getUUID().equals(anchor.liftId());
    }
    private FieldAnchorEntity anchor() {
        if (!(level() instanceof ServerLevel server) || !server.hasChunkAt(anchorPos())) return null;
        return server.getBlockEntity(anchorPos()) instanceof FieldAnchorEntity anchor && boundTo(anchor) ? anchor : null;
    }
    @Override public void tick() {
        super.tick(); setNoGravity(true);
        if (level().isClientSide) {
            if (lerpSteps > 0) { setPos(getX() + (lerpX - getX()) / lerpSteps, getY() + (lerpY - getY()) / lerpSteps, getZ() + (lerpZ - getZ()) / lerpSteps); lerpSteps--; }
            return;
        }
        if (!level().hasChunkAt(anchorPos())) { entityData.set(MOVING, false); setDeltaMovement(Vec3.ZERO); return; }
        FieldAnchorEntity anchor = anchor();
        if (anchor == null) { discard(); return; }
        entityData.set(FUEL, anchor.fuel());
        double x = anchorPos().getX() + .5, z = anchorPos().getZ() + .5;
        if (Math.abs(getX() - x) > .001 || Math.abs(getZ() - z) > .001 || getY() < anchor.baseY() - .01 || getY() > anchor.ceiling() + .01) {
            anchor.stop(FieldAnchorEntity.Status.OBSTRUCTED); entityData.set(MOVING, false); setDeltaMovement(Vec3.ZERO); return;
        }
        if (!anchor.commanded() || anchor.fuel() <= 0) { entityData.set(MOVING, false); setDeltaMovement(Vec3.ZERO); return; }
        double target = Math.clamp(anchor.target(), anchor.baseY(), anchor.ceiling());
        double difference = target - getY();
        if (Math.abs(difference) < .0001) { anchor.stop(FieldAnchorEntity.Status.STOPPED); entityData.set(MOVING, false); setDeltaMovement(Vec3.ZERO); return; }
        double dy = Math.clamp(difference, -RealmLift.SPEED, RealmLift.SPEED);
        if (level().getBlockCollisions(this, getBoundingBox().move(0, dy, 0)).iterator().hasNext()) {
            anchor.stop(FieldAnchorEntity.Status.OBSTRUCTED); entityData.set(MOVING, false); setDeltaMovement(Vec3.ZERO); return;
        }
        for (Entity rider : getPassengers()) if (level().getBlockCollisions(rider, rider.getBoundingBox().move(0, dy, 0)).iterator().hasNext()) {
            anchor.stop(FieldAnchorEntity.Status.OBSTRUCTED); entityData.set(MOVING, false); setDeltaMovement(Vec3.ZERO); return;
        }
        double before = getY(); setDeltaMovement(0, dy, 0); move(MoverType.SELF, getDeltaMovement());
        if (Math.abs(getY() - before) < .000001) {
            anchor.stop(FieldAnchorEntity.Status.OBSTRUCTED); entityData.set(MOVING, false); setDeltaMovement(Vec3.ZERO); return;
        }
        anchor.payMovingTick(this); entityData.set(FUEL, anchor.fuel()); entityData.set(MOVING, anchor.commanded());
        getPassengers().forEach(this::positionRider);
    }
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        lerpX = x; lerpY = y; lerpZ = z; lerpSteps = Math.max(1, steps); setYRot(0); setXRot(0);
    }
    @Override public LivingEntity getControllingPassenger() { return null; }
    @Override public boolean startRiding(Entity vehicle, boolean force) { return false; }
    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    @Override public boolean canChangeDimensions(Level source, Level destination) { return false; }
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        var box = getBoundingBox();
        return anchorId() == null ? box : new net.minecraft.world.phys.AABB(box.minX, Math.min(box.minY, anchorPos().getY() + 1), box.minZ, box.maxX, box.maxY + .5, box.maxZ);
    }
    @Override protected boolean canAddPassenger(Entity entity) { return entity instanceof Player && getPassengers().isEmpty(); }
    @Override protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        // Entity.positionRider subtracts the passenger's own attachment (.6Y for players).
        // Add that native offset so actual feet stand on the deck instead of inside its anchor.
        return new Vec3(0, .35, 0).add(entity.getVehicleAttachmentPoint(this));
    }
    @Override public boolean canBeCollidedWith() { return !isRemoved(); }
    @Override public boolean canCollideWith(Entity other) { return !isPassengerOfSameVehicle(other) && other.canBeCollidedWith(); }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean hasExactlyOnePlayerPassenger() {
        // Vanilla uses this predicate only for player-owned RootVehicle saving/logout (and TraderLlama).
        // This anchored cargo is world-owned: retain the one world entity, never a second cargo copy in playerdata.
        return false;
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server) || !player.isAlive() || player.isSpectator() || released) return InteractionResult.FAIL;
        FieldAnchorEntity anchor = anchor(); if (anchor == null) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            if (!anchor.authorized(player) || player.distanceToSqr(this) > 64) return InteractionResult.FAIL;
            server.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, cargo, 1), Component.translatable("container.interstice.field_lift")));
            return InteractionResult.CONSUME;
        }
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
    @Override protected Item getDropItem() { return Items.AIR; }
    @Override public void destroy(Item item) { discard(); }
    @Override protected void destroy(DamageSource source) { discard(); }
    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) {
            releaseCargo();
            var anchor = anchor(); if (anchor != null) anchor.destroyedLift(getUUID());
        }
        super.remove(reason);
    }
    private void releaseCargo() {
        if (released) return;
        released = true;
        var drops = new SimpleContainer(RealmLift.CARGO_SLOTS);
        for (int slot = 0; slot < RealmLift.CARGO_SLOTS; slot++) { drops.setItem(slot, cargo.getItem(slot).copy()); cargo.setItem(slot, ItemStack.EMPTY); }
        // Reject unregistered duplicate-UUID decode objects; only the actual world-owned entity may drop cargo.
        if (level() instanceof ServerLevel server && server.getEntity(getUUID()) == this && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS))
            Containers.dropContents(level(), this, drops);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("AnchorPos", anchorPos().asLong()); if (anchorId() != null) tag.putUUID("AnchorId", anchorId()); tag.putBoolean("CargoReleased", released);
        cargo.save(tag);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(ANCHOR_POS, BlockPos.of(tag.getLong("AnchorPos")));
        entityData.set(ANCHOR_ID, tag.hasUUID("AnchorId") ? Optional.of(tag.getUUID("AnchorId")) : Optional.empty());
        cargo.clearContent(); released = tag.getBoolean("CargoReleased"); if (!released) cargo.load(tag);
        setNoGravity(true); setDeltaMovement(Vec3.ZERO); entityData.set(MOVING, false);
    }
    @Override public ItemStack getPickResult() { return new ItemStack(RealmLift.ANCHOR_ITEM.get()); }
    private final class LiftCargo extends SimpleContainer {
        private boolean loading;
        private LiftCargo() { super(RealmLift.CARGO_SLOTS); }
        @Override public boolean stillValid(Player player) { var anchor = anchor(); return !released && isAlive() && player.level() == level() && player.distanceToSqr(FieldLiftEntity.this) <= 64 && anchor != null && anchor.authorized(player); }
        private void save(CompoundTag tag) {
            var stacks = net.minecraft.core.NonNullList.withSize(RealmLift.CARGO_SLOTS, ItemStack.EMPTY);
            for (int i = 0; i < stacks.size(); i++) stacks.set(i, getItem(i).copy());
            net.minecraft.world.ContainerHelper.saveAllItems(tag, stacks, registryAccess());
        }
        private void load(CompoundTag tag) {
            var stacks = net.minecraft.core.NonNullList.withSize(RealmLift.CARGO_SLOTS, ItemStack.EMPTY);
            net.minecraft.world.ContainerHelper.loadAllItems(tag, stacks, registryAccess());
            loading = true; for (int i = 0; i < stacks.size(); i++) setItem(i, stacks.get(i)); loading = false;
        }
    }
}
