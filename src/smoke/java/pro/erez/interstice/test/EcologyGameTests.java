package pro.erez.interstice.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.ecology.*;

@GameTestHolder("interstice_ecology")
@PrefixGameTestTemplate(false)
public final class EcologyGameTests {
    private static BlockPos plant(GameTestHelper h, int x) {
        var p = h.absolutePos(new BlockPos(x, 3, 4));
        h.getLevel().setBlock(p, CaveEcology.CLINGWEED.get().defaultBlockState(), 3);
        return p;
    }
    private static ClingweedBlockEntity stash(GameTestHelper h, BlockPos p) {
        return (ClingweedBlockEntity) h.getLevel().getBlockEntity(p);
    }
    private static int clouds(GameTestHelper h, BlockPos p) {
        return h.getLevel().getEntitiesOfClass(AreaEffectCloud.class, new AABB(p).inflate(12), e -> e.getTags().contains(ClingweedGas.ENTITY_TAG)).size();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void safeCaveGlowHasSupportAndNoContactDamage(GameTestHelper h) {
        var p = h.absolutePos(new BlockPos(4, 3, 4));
        var glow = CaveEcology.GLOW_BLOOM.get().defaultBlockState();
        var pendant = CaveEcology.HANGING_GLOW_BLOOM.get().defaultBlockState();
        h.getLevel().setBlock(p.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(p.above(), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(glow.canSurvive(h.getLevel(), p) && pendant.canSurvive(h.getLevel(), p), "Floor and hanging cave growth reject real cave rock");
        h.assertTrue(glow.getLightEmission(h.getLevel(), p) == 7 && pendant.getLightEmission(h.getLevel(), p) == 7, "Safe cave growth has no navigable faint light");
        var player = TestPlayers.create(h, new BlockPos(4, 2, 4), GameType.SURVIVAL);
        try {
            float health = player.getHealth();
            glow.entityInside(h.getLevel(), p, player);
            pendant.entityInside(h.getLevel(), p, player);
            h.assertTrue(player.getHealth() == health && player.getActiveEffects().isEmpty(), "Harmless cave glow harms the visitor");
        } finally { TestPlayers.remove(player); }
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void passageStealsOneComponentItemWithoutArmorDamageOrGas(GameTestHelper h) {
        var first = plant(h, 4); var second = plant(h, 6);
        var player = TestPlayers.create(h, new BlockPos(4, 2, 4), GameType.SURVIVAL);
        try {
            player.getInventory().clearContent();
            var named = new ItemStack(Items.ENDER_PEARL, 5);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Recovered relic"));
            var custom = new CompoundTag(); custom.putInt("unique_payload", 193);
            named.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(custom));
            player.getInventory().items.set(0, named);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            float before = player.getHealth();
            h.getLevel().getBlockState(first).entityInside(h.getLevel(), first, player);
            h.getLevel().getBlockState(second).entityInside(h.getLevel(), second, player);
            var held = stash(h, first).storedItems();
            h.assertTrue(named.getCount() == 4 && held.size() == 1 && held.get(0).getCount() == 1 && ItemStack.isSameItemSameComponents(named, held.get(0)), "Passage did not preserve exactly one component-bearing item");
            h.assertTrue(stash(h, second).storedItems().isEmpty(), "Moving between plant cells bypassed the global cooldown");
            h.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET) && player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "Clingweed stole worn armor or offhand equipment");
            h.assertTrue(player.getHealth() == before && player.getActiveEffects().isEmpty() && clouds(h, first) == 0, "Walking through clingweed damaged or poisoned the player");
            h.assertTrue(h.getLevel().getBlockState(first).getCollisionShape(h.getLevel(), first).isEmpty(), "Clingweed blocks movement");
            var saved = new CompoundTag(); player.saveWithoutId(saved);
            h.assertTrue(saved.toString().contains(ClingweedBlock.COOLDOWN_TAG), "The player's theft cooldown is not serialized");
        } finally { TestPlayers.remove(player); }
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void creativeSpectatorEmptyInventoryAndFullStashAreUntouched(GameTestHelper h) {
        var p = plant(h, 4);
        var player = TestPlayers.create(h, new BlockPos(4, 2, 4), GameType.CREATIVE);
        try {
            player.getInventory().clearContent(); player.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 12));
            h.getLevel().getBlockState(p).entityInside(h.getLevel(), p, player);
            player.setGameMode(GameType.SPECTATOR);
            h.getLevel().getBlockState(p).entityInside(h.getLevel(), p, player);
            h.assertTrue(stash(h, p).storedItems().isEmpty() && player.getInventory().countItem(Items.DIAMOND) == 12, "Clingweed steals in creative or spectator mode");
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND));
            h.getLevel().getBlockState(p).entityInside(h.getLevel(), p, player);
            h.assertTrue(stash(h, p).storedItems().isEmpty(), "Offhand-only inventory is eligible for theft");
            for (int i = 0; i < ClingweedBlockEntity.CAPACITY; i++) h.assertTrue(stash(h, p).keepOne(new ItemStack(Items.IRON_INGOT)), "Pocket capacity is wrong");
            player.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 2));
            h.getLevel().getBlockState(p).entityInside(h.getLevel(), p, player);
            h.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3 && stash(h, p).storedItems().size() == 8, "A full stash destroyed another inventory item");
        } finally { TestPlayers.remove(player); }
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void stashRoundTripRetainsComponentsAndRemovalDropsExactlyOnce(GameTestHelper h) {
        var p = plant(h, 4);
        var relic = new ItemStack(Items.IRON_PICKAXE); relic.setDamageValue(27);
        relic.set(DataComponents.CUSTOM_NAME, Component.literal("Owner's tool"));
        stash(h, p).keepOne(relic);
        var encoded = stash(h, p).saveWithFullMetadata(h.getLevel().registryAccess());
        var reloaded = (ClingweedBlockEntity) BlockEntity.loadStatic(p, CaveEcology.CLINGWEED.get().defaultBlockState(), encoded, h.getLevel().registryAccess());
        h.assertTrue(reloaded != null && reloaded.storedItems().size() == 1 && ItemStack.isSameItemSameComponents(relic, reloaded.storedItems().get(0)), "Chunk NBT round-trip lost item data");
        h.getLevel().setBlockEntity(reloaded);
        h.getLevel().destroyBlock(p, false);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(2), e -> ItemStack.isSameItemSameComponents(relic, e.getItem()));
        h.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1 && clouds(h, p) == 0, "Removal lost, duplicated or unexpectedly gassed the preserved item");
        reloaded.release(h.getLevel());
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(2), e -> ItemStack.isSameItemSameComponents(relic, e.getItem())).stream().mapToInt(e -> e.getItem().getCount()).sum() == 1, "Repeated removal duplicated the stash");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=160)
    public static void destroyingPlantCreatesOneFiniteLocalToxicCloud(GameTestHelper h) {
        var first = plant(h, 4); var second = plant(h, 6);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            h.getLevel().setBlock(first.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
        var player = TestPlayers.create(h, new BlockPos(4, 2, 4), GameType.SURVIVAL);
        try {
            CaveEcology.CLINGWEED.get().playerWillDestroy(h.getLevel(), first, h.getLevel().getBlockState(first), player);
            h.assertTrue(clouds(h, first) == 0, "Plant emitted gas before actual removal");
            h.getLevel().destroyBlock(first, false);
            var blast = new Explosion(h.getLevel(), null, second.getX(), second.getY(), second.getZ(), 2F, false, Explosion.BlockInteraction.DESTROY);
            CaveEcology.CLINGWEED.get().onBlockExploded(h.getLevel().getBlockState(second), h.getLevel(), second, blast);
            h.assertTrue(clouds(h, first) == 1, "Nearby cell destruction multiplied clouds");
            player.teleportTo(h.getLevel(), first.getX() + .5, first.getY() + .01, first.getZ() + .5, java.util.Set.of(), 0, 0);
            player.hasChangedDimension();
        } catch (Throwable failure) { TestPlayers.remove(player); throw failure; }
        h.runAtTickTime(10, () -> {
            try { h.assertTrue(player.hasEffect(MobEffects.POISON) && player.hasEffect(MobEffects.WEAKNESS) && player.hasEffect(MobEffects.CONFUSION), "Defensive gas did not apply its real local effects"); }
            finally { TestPlayers.remove(player); }
        });
        h.runAtTickTime(130, () -> { h.assertTrue(clouds(h, first) == 0, "Toxic cloud persists beyond its bounded lifetime"); h.succeed(); });
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void stingsHaveTheirOwnPerVictimCooldown(GameTestHelper h) {
        var p = h.absolutePos(new BlockPos(4, 3, 4));
        var state = CaveEcology.STING_FROND.get().defaultBlockState();
        var player = TestPlayers.create(h, new BlockPos(4, 2, 4), GameType.SURVIVAL);
        try {
            state.entityInside(h.getLevel(), p, player);
            long until = player.getPersistentData().getLong(CavePlantBlock.STING_COOLDOWN_TAG);
            float health = player.getHealth();
            for (int i = 0; i < 30; i++) state.entityInside(h.getLevel(), p.east(i % 3), player);
            h.assertTrue(player.hasEffect(MobEffects.POISON) && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && player.getHealth() == health && until == player.getPersistentData().getLong(CavePlantBlock.STING_COOLDOWN_TAG), "Repeated contact multiplies sting damage or refreshes its cooldown");
        } finally { TestPlayers.remove(player); }
        h.succeed();
    }
}
