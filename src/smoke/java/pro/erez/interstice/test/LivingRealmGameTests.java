package pro.erez.interstice.test;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.ecology.CaveEcology;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.rift.RiftSafety;
import pro.erez.interstice.rift.RiftLinks;
import pro.erez.interstice.rift.RiftTravel;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.RealmBiomes;
import pro.erez.interstice.worldgen.StoneVaults;
import pro.erez.interstice.worldgen.terrain.TerrainV2;

/** Tests actual dimension integration, separately from the numerical terrain and cave fixtures. */
@GameTestHolder("interstice_living")
@PrefixGameTestTemplate(false)
public final class LivingRealmGameTests {
    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(0, 256);
    private record Fixture(IslandChunkGenerator generator, RandomState random, long seed) {}
    private record Candidate(int x, int z, int surface, int thickness) {}
    private static ServerLevel world(GameTestHelper h) {
        return Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.LIVING_WORLD), "Missing living realm test dimension");
    }
    private static IslandChunkGenerator generator(ServerLevel level) { return (IslandChunkGenerator) level.getChunkSource().getGenerator(); }
    private static void bind(GameTestHelper h, IslandChunkGenerator generator, RandomState random, long seed) {
        generator.createState(h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup(), random, seed);
    }
    private static Fixture fixture(GameTestHelper h, long seed) {
        var template = generator(world(h));
        var generator = new IslandChunkGenerator(template.getBiomeSource(), template.generatorSettings(), GeometryProfile.TALL, 4);
        var random = RandomState.create(template.generatorSettings().value(), h.getLevel().registryAccess().registryOrThrow(Registries.NOISE).asLookup(), seed);
        bind(h, generator, random, seed);
        return new Fixture(generator, random, seed);
    }
    private static CompletableFuture<ProtoChunk> noise(GameTestHelper h, Fixture fixture, int cx, int cz) {
        var chunk = new ProtoChunk(new ChunkPos(cx, cz), UpgradeData.EMPTY, HEIGHT, h.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null);
        return fixture.generator.createBiomes(fixture.random, Blender.empty(), world(h).structureManager(), chunk)
                .thenCompose(c -> fixture.generator.fillFromNoise(Blender.empty(), fixture.random, world(h).structureManager(), c))
                .thenApply(c -> (ProtoChunk)c);
    }
    private static int top(NoiseColumn column) {
        for (int y = GeometryProfile.TALL.maxLand(); y > GeometryProfile.TALL.lowerSeaTop(); y--)
            if (StoneVaults.isGround(column.getBlock(y))) return y;
        return -1;
    }
    private static int actualTop(ChunkAccess chunk, int x, int z) {
        for (int y = GeometryProfile.TALL.maxLand(); y > GeometryProfile.TALL.lowerSeaTop(); y--)
            if (StoneVaults.isGround(chunk.getBlockState(new BlockPos(x, y, z)))
                    || chunk.getBlockState(new BlockPos(x,y,z)).is(pro.erez.interstice.minerals.MineralEcology.TOXIC_SAND.get())
                    || chunk.getBlockState(new BlockPos(x,y,z)).is(pro.erez.interstice.minerals.MineralEcology.MINERAL_POWDER.get())) return y;
        return -1;
    }
    /** Coarse columns classify first. Only the three selected samples become actual FULL chunks. */
    private static Candidate[] candidates(GameTestHelper h) {
        var level = world(h); var generator = generator(level); var random = level.getChunkSource().randomState();
        Candidate ash = null, garden = null, mountain = null;
        // Local coordinate2 stays outside central ruin/arch footprints: measure the terrain,
        // rather than counting an independently placed masonry roof as a mountain summit.
        for (int x = -2046; x <= 2046; x += 96) for (int z = -2046; z <= 2046; z += 96) {
            var fields = generator.terrainColumn(random, x, z); var weights = fields.weights();
            if (weights.ash() < .995 && weights.gardens() < .995 && weights.vaults() < .995) continue;
            // A nearly-zero mountain blend is still an actual foothill biome. Select the
            // semantic core and its surrounding native quart cells, not only a blend weight.
            var expected=weights.ash()>.995?RealmBiomes.ASH_ISLANDS:weights.gardens()>.995?RealmBiomes.PALE_GARDENS:RealmBiomes.STONE_VAULTS;
            boolean core=true;
            for(int[] offset:new int[][]{{0,0},{8,0},{-8,0},{0,8},{0,-8}})
                if(!generator.getBiomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x+offset[0]),10,
                        net.minecraft.core.QuartPos.fromBlock(z+offset[1]),random.sampler()).is(expected))core=false;
            if(!core)continue;
            if (weights.ash() > .995 && ash == null) {
                var column = generator.getBaseColumn(x, z, level, random); int thickness = 0;
                for (int y = 41; y < 198; y++) if (StoneVaults.isGround(column.getBlock(y))) thickness++;
                if (thickness >= 18) ash = new Candidate(x, z, top(column), thickness);
            } else if (weights.gardens() > .995 && garden == null) {
                int surface = top(generator.getBaseColumn(x, z, level, random));
                if (surface >= 34 && surface <= 39) garden = new Candidate(x, z, surface, 0);
            } else if (weights.vaults() > .995 && (mountain == null || fields.vaultHeight() > mountain.surface)) {
                int surface = top(generator.getBaseColumn(x, z, level, random));
                if (surface >= 145 && (mountain == null || surface > mountain.surface)) mountain = new Candidate(x, z, surface, 0);
            }
        }
        h.assertTrue(ash != null && garden != null && mountain != null, "Bounded climate scan found no thick island / low garden / high mountain cores");
        return new Candidate[]{ash, garden, mountain};
    }
    private record FoundationPath(List<BlockPos> cells, int visited, int columns, int lowest,
                                  boolean touchedBoundary, boolean exhaustedBudget) {}
    /** A chamber can span several chunks: find a real six-face rock route in global columns.
     * Search is bounded to5x5 chunks and250000 visits, favoring descent without pruning uphill routes.
     * Only chunks crossed by the resulting route are subsequently loaded for physical verification. */
    private static FoundationPath foundationPath(ServerLevel level, BlockPos start) {
        var generator = generator(level); var random = level.getChunkSource().randomState(); var profile = generator.geometry();
        var center = new ChunkPos(start);
        int minX = center.getMinBlockX() - 32, maxX = center.getMinBlockX() + 47;
        int minZ = center.getMinBlockZ() - 32, maxZ = center.getMinBlockZ() + 47;
        var pending = new PriorityQueue<BlockPos>(Comparator.<BlockPos>comparingInt(p -> p.getY())
                .thenComparingInt(p -> Math.abs(p.getX() - start.getX()) + Math.abs(p.getZ() - start.getZ())));
        var previous = new HashMap<Long, BlockPos>(); var columns = new HashMap<Long, NoiseColumn>();
        previous.put(start.asLong(), null); pending.add(start); int visited = 0, lowest = start.getY(); boolean boundary = false;
        final int budget = 250000;
        while (!pending.isEmpty() && visited < budget) {
            var at = pending.remove(); visited++;
            long key = ((long)at.getX() << 32) ^ (at.getZ() & 0xffffffffL);
            var column = columns.computeIfAbsent(key, ignored -> generator.getBaseColumn(at.getX(), at.getZ(), level, random));
            if (!StoneVaults.isGround(column.getBlock(at.getY()))) continue;
            lowest = Math.min(lowest, at.getY());
            if (at.getY() <= profile.minY() + 5) {
                var path = new ArrayList<BlockPos>();
                for (BlockPos node = at; node != null; node = previous.get(node.asLong())) path.add(node);
                Collections.reverse(path);
                return new FoundationPath(List.copyOf(path), visited, columns.size(), lowest, boundary, false);
            }
            for (var direction : net.minecraft.core.Direction.values()) {
                var next = at.relative(direction);
                if (next.getX() < minX || next.getX() > maxX || next.getZ() < minZ || next.getZ() > maxZ) { boundary = true; continue; }
                if (next.getY() <= profile.minY() || next.getY() > profile.maxLand() || previous.containsKey(next.asLong())) continue;
                previous.put(next.asLong(), at); pending.add(next);
            }
        }
        return new FoundationPath(List.of(), visited, columns.size(), lowest, boundary, !pending.isEmpty());
    }
    private static void verifyActualFoundation(GameTestHelper h, ServerLevel level, BlockPos start) {
        var result = foundationPath(level, start);
        String diagnostic = "start=" + start + " visited=" + result.visited + " columns=" + result.columns
                + " lowest=" + result.lowest + " touchedBoundary=" + result.touchedBoundary + " budgetExhausted=" + result.exhaustedBudget;
        h.assertTrue(!result.cells.isEmpty(), "No canonical bedrock-shell route inside bounded5x5 neighborhood; " + diagnostic);
        var chunks = new HashMap<ChunkPos, ChunkAccess>(); BlockPos before = null;
        for (var at : result.cells) {
            var chunk = chunks.computeIfAbsent(new ChunkPos(at), p -> level.getChunk(p.x, p.z));
            h.assertTrue(StoneVaults.isGround(chunk.getBlockState(at)), "Actual decorated chunk broke its canonical rock support at " + at + "; " + diagnostic);
            if (before != null) h.assertTrue(before.distManhattan(at) == 1, "Foundation witness contains a non-face-connected jump");
            before = at;
        }
        h.assertTrue(before != null && before.getY() <= 5, "Physical support witness did not reach the lower foundation shell");
        System.out.println("LIVING_REALM_FOUNDATION " + diagnostic + " actualChunks=" + chunks.size()
                + " pathLength=" + result.cells.size() + " reached=" + before);
    }
    @GameTest(template="empty", timeoutTicks=600)
    public static void actualBiomeCoresHaveDifferentReliefAndFoundation(GameTestHelper h) {
        var level = world(h); var generator = generator(level); var samples = candidates(h);
        h.assertTrue(generator.isLivingRealm() && generator.terrainRevision() == 4 && generator.geometry().equals(GeometryProfile.TALL), "Living realm silently uses old island terrain or moved seas");
        var biomes = java.util.List.of(RealmBiomes.ASH_ISLANDS, RealmBiomes.PALE_GARDENS, RealmBiomes.STONE_VAULTS);
        for (int i = 0; i < samples.length; i++) {
            var sample = samples[i]; var chunk = level.getChunk(sample.x >> 4, sample.z >> 4);
            int surface = actualTop(chunk, sample.x, sample.z); var pos = new BlockPos(sample.x, surface, sample.z);
            h.assertTrue(surface == sample.surface, "Decoration changed the underlying terrain silhouette at " + pos);
            h.assertTrue(level.getBiome(pos).is(biomes.get(i)), "Biome palette does not match its terrain core at " + pos);
            h.assertTrue(chunk.getBlockState(pos.atY(0)).is(Blocks.BEDROCK) && chunk.getBlockState(pos.atY(255)).is(Blocks.BEDROCK), "Actual living chunk moved its bedrock shell");
            double upper = SeaSurface.cellMinimum(GeometryProfile.TALL, sample.x, sample.z, true);
            for (int y = (int)Math.floor(upper); y < 255; y++)
                h.assertTrue(chunk.getBlockState(pos.atY(y)).is(Interstice.LIGHT_SEA.get()), "Actual FULL chunk lost the unchanged shaped upper sea");
            for (int y = 206; y < Math.floor(upper); y++)
                h.assertTrue(chunk.getBlockState(pos.atY(y)).isAir(), "Actual terrain or decoration entered the upper clearance shell");
            for (int y = 1; y <= 5; y++) {
                var foundation = chunk.getBlockState(pos.atY(y));
                h.assertTrue(i == 0 ? foundation.is(Interstice.HEAVY_BLOCK.get()) : StoneVaults.isGround(foundation), "Island / grounded foundation mismatch at " + pos.atY(y));
            }
            if (i != 0) {
                var rock=pos;
                for(int veneer=0;veneer<5&&!StoneVaults.isGround(level.getBlockState(rock));veneer++)rock=rock.below();
                h.assertTrue(StoneVaults.isGround(level.getBlockState(rock)),"Loose surface veneer has no actual rock foundation");
                verifyActualFoundation(h, level, rock);
            }
            else {
                int stone = 0;
                for (int y = 41; y < 198; y++) if (StoneVaults.isGround(chunk.getBlockState(pos.atY(y)))) stone++;
                h.assertTrue(stone >= 18 && chunk.getBlockState(pos.atY(40)).isAir(), "Rare ash body became a thin platform or a connected mainland");
            }
            System.out.println("LIVING_REALM_CORE biome=" + biomes.get(i).location() + " x=" + sample.x + " z=" + sample.z + " surface=" + surface);
        }
        h.assertTrue(samples[2].surface - samples[1].surface >= 50, "Mountains and gardens retain the same visible altitude");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=600)
    public static void nativeNoiseAndCanonicalColumnsAgreeIncludingBothSeas(GameTestHelper h) {
        var fixture = fixture(h, 20261006L); var pending = noise(h, fixture, -1, 0);
        h.succeedWhen(() -> {
            h.assertTrue(pending.isDone(), "Waiting for NOISE-stage living chunk"); var chunk = pending.join(); var p = new BlockPos.MutableBlockPos();
            int keptRock = 0, filledWater = 0, dryCaves=0;
            for (int x = -16; x < 0; x++) for (int z = 0; z < 16; z++) {
                var base = fixture.generator.getBaseColumn(x, z, HEIGHT, fixture.random);
                var uncarved=fixture.generator.terrainColumn(fixture.random,x,z);
                double ceiling = SeaSurface.cellMinimum(GeometryProfile.TALL, x, z, true);
                for (int y = 0; y < 256; y++) {
                    p.set(x, y, z); var raw = chunk.getBlockState(p); var mapped = fixture.generator.terrainMaterialAt(x,y,z,raw,uncarved);
                    h.assertTrue(mapped.equals(base.getBlock(y)), "NOISE / canonical column mismatch at " + p);
                    if (y > 0 && y <= 34) {
                        if (raw.isAir()&&y>5&&uncarved.density(y)>0) {h.assertTrue(mapped.isAir(),"Sealed subtractive cave was replaced by ocean");dryCaves++;}
                        else if (raw.isAir()) { h.assertTrue(mapped.is(Interstice.HEAVY_BLOCK.get()), "Lower sea did not fill its actual exterior empty cell"); filledWater++; }
                        else { h.assertTrue(mapped.equals(raw), "Lower sea erased actual foundation rock"); keptRock++; }
                    }
                    if (y == 0 || y == 255) h.assertTrue(mapped.is(Blocks.BEDROCK), "World shell was moved by terrain revision");
                    else if (y >= Math.floor(ceiling)) h.assertTrue(mapped.is(Interstice.LIGHT_SEA.get()), "Upper toxic sea no longer follows its existing geometry");
                    else if (StoneVaults.isGround(mapped)) h.assertTrue(y + 1 <= ceiling - 6, "Living terrain breaches upper-sea clearance");
                }
            }
            h.assertTrue(keptRock + filledWater +dryCaves== 16 * 16 * 34, "Lower-sea mapping did not cover every column");
            h.assertTrue(IslandChunkGenerator.livingMaterialAt(GeometryProfile.TALL, -1, 10, 0, Interstice.RIFTSTONE.get().defaultBlockState()).is(Interstice.RIFTSTONE.get()), "Subsea material conversion still overwrites explicit rock");
        });
    }
    @GameTest(template="empty", timeoutTicks=600)
    public static void neighboringNoiseChunksAreIndependentOfOrderAndWarmSampler(GameTestHelper h) {
        var fixture = fixture(h, 20261006L);
        var pending = noise(h, fixture, -1, 0).thenCompose(west -> noise(h, fixture, 0, 0).thenApply(east -> new ProtoChunk[]{west, east}))
                .thenCompose(first -> {
                    // Warm another world/seed before replaying in the opposite order.
                    var other = fixture(h, 20261006L + (1L << 32));
                    other.generator.getBaseColumn(-1, 7, HEIGHT, other.random);
                    return noise(h, fixture, 0, 0).thenCompose(east -> noise(h, fixture, -1, 0).thenApply(west -> new ProtoChunk[]{first[0], first[1], west, east}));
                });
        h.succeedWhen(() -> {
            h.assertTrue(pending.isDone(), "Waiting for reordered living NOISE chunks"); var chunks = pending.join(); var p = new BlockPos.MutableBlockPos();
            for (int x = -16; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 256; y++) {
                p.set(x, y, z); int side = x < 0 ? 0 : 1;
                h.assertTrue(chunks[side].getBlockState(p).equals(chunks[side + 2].getBlockState(p)), "Generation order or another world changed a living chunk at " + p);
            }
            for (int x : new int[]{-1, 0}) for (int z = 0; z < 16; z++) {
                var column = fixture.generator.getBaseColumn(x, z, HEIGHT, fixture.random);
                var uncarved=fixture.generator.terrainColumn(fixture.random,x,z);
                for (int y = 0; y < 256; y++) {
                    p.set(x, y, z); var raw = chunks[x < 0 ? 0 : 1].getBlockState(p);
                    h.assertTrue(fixture.generator.terrainMaterialAt(x,y,z,raw,uncarved).equals(column.getBlock(y)), "World-coordinate border disagrees with the canonical column at " + p);
                }
            }
        });
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void generatorCodecBindsRevisionAndPreservesLegacyDefault(GameTestHelper h) {
        var fixture = fixture(h, 20261006L); var ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
        var encoded = IslandChunkGenerator.CODEC.codec().encodeStart(ops, fixture.generator).getOrThrow().getAsJsonObject();
        h.assertTrue(encoded.get("terrain_revision").getAsInt() == 4, "Living generator lost its saved revision");
        var reloaded = IslandChunkGenerator.CODEC.codec().parse(ops, encoded).getOrThrow(); bind(h, reloaded, fixture.random, fixture.seed);
        for (int x : new int[]{-121, -1, 0, 83, 1201}) {
            var before = fixture.generator.getBaseColumn(x, 17, HEIGHT, fixture.random); var after = reloaded.getBaseColumn(x, 17, HEIGHT, fixture.random);
            for (int y = 0; y < 256; y++) h.assertTrue(before.getBlock(y).equals(after.getBlock(y)), "Cold generator decode changed its seeded terrain");
        }
        var oldLevel = Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD)); var old = generator(oldLevel);
        var legacyJson = IslandChunkGenerator.CODEC.codec().encodeStart(ops, old).getOrThrow().getAsJsonObject(); legacyJson.remove("terrain_revision");
        var preserved = IslandChunkGenerator.CODEC.codec().parse(ops, legacyJson).getOrThrow();
        h.assertTrue(preserved.terrainRevision() == 1 && !preserved.isLivingRealm() && preserved.geometry().equals(GeometryProfile.TALL), "Absent terrain field changed an existing tall world");
        for (int x : new int[]{-33, 0, 71}) {
            var a = old.getBaseColumn(x, 5, oldLevel, oldLevel.getChunkSource().randomState()); var b = preserved.getBaseColumn(x, 5, oldLevel, oldLevel.getChunkSource().randomState());
            for (int y = 0; y < 256; y++) h.assertTrue(a.getBlock(y).equals(b.getBlock(y)), "Old saved generator changed after adding the new revision");
        }
        var invalid = encoded.deepCopy(); invalid.addProperty("terrain_revision", 6);
        h.assertTrue(IslandChunkGenerator.CODEC.codec().parse(ops, invalid).error().isPresent(), "Unknown future terrain revision was accepted");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void highSeedBitsChangeIndependentLivingWorldColumns(GameTestHelper h) {
        var first = fixture(h, 20261006L); var second = fixture(h, 20261006L + (1L << 32)); int differences = 0;
        for (int x : new int[]{-1901, -501, -3, 0, 211, 701, 1601}) {
            var before = first.generator.getBaseColumn(x, 71, HEIGHT, first.random);
            var other = second.generator.getBaseColumn(x, 71, HEIGHT, second.random);
            var after = first.generator.getBaseColumn(x, 71, HEIGHT, first.random);
            for (int y = 1; y <= 205; y++) {
                h.assertTrue(before.getBlock(y).equals(after.getBlock(y)), "Sampling another world's seed changed the first realm");
                if (!before.getBlock(y).equals(other.getBlock(y))) differences++;
            }
        }
        h.assertTrue(differences > 0, "Independent living world ignores seed changes in the high bits"); h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=600)
    public static void actualEntryHasDryHeadroomAndRejectsPermeableHazardPlants(GameTestHelper h) {
        var level = world(h); var landing = IslandWorld.findLanding(level);
        h.assertTrue(IslandWorld.isSafeLandingPlatform(level, landing.below()) && RiftSafety.standing(level, landing), "Actual new-dimension entry is not a safe dry platform");
        h.assertTrue(landing.getY() >= 35 && landing.getY() <= 131, "Living expedition starts submerged or at an inaccessible high mountain");
        var test = h.absolutePos(new BlockPos(4, 3, 4)); h.getLevel().setBlock(test.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(test, Blocks.AIR.defaultBlockState(), 3); h.getLevel().setBlock(test.above(), Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(RiftSafety.standing(h.getLevel(), test), "Dry collision fixture itself is unsafe");
        h.getLevel().setBlock(test, CaveEcology.STING_FROND.get().defaultBlockState(), 3);
        h.assertTrue(!RiftSafety.standing(h.getLevel(), test), "Portal landing accepts a non-colliding stinging plant");
        h.getLevel().setBlock(test, CaveEcology.CLINGWEED.get().defaultBlockState(), 3);
        h.assertTrue(!RiftSafety.standing(h.getLevel(), test), "Portal landing accepts an inventory-stealing plant curtain");
        var origin = h.absolutePos(new BlockPos(10, 3, 10));
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            h.getLevel().setBlock(origin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
            for (int dy = 0; dy < 3; dy++) h.getLevel().setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
        }
        var player = TestPlayers.create(h, new BlockPos(10, 3, 10), net.minecraft.world.level.GameType.SURVIVAL);
        try {
            var source = new RiftLinks.Endpoint(h.getLevel().dimension(), origin, net.minecraft.core.Direction.Axis.X);
            h.assertTrue(RiftTravel.enter(player, source, RiftLinks.Kind.FISHING), "Production first-rift transfer failed on the new terrain");
            player.hasChangedDimension();
            var current=player.server.getLevel(IslandWorld.CURRENT_WORLD);
            h.assertTrue(player.serverLevel() == current && ShelterDetector.isSheltered(current, player), "Unpaired production rift does not reach a sheltered current-realm landing");
            var link = RiftLinks.get(player.server).byId(player.getPersistentData().getUUID(RiftTravel.ACTIVE));
            h.assertTrue(link != null && link.echo().dimension().equals(IslandWorld.CURRENT_WORLD), "First-rift routing used a fake test destination or old island world");
            player.getPersistentData().remove(RiftTravel.COOLDOWN);
            h.assertTrue(RiftTravel.returnThroughEcho(player, link.echo().pos()), "Production living-realm echo cannot return to its original safe endpoint");
            player.hasChangedDimension();
            h.assertTrue(player.serverLevel() == h.getLevel() && RiftSafety.standing(h.getLevel(), player.blockPosition()), "Living realm round-trip returned to an unsafe source");
        } finally { TestPlayers.remove(player); }
        h.succeed();
    }
}
