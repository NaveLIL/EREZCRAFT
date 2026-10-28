from pathlib import Path
import json,hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];design=json.loads((ROOT/'source/worktools_WT09.json').read_text(encoding='utf-8'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
rows=[]
for name,row in design['models'].items():
    model=row['model'];export=ROOT/'exports/WT09/assets/interstice/models/item'/(name+'.json')
    assert json.loads(export.read_text())==model
    assert model['parent']=='minecraft:block/block'
    es=model['elements'];connected={0};remaining=set(range(1,len(es)))
    def touch(a,b):
        # Shared planes count as connected; visual elements must not float.
        return all(min(a['to'][i],b['to'][i])+1e-5>=max(a['from'][i],b['from'][i]) for i in range(3))
    while True:
        add={i for i in remaining if any(touch(es[i],es[j]) for j in connected)}
        if not add:break
        connected|=add;remaining-=add
    assert not remaining,(name,'floating geometry',[es[i]['name'] for i in remaining])
    for e in es:
        assert 'rotation' not in e
        assert all(-16<=a<b<=32 for a,b in zip(e['from'],e['to']))
        for face in e['faces'].values():
            ref=model['textures'][face['texture'][1:]];assert Path(design['textures'][ref]).is_file()
            assert all(0<=v<=16 for v in face['uv'])
    rows.append({'item':name,'elements':len(es),'quads':len(es)*6,'one_connected_assembly':True,'no_rotated_segments':True,'model_sha256':sha(export)})
for row in design['texture_provenance']:
    src=Image.open(row['original']).convert('RGBA');dst=Image.open(design['textures'][row['reference']]).convert('RGBA')
    rgb=tuple(int(row['tint'][i:i+2],16) for i in (1,3,5))
    expected=[tuple(round(c*(.2126*r+.7152*g+.0722*b)/255) for c in rgb)+(a,) for r,g,b,a in src.getdata()]
    assert src.size==dst.size and list(dst.getdata())==expected
b=json.loads((ROOT/'reports/blender_WT09.json').read_text());assert b['source_sha256']==sha(ROOT/'source/worktools_WT09.json')
for name in design['models']:assert b['sets'][name]['mesh_objects']==len(design['models'][name]['model']['elements'])
report={'revision':'WT09','source_sha256':sha(ROOT/'source/worktools_WT09.json'),'passed':True,'geometry':rows,'palette_only_verified':True,'blender_geometry_matches':True,'gameplay_validation':'See GameTests and native reports separately'}
(ROOT/'reports/design_verification_WT09.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'passed':True,'items':len(rows),'maximum_held_quads':max(r['quads'] for r in rows)}))
