package pro.erez.interstice.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.agriculture.RetortBlockEntity;
import pro.erez.interstice.equipment.ExpeditionEquipment;
import pro.erez.interstice.gear.RealmGear;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.tide.BuoyancyController;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideState;
import pro.erez.interstice.worldgen.IslandWorld;

/** Native wear, service and catalytic production; no free first membrane or refilled save. */
@GameTestHolder("interstice_gear")
@PrefixGameTestTemplate(false)
public final class V6GearEconomyGameTests {
    private static final BlockPos MACHINE = new BlockPos(4, 2, 4);

    private static ItemStack namedBelt(String name, int damage) {
        var belt = new ItemStack(RealmGear.BALLAST_BELT.get());
        belt.setDamageValue(damage);
        belt.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        var note = new CompoundTag();
        note.putString("service_record", "retain the original pressure core");
        belt.set(DataComponents.CUSTOM_DATA, CustomData.of(note));
        return belt;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void activeWearFollowsTheHeldCoreAcrossHandsAndRetainsBothExhaustedItems(GameTestHelper h) {
        var player = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        var world = player.server.getLevel(IslandWorld.TALL_WORLD);
        h.assertTrue(world != null, "The old tall dimension must remain available for wear accounting");
        int x = 1002, z = 946;
        world.getChunk(x >> 4, z >> 4);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = 100; y <= 214; y++)
            world.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 3);
        player.teleportTo(world, x + .5, 100, z + .5, java.util.Set.of(), 0, 0);
        player.hasChangedDimension();
        player.setNoGravity(true);
        var first = namedBelt("original held core", RealmGear.BELT_MAX_DAMAGE - 2);
        var second = namedBelt("reserve core", RealmGear.BELT_MAX_DAMAGE - 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, first);
        player.setItemInHand(InteractionHand.OFF_HAND, second);
        var surge = new TideState(TidePhase.SURGE, 500, 2000, 0);
        for (int tick = 1; tick <= 60; tick++) {
            final int step = tick;
            h.runAtTickTime(step, () -> {
                try {
                    if (step == 11) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        player.getInventory().setItem(12, second);
                        player.setItemInHand(InteractionHand.OFF_HAND, first);
                    }
                    if (step == 21) {
                        h.assertTrue(first.getDamageValue() == RealmGear.BELT_MAX_DAMAGE - 1
                                && second.getDamageValue() == RealmGear.BELT_MAX_DAMAGE - 1,
                                "Ten ticks in each hand must total one wear unit on the same core; the reserve stays untouched");
                        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                        player.getInventory().setItem(13, first);
                        player.getInventory().setItem(12, ItemStack.EMPTY);
                        player.setItemInHand(InteractionHand.OFF_HAND, second);
                    }
                    if (step == 41) {
                        h.assertTrue(second.getCount() == 1 && second.getDamageValue() == RealmGear.BELT_MAX_DAMAGE
                                && RealmGear.buoyancyFactor(player) == 1,
                                "The last active second must preserve an inactive core; a stowed charged belt cannot keep protection");
                        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                        player.getInventory().setItem(12, second);
                        player.getInventory().setItem(13, ItemStack.EMPTY);
                        player.setItemInHand(InteractionHand.MAIN_HAND, first);
                    }
                    BuoyancyController.applyEntityBuoyancy(player, 1);
                    RealmGear.tick(player, surge);
                    RealmGear.tick(player, surge);
                } catch (RuntimeException | Error failure) {
                    TestPlayers.remove(player);
                    throw failure;
                }
            });
        }
        h.runAtTickTime(61, () -> {
            try {
                h.assertTrue(first.getCount() == 1 && second.getCount() == 1
                        && first.getDamageValue() == RealmGear.BELT_MAX_DAMAGE && second.getDamageValue() == RealmGear.BELT_MAX_DAMAGE
                        && RealmGear.buoyancyFactor(player) == 1,
                        "Sixty actual eligible ticks must spend three remaining seconds once and retain both inactive pressure cores");
                for (var belt : List.of(first, second)) {
                    var loaded = ItemStack.parse(world.registryAccess(), belt.save(world.registryAccess())).orElseThrow();
                    h.assertTrue(ItemStack.matches(belt, loaded) && loaded.getCount() == 1
                            && loaded.get(DataComponents.CUSTOM_DATA).copyTag().getString("service_record").equals("retain the original pressure core"),
                            "Native item serialization must preserve an exhausted core, its name and unrelated components");
                }
            } finally {
                TestPlayers.remove(player);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeAnvilConsumesFourLiningsAndServicesTheOriginalNamedCore(GameTestHelper h) {
        var player = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        h.setBlock(MACHINE, Blocks.ANVIL);
        player.giveExperienceLevels(20);
        try {
            var core = namedBelt("retained expedition ballast", RealmGear.BELT_MAX_DAMAGE);
            var originalName = core.get(DataComponents.CUSTOM_NAME);
            var originalData = core.get(DataComponents.CUSTOM_DATA);
            var menu = new AnvilMenu(47, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(MACHINE)));
            player.containerMenu = menu;
            menu.getSlot(0).set(core);
            menu.setItemName(core.getHoverName().getString());
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 4));
            menu.createResult();
            h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Ordinary external metal must not service the native pressure core");
            menu.getSlot(1).set(new ItemStack(RealmAgriculture.PURE_LINING.get(), 4));
            menu.createResult();
            h.assertTrue(menu.getSlot(2).getItem().is(RealmGear.BALLAST_BELT.get())
                    && menu.getSlot(2).getItem().getDamageValue() == 0 && menu.repairItemCountCost == 4 && menu.getCost() == 4,
                    "The actual anvil must offer one fully serviced core for four expensive native linings and four levels");
            menu.clicked(2, 0, ClickType.PICKUP, player);
            var serviced = menu.getCarried();
            h.assertTrue(serviced.is(RealmGear.BALLAST_BELT.get()) && serviced.getCount() == 1 && serviced.getDamageValue() == 0
                    && originalName.equals(serviced.get(DataComponents.CUSTOM_NAME)) && originalData.equals(serviced.get(DataComponents.CUSTOM_DATA))
                    && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty() && player.experienceLevel == 16,
                    "Taking the native result must pay lining and experience exactly once while preserving the original name and components");
            menu.clicked(2, 0, ClickType.PICKUP, player);
            h.assertTrue(menu.getCarried().getCount() == 1 && menu.getSlot(2).getItem().isEmpty(), "A second result click cannot copy the repaired pressure core");
            player.getInventory().setItem(12, serviced);
            menu.setCarried(ItemStack.EMPTY);
            player.closeContainer();
        } finally {
            TestPlayers.remove(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void catalyticMembraneBatchRequiresTheFirstFindAndSurvivesPendingReloadWithoutDuplication(GameTestHelper h) {
        h.setBlock(MACHINE, RealmAgriculture.RETORT.get());
        var pos = h.absolutePos(MACHINE);
        var retort = (RetortBlockEntity) h.getLevel().getBlockEntity(pos);
        var recipe = ResourceLocation.fromNamespaceAndPath(Interstice.ID, "retort_pressure_coupler");
        h.assertTrue(retort.selectRecipe(recipe), "The installed native retort recipe must expose catalytic membrane production");
        retort.setItem(1, new ItemStack(Interstice.RIFTSILVER_INGOT.get(), 4));
        retort.setItem(2, new ItemStack(RealmAgriculture.PURE_LINING.get(), 4));
        retort.setItem(3, new ItemStack(RealmAgriculture.PASTE.get(), 4));
        retort.setItem(4, new ItemStack(RealmAgriculture.SORBENT.get(), 4));
        retort.setItem(5, new ItemStack(ExpeditionEquipment.CHEMOTROPHIC_FABRIC.get(), 4));
        retort.setItem(6, new ItemStack(MineralEcology.UMBRAL_COAL.get()));
        retort.process();
        h.assertTrue(!retort.hasBatch() && retort.getItem(7).isEmpty() && retort.data.get(2) == 0 && retort.getItem(6).getCount() == 1,
                "External preparation and native materials alone cannot create a free first membrane or burn waiting fuel");
        for (int slot = 1; slot <= 5; slot++) h.assertTrue(retort.getItem(slot).getCount() == 4, "A missing catalyst cannot reserve any partial ingredient");
        retort.setItem(0, new ItemStack(Interstice.PRESSURE_COUPLER.get()));
        for (int tick = 0; tick < 557; tick++) retort.process();
        h.assertTrue(retort.hasBatch() && retort.data.get(0) == 557 && retort.data.get(1) == 3200
                && retort.data.get(2) == 2643 && retort.getItem(6).isEmpty() && retort.getItem(7).isEmpty(),
                "One real coal must reserve the first membrane and all five costly ingredients for the full 3200-step process");
        for (int slot = 0; slot <= 5; slot++) h.assertTrue(retort.getItem(slot).isEmpty(), "The saved batch must exclusively own all six counted inputs");
        var tag = retort.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored = new RetortBlockEntity(pos, h.getLevel().getBlockState(pos));
        restored.loadWithComponents(tag, h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(restored);
        h.assertTrue(restored.hasBatch() && restored.data.get(0) == 557 && restored.data.get(2) == 2643
                && restored.preview().getFirst().is(Interstice.PRESSURE_COUPLER.get()) && restored.preview().getFirst().getCount() == 2,
                "Native block-entity reload must retain progress, paid heat and the pending two-membrane output snapshot");
        for (int tick = 557; tick < 3200; tick++) restored.process();
        h.assertTrue(!restored.hasBatch() && restored.data.get(2) == 0 && restored.getItem(7).is(Interstice.PRESSURE_COUPLER.get())
                && restored.getItem(7).getCount() == 2 && restored.getItem(8).isEmpty() && restored.getItem(9).isEmpty(),
                "Exactly one restored paid batch must produce two membranes, a net gain of one from the original find");
        for (int tick = 0; tick < 3200; tick++) restored.process();
        h.assertTrue(!restored.hasBatch() && restored.getItem(7).getCount() == 2 && restored.data.get(2) == 0,
                "Output membranes cannot catalyse a second batch without new materials and finite heat");
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        restored.release();
        restored.release();
        int dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
                entity -> entity.getItem().is(Interstice.PRESSURE_COUPLER.get())).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        h.assertTrue(dropped == 2, "Destruction plus repeated release callbacks must drop only the two completed membranes");
        h.succeed();
    }
}
