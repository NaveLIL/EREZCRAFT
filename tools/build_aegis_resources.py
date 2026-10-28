"""Magen dependency chain, smithing upgrades, discovery and ready-sprite item models."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources';DATA=RES/'data/interstice';ASSETS=RES/'assets/interstice'
def write(path,data):
    # Derived armor miniatures are authored by creations/15_armor_inventory_icons.
    if path.exists() and data.get("loader") != "interstice:armor_icon":
        previous = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(previous, dict) and previous.get("loader") == "interstice:armor_icon":
            return
    path.parent.mkdir(parents=True,exist_ok=True);text=json.dumps(data,ensure_ascii=False,indent=2)+'\n'
    if not path.exists() or path.read_text(encoding='utf-8')!=text:path.write_text(text,encoding='utf-8')
def tag(namespace,kind,path,values):
    p=RES/f'data/{namespace}/tags/{kind}/{path}.json';old=json.loads(p.read_text()) if p.exists() else {'replace':False,'values':[]}
    old['values']=list(dict.fromkeys(old['values']+values));write(p,old)
def unlock(name,ingredient):
    write(DATA/f'advancement/recipes/aegis/{name}.json',{'parent':'minecraft:recipes/root',
        'criteria':{'material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[ingredient]}]}}},
        'requirements':[['material']],'rewards':{'recipes':['interstice:'+name]}})
def craft(name,pattern,key,count=1):
    write(DATA/f'recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,
        'key':{k:{'item':v} for k,v in key.items()},'result':{'id':'interstice:'+name,'count':count}});unlock(name,next(iter(key.values())))
def retort(name,inputs,output,count,ticks):
    write(DATA/f'recipe/{name}.json',{'type':'interstice:retort','inputs':[{'ingredient':{'item':item},'count':amount} for item,amount in inputs],
        'outputs':[{'id':'interstice:'+output,'count':count}],'ticks':ticks})
retort('retort_abyssal_alloy',[('interstice:riftsteel_ingot',8),('minecraft:netherite_ingot',1),('minecraft:echo_shard',2),('interstice:pure_vitriolite_lining',4)],'abyssal_alloy',4,2400)
craft('abyssal_plate',['AAA','ADA','AAA'],{'A':'interstice:abyssal_alloy','D':'minecraft:diamond'})
craft('ceramic_substrate',[' B ','BLB',' B '],{'B':'minecraft:brick','L':'interstice:pure_vitriolite_lining'},2)
retort('retort_etched_circuit_board',[('interstice:riftsilver_wire',4),('interstice:ceramic_substrate',1),('minecraft:redstone',8),('interstice:pure_vitriolite_lining',1)],'etched_circuit_board',2,400)
craft('resonant_processor',['AQA','BEB','RQR'],{'A':'minecraft:amethyst_shard','Q':'minecraft:quartz','B':'interstice:etched_circuit_board','E':'minecraft:echo_shard','R':'minecraft:redstone'})
craft('aegis_optics',['GLG','BPB','FLF'],{'G':'minecraft:glass','L':'interstice:rift_lens','B':'interstice:etched_circuit_board','P':'minecraft:ender_pearl','F':'interstice:phosphorite_paste'})
craft('aegis_control_unit',['ARB','PHO','BRA'],{'A':'interstice:abyssal_alloy','R':'minecraft:redstone_block','B':'interstice:etched_circuit_board','P':'interstice:resonant_processor','H':'minecraft:heart_of_the_sea','O':'interstice:aegis_optics'})
sprites={'abyssal_alloy':'ingot_abyssium','abyssal_plate':'part_plate_abyssium','ceramic_substrate':'part_plate_small_ceramic',
 'etched_circuit_board':'part_electronic_circuit_printed','resonant_processor':'part_electronic_circuit_integrated','aegis_optics':'gem_lens','aegis_control_unit':'part_electronic_magic_circuit_2',
 'aegis_helmet':'armor_helmet_adamantium','aegis_chestplate':'armor_chest_adamantium','aegis_leggings':'armor_legs_adamantium','aegis_boots':'armor_boots_adamantium'}
for name,sprite in sprites.items():write(ASSETS/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'interstice:item/aegis/'+sprite}})
for kind,slot in [('helmet','head'),('chestplate','chest'),('leggings','legs'),('boots','foot')]:
    name='aegis_'+kind
    write(DATA/f'recipe/{name}.json',{'type':'minecraft:smithing_transform','template':{'item':'interstice:aegis_control_unit'},
        'base':{'item':'interstice:riftsteel_'+kind},'addition':{'item':'interstice:abyssal_plate'},'result':{'id':'interstice:'+name}})
    unlock(name,'interstice:abyssal_plate')
    for path in ['enchantable/armor','enchantable/equippable','enchantable/durability','trimmable_armor','dyeable','enchantable/'+slot+'_armor']:
        tag('minecraft','item',path,['interstice:'+name])
    tag('interstice','item','enchantable/'+('rift_chest' if kind=='chestplate' else 'rift_boots'),['interstice:'+name]) if kind in ['chestplate','boots'] else None
tag('c','item','ingots',['interstice:abyssal_alloy']);tag('c','item','ingots/abyssal_alloy',['interstice:abyssal_alloy'])
strings={
 'item.interstice.abyssal_alloy':('Абиссальный композит','Abyssal Composite'),
 'item.interstice.abyssal_plate':('Абиссальная бронепластина','Abyssal Armor Plate'),
 'item.interstice.ceramic_substrate':('Изолирующая керамическая подложка','Insulating Ceramic Substrate'),
 'item.interstice.etched_circuit_board':('Травлёная печатная плата','Etched Circuit Board'),
 'item.interstice.resonant_processor':('Резонансный процессор','Resonant Processor'),
 'item.interstice.aegis_optics':('Оптический модуль «Маген»','Magen Optics'),
 'item.interstice.aegis_control_unit':('Контроллер «Маген»','Magen Control Unit'),
 'item.interstice.aegis_helmet':('Шлем «Маген»','Magen Helmet'),
 'item.interstice.aegis_chestplate':('Нагрудник «Маген»','Magen Cuirass'),
 'item.interstice.aegis_leggings':('Поножи «Маген»','Magen Leggings'),
 'item.interstice.aegis_boots':('Ботинки «Маген»','Magen Boots'),
 'tooltip.interstice.abyssal_alloy':('Реторта: 8 рифтстали + 1 незерит + 2 осколка эха + 4 чистые подкладки → 4 композита; 120 секунд.','Retort: 8 riftsteel + 1 netherite + 2 echo shards + 4 pure linings → 4 composite; 120 seconds.'),
 'tooltip.interstice.abyssal_plate':('8 композитов вокруг алмаза. Кузнечный стол: контроллер + броня из рифтстали + пластина → «Маген».','8 composite around a diamond. Smithing: controller + riftsteel armor + plate → Magen.'),
 'tooltip.interstice.ceramic_substrate':('Изоляция для травлёной платы.','Insulation for an etched circuit board.'),
 'tooltip.interstice.etched_circuit_board':('Реторта: 4 серебряных провода + 1 подложка + 8 редстоуна + 1 чистая подкладка → 2 платы; 20 секунд.','Retort: 4 silver wires + 1 substrate + 8 redstone + 1 pure lining → 2 boards; 20 seconds.'),
 'tooltip.interstice.resonant_processor':('Обрабатывает сигналы оптики и гравитационного поля.','Processes optics and gravity-field signals.'),
 'tooltip.interstice.aegis_optics':('Ночное зрение и локальный сканер существ на 24 блока, в том числе сквозь стены.','Night vision and a local 24-block creature scanner, including through walls.'),
 'tooltip.interstice.aegis_control_unit':('Расходуется при кузнечном усилении. Каждая часть имеет собственный накопитель.','Consumed in smithing upgrades. Each armor piece has its own energy reservoir.'),
 'tooltip.interstice.aegis_armor':('Гравитационный композит. Токсин, укусы и плевки: −12% за часть. Подкладка расходует прочность.','Gravity composite. Toxin, bites and spit: −12% per piece. Lining consumes durability.'),
 'tooltip.interstice.armor.aegis_head':('Активная оптика: постоянное ночное зрение и контуры существ до 24 блоков. Кислород +3.','Active optics: continuous night vision and creature outlines within 24 blocks. Oxygen +3.'),
 'tooltip.interstice.armor.aegis_chest':('Усиленный каркас: +4 сердца максимального здоровья.','Reinforced frame: +4 hearts maximum health.'),
 'tooltip.interstice.armor.aegis_legs':('Разгрузочные сочленения: +10% скорости.','Load-bearing joints: +10% movement speed.'),
 'tooltip.interstice.armor.aegis_feet':('Шаг +0,5 блока; урон падения −60%.','+0.5-block step height; −60% fall damage.'),
 'tooltip.interstice.armor.aegis_set':('Полный комплект: −25% обычного боевого урона. При питании: −40% урона, герметизация от токсина, лавы, огня и утопления; защита от захвата приливом.','Full set: −25% ordinary combat damage. While powered: −40% damage, toxin/lava/fire/drowning sealing and tide-capture protection.'),
 'tooltip.interstice.aegis_charge':('Накопитель: %s%%. Вне Междуморья полного заряда хватает на 2 часа ношения.','Energy: %s%%. A full charge lasts 2 hours of wear outside Interstice.'),
 'tooltip.interstice.aegis_harvest':('В Междуморье питание без расхода. Зарядка: 30 минут; в открытой зоне прилива — 1 минута.','In Interstice: inexhaustible field power. Recharge: 30 minutes, or 1 minute exposed to a surge.'),
 'journal.interstice.armour':('9. Броня\n\nСеребро — походы.\nРифтсталь — бой.\nМаген — силовая.\n\nСтол зачарований\nи наковальня работают.\n\nМаген питается\nполем Междуморья.\nВне его нужен заряд.', '9. Armor\n\nSilver: exploration.\nSteel: heavy combat.\nMagen: power armor.\n\nEnchanting and anvils\nremain available.\n\nField power in Interstice.\nStored charge abroad.'),
 'hud.interstice.aegis.sealed':('Контур замкнут · резерв %s%%','Seals online · reserve %s%%'),
 'hud.interstice.aegis.unsealed':('Контур разомкнут · части/заряд','Seals offline · pieces/charge'),
 'hud.interstice.aegis.realm':('МАГЕН · поле · %s%%','MAGEN · field · %s%%'),
 'hud.interstice.aegis.battery':('МАГЕН · заряд %s%%','MAGEN · charge %s%%'),
 'hud.interstice.aegis.empty':('МАГЕН · заряд исчерпан','MAGEN · charge depleted'),
 'advancements.interstice.aegis.title':('Укрощая аномалию','Taming the Anomaly'),
 'advancements.interstice.aegis.description':('Соберите четыре части «Эгиды»: оптика, броня и энергия Междуморья.','Assemble all four Magen pieces: optics, armor and Interstice power.')}
for locale,index in [('ru_ru',0),('en_us',1)]:
    p=ASSETS/f'lang/{locale}.json';lang=json.loads(p.read_text(encoding='utf-8'));lang.update({k:v[index] for k,v in strings.items()});write(p,lang)
write(DATA/'advancement/research/aegis.json',{'parent':'interstice:trials/master','display':{'icon':{'id':'interstice:aegis_helmet'},
    'title':{'translate':'advancements.interstice.aegis.title'},'description':{'translate':'advancements.interstice.aegis.description'},'frame':'challenge'},
    'criteria':{'assembled':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['interstice:aegis_'+k]} for k in ['helmet','chestplate','leggings','boots']]}}},'requirements':[['assembled']],'rewards':{'experience':250}})
