from pathlib import Path
import json,math,hashlib,shutil
import numpy as np
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ea01');RES=CAND/'src/main/resources';ASSETS=RES/'assets/interstice'
d=json.loads((ROOT/'source/armor_E12.json').read_text(encoding='utf-8'));records=[]
(ASSETS/'armor').mkdir(exist_ok=True)
shutil.copy2(ROOT/'source/armor_E12.json',ASSETS/'armor/engineer.json')
shutil.copy2(ROOT/'source/armor_E12_atlas.png',ASSETS/'textures/models/armor/engineer.png')
sha=lambda b:hashlib.sha256(b).hexdigest()
atlas=RES/'assets/minecraft/atlases/blocks.json';a=json.loads(atlas.read_text(encoding='utf-8'))
entry={'type':'minecraft:single','resource':'interstice:item/engineer_atlas','sprite':'interstice:armor_icons/engineer'}
if entry not in a['sources']:a['sources'].append(entry)
atlas.write_text(json.dumps(a,indent=2)+'\n',encoding='utf-8')
(ASSETS/'textures/item').mkdir(exist_ok=True);shutil.copy2(ROOT/'source/armor_E12_atlas.png',ASSETS/'textures/item/engineer_atlas.png')
for kind in ('helmet','chestplate','leggings','boots'):
    faces=[]
    for p in d['parts']:
        if p['slot']!=kind:continue
        pivot=d['pivots'][p['bone']]
        for f in p['faces']:
            indices=f['indices'];uv=f['uv']
            if len(indices)==3:indices=[indices[0]]+indices;uv=[uv[0]]+uv
            assert len(indices)==4
            # Y flip changes handedness; reverse winding with each matching UV.
            points=[[p['vertices'][i][0]+pivot[0],-(p['vertices'][i][1]+pivot[1]),p['vertices'][i][2]+pivot[2]] for i in reversed(indices)]
            faces.append({'role':0,'vertices':points,'material':'armor','uv':[[u/d['atlas']['width'],v/d['atlas']['height']] for u,v in reversed(uv)]})
    low=[min(p[i] for f in faces for p in f['vertices']) for i in range(3)];high=[max(p[i] for f in faces for p in f['vertices']) for i in range(3)];center=[(a+b)/2 for a,b in zip(low,high)]
    rx,ry=map(math.radians,(15,155));projected=[]
    for f in faces:
        for point in f['vertices']:
            x,y,z=[point[i]-center[i] for i in range(3)];y,z=math.cos(rx)*y-math.sin(rx)*z,math.sin(rx)*y+math.cos(rx)*z;projected.append((math.cos(ry)*x+math.sin(ry)*z,y))
    span=max(max(p[i] for p in projected)-min(p[i] for p in projected) for i in (0,1));scale=14.5/span
    for f in faces:
        f['vertices']=[[.5+(p[i]-center[i])*scale/16 for i in range(3)] for p in f['vertices']]
        v=np.array(f['vertices'],dtype=np.float32);normal=np.cross(v[1]-v[0],v[2]-v[0])
        assert np.all(np.isfinite(normal)) and np.dot(normal,normal)>1e-24,('Collapsed native icon face',kind)
    name='engineer_'+kind
    model={'loader':'interstice:armor_icon','ambientocclusion':False,'gui_light':'front','textures':{'armor':'interstice:armor_icons/engineer','particle':'interstice:armor_icons/engineer'},'quads':faces,
        'display':{'gui':{'rotation':[15,155,0],'scale':[1,1,1]},'ground':{'translation':[0,2,0],'scale':[.35]*3},'fixed':{'rotation':[0,180,0],'scale':[.8]*3}},
        'erez_source':{'geometry':'armor_E12.json','sha256':sha((ROOT/'source/armor_E12.json').read_bytes()),'slot':kind,'exact_uv':True}}
    file=ASSETS/'models/item'/(name+'.json');file.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf-8');records.append({'item':name,'quads':len(faces),'sha256':sha(file.read_bytes())})
for lang,names in [('ru_ru',['Маска Разломного инженера','Пальто Разломного инженера','Брюки Разломного инженера','Сапоги Разломного инженера']),('en_us',['Rift Engineer Mask','Rift Engineer Coat','Rift Engineer Trousers','Rift Engineer Boots'])]:
    p=ASSETS/'lang'/(lang+'.json');values=json.loads(p.read_text(encoding='utf-8'))
    for kind,title in zip(('helmet','chestplate','leggings','boots'),names):values['item.interstice.engineer_'+kind]=title
    p.write_text(json.dumps(values,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
# Existing enchantment compatibility, without authoring recipes or balanced abilities.
for tag,parts in {'armor':['helmet','chestplate','leggings','boots'],'head_armor':['helmet'],'chest_armor':['chestplate'],'legs_armor':['leggings'],'foot_armor':['boots'],'durability':['helmet','chestplate','leggings','boots'],'equippable':['helmet','chestplate','leggings','boots']}.items():
    p=RES/'data/minecraft/tags/item/enchantable'/(tag+'.json');values=json.loads(p.read_text()) if p.exists() else {'replace':False,'values':[]}
    values['values']=list(dict.fromkeys(values['values']+['interstice:engineer_'+part for part in parts]));p.write_text(json.dumps(values,indent=2)+'\n',encoding='utf-8')
(ROOT/'reports/game_resources.json').write_text(json.dumps({'revision':'E12','icons':records,'wearable_atlas_sha256':sha((ASSETS/'textures/models/armor/engineer.png').read_bytes()),'atlas_matches_blender':True,'balance':'Vanilla iron placeholder for visual acceptance; no special properties or recipes'},indent=2)+'\n',encoding='utf-8')
print('Engineer E12 resources built')
