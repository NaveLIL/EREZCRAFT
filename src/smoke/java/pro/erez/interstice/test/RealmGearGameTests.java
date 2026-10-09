package pro.erez.interstice.test;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ToxicLiquidBlock;
import pro.erez.interstice.ecology.ClingweedGas;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.agriculture.RetortBlockEntity;
import pro.erez.interstice.equipment.ExpeditionEquipment;
import pro.erez.interstice.gear.*;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.tide.BuoyancyController;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideState;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder("interstice_gear")
@PrefixGameTestTemplate(false)
public final class RealmGearGameTests {
    private static ServerPlayer player(GameTestHelper h) { return TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL); }
    private static ServerPlayer tidalPlayer(GameTestHelper h, int x) {
        var player = player(h); var world = h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD);
        h.assertTrue(world != null, "Retained tall dimension is required for native tide checks");
        world.getChunk(x >> 4, 750 >> 4);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = 100; y <= 214; y++)
            world.setBlock(new BlockPos(x + dx, y, 750 + dz), Blocks.AIR.defaultBlockState(), 3);
        player.teleportTo(world, x + .5, 100, 750.5, java.util.Set.of(), 0, 0); player.hasChangedDimension();
        return player;
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void optionalBallastDoesNotAlterOrdinaryWorldOrStowedInventory(GameTestHelper h) {
        var player = tidalPlayer(h, 750);
        try {
            var belt = new ItemStack(RealmGear.BALLAST_BELT.get()); player.getInventory().setItem(12, belt);
            h.assertTrue(RealmGear.buoyancyFactor(player) == 1, "A stowed belt cannot change existing tide physics");
            player.setItemInHand(InteractionHand.OFF_HAND, belt);
            h.assertTrue(RealmGear.buoyancyFactor(player) == .62, "A held belt must provide its documented optional counterweight");
            RealmGear.tick(player, new TideState(TidePhase.CALM, 0, 12000, 0));
            h.assertTrue(belt.getDamageValue() == 0, "Calm phases cannot consume the belt");
            player.teleportTo(h.getLevel(), h.absolutePos(new BlockPos(6, 2, 6)).getX(), h.absolutePos(new BlockPos(6, 2, 6)).getY(),
                    h.absolutePos(new BlockPos(6, 2, 6)).getZ(), java.util.Set.of(), 0, 0); player.hasChangedDimension();
            h.assertTrue(RealmGear.buoyancyFactor(player) == 1, "The ordinary world must keep native gravity even with held ballast");
        } finally { TestPlayers.remove(player); }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ballastConsumesLoadedActiveTicksOnceAndOnlyTheSelectedHand(GameTestHelper h) {
        var player = tidalPlayer(h, 780);
        var main = new ItemStack(RealmGear.BALLAST_BELT.get()); var off = new ItemStack(RealmGear.BALLAST_BELT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, main); player.setItemInHand(InteractionHand.OFF_HAND, off);
        player.setNoGravity(true);
        TideState surge = new TideState(TidePhase.SURGE, 500, 2000, 0);
        for (int step = 1; step <= 20; step++) h.runAtTickTime(step, () -> {
            BuoyancyController.applyEntityBuoyancy(player, 1);
            RealmGear.tick(player, surge); RealmGear.tick(player, surge);
        });
        h.runAtTickTime(21, () -> {
            try {
                h.assertTrue(main.getDamageValue() == 1 && off.getDamageValue() == 0, "Twenty eligible server ticks must wear one held belt once, not both hands or duplicate calls");
                var tag = main.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.putInt("interstice_ballast_active_ticks", 19); tag.remove("interstice_ballast_wear_tick");
                main.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); main.setDamageValue(RealmGear.BELT_MAX_DAMAGE - 1);
                BuoyancyController.applyEntityBuoyancy(player, 1); RealmGear.tick(player, surge);
                h.assertTrue(main.getCount() == 1 && main.getDamageValue() == RealmGear.BELT_MAX_DAMAGE
                        && off.getCount() == 1 && off.getDamageValue() == 0,
                        "The final wear unit must retain the exhausted selected core without consuming or wearing the other hand");
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                h.assertTrue(RealmGear.buoyancyFactor(player) == 1, "An exhausted belt cannot leave permanent counterweight behind");
            } finally { TestPlayers.remove(player); }
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coatingUsePreservesChestplateComponentsAndConsumesOneDose(GameTestHelper h) {
        var player = player(h);
        try {
            var chest = new ItemStack(Items.DIAMOND_CHESTPLATE); chest.setDamageValue(73);
            chest.set(DataComponents.CUSTOM_NAME, Component.literal("Expedition armour"));
            var origin = new CompoundTag(); origin.putString("owner_note", "preserve me"); chest.set(DataComponents.CUSTOM_DATA, CustomData.of(origin));
            var dose = new ItemStack(RealmGear.PROTECTIVE_COATING.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, dose); player.setItemInHand(InteractionHand.OFF_HAND, chest);
            h.assertTrue(RealmGear.PROTECTIVE_COATING.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "Native use must apply to chest armour in the other hand");
            h.assertTrue(dose.getCount() == 1 && RealmGear.coatingCharges(chest) == RealmGear.COATING_CHARGES, "Applying coating must consume exactly one dose");
            var loaded = ItemStack.parse(h.getLevel().registryAccess(), chest.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(loaded.getDamageValue() == 73 && loaded.getHoverName().getString().equals("Expedition armour")
                    && loaded.get(DataComponents.CUSTOM_DATA).copyTag().getString("owner_note").equals("preserve me")
                    && RealmGear.coatingCharges(loaded) == RealmGear.COATING_CHARGES, "Native serialization must retain armour damage, name, unrelated data and coating");
            RealmGear.PROTECTIVE_COATING.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            h.assertTrue(dose.getCount() == 1, "An already coated chestplate must not waste another dose");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ELYTRA));
            h.assertTrue(!RealmGear.PROTECTIVE_COATING.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction()
                    && dose.getCount() == 1, "Non-chestplate items must reject coating without consumption");
        } finally { TestPlayers.remove(player); }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 340)
    public static void coatingProtectsFiniteFreshHazardsAndNeverGrantsPermanentImmunity(GameTestHelper h) {
        h.setBlock(new BlockPos(6, 1, 6), Blocks.STONE);
        var player = player(h); player.setNoGravity(true);
        var armor = new ItemStack(Items.IRON_CHESTPLATE);
        try {
            h.assertTrue(RealmGear.applyCoating(armor), "Eligible chestplate must accept one coating");
            player.getInventory().setItem(13, armor);
            h.assertTrue(!RealmGear.protect(player, RealmGear.HazardKind.CAVE_GAS), "Coating in inventory cannot protect the wearer");
            player.getInventory().setItem(13, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.CHEST, armor);
        } catch (RuntimeException | Error failure) { TestPlayers.remove(player); throw failure; }
        for (int dose = 0; dose < RealmGear.COATING_CHARGES; dose++) {
            final int expected = RealmGear.COATING_CHARGES - dose - 1;
            h.runAtTickTime(2 + dose * RealmGear.COATING_WINDOW_TICKS, () -> {
                try {
                    h.assertTrue(RealmGear.protect(player, RealmGear.HazardKind.CAVE_GAS), "A fresh finite coating charge must block a native hazard after twenty actual ticks");
                    h.assertTrue(RealmGear.coatingCharges(armor) == expected, "Every real-time protection window must consume exactly one remaining charge");
                    for (var kind : RealmGear.HazardKind.values()) h.assertTrue(RealmGear.protect(player, kind), "One guarded interval must cover overlapping native contacts");
                    h.assertTrue(RealmGear.coatingCharges(armor) == expected, "Overlapping sources in the same short interval cannot burn multiple coating units");
                } catch (RuntimeException | Error failure) { TestPlayers.remove(player); throw failure; }
            });
        }
        h.runAtTickTime(2 + RealmGear.COATING_CHARGES * RealmGear.COATING_WINDOW_TICKS, () -> {
            try {
                h.assertTrue(RealmGear.coatingCharges(armor) == 0 && !RealmGear.protect(player, RealmGear.HazardKind.LOWER_SEA), "An exhausted coating must restore the ordinary native hazard path");
                RealmGear.applyCoating(armor); var data = armor.get(DataComponents.CUSTOM_DATA).copyTag();
                data.putLong("interstice_coating_until", h.getLevel().getGameTime() + 1000000); armor.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                h.assertTrue(RealmGear.protect(player, RealmGear.HazardKind.SPORE_POD) && RealmGear.coatingCharges(armor) == RealmGear.COATING_CHARGES - 1,
                        "A restored long cooldown cannot provide infinite protection without consuming a fresh charge");
            } finally { TestPlayers.remove(player); }
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void beaconUsesActualCrystalFuelAndRestoresOnlyItsSavedRemainder(GameTestHelper h) {
        var player = player(h); BlockPos local = new BlockPos(4, 2, 4); h.setBlock(local, RealmGear.BEACON.get());
        try {
            BlockPos pos = h.absolutePos(local); var beacon = (RouteBeaconEntity)h.getLevel().getBlockEntity(pos);
            beacon.setOwner(player.getUUID());
            var fuel = new ItemStack(MineralEcology.PHOSPHORITE_CRYSTAL.get(), 2); player.setItemInHand(InteractionHand.MAIN_HAND, fuel);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
            h.assertTrue(player.gameMode.useItemOn(player, h.getLevel(), fuel, InteractionHand.MAIN_HAND, hit).consumesAction(), "Native fuel interaction must reach the actual beacon block");
            h.assertTrue(fuel.getCount() == 1 && beacon.fuelTicks() == RealmGear.BEACON_FUEL_TICKS
                    && h.getLevel().getBlockState(pos).getValue(RouteBeaconBlock.LIT), "One crystal must pay for the saved finite light duration");
            for (int tick = 0; tick < 137; tick++) beacon.tick();
            var saved = beacon.saveWithFullMetadata(h.getLevel().registryAccess());
            var loaded = new RouteBeaconEntity(pos, h.getLevel().getBlockState(pos));
            loaded.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(loaded.fuelTicks() == RealmGear.BEACON_FUEL_TICKS - 137 && player.getUUID().equals(loaded.owner()), "Native reload must preserve fuel remainder and owner");
            h.assertTrue(!loaded.getBlockState().canOcclude(), "The shaped beacon must not cull the ground beneath invisible model corners");
            loaded.setLevel(h.getLevel()); h.getLevel().setBlockEntity(loaded);
            for (int tick = 0; tick < RealmGear.BEACON_FUEL_TICKS - 137; tick++) loaded.tick();
            h.assertTrue(loaded.fuelTicks() == 0 && !h.getLevel().getBlockState(pos).getValue(RouteBeaconBlock.LIT), "Fuel exhaustion must remove the real emitted light");
        } finally { TestPlayers.remove(player); }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void beaconCapacityAndMalformedSavedFuelAreBounded(GameTestHelper h) {
        BlockPos local = new BlockPos(4, 2, 4); h.setBlock(local, RealmGear.BEACON.get());
        var beacon = (RouteBeaconEntity)h.getLevel().getBlockEntity(h.absolutePos(local));
        for (int i = 0; i < 4; i++) h.assertTrue(beacon.addFuel(), "Each of four crystals must fit the finite beacon reservoir");
        h.assertTrue(!beacon.addFuel() && beacon.fuelTicks() == RealmGear.BEACON_MAX_FUEL, "A full beacon cannot consume a fifth crystal");
        var tag = beacon.saveWithoutMetadata(h.getLevel().registryAccess()); tag.putInt("FuelTicks", Integer.MAX_VALUE); tag.putInt("PulseTicks", 50000);
        var loaded = new RouteBeaconEntity(h.absolutePos(local), beacon.getBlockState()); loaded.loadWithComponents(tag, h.getLevel().registryAccess());
        h.assertTrue(loaded.fuelTicks() == RealmGear.BEACON_MAX_FUEL, "Malformed saved fuel cannot bypass the maximum charge");
        tag.putInt("FuelTicks", -20); loaded.loadWithComponents(tag, h.getLevel().registryAccess());
        h.assertTrue(loaded.fuelTicks() == 0, "A negative saved reservoir must become empty, never refill itself");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void equipmentCraftsRequireNativeProcessedMaterialsAndCoatingRunsThroughRetort(GameTestHelper h) {
        var mesh = RealmAgriculture.MESH.get(); var lining = RealmAgriculture.PURE_LINING.get();
        var input = CraftingInput.of(3, 3, List.of(new ItemStack(mesh), new ItemStack(lining), new ItemStack(mesh),
                new ItemStack(ExpeditionEquipment.CHEMOTROPHIC_FABRIC.get()), new ItemStack(Interstice.PRESSURE_COUPLER.get()), new ItemStack(ExpeditionEquipment.CHEMOTROPHIC_FABRIC.get()),
                new ItemStack(Interstice.RIFTSILVER_INGOT.get()), new ItemStack(Interstice.RIFTSILVER_INGOT.get()), new ItemStack(Interstice.RIFTSILVER_INGOT.get())));
        var output = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow().value().assemble(input, h.getLevel().registryAccess());
        h.assertTrue(output.is(RealmGear.BALLAST_BELT.get()) && output.getDamageValue() == 0, "The loaded native recipe must create one fresh ballast belt");
        BlockPos machine = new BlockPos(4, 2, 4); h.setBlock(machine, RealmAgriculture.RETORT.get());
        var retort = (RetortBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(machine));
        retort.setItem(0, new ItemStack(lining)); retort.setItem(1, new ItemStack(RealmAgriculture.PASTE.get(), 2));
        retort.setItem(2, new ItemStack(RealmAgriculture.SORBENT.get(), 2)); retort.setItem(6, new ItemStack(MineralEcology.UMBRAL_COAL.get()));
        h.assertTrue(retort.selectRecipe(ResourceLocation.fromNamespaceAndPath(Interstice.ID, "retort_protective_coating")), "Coating must be a real registered retort recipe");
        for (int tick = 0; tick < 1000; tick++) retort.process();
        h.assertTrue(retort.getItem(7).is(RealmGear.PROTECTIVE_COATING.get()) && retort.getItem(7).getCount() == 1
                && retort.getItem(0).isEmpty() && retort.getItem(1).isEmpty() && retort.getItem(2).isEmpty(),
                "Advanced coating processing must pay all three counted ingredients and return exactly one dose");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void actualTaggedGasUsesCoatingWhileUncoatedAndVanillaPoisonKeepTheirEffects(GameTestHelper h) {
        h.setBlock(new BlockPos(6, 1, 6), Blocks.STONE); h.setBlock(new BlockPos(8, 1, 6), Blocks.STONE);
        var coated = player(h); var bare = TestPlayers.create(h, new BlockPos(8, 2, 6), GameType.SURVIVAL);
        var armor = new ItemStack(Items.DIAMOND_CHESTPLATE); RealmGear.applyCoating(armor); coated.setItemSlot(EquipmentSlot.CHEST, armor);
        h.assertTrue(ClingweedGas.emit(h.getLevel(), h.absolutePos(new BlockPos(6, 2, 6))), "An actual tagged native gas cloud must be created");
        h.runAtTickTime(8, () -> {
            try {
                h.assertTrue(!coated.hasEffect(MobEffects.POISON) && !coated.hasEffect(MobEffects.WEAKNESS) && !coated.hasEffect(MobEffects.CONFUSION)
                        && RealmGear.coatingCharges(armor) == RealmGear.COATING_CHARGES - 1,
                        "Actual source-tagged cloud applications must share one finite coating charge");
                h.assertTrue(bare.hasEffect(MobEffects.POISON) && bare.hasEffect(MobEffects.WEAKNESS), "The existing uncoated gas behaviour must remain intact");
                coated.addEffect(new MobEffectInstance(MobEffects.POISON, 80));
                h.assertTrue(coated.hasEffect(MobEffects.POISON), "An ordinary poison application next to our cloud cannot be blocked without the tagged effect source");
            } finally { TestPlayers.remove(coated); TestPlayers.remove(bare); }
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void nativeLowerSeaContactSpendsCoatingButUpperSeaStaysDangerous(GameTestHelper h) {
        h.setBlock(new BlockPos(6, 1, 6), Blocks.STONE); var player = player(h); player.setNoGravity(true);
        // Native ServerPlayer starts with a separate 60-tick spawn guard. Let real ticks expire it.
        h.runAtTickTime(75, () -> {
            try {
                var armor = new ItemStack(Items.DIAMOND_CHESTPLATE); RealmGear.applyCoating(armor); player.setItemSlot(EquipmentSlot.CHEST, armor);
                h.setBlock(new BlockPos(6, 2, 6), Interstice.HEAVY_BLOCK.get());
                ToxicLiquidBlock.onEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
                h.assertTrue(player.getHealth() == 20 && RealmGear.coatingCharges(armor) == RealmGear.COATING_CHARGES - 1,
                        "Actual lower toxin contact must consume a finite coating interval before its native harm");
                h.setBlock(new BlockPos(6, 2, 6), Interstice.LIGHT_BLOCK.get());
                player.getPersistentData().remove("interstice:last_toxin_tick");
                ToxicLiquidBlock.onEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
                h.assertTrue(player.getHealth() < 20, "After the real spawn guard expires, the upper toxic sea must still inflict native harm");
            } finally { TestPlayers.remove(player); }
            h.succeed();
        });
    }
}
