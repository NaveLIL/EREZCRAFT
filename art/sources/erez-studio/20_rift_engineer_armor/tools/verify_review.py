from pathlib import Path
import json,hashlib,math,struct,zipfile,datetime
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
read=lambda p:json.loads(p.read_text(encoding='utf-8'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
d=read(ROOT/'source/armor_E12.json');report=read(ROOT/'reports/blender_E12.json');assert report['source_sha256']==sha(ROOT/'source/armor_E12.json')
previous=ROOT.parent/'17_rift_engineer_skin'
assert (previous/'source/avatar_S03.json').read_bytes()==(ROOT/'source/reference/avatar_S03.json').read_bytes()
assert (previous/'source/avatar_atlas_S03.png').read_bytes()==(ROOT/'source/reference/avatar_atlas_S03.png').read_bytes()
rows=[];bounds=[[math.inf]*3,[-math.inf]*3]
for p in d['parts']:
    assert p['bone'] in d['pivots'] and p['slot'] in ('helmet','chestplate','leggings','boots')
    assert p['vertices'] and p['faces']
    for v in p['vertices']:
        assert len(v)==3 and all(math.isfinite(x) for x in v)
        for i in range(3):
            g=v[i]+d['pivots'][p['bone']][i];bounds[0][i]=min(bounds[0][i],g);bounds[1][i]=max(bounds[1][i],g)
    for f in p['faces']:
        assert len(f['indices'])>=3 and len(f['indices'])==len(f['uv'])
        assert all(0<=i<len(p['vertices']) for i in f['indices'])
        assert all(0<=u<=d['atlas']['width'] and 0<=v<=d['atlas']['height'] for u,v in f['uv'])
    rows.append({'name':p['name'],'bone':p['bone'],'slot':p['slot'],'vertices':len(p['vertices']),'faces':len(p['faces'])})
assert len(set(p['name'] for p in d['parts']))==len(d['parts'])
text=next(p for p in d['parts'] if p['name']=='back_hebrew');assert text['text']=='ארז'
assert len([p for p in d['parts'] if p['name'].startswith('chest_star_')])==0
assert len([p for p in d['parts'] if p['name'].startswith('back_star_')])==6
assert report['parts']==len(d['parts']) and report['faces']==sum(len(p['faces']) for p in d['parts'])
assert sha(ROOT/'source/armor_E12_atlas.png')==d['atlas']['sha256']
for row in d['texture_provenance']:
    if 'derived' not in row:continue
    a=Image.open(row['original']).convert('RGBA');b=Image.open(ROOT/'source'/row['derived']).convert('RGBA')
    assert a.size==b.size and a.getchannel('A').tobytes()==b.getchannel('A').tobytes()
for rel,h in report['outputs'].items():assert sha(ROOT/rel)==h,rel

# Inspect GLB structural data: actual renderable meshes and packed image are present.
g=(ROOT/'exports/EREZ_Rift_Engineer_Armor_E12.glb').read_bytes();magic,version,total=struct.unpack_from('<III',g,0);assert magic==0x46546c67 and version==2 and total==len(g)
size,kind=struct.unpack_from('<II',g,12);assert kind==0x4e4f534a
glb=json.loads(g[20:20+size]);assert len(glb['meshes'])==len(d['parts']) and glb.get('images')
assert all('bufferView' in im for im in glb['images'])
verification={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'revision':d['revision'],'passed':True,'parts':len(rows),'faces':report['faces'],'vertices':report['vertices'],
    'authoritative_mesh_sha256':sha(ROOT/'source/armor_E12.json'),'atlas_sha256':d['atlas']['sha256'],'bounds_mc_neutral':bounds,'model_units_per_block':16,
    'native_bone_pivots_preserved':True,'old_source_and_atlas_unchanged':True,'previous_blend_preserved':{'path':str(previous/'assembly/EREZ_Rift_Engineer_S03.blend'),'sha256':sha(previous/'assembly/EREZ_Rift_Engineer_S03.blend')},
    'actual_3d_insignia_and_hebrew':True,'glb_meshes':len(glb['meshes']),'packed_glb_textures':True,'blender':report,'geometry':rows,
    'limits':['Game evidence is recorded separately in reports/native; this file validates authoring assets','Reference is a concept image; likeness requires owner visual review','Blender render uses a mild A-stance; native poses and lighting differ','Iron statistics are a temporary visual-test baseline; no recipes, deployment or launcher changes']}
(ROOT/'reports/review_verification.json').write_text(json.dumps(verification,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:verification[k] for k in ('passed','parts','faces','vertices','old_source_and_atlas_unchanged')}))
