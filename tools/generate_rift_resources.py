"""Reproducible native portal resources. Recipes intentionally require an initial Interstice expedition."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/interstice"
DATA = RES / "data"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


write(ASSETS / "blockstates/rift_frame.json", {"variants": {"": {"model": "interstice:block/rift_frame"}}})
write(ASSETS / "models/block/rift_frame.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "interstice:block/rift_frame"}})
write(ASSETS / "models/item/rift_frame.json", {"parent": "interstice:block/rift_frame"})
write(ASSETS / "models/item/rift_lens.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "interstice:item/rift_lens"}})
write(ASSETS / "blockstates/rift_portal.json", {"variants": {"axis=x": {"model": "interstice:block/rift_portal"}, "axis=z": {"model": "interstice:block/rift_portal", "y": 90}}})
write(ASSETS / "models/block/rift_portal.json", {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
      "textures": {"particle": "interstice:block/rift_portal", "portal": "interstice:block/rift_portal"},
      "elements": [{"from": [0, 0, 7.99], "to": [16, 16, 8.01], "shade": False,
                    "neoforge_data": {"block_light": 15, "sky_light": 0, "ambient_occlusion": False},
                    "faces": {"north": {"texture": "#portal", "uv": [0, 0, 16, 16]}, "south": {"texture": "#portal", "uv": [0, 0, 16, 16]}}}]})
write(ASSETS / "textures/block/rift_portal.png.mcmeta", {"animation": {"frametime": 3, "interpolate": True}})
write(ASSETS / "blockstates/rift_echo.json", {"variants": {"": {"model": "interstice:block/rift_echo"}}})


def box(start, end, texture):
    element = {"from": start, "to": end, "faces": {face: {"texture": "#" + texture} for face in ["up", "down", "north", "south", "east", "west"]}}
    if texture == "core":
        element.update({"shade": False, "neoforge_data": {"block_light": 15, "sky_light": 0, "ambient_occlusion": False}})
    return element


write(ASSETS / "models/block/rift_echo.json", {"parent": "minecraft:block/block", "ambientocclusion": True,
      "textures": {"particle": "interstice:block/rift_frame", "frame": "interstice:block/rift_frame", "core": "interstice:block/tide_sprout_core"},
      "elements": [box([3, 0, 3], [13, 3, 13], "frame"), box([5, 3, 5], [11, 5, 11], "frame"),
                   box([3, 5, 3], [5, 12, 5], "frame"), box([11, 5, 3], [13, 12, 5], "frame"),
                   box([3, 5, 11], [5, 12, 13], "frame"), box([11, 5, 11], [13, 12, 13], "frame"),
                   box([3, 12, 3], [13, 14, 5], "frame"), box([3, 12, 11], [13, 14, 13], "frame"),
                   box([3, 12, 5], [5, 14, 11], "frame"), box([11, 12, 5], [13, 14, 11], "frame"),
                   box([6, 6, 6], [10, 11, 10], "core")]})
write(DATA / "interstice/loot_table/blocks/rift_frame.json", {"type": "minecraft:block", "pools": [{"rolls": 1,
      "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [{"type": "minecraft:item", "name": "interstice:rift_frame"}]}]})
write(DATA / "interstice/recipe/rift_frame.json", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": [" R ", "RSR", " R "],
      "key": {"R": {"item": "interstice:riftstone"}, "S": {"item": "interstice:riftsilver_ingot"}}, "result": {"id": "interstice:rift_frame", "count": 4}})
write(DATA / "interstice/recipe/rift_lens.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" A ", "ASA", " C "],
      "key": {"A": {"item": "minecraft:amethyst_shard"}, "S": {"item": "interstice:riftsilver_ingot"}, "C": {"item": "minecraft:compass"}}, "result": {"id": "interstice:rift_lens", "count": 1}})
write(DATA / "interstice/advancement/rift_discovery.json", {"parent": "interstice:root", "display": {
      "icon": {"id": "interstice:rift_lens"}, "title": {"translate": "advancements.interstice.rift_discovery.title"},
      "description": {"translate": "advancements.interstice.rift_discovery.description"}, "frame": "task", "show_toast": True, "announce_to_chat": True, "hidden": False},
      "criteria": {"tall": {"trigger": "minecraft:changed_dimension", "conditions": {"to": "interstice:islands_tall"}},
                   "legacy": {"trigger": "minecraft:changed_dimension", "conditions": {"to": "interstice:islands"}}},
      "requirements": [["tall", "legacy"]], "rewards": {"recipes": ["interstice:rift_frame", "interstice:rift_lens"]}})
for name in ["rift_frame", "rift_lens"]:
    write(DATA / f"interstice/advancement/recipes/misc/{name}.json", {"parent": "minecraft:recipes/root",
          "criteria": {"has_silver": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": "interstice:riftsilver_ingot"}]}},
                       "has_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": f"interstice:{name}"}}},
          "requirements": [["has_silver", "has_recipe"]], "rewards": {"recipes": [f"interstice:{name}"]}})
write(DATA / "interstice/tags/block/rift_conductors.json", {"replace": False, "values": ["minecraft:" + name for name in [
      "copper_block", "exposed_copper", "weathered_copper", "oxidized_copper", "waxed_copper_block", "waxed_exposed_copper", "waxed_weathered_copper", "waxed_oxidized_copper"]]})
for tag in ["mineable/pickaxe"]:
    path = DATA / f"minecraft/tags/block/{tag}.json"
    content = json.loads(path.read_text(encoding="utf-8")); content["values"] = list(dict.fromkeys(content["values"] + ["interstice:rift_frame"]))
    write(path, content)

translations = {
    "block.interstice.rift_frame": ("Рамка разлома", "Rift Frame"),
    "block.interstice.rift_portal": ("Разлом Междуморья", "Interstice Rift"),
    "block.interstice.rift_echo": ("Узел возвращения", "Return Echo"),
    "item.interstice.rift_lens": ("Линза разлома", "Rift Lens"),
    "advancements.interstice.rift_discovery.title": ("По ту сторону улова", "Beyond the Catch"),
    "advancements.interstice.rift_discovery.description": ("Попадите в Междуморье и узнайте, как закрепить разлом", "Enter the Interstice and learn to stabilize a rift"),
    "message.interstice.rift.impossible_catch": ("Леска тянется вверх. На другом конце — не рыба…", "The line pulls upward. There is no fish on the other end…"),
    "message.interstice.rift.rising_water": ("Вода в котле падает вверх…", "The cauldron water falls upward…"),
    "message.interstice.rift.entered": ("Междуморье. Светящийся узел под навесом вернёт вас к месту входа: нажмите на него ПКМ.", "The Interstice. Right-click the glowing echo under the shelter to return to your entrance."),
    "message.interstice.rift.emerged": ("Вы снова в обычном мире. Этот узел связан с построенным в Междуморье порталом.", "You are back in the Overworld. This echo is linked to the portal built in the Interstice."),
    "message.interstice.rift.incomplete_frame": ("Нужна рамка 4×5 из рамок разлома с пустым проёмом 2×3. Углы необязательны.", "Build a 4×5 Rift Frame with an empty 2×3 interior. Corners are optional."),
    "message.interstice.rift.unsafe_source": ("У входа нет безопасного места для возвращения.", "There is no safe return position at this entrance."),
    "message.interstice.rift.missing_dimension": ("Назначение разлома недоступно. Переход не состоялся.", "The rift destination is unavailable. No transfer occurred."),
    "message.interstice.rift.no_landing": ("Разлом не нашёл безопасной высадки. Переход не состоялся.", "The rift found no safe landing. No transfer occurred."),
    "message.interstice.rift.broken_echo": ("Связанный узел возвращения разрушен. Переход не состоялся.", "The linked return echo was destroyed. No transfer occurred."),
    "message.interstice.rift.unsafe_return": ("Обратный вход разрушен или рядом нет безопасного места. Переход не состоялся.", "The return entrance is broken or has no safe landing. No transfer occurred."),
    "message.interstice.rift.unlinked_echo": ("Этот узел не связан с местом входа.", "This echo has no linked entrance."),
    "message.interstice.rift.cooldown": ("Разлом ещё не успокоился. Подождите несколько секунд.", "The rift is still settling. Wait a few seconds."),
    "message.interstice.rift.transfer_failed": ("Разлом не открылся. Попробуйте снова.", "The rift did not open. Try again."),
    "item.interstice.rift_lens.frame_hint": ("ПКМ по рамке: открыть портал. Проём 2×3, внешняя рамка 4×5.", "Right-click a frame to open the portal. Interior 2×3, outer frame 4×5."),
    "item.interstice.rift_lens.anomaly_hint": ("Вторая рука: необычный улов без грозы. ПКМ по полному котлу на медном блоке: вызвать аномалию.", "Offhand: an unusual catch without a storm. Right-click a full cauldron on copper to call an anomaly."),
}
for index, language in enumerate(["ru_ru", "en_us"]):
    path = ASSETS / f"lang/{language}.json"; content = json.loads(path.read_text(encoding="utf-8"))
    content.update({key: text[index] for key, text in translations.items()}); write(path, content)
print("Generated project models, recipes, tags and localization. Texture provenance is owned by GenerateProjectTextures.java.")
