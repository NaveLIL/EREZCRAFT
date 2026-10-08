"""Author the editable 9x6x9 Watchpost ruin as a vanilla compressed NBT structure."""
import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


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


palette = [
    {"Name": "interstice:riftstone"},
    {"Name": "interstice:gloomcrown_planks"},
    {"Name": "interstice:stripped_gloomcrown_log", "Properties": {"axis": "y"}},
    {"Name": "minecraft:barrel", "Properties": {"facing": "north", "open": "false"}},
    {"Name": "interstice:phosphorite"},
]
palette_bytes = []
for state in palette:
    values = [tag(8, "Name", string(state["Name"]))]
    if "Properties" in state:
        values.append(tag(10, "Properties", compound([tag(8, key, string(value)) for key, value in state["Properties"].items()])))
    palette_bytes.append(compound(values))
cells = {}
for x in range(9):
    for z in range(9):
        if (x, z) not in [(0, 0), (0, 8), (8, 0), (8, 8)]:
            cells[(x, 0, z)] = (0, None)
for y in range(1, 4):
    for x in range(9):
        for z in range(9):
            if (x in [0, 8] or z in [0, 8]) and not (z == 0 and 3 <= x <= 5):
                if (x * 7 + z * 11 + y * 13) % 7 not in [0, 1]:
                    cells[(x, y, z)] = (0, None)
for x, z in [(2, 2), (6, 2), (2, 6), (6, 6)]:
    for y in range(1, 4):
        cells[(x, y, z)] = (2, None)
for x in range(2, 7):
    for z in range(2, 7):
        cells[(x, 4, z)] = (0 if x in [2, 6] or z in [2, 6] else 1, None)
for x in [2, 3]:
    cells[(x, 1, 4)] = (1, None)
cells[(4, 3, 2)] = (4, None)
loot = compound([tag(8, "id", string("minecraft:barrel")), tag(8, "LootTable", string("interstice:chests/watchpost_ruin"))])
cells[(5, 1, 5)] = (3, loot)
blocks = []
for pos, (state, nbt) in sorted(cells.items()):
    values = [tag(9, "pos", ints(pos)), tag(3, "state", struct.pack(">i", state))]
    if nbt:
        values.append(tag(10, "nbt", nbt))
    blocks.append(compound(values))
body = compound([tag(3, "DataVersion", struct.pack(">i", 3955)), tag(9, "size", ints([9, 6, 9])),
                 tag(9, "palette", compounds(palette_bytes)), tag(9, "blocks", compounds(blocks)), tag(9, "entities", compounds([]))])
output = ROOT / "src/main/resources/data/interstice/structure/watchpost_ruin.nbt"
output.parent.mkdir(parents=True, exist_ok=True)
with output.open("wb") as file:
    with gzip.GzipFile(filename="", mode="wb", fileobj=file, mtime=0) as compressed:
        compressed.write(tag(10, "", body))
print(f"Authored {len(blocks)} blocks and one loot barrel: {output}")
