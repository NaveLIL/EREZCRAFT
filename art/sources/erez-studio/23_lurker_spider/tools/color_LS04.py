"""Palette-only colouring of accepted LS03; existing texels, UV and geometry retained."""
from pathlib import Path
from PIL import Image
import copy,json,hashlib,base64,uuid,math,argparse
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--preset',default='lurker',choices=['lurker','murinus','rufilata','bluefang']);args=parser.parse_args()
PRESETS={
 'lurker':('LS04','lurker','User photograph 2-36816.jpg: dark body, copper/orange knee markings'),
 'murinus':('SK01','skirmisher','Pterinochilus murinus / Orange Baboon Tarantula: orange colour form'),
 'rufilata':('SP01','spitter','Poecilotheria rufilata: olive-green/yellow, dark legs and rusty setae; intensified lime palette'),
 'bluefang':('SP02','spitter','Ephebopus cyanognathus: olive/emerald body, yellow leg bands and blue-violet chelicerae; intensified green palette'),
}
REV,ROLE,REFERENCE=PRESETS[args.preset];STEM=f'{ROLE}_{REV}'
SOURCE=ROOT/'source/lurker_LS03.bbmodel'
base=json.loads(SOURCE.read_text(encoding='utf8'));model=copy.deepcopy(base)
model['name']=f'EREZCRAFT {ROLE} {REV} - {REFERENCE}'
atlas=Image.open(ROOT/'source/cave_rift_spider_lurker.png').convert('RGBA')
original_colors=sorted({p[:3] for p in atlas.getdata() if p[3]},key=lambda p:.2126*p[0]+.7152*p[1]+.0722*p[2])
assert len(original_colors)==5
rank={c:i for i,c in enumerate(original_colors)}
PALETTES={
 'legs':['161316','201b1c','2c2322','3c2d25','4c382b'],
 'abdomen':['1b1716','241d19','30241e','403027','584034'],
 'carapace':['33241c','453024','5a3d2b','735036','8a6344'],
 'knee':['744023','99512a','b76c35','d58b45','e2aa60'],
 'hair':['423024','5c4030','79563b','a4774f','bb9161'],
 'eyes':['080909','0c0d0c','111211','181916','28251d'],
 'fangs':['0d0d0e','141314','1e1b1a','29231f','3a3027'],
}
OVERRIDES={
 'murinus':{
  'legs':['783214','a34418','c85c20','e5792c','f49a47'],
  'abdomen':['68301a','893a1c','ae4e23','ce692d','e88b41'],
  'carapace':['76351b','a74b20','cd6629','e88937','f4a755'],
  'knee':['a64c1c','c56524','de8131','eea04a','f8c66e'],
  'hair':['9a4b20','b96028','d47c36','e69d51','f3ba70'],
 },
 'rufilata':{
  'legs':['17231b','233622','354c29','4e6630','759344'],
  'abdomen':['26331b','3b4923','56632b','758533','a8b64b'],
  'carapace':['32421c','526629','7b9436','a5bf48','cbd867'],
  'knee':['75853a','96aa46','b8cc52','d2df68','e7e999'],
  'hair':['493326','694934','8a6541','b28a59','c9a66f'],
  'fangs':['182b29','25423c','365a4c','4a7255','7a955e'],
 },
 'bluefang':{
  'legs':['102a28','16413b','225f50','348369','55ac7c'],
  'abdomen':['163e29','205e36','368844','55ae54','83ce70'],
  'carapace':['29472b','3c6635','598a43','7aaf55','add578'],
  'knee':['6e7b25','8d9b31','b0bd43','d2db61','e6eb95'],
  'hair':['293f2b','3d5b36','587c45','7ea356','a9c875'],
  'fangs':['20184d','2c2679','3c40ad','5864d8','879cec'],
 },
}
PALETTES.update(OVERRIDES.get(args.preset,{}))
palettes={k:[tuple(bytes.fromhex(c)) for c in colors] for k,colors in PALETTES.items()}
groups={g['uuid']:g for g in model['groups']};nodes={};owner={};leg_depth={};parent={}
def walk(n,p=None,depth=None):
    g=groups[n['uuid']];name=g['name'];nodes[name]=n
    if p in ['left_legs','right_legs']:depth=0
    elif depth is not None:depth+=1
    if depth is not None:leg_depth[name]=depth
    parent[name]=p
    for c in n['children']:
        if isinstance(c,dict):walk(c,name,depth)
        else:owner[c]=name
walk(model['outliner'][0])

# Locate the actual highest leg bend in the accepted rest pose. Front/rear legs
# have one more segment than the middle legs, so fixed segment numbers misplace
# the red knee bands. Match the existing kinematics instead of changing the rig.
idle=next(a for a in base['animations'] if a['name']=='idle');rest={}
def number(v):
    try:return float(str(v).strip())
    except (ValueError,TypeError):return 0.
for channel in idle['animators'].values():
    for k in channel.get('keyframes',[]):
        if k['channel']=='rotation' and float(k['time'])==0:
            rest[channel['name']]=[number(k['data_points'][0].get(c,0)) for c in 'xyz']
world={}
def pose(n,parent_matrix=None,parent_origin=None):
    g=groups[n['uuid']];name=g['name'];o=g['origin'];p=parent_origin or [0,24,0]
    angles=[math.radians(g['rotation'][i]+rest.get(name,[0,0,0])[i])*(-1 if i!=1 else 1) for i in range(3)]
    x,y,z=angles;cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(z),math.sin(z)
    rx=np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]]);ry=np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]]);rz=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])
    local=np.eye(4);local[:3,:3]=rz@ry@rx;local[:3,3]=[o[0]-p[0],-(o[1]-p[1])-(5 if parent_origin is None else 0),o[2]-p[2]]
    world[name]=(parent_matrix if parent_matrix is not None else np.eye(4))@local
    for child in n['children']:
        if isinstance(child,dict):pose(child,world[name],o)
pose(model['outliner'][0])
knee_bones={};knee_locations=[]
for start in [name for name,p in parent.items() if p in ['left_legs','right_legs']]:
    chain=[];node=nodes[start]
    while True:
        chain.append(groups[node['uuid']]['name'])
        children=[c for c in node['children'] if isinstance(c,dict)]
        if not children:break
        node=children[0]
    index=min(range(1,len(chain)-1),key=lambda i:world[chain[i]][1,3])
    knee_bones[chain[index-1]]=('end',chain[index]);knee_bones[chain[index]]=('start',chain[index])
    knee_locations.append({'leg':start,'knee':chain[index],'incoming':chain[index-1],
                           'rest_position_model_units':world[chain[index]][:3,3].tolist()})

# Same face corner convention as the native Blender review importer.
FACES={
 'north':[(1,1,0),(1,0,0),(0,0,0),(0,1,0)],
 'south':[(0,1,1),(0,0,1),(1,0,1),(1,1,1)],
 'west':[(0,1,0),(0,0,0),(0,0,1),(0,1,1)],
 'east':[(1,1,1),(1,0,1),(1,0,0),(1,1,0)],
 'up':[(0,1,0),(0,1,1),(1,1,1),(1,1,0)],
 'down':[(0,0,1),(0,0,0),(1,0,0),(1,0,1)]}
model['textures']=[];material_roles={};cache={};texture_reports=[]
def make_texture(key,role,knee_element=None,knee_coord=None,band=None,axis=None):
    if key in cache:return cache[key]
    image=atlas.copy();pixels=image.load();src=atlas.load()
    for y in range(128):
        for x in range(128):
            p=src[x,y]
            if p[3]:pixels[x,y]=(*palettes[role][rank[p[:3]]],p[3])
    copper_pixels=set()
    if knee_element:
        e=knee_element
        # Recolour existing opaque texels near the physical joint on each face.
        # This never adds/deletes pixels, rotates artwork or changes UV coordinates.
        for side,face in e['faces'].items():
            u,v,uu,vv=face['uv']
            if u==uu or v==vv:continue
            corners=list(reversed(FACES[side]));bits=[c[axis] for c in corners]
            for y in range(max(0,int(min(v,vv))),min(128,int(max(v,vv)+.999))):
                for x in range(max(0,int(min(u,uu))),min(128,int(max(u,uu)+.999))):
                    if not src[x,y][3]:continue
                    s=(x+.5-u)/(uu-u);t=(y+.5-v)/(vv-v)
                    bit=(1-s)*(1-t)*bits[0]+s*(1-t)*bits[1]+s*t*bits[2]+(1-s)*t*bits[3]
                    coordinate=e['from'][axis]+bit*(e['to'][axis]-e['from'][axis])
                    if abs(coordinate-knee_coord)<=band:copper_pixels.add((x,y))
        for x,y in copper_pixels:
            p=src[x,y];pixels[x,y]=(*palettes['knee'][rank[p[:3]]],p[3])
    assert image.size==atlas.size and image.getchannel('A').tobytes()==atlas.getchannel('A').tobytes()
    path=ROOT/f'source/{STEM}_{key}.png';image.save(path)
    tex=copy.deepcopy(base['textures'][0]);index=len(model['textures'])
    tex.update(name=path.name,relative_path=path.name,id=str(index),
               uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,f'erezcraft/{REV}/palette/'+key)),
               source='data:image/png;base64,'+base64.b64encode(path.read_bytes()).decode())
    model['textures'].append(tex);cache[key]=index
    material_roles[str(index)]={'role':role,'roughness':.24 if role=='eyes' else .38 if role=='fangs' else .9,
                                'specular':.6 if role=='eyes' else .26 if role=='fangs' else .14}
    texture_reports.append({'file':path.name,'role':role,'copper_texel_count':len(copper_pixels),
                            'size':[128,128],'alpha_identical':True,'original_pixel_structure_preserved':True,
                            'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    return index

leg_assignments=[]
for e in model['elements']:
    name=e['name'];group=owner[e['uuid']]
    if '_bristle_' in name or name.startswith('abdomen_tuft_'):idx=make_texture('hair','hair')
    elif '_eye_' in name or '_outer_eye' in name:idx=make_texture('eyes','eyes')
    elif 'fang' in name:idx=make_texture('fangs','fangs')
    elif group in leg_depth and name=='cube':
        depth=leg_depth[group];a=e['from'];b=e['to'];axis=max(range(3),key=lambda i:b[i]-a[i]);coord=None;band=None
        if group in knee_bones:
            end,knee_name=knee_bones[group]
            coord=groups[nodes[knee_name]['uuid']]['origin'][axis]
            band=4.1 if end=='end' else 2.65
        if coord is None:idx=make_texture('legs','legs')
        else:
            signature=json.dumps({'uv':[f['uv'] for f in e['faces'].values()],
                                  'a':a,'b':b,'axis':axis,'joint':coord,'band':band},sort_keys=True)
            key='knee_'+hashlib.sha256(signature.encode()).hexdigest()[:8]
            idx=make_texture(key,'legs',e,coord,band,axis)
        leg_assignments.append({'group':group,'depth':depth,'texture':idx,'joint_coordinate':coord,'band_width':band})
    elif group.startswith('opisthosoma'):idx=make_texture('abdomen','abdomen')
    elif 'chelifore' in group and args.preset=='bluefang':idx=make_texture('fangs','fangs')
    elif 'palp' in group or 'chelifore' in group:idx=make_texture('legs','legs')
    else:idx=make_texture('carapace','carapace')
    for face in e['faces'].values():
        if face.get('texture') is not None:face['texture']=idx

model['erezcraft_material_roles']=material_roles
def geometry_only(d):
    result=copy.deepcopy(d)
    for k in ['name','textures','erezcraft_material_roles']:result.pop(k,None)
    for e in result['elements']:
        for face in e['faces'].values():
            if face.get('texture') is not None:face['texture']=0
    return result
assert geometry_only(model)==geometry_only(base)
dest=ROOT/f'source/{STEM}.bbmodel';dest.write_text(json.dumps(model,indent=2)+'\n',encoding='utf8')
report={'design':REV,'baseline':'LS03','source':str(SOURCE),'geometry_identical_to_LS03':True,
        'eyes_positions_identical':True,'groups_identical':True,'animations_identical':True,'uv_identical':True,
        'leg_lengths_all_preserved':True,'leg_lengths':json.loads((ROOT/'reports/design_LS03.json').read_text())['leg_lengths'],
        'prototype_cubes':len(model['elements']),'texture_bytes_identical':False,'texture_alpha_and_size_identical':True,
        'role':ROLE,'preset':args.preset,'palette_reference':REFERENCE,
        'variant_scale':.6 if ROLE=='skirmisher' else .55 if ROLE=='spitter' else .85,
        'original_palette':original_colors,'palettes':PALETTES,'textures':texture_reports,'leg_assignments':leg_assignments,
        'knee_locations':knee_locations,
        'model_sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'game_verified':False}
(ROOT/f'reports/design_{REV}.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps({'revision':REV,'geometry_identical':True,'textures':len(model['textures']),'knee_segments':sum(x['joint_coordinate'] is not None for x in leg_assignments)}))
