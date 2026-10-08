"""Generate the complete M13 stone family without touching existing artwork."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/interstice"
DATA = RES / "data"
BASES = ["vaultstone", "weathered_vaultstone", "polished_vaultstone", "vaultstone_bricks"]
BUILDING_BASES = ["vaultstone", "polished_vaultstone", "vaultstone_bricks"]
VARIANTS = [f"{base}_{shape}" for base in BUILDING_BASES for shape in ["slab", "stairs", "wall"]]
ALL_BLOCKS = BASES + VARIANTS


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def model(name):
    return f"interstice:block/{name}"


def item(name):
    return f"interstice:{name}"


def texture(base):
    return f"interstice:block/stone/{base}"


def block_model(name, parent, textures):
    write(ASSETS / f"models/block/{name}.json", {
        "parent": f"minecraft:block/{parent}", "textures": textures,
    })


def tag(registry, name, values, namespace="minecraft"):
    path = DATA / f"{namespace}/tags/{registry}/{name}.json"
    content = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
    content["values"] = list(dict.fromkeys(content["values"] + [item(value) for value in values]))
    write(path, content)


def recipe(name, value, ingredient):
    write(DATA / f"interstice/recipe/{name}.json", value)
    write(DATA / f"interstice/advancement/recipes/building_blocks/{name}.json", {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_material": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item(ingredient)}]}},
            "has_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": item(name)}},
        },
        "requirements": [["has_material", "has_recipe"]],
        "rewards": {"recipes": [item(name)]},
    })


def crafting(name, source, pattern, count):
    recipe(name, {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": pattern,
        "key": {"#": {"item": item(source)}}, "result": {"id": item(name), "count": count},
    }, source)


def stonecutting(source, target, count=1):
    recipe(f"{target}_from_{source}_stonecutting", {
        "type": "minecraft:stonecutting", "ingredient": {"item": item(source)},
        "result": {"id": item(target), "count": count},
    }, source)


for base in BASES:
    block_model(base, "cube_all", {"all": texture(base)})
    variant = {"model": model(base)}
    if base in ["vaultstone", "weathered_vaultstone"]:
        variant = [{"model": model(base)}]
        for index in [1, 2]:
            name = f"{base}_{index}"
            block_model(name, "cube_all", {"all": texture(name)})
            variant.append({"model": model(name)})
    write(ASSETS / f"blockstates/{base}.json", {"variants": {"": variant}})

for base in BUILDING_BASES:
    faces = {face: texture(base) for face in ["bottom", "top", "side"]}
    slab = f"{base}_slab"
    block_model(slab, "slab", faces)
    block_model(f"{slab}_top", "slab_top", faces)
    write(ASSETS / f"blockstates/{slab}.json", {"variants": {
        "type=bottom": {"model": model(slab)},
        "type=top": {"model": model(f"{slab}_top")},
        "type=double": {"model": model(base)},
    }})
    stairs = f"{base}_stairs"
    for suffix, parent in [("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")]:
        block_model(f"{stairs}{suffix}", parent, faces)
    variants = {}
    for facing, rotation in [("east", 0), ("south", 90), ("west", 180), ("north", 270)]:
        for half in ["bottom", "top"]:
            for shape in ["straight", "inner_left", "inner_right", "outer_left", "outer_right"]:
                suffix = "_" + shape.split("_")[0] if shape != "straight" else ""
                y = rotation
                if half == "bottom" and shape.endswith("_left"):
                    y = (y + 270) % 360
                elif half == "top" and shape.endswith("_right"):
                    y = (y + 90) % 360
                variant = {"model": model(f"{stairs}{suffix}")}
                if half == "top":
                    variant["x"] = 180
                if y:
                    variant["y"] = y
                if half == "top" or y:
                    variant["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = variant
    write(ASSETS / f"blockstates/{stairs}.json", {"variants": variants})
    wall = f"{base}_wall"
    for suffix, parent in [("post", "template_wall_post"), ("side", "template_wall_side"),
                           ("side_tall", "template_wall_side_tall"), ("inventory", "wall_inventory")]:
        block_model(f"{wall}_{suffix}", parent, {"wall": texture(base)})
    multipart = [{"when": {"up": "true"}, "apply": {"model": model(f"{wall}_post")}}]
    for height, suffix in [("low", "side"), ("tall", "side_tall")]:
        for facing, rotation in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
            apply = {"model": model(f"{wall}_{suffix}"), "uvlock": True}
            if rotation:
                apply["y"] = rotation
            multipart.append({"when": {facing: height}, "apply": apply})
    write(ASSETS / f"blockstates/{wall}.json", {"multipart": multipart})

for name in ALL_BLOCKS:
    inventory_model = f"{name}_inventory" if name.endswith("_wall") else name
    write(ASSETS / f"models/item/{name}.json", {"parent": model(inventory_model)})
    functions = []
    if name.endswith("_slab"):
        functions.append({
            "function": "minecraft:set_count", "count": 2.0, "add": False,
            "conditions": [{"condition": "minecraft:block_state_property", "block": item(name), "properties": {"type": "double"}}],
        })
        functions.append({"function": "minecraft:explosion_decay"})
    entry = {"type": "minecraft:item", "name": item(name)}
    pool = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]}
    if functions:
        entry["functions"] = functions
    else:
        pool["conditions"] = [{"condition": "minecraft:survives_explosion"}]
    write(DATA / f"interstice/loot_table/blocks/{name}.json", {
        "type": "minecraft:block", "pools": [pool],
        "random_sequence": f"interstice:blocks/{name}",
    })

tag("block", "mineable/pickaxe", ALL_BLOCKS)
tag("block", "needs_stone_tool", ALL_BLOCKS)
tag("block", "vaultstones", BASES, "interstice")
for registry in ["block", "item"]:
    tag(registry, "vaultstone_building_blocks", ALL_BLOCKS, "interstice")
    for shape in ["slab", "stairs", "wall"]:
        tag(registry, {"slab": "slabs", "stairs": "stairs", "wall": "walls"}[shape],
            [f"{base}_{shape}" for base in BUILDING_BASES])

crafting("polished_vaultstone", "vaultstone", ["##", "##"], 4)
crafting("vaultstone_bricks", "polished_vaultstone", ["##", "##"], 4)
for base in BUILDING_BASES:
    crafting(f"{base}_slab", base, ["###"], 6)
    crafting(f"{base}_stairs", base, ["#  ", "## ", "###"], 4)
    crafting(f"{base}_wall", base, ["###", "###"], 6)
    for shape in ["slab", "stairs", "wall"]:
        stonecutting(base, f"{base}_{shape}", 2 if shape == "slab" else 1)
    recipe(f"{base}_from_slabs", {
        "type": "minecraft:crafting_shapeless", "category": "building",
        "ingredients": [{"item": item(f"{base}_slab")}] * 2, "result": {"id": item(base), "count": 1},
    }, f"{base}_slab")

for source, target in [("weathered_vaultstone", "vaultstone"), ("vaultstone", "polished_vaultstone"),
                       ("vaultstone", "vaultstone_bricks"), ("polished_vaultstone", "vaultstone_bricks")]:
    stonecutting(source, target)
for source, targets in [("vaultstone", ["polished_vaultstone", "vaultstone_bricks"]),
                        ("polished_vaultstone", ["vaultstone_bricks"])]:
    for target in targets:
        for shape in ["slab", "stairs", "wall"]:
            stonecutting(source, f"{target}_{shape}", 2 if shape == "slab" else 1)
recipe("vaultstone_from_weathered_vaultstone_smelting", {
    "type": "minecraft:smelting", "category": "blocks", "ingredient": {"item": item("weathered_vaultstone")},
    "result": {"id": item("vaultstone")}, "experience": 0.1, "cookingtime": 200,
}, "weathered_vaultstone")

translations = {
    "vaultstone": ("Сводчатый камень", "Vaultstone"),
    "weathered_vaultstone": ("Выветренный сводчатый камень", "Weathered Vaultstone"),
    "polished_vaultstone": ("Полированный сводчатый камень", "Polished Vaultstone"),
    "vaultstone_bricks": ("Кирпичи сводчатого камня", "Vaultstone Bricks"),
    "vaultstone_slab": ("Плита из сводчатого камня", "Vaultstone Slab"),
    "vaultstone_stairs": ("Ступени из сводчатого камня", "Vaultstone Stairs"),
    "vaultstone_wall": ("Ограда из сводчатого камня", "Vaultstone Wall"),
    "polished_vaultstone_slab": ("Плита из полированного сводчатого камня", "Polished Vaultstone Slab"),
    "polished_vaultstone_stairs": ("Ступени из полированного сводчатого камня", "Polished Vaultstone Stairs"),
    "polished_vaultstone_wall": ("Ограда из полированного сводчатого камня", "Polished Vaultstone Wall"),
    "vaultstone_bricks_slab": ("Плита из кирпичей сводчатого камня", "Vaultstone Brick Slab"),
    "vaultstone_bricks_stairs": ("Ступени из кирпичей сводчатого камня", "Vaultstone Brick Stairs"),
    "vaultstone_bricks_wall": ("Ограда из кирпичей сводчатого камня", "Vaultstone Brick Wall"),
}
for index, language in enumerate(["ru_ru", "en_us"]):
    path = ASSETS / f"lang/{language}.json"
    content = json.loads(path.read_text(encoding="utf-8"))
    content.update({f"block.interstice.{name}": labels[index] for name, labels in translations.items()})
    write(path, content)

print(f"Generated {len(ALL_BLOCKS)} Vaultstone blocks with models, loot, tool tags, recipes and localization.")
