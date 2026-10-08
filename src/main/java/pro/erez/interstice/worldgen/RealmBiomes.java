package pro.erez.interstice.worldgen;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import pro.erez.interstice.Interstice;

/** Seeded climate selection, independent of the island density and either sea. */
public final class RealmBiomes {
    public static final ResourceKey<Biome> ASH_ISLANDS = key("ash_islands");
    public static final ResourceKey<Biome> STONE_VAULTS = key("stone_vaults");
    private RealmBiomes() {}
    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Interstice.ID, name));
    }
    public static BiomeSource create(HolderGetter<Biome> biomes) {
        var all = Climate.Parameter.span(-2, 2);
        return MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(List.of(
                Pair.of(Climate.parameters(all, all, Climate.Parameter.span(-2, .08F), all, all, all, 0), biomes.getOrThrow(ASH_ISLANDS)),
                Pair.of(Climate.parameters(all, all, Climate.Parameter.span(.08F, 2), all, all, all, 0), biomes.getOrThrow(STONE_VAULTS)))));
    }
    /** Upgrade only our former placeholder. Existing chunk biome palettes and blocks are never rewritten. */
    public static BiomeSource upgradeLegacy(BiomeSource source, HolderGetter<Biome> biomes) {
        if (source instanceof FixedBiomeSource && source.possibleBiomes().size() == 1
                && source.possibleBiomes().iterator().next().is(Biomes.THE_END)) return create(biomes);
        return source;
    }
    public static boolean isVault(ChunkAccess chunk, BlockPos pos) {
        return chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ())).is(STONE_VAULTS);
    }
}
