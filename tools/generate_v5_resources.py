#!/usr/bin/env python3
"""A new native-density revision. No old dimension, noise settings or image is rewritten."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];DATA=ROOT/'src/main/resources/data/interstice'
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def field(name,settings='minecraft:overworld',offset=0,ys=1.25,yo=20.5):return {'type':'interstice:vanilla_field','settings':settings,'field':name,'xz_scale':1.35,'y_scale':ys,'y_offset':yo,'xz_offset':offset}
def mixer(terrain=False):return {'type':'interstice:vanilla_realm','land':field('terrain' if terrain else 'solid'),'islands':field('solid','minecraft:end',4096,1,-64),'continents':field('continents')}
settings={
 'aquifers_enabled':False,'default_block':{'Name':'interstice:riftstone'},'default_fluid':{'Name':'minecraft:air'},'disable_mob_generation':True,'legacy_random_source':False,
 'noise':{'min_y':0,'height':256,'size_horizontal':1,'size_vertical':2},
 'noise_router':{'barrier':0,'fluid_level_floodedness':0,'fluid_level_spread':0,'lava':0,'temperature':field('temperature'),'vegetation':field('vegetation'),'continents':field('continents'),'erosion':field('erosion'),'depth':field('depth'),'ridges':field('ridges'),'initial_density_without_jaggedness':mixer(True),'final_density':{'type':'minecraft:interpolated','argument':mixer()},'vein_toggle':0,'vein_ridged':0,'vein_gap':0},
 'ore_veins_enabled':False,'sea_level':34,'spawn_target':[],'surface_rule':{'type':'minecraft:block','result_state':{'Name':'interstice:riftstone'}}
}
write(DATA/'worldgen/noise_settings/vanilla_realm_v5.json',settings)
all=[-2,2]
def point(biome,c,h=all,e=all):return {'biome':'interstice:'+biome,'parameters':{'temperature':all,'humidity':h,'continentalness':c,'erosion':e,'depth':all,'weirdness':all,'offset':0}}
biomes=[point('ash_islands',[-2,-.275]),point('pale_gardens',[-.275,2],[-.15,2],[-.20,2]),point('crimson_thickets',[-.275,2],[-2,-.15],[-.20,2]),point('stone_vaults',[-.275,2],all,[-2,-.20])]
dimension=json.loads((DATA/'dimension/islands_v4.json').read_text(encoding='utf-8'))
dimension['generator'].update(terrain_revision=5,settings='interstice:vanilla_realm_v5',biome_source={'type':'minecraft:multi_noise','biomes':biomes})
write(DATA/'dimension/islands_v5.json',dimension)
fixture=ROOT/'src/geometryFixture/resources/data/minecraft/worldgen/world_preset/flat.json'
fixture_data=json.loads(fixture.read_text(encoding='utf-8'));fixture_data['dimensions']['interstice:islands_v5']=dimension;write(fixture,fixture_data)
forest=json.loads((DATA/'worldgen/biome/pale_gardens.json').read_text(encoding='utf-8'))
write(DATA/'worldgen/biome/crimson_thickets.json',forest)
assets=ROOT/'src/main/resources/assets/interstice'
for language,index in [('ru_ru',0),('en_us',1)]:
    path=assets/f'lang/{language}.json';labels=json.loads(path.read_text(encoding='utf-8'))
    for fragment in ['forest_ecology_lang.json','fragments/gear_lang.json','fragments/lift_lang.json','tether_lang.json']:
        source=ROOT/'tools'/fragment
        if source.exists():
            fragment_data=json.loads(source.read_text(encoding='utf-8'));labels.update(fragment_data[language] if language in fragment_data else {k:v[index] for k,v in fragment_data.items()})
    write(path,labels)
for fragment in ['gear_tags.json','lift_tags.json']:
    tags=ROOT/'tools/fragments'/fragment
    if tags.exists():
        for tag,entries in json.loads(tags.read_text(encoding='utf-8')).items():
            namespace,name=tag.split(':');path=ROOT/f'src/main/resources/data/{namespace}/tags/block/{name}.json';data=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace':False,'values':[]};data['values']=list(dict.fromkeys(data['values']+entries));write(path,data)
print('V5 native density, four-biome selector and new dimension written; legacy data untouched')
