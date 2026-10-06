# Infection confined to the island dimension

## Current decision, 2026-10-06

The user permits replacing Overgrown Vegetation with a comparable infection mod. The sole gameplay boundary is the dimension: infection may grow, mutate, corrupt terrain and infect eligible creatures freely inside `interstice:islands`. Do not add protected settlements, creature allowlists, progression caps or infection radius limits as containment policy. Native mod behaviour still determines which hosts it can infect. The existing toxic seas and dimension boundary remain necessary world infrastructure, not safe zones.

**Fungal Infection: Spore 2.2.0j is the preferred candidate for a containment prototype**, not an accepted isolated integration. Its inspected spreading entry points receive the affected `Level`; unlike the examined Overgrown Vegetation phase handlers, those paths do not need to be moved from hard-coded Overworld ticks. This is a scoped static observation, not a complete audit or proof of lower total integration effort.

No examined candidate has a verified ready-made configuration that contains every propagation route to one custom dimension. The currently delivered Interstice 0.2.0 still contains no external infection mod or containment adapter. Downloaded research artifacts have not been executed or added to any game's mod path.

| Candidate | Version compatibility | Containment finding | Decision |
| --- | --- | --- | --- |
| Fungal Infection: Spore 2.2.0j | Native Minecraft 1.21.1 / NeoForge 21.1.212+; our loader is 21.1.252 | Config fields named `dimension_parameters` and `dimension_blacklist` select biome IDs/tags. The inspected foliage spread and mycelium effect entry points have no dimension guard. | Preferred prototype; a runtime adapter is required. |
| Overgrown Vegetation 0.1.2 | Native 1.21.1 / NeoForge; requires GeckoLib and Player Animation Library | Several progression/event systems explicitly target the Overworld; conversion paths still need external guards. | Retain as a visual alternative; details below. |
| Nexus Infection 1.6.3 | Native 1.21.1 / NeoForge | Inspected block-spread procedure has no dimension guard; infection activation is stored through the Overworld's global map variables. | Viable fallback, not an easier confirmed configuration solution. |
| Sculk Horde 0.12.7 | Official published release is Forge 1.20.1 | Incompatible with our selected version/loader as published. | Excluded from the current shortlist. |
| The Hordes | Official page supports 1.21.1 / NeoForge | Zombie infection and invasion waves; no equivalent large organic terrain infection established in this research. Full confinement was not audited. | Does not match the desired environment transformation as closely. |

Primary pages: [Spore](https://modrinth.com/mod/fungal-infectionspore), [Spore wiki](https://www.fungalinfectionspore.wiki/home/help-center), [Nexus 1.6.3 NeoForge file](https://www.curseforge.com/minecraft/mc-mods/nexus-infection/files/8557659), [Sculk Horde](https://www.curseforge.com/minecraft/mc-mods/sculk-horde), [The Hordes](https://modrinth.com/mod/the-hordes).

### Spore binary evidence

Original file `research/spore/spore_1.21.1_2.2.0j_neo-original.jar`, Modrinth project `X661i40C`, version `DwE3w8IX`, published 2026-06-29. Download SHA-512 verified against the version API; SHA-256 recorded in `research/spore/inspection.json`. Selected class files were decompiled with Vineflower 1.10.1 for inspection only. These files are not our source and must not be packaged in the release.

- `decompiled/com/Harbinger/Spore/core/SConfig.java:2956-2966`: the two misleadingly named config fields live under `Spawns` and are described as biome controls.
- `extra-decompiled/com/Harbinger/Spore/ExtremelySusThings/BiomeModification.java`: `modify(Holder<Biome>, Phase, Builder)` resolves both lists as biome tags or biome IDs. It receives no dimension and cannot distinguish two dimensions using the same biome.
- `decompiled/com/Harbinger/Spore/Sentities/FoliageSpread.java:49`: `SpreadInfection(Level, double, BlockPos)` checks server side and the global foliage setting, but not the dimension. `SpreadFoliageAndConvert(...)` and direct placement/conversion helpers also need containment coverage.
- `decompiled/com/Harbinger/Spore/Effect/Mycelium.java:23`: `triggerEffects(LivingEntity, int)` acts on the carrier's current level without a dimension guard. A player leaving through a portal must not carry an active spreading infection into another dimension.
- `decompiled/com/Harbinger/Spore/Sevents/HandlerEvents.java:180`: death handling delegates to `Infection.onEntityDeath(...)`; this is another route requiring inspection and guarding, not just natural spawn filtering.

Our current fixed End biome is shared with the End. Do not whitelist `minecraft:the_end` as a substitute for a dimension whitelist. A dedicated island biome/tag can restrict natural initiation, but runtime containment is still required independently of biome selection.

The adapter must allow infection behaviour only when the affected level's key equals `interstice:islands`, covering spawn/load, transformations, block and biome changes, effects/death conversion, active items, projectiles and deferred work. Prevent infected non-player carriers from leaving through any dimension transition. Players may return normally; neutralize the mod's active infection state before it can act in the destination. Ordinary loot need not be prohibited, but active growth items must not initiate infection outside the island dimension. Audit all such routes against the pinned original version and fail visibly on an unsupported version rather than silently enabling an unguarded mod.

Acceptance must demonstrate both sides: real uncontrolled growth and native evolution inside the island dimension, and no infection changes outside it after natural initiation attempts, direct placement, carrier transfer, player death, save/reload and deferred work. No runtime acceptance has happened yet.

### Nexus binary evidence

Official CurseForge project 593612, file 8557659; original file and SHA-256 are recorded in `research/nexus/inspection.json`. Selected bytecode inspected without execution.

- `decompiled/net/mcreator/nexus_crusade/procedures/DeadNexusOnTickUpdateProcedure.java:22`: activation/nearby entities/immunity affect spread; no dimension check appears at the inspected entry point.
- `decompiled/net/mcreator/nexus_crusade/network/NexusCrusadeModVariables.java:102-108`: `MapVariables.get(...)` obtains activation state from `Level.OVERWORLD` storage. Storage location alone does not spread infection, but its scope is global and needs deliberate handling.

## Original Overgrown Vegetation assessment

Static inspection, 2026-10-06. Runtime isolation is **not implemented or verified**. The downloaded mod remains under `research/overgrown-vegetation/` and is not on the game's mod path.

## Examined artifact

Official file: [Overgrown Vegetation 0.1.2](https://www.curseforge.com/minecraft/mc-mods/overgrown-vegetation/files/9008205), file ID 9008205, Minecraft 1.21.1 / NeoForge. Artifact identity and method are recorded in `research/overgrown-vegetation/inspection.json`. Selected bytecode was inspected using Vineflower 1.10.1; the original JAR was not modified or executed.

Required libraries in its embedded metadata: GeckoLib 4.7+, Player Animation Library 1.1+. Our NeoForge 21.1.252 meets its declared loader requirement.

The public [author repository](https://github.com/yayuy-01/overgrown-vegetation) identifies its source as v0.1.0. Code is MIT; models, textures, animations and sounds are separately All Rights Reserved. The integration should use the author's original installed JAR as a dependency and reference its registered blocks. The adapter should be distributed separately from those assets.

## Confirmed in the 0.1.2 binary

- `phase/PhaseTicker.java:20`: progression ticks only for `Level.OVERWORLD`.
- `quake/EarthquakeTicker.java:18`: earthquakes tick only for `Level.OVERWORLD`.
- `corruption/LedgerSpread.java:40,53,69`: chunk-load, level-tick and sleep processing explicitly target the Overworld.
- `phase/PhaseData.java:307-309`: progression is stored in `level.getServer().overworld().getDataStorage()`.
- No dimension whitelist/blacklist setting was found in the inspected common config. This does not establish that every possible customisation mechanism has been exhausted.
- `CorruptionSpreader.tick(...)` and `corruptSingle(...)` accept a `ServerLevel`; their inspected entry points have no dimension guard. Limiting natural spawning alone cannot establish isolation.
- `BlightFernSelfSpread.findCorruptedGroundSpot(...)` uses `MOTION_BLOCKING_NO_LEAVES` and `OCEAN_FLOOR`. In our dimension the upper ocean and roof hide the actual island terrain from these heightmaps; island-aware placement is needed.
- `data/overgrown_vegetation/tags/block/corruption_immune.json` exists, so the project's liquids and other essential blocks can be explicitly protected from that conversion path.

## Proposed adapter boundary

Allow active infection only in `interstice:islands`. The mod still loads and registers its content normally for client and server; the adapter scopes gameplay behaviour.

1. Route progression, initial fissure events, region ledgers, raids and spore accumulation to the selected dimension; prevent those handlers from changing the Overworld, Nether and End.
2. Guard direct block conversion, biome conversion, plant growth, mob- and corpse-origin spread, projectile/death bursts and player infection hooks outside that dimension.
3. Define portal behaviour for infected creatures, players and active growth items. Prevention must cover deliberate placement outside the dimension, not just natural spawning.
4. Protect `interstice:light_toxin`, `interstice:light_sea`, `interstice:heavy_toxin`, the world bounds and portal mechanisms through the immunity tag plus checks for non-conversion growth/deformation paths.
5. Replace ground searches with island-band searches, and bound trees and other growth below the real upper sea. A dimension whitelist cannot fix terrain assumptions by itself.
6. Map infection biome/spawn categories to the project's future biomes. The current fixed End biome is only a terrain-development placeholder.

## Required acceptance tests

The same controlled infection seed must grow in the island dimension and leave the three vanilla dimensions unchanged. Repeat with natural initiation, direct placement, infected animals/corpses, portal transfer, save/reload and unloaded-region catch-up. Check that progression storage stays scoped, no events start in the Overworld, and growth does not replace or penetrate the protected seas. Then verify actual player journeys with the original mod and its required libraries in an isolated profile.

## Block palette direction

Use native materials for basic terrain and ruins; use the original Overgrown Vegetation content for the infection's organic identity. Its artifact contains overgrown stone/dirt/wood, shelf and hanging growths, glow vines, spore growths and a range of devices. Do not assume every such block is inert decoration.

Keep a small custom set for the dimension's unique identity: porous pale stone, mineral crust, dim light nodules, anchor material for the future tide, and a compact ruin-stone family. These are concepts, not implemented blocks. The first decoration pass should use a restricted palette and a few controlled infection origins rather than marking every island corrupted at generation.
