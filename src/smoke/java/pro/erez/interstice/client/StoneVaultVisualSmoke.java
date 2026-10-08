package pro.erez.interstice.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.RealmBiomes;
import pro.erez.interstice.worldgen.StoneVaults;
import pro.erez.interstice.worldgen.VaultMaterials;

/** Natural-generation visual/persistence inspection in an explicitly disposable Creative world. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class StoneVaultVisualSmoke {
    private static final String MODE = System.getProperty("interstice.vaultSmoke", "");
    private static final String WORLD = "natural-stone-vault-check";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean started, finished;
    private static int stage, ticks;
    private static long began, deadline;
    private static CompletableFuture<JsonObject> work;
    private static JsonObject result = new JsonObject();

    private StoneVaultVisualSmoke() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (MODE.isEmpty() || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (began == 0) {
            began = System.nanoTime();
            deadline = began + TimeUnit.MINUTES.toNanos(7);
        }
        try {
            require(System.nanoTime() < deadline, "Stone vault visual check timed out at stage " + stage);
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                mc.options.renderDistance().set(5);
                mc.options.framerateLimit().set(60);
                if (MODE.equals("create")) {
                    require(!Files.exists(worldPath(mc).resolve("level.dat")), "Disposable world already exists");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,
                            new LevelSettings("Disposable natural stone vault check", GameType.CREATIVE,
                                    false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20261006L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
                } else {
                    require(MODE.equals("reload"), "Unknown stone vault smoke mode: " + MODE);
                    result = JsonParser.parseString(Files.readString(reportPath(mc, "create"))).getAsJsonObject();
                    require(result.get("passed").getAsBoolean(), "Creation did not pass");
                    require(result.get("creator_pid").getAsLong() != ProcessHandle.current().pid(), "Reload requires a different JVM");
                    require(Files.isRegularFile(worldPath(mc).resolve("level.dat")), "Disposable world was not saved");
                    installLegacyPlaceholderFixture(mc);
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> report(mc, false, "World reopen cancelled"));
                }
                return;
            }
            if (!started || SmokeWorldPrompts.advance(mc)) return;
            if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.screen != null) return;
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            if (stage == 0) {
                if (MODE.equals("create")) {
                    mc.getConnection().sendCommand("interstice explore");
                    stage = 1;
                } else {
                    require(mc.level.dimension().equals(IslandWorld.TALL_WORLD), "Saved player did not reopen in tall realm");
                    work = server.submit(() -> validateReload(mc, server.getLevel(IslandWorld.TALL_WORLD), server.getPlayerList().getPlayer(uuid)));
                    stage = 8;
                }
                ticks = 0;
                return;
            }
            if (!mc.level.dimension().equals(IslandWorld.TALL_WORLD)) return;
            if (stage == 1 && ++ticks >= 40) {
                work = server.submit(() -> findNaturalVault(mc, server.getLevel(IslandWorld.TALL_WORLD), server.getPlayerList().getPlayer(uuid)));
                stage = 2;
                ticks = 0;
            } else if (stage == 2 && work.isDone()) {
                result = work.join();
                stage = 3;
                ticks = 0;
            } else if (stage == 3 && ++ticks >= 100) {
                capture(mc, "stone-vault-natural-darkness.png");
                recordCamera(mc, "darkness_camera");
                server.execute(() -> server.getPlayerList().getPlayer(uuid).addEffect(
                        new MobEffectInstance(MobEffects.NIGHT_VISION, 12000, 0, false, false)));
                stage = 4;
                ticks = 0;
            } else if (stage == 4 && ++ticks >= 70) {
                capture(mc, "stone-vault-natural-geology.png");
                recordCamera(mc, "geology_camera");
                work = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    var level = player.serverLevel();
                    BlockPos roof = roofPos(result);
                    camera(player, level, roof.getX() + .5, result.get("floor_y").getAsInt() + 1.05,
                            roof.getZ() + .5, roof.getX() + .5, roof.getY() + .5, roof.getZ() + .5, false);
                    require(ShelterDetector.isSheltered(level, player), "Natural stone arch does not physically shelter its opening");
                    result.addProperty("sheltered_beneath_natural_arch", true);
                    return result;
                });
                stage = 5;
                ticks = 0;
            } else if (stage == 5 && work.isDone()) {
                result = work.join();
                stage = 6;
                ticks = 0;
            } else if (stage == 6 && ++ticks >= 80) {
                capture(mc, "stone-vault-natural-underside.png");
                recordCamera(mc, "underside_camera");
                work = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    var level = player.serverLevel();
                    BlockPos roof = roofPos(result);
                    require(isVaultRock(level.getBlockState(roof)), "Selected roof disappeared before mutation");
                    result.addProperty("broken_roof_original_state", level.getBlockState(roof).toString());
                    require(player.gameMode.destroyBlock(roof), "Creative roof break did not complete");
                    require(level.getBlockState(roof).isAir(), "Roof break did not leave AIR");
                    result.addProperty("one_roof_block_broken", true);
                    result.addProperty("sheltered_after_hole", ShelterDetector.isSheltered(level, player));
                    result.addProperty("player_uuid", uuid.toString());
                    return result;
                });
                stage = 7;
                ticks = 0;
            } else if (stage == 7 && work.isDone() && ++ticks >= 60) {
                result = work.join();
                requireScreenshot(mc, "stone-vault-natural-darkness.png");
                requireScreenshot(mc, "stone-vault-natural-geology.png");
                requireScreenshot(mc, "stone-vault-natural-underside.png");
                report(mc, true, "Natural arch inspected and one Creative roof block broken; normal shutdown saves the disposable world");
            } else if (stage == 8 && work.isDone()) {
                result = work.join();
                stage = 9;
                ticks = 0;
            } else if (stage == 9 && ++ticks >= 100) {
                capture(mc, "stone-vault-reloaded-hole.png");
                recordCamera(mc, "reload_camera");
                stage = 10;
                ticks = 0;
            } else if (stage == 10 && ++ticks >= 40) {
                requireScreenshot(mc, "stone-vault-reloaded-hole.png");
                report(mc, true, "Cold restart preserved the broken roof and biome; an untouched candidate region generated afterward");
            }
        } catch (Throwable error) {
            error.printStackTrace();
            report(mc, false, error.toString());
        }
    }

    private static JsonObject findNaturalVault(Minecraft mc, ServerLevel level, ServerPlayer player) {
        require(player != null && level != null, "Tall realm/player unavailable");
        IslandChunkGenerator generator = generator(level);
        GeometryProfile profile = generator.geometry();
        var random = level.getChunkSource().randomState();
        int loadedCandidates = 0, filteredCandidates = 0;
        for (int radius = 0; radius <= 24; radius++) {
            for (int cx = -radius; cx <= radius; cx++) for (int cz = -radius; cz <= radius; cz++) {
                require(System.nanoTime() < deadline, "Natural vault search deadline reached");
                if (Math.max(Math.abs(cx), Math.abs(cz)) != radius || !StoneVaults.candidate(level.getSeed(), cx, cz)) continue;
                int centerX = cx * 16 + 7, centerZ = cz * 16 + 7;
                // No chunk loading for biome rejection. The climate selection can vary vertically.
                boolean vault = false;
                for (int y = profile.minLand(); y <= profile.maxLand(); y += 8) {
                    if (generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(centerX), QuartPos.fromBlock(y),
                            QuartPos.fromBlock(centerZ), random.sampler()).is(RealmBiomes.STONE_VAULTS)) { vault = true; break; }
                }
                if (!vault) { filteredCandidates++; continue; }
                // A supported roof requires real land somewhere in the sampled native center column.
                var centerColumn = generator.getBaseColumn(centerX, centerZ, level, random);
                boolean land = false;
                for (int y = profile.minLand(); y <= profile.maxLand(); y++) if (StoneVaults.isGround(centerColumn.getBlock(y))) { land = true; break; }
                if (!land) continue;
                var chunk = level.getChunk(cx, cz);
                loadedCandidates++;
                List<BlockPos> additions = new ArrayList<>();
                int geologyCount = 0;
                double smallestUpperGap = Double.POSITIVE_INFINITY;
                double smallestLowerGap = Double.POSITIVE_INFINITY;
                for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
                    var base = generator.getBaseColumn(x, z, level, random);
                    for (int y = profile.minLand(); y <= profile.maxLand(); y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (!isVaultRock(chunk.getBlockState(p))) continue;
                        geologyCount++;
                        if (base.getBlock(y).isAir()) {
                            additions.add(p);
                            require(IslandChunkGenerator.landAllowed(profile, x, y, z), "Generated arch breaches sea clearance: " + p);
                            smallestUpperGap = Math.min(smallestUpperGap, SeaSurface.cellMinimum(profile, x, z, true) - (y + 1));
                            smallestLowerGap = Math.min(smallestLowerGap, y - (profile.lowerSeaTop() + 1));
                        }
                    }
                }
                if (additions.isEmpty()) continue;
                int roofX = centerX + 1, roofZ = centerZ + 1;
                BlockPos topRoof = additions.stream().filter(p -> p.getX() == roofX && p.getZ() == roofZ)
                        .max(java.util.Comparator.comparingInt(BlockPos::getY)).orElse(null);
                if (topRoof == null) continue;
                // The roof can have several contiguous stone layers. Its underside, rather than
                // the next roof layer, marks the beginning of the walkable opening.
                int openingTop = topRoof.getY();
                while (openingTop >= profile.minLand()
                        && isVaultRock(chunk.getBlockState(new BlockPos(roofX, openingTop, roofZ)))) openingTop--;
                if (openingTop < profile.minLand() || !chunk.getBlockState(new BlockPos(roofX, openingTop, roofZ)).isAir()) continue;
                BlockPos roof = new BlockPos(roofX, openingTop + 1, roofZ);
                if (!level.getBiome(roof).is(RealmBiomes.STONE_VAULTS)) continue;
                int floorY = -1;
                for (int y = openingTop - 1; y >= profile.minLand(); y--) {
                    var state = chunk.getBlockState(new BlockPos(roofX, y, roofZ));
                    if (state.isAir()) continue;
                    if (StoneVaults.isGround(state)) floorY = y;
                    break;
                }
                if (floorY == -1 || openingTop - floorY < 3) continue;
                JsonObject found = new JsonObject();
                found.addProperty("creator_pid", ProcessHandle.current().pid());
                found.addProperty("naturally_generated", true);
                found.addProperty("scene_type", "disposable Creative natural generation inspection; not Survival acceptance");
                found.addProperty("seed", level.getSeed());
                found.addProperty("chunk_x", cx); found.addProperty("chunk_z", cz);
                found.addProperty("search_radius", radius);
                found.addProperty("loaded_candidate_chunks", loadedCandidates);
                found.addProperty("biome_rejected_without_chunk_load", filteredCandidates);
                found.addProperty("geological_blocks_in_chunk", geologyCount);
                found.addProperty("arch_blocks_added_to_native_air", additions.size());
                found.addProperty("minimum_upper_sea_gap", smallestUpperGap);
                found.addProperty("minimum_lower_sea_gap", smallestLowerGap);
                found.addProperty("required_clearance", profile.clearance());
                found.addProperty("roof_x", roof.getX()); found.addProperty("roof_y", roof.getY()); found.addProperty("roof_z", roof.getZ());
                found.addProperty("roof_top_y", topRoof.getY());
                found.addProperty("opening_air_height", openingTop - floorY);
                found.addProperty("broken_roof_layer", "underside; visible recess persists while the upper layer still shelters");
                found.addProperty("floor_y", floorY);
                found.addProperty("biome_key", level.getBiome(roof).unwrapKey().orElseThrow().location().toString());
                found.add("natural_arch_positions", JSON.toJsonTree(additions.stream().map(p -> List.of(p.getX(), p.getY(), p.getZ())).toList()));
                chooseUntouchedRegion(mc, level, generator, cx, cz, found);
                player.removeEffect(MobEffects.NIGHT_VISION);
                naturalPanoramaCamera(player, level, profile, centerX, floorY, centerZ);
                return found;
            }
        }
        throw new IllegalStateException("No naturally generated stone vault within 24 chunks; loaded candidates=" + loadedCandidates);
    }

    private static void chooseUntouchedRegion(Minecraft mc, ServerLevel level, IslandChunkGenerator generator, int originX, int originZ, JsonObject found) {
        var random = level.getChunkSource().randomState();
        for (int dx = 64; dx <= 96; dx++) for (int dz = 64; dz <= 96; dz++) {
            int x = originX + dx, z = originZ + dz;
            if (!StoneVaults.candidate(level.getSeed(), x, z) || Files.exists(regionPath(mc, x, z))) continue;
            if (!generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x * 16 + 7), QuartPos.fromBlock(100),
                    QuartPos.fromBlock(z * 16 + 7), random.sampler()).is(RealmBiomes.STONE_VAULTS)) continue;
            found.addProperty("new_chunk_x", x); found.addProperty("new_chunk_z", z);
            found.addProperty("new_region_absent_at_creation", true);
            return;
        }
        throw new IllegalStateException("No untouched candidate region available for reload check");
    }

    private static JsonObject validateReload(Minecraft mc, ServerLevel level, ServerPlayer player) {
        var restoredGenerator = generator(level);
        var restoredBiomes = restoredGenerator.getBiomeSource().possibleBiomes();
        require(restoredBiomes.stream().anyMatch(biome -> biome.is(RealmBiomes.STONE_VAULTS))
                && restoredBiomes.stream().anyMatch(biome -> biome.is(RealmBiomes.ASH_ISLANDS)),
                "The saved legacy placeholder did not upgrade to the two realm biomes");
        require(player != null && player.getUUID().toString().equals(result.get("player_uuid").getAsString()), "Saved player identity changed");
        BlockPos roof = roofPos(result);
        level.getChunk(roof);
        require(level.getBlockState(roof).isAir(), "Broken natural roof was regenerated after cold restart");
        require(level.getBiome(roof).unwrapKey().orElseThrow().location().toString().equals(result.get("biome_key").getAsString()), "Saved biome changed");
        int x = result.get("new_chunk_x").getAsInt(), z = result.get("new_chunk_z").getAsInt();
        require(!Files.exists(regionPath(mc, x, z)), "Reload candidate region was already generated");
        var fresh = level.getChunk(x, z);
        require(fresh.getHeight() == 256 && fresh.getBlockState(new BlockPos(x * 16, 255, z * 16)).is(Blocks.BEDROCK), "New saved-generator chunk lost geometry bounds");
        require(fresh.getNoiseBiome(QuartPos.fromBlock(x * 16 + 7), QuartPos.fromBlock(100),
                QuartPos.fromBlock(z * 16 + 7)).is(RealmBiomes.STONE_VAULTS), "New chunk did not receive its selected stone biome");
        result.addProperty("broken_roof_air_after_cold_restart", true);
        result.addProperty("saved_biome_unchanged", true);
        result.addProperty("legacy_placeholder_upgraded_on_cold_restart", true);
        result.addProperty("fresh_region_absent_before_reload_generation", true);
        result.addProperty("new_candidate_chunk_generated_after_restart", true);
        result.addProperty("reload_pid", ProcessHandle.current().pid());
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 12000, 0, false, false));
        camera(player, level, roof.getX() + .5, result.get("floor_y").getAsInt() + 1.05, roof.getZ() + .5,
                roof.getX() + .5, roof.getY() + .5, roof.getZ() + .5, false);
        return result;
    }

    /** Recreates just the old generator placeholder, exclusively in this newly created disposable save. */
    private static void installLegacyPlaceholderFixture(Minecraft mc) throws java.io.IOException {
        Path savesRoot = mc.gameDirectory.toPath().resolve("saves").toRealPath();
        Path worldRoot = worldPath(mc).toRealPath();
        require(worldRoot.startsWith(savesRoot) && worldRoot.getParent().equals(savesRoot)
                && worldRoot.getFileName().toString().equals(WORLD), "Legacy fixture escaped its disposable world path");
        require(result.get("naturally_generated").getAsBoolean()
                && result.get("scene_type").getAsString().startsWith("disposable Creative natural generation inspection"),
                "Legacy fixture requires this controller's disposable creation report");
        Path levelDat = worldRoot.resolve("level.dat");
        Path backup = worldRoot.resolve("level.before-legacy-fixture.dat");
        require(!Files.exists(backup), "Legacy placeholder fixture already installed; use a fresh verification profile");
        CompoundTag saved = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
        CompoundTag before = saved.copy();
        require(saved.contains("Data", 10), "Saved level Data is missing");
        CompoundTag data = saved.getCompound("Data");
        require(data.contains("WorldGenSettings", 10), "WorldGenSettings is missing");
        CompoundTag settings = data.getCompound("WorldGenSettings");
        require(settings.contains("dimensions", 10), "Saved dimensions are missing");
        CompoundTag dimensions = settings.getCompound("dimensions");
        require(dimensions.contains("interstice:islands_tall", 10), "Saved tall realm is missing");
        CompoundTag dimension = dimensions.getCompound("interstice:islands_tall");
        require(dimension.contains("generator", 10), "Tall generator is missing");
        CompoundTag generator = dimension.getCompound("generator");
        require(generator.getString("type").equals("interstice:coupled_islands"), "Not our disposable coupled island generator");
        require(generator.contains("geometry", 10) && generator.contains("biome_source", 10), "Generator geometry or biome source is missing");
        CompoundTag geometry = generator.getCompound("geometry").copy();
        CompoundTag oldSource = generator.getCompound("biome_source").copy();
        CompoundTag placeholder = new CompoundTag();
        placeholder.putString("type", "minecraft:fixed");
        placeholder.putString("biome", "minecraft:the_end");
        generator.put("biome_source", placeholder);
        require(generator.getCompound("geometry").equals(geometry), "Fixture changed generator geometry");
        CompoundTag restoredCopy = saved.copy();
        restoredCopy.getCompound("Data").getCompound("WorldGenSettings").getCompound("dimensions")
                .getCompound("interstice:islands_tall").getCompound("generator").put("biome_source", oldSource);
        require(restoredCopy.equals(before), "Fixture changed fields outside its biome source");
        Files.copy(levelDat, backup);
        NbtIo.writeCompressed(saved, levelDat);
        result.addProperty("legacy_placeholder_fixture", true);
        result.addProperty("legacy_fixture_geometry_unchanged", true);
        result.addProperty("legacy_fixture_scope", "Only the disposable world's tall generator biome_source was changed to its former fixed the_end placeholder; not a test of all real older worlds");
    }

    private static IslandChunkGenerator generator(ServerLevel level) {
        require(level != null && level.getChunkSource().getGenerator() instanceof IslandChunkGenerator, "Wrong island generator");
        var generator = (IslandChunkGenerator) level.getChunkSource().getGenerator();
        require(generator.geometry().equals(GeometryProfile.TALL), "Wrong geometry profile");
        return generator;
    }
    private static boolean isVaultRock(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(VaultMaterials.VAULTSTONE.get()) || state.is(VaultMaterials.WEATHERED_VAULTSTONE.get());
    }
    private static void naturalPanoramaCamera(ServerPlayer player, ServerLevel level, GeometryProfile profile,
                                             int centerX, int floorY, int centerZ) {
        for (int elevation = 0; elevation <= 2; elevation++) for (int[] offset : new int[][]{{11, -11}, {-11, -11}, {11, 11}, {-11, 11}}) {
            double x = centerX + offset[0] + .5, y = floorY + 6.5 + elevation, z = centerZ + offset[1] + .5;
            if (y + player.getEyeHeight() >= SeaSurface.cellMinimum(profile, (int) Math.floor(x), (int) Math.floor(z), true) - 1) continue;
            if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir()
                    || !level.getBlockState(BlockPos.containing(x, y + 1, z)).isAir()
                    || !level.getBlockState(BlockPos.containing(x, y + 2, z)).isAir()) continue;
            camera(player, level, x, y, z, centerX + .5, floorY + 3.0, centerZ + .5, true);
            return;
        }
        throw new IllegalStateException("No unobstructed panorama camera near the natural vault");
    }
    private static void camera(ServerPlayer player, ServerLevel level, double x, double y, double z,
                               double targetX, double targetY, double targetZ, boolean flying) {
        double dx = targetX - x, dz = targetZ - z, dy = targetY - (y + player.getEyeHeight());
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, x, y, z, java.util.Set.of(), yaw, pitch);
        player.setDeltaMovement(0, 0, 0);
        player.getAbilities().flying = flying;
        player.onUpdateAbilities();
    }
    private static BlockPos roofPos(JsonObject data) {
        return new BlockPos(data.get("roof_x").getAsInt(), data.get("roof_y").getAsInt(), data.get("roof_z").getAsInt());
    }
    private static Path worldPath(Minecraft mc) { return mc.gameDirectory.toPath().resolve("saves").resolve(WORLD); }
    private static Path regionPath(Minecraft mc, int x, int z) {
        return worldPath(mc).resolve("dimensions/interstice/islands_tall/region")
                .resolve("r." + Math.floorDiv(x, 32) + "." + Math.floorDiv(z, 32) + ".mca");
    }
    private static Path reportPath(Minecraft mc, String mode) { return mc.gameDirectory.toPath().resolve("vault-" + mode + "-validation.json"); }
    private static void recordCamera(Minecraft mc, String name) {
        JsonObject camera = new JsonObject();
        camera.addProperty("x", mc.player.getX()); camera.addProperty("y", mc.player.getY()); camera.addProperty("z", mc.player.getZ());
        camera.addProperty("yaw", mc.player.getYRot()); camera.addProperty("pitch", mc.player.getXRot());
        result.add(name, camera);
    }
    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> System.out.println("STONE_VAULT_SCREENSHOT " + name));
    }
    private static void requireScreenshot(Minecraft mc, String name) {
        require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)), "Screenshot was not saved: " + name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void report(Minecraft mc, boolean passed, String reason) {
        if (finished) return;
        finished = true;
        result.addProperty("passed", passed); result.addProperty("reason", reason); result.addProperty("mode", MODE);
        result.addProperty("stage", stage); result.addProperty("duration_seconds", (System.nanoTime() - began) / 1_000_000_000L);
        try { Files.writeString(reportPath(mc, MODE), JSON.toJson(result)); }
        catch (Exception error) { error.printStackTrace(); }
        System.out.println("STONE_VAULT_VALIDATION " + JSON.toJson(result));
        mc.stop();
    }
}
