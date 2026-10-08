# Ready-made source patterns for the Pale Gardens

The original PNG patterns are included unchanged. Only a one-to-one colour palette substitution is applied by `tools/GenerateGardenPalettes.java`; dimensions, coordinates and alpha are preserved.

M15 additionally uses the unchanged `bud.png` from Plant Tileset for the ripe/unripe food pod and the existing sapling sprite for a crown sapling. Exact source hashes are in `fruit-sources.json`; the effect icon copies the generated food palette without drawing additional pixels.

- [16×16 Block Texture Set](https://opengameart.org/content/16x16-block-texture-set), ARoachIFoundOnMyPillow, CC0-1.0. Archive and exact source SHA are recorded in `sources.json`.
- [Plant Tileset](https://opengameart.org/content/plant-tileset), same author, CC0-1.0. The unmodified 16×32 vine modules and archive SHA are recorded in `vine-sources.json`. Two modules are used; `stem2.png` is an unused future candidate.

The CC0 legal text is included at `../block-texture-set/CC0-1.0.html`; official text: https://creativecommons.org/publicdomain/zero/1.0/legalcode.en . Received 2026-10-08. Resulting texture paths and SHA are listed in `src/main/resources/assets/interstice/provenance/pale-gardens.json`.
