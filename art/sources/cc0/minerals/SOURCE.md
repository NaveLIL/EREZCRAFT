# Ready mineral, ground and torch sources

Obtained and checked on 2026-10-08. All selected images are16×16 original author-provided PNGs with declared CC0-1.0 rights. The numbered files and SHA-256 values are recorded in `sources.json`.

- `coal_lump.png` and `stick.png`: JoeEnderman's [Assorted Minecraft style textures](https://opengameart.org/content/assorted-minecraft-style-textures), `assorted_textures_1.zip`. Original pack filenames `default_coal_lump.png` and `default_stick.png` are retained in the registry as source members.
- `torch.png`: ARoachIFoundOnMyPillow's [More Blocks](https://opengameart.org/content/more-blocks), explicitly `new blocks/fully original/torch2.png`. `more-blocks-credits.txt` records the original folder/author distinction. No JoeEnderman-derived plank or andesite file from that archive is selected here.
- `flower2.png`: ARoachIFoundOnMyPillow's [Plant Tileset](https://opengameart.org/content/plant-tileset). This ready flower was not previously used for the crown fruit or cave flowers.
- `dirt.png` and `slate.png`: previously unused ready patterns from [16x16 Block Texture Set](https://opengameart.org/content/16x16-block-texture-set). They supply root loam and the rift shale stone band; existing M13 basalt/schist patterns are retained unchanged.
- `tin_nugget.png`, `lexxite_shard.png` and `ausene_shard.png`: [Auseawesome/Minecraft-Textures](https://github.com/Auseawesome/Minecraft-Textures), commit `149e1b2a852de778841d85b0acc0fef9562538ba`. Original PNGs, pinned raw URLs and full CC0 text are saved here.

`tools/GenerateMineralPalettes.java` changes only palette classes, preserving every pixel position, alpha value and resolution. The warm torch is copied unchanged. No Forager files, vanilla Minecraft bitmaps, painted ore masks or resized/cropped sprites are included.

Ore JSON models use the exact existing host cube and its weighted variants/UVs. Whole ready mineral sprites appear as small surface inlays; geometry provides their scale and placement. Torch model geometry and blockstate orientation are configured separately from the unchanged source patterns.
