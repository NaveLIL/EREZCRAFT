"""Reproducible expedition recipes, model references and Russian/English product text."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/interstice"
DATA = RES / "data/interstice"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


write(ASSETS / "models/item/wayfarer_key.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "interstice:item/wayfarer_key"}})
write(ASSETS / "models/item/pressure_coupler.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "interstice:item/pressure_coupler"}})
write(DATA / "loot_table/chests/watchpost_ruin.json", {"type": "minecraft:chest", "pools": [
    {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "functions": [{"function": "minecraft:set_count", "count": count}]}]}
    for item, count in [("interstice:pressure_coupler", 1), ("interstice:riftsilver_ingot", 4),
                        ("minecraft:copper_ingot", 12), ("minecraft:amethyst_shard", 8), ("minecraft:compass", 3)]
]})
write(DATA / "recipe/tide_indicator.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["CPC", "CKC", "CAC"],
      "key": {"C": {"item": "minecraft:copper_ingot"}, "P": {"item": "interstice:pressure_coupler"},
              "K": {"item": "minecraft:compass"}, "A": {"item": "minecraft:amethyst_shard"}}, "result": {"id": "interstice:tide_indicator", "count": 1}})
write(DATA / "advancement/recipes/misc/tide_indicator.json", {"parent": "minecraft:recipes/root",
      "criteria": {"has_coupler": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": "interstice:pressure_coupler"}]}},
                   "has_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": "interstice:tide_indicator"}}},
      "requirements": [["has_coupler", "has_recipe"]], "rewards": {"recipes": ["interstice:tide_indicator"]}})
write(DATA / "advancement/watchpost_salvage.json", {"parent": "interstice:root", "display": {
      "icon": {"id": "interstice:pressure_coupler"}, "title": {"translate": "advancements.interstice.watchpost_salvage.title"},
      "description": {"translate": "advancements.interstice.watchpost_salvage.description"}, "frame": "task", "show_toast": True, "announce_to_chat": True, "hidden": False},
      "criteria": {"salvaged": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": "interstice:pressure_coupler"}]}}},
      "rewards": {"recipes": ["interstice:tide_indicator"]}})
write(DATA / "recipe/wayfarer_key.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" A ", "CKC", " C "],
      "key": {"A": {"item": "minecraft:amethyst_shard"}, "C": {"item": "minecraft:copper_ingot"}, "K": {"item": "minecraft:compass"}}, "result": {"id": "interstice:wayfarer_key", "count": 1}})
write(DATA / "advancement/recipes/misc/wayfarer_key.json", {"parent": "minecraft:recipes/root",
      "criteria": {"has_amethyst": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": "minecraft:amethyst_shard"}]}},
                   "has_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": "interstice:wayfarer_key"}}},
      "requirements": [["has_amethyst", "has_recipe"]], "rewards": {"recipes": ["interstice:wayfarer_key"]}})
text = {
    "item.interstice.wayfarer_key": ("Путевой ключ", "Wayfarer Key"),
    "item.interstice.pressure_coupler": ("Приливная мембрана", "Tidal Membrane"),
    "advancements.interstice.watchpost_salvage.title": ("Пост ещё помнит море", "The Watchpost Remembers"),
    "advancements.interstice.watchpost_salvage.description": ("Найдите приливную мембрану в руине наблюдательного поста", "Salvage a tidal membrane from a ruined watchpost"),
    "item.interstice.wayfarer_key.desc": ("Удерживайте ПКМ 10 секунд без движения и урона. Один аварийный возврат на поход.", "Hold use for 10 seconds without moving or taking damage. One emergency return per expedition."),
    "item.interstice.wayfarer_key.prepare": ("Снаружи: Shift+ПКМ и осколок аметиста — подготовить новый поход.", "Outside: sneak-use with an amethyst shard to prepare a new expedition."),
    "item.interstice.wayfarer_key.bound": ("Привязан к владельцу и походу. Передача другому игроку не меняет привязку.", "Bound to an owner and expedition. Giving it away does not change its binding."),
    "message.interstice.key.prepared": ("Новый поход подготовлен. Ключ настроен; доступен один аварийный возврат.", "New expedition prepared. The key is tuned for one emergency return."),
    "message.interstice.key.prepare_first": ("Подготовьте поход: Shift+ПКМ снаружи с осколком аметиста.", "Prepare an expedition: sneak-use outside with an amethyst shard."),
    "message.interstice.key.need_shard": ("Для нового похода нужен один осколок аметиста в инвентаре.", "A new expedition requires one amethyst shard in your inventory."),
    "message.interstice.key.bound": ("Ключ настроен на текущий поход.", "The key is tuned to the current expedition."),
    "message.interstice.key.no_origin": ("У этого похода нет сохранённого внешнего входа.", "This expedition has no recorded external entrance."),
    "message.interstice.key.foreign": ("Этот ключ принадлежит другому игроку.", "This key belongs to another player."),
    "message.interstice.key.old": ("Ключ настроен на прежний поход. Настройте его заново снаружи.", "This key is tuned to an earlier expedition. Retune it outside."),
    "message.interstice.key.spent": ("Аварийный возврат этого похода уже использован. Новый ключ не восстановит его.", "This expedition's emergency return is already spent. A new key will not restore it."),
    "message.interstice.key.concentrating": ("Не двигайтесь. Возвращение через %s с…", "Stay still. Returning in %s s…"),
    "message.interstice.key.moved": ("Движение прервало возвращение. Попытку можно повторить.", "Movement interrupted the return. You can try again."),
    "message.interstice.key.hurt": ("Урон прервал возвращение. Попытку можно повторить.", "Damage interrupted the return. You can try again."),
    "message.interstice.key.released": ("Концентрация прервана. Удерживайте ключ до конца.", "Concentration interrupted. Keep holding the key until it finishes."),
    "message.interstice.key.interrupted": ("Возвращение прервано.", "Return interrupted."),
    "message.interstice.key.dimension_changed": ("Смена измерения прервала концентрацию.", "Changing dimensions interrupted concentration."),
    "message.interstice.key.logout": ("Выход из игры прервал концентрацию.", "Leaving the game interrupted concentration."),
    "message.interstice.key.death": ("Поход завершился гибелью.", "The expedition ended in death."),
    "message.interstice.key.unsafe": ("У внешнего входа нет безопасной точки. Возврат не использован.", "There is no safe position at the external entrance. Your return remains unused."),
    "message.interstice.key.failed": ("Переход не состоялся. Возврат не использован.", "The transfer did not occur. Your return remains unused."),
    "message.interstice.key.returned": ("Аварийный возврат выполнен. Право этого похода использовано.", "Emergency return completed. This expedition's entitlement is spent."),
}
for index, language in enumerate(["ru_ru", "en_us"]):
    path = ASSETS / f"lang/{language}.json"; values = json.loads(path.read_text(encoding="utf-8")); values.update({key: pair[index] for key, pair in text.items()}); write(path, values)
print("Generated project models, recipes, tags and localization. Texture provenance is owned by GenerateProjectTextures.java.")
