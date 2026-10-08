"""Versioned dimension settings and shared integration resources; old generators remain frozen."""
import json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
def write(p,d):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def read(p):return json.loads(p.read_text(encoding='utf-8'))
dimension=read(RES/'data/interstice/dimension/islands_tall.json')
dimension['generator'].update(terrain_revision=2,settings='interstice:living_realm_v2')
write(RES/'data/interstice/dimension/islands_v2.json',dimension)
settings=read(RES/'data/interstice/worldgen/noise_settings/islands_tall.json')
# Actual revision2 density is the canonical numerical adapter. Native climate fields
# still drive biome filling and continuous terrain mixing from the same world seed.
settings['noise_router']['final_density']=0.0
settings['noise_router']['initial_density_without_jaggedness']=0.0
for axis,name in [('continents','realm_geology_v2'),('vegetation','realm_gardens_v2')]:
    settings['noise_router'][axis]={'type':'minecraft:noise','noise':'interstice:'+name,'xz_scale':.45,'y_scale':0}
    write(RES/f'data/interstice/worldgen/noise/{name}.json',{'firstOctave':-7,'amplitudes':[1,.35,.15]})
write(RES/'data/interstice/worldgen/noise_settings/living_realm_v2.json',settings)
v3=json.loads(json.dumps(dimension));v3['generator'].update(terrain_revision=3,settings='interstice:living_realm_v3')
write(RES/'data/interstice/dimension/islands_v3.json',v3)
v3settings=json.loads(json.dumps(settings))
for axis,name in [('continents','realm_geology_v3'),('vegetation','realm_gardens_v3')]:
    v3settings['noise_router'][axis]={'type':'minecraft:noise','noise':'interstice:'+name,'xz_scale':.28,'y_scale':0}
    write(RES/f'data/interstice/worldgen/noise/{name}.json',{'firstOctave':-8,'amplitudes':[1,.35,.15]})
write(RES/'data/interstice/worldgen/noise_settings/living_realm_v3.json',v3settings)
v4=json.loads(json.dumps(v3));v4['generator'].update(terrain_revision=4,settings='interstice:living_realm_v4')
write(RES/'data/interstice/dimension/islands_v4.json',v4)
v4settings=json.loads(json.dumps(v3settings))
for axis,name in [('continents','realm_geology_v4'),('vegetation','realm_gardens_v4')]:
    v4settings['noise_router'][axis]={'type':'minecraft:noise','noise':'interstice:'+name,'xz_scale':.65,'y_scale':0}
    write(RES/f'data/interstice/worldgen/noise/{name}.json',{'firstOctave':-6,'amplitudes':[1,.35,.15]})
write(RES/'data/interstice/worldgen/noise_settings/living_realm_v4.json',v4settings)
fixture=ROOT/'src/geometryFixture/resources/data/minecraft/worldgen/world_preset/flat.json'
d=read(fixture);d['dimensions']['interstice:islands_v2']=dimension;d['dimensions']['interstice:islands_v3']=v3;d['dimensions']['interstice:islands_v4']=v4;write(fixture,d)
for kind in ('block','item'):
    p=RES/f'data/minecraft/tags/{kind}/mineable/pickaxe.json'
    if kind=='block':
        d=read(p);d['values']=list(dict.fromkeys(d['values']+['#interstice:cave_spires']));write(p,d)
labels={
 'ru_ru':{'block.interstice.ash_spire':'Пепельный натёк','block.interstice.garden_spire':'Садовый натёк','block.interstice.vault_spire':'Сводовый натёк',
 'block.interstice.glow_bloom':'Тихосвет','block.interstice.hanging_glow_bloom':'Свисающий тихосвет','block.interstice.sting_frond':'Жалящее перо','block.interstice.clingweed':'Токсичная липучка',
 'message.interstice.clingweed.stolen':'Липучка удержала: %s. Предмет можно вернуть, разрушив растение.',
 'tooltip.interstice.clingweed.passage':'Проходимая завеса: удерживает один предмет раз в 20 секунд.',
 'tooltip.interstice.clingweed.recovery':'Украденное сохраняется в растении и выпадает при разрушении.',
 'tooltip.interstice.clingweed.gas':'При разрушении выпускает ядовитые споры.'},
 'en_us':{'block.interstice.ash_spire':'Ash Dripstone','block.interstice.garden_spire':'Garden Dripstone','block.interstice.vault_spire':'Vault Dripstone',
 'block.interstice.glow_bloom':'Hushlight','block.interstice.hanging_glow_bloom':'Hanging Hushlight','block.interstice.sting_frond':'Stinging Frond','block.interstice.clingweed':'Toxic Clingweed',
 'message.interstice.clingweed.stolen':'Clingweed retained: %s. Break the plant to recover it.',
 'tooltip.interstice.clingweed.passage':'Passable curtain: retains one item every 20 seconds.',
 'tooltip.interstice.clingweed.recovery':'Stolen items remain in the plant and drop on destruction.',
 'tooltip.interstice.clingweed.gas':'Breaking releases toxic spores.'}}
for lang,values in labels.items():
    p=RES/f'assets/interstice/lang/{lang}.json';d=read(p);d.update(values);write(p,d)
for language in ('ru_ru','en_us'):
    p=RES/f'assets/interstice/lang/{language}.json';d=read(p)
    names=['riftsilver_seam','umbral_coal_ore','phosphorite_ore','vitriolite_ore','root_loam','rift_shale','coal_torch','living_torch']
    ru=['Рифтосеребряная жила','Руда сумрачного угля','Фосфоритовая руда','Витриолитовая руда','Корневой суглинок','Рифтовый сланец','Угольный факел','Живой факел']
    d.update({f'block.interstice.{name}':ru[i] if language=='ru_ru' else name.replace('_',' ').title() for i,name in enumerate(names)})
    item_names=['umbral_coal','phosphorite_crystal','vitriolite_shard','world_stick','luminous_bud']
    ru_items=['Сумрачный уголь','Фосфоритовый кристалл','Витриолитовый осколок','Палочка Междуморья','Светящийся бутон']
    d.update({f'item.interstice.{name}':ru_items[i] if language=='ru_ru' else name.replace('_',' ').title() for i,name in enumerate(item_names)})
    extra={'tooltip.interstice.umbral_coal':'Горит вдвое дольше обычного угля.',
           'tooltip.interstice.luminous_bud':'Срезается ножницами с верхушки раскрытого приливного растения.',
           'tooltip.interstice.living_torch':'Свет 13. Спешка I в пределах 4 блоков при прямой видимости; токсины остаются опасными.'} if language=='ru_ru' else {
           'tooltip.interstice.umbral_coal':'Burns twice as long as ordinary coal.',
           'tooltip.interstice.luminous_bud':'Shear the top of an open tide plant to collect it.',
           'tooltip.interstice.living_torch':'Light 13. Haste I within 4 blocks and direct sight; toxins remain dangerous.'}
    d.update(extra);write(p,d)
for name,values in [('mineable/pickaxe',['#interstice:mineral_ores','interstice:rift_shale']),('needs_stone_tool',['#interstice:stone_mineral_ores']),('needs_iron_tool',['#interstice:iron_mineral_ores']),('mineable/shovel',['interstice:root_loam'])]:
    p=RES/f'data/minecraft/tags/block/{name}.json';d=read(p) if p.exists() else {'replace':False,'values':[]}
    d['values']=list(dict.fromkeys(d['values']+values));write(p,d)
# Historical client fixtures explicitly exercise the old profile; production default
# /explore now selects the living realm. Tests never reinterpret saved old chunks.
for name in ('CrownFoodVisualSmoke','FloraVisualSmoke','GardenVisualSmoke','IslandPreview','IslandVisualSmoke','WatchpostVisualSmoke'):
    p=ROOT/f'src/smoke/java/pro/erez/interstice/client/{name}.java'
    p.write_text(p.read_text(encoding='utf-8').replace('"interstice explore"','"interstice explore tall"'),encoding='utf-8')
p=ROOT/'src/smoke/java/pro/erez/interstice/test/RiftGameTests.java'
p.write_text(p.read_text(encoding='utf-8').replace('player.level().dimension().equals(IslandWorld.TALL_WORLD)','player.level().dimension().equals(IslandWorld.LIVING_WORLD)'),encoding='utf-8')
print('Generated isolated terrain revisions2/3/4 and shared translations; historical settings unchanged.')
