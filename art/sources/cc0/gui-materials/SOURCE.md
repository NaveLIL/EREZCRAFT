# Ready GUI and backpack model surfaces

- `steel_block.png`: whole `default_steel_block.png` by JoeEnderman from [Assorted Minecraft Style Textures](https://opengameart.org/content/assorted-minecraft-style-textures), declared CC0-1.0. It supplies the cool-gray retort GUI metal.
- `linen_pattern.png`: whole `Tiles (Grayscale)/tile_0013.png` from Kenney [Pattern Pack Pixel1.1](https://kenney.nl/assets/pattern-pack-pixel), CC0-1.0 as declared in both the primary page and the included `License.txt`. This is a ready geometric diagonal pattern used as a stylized cloth-like surface; it is not presented as a photograph of fabric.

Both original images are opaque16×16 separate archive members. Neither is cropped from the accompanying tilesheet. The original archives, primary page snapshots, original Kenney license and CC0 legal text are retained here. `sources.json` records authors, precise archive members, URLs, retrieval date and all original byte hashes.

`tools/GenerateGuiMaterialPalettes.java` changes only the whole images' color palettes. Source coordinates, resolution, transparency and pixel-equivalence classes remain unchanged. It draws no new pixels, resizes no bitmap, and adds no noise, lines or layers. Retort metal uses a cool muted gray palette; backpack cloth-like material uses two distinct dark olive/linen gray colors. The resulting textures are available for both GUI tiling and the native backpack model UVs.

The originals were compared to1568 complete16×16 block/item textures in the installed Minecraft1.21.1 resources, including rotations/mirrors and color-class/alpha structure. The bounded technical findings are retained in `sources.json`; they are not a blanket rights certification.
