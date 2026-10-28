from pathlib import Path
import json,hashlib,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
source=ROOT/'source/tools_T01.json';d=json.loads(source.read_text(encoding='utf-8'));b=json.loads((ROOT/'reports/blender.json').read_text());assert b['source_sha256']==sha(source)
assets=ROOT/'exports/assets';items=[]
for name,item in d['models'].items():
    model=json.loads((assets/'interstice/models/item'/(name+'.json')).read_text());assert model==item['model']
    assert b['sets'][name]['mesh_objects']==len(model['elements'])
    for e in model['elements']:
        assert all(-16<=e['from'][i]<e['to'][i]<=32 for i in range(3))
        if 'rotation' in e:assert e['rotation']['axis'] in ('x','y','z') and e['rotation']['angle'] in (-45,-22.5,0,22.5,45)
        for f in e['faces'].values():
            ref=f['texture']
            while ref.startswith('#'):ref=model['textures'][ref[1:]]
            ns,path=ref.split(':',1);assert (assets/ns/'textures'/(path+'.png')).exists()
            assert all(0<=uv<=16 for uv in f['uv'])
    items.append({'item':name,'elements':len(model['elements']),'max_quads':len(model['elements'])*6})
for row in d['texture_provenance']:
    path=Path(d['textures'][row['reference']]);assert sha(path)==row['sha256']
    if row['operation'].startswith('Palette only'):
        before=Image.open(d['textures'][row['source']]).convert('RGBA');after=Image.open(path).convert('RGBA')
        assert before.size==after.size and before.getchannel('A').tobytes()==after.getchannel('A').tobytes()
for name,h in b['outputs'].items():assert sha(ROOT/name)==h
proof={'passed':True,'stage':'Blender visual prototype only','source_sha256':sha(source),'actual_blender_version':b['blender_version'],
 'same_native_model_elements':True,'native_model_limits_checked':True,'resolved_texture_paths':True,'palette_variant_size_alpha_preserved':True,'models':items,
 'owner_approved':False,'game_verified':False,'installed':False,'published':False}
(ROOT/'reports/review_verification.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
backlog=ROOT.parent/'16_expedition_backpacks/source/item_model_backlog.json';queue=json.loads(backlog.read_text(encoding='utf-8'))
queue['queue'][1]['status']='T01 Blender prototypes and before/after rendered; owner visual approval pending; not installed'
queue['queue'][1]['project']='../18_expedition_tools';backlog.write_text(json.dumps(queue,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
with zipfile.ZipFile(ROOT/'exports/expedition_tools_T01_review.zip','w',zipfile.ZIP_DEFLATED) as z:
    for folder in ('source','assembly','preview','reports'):
        for f in (ROOT/folder).rglob('*'):
            if f.is_file() and f.suffix not in ('.blend1',):z.write(f,f.relative_to(ROOT).as_posix())
    for f in assets.rglob('*'):
        if f.is_file():z.write(f,f.relative_to(ROOT).as_posix())
    for f in (ROOT/'exports').glob('*.glb'):z.write(f,f.relative_to(ROOT).as_posix())
    z.write(ROOT/'README.md','README.md')
print(json.dumps(proof))
