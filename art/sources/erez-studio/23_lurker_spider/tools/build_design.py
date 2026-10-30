"""LS01: editable, reversible model study; no writes to the game repository."""
from pathlib import Path
import copy, json, hashlib, uuid, shutil

ROOT=Path(__file__).resolve().parents[1]
REV='LS02'
GAME=Path('D:/repos/Minecraft/EREZCRAFT')
ORIGINAL=GAME/'art/sources/cc0/sea_spider/sea_spider.bbmodel'
TEXTURE=GAME/'src/main/resources/assets/interstice/textures/entity/spider/cave_rift_spider_lurker.png'
for name in ['source','assembly','preview','reports','exports']: (ROOT/name).mkdir(exist_ok=True)
original=json.loads(ORIGINAL.read_text(encoding='utf8'))
model=copy.deepcopy(original)
model['name']=f'EREZCRAFT Lurker {REV} - long-legged tarantula study'
groups={g['uuid']:g for g in model['groups']}
nodes={}; owner={}
def visit(n):
    nodes[groups[n['uuid']]['name']]=n
    for c in n['children']:
        if isinstance(c,dict): visit(c)
        else: owner[c]=groups[n['uuid']]['name']
visit(model['outliner'][0])
original_elements={e['uuid']:copy.deepcopy(e) for e in model['elements']}
body_template=copy.deepcopy(model['elements'][0])
abdomen_template=copy.deepcopy(next(e for e in model['elements'] if owner[e['uuid']]=='opisthosoma2'))
leg_template=copy.deepcopy(next(e for e in model['elements'] if owner[e['uuid']]=='left_second_leg2'))
changes=[]
for e in model['elements']:
    name=owner[e['uuid']]
    if 'leg' in name and name not in ['left_legs','right_legs']:
        lengths=[e['to'][i]-e['from'][i] for i in range(3)]
        axis=max(range(3),key=lambda i:lengths[i])
        for i in range(3):
            if i==axis: continue
            center=(e['from'][i]+e['to'][i])/2
            width=3.1 if lengths[i]>1.2 else 1.85
            e['from'][i]=center-width/2; e['to'][i]=center+width/2
        changes.append({'part':name,'longitudinal_axis':axis,'length':lengths[axis],'length_preserved':True})
    elif name=='prosoma': e['from']=[-8,11.5,-8.5];e['to']=[12,22,8.5]
    elif name=='opisthosoma':e['from']=[10.75,11.5,-5];e['to']=[16,19.5,5]
    elif name=='opisthosoma2':e['from']=[14,9,-9.5];e['to']=[33,22,9.5]
    elif name=='proboscis':e['from']=[-9,14,-2];e['to']=[-7,16,2]
    elif 'palp' in name or 'chelifore' in name:
        lengths=[e['to'][i]-e['from'][i] for i in range(3)]
        axis=max(range(3),key=lambda i:lengths[i])
        for i in range(3):
            if i!=axis:
                center=(e['from'][i]+e['to'][i])/2; width=max(lengths[i],2.4)
                e['from'][i]=center-width/2;e['to'][i]=center+width/2

def add(name,group,a,b,template=body_template,uv=None,rotation=None):
    e=copy.deepcopy(template);e['uuid']=str(uuid.uuid5(uuid.NAMESPACE_URL,'erezcraft/'+REV+'/'+name))
    e['name']=name;e['from']=a;e['to']=b;e['box_uv']=False
    if uv:
        for face in e['faces'].values():face['uv']=list(uv)
    if rotation:
        e['origin']=[(a[i]+b[i])/2 for i in range(3)];e['rotation']=rotation
    model['elements'].append(e);nodes[group]['children'].append(e['uuid']);return e

# Stepped cuboids give volume while retaining a Minecraft silhouette.
add('abdomen_upper_rounding','opisthosoma2',[15.5,21,-8],[31.5,24,8],abdomen_template)
add('abdomen_top_rounding','opisthosoma2',[18,23.5,-5.5],[29,25,5.5],abdomen_template)
add('abdomen_lower_rounding','opisthosoma2',[16,7.5,-7.5],[31,10,7.5],abdomen_template)
add('abdomen_rear_cap','opisthosoma2',[32,11,-7.5],[35.5,20,7.5],abdomen_template)
add('abdomen_rear_tip','opisthosoma2',[35,13,-4.5],[37,18,4.5],abdomen_template)
add('carapace_crown','prosoma',[-5.5,21.5,-6.75],[8.5,24,6.75])
add('carapace_left_bevel','prosoma',[-5,13,8],[9,20,10])
add('carapace_right_bevel','prosoma',[-5,13,-10],[9,20,-8])
add('forehead','prosoma',[-10,14,-6.75],[-7,21,6.75])
# A pair of blocky chelicerae and darker hooked tips.
for side,sign in [('left',1),('right',-1)]:
    z=sign*3.1
    add(side+'_fang_base','prosoma',[-13,11,z-2],[-8,17,z+2],leg_template)
    add(side+'_fang_tip','prosoma',[-14.5,9.5,z-.9],[-11.5,12.5,z+.9],leg_template,uv=[80.2,70.2,80.8,70.8])
    # Small compact eye cluster on the anterior upper surface.
    for j,(y,w) in enumerate([(21.2,1.1),(22,0.55)]):
        add(side+'_eye_'+str(j),'prosoma',[-8.9,y-.3,sign*w-.32],[-8.2,y+.3,sign*w+.32],uv=[75.2,75.2,75.8,75.8])
    add(side+'_outer_eye','prosoma',[-8.5,20.75,sign*2.1-.25],[-7.9,21.25,sign*2.1+.25],uv=[75.2,75.2,75.8,75.8])

# Attach small flat bristle clusters to existing animated parts; no fur system.
legs=[e for e in model['elements'][:len(original['elements'])] if 'leg' in owner[e['uuid']]]
for e in legs:
    name=owner[e['uuid']];a=e['from'];b=e['to'];lengths=[b[i]-a[i] for i in range(3)]
    axis=max(range(3),key=lambda i:lengths[i]);cross=2 if axis==0 else 0
    center=[(a[i]+b[i])/2 for i in range(3)]
    if lengths[axis]<10: continue
    # Thin diagonal bristles, clustered rather than regular rectangular pegs.
    for j,fraction in enumerate([.24,.34,.66,.76]):
        for sign in [-1,1]:
            p=center.copy();p[axis]=a[axis]+lengths[axis]*fraction
            extension=1.25 if j%2 else 1.75
            p[cross]=(b[cross] if sign>0 else a[cross])+sign*(extension/2-.15)
            p[1]+=.25 if j%2 else -.2
            sizes=[.16,.16,.16];sizes[axis]=.28;sizes[cross]=extension
            angle=sign*(28 if axis==0 else -28)
            add(name+'_bristle_'+str(j)+'_'+str(sign),name,
                [p[i]-sizes[i]/2 for i in range(3)],
                [p[i]+sizes[i]/2 for i in range(3)],leg_template,uv=[80.2,70.2,80.8,70.8],rotation=[0,angle,0])
for x in [16,18,20,22,24,26,28,30,32]:
    for sign in [-1,1]:
        z=sign*9.9
        add('abdomen_tuft_'+str(x)+'_'+str(sign),'opisthosoma2',
            [x,16.5,z-.65],[x+.28,16.68,z+.65],leg_template,uv=[80.2,70.2,80.8,70.8],rotation=[0,sign*30,0])

# Embed an exact copy of the existing Lurker PNG. No pixel, alpha or palette edits.
import base64
tex=model['textures'][0]
tex.update(name='cave_rift_spider_lurker.png',relative_path='cave_rift_spider_lurker.png',
           source='data:image/png;base64,'+base64.b64encode(TEXTURE.read_bytes()).decode())
shutil.copyfile(TEXTURE,ROOT/'source/cave_rift_spider_lurker.png')
path=ROOT/f'source/lurker_{REV}.bbmodel'
path.write_text(json.dumps(model,indent=2)+'\n',encoding='utf8')
report={'design':REV,'purpose':'Blender/Blockbench model prototype; not installed in Minecraft',
        'variant':'LURKER','minecraft_scale':.85,'source':str(ORIGINAL),
        'source_sha256':hashlib.sha256(ORIGINAL.read_bytes()).hexdigest(),
        'texture_sha256':hashlib.sha256(TEXTURE.read_bytes()).hexdigest(),
        'model_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
        'original_cubes':len(original['elements']),'prototype_cubes':len(model['elements']),
        'groups_identical':model['groups']==original['groups'],
        'animations_identical':model['animations']==original['animations'],
        'leg_lengths':changes,'leg_lengths_all_preserved':all(c['length_preserved'] for c in changes),
        'texture_bytes_identical':TEXTURE.read_bytes()==(ROOT/'source/cave_rift_spider_lurker.png').read_bytes(),
        'license':f'Original sea spider by atcg / CC0-1.0; new {REV} geometry authored for EREZCRAFT',
        'game_verified':False}
assert report['groups_identical'] and report['animations_identical'] and report['texture_bytes_identical']
for e in model['elements'][:len(original['elements'])]:
    if 'leg' in owner[e['uuid']]:
        old=original_elements[e['uuid']];axis=max(range(3),key=lambda i:old['to'][i]-old['from'][i])
        assert old['from'][axis]==e['from'][axis] and old['to'][axis]==e['to'][axis]
(ROOT/f'reports/design_{REV}.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps({k:v for k,v in report.items() if k!='leg_lengths'}))
