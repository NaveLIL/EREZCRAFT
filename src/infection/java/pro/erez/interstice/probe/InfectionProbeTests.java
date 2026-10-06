package pro.erez.interstice.probe;

import com.Harbinger.Spore.Sentities.Organoids.Mound;
import com.Harbinger.Spore.core.SConfig;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;

@PrefixGameTestTemplate(false)
public final class InfectionProbeTests {
    public static final ResourceKey<Level> BASIN = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(InfectionProbe.ID, "basin"));
    private static final BlockPos ORIGIN = new BlockPos(8, 65, 8);

    /** Native timers and conversion logic; the fixture only seeds a single persistent mound. */
    @GameTest(template = "empty", templateNamespace = InfectionProbe.ID, timeoutTicks = 1500)
    public static void nativeMoundChangesTerrainInCustomBiome(GameTestHelper helper) {
        ServerLevel basin = helper.getLevel().getServer().getLevel(BASIN);
        helper.assertTrue(basin != null, "The separate infection basin must be loaded");
        helper.assertTrue(basin.getBiome(ORIGIN).is(ResourceLocation.fromNamespaceAndPath(InfectionProbe.ID, "basin")),
                "The probe must use its own biome, not the shared End biome");
        helper.assertTrue(SConfig.SERVER.mound_cooldown.get() == 30, "Use the native 30-second spreading timer");
        helper.assertTrue(SConfig.SERVER.mound_age.get() == 900, "Do not accelerate native evolution for baseline acceptance");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) basin.setChunkForced(x, z, true);
        int before = countSporeBlocks(basin);
        helper.assertTrue(before == 0, "A fresh fixture must contain no infected terrain");
        int myceliumBefore = countMyceliumBlocks(basin);
        helper.assertTrue(myceliumBefore == 0, "The fresh basin must not already contain vanilla mycelium");
        var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("spore", "mound"));
        var created = type.create(basin);
        helper.assertTrue(created instanceof Mound, "Pinned artifact must provide the original mound entity");
        Mound mound = (Mound) created;
        mound.moveTo(ORIGIN.getX() + 0.5, ORIGIN.getY(), ORIGIN.getZ() + 0.5, 0, 0);
        mound.setPersistenceRequired();
        helper.assertTrue(basin.addFreshEntity(mound), "Native mound must join the actual server level");
        long started = basin.getGameTime();
        int[] previousCounter = {mound.getCounter()};
        boolean[] nativeCycleCompleted = {false};
        var samples = new ArrayList<Map<String, Object>>();
        helper.onEachTick(() -> {
            if (mound.getCounter() < previousCounter[0]) {
                nativeCycleCompleted[0] = true;
                samples.add(Map.of("elapsed_ticks", basin.getGameTime() - started,
                        "spore_blocks", countSporeBlocks(basin), "vanilla_mycelium_blocks", countMyceliumBlocks(basin),
                        "mound_age", mound.getAge()));
                writeResult(Map.of("passed", false, "status", "observing_native_growth", "growth_samples", samples));
            }
            previousCounter[0] = mound.getCounter();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(mound.isAlive(), "Native infection source died before a spreading cycle");
            helper.assertTrue(nativeCycleCompleted[0], "Waiting for the original mound's spreading timer");
            int after = countSporeBlocks(basin);
            int myceliumAfter = countMyceliumBlocks(basin);
            // The original default conversion maps grass to MINECRAFT mycelium. Namespace-only
            // counting misses the principal terrain change on this grass-covered fixture.
            helper.assertTrue(after > 0 && after + myceliumAfter >= 8,
                    "Native infection must actually convert terrain; spore=" + after + ", mycelium=" + myceliumAfter);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("passed", true);
            result.put("dimension", BASIN.location().toString());
            result.put("biome", InfectionProbe.ID + ":basin");
            result.put("spore_version", ModList.get().getModContainerById("spore").orElseThrow().getModInfo().getVersion().toString());
            result.put("before_spore_blocks", before);
            result.put("after_spore_blocks", after);
            result.put("before_vanilla_mycelium_blocks", myceliumBefore);
            result.put("after_vanilla_mycelium_blocks", myceliumAfter);
            result.put("growth_samples", samples);
            result.put("elapsed_server_ticks", basin.getGameTime() - started);
            result.put("native_timer_reset_observed", true);
            result.put("mound_age", mound.getAge());
            result.put("containment_adapter_loaded", false);
            result.put("evolution_accepted", false);
            writeResult(result);
            mound.discard();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) basin.setChunkForced(x, z, false);
        });
    }

    public static int countSporeBlocks(ServerLevel level) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(ORIGIN.offset(-12, -5, -12), ORIGIN.offset(12, 9, 12))) {
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getNamespace().equals("spore")) count++;
        }
        return count;
    }

    private static int countMyceliumBlocks(ServerLevel level) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(ORIGIN.offset(-12, -5, -12), ORIGIN.offset(12, 9, 12))) {
            if (level.getBlockState(pos).is(Blocks.MYCELIUM)) count++;
        }
        return count;
    }

    private static void writeResult(Map<String, Object> result) {
        try {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(result);
            Files.writeString(Path.of("infection-baseline.json"), json + "\n");
            System.out.println("INFECTION_BASELINE " + json.replace('\n', ' '));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to preserve infection evidence", failure);
        }
    }
}
