"""Original M21 anchor/controller geometry; reuse whole existing CC0/project textures, no pixel edits."""
from build_realm_gear_resources import write, box, model, craft


def main():
    textures = {"body": "interstice:block/vitriolite", "stone": "interstice:block/stone/polished_vaultstone",
                "wood": "interstice:block/garden/paleheart_log", "core": "interstice:block/tide_sprout_core"}
    anchor = model(textures, [box([0, 0, 0], [16, 4, 16], "stone"), box([2, 4, 2], [14, 12, 14], "body"),
                             box([5, 12, 5], [11, 16, 11], "wood"), box([3, 6, 1.9], [13, 9, 2.1], "core")])
    write("src/main/resources/assets/interstice/models/block/lift/field_anchor.json", anchor)
    write("src/main/resources/assets/interstice/blockstates/field_anchor.json", {"variants": {"": {"model": "interstice:block/lift/field_anchor"}}})
    write("src/main/resources/assets/interstice/models/item/field_anchor.json", {"parent": "interstice:block/lift/field_anchor"})
    controller = model(textures, [box([6, 0, 6], [10, 9, 10], "wood"), box([4, 9, 4], [12, 15, 12], "body"),
                                 box([5, 11, 3.9], [11, 13, 4.1], "core")])
    write("src/main/resources/assets/interstice/models/item/lift_controller.json", controller)
    write("src/main/resources/data/interstice/loot_table/blocks/field_anchor.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": "interstice:field_anchor"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    craft("field_anchor", ["MVM", "LCL", "PSP"], {"M": "riftsilver_mesh", "V": "vitriolite", "L": "pure_vitriolite_lining",
          "C": "pressure_coupler", "P": "paleheart_planks", "S": "world_stick"})
    craft("lift_controller", [" M ", "WCW", " L "], {"M": "riftsilver_mesh", "W": "world_stick", "C": "pressure_coupler", "L": "pure_vitriolite_lining"})
    write("src/main/resources/data/interstice/advancement/recipes/lift_discovery.json", {"parent": "minecraft:recipes/root",
        "criteria": {"processed_mesh": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": "interstice:riftsilver_mesh"}]}},
                     "has_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": "interstice:field_anchor"}}},
        "requirements": [["processed_mesh", "has_recipe"]], "rewards": {"recipes": ["interstice:field_anchor", "interstice:lift_controller"]}})
    write("tools/fragments/lift_tags.json", {"minecraft:mineable/pickaxe": ["interstice:field_anchor"], "minecraft:needs_iron_tool": ["interstice:field_anchor"]})
    write("tools/fragments/lift_lang.json", {
        "ru_ru": {"block.interstice.field_anchor": "Полевой якорь лифта", "entity.interstice.field_lift": "Грузовая платформа",
                  "item.interstice.lift_controller": "Пульт полевого лифта", "container.interstice.field_lift": "Груз платформы",
                  "tooltip.interstice.field_anchor": "9 ячеек груза, подъём до 48 блоков. Поставь якорь внизу маршрута, отступив от края чанка. Топливо: местный уголь или фосфорит.",
                  "tooltip.interstice.lift_controller": "Shift+ПКМ по якорю: связать. ПКМ: вверх → стоп → вниз → стоп. Shift на платформе — обычный выход; стоя снаружи, Shift+ПКМ отправляет вниз.",
                  "tooltip.interstice.lift_linked": "Пульт связан с якорем; работает в пределах 64 блоков в том же измерении.",
                  "message.interstice.lift.linked": "Пульт связан с этим полевым якорем.",
                  "message.interstice.lift.unavailable": "Лифт недоступен: проверь владельца, топливо, пространство и загрузку якоря.",
                  "message.interstice.lift.status": "Платформа: %s", "message.interstice.lift.fuel": "Лифт: ещё %s секунд движения.",
                  "status.interstice.lift.stopped": "остановлена", "status.interstice.lift.moving": "движется",
                  "status.interstice.lift.obstructed": "путь перекрыт", "status.interstice.lift.empty": "нет топлива",
                  "status.interstice.lift.broken": "нужна чистая облицовка для ремонта", "status.interstice.lift.waiting": "ожидается загрузка платформы"},
        "en_us": {"block.interstice.field_anchor": "Field Lift Anchor", "entity.interstice.field_lift": "Cargo Platform",
                  "item.interstice.lift_controller": "Field Lift Controller", "container.interstice.field_lift": "Platform Cargo",
                  "tooltip.interstice.field_anchor": "9 cargo slots, up to 48 blocks of ascent. Place below the route, away from chunk edges. Fuel: native coal or phosphorite.",
                  "tooltip.interstice.lift_controller": "Sneak-use an anchor to link. Use cycles up → stop → down → stop. Sneak still dismounts; sneak-use while standing outside sends the platform down.",
                  "tooltip.interstice.lift_linked": "Linked controller; works within 64 blocks in the same dimension.",
                  "message.interstice.lift.linked": "Controller linked to this field anchor.",
                  "message.interstice.lift.unavailable": "Lift unavailable: check ownership, fuel, clearance and whether its anchor is loaded.",
                  "message.interstice.lift.status": "Platform: %s", "message.interstice.lift.fuel": "%s seconds of powered movement remaining.",
                  "status.interstice.lift.stopped": "stopped", "status.interstice.lift.moving": "moving",
                  "status.interstice.lift.obstructed": "route obstructed", "status.interstice.lift.empty": "out of fuel",
                  "status.interstice.lift.broken": "repair requires purified lining", "status.interstice.lift.waiting": "waiting for platform load"}})
    write("src/main/resources/assets/interstice/provenance/lift.json", {"format": 1, "generator": "tools/build_realm_lift_resources.py",
          "new_pngs": 0, "existing_textures_unchanged": True, "operation": "Original model geometry and native block-mesh vehicle renderer",
          "textures": sorted(set(textures.values())), "source_registry": "docs/ASSET_LIBRARY.md"})
    print("Created M21 geometry, recipes, loot and fragments; zero PNG edits")


if __name__ == "__main__":
    main()
