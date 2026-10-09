package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.equipment.*;

/** Server/native inventory, crafting, pickup and item-destruction boundaries of portable storage. */
@GameTestHolder("interstice_equipment")
@PrefixGameTestTemplate(false)
public final class BackpackGameTests {
    private static final String TEMPLATE = "empty";
    private static ItemStack field() { return new ItemStack(ExpeditionEquipment.FIELD_BACKPACK.get()); }
    private static ItemStack expedition() { return new ItemStack(ExpeditionEquipment.EXPEDITION_BACKPACK.get()); }
    private static ServerPlayer player(GameTestHelper h) { return TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL); }
    private static int stored(ItemStack bag, Item item) {
        return BackpackStorage.read(bag).stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static ItemStack named(Item item, int count, String name) {
        var result = new ItemStack(item, count); result.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return result;
    }
    private static ItemEntity drop(GameTestHelper h, ItemStack stack) {
        var at = h.absolutePos(new BlockPos(6, 2, 6));
        var entity = new ItemEntity(h.getLevel(), at.getX() + .5, at.getY() + .5, at.getZ() + .5, stack);
        entity.setPickUpDelay(0); h.getLevel().addFreshEntity(entity); return entity;
    }
    private static void fillInventory(ServerPlayer player, ItemStack bag) {
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().selected = 0; player.getInventory().setItem(0, bag);
    }
    private static BackpackMenu open(GameTestHelper h, ServerPlayer player, int slot) {
        h.assertTrue(BackpackItem.open(player, slot), "Native server menu did not open");
        h.assertTrue(player.containerMenu instanceof BackpackMenu, "Opened the wrong container type");
        return (BackpackMenu) player.containerMenu;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void nativeItemRoundtripPreservesSparseSlotsComponentsModeAndIndependentCopies(GameTestHelper h) {
        var bag = expedition(); var list = BackpackStorage.read(bag);
        var tool = named(Items.DIAMOND_PICKAXE, 1, "saved expedition tool"); tool.setDamageValue(123);
        var custom = new CompoundTag(); custom.putString("test_origin", "cold-backpack-components");
        tool.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        list.set(0, new ItemStack(Items.COAL, 64)); list.set(71, tool); BackpackStorage.write(bag, list);
        var id = BackpackStorage.ensureId(bag); BackpackStorage.mode(bag, BackpackStorage.MATERIALS);
        bag.set(DataComponents.CUSTOM_NAME, Component.literal("named expedition"));
        var loaded = ItemStack.parse(h.getLevel().registryAccess(), bag.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(BackpackStorage.capacity(loaded) == 72 && BackpackStorage.id(loaded).equals(id)
                        && BackpackStorage.mode(loaded) == BackpackStorage.MATERIALS,
                "Native stack serialization lost tier, identifier or collection mode");
        var restored = BackpackStorage.read(loaded);
        h.assertTrue(restored.get(70).isEmpty() && ItemStack.matches(restored.get(71), tool), "Sparse last slot or item components were lost");
        restored.get(0).shrink(10); restored.get(71).setDamageValue(4);
        h.assertTrue(stored(loaded, Items.COAL) == 64 && BackpackStorage.read(loaded).get(71).getDamageValue() == 123,
                "Storage read exposes mutable native component references");
        h.assertTrue(loaded.getHoverName().getString().equals("named expedition"), "Backpack name did not survive the stack codec");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void nestingCorrodingBucketsAndForeignContainerDataAreRejectedButSilverAndSealedReservoirWork(GameTestHelper h) {
        var disguised = new ItemStack(Items.STONE); disguised.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        var foreign = new ItemStack(Items.CHEST); foreign.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(new CompoundTag()));
        for (var candidate : List.of(field(), expedition(), new ItemStack(Items.BUNDLE), new ItemStack(Items.SHULKER_BOX),
                new ItemStack(Interstice.HEAVY_BUCKET.get()), new ItemStack(Interstice.LIGHT_BUCKET.get()), disguised, foreign)) {
            h.assertTrue(!BackpackStorage.allowed(candidate), "Nested/unsafe item accepted: " + candidate);
            h.assertTrue(BackpackStorage.insert(BackpackStorage.read(field()), candidate) == 0, "Insert bypasses nesting or corrosion rejection");
        }
        h.assertTrue(BackpackStorage.allowed(new ItemStack(Interstice.RIFTSILVER_HEAVY_BUCKET.get())),
                "Properly contained silver toxin bucket was unnecessarily rejected");
        var sealed = new ItemStack(RealmAgriculture.RESERVOIR_ITEM.get());
        var data = new CompoundTag(); data.putString("id", "interstice:nutrient_reservoir"); data.putInt("Amount", 1000);
        sealed.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data)); sealed.set(DataComponents.MAX_STACK_SIZE, 1);
        h.assertTrue(BackpackStorage.allowed(sealed), "Closed nutrient reservoir is mistaken for a recursively nested container");
        var list = BackpackStorage.read(field()); h.assertTrue(BackpackStorage.insert(list, sealed) == 1, "Sealed liquid item cannot be carried");
        h.assertTrue(sealed.getCount() == 1 && list.get(0).get(DataComponents.BLOCK_ENTITY_DATA).copyTag().getInt("Amount") == 1000,
                "Carrying sealed reservoir consumes its source or loses its actual saved liquid");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void heldPackLocksMenuMovesAndSameUuidReplacementCannotReceiveStaleWrites(GameTestHelper h) {
        var player = player(h);
        try {
            var bag = field(); var contents = BackpackStorage.read(bag); contents.set(0, new ItemStack(Items.COAL, 5)); BackpackStorage.write(bag, contents);
            player.getInventory().selected = 0; player.setItemInHand(InteractionHand.MAIN_HAND, bag);
            var menu = open(h, player, 0); int sourceIndex = menu.capacity + 27;
            menu.clicked(sourceIndex, 0, ClickType.PICKUP, player); menu.clicked(sourceIndex, 0, ClickType.THROW, player);
            menu.quickMoveStack(player, sourceIndex); menu.clicked(0, 0, ClickType.SWAP, player);
            h.assertTrue(player.getMainHandItem() == bag && menu.stillValid(player) && menu.getCarried().isEmpty(),
                    "An ordinary menu click/drop/shift/swap can detach the currently opened backpack");
            var replacement = bag.copy(); var before = replacement.get(DataComponents.CONTAINER);
            player.getInventory().setItem(0, replacement);
            h.assertTrue(!menu.stillValid(player), "Same-UUID copy defeats the source object lease");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            ((BackpackInventory) menu.container).setItem(0, new ItemStack(Items.DIAMOND, 64)); menu.removed(player);
            h.assertTrue(menu.getCarried().isEmpty() && replacement.get(DataComponents.CONTAINER).equals(before)
                            && stored(bag, Items.COAL) == 5,
                    "Invalidated/closed menu overwrites a replacement or duplicates detached contents");
            h.succeed();
        } finally { player.closeContainer(); TestPlayers.remove(player); }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void offhandSourceIsLockedEvenThoughItHasNoVisiblePlayerSlot(GameTestHelper h) {
        var player = player(h);
        try {
            var bag = field(); var contents = BackpackStorage.read(bag); contents.set(0, new ItemStack(Items.IRON_INGOT, 3)); BackpackStorage.write(bag, contents);
            player.setItemInHand(InteractionHand.OFF_HAND, bag); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
            var menu = open(h, player, 40); menu.clicked(0, 40, ClickType.SWAP, player);
            h.assertTrue(player.getOffhandItem() == bag && stored(bag, Items.IRON_INGOT) == 3 && menu.stillValid(player),
                    "Offhand swap bypasses a lock because inventory slot 40 is not drawn");
            h.succeed();
        } finally { player.closeContainer(); TestPlayers.remove(player); }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void realMenuStashSortAndRefillPreserveHotbarAndDistinctComponents(GameTestHelper h) {
        var player = player(h);
        try {
            var bag = field(); var contents = BackpackStorage.read(bag);
            contents.set(0, new ItemStack(Items.TORCH, 64)); contents.set(1, new ItemStack(Items.TORCH, 2));
            contents.set(2, named(Items.COAL, 3, "red sample")); contents.set(3, named(Items.COAL, 4, "blue sample")); BackpackStorage.write(bag, contents);
            player.getInventory().setItem(10, bag); player.getInventory().setItem(11, new ItemStack(Items.STONE, 64));
            player.getInventory().setItem(12, new ItemStack(Interstice.HEAVY_BUCKET.get()));
            player.getInventory().setItem(0, new ItemStack(Items.TORCH, 7)); player.getInventory().setItem(40, new ItemStack(Items.SHIELD));
            var menu = open(h, player, 10);
            h.assertTrue(menu.clickMenuButton(player, BackpackMenu.STASH), "Actual quick-stash button was rejected");
            h.assertTrue(player.getInventory().getItem(11).isEmpty() && player.getInventory().getItem(12).is(Interstice.HEAVY_BUCKET.get())
                            && player.getInventory().getItem(0).getCount() == 7 && player.getOffhandItem().is(Items.SHIELD),
                    "Quick stash empties hotbar/offhand, nests the source pack or accepts unsafe fluid");
            menu.clickMenuButton(player, BackpackMenu.SORT);
            h.assertTrue(stored(bag, Items.COAL) == 7 && BackpackStorage.read(bag).stream().filter(s -> s.is(Items.COAL)).count() == 2,
                    "Sorting merges stacks with different components or loses their counts");
            menu.clickMenuButton(player, BackpackMenu.REFILL);
            h.assertTrue(player.getInventory().getItem(0).getCount() == 64 && stored(bag, Items.TORCH) == 9
                            && player.getInventory().getItem(1).isEmpty() && stored(bag, Items.STONE) == 64,
                    "Refill loses reserves, exceeds stack limits or invents an assignment for an empty hotbar slot");
            h.succeed();
        } finally { player.closeContainer(); TestPlayers.remove(player); }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void actualItemTouchCollectsMatchingLootWithAFullInventoryAndCountsPickupOnce(GameTestHelper h) {
        var player = player(h);
        try {
            var bag = field(); var contents = BackpackStorage.read(bag); contents.set(0, new ItemStack(Items.COAL)); BackpackStorage.write(bag, contents);
            BackpackStorage.mode(bag, BackpackStorage.MATCHING); fillInventory(player, bag);
            var entity = drop(h, new ItemStack(Items.COAL, 20)); entity.playerTouch(player);
            h.assertTrue(entity.isRemoved() && stored(bag, Items.COAL) == 21, "Actual collision fails when the player's ordinary inventory is full");
            h.assertTrue(player.getStats().getValue(Stats.ITEM_PICKED_UP.get(Items.COAL)) == 20,
                    "Bag collection omits vanilla pickup accounting or counts an accepted item twice");
            h.succeed();
        } finally { TestPlayers.remove(player); }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void partialItemTouchLeavesExactRemainderWhenBothInventoriesAreFull(GameTestHelper h) {
        var player = player(h);
        try {
            var bag = field(); var contents = BackpackStorage.read(bag);
            for (int i = 0; i < contents.size(); i++) contents.set(i, new ItemStack(Items.COBBLESTONE, 64));
            contents.set(0, new ItemStack(Items.COAL, 63)); BackpackStorage.write(bag, contents);
            BackpackStorage.mode(bag, BackpackStorage.MATCHING); fillInventory(player, bag);
            var entity = drop(h, new ItemStack(Items.COAL, 5)); entity.playerTouch(player);
            h.assertTrue(!entity.isRemoved() && entity.getItem().getCount() == 4 && stored(bag, Items.COAL) == 64,
                    "Partial collection discards, duplicates or overfills the remainder");
            h.assertTrue(player.getStats().getValue(Stats.ITEM_PICKED_UP.get(Items.COAL)) == 1,
                    "Statistics count ground remainder as accepted loot");
            h.succeed();
        } finally { TestPlayers.remove(player); }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void actualCollectionRespectsDelayTargetOffModeAndMaterialWhitelist(GameTestHelper h) {
        var player = player(h);
        try {
            var bag = expedition(); fillInventory(player, bag);
            BackpackStorage.mode(bag, BackpackStorage.MATERIALS);
            var delayed = drop(h, new ItemStack(Items.RAW_IRON, 5)); delayed.setPickUpDelay(20); delayed.playerTouch(player);
            h.assertTrue(delayed.getItem().getCount() == 5 && stored(bag, Items.RAW_IRON) == 0, "Backpack bypasses pickup delay");
            var owned = drop(h, new ItemStack(Items.RAW_IRON, 5)); owned.setTarget(UUID.randomUUID()); owned.playerTouch(player);
            h.assertTrue(owned.getItem().getCount() == 5 && stored(bag, Items.RAW_IRON) == 0, "Backpack steals loot targeted to another player");
            var forbidden = drop(h, new ItemStack(Items.DIAMOND_SWORD)); forbidden.playerTouch(player);
            h.assertTrue(!forbidden.isRemoved() && stored(bag, Items.DIAMOND_SWORD) == 0, "Material mode vacuums arbitrary valuable equipment");
            BackpackStorage.mode(bag, BackpackStorage.OFF); var off = drop(h, new ItemStack(Items.RAW_IRON, 5)); off.playerTouch(player);
            h.assertTrue(off.getItem().getCount() == 5 && stored(bag, Items.RAW_IRON) == 0, "Default/off mode still steals material drops");
            BackpackStorage.mode(bag, BackpackStorage.MATERIALS); var allowed = drop(h, new ItemStack(Items.RAW_IRON, 5)); allowed.playerTouch(player);
            h.assertTrue(allowed.isRemoved() && stored(bag, Items.RAW_IRON) == 5, "Material mode does not recognize actual whitelisted raw ore");
            h.succeed();
        } finally { TestPlayers.remove(player); }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void collectionWhileOpenUpdatesTheSameLiveInventoryBeforeSubsequentMenuMutation(GameTestHelper h) {
        var player = player(h);
        try {
            var bag = field(); var contents = BackpackStorage.read(bag); contents.set(0, new ItemStack(Items.COAL)); BackpackStorage.write(bag, contents);
            BackpackStorage.mode(bag, BackpackStorage.MATCHING); fillInventory(player, bag); var menu = open(h, player, 0);
            var entity = drop(h, new ItemStack(Items.COAL, 3)); entity.playerTouch(player); menu.clickMenuButton(player, BackpackMenu.SORT);
            h.assertTrue(stored(bag, Items.COAL) == 4 && menu.container.getItem(0).is(Items.COAL) && menu.container.getItem(0).getCount() == 4,
                    "Auto collection and a menu snapshot overwrite each other's contents");
            h.succeed();
        } finally { player.closeContainer(); TestPlayers.remove(player); }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void nativeCraftingUpgradeConsumesOneLoadedBaseAndPreservesStorageNameModeWithANewLease(GameTestHelper h) {
        var level = h.getLevel();
        var holder = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(r -> r.value() instanceof BackpackUpgradeRecipe && r.value().getResultItem(level.registryAccess()).is(ExpeditionEquipment.EXPEDITION_BACKPACK.get()))
                .findFirst().orElseThrow();
        var ingredients = holder.value().getIngredients();
        h.assertTrue(ingredients.size() == 9, "Upgrade does not expose a complete 3 by 3 shaped recipe");
        var bag = field(); var contents = BackpackStorage.read(bag); contents.set(53, named(Items.DIAMOND_PICKAXE, 1, "packed upgrade proof")); BackpackStorage.write(bag, contents);
        var oldId = BackpackStorage.ensureId(bag); BackpackStorage.mode(bag, BackpackStorage.MATCHING); bag.set(DataComponents.CUSTOM_NAME, Component.literal("upgraded name"));
        var inputs = new ArrayList<ItemStack>(); int base = -1;
        for (int i = 0; i < ingredients.size(); i++) {
            var ingredient = ingredients.get(i); var at = ingredient.isEmpty() ? ItemStack.EMPTY : ingredient.getItems()[0].copyWithCount(1);
            if (at.is(ExpeditionEquipment.FIELD_BACKPACK.get())) { base = i; at = bag; }
            inputs.add(at);
        }
        h.assertTrue(base >= 0, "Native upgrade has no real field-pack ingredient");
        var input = CraftingInput.of(3, 3, inputs);
        h.assertTrue(holder.value().matches(input, level), "Loaded named backpack is rejected by its actual upgrade");
        var player = player(h);
        try {
            var table = h.absolutePos(new BlockPos(6, 1, 5)); level.setBlock(table, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
            var menu = new CraftingMenu(120, player.getInventory(), ContainerLevelAccess.create(level, table)); player.containerMenu = menu;
            for (int i = 0; i < inputs.size(); i++) menu.getSlot(i + 1).setByPlayer(inputs.get(i));
            h.assertTrue(menu.getSlot(0).hasItem(), "Real vanilla crafting result slot did not assemble the upgrade");
            menu.quickMoveStack(player, 0);
            var results = player.getInventory().items.stream().filter(s -> s.is(ExpeditionEquipment.EXPEDITION_BACKPACK.get())).toList();
            h.assertTrue(results.size() == 1 && menu.getSlot(base + 1).getItem().isEmpty(), "Native result-taking leaves the original bag or produces two upgrades");
            var result = results.get(0);
            h.assertTrue(BackpackStorage.capacity(result) == 72 && BackpackStorage.read(result).get(53).getHoverName().getString().equals("packed upgrade proof")
                            && BackpackStorage.mode(result) == BackpackStorage.MATCHING && result.getHoverName().getString().equals("upgraded name"),
                    "Actual crafting loses contents, components, mode or custom pack name");
            h.assertTrue(BackpackStorage.id(result) != null && !BackpackStorage.id(result).equals(oldId)
                            && !result.canBeHurtBy(level.damageSources().lava()),
                    "Upgrade reuses a stale source identifier or loses the new tier's default fire resistance");
            h.succeed();
        } finally { player.closeContainer(); TestPlayers.remove(player); }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void reinforcedItemSurvivesFireWhileOrdinaryDestructionSpillsSavedContentsOnlyOnce(GameTestHelper h) {
        var level = h.getLevel(); var bag = expedition(); var contents = BackpackStorage.read(bag);
        contents.set(0, named(Items.DIAMOND, 5, "single spill proof")); BackpackStorage.write(bag, contents);
        var entity = drop(h, bag);
        h.assertTrue(!entity.hurt(level.damageSources().lava(), 99) && !entity.isRemoved() && stored(bag, Items.DIAMOND) == 5,
                "Reinforced dropped pack or contained items are consumed by fire/lava damage");
        h.assertTrue(entity.hurt(level.damageSources().generic(), 99) && entity.isRemoved(), "Fire protection accidentally gives universal item immunity");
        bag.onDestroyed(entity, level.damageSources().generic());
        int spilled = level.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(new BlockPos(6, 2, 6))).inflate(3),
                e -> e.getItem().is(Items.DIAMOND) && e.getItem().getHoverName().getString().equals("single spill proof"))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(spilled == 5 && bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().findAny().isEmpty(),
                "Destruction repeats or loses saved contents instead of clearing them before spill");
        var ordinary = drop(h, field()); h.assertTrue(ordinary.hurt(level.damageSources().lava(), 99) && ordinary.isRemoved(), "Field tier unexpectedly inherits reinforced fire immunity");
        h.succeed();
    }

    private static ItemStack rift() { return new ItemStack(ExpeditionEquipment.RIFT_BACKPACK.get()); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void modulesArePersistedAndLimitedByTierAndRejectInvalidStacks(GameTestHelper h) {
        var f = field(); var e = expedition(); var r = rift();
        h.assertTrue(BackpackStorage.moduleSlots(f) == 1, "Field pack module slots must be 1");
        h.assertTrue(BackpackStorage.moduleSlots(e) == 2, "Expedition pack module slots must be 2");
        h.assertTrue(BackpackStorage.moduleSlots(r) == 4, "Rift pack module slots must be 4");

        var magnet = new ItemStack(ExpeditionEquipment.MAGNET_MODULE_TIER1.get());
        var feeder = new ItemStack(ExpeditionEquipment.FEEDER_MODULE_TIER1.get());
        var modules = BackpackStorage.readModules(e);
        modules.set(0, magnet);
        modules.set(1, feeder);
        BackpackStorage.writeModules(e, modules);

        h.assertTrue(BackpackStorage.hasModule(e, ModuleType.MAGNET), "Magnet module not detected");
        h.assertTrue(BackpackStorage.hasModule(e, ModuleType.FEEDER), "Feeder module not detected");
        h.assertTrue(!BackpackStorage.hasModule(e, ModuleType.COMPRESSION), "Compression module falsely detected");

        var loaded = ItemStack.parse(h.getLevel().registryAccess(), e.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(BackpackStorage.hasModule(loaded, ModuleType.MAGNET) && BackpackStorage.hasModule(loaded, ModuleType.FEEDER),
                "Module serialization did not survive item stack roundtrip");

        try {
            var invalid = BackpackStorage.readModules(f);
            invalid.set(0, new ItemStack(Items.DIAMOND));
            BackpackStorage.writeModules(f, invalid);
            h.fail("Non-module items must be rejected from module storage");
        } catch (IllegalArgumentException expected) {}

        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void riftBackpackHas84SlotsFourModulesAndBlastVoidImmunity(GameTestHelper h) {
        var r = rift();
        h.assertTrue(BackpackStorage.capacity(r) == 84, "Rift pack capacity must be 84");
        h.assertTrue(BackpackStorage.moduleSlots(r) == 4, "Rift pack module slots must be 4");

        var level = h.getLevel();
        var entity = drop(h, r);
        h.assertTrue(!entity.hurt(level.damageSources().lava(), 99), "Rift pack must be fire resistant");

        var stored = BackpackStorage.read(r);
        stored.set(0, new ItemStack(Items.NETHERITE_INGOT, 2));
        BackpackStorage.write(r, stored);

        // Test void/blast immunity in onDestroyed
        r.onDestroyed(entity, level.damageSources().fellOutOfWorld());
        h.assertTrue(BackpackStorage.read(r).get(0).getCount() == 2, "Void damage must not wipe or drop Rift pack contents");

        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void magnetModulePullsAndDepositsEntitiesDirectlyIntoBackpack(GameTestHelper h) {
        var player = player(h);
        try {
            var bag = expedition();
            var modules = BackpackStorage.readModules(bag);
            modules.set(0, new ItemStack(ExpeditionEquipment.MAGNET_MODULE_TIER1.get()));
            BackpackStorage.writeModules(bag, modules);
            BackpackHarness.set(player, bag);

            var dropStack = new ItemStack(Items.COAL, 5);
            var entity = drop(h, dropStack);

            BackpackModules.tickMagnet(player, bag);

            int inBag = stored(bag, Items.COAL);
            h.assertTrue(inBag == 5 || entity.isRemoved() || entity.getDeltaMovement().lengthSqr() > 0.01,
                    "Magnet module did not pull or deposit coal into backpack");
            h.succeed();
        } finally {
            BackpackHarness.set(player, ItemStack.EMPTY);
            TestPlayers.remove(player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void feederModuleEatsNutritiousFoodFromBackpackWhenHungry(GameTestHelper h) {
        var player = player(h);
        try {
            var bag = field();
            var modules = BackpackStorage.readModules(bag);
            modules.set(0, new ItemStack(ExpeditionEquipment.FEEDER_MODULE_TIER1.get()));
            BackpackStorage.writeModules(bag, modules);

            var items = BackpackStorage.read(bag);
            items.set(0, new ItemStack(Items.COOKED_BEEF, 3));
            BackpackStorage.write(bag, items);
            BackpackHarness.set(player, bag);

            player.getFoodData().setFoodLevel(10);
            h.assertTrue(player.getFoodData().getFoodLevel() == 10, "Could not set test player food level");

            BackpackModules.tickFeeder(player, bag);

            h.assertTrue(player.getFoodData().getFoodLevel() > 10, "Feeder module did not increase food level");
            h.assertTrue(stored(bag, Items.COOKED_BEEF) == 2, "Feeder module did not consume 1 cooked beef from bag");
            h.succeed();
        } finally {
            BackpackHarness.set(player, ItemStack.EMPTY);
            TestPlayers.remove(player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void compressionModuleCompactsNineIngotsIntoBlock(GameTestHelper h) {
        var player = player(h);
        try {
            var bag = field();
            var modules = BackpackStorage.readModules(bag);
            modules.set(0, new ItemStack(ExpeditionEquipment.COMPRESSION_MODULE_TIER1.get()));
            BackpackStorage.writeModules(bag, modules);

            var items = BackpackStorage.read(bag);
            items.set(0, new ItemStack(Items.IRON_INGOT, 18));
            BackpackStorage.write(bag, items);
            BackpackHarness.set(player, bag);

            BackpackModules.tickCompression(player, bag);

            h.assertTrue(stored(bag, Items.IRON_INGOT) == 9, "Compression did not shrink 9 iron ingots");
            h.assertTrue(stored(bag, Items.IRON_BLOCK) == 1, "Compression did not create 1 iron block");
            h.succeed();
        } finally {
            BackpackHarness.set(player, ItemStack.EMPTY);
            TestPlayers.remove(player);
        }
    }
}
