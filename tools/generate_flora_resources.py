"""Build the native cuboid plant models and the Gloomcrown resource family."""
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/interstice"
DATA = RES / "data"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def box(start, end, texture="petal", top=None):
    faces = {side: {"texture": "#" + (top if side == "up" and top else texture)}
             for side in ["up", "down", "north", "south", "west", "east"]}
    for face in faces.values():
        if face["texture"] == "#core":
            face["neoforge_data"] = {"block_light": 15 if texture == "core" else 11,
                                     "sky_light": 0, "ambient_occlusion": False}
    return {"from": start, "to": end, "faces": faces}


def plant(name, elements, textures=None):
    write(ASSETS / f"models/block/{name}.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "ambientocclusion": True,
        "textures": textures or {"particle": "interstice:block/tide_sprout_petal",
                                 "petal": "interstice:block/tide_sprout_petal",
                                 "core": "interstice:block/tide_sprout_core",
                                 "stalk": "interstice:block/tide_sprout_stalk"},
        "elements": elements,
    })


stem = box([6.5, 0, 6.5], [9.5, 16, 9.5], "stalk")
bud = [box([6, 0, 6], [10, 3, 10], "stalk"), box([6, 3, 6], [10, 9, 10])]
for x, z in [(0, -1), (1, 0), (0, 1), (-1, 0)]:
    if x:
        bud += [box([8 + x * 3 - 1, 2, 5], [8 + x * 3 + 1, 7, 11]),
                box([8 + x * 2 - 1, 7, 6], [8 + x * 2 + 1, 10, 10])]
    else:
        bud += [box([5, 2, 8 + z * 3 - 1], [11, 7, 8 + z * 3 + 1]),
                box([6, 7, 8 + z * 2 - 1], [10, 10, 8 + z * 2 + 1])]
bud += [box([6, 10, 6], [10, 11, 10], top="core")]
plant("tide_sprout_bud", bud)
flower = [box([6.5, 0, 6.5], [9.5, 6, 9.5], "stalk"), box([6, 5, 6], [10, 9, 10], "core")]
for start, end in [([5, 3, 2], [11, 6, 6]), ([10, 3, 5], [14, 6, 11]),
                   ([5, 3, 10], [11, 6, 14]), ([2, 3, 5], [6, 6, 11])]:
    flower.append(box(start, end))
for start, end in [([6, 6, 1], [10, 7, 3]), ([13, 6, 6], [15, 7, 10]),
                   ([6, 6, 13], [10, 7, 15]), ([1, 6, 6], [3, 7, 10])]:
    flower.append(box(start, end, top="core"))
for start, end in [([3, 3, 3], [6, 6, 6]), ([10, 3, 3], [13, 6, 6]),
                   ([10, 3, 10], [13, 6, 13]), ([3, 3, 10], [6, 6, 13])]:
    flower.append(box(start, end))
for start, end in [([2, 6, 2], [4, 7, 4]), ([12, 6, 2], [14, 7, 4]),
                   ([12, 6, 12], [14, 7, 14]), ([2, 6, 12], [4, 7, 14])]:
    flower.append(box(start, end, top="core"))
plant("tide_sprout_flower", flower)
node = box([6.25, 7, 6.25], [9.75, 8, 9.75], top="core")
plant("tide_sprout_stem", [stem, node])
plant("tide_sprout_stem_leaf", [stem, node, box([2, 6, 6], [7, 8, 10]),
      box([9, 8, 6], [14, 10, 10]), box([6, 10, 2], [10, 12, 7]), box([6, 4, 9], [10, 6, 14]),
      box([2, 7, 6], [3, 8, 10], top="core"), box([13, 9, 6], [14, 10, 10], top="core"),
      box([6, 11, 2], [10, 12, 3], top="core"), box([6, 5, 13], [10, 6, 14], top="core")])
plant("tide_sprout_root", [stem, box([4, 0, 6], [12, 2, 10], "stalk"), box([6, 0, 4], [10, 2, 12], "stalk")])
write(ASSETS / "models/item/tide_sprout.json", {"parent": "interstice:block/tide_sprout_bud"})
plant("gloomcrown_sapling", [box([7, 0, 7], [9, 11, 9], "stalk"),
                           box([4, 4, 6], [7, 6, 10]), box([9, 7, 6], [12, 9, 10]),
                           box([6, 11, 6], [10, 13, 10])],
      {"particle": "interstice:block/gloomcrown_leaves", "petal": "interstice:block/gloomcrown_leaves",
       "stalk": "interstice:block/gloomcrown_log"})

family = ["gloomcrown_log", "stripped_gloomcrown_log", "gloomcrown_planks", "gloomcrown_leaves", "gloomcrown_sapling"]
for name in family:
    if name.endswith("_log"):
        write(ASSETS / f"models/block/{name}.json", {"parent": "minecraft:block/cube_column",
              "textures": {"end": f"interstice:block/{name}_top", "side": f"interstice:block/{name}"}})
        variants = {"axis=y": {"model": f"interstice:block/{name}"},
                    "axis=x": {"model": f"interstice:block/{name}", "x": 90, "y": 90},
                    "axis=z": {"model": f"interstice:block/{name}", "x": 90}}
    else:
        variants = {"": {"model": f"interstice:block/{name}"}}
        if not name.endswith("_sapling"):
            model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"interstice:block/{name}"}}
            if name.endswith("_leaves"):
                model["render_type"] = "minecraft:cutout_mipped"
                variants[""] = [{"model": f"interstice:block/{name}", "y": rotation} for rotation in [0, 90, 180, 270]]
            write(ASSETS / f"models/block/{name}.json", model)
    write(ASSETS / f"blockstates/{name}.json", {"variants": variants})
    write(ASSETS / f"models/item/{name}.json", {"parent": f"interstice:block/{name}"})
    if not name.endswith("_leaves"):
        write(DATA / f"interstice/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
             "entries": [{"type": "minecraft:item", "name": f"interstice:{name}"}]}]})

# Vanilla leaf rules provide shears, silk touch, fortune, explosion survival and sapling drops.
with ZipFile(ROOT / "build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar") as archive:
    leaves_loot = json.loads(archive.read("data/minecraft/loot_table/blocks/oak_leaves.json"))
leaves_loot["pools"] = leaves_loot["pools"][:2]  # no terrestrial apples
text = json.dumps(leaves_loot).replace("minecraft:oak_leaves", "interstice:gloomcrown_leaves").replace("minecraft:oak_sapling", "interstice:gloomcrown_sapling").replace("minecraft:blocks/oak_leaves", "interstice:blocks/gloomcrown_leaves")
write(DATA / "interstice/loot_table/blocks/gloomcrown_leaves.json", json.loads(text))


def tag(registry, name, values):
    path = DATA / f"minecraft/tags/{registry}/{name}.json"
    content = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
    content["values"] = list(dict.fromkeys(content["values"] + [f"interstice:{value}" for value in values]))
    write(path, content)


logs = family[:2]
for registry in ["block", "item"]:
    tag(registry, "logs_that_burn", logs)
    tag(registry, "logs", logs)
    tag(registry, "planks", ["gloomcrown_planks"])
    tag(registry, "leaves", ["gloomcrown_leaves"])
    tag(registry, "saplings", ["gloomcrown_sapling"])
tag("block", "mineable/axe", logs + ["gloomcrown_planks"])
tag("block", "mineable/hoe", ["gloomcrown_leaves"])
write(DATA / "interstice/tags/item/gloomcrown_logs.json", {"replace": False, "values": [f"interstice:{name}" for name in logs]})
write(DATA / "interstice/recipe/gloomcrown_planks.json", {"type": "minecraft:crafting_shapeless", "category": "building",
      "ingredients": [{"tag": "interstice:gloomcrown_logs"}], "result": {"id": "interstice:gloomcrown_planks", "count": 4}})
write(DATA / "interstice/advancement/recipes/building_blocks/gloomcrown_planks.json", {
    "parent": "minecraft:recipes/root", "criteria": {"has_log": {"trigger": "minecraft:inventory_changed",
    "conditions": {"items": [{"items": "#interstice:gloomcrown_logs"}]}}, "has_recipe": {"trigger": "minecraft:recipe_unlocked",
    "conditions": {"recipe": "interstice:gloomcrown_planks"}}}, "requirements": [["has_log", "has_recipe"]],
    "rewards": {"recipes": ["interstice:gloomcrown_planks"]}})
write(DATA / "neoforge/data_maps/block/strippables.json", {"values": {"interstice:gloomcrown_log": {"stripped_block": "interstice:stripped_gloomcrown_log"}}})
write(DATA / "neoforge/data_maps/item/compostables.json", {"values": {"interstice:gloomcrown_sapling": {"chance": .3}, "interstice:gloomcrown_leaves": {"chance": .3}}})
for language, labels in {
    "ru_ru": ["Бревно сумракрона", "Обтёсанное бревно сумракрона", "Доски сумракрона", "Крона сумракрона", "Саженец сумракрона"],
    "en_us": ["Gloomcrown Log", "Stripped Gloomcrown Log", "Gloomcrown Planks", "Gloomcrown Leaves", "Gloomcrown Sapling"],
}.items():
    path = ASSETS / f"lang/{language}.json"
    content = json.loads(path.read_text(encoding="utf-8"))
    content.update({f"block.interstice.{name}": label for name, label in zip(family, labels)})
    write(path, content)

print("Generated project models, recipes, tags and localization. Texture provenance is owned by GenerateProjectTextures.java.")
