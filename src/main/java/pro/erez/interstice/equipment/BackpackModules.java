package pro.erez.interstice.equipment;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import pro.erez.interstice.Interstice;

public final class BackpackModules {
    private static final Map<Item, Item> COMPACT_RECIPES = new HashMap<>();

    static {
        COMPACT_RECIPES.put(Items.IRON_INGOT, Items.IRON_BLOCK);
        COMPACT_RECIPES.put(Items.IRON_NUGGET, Items.IRON_INGOT);
        COMPACT_RECIPES.put(Items.GOLD_INGOT, Items.GOLD_BLOCK);
        COMPACT_RECIPES.put(Items.GOLD_NUGGET, Items.GOLD_INGOT);
        COMPACT_RECIPES.put(Items.COPPER_INGOT, Items.COPPER_BLOCK);
        COMPACT_RECIPES.put(Items.DIAMOND, Items.DIAMOND_BLOCK);
        COMPACT_RECIPES.put(Items.COAL, Items.COAL_BLOCK);
        COMPACT_RECIPES.put(Items.REDSTONE, Items.REDSTONE_BLOCK);
        COMPACT_RECIPES.put(Items.LAPIS_LAZULI, Items.LAPIS_BLOCK);
        COMPACT_RECIPES.put(Items.EMERALD, Items.EMERALD_BLOCK);
        COMPACT_RECIPES.put(Items.WHEAT, Items.HAY_BLOCK);
    }

    private BackpackModules() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tickModules(player);
        }
    }

    public static void tickModules(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        var worn = BackpackHarness.get(player);
        ItemStack pack = BackpackStorage.isPack(worn) ? worn : ItemStack.EMPTY;
        if (pack.isEmpty()) {
            var main = player.getMainHandItem();
            if (BackpackStorage.isPack(main)) pack = main;
        }
        if (pack.isEmpty() || !BackpackStorage.valid(pack)) return;

        // 1. Magnet Module (every 5 ticks)
        if (player.tickCount % 5 == 0 && BackpackStorage.hasModule(pack, ModuleType.MAGNET)) {
            tickMagnet(player, pack);
        }

        // 2. Feeder Module (every 20 ticks = 1 second)
        if (player.tickCount % 20 == 0 && BackpackStorage.hasModule(pack, ModuleType.FEEDER)) {
            tickFeeder(player, pack);
        }

        // 3. Compression Module (every 40 ticks = 2 seconds)
        if (player.tickCount % 40 == 0 && BackpackStorage.hasModule(pack, ModuleType.COMPRESSION)) {
            tickCompression(player, pack);
        }
    }

    public static void tickMagnet(ServerPlayer player, ItemStack pack) {
        double r = 4.5;
        var box = new AABB(player.getX() - r, player.getY() - r, player.getZ() - r,
                player.getX() + r, player.getY() + r, player.getZ() + r);
        var entities = player.serverLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay()
                &&(e.getTarget()==null||e.getTarget().equals(player.getUUID())));
        boolean insertedAny = false;
        for (var entity : entities) {
            var stack = entity.getItem();
            if (stack.isEmpty() || !BackpackStorage.allowed(stack)) continue;
            var diff = player.position().add(0, 0.5, 0).subtract(entity.position());
            double distSq = diff.lengthSqr();
            if (distSq < 2.0) {
                var packItems = BackpackStorage.read(pack);
                int inserted = BackpackStorage.insert(packItems, stack);
                if (inserted > 0) {
                    var picked=stack.getItem();
                    insertedAny = true;
                    stack.shrink(inserted);
                    BackpackStorage.writeOwned(player,pack,packItems);
                    player.take(entity,inserted);player.awardStat(net.minecraft.stats.Stats.ITEM_PICKED_UP.get(picked),inserted);player.onItemPickup(entity);
                    player.serverLevel().sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 0.2, entity.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
                    if (stack.isEmpty()) {
                        entity.discard();
                    } else {
                        entity.setItem(stack);
                    }
                }
            } else if (distSq < r * r) {
                var move = diff.normalize().scale(0.35);
                entity.setDeltaMovement(move);
                entity.hasImpulse = true;
            }
        }
        if (insertedAny) {
            player.getInventory().setChanged();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.4F);
        }
    }

    public static void tickFeeder(ServerPlayer player, ItemStack pack) {
        var feederStack = BackpackStorage.getModule(pack, ModuleType.FEEDER);
        if (feederStack.isEmpty()) return;

        int mode = BackpackModuleItem.getFeederMode(feederStack);
        int foodLevel = player.getFoodData().getFoodLevel();

        // ECO mode: only eat when hunger drops by 3 drumsticks or more (<= 14)
        // FAST mode: eat immediately upon any hunger drop (< 20) to sustain constant saturation/regeneration
        boolean shouldEat = (mode == BackpackModuleItem.FEEDER_FAST)
                ? player.getFoodData().needsFood()
                : (foodLevel <= 14);
        if (!shouldEat) return;

        var packItems = BackpackStorage.read(pack);
        int bestIndex = -1;

        if (mode == BackpackModuleItem.FEEDER_FAST) {
            int deficit = 20 - foodLevel;
            int bestNut = -1;
            int closestDiff = Integer.MAX_VALUE;
            for (int i = 0; i < packItems.size(); i++) {
                var item = packItems.get(i);
                if (!item.isEmpty() && item.has(DataComponents.FOOD)) {
                    if (isDangerousFood(player,item)) continue;
                    var food = item.get(DataComponents.FOOD);
                    int nut = food.nutrition();
                    int diff = Math.abs(nut - deficit);
                    if (diff < closestDiff || (diff == closestDiff && nut > bestNut)) {
                        closestDiff = diff;
                        bestNut = nut;
                        bestIndex = i;
                    }
                }
            }
        } else {
            int bestNutrition = -1;
            for (int i = 0; i < packItems.size(); i++) {
                var item = packItems.get(i);
                if (!item.isEmpty() && item.has(DataComponents.FOOD)) {
                    if (isDangerousFood(player,item)) continue;
                    var food = item.get(DataComponents.FOOD);
                    int nut = food.nutrition();
                    if (nut > bestNutrition) {
                        bestNutrition = nut;
                        bestIndex = i;
                    }
                }
            }
        }

        if (bestIndex >= 0) {
            var foodItem = packItems.get(bestIndex);
            var consumed=foodItem.copyWithCount(1);
            foodItem.shrink(1);
            BackpackStorage.writeOwned(player,pack,packItems);
            var remainder=consumed.finishUsingItem(player.serverLevel(),player);
            if(!remainder.isEmpty()){
                var updated=BackpackStorage.read(pack);
                int inserted=BackpackStorage.insert(updated,remainder);
                if(inserted>0){remainder.shrink(inserted);BackpackStorage.writeOwned(player,pack,updated);}
                if(!remainder.isEmpty()&&!player.getInventory().add(remainder))player.drop(remainder,false);
            }
            player.getInventory().setChanged();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    private static boolean isDangerousFood(ServerPlayer player,ItemStack item) {
        if(item.is(pro.erez.interstice.agriculture.RealmAgriculture.ROOT.get())
                &&!pro.erez.interstice.agriculture.NativeFoodTolerance.canDigest(player,pro.erez.interstice.agriculture.NativeFoodTolerance.FoodKind.ROOT))return true;
        if(item.is(pro.erez.interstice.food.TideHeart.FRUIT.get())||item.is(Items.CHORUS_FRUIT))return true;
        var food=item.getFoodProperties(player);
        if(food!=null&&food.effects().stream().anyMatch(effect->effect.effect().getEffect().value().getCategory()==net.minecraft.world.effect.MobEffectCategory.HARMFUL))return true;
        return item.is(Items.ROTTEN_FLESH) || item.is(Items.SPIDER_EYE)
                || item.is(Items.POISONOUS_POTATO) || item.is(Items.PUFFERFISH);
    }

    public static void tickCompression(ServerPlayer player, ItemStack pack) {
        var packItems = BackpackStorage.read(pack);
        boolean compressedAny = false;
        for (int i = 0; i < packItems.size(); i++) {
            var item = packItems.get(i);
            if (item.isEmpty() || item.getCount() < 9) continue;
            if(!ItemStack.isSameItemSameComponents(item,new ItemStack(item.getItem())))continue;
            var targetItem = COMPACT_RECIPES.get(item.getItem());
            if (targetItem != null) {
                var compressed = new ItemStack(targetItem, 1);
                // Check if compressed can be inserted into the pack
                item.shrink(9);
                if (BackpackStorage.insert(packItems, compressed) == 1) {
                    compressedAny = true;
                    break; // Process one compacting step per cycle to be paced and predictable
                } else {
                    item.grow(9); // rollback if no space
                }
            }
        }
        if (compressedAny) {
            BackpackStorage.writeOwned(player,pack,packItems);
            player.getInventory().setChanged();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 0.5F, 0.8F);
        }
    }
}
