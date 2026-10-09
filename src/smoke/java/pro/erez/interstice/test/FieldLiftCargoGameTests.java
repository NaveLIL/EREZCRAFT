package pro.erez.interstice.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.lift.FieldAnchorEntity;
import pro.erez.interstice.lift.FieldLiftEntity;
import pro.erez.interstice.lift.FieldLiftMenu;
import pro.erez.interstice.lift.RealmLift;

@GameTestHolder("interstice_lift")
@PrefixGameTestTemplate(false)
public final class FieldLiftCargoGameTests {
    private record Fixture(ServerPlayer owner, BlockPos pos, FieldAnchorEntity anchor, FieldLiftEntity lift) {}

    private static Fixture fixture(GameTestHelper h) {
        var approximate = h.absolutePos(new BlockPos(6, 2, 6));
        var pos = new BlockPos((approximate.getX() & ~15) + 7, approximate.getY(), (approximate.getZ() & ~15) + 7);
        h.getLevel().getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 52; dy++)
            h.getLevel().setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(pos, RealmLift.ANCHOR.get().defaultBlockState(), 3);
        var owner = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        owner.teleportTo(h.getLevel(), pos.getX() + 2.5, pos.getY() + 1, pos.getZ() + .5, java.util.Set.of(), 0, 0);
        owner.hasChangedDimension();
        var anchor = (FieldAnchorEntity) h.getLevel().getBlockEntity(pos);
        anchor.setOwner(owner.getUUID());
        h.assertTrue(anchor.deploy(owner), "The native owned anchor must deploy one world-owned platform");
        return new Fixture(owner, pos, anchor, anchor.lift());
    }

    private static void cleanup(GameTestHelper h, Fixture fixture) {
        fixture.owner.closeContainer();
        var live = fixture.anchor.lift();
        if (live != null) live.cargo().clearContent();
        h.getLevel().setBlock(fixture.pos, Blocks.AIR.defaultBlockState(), 3);
        TestPlayers.remove(fixture.owner);
    }

    private static int dropped(GameTestHelper h, Fixture fixture, Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(fixture.pos).inflate(4, 54, 4), entity -> entity.getItem().is(item))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static ItemStack tool(int index) {
        var item = new ItemStack(Items.DIAMOND_PICKAXE);
        item.setDamageValue(11 + index * 7);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("V5 cargo slot " + index));
        var note = new CompoundTag();
        note.putInt("original_cargo_slot", index);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(note));
        return item;
    }

    private static FieldLiftEntity restore(GameTestHelper h, Fixture fixture, CompoundTag saved) {
        fixture.lift.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var loaded = EntityType.loadEntityRecursive(saved, h.getLevel(), entity -> entity);
        h.assertTrue(loaded instanceof FieldLiftEntity && h.getLevel().addFreshEntity(loaded), "Native reload must register the same saved world-owned vehicle");
        return (FieldLiftEntity) loaded;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyNineSlotNativeNbtRestoresInTheSameIndicesWithEighteenNewEmptySlots(GameTestHelper h) {
        var fixture = fixture(h);
        try {
            fixture.anchor.addFuel(123);
            var legacy = NonNullList.withSize(9, ItemStack.EMPTY);
            for (int slot = 0; slot < 9; slot++) legacy.set(slot, tool(slot));
            var saved = new CompoundTag();
            h.assertTrue(fixture.lift.save(saved), "The native platform must encode its real entity type, UUID and anchor binding");
            ContainerHelper.saveAllItems(saved, legacy, h.getLevel().registryAccess());
            var loaded = restore(h, fixture, saved);
            h.assertTrue(loaded.cargo().getContainerSize() == 27 && loaded.getUUID().equals(fixture.lift.getUUID())
                    && fixture.anchor.lift() == loaded && fixture.anchor.fuel() == 123 && loaded.quarantinedCargoRecords() == 0,
                    "A normal V5 nine-slot save must grow capacity without changing world ownership, fuel or inventing overflow");
            for (int slot = 0; slot < 9; slot++) h.assertTrue(ItemStack.matches(loaded.cargo().getItem(slot), legacy.get(slot)),
                    "The old slot number, name, damage, custom data and item count must survive migration: " + slot);
            for (int slot = 9; slot < 27; slot++) h.assertTrue(loaded.cargo().getItem(slot).isEmpty(), "New cargo capacity must start empty: " + slot);
            h.assertTrue(dropped(h, fixture, Items.DIAMOND_PICKAXE) == 0, "Native unload/reload must not emit a second copy of legacy cargo");
            var roundTrip = loaded.saveWithoutId(new CompoundTag());
            var decoded = RealmLift.LIFT.get().create(h.getLevel());
            decoded.load(roundTrip);
            for (int slot = 0; slot < 9; slot++) h.assertTrue(ItemStack.matches(decoded.cargo().getItem(slot), legacy.get(slot)), "The migrated 27-slot save must preserve every legacy component");
            decoded.discard();
            h.assertTrue(dropped(h, fixture, Items.DIAMOND_PICKAXE) == 0, "An unregistered duplicate decode object cannot drop the nine original items");
        } finally {
            cleanup(h, fixture);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeQuickMoveCrossesOldRowAndHotbarBoundariesWithoutChangingPlayerOrCargoCounts(GameTestHelper h) {
        var fixture = fixture(h);
        try {
            for (int slot = 0; slot < 9; slot++) fixture.lift.cargo().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            var expected = tool(9);
            fixture.owner.getInventory().setItem(9, expected.copy());
            fixture.owner.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 17));
            fixture.owner.setShiftKeyDown(true);
            h.assertTrue(fixture.lift.interact(fixture.owner, InteractionHand.MAIN_HAND).consumesAction()
                    && fixture.owner.containerMenu instanceof FieldLiftMenu, "Native platform interaction must open the actual owned three-row cargo menu");
            var menu = (FieldLiftMenu) fixture.owner.containerMenu;
            h.assertTrue(menu.slots.size() == 63 && menu.getRowCount() == 3 && menu.stillValid(fixture.owner),
                    "The real menu must contain exactly 27 cargo plus the unchanged 36 player slots");
            h.assertTrue(menu.quickMoveStack(fixture.owner, 27).is(Items.DIAMOND_PICKAXE)
                    && fixture.owner.getInventory().getItem(9).isEmpty() && ItemStack.matches(fixture.lift.cargo().getItem(9), expected),
                    "Player slot 9 must map to menu slot 27 and move intact into the first new cargo row after the old nine slots");
            h.assertTrue(menu.quickMoveStack(fixture.owner, 54).is(Items.GOLD_INGOT)
                    && fixture.owner.getInventory().getItem(0).isEmpty() && fixture.lift.cargo().getItem(10).getCount() == 17,
                    "The first unchanged hotbar slot must map to menu slot 54 and pay its stack once");
            fixture.lift.cargo().setItem(26, new ItemStack(Items.DIAMOND, 37));
            h.assertTrue(menu.quickMoveStack(fixture.owner, 26).is(Items.DIAMOND) && fixture.lift.cargo().getItem(26).isEmpty()
                    && fixture.owner.getInventory().countItem(Items.DIAMOND) == 37, "Quick-move from the last cargo slot must reach the native player inventory exactly once");
            int cobblestone = 0;
            for (int slot = 0; slot < 9; slot++) cobblestone += fixture.lift.cargo().getItem(slot).getCount();
            h.assertTrue(cobblestone == 9 * 64 && fixture.owner.getInventory().countItem(Items.GOLD_INGOT) == 0
                    && fixture.lift.cargo().getItem(10).getCount() == 17 && menu.quickMoveStack(fixture.owner, -1).isEmpty()
                    && menu.quickMoveStack(fixture.owner, 63).isEmpty(), "Valid boundary transfers and rejected external indices must leave all unrelated cargo and player quantities intact");
            fixture.lift.discard();
            h.assertTrue(!menu.stillValid(fixture.owner) && menu.quickMoveStack(fixture.owner, 10).isEmpty(), "Destroying the actual vehicle must revoke the old menu lease");
            h.assertTrue(dropped(h, fixture, Items.GOLD_INGOT) == 17 && dropped(h, fixture, Items.COBBLESTONE) == 9 * 64,
                    "The expanded platform must release all remaining cargo once after menu transfers");
            fixture.lift.discard();
            h.assertTrue(dropped(h, fixture, Items.GOLD_INGOT) == 17, "A repeated destruction cannot emit the expanded cargo twice");
        } finally {
            cleanup(h, fixture);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unsupportedCargoRecordsRoundTripRawWhileValidOverflowCanDropOnlyFromTheOwnedEntity(GameTestHelper h) {
        var fixture = fixture(h);
        try {
            var saved = new CompoundTag();
            h.assertTrue(fixture.lift.save(saved), "Overflow fixture requires the native world-owned entity identity");
            var normal = (CompoundTag) new ItemStack(Items.IRON_INGOT, 5).save(h.getLevel().registryAccess());
            normal.putByte("Slot", (byte) 0);
            var outside = (CompoundTag) new ItemStack(Items.IRON_INGOT, 17).save(h.getLevel().registryAccess());
            outside.putByte("Slot", (byte) 40);
            var duplicate = (CompoundTag) new ItemStack(Items.GOLD_INGOT, 3).save(h.getLevel().registryAccess());
            duplicate.putByte("Slot", (byte) 0);
            var oversized = (CompoundTag) new ItemStack(Items.COBBLESTONE, 64).save(h.getLevel().registryAccess());
            oversized.putByte("Slot", (byte) 2);
            oversized.putInt("count", 80);
            var unreadable = new CompoundTag();
            unreadable.putByte("Slot", (byte) 3);
            unreadable.putString("id", "interstice:unsupported_cargo_fixture");
            unreadable.putInt("count", 1);
            var entries = new ListTag();
            entries.add(normal.copy());
            var expected = new ListTag();
            for (var record : List.of(outside, duplicate, oversized, unreadable)) {
                entries.add(record.copy());
                expected.add(record.copy());
            }
            saved.put("Items", entries);
            var loaded = restore(h, fixture, saved);
            h.assertTrue(loaded.cargo().getItem(0).getCount() == 5 && loaded.cargo().getItem(2).isEmpty() && loaded.cargo().getItem(3).isEmpty()
                    && loaded.quarantinedCargoRecords() == 4, "Unsupported indices, duplicate slots and oversized/unreadable stacks cannot silently overwrite or truncate ordinary cargo");
            var roundTrip = loaded.saveWithoutId(new CompoundTag());
            h.assertTrue(roundTrip.getList("OverflowCargo", Tag.TAG_COMPOUND).equals(expected), "All unsupported original records must round-trip verbatim while the platform remains saved");
            var decoded = RealmLift.LIFT.get().create(h.getLevel());
            decoded.load(roundTrip);
            h.assertTrue(decoded.quarantinedCargoRecords() == 4
                    && decoded.saveWithoutId(new CompoundTag()).getList("OverflowCargo", Tag.TAG_COMPOUND).equals(expected), "Reloading the diagnostic overflow cannot change or renew its raw records");
            decoded.discard();
            h.assertTrue(dropped(h, fixture, Items.IRON_INGOT) == 0 && dropped(h, fixture, Items.GOLD_INGOT) == 0,
                    "An unregistered duplicate UUID decode object cannot release valid overflow or ordinary cargo");
            loaded.discard();
            h.assertTrue(dropped(h, fixture, Items.IRON_INGOT) == 22 && dropped(h, fixture, Items.GOLD_INGOT) == 3,
                    "Only the actual registered world entity may release known valid overflow once, without losing the ordinary five ingots");
            loaded.discard();
            h.assertTrue(dropped(h, fixture, Items.IRON_INGOT) == 22 && dropped(h, fixture, Items.GOLD_INGOT) == 3,
                    "Repeated removal must not renew valid overflow drops");
        } finally {
            cleanup(h, fixture);
        }
        h.succeed();
    }
}
