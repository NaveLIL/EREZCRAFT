package pro.erez.interstice.tide;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.Entity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.sound.ModSounds;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;

/**
 * Server-side controller for the Interstice tide cycle, storms, and lightning strikes.
 * Advances with server ticks, preserving phase during sleeping, /time set, or empty dimension.
 */
@EventBusSubscriber(modid = Interstice.ID)
public final class TideManager {
    private static int lightningCooldown = 0;
    private static int warningSoundCooldown = 0;
    private static int particleCooldown = 0;
    private static boolean buoyancyActiveLastTick = false;

    private TideManager() {}

    private static ServerLevel primaryLevel(MinecraftServer server) {
        ServerLevel level = server.getLevel(IslandWorld.TALL_WORLD);
        if (level != null) return level;
        level = server.getLevel(IslandWorld.WORLD);
        if (level != null) return level;
        return server.overworld();
    }

    public static TideSavedData getSavedData(MinecraftServer server) {
        return TideSavedData.get(primaryLevel(server));
    }

    public static TideState getState(MinecraftServer server) {
        return getSavedData(server).snapshot();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;
        TideSavedData data = getSavedData(server);
        boolean transition = data.tick();
        TideState state = data.snapshot();

        if (transition) {
            TidePhase next = state.phase().next();
            RandomSource random = primaryLevel(server).random;
            long nextDuration = next.pickDuration(random);
            data.setPhase(next, nextDuration);
            state = data.snapshot();
            applyPhaseWeather(server, state);
            TideSync.broadcast(state);
            broadcastPhaseSounds(server, next);
        } else if (state.phaseTicksElapsed() % 200 == 0) {
            // Heartbeat calibration every 10 seconds
            TideSync.broadcast(state);
        }

        // Atmospheric effects during WARNING
        if (state.phase() == TidePhase.WARNING) {
            tickWarningAtmosphere(server, state);
        }

        // Atmospheric effects during SURGE
        if (state.phase() == TidePhase.SURGE) {
            tickSurgeAtmosphere(server, state);
            if (state.phaseTicksElapsed() % 20 == 0) {
                wakeAllIslandSprouts(server);
            }
        }

        // Rising wind vortex particles during WARNING and SURGE
        if (state.phase() == TidePhase.WARNING || state.phase() == TidePhase.SURGE) {
            tickWindParticles(server, state);
        }

        // Buoyant lifting force in island dimensions during SURGE and EBB
        tickBuoyancy(server, state.buoyancyIntensity());
    }

    private static void broadcastPhaseSounds(MinecraftServer server, TidePhase phase) {
        for (var level : server.getAllLevels()) {
            if (!IslandWorld.isIsland(level.dimension())) continue;
            for (var player : level.players()) {
                if (phase == TidePhase.WARNING) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            ModSounds.TIDE_WARNING.get(), net.minecraft.sounds.SoundSource.WEATHER, 1.2F, 0.7F);
                } else if (phase == TidePhase.SURGE) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            ModSounds.TIDE_SURGE.get(), net.minecraft.sounds.SoundSource.WEATHER, 1.5F, 0.6F);
                }
                pro.erez.interstice.TideSproutBlock.wakeNearbySprouts(level, player.blockPosition(), 36);
            }
        }
    }

    private static void tickWarningAtmosphere(MinecraftServer server, TideState state) {
        if (--warningSoundCooldown <= 0) {
            warningSoundCooldown = 140; // Every 7 seconds
            for (var level : server.getAllLevels()) {
                if (!IslandWorld.isIsland(level.dimension())) continue;
                for (var player : level.players()) {
                    float pitch = 0.65F + player.getRandom().nextFloat() * 0.2F;
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            ModSounds.TIDE_WARNING.get(), net.minecraft.sounds.SoundSource.WEATHER, 0.85F, pitch);
                }
            }
        }
    }

    private static void tickWindParticles(MinecraftServer server, TideState state) {
        if (--particleCooldown > 0) return;
        boolean isSurge = state.phase() == TidePhase.SURGE;
        particleCooldown = isSurge ? 4 : 8;

        for (var level : server.getAllLevels()) {
            if (!IslandWorld.isIsland(level.dimension())) continue;
            for (var player : level.players()) {
                var random = player.getRandom();
                int px = player.getBlockX();
                int py = player.getBlockY();
                int pz = player.getBlockZ();

                int bursts = isSurge ? 4 : 2;
                for (int i = 0; i < bursts; i++) {
                    int rx = px + random.nextIntBetweenInclusive(-14, 14);
                    int rz = pz + random.nextIntBetweenInclusive(-14, 14);
                    double sy = py + random.nextDouble() * 2.5 - 0.5;
                    double vy = isSurge ? 0.32 + 0.15 * state.buoyancyIntensity() : 0.14;
                    level.sendParticles(ParticleTypes.CLOUD, rx + 0.5, sy, rz + 0.5, 1,
                            (random.nextDouble() - 0.5) * 0.08, vy, (random.nextDouble() - 0.5) * 0.08, 0.04);

                    if (isSurge && random.nextFloat() < 0.35F) {
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, rx + 0.5, sy, rz + 0.5, 2,
                                0.2, 0.3, 0.2, 0.08);
                    }
                }
            }
        }
    }

    private static void tickBuoyancy(MinecraftServer server, float intensity) {
        boolean activeNow = intensity > 0.0F;
        boolean needsCleanup = !activeNow && buoyancyActiveLastTick;
        buoyancyActiveLastTick = activeNow;

        for (var level : server.getAllLevels()) {
            if (!IslandWorld.isIsland(level.dimension())) continue;
            for (var player : level.players()) {
                BuoyancyController.applyEntityBuoyancy(player, intensity);
                pro.erez.interstice.gear.RealmGear.tick(player);
            }
            if (activeNow || needsCleanup) {
                List<Entity> nonPlayerEntities = new ArrayList<>();
                for (var entity : level.getAllEntities()) {
                    if (entity != null && !entity.isRemoved() && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                        nonPlayerEntities.add(entity);
                    }
                }
                for (var entity : nonPlayerEntities) {
                    BuoyancyController.applyEntityBuoyancy(entity, intensity);
                }
            }
        }
    }

    private static void applyPhaseWeather(MinecraftServer server, TideState state) {
        int durationTicks = (int) Math.min(Integer.MAX_VALUE, state.phaseDurationTicks());
        boolean isSurge = state.phase() == TidePhase.SURGE;
        for (var level : server.getAllLevels()) {
            if (IslandWorld.isIsland(level.dimension())) {
                if (isSurge) {
                    level.setWeatherParameters(0, durationTicks, true, true);
                } else {
                    level.setWeatherParameters(durationTicks, 0, false, false);
                }
            }
        }
    }

    private static void tickSurgeAtmosphere(MinecraftServer server, TideState state) {
        if (--lightningCooldown > 0) return;
        lightningCooldown = state.lightningIntervalTicks();

        for (var level : server.getAllLevels()) {
            if (!IslandWorld.isIsland(level.dimension())) continue;
            var players = level.players();
            if (players.isEmpty()) continue;

            RandomSource random = level.random;
            ServerPlayer targetPlayer = players.get(random.nextInt(players.size()));
            strikeLightningNear(level, targetPlayer, random);

            // In peak surge, unleash additional thunderbolts for tripled intensity
            if (state.isPeakSurge() && random.nextFloat() < 0.65F) {
                strikeLightningNear(level, targetPlayer, random);
            }
        }
    }

    private static void strikeLightningNear(ServerLevel level, ServerPlayer player, RandomSource random) {
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        int dx = random.nextIntBetweenInclusive(-48, 48);
        int dz = random.nextIntBetweenInclusive(-48, 48);
        int sx = px + dx;
        int sz = pz + dz;

        if (!level.hasChunk(sx >> 4, sz >> 4)) return;

        GeometryProfile profile = GeometryProfiles.get(level);
        int maxLand = profile.maxLand();
        int minLand = IslandChunkGenerator.featureMinimum(level,profile);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = maxLand; y >= minLand; y--) {
            pos.set(sx, y, sz);
            var state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;

            // Found highest solid block in column. Check if top has clearance/shelter.
            BlockPos above = pos.above();
            if (!level.getBlockState(above).isAir() || !level.getBlockState(above.above()).isAir()) break;

            // Verify no solid roof between this island block and the upper sea
            boolean exposed = true;
            for (int scanY = y + 3; scanY <= maxLand; scanY += 3) {
                pos.set(sx, scanY, sz);
                if (!level.getBlockState(pos).isAir()) {
                    exposed = false;
                    break;
                }
            }
            if (!exposed) break;

            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(above));
                bolt.setVisualOnly(false);
                level.addFreshEntity(bolt);
            }
            break;
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TideSync.sendTo(player, getState(player.server));
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TideSync.sendTo(player, getState(player.server));
        }
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("interstice")
                .then(Commands.literal("tide").requires(source -> source.hasPermission(2))
                        .executes(context -> reportStatus(context.getSource()))
                        .then(Commands.literal("status").executes(context -> reportStatus(context.getSource())))
                        .then(Commands.literal("skip").executes(context -> skipPhase(context.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (TidePhase p : TidePhase.values()) b.suggest(p.getSerializedName());
                                            return b.buildFuture();
                                        })
                                        .executes(context -> setPhase(context.getSource(),
                                                StringArgumentType.getString(context, "phase")))))));
    }

    private static int reportStatus(CommandSourceStack source) {
        TideState state = getState(source.getServer());
        long remainingSec = state.remainingTicks() / 20;
        long totalSec = state.phaseDurationTicks() / 20;
        int pct = Math.round(state.progress() * 100);
        String intensity = String.format(Locale.ROOT, "%.2f", state.intensity());

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Tide Phase: %s | Progress: %d%% (%ds / %ds) | Intensity: %s | Peak: %s | Cycles: %d",
                state.phase().getSerializedName().toUpperCase(Locale.ROOT),
                pct, remainingSec, totalSec, intensity, state.isPeakSurge(), state.totalCycles())), false);
        return 1;
    }

    private static int skipPhase(CommandSourceStack source) {
        TideSavedData data = getSavedData(source.getServer());
        TidePhase next = data.snapshot().phase().next();
        long duration = next.pickDuration(primaryLevel(source.getServer()).random);
        data.setPhase(next, duration);
        TideState state = data.snapshot();
        applyPhaseWeather(source.getServer(), state);
        TideSync.broadcast(state);
        wakeAllIslandSprouts(source.getServer());
        source.sendSuccess(() -> Component.literal("Advanced tide to " + next.getSerializedName().toUpperCase(Locale.ROOT)), true);
        return 1;
    }

    private static int setPhase(CommandSourceStack source, String phaseName) {
        TidePhase target = TidePhase.byName(phaseName);
        TideSavedData data = getSavedData(source.getServer());
        long duration = target.pickDuration(primaryLevel(source.getServer()).random);
        data.setPhase(target, duration);
        TideState state = data.snapshot();
        applyPhaseWeather(source.getServer(), state);
        TideSync.broadcast(state);
        wakeAllIslandSprouts(source.getServer());
        source.sendSuccess(() -> Component.literal("Set tide phase to " + target.getSerializedName().toUpperCase(Locale.ROOT)), true);
        return 1;
    }

    public static void wakeAllIslandSprouts(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            if (!IslandWorld.isIsland(level.dimension())) continue;
            for (var player : level.players()) {
                pro.erez.interstice.TideSproutBlock.wakeNearbySprouts(level, player.blockPosition(), 32);
            }
        }
    }
}
