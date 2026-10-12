"""Reproduce the V6 CC0 ruins from included, unmodified Ethereal Structures B1 NBT.

Offline conversion only: no world access, mod installation, textures or network.
Architecture keeps its coordinates; obsolete palette/marker data is explicitly mapped.
"""
import gzip
import hashlib
import io
import json
import struct
from pathlib import Path

from build_realm_structures_v5 import Plan

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "art/sources/cc0/ethereal_structures"
SELECTION = {
    "arch": ["simple/ruined_archway0", "simple/ruined_archway1", "simple/ruined_staircase0"],
    "monolith": ["monoliths/tiny_pillar", "monoliths/small_pillar", "monoliths/medium_pillar", "monoliths/large_pillar"],
    "shrine": ["shrines/empty", "shrines/flower", "shrines/gold", "shrines/lodestone", "shrines/burner"],
    "tower": ["towers/ruined_tower" + str(i) for i in range(6)],
    "relic": ["simple/ancient_golem"],
}
MAPPING = {
    "minecraft:stone_bricks": "interstice:vaultstone_bricks",
    "minecraft:cracked_stone_bricks": "interstice:weathered_vaultstone",
    "minecraft:mossy_stone_bricks": "interstice:vaultstone",
    "minecraft:mossy_cobblestone": "interstice:weathered_vaultstone",
    "minecraft:stone_brick_stairs": "interstice:vaultstone_bricks_stairs",
    "minecraft:mossy_stone_brick_stairs": "interstice:vaultstone_stairs",
    "minecraft:stone_brick_slab": "interstice:vaultstone_bricks_slab",
    "minecraft:mossy_stone_brick_slab": "interstice:vaultstone_slab",
    "minecraft:stone_brick_wall": "interstice:vaultstone_bricks_wall",
    "minecraft:mossy_stone_brick_wall": "interstice:vaultstone_wall",
    "chipped:spiraled_mossy_stone_bricks": "interstice:polished_vaultstone",
    "chipped:crying_mossy_stone_bricks": "interstice:vaultstone_bricks",
    "decorative_blocks:stone_pillar": "interstice:polished_vaultstone",
    "decorative_blocks:brazier": "minecraft:campfire",
    "create:flywheel": "interstice:polished_vaultstone",
    "create:lit_blaze_burner": "interstice:phosphorite",
    "createdeco:pearl_bricks": "interstice:polished_vaultstone",
    "minecraft:gold_block": "interstice:phosphorite",
    "minecraft:lodestone": "interstice:polished_vaultstone",
    "minecraft:dirt": "interstice:root_loam",
    "minecraft:coarse_dirt": "interstice:root_loam",
    "minecraft:rooted_dirt": "interstice:root_loam",
    "minecraft:dirt_path": "interstice:root_loam",
    "minecraft:grass_block": "interstice:abyssal_turf",
}


class NbtReader:
    def __init__(self, data):
        self.stream = io.BytesIO(gzip.decompress(data))

    def number(self, fmt):
        return struct.unpack(">" + fmt, self.stream.read(struct.calcsize(fmt)))[0]

    def string(self):
        return self.stream.read(self.number("H")).decode("utf-8")

    def value(self, kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 7:
            return self.stream.read(self.number("i"))
        if kind == 8:
            return self.string()
        if kind == 9:
            element, count = self.number("B"), self.number("i")
            return [self.value(element) for _ in range(count)]
        if kind == 10:
            result = {}
            while True:
                element = self.number("B")
                if element == 0:
                    return result
                name = self.string()
                result[name] = self.value(element)
        if kind in (11, 12):
            return [self.number("i" if kind == 11 else "q") for _ in range(self.number("i"))]
        raise ValueError("Unknown NBT kind " + str(kind))

    def root(self):
        if self.number("B") != 10:
            raise ValueError("Template must have a compound root")
        self.string()
        return self.value(10)


def state(source, marker=None):
    name, properties = source["Name"], dict(source.get("Properties", {}))
    if name == "minecraft:jigsaw":
        final = (marker or {}).get("final_state", "minecraft:air")
        if "[" in final:
            raise ValueError("Unexpected complex marker state " + final)
        return state({"Name": final})
    if name in ("minecraft:air", "minecraft:cave_air", "minecraft:structure_void"):
        return None
    if name.startswith("diagonalwalls:minecraft/"):
        name = "minecraft:" + name.split("/", 1)[1]
        properties = {k: ("low" if properties.get(k) == "true" else "none") for k in ("north", "south", "east", "west")}
        properties.update(up="true", waterlogged="false")
    if name in ("minecraft:chest", "minecraft:trapped_chest"):
        return "minecraft:barrel", {"facing": properties.get("facing", "north"), "open": "false"}
    if name == "decorative_blocks:brazier":
        properties = {"facing": "north", "lit": "false", "signal_fire": "false", "waterlogged": "false"}
    elif name in ("decorative_blocks:stone_pillar", "create:flywheel", "create:lit_blaze_burner", "minecraft:grass_block", "minecraft:dirt_path"):
        properties = {}
    converted = MAPPING.get(name, name)
    if converted.split(":")[0] not in ("minecraft", "interstice"):
        raise ValueError("Unmapped external block " + name)
    return converted, properties


def convert(family, relative, expected_hash):
    source = SOURCE / (relative + ".nbt")
    raw = source.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_hash:
        raise ValueError("Included source SHA-256 mismatch: " + relative)
    data = NbtReader(raw).root()
    plan = Plan("ethereal/" + family, Path(relative).name, data["size"])
    loot_family = "observation_post" if family == "tower" else "laboratory"
    containers = 0
    for block in data["blocks"]:
        converted = state(data["palette"][block["state"]], block.get("nbt"))
        if converted is None:
            continue
        name, properties = converted
        loot = loot_family if name == "minecraft:barrel" else None
        containers += bool(loot)
        plan.put(*block["pos"], name, properties or None, loot)
    # Monoliths/statue originally lack loot. One exposed cache, without moving source cells.
    added_cache = None
    if containers == 0:
        width, _, depth = plan.size
        choices = [(x, 0, z) for x in range(width) for z in range(depth)
                   if (x in (0, width - 1) or z in (0, depth - 1))
                   and all((x, y, z) not in plan.cells for y in range(3))]
        if not choices:
            raise ValueError("No accessible cache location in " + relative)
        added_cache = choices[0]
        plan.put(*added_cache, "minecraft:barrel", {"facing": "up", "open": "false"}, loot_family)
    if not (plan.size[0] <= 13 and plan.size[2] <= 13 and plan.size[1] <= 24):
        raise ValueError("Template exceeds the supported chunk footprint: " + relative)
    output = plan.save()
    output.update(source=relative + ".nbt", source_sha256=expected_hash,
                  loot_table="interstice:chests/v5/" + loot_family, added_cache=added_cache)
    return output


def main():
    manifest = json.loads((SOURCE / "sources.json").read_text())
    converted = [convert(family, source, manifest["templates"][source + ".nbt"])
                 for family, sources in SELECTION.items() for source in sources]
    output = {"schema": 1, "license": "CC0-1.0", "project_url": manifest["project_url"],
              "version_id": manifest["version_id"], "archive_sha512": manifest["archive_sha512"],
              "conversion": "Preserved occupied cell coordinates; explicit material replacements, removed empty cells/entities/ForgeCaps, resolved jigsaw final states, native barrels/loot; one added cache on templates without containers.",
              "block_mapping": MAPPING, "templates": converted}
    path = ROOT / "src/main/resources/assets/interstice/provenance/ethereal-structures.json"
    path.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
    print("Reproduced", len(converted), "CC0 Ethereal templates")


if __name__ == "__main__":
    main()
