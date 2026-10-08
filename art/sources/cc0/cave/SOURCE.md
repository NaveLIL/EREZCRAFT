# Ready cave imagery

Retrieved on 2026-10-08. The unmodified PNG files in this folder are by
ARoachIFoundOnMyPillow and licensed CC0 1.0 by the original source pages:

- `flower3.png` and `root1.png`: [Plant Tileset](https://opengameart.org/content/plant-tileset).
- `sandstone.png`: [16×16 Block Texture Set](https://opengameart.org/content/16x16-block-texture-set).

Archive SHA-256, extracted-file SHA-256 and source URLs are recorded in `sources.json`.
The included `CC0-1.0.html` is the legal text. These three ready-made patterns have
not previously been used in our released tree/terrain palettes.

`tools/generate_cave_resources.py` performs one-to-one colour substitution only.
It verifies hashes, alpha, resolution and the original pixel colour classes.
Hanging flower orientation is a model rotation; the image is never flipped.
The spire geometry is a tapered cuboid mesh using the native directional/thickness
state convention. It does not use the Mojang pointed-dripstone image.

Custom toxic clingweed art is a separately generated project asset and is not
derived from these third-party patterns.
