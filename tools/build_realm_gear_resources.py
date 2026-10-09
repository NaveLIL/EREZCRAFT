"""M19 model geometry and recipes, reusing existing whole project textures without PNG edits."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
FACES = ("north", "south", "east", "west", "up", "down")


def write(path, data):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def box(start, end, texture, glowing=False):
    faces = {face: {"texture": "#" + texture, "uv": [0, 0, 16, 16]} for face in FACES}
    if glowing:
        for face in faces.values():
            face["neoforge_data"] = {"block_light": 15, "sky_light": 0, "ambient_occlusion": False}
    return {"from": start, "to": end, "faces": faces}


def model(textures, elements):
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": True,
            "textures": {"particle": textures["body"], **textures}, "elements": elements}


def craft(name, pattern, keys):
    write(f"src/main/resources/data/interstice/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
        "key": {key: {"item": "interstice:" + value} for key, value in keys.items()},
        "result": {"id": "interstice:" + name, "count": 1}})


def main():
    textures = {"body": "interstice:block/vitriolite", "stone": "interstice:block/stone/polished_vaultstone",
                "wood": "interstice:block/garden/paleheart_log", "core": "interstice:block/tide_sprout_core",
                "petal": "interstice:block/tide_sprout_petal"}
    for lit in (False, True):
        elements = [box([1, 0, 1], [15, 3, 15], "stone"), box([4, 3, 4], [12, 5, 12], "body"),
                    box([6, 5, 6], [10, 12, 10], "wood"), box([2, 12, 2], [14, 13, 14], "body"),
                    box([6, 13, 6], [10, 16, 10], "core" if lit else "body", glowing=lit)]
        for start, end in (([2, 13, 5], [5, 15, 11]), ([11, 13, 5], [14, 15, 11]),
                           ([5, 13, 2], [11, 15, 5]), ([5, 13, 11], [11, 15, 14])):
            elements.append(box(start, end, "petal"))
        write(f"src/main/resources/assets/interstice/models/block/gear/beacon_{str(lit).lower()}.json", model(textures, elements))
    write("src/main/resources/assets/interstice/blockstates/route_beacon.json", {"variants": {
        "lit=false": {"model": "interstice:block/gear/beacon_false"}, "lit=true": {"model": "interstice:block/gear/beacon_true"}}})
    write("src/main/resources/assets/interstice/models/item/route_beacon.json", {"parent": "interstice:block/gear/beacon_false"})
    belt = model({"body": textures["body"], "lining": textures["stone"]}, [
        box([2, 6, 4], [14, 9, 6], "body"), box([2, 6, 10], [14, 9, 12], "body"),
        box([2, 6, 6], [4, 9, 10], "body"), box([12, 6, 6], [14, 9, 10], "body"),
        box([6, 5, 3], [10, 10, 5], "lining")])
    belt["display"] = {"gui": {"rotation": [35, 35, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                       "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1, 0], "scale": [.6, .6, .6]}}
    write("src/main/resources/assets/interstice/models/item/ballast_belt.json", belt)
    coating = model({"body": textures["body"], "contents": textures["core"], "lid": textures["stone"]}, [
        box([5, 2, 5], [11, 11, 11], "body"), box([6, 11, 6], [10, 13, 10], "body"),
        box([5, 13, 5], [11, 15, 11], "lid"), box([5, 6, 4.9], [11, 9, 5.1], "contents")])
    write("src/main/resources/assets/interstice/models/item/protective_coating.json", coating)
    write("src/main/resources/data/interstice/loot_table/blocks/route_beacon.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": "interstice:route_beacon"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    craft("ballast_belt", ["MLM", "FSF", "III"], {"M": "riftsilver_mesh", "L": "pure_vitriolite_lining",
          "F": "chemotrophic_fabric", "S": "pressure_coupler", "I": "riftsilver_ingot"})
    craft("route_beacon", ["PBP", "MCM", "LSL"], {"P": "phosphorite_crystal", "B": "luminous_bud", "M": "riftsilver_mesh",
          "C": "pressure_coupler", "L": "pure_vitriolite_lining", "S": "world_stick"})
    write("src/main/resources/data/interstice/recipe/retort_protective_coating.json", {"type": "interstice:retort", "inputs": [
        {"ingredient": {"item": "interstice:pure_vitriolite_lining"}, "count": 1},
        {"ingredient": {"item": "interstice:phosphorite_paste"}, "count": 2},
        {"ingredient": {"item": "interstice:umbral_sorbent"}, "count": 2}],
        "outputs": [{"id": "interstice:protective_coating", "count": 1}], "ticks": 1000})
    write("src/main/resources/data/interstice/advancement/recipes/gear_discovery.json", {"parent": "minecraft:recipes/root",
        "criteria": {"processed_lining": {"trigger": "minecraft:inventory_changed", "conditions": {
            "items": [{"items": "interstice:pure_vitriolite_lining"}]}}, "has_recipe": {"trigger": "minecraft:recipe_unlocked",
            "conditions": {"recipe": "interstice:ballast_belt"}}}, "requirements": [["processed_lining", "has_recipe"]],
        "rewards": {"recipes": ["interstice:ballast_belt", "interstice:route_beacon"]}})
    lang = {
        "ru_ru": {
            "block.interstice.route_beacon": "Маршрутный маяк", "item.interstice.ballast_belt": "Балластный пояс",
            "item.interstice.protective_coating": "Защитное покрытие",
            "tooltip.interstice.route_beacon": "Фосфорит: 2 минуты света за кристалл, до 8 минут. Метка владельцу в радиусе 48 блоков. При демонтаже топливо теряется.",
            "tooltip.interstice.ballast_belt": "Держи в любой руке: ослабляет приливную тягу. Износ идёт только под открытой тягой; броня остаётся свободной.",
            "tooltip.interstice.ballast_remaining": "Осталось %s секунд работы под приливом",
            "tooltip.interstice.protective_coating": "ПКМ: покрой надетый нагрудник или нагрудник в другой руке. %s защит от наших токсинов; не защищает от обычного урона.",
            "message.interstice.coating.target": "Нужен нагрудник без оставшегося покрытия — надетый или в другой руке.",
            "message.interstice.coating.applied": "На нагруднике %s зарядов защиты от токсинов.",
            "tooltip.interstice.coating_charges": "Защита от токсинов: %s зарядов покрытия",
            "message.interstice.beacon.fuel": "Маяк: %s секунд света. Топливо — фосфоритовый кристалл.",
            "message.interstice.beacon.position": "Маяк [%s, %s, %s] · свет ещё %s с"
        },
        "en_us": {
            "block.interstice.route_beacon": "Route Beacon", "item.interstice.ballast_belt": "Ballast Belt",
            "item.interstice.protective_coating": "Protective Coating",
            "tooltip.interstice.route_beacon": "Phosphorite: 2 minutes per crystal, up to 8 minutes. Owner receives a marker within 48 blocks. Breaking discards fuel.",
            "tooltip.interstice.ballast_belt": "Hold in either hand to counter tidal lift. Wears only under exposed lift; your armour slot stays free.",
            "tooltip.interstice.ballast_remaining": "%s seconds of exposed tidal use remaining",
            "tooltip.interstice.protective_coating": "Use on an equipped chestplate or one in your other hand. %s protections from our toxins; no ordinary damage immunity.",
            "message.interstice.coating.target": "An uncoated chestplate must be equipped or held in your other hand.",
            "message.interstice.coating.applied": "Chestplate coated: %s toxin protections.",
            "tooltip.interstice.coating_charges": "Toxin protection: %s coating charges",
            "message.interstice.beacon.fuel": "Beacon: %s seconds of light. Fuel: phosphorite crystals.",
            "message.interstice.beacon.position": "Beacon [%s, %s, %s] · %s seconds remaining"
        }
    }
    write("tools/fragments/gear_lang.json", lang)
    # No copied pixels: provenance points at the already verified asset families used by these models.
    write("src/main/resources/assets/interstice/provenance/gear.json", {"format": 1, "generator": "tools/build_realm_gear_resources.py",
          "new_pngs": 0, "existing_textures_unchanged": True, "operation": "Original model geometry referencing existing whole project textures",
          "textures": sorted(set(textures.values())), "source_registry": "docs/ASSET_LIBRARY.md"})
    print("Created M19 geometry, three recipes, discovery advancement, loot, language fragment; zero new or changed PNGs")


if __name__ == "__main__":
    main()
