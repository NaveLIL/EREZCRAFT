"""Offline CC0 temple/camp assembly using original B1 marker positions and intact parts.

Two deterministic camp layouts per base. Missing archive entries are excluded;
overlapping optional pieces are skipped like jigsaw bounding-box collision checks.
No world, textures, mod binaries or network access.
"""
import hashlib
import json
from pathlib import Path
from build_realm_structures_v5 import Plan
from import_ethereal_structures import NbtReader, SOURCE, ROOT, state as ruin_state, MAPPING

EXTRA = {
    "chipped:hewn_spruce_planks": "interstice:gloomcrown_planks",
    "chipped:solid_spruce_trapdoor": "minecraft:spruce_trapdoor",
    "chipped:crooked_ropeplank_ladder": "minecraft:ladder",
    "chipped:stern_stripped_spruce_log": "interstice:stripped_gloomcrown_log",
    "chipped:spruce_crate": "minecraft:barrel",
    "decorative_blocks:spruce_palisade": "interstice:gloomcrown_log",
    "decorative_blocks:spruce_support": "minecraft:spruce_fence",
    "decorative_blocks:bar_panel": "minecraft:iron_bars",
    "handcrafted:spruce_pillar_trim": "interstice:gloomcrown_log",
    "handcrafted:spruce_dining_bench": "minecraft:spruce_stairs",
    "farmersdelight:straw": "minecraft:hay_block",
    "minecraft:water": "interstice:phosphorite",
}
DIRECTIONS = ["north", "east", "south", "west"]


def load(relative):
    p = SOURCE / "complexes" / (relative + ".nbt")
    raw = p.read_bytes()
    expected = json.loads((SOURCE / "sources.json").read_text())["templates"]["complexes/" + relative + ".nbt"]
    assert hashlib.sha256(raw).hexdigest() == expected, relative
    return NbtReader(raw).root()


def converted(palette, marker=None):
    name, props = palette["Name"], dict(palette.get("Properties", {}))
    if name == "minecraft:jigsaw":
        return converted({"Name": (marker or {}).get("final_state", "minecraft:air")})
    if name.startswith("diagonalfences:minecraft/"):
        name = "minecraft:" + name.split("/", 1)[1]
        props = {k: props.get(k, "false") for k in DIRECTIONS}
        props["waterlogged"] = "false"
    if name.startswith("chipped:") and ("bundled_" in name or "patched_" in name) and name.endswith("_wool"):
        name = "minecraft:" + name.split(":", 1)[1].split("_", 1)[1]
    if name in EXTRA:
        original = name
        name = EXTRA[name]
        if name == "minecraft:barrel":
            props = {"facing": "up", "open": "false"}
        elif name == "interstice:gloomcrown_log":
            props = {"axis": "y"}
        elif name in ("minecraft:spruce_fence", "minecraft:iron_bars"):
            props = {k: "false" for k in DIRECTIONS}
            facing = palette.get("Properties", {}).get("facing", "north")
            if name == "minecraft:iron_bars":
                for k in (["east", "west"] if facing in ("north", "south") else ["north", "south"]):
                    props[k] = "true"
            props["waterlogged"] = "false"
        elif original == "handcrafted:spruce_dining_bench":
            props = {"facing": props.get("facing", "north"), "half": "bottom", "shape": "straight", "waterlogged": "false"}
        elif name == "interstice:phosphorite":
            props = {}
    return ruin_state({"Name": name, **({"Properties": props} if props else {})})


def turn_position(p, rotation, width, depth):
    x, y, z = p
    return [(x, y, z), (depth - 1 - z, y, x), (width - 1 - x, y, depth - 1 - z), (z, y, width - 1 - x)][rotation]


def turn_properties(props, rotation):
    result = dict(props)
    if result.get("facing") in DIRECTIONS:
        result["facing"] = DIRECTIONS[(DIRECTIONS.index(result["facing"]) + rotation) % 4]
    for key in DIRECTIONS:
        if key in props:
            result[DIRECTIONS[(DIRECTIONS.index(key) + rotation) % 4]] = props[key]
    if rotation % 2 and result.get("axis") in ("x", "z"):
        result["axis"] = "z" if result["axis"] == "x" else "x"
    return result


def cells(data, offset=(0, 0, 0), rotation=0):
    width, _, depth = data["size"]
    result = {}
    for b in data["blocks"]:
        item = converted(data["palette"][b["state"]], b.get("nbt"))
        if item is None:
            continue
        p = turn_position(b["pos"], rotation, width, depth)
        position = tuple(p[i] + offset[i] for i in range(3))
        result[position] = (item[0], turn_properties(item[1], rotation))
    return result


def markers(data):
    return [b for b in data["blocks"] if data["palette"][b["state"]]["Name"] == "minecraft:jigsaw"]


def pool(relative):
    # Original pool candidates which actually exist in B1.
    if relative == "tent":
        return ["misc/tents/" + name for name in ["teepee_brown", "teepee_green", "teepee_red", "teepee_white", "teepee_lightgray", "tent_brown", "tent_cyan", "tent_green", "tent_red", "tent_white", "tent_lightgray"]]
    if relative == "tower":
        return ["outposts/outpost_lookout0", "outposts/outpost_lookout1"]
    return ["outposts/deco/outpost_deco_" + n for n in ["campfire", "well", "cage", "dummy", "allay", "pigpen"]]


def camp(size, variant):
    base_name = "outposts/outpost_base_" + size
    data = load(base_name); result = cells(data); boxes = []; parts = []
    all_markers = markers(data)
    # Source base has adjacent alternative sites; reject overlap instead of superimposing units.
    all_markers.sort(key=lambda b: (0 if "tower_pool" in b["nbt"]["pool"] else 1 if "tent_pool" in b["nbt"]["pool"] else 2, tuple(b["pos"])))
    for index, b in enumerate(all_markers):
        role = "tower" if "tower_pool" in b["nbt"]["pool"] else "tent" if "tent_pool" in b["nbt"]["pool"] else "deco"
        candidates = pool(role)
        for attempt in range(len(candidates)):
            relative = candidates[(index + variant * 5 + attempt) % len(candidates)]
            child = load(relative); connector = markers(child)[0]
            parent_top = data["palette"][b["state"]]["Properties"]["orientation"].split("_")[1]
            child_top = child["palette"][connector["state"]]["Properties"]["orientation"].split("_")[1]
            rotation = (DIRECTIONS.index(parent_top) - DIRECTIONS.index(child_top)) % 4
            pivot = turn_position(connector["pos"], rotation, child["size"][0], child["size"][2])
            offset = (b["pos"][0] - pivot[0], 1 - pivot[1], b["pos"][2] - pivot[2])
            w, dep = (child["size"][0], child["size"][2]) if rotation % 2 == 0 else (child["size"][2], child["size"][0])
            box = (offset[0], offset[2], offset[0] + w - 1, offset[2] + dep - 1)
            if any(box[0] <= other[2] and box[2] >= other[0] and box[1] <= other[3] and box[3] >= other[1] for other in boxes):
                continue
            addition = cells(child, offset, rotation)
            assert not result.keys() & addition.keys(), (relative, "occupied source overlap")
            result.update(addition); boxes.append(box); parts.append({"source": relative, "marker": b["pos"], "offset": offset, "rotation": rotation, "role": role})
            break
    assert any(p["role"] == "tent" for p in parts) and any(p["role"] == "tower" for p in parts), size
    return save("camp_" + size + "_" + str(variant), result, "cargo_station", {"base": base_name, "parts": parts})


def save(name, content, loot, assembly):
    low = [min(p[i] for p in content) for i in range(3)]
    high = [max(p[i] for p in content) for i in range(3)]
    plan = Plan("ethereal/complex", name, [high[i] - low[i] + 1 for i in range(3)])
    for p, (block, props) in content.items():
        pos = tuple(p[i] - low[i] for i in range(3))
        plan.put(*pos, block, props or None, loot if block == "minecraft:barrel" else None)
    containers = sum(block == "minecraft:barrel" for block, _ in content.values())
    if not containers:
        # Accessible stash at a free source-ground cell, not invented architecture.
        available = [p for p in sorted(plan.cells) if p[1] == 0 and (p[0], 1, p[2]) not in plan.cells and (p[0], 2, p[2]) not in plan.cells]
        assert available, name
        x, _, z = available[len(available) // 2];plan.put(x, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"}, loot)
    row = plan.save();row.update(assembly=assembly, coordinate_shift=low, loot_table="interstice:chests/v5/" + loot)
    return row


def main():
    rows = [save("temple", cells(load("ruins/simple/ruined_temple0")), "laboratory", {"source": "ruins/simple/ruined_temple0"})]
    rows += [camp(size, variant) for size in ["small", "medium", "large"] for variant in range(2)]
    provenance = {"schema": 1, "license": "CC0-1.0", "source_version": "px0RRcOK", "archive_sha512": json.loads((SOURCE / "sources.json").read_text())["archive_sha512"], "material_mapping": EXTRA,
                  "assembly": "Original marker coordinates and connector rotations; original components unchanged spatially. Overlapping optional modules skipped; missing outpost_tower0 and teepee_cyan excluded. Sources contain one large temple; two assembled camp variants per original base.", "entities": "Source entity NBT omitted; original pillager/vindicator spawn overrides retained for camps in native structure settings.", "templates": rows}
    (ROOT / "src/main/resources/assets/interstice/provenance/ethereal-complexes.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n")
    for row in rows:
        print(row["id"], row["size"], row["blocks"], "caches", row["loot_barrels"])


if __name__ == "__main__":
    main()
