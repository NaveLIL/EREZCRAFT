"""Ready-sprite mineral inlays on exact host model geometry; no ore bitmap compositing."""
import copy
import json
from pathlib import Path
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/interstice'
VANILLA=ZipFile(ROOT/'build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar')
HOSTS=('riftstone','palestone','vaultstone','weathered_vaultstone','rift_shale')
MINERALS={'riftsilver_seam':'riftsilver','umbral_coal_ore':'umbral_coal','phosphorite_ore':'phosphorite','vitriolite_ore':'vitriolite'}
TEXTURES='interstice:block/minerals/'

def write(relative, value):
    path=ASSETS/relative
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def read_model(name):
    if ':' not in name: name='minecraft:'+name
    namespace,relative=name.split(':',1)
    if namespace=='interstice':
        return json.loads((ASSETS/'models'/f'{relative}.json').read_text(encoding='utf-8'))
    return json.loads(VANILLA.read(f'assets/{namespace}/models/{relative}.json'))

def resolved_model(name):
    model=read_model(name)
    inherited=resolved_model(model['parent']) if model.get('parent') else {}
    for key,value in model.items():
        if key=='parent': continue
        if key in ('textures','display'):
            inherited.setdefault(key,{}).update(copy.deepcopy(value))
        else: inherited[key]=copy.deepcopy(value)
    return inherited

def inlays():
    # Whole ready16x16 transparent sprites: geometry sets size and position, no painted masks.
    positions=((2,3,4.5),(8,8,5),(4.5,11.5,3))
    result=[]
    for face in ('up','down','north','south','west','east'):
        for u,v,size in positions:
            if face=='up': start,end=[u,16.015,v],[u+size,16.015,v+size]
            elif face=='down': start,end=[u,-.015,v],[u+size,-.015,v+size]
            elif face=='north': start,end=[u,v,-.015],[u+size,v+size,-.015]
            elif face=='south': start,end=[u,v,16.015],[u+size,v+size,16.015]
            elif face=='west': start,end=[-.015,v,u],[-.015,v+size,u+size]
            else: start,end=[16.015,v,u],[16.015,v+size,u+size]
            result.append({'from':start,'to':end,'faces':{face:{'texture':'#mineral','uv':[0,0,16,16],'cullface':face}}})
    return result

for ground in ('root_loam','rift_shale'):
    write(f'blockstates/{ground}.json',{'variants':{'':{'model':f'interstice:block/minerals/{ground}'}}})
    write(f'models/block/minerals/{ground}.json',{'parent':'minecraft:block/cube_all','textures':{'all':TEXTURES+ground}})
    write(f'models/item/{ground}.json',{'parent':f'interstice:block/minerals/{ground}'})

audit=[]
for ore,mineral in MINERALS.items():
    variants={}
    for host in HOSTS:
        source=json.loads((ASSETS/'blockstates'/f'{host}.json').read_text(encoding='utf-8'))['variants']['']
        original=source if isinstance(source,list) else [source]
        mapped=[]
        for index,state in enumerate(original):
            exact=resolved_model(state['model'])
            combined=copy.deepcopy(exact)
            combined['textures']['mineral']=TEXTURES+mineral
            combined.setdefault('elements',[]).extend(inlays())
            combined['render_type']='minecraft:cutout'
            name=f'block/minerals/{ore}_{host}_{index}'
            write(f'models/{name}.json',combined)
            entry=copy.deepcopy(state);entry['model']='interstice:'+name;mapped.append(entry)
            # Any future accidental change to the host geometry/UVs fails regeneration.
            assert combined['elements'][:len(exact['elements'])]==exact['elements']
            assert all(combined['textures'][key]==value for key,value in exact['textures'].items())
            audit.append({'ore':ore,'host':host,'variant':index,'original_host_model':state['model'],'ore_model':'interstice:'+name,
                          'host_geometry_uv_textures_exact':True,'state_transform_and_weight_preserved':{k:v for k,v in state.items() if k!='model'}})
        variants['host='+host]=mapped if isinstance(source,list) else mapped[0]
    write(f'blockstates/{ore}.json',{'variants':variants})
    write(f'models/item/{ore}.json',{'parent':f'interstice:block/minerals/{ore}_riftstone_0'})

for item,texture in {'umbral_coal':'umbral_coal','phosphorite_crystal':'phosphorite','vitriolite_shard':'vitriolite',
                     'world_stick':'world_stick','luminous_bud':'luminous_bud','coal_torch':'coal_torch'}.items():
    write(f'models/item/{item}.json',{'parent':'minecraft:item/generated','textures':{'layer0':TEXTURES+texture}})

write('models/block/minerals/coal_torch.json',{'parent':'minecraft:block/torch','render_type':'minecraft:cutout','textures':{'torch':TEXTURES+'coal_torch'}})
write('models/block/minerals/coal_wall_torch.json',{'parent':'minecraft:block/wall_torch','render_type':'minecraft:cutout','textures':{'torch':TEXTURES+'coal_torch'}})

def living_torch(wall):
    faces={face:{'texture':'#stem','uv':[0,0,2,10]} for face in ('north','south','west','east')}
    faces.update({'up':{'texture':'#end','uv':[7,7,9,9]},'down':{'texture':'#end','uv':[7,7,9,9]}})
    original=read_model('interstice:block/tide_sprout_flower')
    head=[copy.deepcopy(e) for e in original['elements'] if any(face['texture']!='#stalk' for face in e['faces'].values())]
    for part in head:
        for key in ('from','to'):
            part[key][0]=8+(part[key][0]-8)*.45
            part[key][2]=8+(part[key][2]-8)*.45
            part[key][1]=9.5+part[key][1]*.45
    # The stem overlaps the original core base at y11.75; the harvested head never floats.
    parts=[{'from':[7,0,7],'to':[9,12.2,9],'faces':faces}]+head
    if wall:
        for part in parts:
            for key in ('from','to'):
                part[key][0]-=8;part[key][1]+=2.25
            part['rotation']={'origin':[0,2.25,8],'axis':'z','angle':-22.5}
    return {'parent':'minecraft:block/block','ambientocclusion':False,'render_type':'minecraft:cutout',
            'textures':{**original['textures'],'particle':original['textures']['core'],'stem':'interstice:block/garden/paleheart_log',
                        'end':'interstice:block/garden/paleheart_log_top'},'elements':parts}

write('models/block/minerals/living_torch.json',living_torch(False))
write('models/block/minerals/living_wall_torch.json',living_torch(True))
write('models/item/living_torch.json',{'parent':'interstice:block/minerals/living_torch',
                                     'display':{'gui':{'rotation':[20,-35,0],'translation':[0,-1,0],'scale':[1.15,1.15,1.15]}}})
bud=read_model('interstice:block/tide_sprout_flower')
bud['elements']=[e for e in bud['elements'] if any(face['texture']!='#stalk' for face in e['faces'].values())]
for part in bud['elements']:
    for key in ('from','to'):
        part[key][0]=8+(part[key][0]-8)*.8
        part[key][2]=8+(part[key][2]-8)*.8
        part[key][1]=8+(part[key][1]-5)*.8
bud['textures']['particle']=bud['textures']['core']
write('models/block/minerals/luminous_bud.json',bud)
write('models/item/luminous_bud.json',{'parent':'interstice:block/minerals/luminous_bud','display':{'gui':{'rotation':[25,35,0],'scale':[1.2,1.2,1.2]}}})
wall_template=json.loads(VANILLA.read('assets/minecraft/blockstates/wall_torch.json'))
for family in ('coal','living'):
    write(f'blockstates/{family}_torch.json',{'variants':{'':{'model':f'interstice:block/minerals/{family}_torch'}}})
    wall=copy.deepcopy(wall_template)
    for variant in wall['variants'].values():variant['model']=f'interstice:block/minerals/{family}_wall_torch'
    write(f'blockstates/{family}_wall_torch.json',wall)

write('provenance/mineral-models.json',{'generator':'tools/generate_mineral_resources.py',
      'operation':'Exact host cube model, textures, weighted choices and UV orientation retained; whole ready mineral sprites added as surface geometry',
      'ore_hosts':audit,'world_stick':'Ready Assorted Minecraft16x16 stick sprite; palette only',
      'living_torch':'Original TideSprout flower central bud and petal mesh, scaled to .45 on a realm wood stem; exact original UVs and textures retained; inventory bud uses the same original mesh',
      'coal_torch':'Fully original More Blocks torch2 PNG unchanged; native torch/wall model parents with project texture reference'})
print(f'MINERAL_RESOURCES passed {len(MINERALS)} ores x {len(HOSTS)} host families, {len(audit)} exact host variants, two floor/wall torch families and ready item icons.')
