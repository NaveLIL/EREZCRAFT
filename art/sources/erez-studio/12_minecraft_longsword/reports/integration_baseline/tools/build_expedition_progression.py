#!/usr/bin/env python3
"""Deterministic recipes, visible trials, bilingual handbook and whole-texture item geometry."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
DATA = RES / "data/interstice"
ASSETS = RES / "assets/interstice"

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")

source_root = ROOT / "art/sources/cc-by/armaments"
provenance = json.loads((source_root / "sources.json").read_text(encoding="utf-8"))
assert provenance["license"] == "CC-BY-4.0"
for row in provenance["sources"]:
    data = (source_root / row["file"]).read_bytes()
    assert hashlib.sha256(data).hexdigest() == row["source_sha256"], row["file"]
    target = ASSETS / "textures/item/armaments" / row["file"]
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
(ASSETS / "provenance/armaments-LICENSE.txt").write_bytes((source_root / "LICENSE.txt").read_bytes())
provenance["license_url"] = "https://creativecommons.org/licenses/by/4.0/"
provenance["license_file"] = "armaments-LICENSE.txt"
write(ASSETS / "provenance/armaments.json", provenance)

LANG = {lang: json.loads((ASSETS / f"lang/{lang}.json").read_text(encoding="utf-8")) for lang in ["ru_ru", "en_us"]}
def text(key, ru, en):
    LANG["ru_ru"][key] = ru
    LANG["en_us"][key] = en

text("advancements.interstice.root.title", "Междуморье", "Interstice")
text("advancements.interstice.root.description", "Освойте путь между морями. Полевой журнал объяснит испытания, снаряжение и безопасное возвращение.", "Master the path between seas. Your field journal explains trials, equipment and safe return travel.")

def inventory(item):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": "interstice:" + item}]}}

def trial(id, parent, icon, ru, en, desc_ru, desc_en, criteria, requirements=None, frame="task", xp=0):
    prefix = "advancements.interstice." + id
    text(prefix + ".title", ru, en)
    text(prefix + ".description", desc_ru, desc_en)
    value = {"parent": "interstice:" + parent,
             "display": {"icon": {"id": "interstice:" + icon}, "title": {"translate": prefix + ".title"},
                         "description": {"translate": prefix + ".description"}, "frame": frame,
                         "show_toast": True, "announce_to_chat": frame == "challenge", "hidden": False},
             "criteria": criteria, "requirements": requirements or [[k] for k in criteria]}
    if xp: value["rewards"] = {"experience": xp}
    write(DATA / f"advancement/trials/{id}.json", value)

def has(id, parent, icon, ru, en, dr, de, frame="task", xp=0):
    trial(id, parent, icon, ru, en, dr, de, {"obtained": inventory(icon)}, frame=frame, xp=xp)

def kill(kind, nbt=None):
    entity = {"type": "interstice:" + kind}
    if nbt: entity["nbt"] = nbt
    return {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": entity}}

def recipe(id, pattern, key, result=None, count=1):
    write(DATA / f"recipe/{id}.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
          "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
          "result": {"id": "interstice:" + (result or id), "count": count}})
    write(DATA / f"advancement/recipes/equipment/{id}.json", {"parent": "minecraft:recipes/root",
          "criteria": {"material": inventory("riftsilver_ingot"), "craft": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": "interstice:" + id}}},
          "requirements": [["material", "craft"]], "rewards": {"recipes": ["interstice:" + id]}})

def tag(namespace, path, values):
    p = RES / f"data/{namespace}/tags/item/{path}.json"
    old = json.loads(p.read_text()) if p.exists() else {"replace": False, "values": []}
    old["values"] = list(dict.fromkeys(old["values"] + values)); write(p, old)

# Both tiers use local wood; no circular requirement on their own equipment.
patterns = {"sword": [" M ", " M ", " S "], "pickaxe": ["MMM", " S ", " S "],
            "axe": ["MM", "MS", " S"], "shovel": [" M ", " S ", " S "], "hoe": ["MM", " S", " S"],
            "helmet": ["MMM", "M M"], "chestplate": ["M M", "MMM", "MMM"],
            "leggings": ["MMM", "M M", "M M"], "boots": ["M M", "M M"]}
names = {"sword": ("меч", "Sword"), "pickaxe": ("кирка", "Pickaxe"), "axe": ("топор", "Axe"),
         "shovel": ("лопата", "Shovel"), "hoe": ("мотыга", "Hoe"), "helmet": ("шлем", "Helmet"),
         "chestplate": ("нагрудник", "Chestplate"), "leggings": ("поножи", "Leggings"), "boots": ("ботинки", "Boots")}
for tier in ["riftsilver", "riftsteel"]:
    for kind, pattern in patterns.items():
        id = tier + "_" + kind
        key = {"M": "interstice:" + tier + "_ingot"}
        if any("S" in row for row in pattern): key["S"] = "interstice:world_stick"
        recipe(id, pattern, key)
        ru, en = names[kind]
        text("item.interstice." + id, ru.capitalize() + (" из разломного серебра" if tier == "riftsilver" else " из рифтстали"),
             ("Riftsilver " if tier == "riftsilver" else "Riftsteel ") + en)
        if kind in ["sword", "pickaxe", "axe", "shovel", "hoe"]:
            tag("minecraft", "" + ("swords" if kind == "sword" else kind + "s"), ["interstice:" + id])
            tag("c", "tools/" + ("sword" if kind == "sword" else kind), ["interstice:" + id])
            tag("minecraft", "enchantable/mining" if kind != "sword" else "enchantable/sword", ["interstice:" + id])
            tag("minecraft", "enchantable/mining_loot" if kind != "sword" else "enchantable/weapon", ["interstice:" + id])
            if kind == "axe": tag("minecraft", "enchantable/sharp_weapon", ["interstice:" + id])
            tag("minecraft", "enchantable/durability", ["interstice:" + id])
        else:
            tag("minecraft", "enchantable/armor", ["interstice:" + id])
            tag("minecraft", "dyeable", ["interstice:" + id])
            tag("minecraft", "enchantable/equippable", ["interstice:" + id])
            tag("minecraft", "trimmable_armor", ["interstice:" + id])
            tag("minecraft", "enchantable/" + {"helmet": "head_armor", "chestplate": "chest_armor", "leggings": "leg_armor", "boots": "foot_armor"}[kind], ["interstice:" + id])
            tag("minecraft", "enchantable/durability", ["interstice:" + id])
        # Explicit self tags also let other mods discover and classify both native tiers.
        tag("interstice", tier + "_equipment", ["interstice:" + id])

text("item.interstice.riftsteel_ingot", "Слиток рифтстали", "Riftsteel Ingot")
text("item.interstice.field_journal", "Полевой журнал Междуморья", "Interstice Field Journal")
text("tooltip.interstice.field_journal", "ПКМ: маршрут экспедиции и ваши испытания. Прогресс обновляется при открытии.", "Use: expedition route and your trials. Progress refreshes when opened.")
text("message.interstice.field_journal", "Вы получили полевой журнал. Откройте его ПКМ; испытания находятся во вкладке «Междуморье» меню достижений.", "You received a field journal. Use it to read; trials are in the Interstice advancement tab.")
text("tooltip.interstice.riftsilver_armor", "Первая ступень экспедиции. Не защищает от пробивающего урона пауков.", "First expedition tier. Does not block piercing spider damage.")
text("tooltip.interstice.riftsteel_armor", "Укусы и плевки местных пауков: −8% урона за надетую часть, до −32%. Подкладка изнашивается. Верхнее море остаётся смертельным.", "Native spider bites and spit: −8% damage per worn piece, up to −32%. Lining wears out. The upper sea remains lethal.")
tag("c", "ingots", ["interstice:riftsteel_ingot"])
tag("c", "ingots/riftsteel", ["interstice:riftsteel_ingot"])
write(DATA / "recipe/retort_riftsteel.json", {"type": "interstice:retort", "inputs": [
    {"ingredient": {"item": "interstice:" + item}, "count": count} for item, count in [
        ("riftsilver_ingot", 4), ("pyrolith", 2), ("pure_vitriolite_lining", 2), ("chemotrophic_fabric", 2)]],
    "outputs": [{"id": "interstice:riftsteel_ingot", "count": 4}], "ticks": 1200})
recipe("field_journal", ["B", "R"], {"B": "minecraft:book", "R": "interstice:riftstone"})

has("first_tools", "smelt_riftsilver", "riftsilver_pickaxe", "Серебряная жила", "Silver Vein",
    "Сделайте серебряную кирку: 3 слитка и 2 местные палки. Она добывает руды железного уровня.",
    "Craft a riftsilver pickaxe: 3 ingots and 2 native sticks. Mines iron-tier ores.", xp=25)
trial("silver_suit", "trials/first_tools", "riftsilver_chestplate", "Первая экипировка", "First Equipment",
      "Соберите четыре части серебряной брони. Она помогает в обычном бою, но укусы пауков пробивают её.",
      "Collect all four riftsilver armour pieces. Useful in ordinary combat; spider bites pierce it.",
      {k: inventory("riftsilver_" + k) for k in ["helmet", "chestplate", "leggings", "boots"]}, frame="goal", xp=50)
trial("return_home", "rift_discovery", "wayfarer_key", "Есть обратный путь", "A Way Back",
      "Вернитесь живым в обычный мир. Светящийся узел под навесом активируется ПКМ.",
      "Return alive to the Overworld. Use the glowing node under the arrival shelter.", {"returned": {"trigger": "minecraft:impossible"}}, xp=25)
trial("stable_portal", "trials/return_home", "rift_frame", "Дорога между морями", "Road Between Seas",
      "Пройдите через построенный портал. Внешняя рамка 4×5, проём 2×3; активируйте линзой. Углы не нужны.",
      "Travel through a built portal. Outer frame 4×5, opening 2×3; activate with a lens. Corners optional.",
      {"travelled": {"trigger": "minecraft:impossible"}}, frame="goal", xp=100)
has("tide_indicator", "rift_discovery", "tide_indicator", "Слушая прилив", "Reading the Tide",
    "Получите индикатор прилива. Муфту давления ищите в руинах; индикатор показывает приближение опасности.",
    "Obtain a tide indicator. Find a pressure coupler in ruins; the indicator warns of the tide.")
trial("survive_tide", "trials/tide_indicator", "ballast_belt", "Под надёжной крышей", "A Reliable Roof",
      "Укройтесь на последние 10 секунд прилива и дождитесь начала отлива в Междуморье. Смерть, выход или уход сбрасывают попытку.",
      "Stay sheltered for the last 10 seconds of surge and reach ebb in the realm. Death, logout or leaving resets the attempt.",
      {"survived": {"trigger": "minecraft:impossible"}}, frame="challenge", xp=100)
has("retort", "smelt_riftsilver", "reaction_retort", "Полевая химия", "Field Chemistry",
    "Сделайте реакционную реторту. Откройте её библиотеку рецептов, загрузите реагенты и топливо, затем запустите реакцию.",
    "Craft a reaction retort. Open its recipe library, load reagents and fuel, then start the reaction.")
has("clean_lining", "trials/retort", "pure_vitriolite_lining", "Чистая подкладка", "Clean Lining",
    "В реторте соедините 2 осколка витриолита и пиролит. Подкладка нужна для защиты и рифтстали.",
    "React 2 vitriolite shards and pyrolith in the retort. Lining is used for protection and riftsteel.")
trial("clean_food", "trials/retort", "purified_bread", "Еда без последствий", "Safe Food",
      "Очистите хлеб или корень в реторте с умбральным сорбентом. Не ешьте необработанный токсичный урожай.",
      "Purify bread or roots with umbral sorbent in a retort. Do not eat raw toxic crops.",
      {"bread": inventory("purified_bread"), "root": inventory("purified_root")}, [["bread", "root"]])
has("field_pack", "trials/clean_lining", "field_backpack", "Всё своё с собой", "Pack for the Journey",
    "Сделайте полевой рюкзак. Носите его в отдельном слоте «Спина», сохраняя место для нагрудника.",
    "Craft a field backpack. Wear it in the separate Back slot without giving up your chestplate.")
has("surveyor", "trials/field_pack", "structure_surveyor", "По следам руин", "Following the Ruins",
    "Сделайте искатель структур. Он поможет искать места для следующей экспедиции.",
    "Craft a structure surveyor to locate destinations for your next expedition.")
trial("first_spider", "rift_discovery", "riftsilver_sword", "Не подходи близко", "Keep Your Distance",
      "Победите местного паука. Укусы и плевки пробивают обычную броню: используйте дистанцию и укрытия.",
      "Defeat a native spider. Its bites and spit pierce ordinary armour: use distance and cover.", {"spider": kill("cave_rift_spider")}, xp=25)
trial("all_spiders", "trials/first_spider", "riftsteel_sword", "Знать своего врага", "Know Your Enemy",
      "Победите налётчика, засадника и плевальщика. Разные повадки требуют разных тактик.",
      "Defeat a skirmisher, lurker and spitter. Their different habits call for different tactics.",
      {k: kill("cave_rift_spider", "{SpiderVariant:" + str(i) + "b}") for i, k in enumerate(["skirmisher", "lurker", "spitter"])}, frame="challenge", xp=100)
for id, entity, ru, en, dr, de in [
    ("vault_hunter", "vault_hunter", "Не теряй его из виду", "Watch the Hunter", "Победите сводника. Разрывайте видимость и не стойте под замахом.", "Defeat a vault hunter. Break sight and evade its wind-up."),
    ("upper_ghast", "upper_sea_ghast", "Охотник верхнего моря", "Upper Sea Hunter", "Победите гаста верхнего моря. Его быстрые заряды опасны на открытом месте.", "Defeat an upper sea ghast. Its fast shots punish exposed targets."),
    ("tide_phantom", "tide_phantom", "Сорванный захват", "Broken Grasp", "Победите приливного фантома. Он появляется перед приливом и может унести игрока.", "Defeat a tide phantom. It appears before the tide and can carry players away.")]:
    trial(id, "trials/all_spiders", "riftsteel_sword", ru, en, dr, de, {"killed": kill(entity)}, frame="challenge", xp=100)
has("riftsteel", "trials/clean_lining", "riftsteel_ingot", "Рождённая между морями", "Forged Between Seas",
    "Реторта: 4 серебряных слитка + 2 пиролита + 2 чистые подкладки + 2 хемотрофные ткани → 4 слитка рифтстали.",
    "Retort: 4 riftsilver ingots + 2 pyrolith + 2 pure linings + 2 chemotrophic fabrics → 4 riftsteel ingots.", frame="goal", xp=50)
trial("riftsteel_tools", "trials/riftsteel", "riftsteel_pickaxe", "На глубине", "Into the Depths",
      "Сделайте меч и кирку из рифтстали. Кирка добывает алмазный уровень; ремонтируйте инструменты слитками сплава.",
      "Craft a riftsteel sword and pickaxe. The pick mines diamond-tier ores; repair tools with alloy ingots.",
      {"sword": inventory("riftsteel_sword"), "pickaxe": inventory("riftsteel_pickaxe")}, frame="goal", xp=50)
trial("riftsteel_suit", "trials/riftsteel", "riftsteel_chestplate", "Местные правила", "Local Rules",
      "Наденьте полный комплект рифтстали в Междуморье. Он снижает урон укусов и плевков на 32%, но не спасает от верхнего моря.",
      "Wear a full riftsteel suit in the realm. Reduces bite and spit damage by 32%; the upper sea remains lethal.",
      {"equipped": {"trigger": "minecraft:impossible"}}, frame="goal", xp=100)
master = ["return_home", "stable_portal", "survive_tide", "clean_food", "surveyor", "all_spiders", "vault_hunter", "upper_ghast", "tide_phantom", "riftsteel_tools", "riftsteel_suit"]
trial("master", "trials/riftsteel_suit", "riftsteel_chestplate", "Свой между морями", "At Home Between Seas",
      "Освойте возвращение, портал, прилив, питание, поиск руин, все виды местных охотников и рифтстальную экипировку.",
      "Master return travel, portals, tides, safe food, ruin surveying, native hunters and riftsteel equipment.",
      {"mastered": {"trigger": "minecraft:tick", "conditions": {"player": {"type_specific": {"type": "minecraft:player",
       "advancements": {"interstice:trials/" + k: True for k in master}}}}}}, frame="challenge", xp=250)

# A readable route with a live checklist, not resource handouts or a separate quest dependency.
pages = {
"overview": ("МЕЖДУМОРЬЕ\nПолевой журнал\n\nИспытания: %s/%s\n\nОткройте меню достижений → «Междуморье». Здесь — маршрут, дальше — личный список целей. Он обновляется при открытии журнала.",
             "INTERSTICE\nField journal\n\nTrials: %s/%s\n\nOpen Advancements → Interstice. This book explains the route and lists your own milestones. Reopen it to refresh progress."),
"arrival": ("1. Обратный путь\n\nЗапомните светящийся узел под навесом. ПКМ вернёт в место входа. Не ломайте узел. Перед дальней экспедицией убедитесь, что умеете возвращаться.", "1. A way back\n\nRemember the glowing node under the arrival roof. Use it to return to your entry point. Keep it intact and learn the return route before exploring."),
"shelter": ("2. Прилив\n\nСделайте индикатор прилива; муфты давления бывают в руинах. При предупреждении ищите прочную крышу. Для испытания укройтесь на последние 10 секунд прилива и дождитесь отлива.", "2. The tide\n\nCraft a tide indicator; pressure couplers can be found in ruins. Seek a solid roof when warned. To pass the trial, shelter through the last 10 seconds of surge until ebb."),
"silver": ("3. Разломное серебро\n\nДобудьте и переплавьте руду. Местные доски дают палки. Сделайте серебряную кирку, меч и броню. Обычная броня не останавливает укусы и плевки местных пауков.", "3. Riftsilver\n\nMine and smelt the ore. Native planks make sticks. Craft a silver pickaxe, sword and armour. Ordinary armour does not stop native spider bites or spit."),
"portal": ("4. Устойчивый портал\n\nПостройте рамку 4×5 из рамок разлома, проём 2×3 пустой. Углы не нужны. ПКМ линзой по рамке; постойте внутри 2 секунды. Рецепты рамок и линзы смотрите в JEI.", "4. Stable portal\n\nBuild a 4×5 rift frame with an empty 2×3 opening. Corners are optional. Use a rift lens on the frame, then stand inside for 2 seconds. Find both recipes in JEI."),
"chemistry": ("5. Полевая химия\n\nРецепты — в библиотеке реторты. Подкладка: 2 осколка витриолита + пиролит. Сорбент: умбральный уголь + аэролит. Загружайте реагенты и топливо. Очищайте пищу сорбентом.", "5. Field chemistry\n\nUse the retort's recipe library. Pure lining: 2 vitriolite shards + pyrolith. Sorbent: umbral coal + aerolite. Supply reagents and fuel. Purify food with sorbent."),
"equipment": ("6. Экспедиция\n\nПолевой рюкзак носится в слоте «Спина». Искатель структур поможет найти руины. Ставьте маршрутные маяки. Защитное покрытие и балласт полезны, но расходуются.", "6. Expedition\n\nWear a field backpack in the Back slot. Use a structure surveyor to find ruins and route beacons to mark your way. Coating and ballast help but wear out."),
"hunting": ("7. Местные охотники\n\nПауки: налётчик, засадник и плевальщик. Используйте дистанцию и укрытия. Сводник преследует по видимости. Гаст стреляет быстро; перед приливом опасайтесь захвата фантомов.", "7. Native hunters\n\nSpiders: skirmisher, lurker and spitter. Use distance and cover. The vault hunter tracks by sight. Ghast shots are fast; beware phantom grabs before the tide."),
"steel": ("8. Рифтсталь\n\nВ реторту: 4 слитка разломного серебра, 2 пиролита, 2 чистые подкладки, 2 хемотрофные ткани. Через 60 секунд реакции получите 4 слитка. Нужны топливо и свободный выход.", "8. Riftsteel\n\nRetort: 4 riftsilver ingots, 2 pyrolith, 2 pure linings and 2 chemotrophic fabrics. After 60 seconds of reaction, receive 4 ingots. Supply fuel and free output space."),
"armour": ("9. Местная защита\n\nРифтсталь: кирка алмазного уровня и прочное снаряжение. Надетая часть брони снижает укусы и плевки на 8%, комплект — на 32%. Подкладка изнашивается. Верхнее море остаётся смертельным.", "9. Native protection\n\nRiftsteel offers diamond-tier mining and durable equipment. Each worn armour piece reduces bites and spit by 8%; a suit gives 32%. Lining wears out. The upper sea remains lethal."),
"completion": ("10. Свой между морями\n\nОсвойте портал, прилив и местную химию, исследуйте руины, победите охотников и соберите рифтсталь. Достижения дают опыт. Они не закрывают рецепты и не отнимают старый прогресс.", "10. At home between seas\n\nMaster portals, tides and chemistry, survey ruins, defeat hunters and forge riftsteel. Trials reward experience. They do not lock recipes or remove existing progress."),
"checklist": ("Ваши испытания", "Your trials")}
for key, (ru, en) in pages.items(): text("journal.interstice." + key, ru, en)

# Native model geometry uses existing whole textures. No third-party PNG is redrawn.
def cube(a, b, texture):
    return {"from": a, "to": b, "faces": {f: {"texture": texture} for f in ["north", "south", "east", "west", "up", "down"]}}
for tier in ["riftsilver", "riftsteel"]:
    metal = "interstice:block/vitriolite" if tier == "riftsilver" else "interstice:block/stone/polished_vaultstone"
    for kind in ["pickaxe", "axe", "shovel", "hoe"]:
        elements = [cube([7, 0, 7], [9, 12, 9], "#handle")]
        heads = {"pickaxe": [([2, 11, 6], [14, 14, 10]), ([1, 9, 7], [3, 12, 9]), ([13, 9, 7], [15, 12, 9])],
                 "axe": [([2, 10, 6], [9, 15, 10]), ([1, 11, 7], [3, 14, 9])],
                 "shovel": [([5, 11, 7], [11, 16, 9])], "hoe": [([2, 12, 7], [10, 14, 9]), ([1, 10, 7], [3, 14, 9])]}
        elements += [cube(a, b, "#metal") for a, b in heads[kind]]
        write(ASSETS / f"models/item/{tier}_{kind}.json", {"parent": "minecraft:block/block", "textures": {
            "handle": "interstice:block/garden/paleheart_log", "metal": metal, "particle": metal}, "elements": elements,
            "display": {"gui": {"rotation": [25, -35, -40], "scale": [.8, .8, .8]},
                        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, .5], "scale": [.85, .85, .85]},
                        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, .5], "scale": [.85, .85, .85]},
                        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [.68, .68, .68]},
                        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [.68, .68, .68]},
                        "ground": {"translation": [0, 2, 0], "scale": [.5, .5, .5]}}})
    for kind, source in [("sword", "tool_greatsword_silver" if tier == "riftsilver" else "tool_greatsword_steel"),
                         ("helmet", "armor_helmet_tin" if tier == "riftsilver" else "armor_helmet_superalloy"),
                         ("chestplate", "armor_chest_tin" if tier == "riftsilver" else "armor_chest_superalloy"),
                         ("leggings", "armor_legs_tin" if tier == "riftsilver" else "armor_legs_superalloy"),
                         ("boots", "armor_boots_tin" if tier == "riftsilver" else "armor_boots_superalloy")]:
        write(ASSETS / f"models/item/{tier}_{kind}.json", {"parent": "minecraft:item/handheld" if kind == "sword" else "minecraft:item/generated",
              "textures": {"layer0": "interstice:item/armaments/" + source}})
write(ASSETS / "models/item/field_journal.json", {"parent": "minecraft:item/written_book"})
write(ASSETS / "models/item/riftsteel_ingot.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "interstice:item/armaments/ingot_steel"}})
for lang, values in LANG.items(): write(ASSETS / f"lang/{lang}.json", values)
print("Built 20 trials, 18 equipment recipes, retort alloy, journal and RU/EN resources")
