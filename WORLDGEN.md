# Island world generation: sources and implementation

Target: Minecraft Java 1.21.1, NeoForge 21.1.252. The playable dimension is `interstice:islands`.

## Examined primary sources

- Minecraft 1.21.1's shipped `data/minecraft/worldgen/noise_settings/floating_islands.json`, `end/base_3d_noise.json`, `NoiseRouterData`, `NoiseBasedChunkGenerator`, `NoiseChunk` and `RandomState`. These are available in the Gradle Minecraft artifacts. Terrain is sampled in absolute coordinates from the world's seed; interpolated noise cells are shared across chunk boundaries.
- The Aether 1.21.1, inspected at revision `e07d30e16fbd0f09cc067b39594035e395dcf996`: [AetherNoiseBuilders](https://github.com/The-Aether-Team/The-Aether/blob/e07d30e16fbd0f09cc067b39594035e395dcf996/src/main/java/com/aetherteam/aether/data/resources/builders/AetherNoiseBuilders.java), [base noise](https://github.com/The-Aether-Team/The-Aether/blob/e07d30e16fbd0f09cc067b39594035e395dcf996/src/generated/resources/data/aether/worldgen/density_function/base_3d_noise_aether.json). Its floating terrain uses Minecraft's blended noise, density offsets and vertical slides. The author identifies the slide operation as Minecraft's existing `NoiseRouterData.slide`.
- [Youmiel/FloatingIslands-Datapack](https://github.com/Youmiel/FloatingIslands-Datapack), inspected at revision `4f1344e2d78df0d3807b6cce53a51d25bee35c82`, shows another data-driven approach to island-shaped Minecraft terrain.
- [FastNoiseLite](https://github.com/Auburn/FastNoiseLite/wiki/Documentation) was examined as an alternative noise library. The selected implementation uses Minecraft's existing sampler and chunk pipeline, so the release needs no additional noise runtime.

These are algorithm references. The mod uses fresh integration code and a project-specific density graph. No Aether blocks, assets, Java implementation or overworld replacement datapack are bundled.

## Density function

Let `N(x,y,z)` be Minecraft's seeded `old_blended_noise` (its established blend of octave noises). Define:

```
b(y) = clamp((y - 40) / 8, 0, 1)
t(y) = clamp((78 - y) / 18, 0, 1)
A = lerp(t(y), -0.20, N(x,y,z) - 0.13)
B = lerp(b(y), -0.10, A) - 0.05
D = squeeze(interpolated(blend_density(B)))
```

Positive density makes stone, negative density makes air. This is the vertical-slide construction used by Minecraft and demonstrated in the Aether source, with the height bands fitted into the space between the toxic seas. The graph is saved in `src/main/resources/data/interstice/worldgen/noise_settings/islands.json`.

Noise parameters: `xz_scale=0.45`, `y_scale=1.0`, `xz_factor=80`, `y_factor=160`, `smear_scale_multiplier=4`. They control shape and scale; they are configuration choices, not claims of a new noise algorithm. Current sampled seed `20261006`: 56 of 169 sampled columns contain land, 113 contain genuine gaps; a second seed changed 686 sampled block positions. This is a small acceptance sample, not a world-wide statistical estimate.

## Coupling to liquids

The generator extends Minecraft's `NoiseBasedChunkGenerator`, preserving seeded interpolation, biome filling and chunk scheduling. Its stages are:

1. Native density generation.
2. Clearance enforcement: terrain may occupy Y=41..77; its upper voxel face must be at least 6 blocks below the minimum of the real upper sea's four corner heights.
3. Native surface rules put moss on exposed land and dirt beneath it.
4. `fillSeas` adds heavy toxin at Y=1..34, stable shaped light toxin from `floor(SeaSurface.cellMinimum(x,z))` through Y=126, and bedrock bounds at Y=0 and 127.
5. Heightmaps are recomputed to describe the final chunk.

The lower sea's worst-case voxel top is Y=35, so land starts at Y=41. The upper sea is bounded below by Y=84, so land ends no higher than Y=77 (upper face Y=78). The geometry has at least 6 blocks of clearance on both sides. No rendering-only displacement is used for the hazard volume.

Fluids are added after surface generation so the upper sea does not hide islands from the surface-rule scan. Aquifers and ore veins are disabled; the native noise stage's default fluid is air. Carvers, automatic lakes, native structures and biome decoration do not run in this first terrain version. All other dimensions keep their existing generators.

## Verification and limits

GameTests exercise actual native chunk generation, compare it with global noise columns, generate neighboring chunks in opposite orders, change the seed, check the fluid and clearance contracts, and round-trip the registered generator codec. The existing liquid/immersion/legacy-scene checks remain part of the suite.

`/interstice explore` searches density columns for solid terrain with headroom, generates the selected chunk and checks the actual landing blocks. `/interstice leave` restores the recorded dimension, coordinates and view direction. Survival entry, distant chunk streaming and return are exercised by `runIslandSmoke` in a disposable client profile.

The original terrain profile is retained. M13 adds seeded `ash_islands` and `stone_vaults` biomes in both height profiles. Vegetation, riftsilver ore, watchpost ruins and geological arches use the explicit Java surface pipeline; vanilla decoration/carvers/structures remain disabled. Poured fluids retain their existing gameplay physics; the generation contract concerns generated terrain and the two generated seas.

## M13 geological pipeline

Only `noise_router.continents` is extended with independent, Y-invariant `interstice:realm_geology` noise. Island density and both sea surfaces are unchanged. Vanilla multi-noise selects ash islands below continentalness 0.08 and stone vaults above it. `RealmBiomes.upgradeLegacy` replaces the former fixed End placeholder during generator decoding; custom biome sources are preserved. Existing chunk blocks and saved biome palettes are never rebuilt.

`buildSurface` runs surface rules → sea/material conversion → geological surface → ore → watchpost → stone vault → tree → sprout. The vault biome changes the upper six exposed rock layers, with connected pockets of existing turf and authored flora. Ore remains mineable and is never overwritten by surface conversion. Ruins accept both old and new natural rock and have placement priority over arches.

One deterministic arch candidate is attempted in roughly one quarter of chunks. Eligible stone-biome terrain can receive a 9×5 vault with three forms, heights 5–7 and two orientations. Every roof piece connects by block faces to ground supports. The full footprint needs real ground within two blocks of the center elevation, open air, and profile-specific clearance for every added block. Placement stays inside its chunk and validates everything before writing. No caves are carved, no fluids removed, and no neighboring chunks modified. This bounds generation and keeps chunk order independent.

M13 evidence and limitations: [docs/progress/M13.md](docs/progress/M13.md). Fresh GameTests inspect both actual named dimensions; noise occupancy is compared before decoration, while runtime sea/heightmap/clearance checks remain on FULL chunks.
