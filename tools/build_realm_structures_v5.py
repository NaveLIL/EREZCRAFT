"""Reproducible, original 1.21.1 NBT architecture for the four V5 structure families.

No copied structure schematics, new textures, third-party packages, or world edits.
Each template stays inside one generation chunk and stores an actual vanilla loot barrel.
"""
import gzip
import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "src/main/resources/data/interstice/structure"
LOOT = ROOT / "src/main/resources/data/interstice/loot_table/chests/v5"
DATA_VERSION = 3955


def string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload


def compound(values):
    return b"".join(values) + b"\0"


def ints(values):
    return bytes([3]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", value) for value in values)


def compounds(values):
    return bytes([10]) + struct.pack(">i", len(values)) + b"".join(values)


class Plan:
    def __init__(self, family, variant, size):
        self.family, self.variant, self.size = family, variant, size
        self.cells = {}

    def put(self, x, y, z, block, properties=None, loot=None):
        assert 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]
        nbt = None
        if loot:
            nbt = compound([tag(8, "id", string("minecraft:barrel")),
                            tag(8, "LootTable", string("interstice:chests/v5/" + loot))])
        self.cells[(x, y, z)] = ({"Name": block, **({"Properties": properties} if properties else {})}, nbt)

    def floor(self, material):
        width, _, depth = self.size
        for x in range(width):
            for z in range(depth):
                # Clipped corners soften the silhouette without substituting a landscape mask.
                if (x in (0, width - 1) and z in (0, depth - 1)):
                    continue
                self.put(x, 0, z, material)

    def barrel(self, x, y, z):
        self.put(x, y, z, "minecraft:barrel", {"facing": "north", "open": "false"}, self.family)

    def save(self):
        palette, palette_indices = [], {}
        blocks = []
        for position, (state, nbt) in sorted(self.cells.items()):
            key = json.dumps(state, sort_keys=True)
            if key not in palette_indices:
                palette_indices[key] = len(palette)
                values = [tag(8, "Name", string(state["Name"]))]
                if "Properties" in state:
                    values.append(tag(10, "Properties", compound([
                        tag(8, k, string(v)) for k, v in sorted(state["Properties"].items())])))
                palette.append(compound(values))
            values = [tag(9, "pos", ints(position)), tag(3, "state", struct.pack(">i", palette_indices[key]))]
            if nbt:
                values.append(tag(10, "nbt", nbt))
            blocks.append(compound(values))
        body = compound([tag(3, "DataVersion", struct.pack(">i", DATA_VERSION)),
                         tag(9, "size", ints(self.size)), tag(9, "palette", compounds(palette)),
                         tag(9, "blocks", compounds(blocks)), tag(9, "entities", compounds([]))])
        path = DESTINATION / self.family / (self.variant + ".nbt")
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("wb") as file:
            with gzip.GzipFile(filename="", mode="wb", fileobj=file, mtime=0) as compressed:
                compressed.write(tag(10, "", body))
        return {"id": f"interstice:{self.family}/{self.variant}", "size": self.size,
                "blocks": len(blocks), "loot_barrels": sum(bool(nbt) for _, nbt in self.cells.values()),
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def refuge(variant):
    plan = Plan("refuge", variant, [9, 6, 9])
    plan.floor("interstice:weathered_vaultstone")
    for x in range(1, 8):
        for z in range(1, 8):
            if x not in (1, 7) and z not in (1, 7):
                continue
            for y in range(1, 4):
                # A two-block doorway and daylight slits remain walkable in both ruins.
                if z == 1 and x in (4, 5) and y <= 2:
                    continue
                if z == 7 and x in (3, 5) and y == 2:
                    continue
                if variant == "collapsed" and x == 7 and z >= 4 and y >= 2:
                    continue
                material = "interstice:gloomcrown_planks" if y == 3 else "interstice:weathered_vaultstone"
                plan.put(x, y, z, material)
    for x in range(1, 8):
        for z in range(1, 8):
            if variant == "collapsed" and x >= 5 and z >= 4:
                continue
            plan.put(x, 4, z, "interstice:gloomcrown_planks")
    plan.put(3, 1, 5, "interstice:polished_vaultstone_slab", {"type": "top", "waterlogged": "false"})
    plan.put(4, 1, 5, "interstice:polished_vaultstone_slab", {"type": "top", "waterlogged": "false"})
    plan.put(2, 3, 3, "interstice:phosphorite")
    plan.barrel(6, 1, 5)
    if variant == "collapsed":
        plan.put(7, 1, 7, "interstice:weathered_vaultstone")
        plan.put(8, 1, 6, "interstice:weathered_vaultstone")
    return plan


def cargo(variant):
    plan = Plan("cargo_station", variant, [13, 7, 11])
    plan.floor("interstice:vaultstone_bricks")
    for x, z in ((2, 2), (10, 2), (2, 8), (10, 8)):
        for y in range(1, 5):
            if variant == "broken_gantry" and x == 10 and z == 8 and y >= 3:
                continue
            plan.put(x, y, z, "interstice:stripped_paleheart_log", {"axis": "y"})
    for x in range(2, 11):
        plan.put(x, 5, 2, "interstice:stripped_paleheart_log", {"axis": "x"})
        if variant != "broken_gantry" or x <= 7:
            plan.put(x, 5, 8, "interstice:stripped_paleheart_log", {"axis": "x"})
    for z in range(2, 9):
        plan.put(2, 5, z, "interstice:stripped_paleheart_log", {"axis": "z"})
        if variant != "broken_gantry" or z <= 5:
            plan.put(10, 5, z, "interstice:stripped_paleheart_log", {"axis": "z"})
    for x in range(2, 7):
        for z in range(3, 8):
            plan.put(x, 5, z, "interstice:paleheart_slab", {"type": "top", "waterlogged": "false"})
    for x in (3, 4, 8, 9):
        for z in (6, 7):
            plan.put(x, 1, z, "interstice:paleheart_planks")
            if (x + z) % 2 == 0:
                plan.put(x, 2, z, "interstice:paleheart_planks")
    plan.barrel(5, 1, 7)
    plan.barrel(8, 1, 3)
    if variant == "broken_gantry":
        for z in range(5, 9):
            plan.put(11, 1, z, "interstice:stripped_paleheart_log", {"axis": "z"})
    plan.put(2, 4, 4, "interstice:phosphorite")
    return plan


def laboratory(variant):
    plan = Plan("laboratory", variant, [11, 7, 11])
    plan.floor("interstice:polished_vaultstone")
    for x in range(1, 10):
        for z in range(1, 10):
            if x not in (1, 9) and z not in (1, 9):
                continue
            for y in range(1, 5):
                if z == 1 and x in (4, 5, 6) and y <= 3:
                    continue
                if variant == "breached" and z == 9 and x >= 5 and y >= 2:
                    continue
                if x in (1, 9) and z in (4, 5, 6) and y in (2, 3):
                    plan.put(x, y, z, "minecraft:glass")
                else:
                    plan.put(x, y, z, "interstice:vaultstone_bricks")
    for x in range(2, 9):
        for z in range(2, 9):
            if (x + 2 * z) % 5 == 0 or (variant == "breached" and x >= 5 and z >= 6):
                continue
            plan.put(x, 5, z, "interstice:vaultstone_bricks_slab", {"type": "bottom", "waterlogged": "false"})
    for x in (2, 3, 7, 8):
        for z in (5, 6):
            plan.put(x, 1, z, "interstice:palestone_bricks")
            plan.put(x, 2, z, "interstice:polished_vaultstone_slab", {"type": "bottom", "waterlogged": "false"})
    # Phosphorite samples, rather than free operating retorts that bypass their craft.
    plan.put(3, 3, 5, "interstice:phosphorite")
    plan.put(7, 3, 6, "interstice:phosphorite")
    plan.barrel(8, 1, 3)
    if variant == "breached":
        plan.put(6, 1, 9, "interstice:weathered_vaultstone")
        plan.put(7, 1, 10, "interstice:weathered_vaultstone")
    return plan


def observation(variant):
    plan = Plan("observation_post", variant, [11, 10, 11])
    plan.floor("interstice:weathered_vaultstone")
    for x, z in ((3, 3), (7, 3), (3, 7), (7, 7)):
        for y in range(1, 5):
            plan.put(x, y, z, "interstice:stripped_gloomcrown_log", {"axis": "y"})
    for x in range(3, 8):
        for z in range(3, 8):
            if variant == "tilted_roof" and x == 7 and z >= 6:
                continue
            plan.put(x, 4, z, "interstice:gloomcrown_planks")
            if x in (3, 7) or z == 7:
                if variant == "tilted_roof" and x == 7 and z >= 5:
                    continue
                plan.put(x, 5, z, "interstice:vaultstone_bricks_slab", {"type": "bottom", "waterlogged": "false"})
    # A supported ladder exits onto an open part of the platform, away from its roof posts.
    for y in range(1, 5):
        plan.put(4, y, 3, "interstice:weathered_vaultstone")
        plan.put(4, y, 2, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
    for x, z in ((3, 3), (7, 3)):
        for y in range(5, 8):
            plan.put(x, y, z, "interstice:stripped_gloomcrown_log", {"axis": "y"})
    for x in range(3, 8):
        for z in range(3, 8):
            if variant == "tilted_roof" and z >= 5 and x >= 5:
                continue
            plan.put(x, 8, z, "interstice:gloomcrown_planks")
    plan.barrel(5, 5, 5)
    plan.put(5, 7, 3, "interstice:phosphorite")
    return plan


def entry(item, low, high, weight=1):
    return {"type": "minecraft:item", "name": "interstice:" + item, "weight": weight,
            "functions": [{"function": "minecraft:set_count", "count": {
                "type": "minecraft:uniform", "min": low, "max": high}}]}


def pool(entries, rolls=1, chance=None):
    result = {"rolls": rolls, "entries": entries}
    if chance is not None:
        result["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return result


def write_loot():
    definitions = {
        "refuge": [pool([entry("umbral_coal", 1, 3)]), pool([entry("world_stick", 2, 5)]),
                   pool([entry("purified_bread", 1, 2)], chance=.65), pool([entry("plant_fiber", 1, 3)], chance=.45)],
        "cargo_station": [pool([entry("raw_riftsilver", 2, 5), entry("umbral_coal", 2, 4), entry("world_stick", 3, 6)], rolls=2),
                          pool([entry("vitriolite_shard", 1, 2)], chance=.3)],
        "laboratory": [pool([entry("phosphorite_crystal", 2, 4)]), pool([entry("vitriolite_shard", 1, 3)]),
                       pool([entry("ash_grain", 1, 3), entry("crimson_root", 1, 3)]),
                       pool([entry("riftsilver_mesh", 1, 1)], chance=.15)],
        "observation_post": [pool([entry("pressure_coupler", 1, 1)]), pool([entry("umbral_coal", 1, 2)]),
                             pool([entry("phosphorite_crystal", 1, 2)], chance=.5)],
    }
    LOOT.mkdir(parents=True, exist_ok=True)
    for name, pools in definitions.items():
        (LOOT / (name + ".json")).write_text(json.dumps({"type": "minecraft:chest", "pools": pools}, indent=2) + "\n", encoding="utf-8")


def main():
    authored = [refuge("roofed"), refuge("collapsed"), cargo("covered_loading"), cargo("broken_gantry"),
                laboratory("sample_hall"), laboratory("breached"), observation("sheltered"), observation("tilted_roof")]
    manifest = {"format": 1, "author": "EREZCRAFT project", "source": "tools/build_realm_structures_v5.py",
                "minecraft": "1.21.1", "original_geometry": True, "templates": [plan.save() for plan in authored]}
    target = ROOT / "docs/structures/V5_TEMPLATES.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    write_loot()
    print(f"Authored {len(authored)} original templates and four loot tables")


if __name__ == "__main__":
    main()
