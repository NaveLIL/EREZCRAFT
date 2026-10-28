"""Reproducible D02 production, service recipes, descriptions and enchantment tags; no PNGs."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources';DATA=RES/'data';ASSETS=RES/'assets/interstice'
SPEC=json.loads((ROOT/'art/designs/engineer_D02.json').read_text(encoding='utf8'))
def read(p):return json.loads(p.read_text(encoding='utf8'))
def write(p,data):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def unlock(name,ingredient):
    write(DATA/f'interstice/advancement/recipes/engineer/{name}.json',{'parent':'minecraft:recipes/root','criteria':{'material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':ingredient}]}},'recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'interstice:'+name}}},'requirements':[['material','recipe']],'rewards':{'recipes':['interstice:'+name]}})
for name,record in SPEC['proposed_recipes'].items():
    if name=='armor_upgrades':continue
    short=name.split(':')[1];ingredients=[{'item':item} for item,n in record['ingredients'].items() for _ in range(n)]
    write(DATA/f'interstice/recipe/{short}.json',{'type':'minecraft:crafting_shapeless','category':'equipment','ingredients':ingredients,'result':{'id':name,'count':record['count']}})
    unlock(short,'interstice:riftsteel_ingot' if short in ('engineer_power_cell','engineer_breathing_cassette') else next(iter(record['ingredients'])))
for row in SPEC['proposed_recipes']['armor_upgrades']:
    name='engineer_'+row['slot'];write(DATA/f'interstice/recipe/{name}.json',{'type':'minecraft:smithing_transform','template':{'item':row['template']},'base':{'item':row['base']},'addition':{'item':row['addition']},'result':{'id':'interstice:'+name,'count':1}});unlock(name,'interstice:engineer_assembly_pack')
for resource,reagents in [('power_cell',['interstice:phosphorite_paste','minecraft:redstone','minecraft:redstone']),('breathing_cassette',['interstice:pure_vitriolite_lining','minecraft:charcoal','interstice:phosphorite_paste'])]:
    name='engineer_'+resource+'_refill';item='interstice:engineer_'+resource
    components={'minecraft:custom_data':{'interstice_engineer_energy_milli':1800000} if resource=='power_cell' else {'interstice_engineer_air_ticks':12000}}
    write(DATA/f'interstice/recipe/{name}.json',{'type':'interstice:engineer_refill','category':'equipment','ingredients':[{'item':x} for x in [item]+reagents],'result':{'id':item,'count':1,'components':components}});unlock(name,item)
for name,texture in {'engineer_assembly_pack':'interstice:item/aegis/part_electronic_circuit_printed','engineer_neuroadapter':'interstice:item/aegis/part_electronic_circuit_integrated','engineer_power_cell':'minecraft:item/copper_ingot','engineer_breathing_cassette':'minecraft:item/iron_ingot'}.items():
    write(ASSETS/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':texture}})
for p in (DATA/'minecraft/tags/item').rglob('*.json'):
    if 'enchantable' not in p.parts and p.stem not in ['head_armor','chest_armor','leg_armor','foot_armor','armor']:continue
    data=read(p);values=data.get('values',[]);changed=False
    for kind in ['helmet','chestplate','leggings','boots']:
        if any(isinstance(v,str) and v in [f'interstice:riftsilver_{kind}',f'interstice:riftsteel_{kind}',f'interstice:aegis_{kind}'] for v in values) and f'interstice:engineer_{kind}' not in values:values.append(f'interstice:engineer_{kind}');changed=True
    if changed:write(p,data)
for tag,kind in [('rift_chest','chestplate'),('rift_boots','boots')]:
    p=DATA/f'interstice/tags/item/enchantable/{tag}.json';d=read(p);value=f'interstice:engineer_{kind}'
    if value not in d['values']:d['values'].append(value);write(p,d)
write(DATA/'interstice/tags/item/enchantable/engineer_helmet.json',{'replace':False,'values':['interstice:engineer_helmet']})
write(DATA/'interstice/tags/damage_type/engineer_inhalation.json',{'replace':False,'values':[]})
write(DATA/'interstice/enchantment/engineer_attunement.json',{'description':{'translate':'enchantment.interstice.engineer_attunement'},'supported_items':'#interstice:enchantable/engineer_helmet','weight':3,'max_level':3,'min_cost':{'base':12,'per_level_above_first':10},'max_cost':{'base':42,'per_level_above_first':10},'anvil_cost':4,'slots':['head'],'effects':{}})
for p in (DATA/'minecraft/tags/enchantment').glob('*.json'):
    d=read(p)
    if 'interstice:rift_ward' in d.get('values',[]) and 'interstice:engineer_attunement' not in d['values']:d['values'].append('interstice:engineer_attunement');write(p,d)
ru={
'item.interstice.engineer_assembly_pack':'Монтажный комплект инженера','item.interstice.engineer_neuroadapter':'Нейроадаптер инженера','item.interstice.engineer_power_cell':'Съёмная ячейка инженера','item.interstice.engineer_breathing_cassette':'Регенеративная дыхательная кассета',
'enchantment.interstice.engineer_attunement':'Согласование','key.interstice.engineer':'Импульс визора / Shift: настройки','menu.interstice.engineer.title':'Разломный инженер','menu.interstice.engineer.button':'Визор',
'menu.interstice.engineer.visor':'Визор','menu.interstice.engineer.night':'Оптика','menu.interstice.engineer.pulse':'Импульс обнаружения','menu.interstice.engineer.settings':'Визор %s · Оптика %s','menu.interstice.engineer.on':'вкл','menu.interstice.engineer.off':'выкл','menu.interstice.engineer.socket.0':'Ячейка','menu.interstice.engineer.socket.1':'Воздух','menu.interstice.engineer.socket.2':'Пояс',
'tooltip.interstice.engineer.role':'Экспедиционный комплект: визор, дыхательный контур и крепление пояса','tooltip.interstice.engineer.cell':'Энергия: %s / 1800','tooltip.interstice.engineer.air':'Дыхательный запас: %s / 600 с','tooltip.interstice.engineer.belt':'Балластный пояс: осталось %s с подъёмной нагрузки','tooltip.interstice.engineer.install':'ПКМ — установить в свободное гнездо надетой брони','message.interstice.engineer.socket':'Нужно свободное гнездо надетого шлема или нагрудника. Откройте «Визор» в инвентаре.',
'tooltip.interstice.engineer.controls':'Настройки и модули. Назначаемая клавиша визора: импульс; Shift — настройки.','tooltip.interstice.engineer.pulse':'Видимые цели до 16 блоков на 3 с; перерыв 12 с; 40 энергии',
'hud.interstice.engineer.air':'Дыхание: %s с · %s','hud.interstice.engineer.sealed':'контур готов','hud.interstice.engineer.unsealed':'контур разомкнут','hud.interstice.engineer.tide':'%s · %s с','hud.interstice.engineer.seas':'Нижнее море: %s м · Верхнее: %s м','hud.interstice.engineer.energy':'Визор: %s / %s','hud.interstice.engineer.tool':'%s · прочность %s','hud.interstice.engineer.off':'Визор выключен / нет питания','hud.interstice.engineer.retort':'Реторта: %s · топливо %s · %s/%s','hud.interstice.engineer.beacon':'Маршрутный маяк: топливо %s с','hud.interstice.engineer.winch':'Лебёдка: занято %s / 2 креплений'
}
en={
'item.interstice.engineer_assembly_pack':'Engineer assembly pack','item.interstice.engineer_neuroadapter':'Engineer neuroadapter','item.interstice.engineer_power_cell':'Removable engineer power cell','item.interstice.engineer_breathing_cassette':'Regenerative breathing cassette','enchantment.interstice.engineer_attunement':'Attunement','key.interstice.engineer':'Visor pulse / Shift: settings','menu.interstice.engineer.title':'Rift engineer','menu.interstice.engineer.button':'Visor','menu.interstice.engineer.visor':'Toggle visor','menu.interstice.engineer.night':'Night vision','menu.interstice.engineer.pulse':'Detection pulse','menu.interstice.engineer.settings':'Visor %s · Optic %s','menu.interstice.engineer.on':'on','menu.interstice.engineer.off':'off','menu.interstice.engineer.socket.0':'Cell','menu.interstice.engineer.socket.1':'Air','menu.interstice.engineer.socket.2':'Belt','tooltip.interstice.engineer.role':'Expedition suit: visor, closed breathing loop and belt mount','tooltip.interstice.engineer.cell':'Energy: %s / 1800','tooltip.interstice.engineer.air':'Breathing reserve: %s / 600 s','tooltip.interstice.engineer.belt':'Ballast belt: %s active lift seconds remaining','tooltip.interstice.engineer.install':'Right-click to install in an empty worn armor socket','message.interstice.engineer.socket':'An empty worn helmet or chest socket is required. Open Visor in inventory.','tooltip.interstice.engineer.controls':'Settings and modules. Configurable visor key: pulse; Shift: settings.','tooltip.interstice.engineer.pulse':'Visible targets within 16 blocks for 3 s; 12 s cooldown; 40 energy','hud.interstice.engineer.air':'Breathing: %s s · %s','hud.interstice.engineer.sealed':'loop ready','hud.interstice.engineer.unsealed':'loop open','hud.interstice.engineer.tide':'%s · %s s','hud.interstice.engineer.seas':'Lower sea: %s m · Upper sea: %s m','hud.interstice.engineer.energy':'Visor: %s / %s','hud.interstice.engineer.tool':'%s · durability %s','hud.interstice.engineer.off':'Visor off / no power','hud.interstice.engineer.retort':'Retort: %s · fuel %s · %s/%s','hud.interstice.engineer.beacon':'Route beacon: %s s fuel','hud.interstice.engineer.winch':'Winch: %s / 2 hooks occupied'}
ru['hud.interstice.engineer.lift']='Подъёмник: %s · топливо %s · цель Y %s'
en['hud.interstice.engineer.lift']='Lift: %s · fuel %s · target Y %s'
for locale,values in [('ru_ru',ru),('en_us',en)]:
    p=ASSETS/f'lang/{locale}.json';d=read(p);d.update(values);write(p,d)
print('ENGINEER_D02_RESOURCES_READY_NO_PNG_CHANGES')
