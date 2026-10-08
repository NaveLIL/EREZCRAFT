# Crown tree ready artwork

Received 2026-10-08. Author: ARoachIFoundOnMyPillow. CC0-1.0.

- Wood and sapling: [16x16 Block Texture Set](https://opengameart.org/content/16x16-block-texture-set), original `maple_log_side`, `maple_log_top`, `maple_planks`, `sapling_maple`.
- Leaves: [More Blocks](https://opengameart.org/content/more-blocks), `new blocks/fully original/oak_leaves_2.png` and `oak_leaves_3.png`. These are the author's original folder, not the separate JoeEnderman derivative folder. Archive credits retained verbatim in `credits.txt`.
- Archive and individual file hashes: `sources.json`. The original PNGs are preserved here. The CC0 legal text shared by this library is in `../block-texture-set/CC0-1.0.html`.

`tools/GenerateCrownPalettes.java` changes only palette entries. It verifies source hashes, image dimensions, unchanged per-pixel alpha and a bijection between original and replacement colors. No drawing, alpha-mask edits, cropping, bitmap rotation or mirroring. Two ready leaf patterns retain their original holes and receive weighted model selection.
