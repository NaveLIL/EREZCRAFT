package pro.erez.interstice.gear;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Fuel advances only through loaded server ticks. A demolished marker drops an unfuelled item. */
public final class RouteBeaconEntity extends BlockEntity {
    private int fuelTicks, pulseTicks, hintTicks;
    private UUID owner;
    public RouteBeaconEntity(BlockPos pos, BlockState state) { super(RealmGear.BEACON_ENTITY.get(), pos, state); }
    public int fuelTicks() { return fuelTicks; }
    public UUID owner() { return owner; }
    public void setOwner(UUID id) { owner = id; setChanged(); }
    public boolean addFuel() {
        if (fuelTicks > RealmGear.BEACON_MAX_FUEL - RealmGear.BEACON_FUEL_TICKS) return false;
        fuelTicks += RealmGear.BEACON_FUEL_TICKS; setChanged(); updateLit(); return true;
    }
    private void updateLit() {
        if (level == null || level.isClientSide || !getBlockState().is(RealmGear.BEACON.get())) return;
        var state = getBlockState(); boolean lit = fuelTicks > 0;
        if (state.getValue(RouteBeaconBlock.LIT) != lit) level.setBlock(worldPosition, state.setValue(RouteBeaconBlock.LIT, lit), 3);
    }
    public void tick() {
        if (!(level instanceof ServerLevel server)) return;
        if (fuelTicks <= 0) { updateLit(); return; }
        updateLit();
        fuelTicks--; pulseTicks++; hintTicks++; setChanged();
        if (pulseTicks >= 20) {
            pulseTicks = 0;
            // Six sparse sparks form a visible local column without rendering a global infinite beam.
            for (int height = 1; height <= 6; height++)
                server.sendParticles(ParticleTypes.END_ROD, worldPosition.getX() + .5, worldPosition.getY() + 1 + height,
                        worldPosition.getZ() + .5, 1, .06, .05, .06, .006);
        }
        if (hintTicks >= 100) {
            hintTicks = 0;
            var player = owner == null ? null : server.getPlayerByUUID(owner);
            if (player != null && player.isAlive() && !player.isSpectator()
                    && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 48 * 48)
                player.displayClientMessage(Component.translatable("message.interstice.beacon.position", worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), fuelTicks / 20), true);
        }
        if (fuelTicks == 0) updateLit();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putInt("FuelTicks", fuelTicks); tag.putInt("PulseTicks", pulseTicks); tag.putInt("HintTicks", hintTicks);
        if (owner != null) tag.putUUID("Owner", owner);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); fuelTicks = Math.clamp(tag.getInt("FuelTicks"), 0, RealmGear.BEACON_MAX_FUEL);
        pulseTicks = Math.clamp(tag.getInt("PulseTicks"), 0, 19); hintTicks = Math.clamp(tag.getInt("HintTicks"), 0, 99);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
