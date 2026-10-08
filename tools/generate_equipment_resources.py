#!/usr/bin/env python3
"""Generate backpack recipes/tags/labels without drawing or editing ready CC0 patterns."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources';A=RES/'assets/interstice';D=RES/'data/interstice'
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def shaped(name,pattern,key,type='minecraft:crafting_shaped'):
    write(D/f'recipe/{name}.json',{'type':type,'category':'equipment','pattern':pattern,'key':{k:{'item':v} for k,v in key.items()},'result':{'id':'interstice:'+name}})
shaped('field_backpack',['FFF','FCF','MLF'],{'F':'interstice:chemotrophic_fabric','C':'minecraft:chest','M':'interstice:riftsilver_mesh','L':'interstice:pure_vitriolite_lining'})
shaped('expedition_backpack',['FMF','LBL','FMF'],{'F':'interstice:chemotrophic_fabric','M':'interstice:riftsilver_mesh','L':'interstice:pure_vitriolite_lining','B':'interstice:field_backpack'},'interstice:backpack_upgrade')
write(D/'recipe/retort_chemotrophic_fabric.json',{'type':'interstice:retort','inputs':[{'ingredient':{'item':'interstice:plant_fiber'},'count':4},{'ingredient':{'item':'interstice:phosphorite_paste'},'count':1}],'outputs':[{'id':'interstice:chemotrophic_fabric','count':2}],'ticks':600})
for name in ['field_backpack','expedition_backpack']:
    write(D/f'advancement/recipes/equipment/{name}.json',{'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'interstice:chemotrophic_fabric'}]}},'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'interstice:'+name}}},'requirements':[['has_material','has_recipe']],'rewards':{'recipes':['interstice:'+name]}})
write(D/'tags/item/backpack_materials.json',{'replace':False,'values':['#c:ores','#c:raw_materials','#c:gems','#c:ingots','interstice:ash_grain','interstice:crimson_root','interstice:plant_fiber','interstice:umbral_coal','interstice:phosphorite_crystal','interstice:vitriolite_shard','interstice:phosphorite','interstice:vitriolite','interstice:aerolite','interstice:pyrolith','interstice:raw_riftsilver','interstice:riftstone','interstice:root_loam']})
labels={
 'item.interstice.field_backpack':('Полевой рюкзак','Field Backpack'),
 'item.interstice.expedition_backpack':('Экспедиционный рюкзак','Expedition Backpack'),
 'item.interstice.chemotrophic_fabric':('Хемотрофная ткань','Chemotrophic Fabric'),
 'key.interstice.open_backpack':('Открыть рюкзак','Open backpack'),
 'key.categories.interstice':('Междуморье','Interstice'),
 'message.interstice.backpack.invalid':('Невозможно открыть: проверьте содержимое рюкзака.','Cannot open: backpack contents need checking.'),
 'tooltip.interstice.backpack.capacity':('Вместимость: %s ячейки.','Capacity: %s slots.'),
 'tooltip.interstice.backpack.used':('Занято ячеек: %s.','Occupied slots: %s.'),
 'tooltip.interstice.backpack.open':('ПКМ — открыть. Shift+ПКМ — надеть; B — открыть носимый.','Use to open. Sneak + use to wear; B opens the worn pack.'),
 'tooltip.interstice.backpack.mode.0':('Автосбор выключен.','Automatic collection is off.'),
 'tooltip.interstice.backpack.mode.1':('Автосбор: только предметы, уже лежащие в рюкзаке.','Collects only items already stored with matching components.'),
 'tooltip.interstice.backpack.mode.2':('Автосбор: сырьё, руды, металлы и урожай; прочее — по образцу.','Collects ores, raw materials, metals and native crops; other items must match stored samples.'),
 'tooltip.interstice.backpack.fire':('Сам рюкзак не горит. Целая сумка защищает груз от огня.','The backpack resists fire and protects its contents while intact.'),
 'menu.interstice.backpack.collect':('Сбор','Auto'),
 'menu.interstice.backpack.mode.0':('Вык','Off'),
 'menu.interstice.backpack.mode.1':('Обр.','Same'),
 'menu.interstice.backpack.mode.2':('Сыр.','Raw'),
 'menu.interstice.backpack.sort':('Сорт.','Sort'),
 'menu.interstice.backpack.stash':('Запасы','Stash'),
 'menu.interstice.backpack.refill':('Панель','Refill'),
 'menu.interstice.backpack.collect_hint':('Переключить режим автосбора. Работает одна сумка: открытая, на спине, в руке, во второй руке или первая в инвентаре.','Cycle collection. One pack works: open, worn, held, offhand, then first in inventory.'),
 'menu.interstice.harness.title':('Экипировка спины','Back Equipment'),
 'menu.interstice.harness.slot':('Рюкзак','Backpack'),
 'menu.interstice.harness.hint':('Один рюкзак; броня сохраняется','One pack; armor stays equipped'),
 'menu.interstice.harness.button':('Спина','Back'),
 'menu.interstice.harness.button_hint':('Надеть или снять рюкзак в отдельной ячейке спины.','Equip or remove a backpack in its own back slot.'),
 'menu.interstice.backpack.sort_hint':('Объединить одинаковые стопки и упорядочить содержимое.','Merge identical stacks and sort the contents.'),
 'menu.interstice.backpack.stash_hint':('Убрать запасы из 27 ячеек инвентаря. Панель быстрого доступа сохраняется.','Move supplies from the 27 inventory slots. Keeps the hotbar unchanged.'),
 'menu.interstice.backpack.refill_hint':('Пополнить существующие стопки на панели из рюкзака. Пустые ячейки не занимать.','Top up existing hotbar stacks from the pack. Leaves empty slots empty.')
}
fragment=ROOT/'tools/retort_ui_lang.json'
if fragment.exists():labels.update(json.loads(fragment.read_text(encoding='utf-8')))
for lang,index in [('ru_ru',0),('en_us',1)]:
    path=A/f'lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8'));data.update({k:v[index] for k,v in labels.items()});write(path,data)
print('Equipment recipes, bounded pickup tag and Russian/English labels generated')
