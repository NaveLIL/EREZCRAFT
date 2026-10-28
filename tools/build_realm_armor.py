"""Native armor enchantments and bilingual tooltips; no painted or copied game textures."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    text=json.dumps(data,ensure_ascii=False,indent=2)+'\n'
    if not path.exists() or path.read_text(encoding='utf-8')!=text:path.write_text(text,encoding='utf-8')
def tag(kind,path,values,namespace='interstice'):
    p=RES/f'data/{namespace}/tags/{kind}/{path}.json'
    old=json.loads(p.read_text(encoding='utf-8')) if p.exists() else {'replace':False,'values':[]}
    old['values']=list(dict.fromkeys(old['values']+values));write(p,old)
tag('item','enchantable/rift_chest',[f'interstice:{tier}_chestplate' for tier in ('riftsilver','riftsteel')])
tag('item','enchantable/rift_boots',[f'interstice:{tier}_boots' for tier in ('riftsilver','riftsteel')])
for name,slot,maxlevel,effects in [('rift_ward','chest',3,{}),('surefooted','feet',2,{'minecraft:attributes':[{
    'id':'interstice:enchantment.surefooted','attribute':'minecraft:generic.safe_fall_distance','operation':'add_value',
    'amount':{'type':'minecraft:linear','base':1.0,'per_level_above_first':1.0}}]})]:
    write(RES/f'data/interstice/enchantment/{name}.json',{'description':{'translate':f'enchantment.interstice.{name}'},
        'supported_items':'#interstice:enchantable/'+('rift_chest' if slot=='chest' else 'rift_boots'),
        'weight':3,'max_level':maxlevel,'min_cost':{'base':12,'per_level_above_first':10},
        'max_cost':{'base':42,'per_level_above_first':10},'anvil_cost':4,'slots':[slot],'effects':effects})
    for pool in ('in_enchanting_table','non_treasure','tradeable','on_random_loot','on_traded_equipment'):
        tag('enchantment',pool,['interstice:'+name],namespace='minecraft')
strings={
 'tooltip.interstice.riftsilver_armor':('Экспедиционный сплав: защита уровня незерита. Укусы, плевки и токсин: −4% за часть; подкладка изнашивается.',
    'Expedition alloy: netherite-level defense. Bites, spit and toxin: −4% per piece; lining wears.'),
 'tooltip.interstice.riftsteel_armor':('Тяжёлый сплав: защита выше незерита. Укусы, плевки и токсин: −8% за часть; подкладка изнашивается.',
    'Heavy alloy: above-netherite defense. Bites, spit and toxin: −8% per piece; lining wears.'),
 'tooltip.interstice.armor.silver_head':('Дыхательный резерв: +1 к запасу кислорода.','Breathing reserve: +1 oxygen bonus.'),
 'tooltip.interstice.armor.steel_head':('Дыхательный резерв: +2 к запасу кислорода.','Breathing reserve: +2 oxygen bonus.'),
 'tooltip.interstice.armor.silver_chest':('Гидродинамика: +25% эффективности движения в воде.','Hydrodynamics: +25% water movement efficiency.'),
 'tooltip.interstice.armor.steel_chest':('Усиленный нагрудник: +2 сердца максимального здоровья.','Reinforced cuirass: +2 hearts maximum health.'),
 'tooltip.interstice.armor.silver_legs':('Походная подвижность: +10% скорости.','Expedition mobility: +10% movement speed.'),
 'tooltip.interstice.armor.steel_legs':('Сервоподобная разгрузка: +5% скорости.','Load-bearing joints: +5% movement speed.'),
 'tooltip.interstice.armor.silver_feet':('Походная подошва: шаг +0,5 блока; урон падения −25%.','Trail soles: +0.5-block step height; −25% fall damage.'),
 'tooltip.interstice.armor.steel_feet':('Амортизаторы: шаг +0,5 блока; урон падения −40%.','Shock absorbers: +0.5-block step height; −40% fall damage.'),
 'tooltip.interstice.armor.silver_set':('Полный комплект: +15% скорости разрушения блоков во всех мирах.','Full set: +15% block breaking speed in every dimension.'),
 'tooltip.interstice.armor.steel_set':('Полный комплект: −15% урона, который не обходит броню, во всех мирах.','Full set: −15% damage from sources that do not bypass armor, in every dimension.'),
 'enchantment.interstice.rift_ward':('Защита разлома','Rift Ward'),
 'enchantment.interstice.surefooted':('Твёрдый шаг','Surefooted'),
 'enchantment.interstice.rift_ward.desc':('Нагрудник: дополнительно −6% за уровень от укусов, плевков и токсина Междуморья.','Chestplate: an additional −6% per level against Interstice bites, spit and toxin.'),
 'enchantment.interstice.surefooted.desc':('Обувь: +1 блок безопасного падения за уровень. Совместимо с Невесомостью.','Boots: +1 block of safe falling per level. Compatible with Feather Falling.')}
for locale,index in [('ru_ru',0),('en_us',1)]:
    path=RES/f'assets/interstice/lang/{locale}.json';data=json.loads(path.read_text(encoding='utf-8'))
    data.update({key:values[index] for key,values in strings.items()});write(path,data)

import runpy
runpy.run_path(str(ROOT/"tools/build_aegis_resources.py"),run_name="__main__")
runpy.run_path(str(ROOT/"tools/build_engineer_d02.py"),run_name="__main__")
