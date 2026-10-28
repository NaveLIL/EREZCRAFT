package pro.erez.interstice.fauna;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.VaultMaterials;

/** Small V6-only populations. No biome rewrite, chunk loading, or global vanilla spawn rule. */
public final class StrongRealmFauna {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Interstice.ID);
    public static final DeferredHolder<EntityType<?>, EntityType<UpperSeaGhast>> UPPER_SEA_GHAST =
            ENTITIES.register("upper_sea_ghast", () -> EntityType.Builder
                    .<UpperSeaGhast>of(UpperSeaGhast::new, MobCategory.MONSTER)
                    .sized(3.25F, 3.5F).clientTrackingRange(12).updateInterval(2)
                    .build("interstice:upper_sea_ghast"));
    public static final DeferredHolder<EntityType<?>, EntityType<UpperSeaCharge>> UPPER_SEA_CHARGE =
            ENTITIES.register("upper_sea_charge", () -> EntityType.Builder
                    .<UpperSeaCharge>of(UpperSeaCharge::new, MobCategory.MISC)
                    .sized(.55F, .55F).clientTrackingRange(12).updateInterval(1)
                    .build("interstice:upper_sea_charge"));
    public static final DeferredHolder<EntityType<?>, EntityType<VaultHunter>> VAULT_HUNTER =
            ENTITIES.register("vault_hunter", () -> EntityType.Builder.<VaultHunter>of(VaultHunter::new, MobCategory.MONSTER)
                    .sized(.95F, 2.7F).eyeHeight(1.9F).clientTrackingRange(8).updateInterval(1).build("interstice:vault_hunter"));
    public static final int HUNTER_LOCAL_CAP = 2, HUNTER_WORLD_CAP = 8;
    public static final int SKELETON_LOCAL_CAP = 2, GHAST_LOCAL_CAP = 1;
    public static final int SKELETON_WORLD_CAP = 16, GHAST_WORLD_CAP = 6;
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Interstice.ID);
    public static final DeferredHolder<Item, Item> UPPER_SEA_GHAST_SPAWN_EGG = ITEMS.register("upper_sea_ghast_spawn_egg", () ->
            new net.neoforged.neoforge.common.DeferredSpawnEggItem(UPPER_SEA_GHAST, 0x343c40, 0xc5d985, new Item.Properties()));
    public static final DeferredHolder<Item, Item> VAULT_HUNTER_SPAWN_EGG = ITEMS.register("vault_hunter_spawn_egg", () ->
            new net.neoforged.neoforge.common.DeferredSpawnEggItem(VAULT_HUNTER, 0x323f42, 0xb1c7b4, new Item.Properties()));
    private static final Map<ServerLevel, Integer> CURSORS = new WeakHashMap<>();
    private StrongRealmFauna() {}

    public static void register(IEventBus modBus) {
        HunterSounds.register(modBus);
        HunterBrain.register(modBus);
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(StrongRealmFauna::attributes);
        NeoForge.EVENT_BUS.addListener(StrongRealmFauna::tick);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(VAULT_HUNTER.get(), VaultHunter.createHunterAttributes().build());
        event.put(UPPER_SEA_GHAST.get(), UpperSeaGhast.createRealmAttributes().build());
    }
    public static boolean isV6(ServerLevel level) {
        return level.dimension().equals(IslandWorld.TENSION_WORLD)
                && level.getChunkSource().getGenerator() instanceof IslandChunkGenerator generator
                && generator.terrainRevision() == 6;
    }
    public static boolean spawningEnabled(ServerLevel level) {
        return isV6(level) && level.getDifficulty() != Difficulty.PEACEFUL && level.getServer().isSpawningMonsters()
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    }
    /** Use the vanilla global budget but count current entities, including additions after its snapshot. */
    public static boolean hasMonsterCapacity(ServerLevel level) {
        if (!spawningEnabled(level)) return false;
        var state = level.getChunkSource().getLastSpawnState();
        if (state == null) return false;
        int cap = MobCategory.MONSTER.getMaxInstancesPerChunk() * state.getSpawnableChunkCount() / 289;
        if (cap <= 0 || state.getMobCategoryCounts().getInt(MobCategory.MONSTER) >= cap) return false;
        int count = 0;
        for (var entity : level.getAllEntities()) {
            if (entity.isAlive() && entity.getType().getCategory() == MobCategory.MONSTER && ++count >= cap)
                return false;
        }
        return true;
    }
    public static boolean loadedBox(ServerLevel level, AABB box) {
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private static boolean naturalRock(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Interstice.RIFTSTONE.get())
                || state.is(VaultMaterials.VAULTSTONE.get()) || state.is(MineralEcology.RIFT_SHALE.get());
    }
    /** Genuine rock roofs, with at least three dry blocks of headroom. Trees are not caverns. */
    public static boolean cavernHabitat(ServerLevel level, BlockPos pos) {
        if (!isV6(level) || !loadedBox(level, new AABB(pos).inflate(1, 24, 1))) return false;
        var geometry = GeometryProfiles.get(level);
        if (pos.getY() <= geometry.lowerSeaTop() || pos.getY() > Math.min(geometry.maxLand(), geometry.minLand() + 72)) return false;
        if (!naturalRock(level.getBlockState(pos.below()))
                || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                || !level.getBlockState(pos.above(2)).isAir()
                || level.getBrightness(LightLayer.BLOCK, pos) != 0) return false;
        for (int dy = 3; dy <= 24; dy++) {
            BlockState ceiling = level.getBlockState(pos.above(dy));
            if (ceiling.is(BlockTags.LOGS) || ceiling.is(BlockTags.LEAVES)) return false;
            if (!ceiling.isAir()) return naturalRock(ceiling);
        }
        return false;
    }
    /** Open dry air, below the local wavy sea; roofs and narrow forest pockets fail. */
    public static boolean airborneHabitat(ServerLevel level, BlockPos pos) {
        if (!isV6(level) || !loadedBox(level, new AABB(pos).inflate(5, 8, 5))) return false;
        var geometry = GeometryProfiles.get(level);
        if (pos.getY() < geometry.lowerSeaTop() + 26
                || pos.getY() + 8 >= SeaSurface.cellMinimum(geometry, pos.getX(), pos.getZ(), true)) return false;
        for (int y = -2; y <= 6; y++) {
            if (!level.getBlockState(pos.above(y)).isAir()) return false;
            for (Direction direction : Direction.Plane.HORIZONTAL)
                if (!level.getBlockState(pos.above(y).relative(direction, 4)).isAir()) return false;
        }
        return true;
    }
    private static boolean awayFromPlayers(ServerLevel level, BlockPos pos) {
        boolean inRange = false;
        Vec3 center = Vec3.atBottomCenterOf(pos);
        for (var player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            double squared = player.position().distanceToSqr(center);
            if (squared <= 24 * 24) return false;
            if (!player.isCreative() && squared <= 64 * 64) inRange = true;
        }
        return inRange && level.getWorldBorder().isWithinBounds(pos)
                && !level.getSharedSpawnPos().closerToCenterThan(center, 24)
                && level.isNaturalSpawningAllowed(pos);
    }
    private static <T extends Mob> boolean belowPopulationCaps(ServerLevel level, BlockPos pos,
                                                              Class<T> type, int local, int world) {
        int count = 0;
        for (var entity : level.getAllEntities())
            if (type.isInstance(entity) && entity.isAlive() && ++count >= world) return false;
        return level.getEntitiesOfClass(type, new AABB(pos).inflate(64), Mob::isAlive).size() < local;
    }
    public static boolean trySpawnSkeleton(ServerLevel level, BlockPos pos) {
        if (!hasMonsterCapacity(level) || !awayFromPlayers(level, pos) || !cavernHabitat(level, pos)
                || !belowPopulationCaps(level, pos, WitherSkeleton.class, SKELETON_LOCAL_CAP, SKELETON_WORLD_CAP)
                || !Monster.checkMonsterSpawnRules(EntityType.WITHER_SKELETON, level, MobSpawnType.NATURAL, pos, level.random)) return false;
        WitherSkeleton entity = EntityType.WITHER_SKELETON.create(level);
        return addNatural(level, pos, entity);
    }
    public static boolean trySpawnGhast(ServerLevel level, BlockPos pos) {
        if (!hasMonsterCapacity(level) || !awayFromPlayers(level, pos) || !airborneHabitat(level, pos)
                || !belowPopulationCaps(level, pos, UpperSeaGhast.class, GHAST_LOCAL_CAP, GHAST_WORLD_CAP)) return false;
        return addNatural(level, pos, UPPER_SEA_GHAST.get().create(level));
    }
    public static boolean trySpawnHunter(ServerLevel level, BlockPos pos) {
        if (!hasMonsterCapacity(level) || !awayFromPlayers(level, pos) || !cavernHabitat(level, pos)
                || !belowPopulationCaps(level, pos, VaultHunter.class, HUNTER_LOCAL_CAP, HUNTER_WORLD_CAP)) return false;
        return addNatural(level, pos, VAULT_HUNTER.get().create(level));
    }
    private static boolean addNatural(ServerLevel level, BlockPos pos, Mob entity) {
        if (entity == null) return false;
        entity.moveTo(Vec3.atBottomCenterOf(pos));
        if (!EventHooks.checkSpawnPosition(entity, level, MobSpawnType.NATURAL)) return false;
        EventHooks.finalizeMobSpawn(entity, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        return !entity.isSpawnCancelled() && level.noCollision(entity) && level.addFreshEntity(entity);
    }
    public static void tick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getGameTime() % 200 != 0 || !hasMonsterCapacity(level)) continue;
            var players = level.players().stream().filter(p -> p.isAlive() && !p.isCreative() && !p.isSpectator()).toList();
            if (players.isEmpty()) continue;
            int cursor = CURSORS.getOrDefault(level, 0);
            ServerPlayer player = players.get(Math.floorMod(cursor, players.size()));
            CURSORS.put(level, cursor == Integer.MAX_VALUE ? 0 : cursor + 1);
            boolean air = level.getGameTime() % 400 == 0 && level.random.nextInt(3) == 0;
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = level.random.nextDouble() * Math.PI * 2;
                int radius = 28 + level.random.nextInt(29);
                int x = (int)Math.floor(player.getX() + Math.cos(angle) * radius);
                int z = (int)Math.floor(player.getZ() + Math.sin(angle) * radius);
                var geometry = GeometryProfiles.get(level);
                if (air) {
                    int y = Math.max(geometry.lowerSeaTop() + 26,
                            Math.min((int)player.getY() + 8 + level.random.nextInt(25), geometry.maxLand() - 8));
                    if (trySpawnGhast(level, new BlockPos(x, y, z))) break;
                } else {
                    var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk == null) continue;
                    int top = Math.min(geometry.minLand() + 72, Math.min(geometry.maxLand(), (int)player.getY() + 16));
                    for (int y = top; y > Math.max(geometry.lowerSeaTop(), (int)player.getY() - 20); y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (chunk.getBlockState(pos).isAir() && naturalRock(chunk.getBlockState(pos.below()))
                                && (level.random.nextInt(4) == 0 ? trySpawnHunter(level, pos) : trySpawnSkeleton(level, pos))) return;
                    }
                }
            }
        }
    }
}
