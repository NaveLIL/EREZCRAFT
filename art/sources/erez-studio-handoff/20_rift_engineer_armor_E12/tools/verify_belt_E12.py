from pathlib import Path
import json,hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
read=lambda p:json.loads(p.read_text(encoding='utf-8'))
new=read(ROOT/'source/armor_E12.json');old=read(ROOT/'source/armor_E11.json');a={p['name']:p for p in old['parts']};b={p['name']:p for p in new['parts']}
assert a.keys()==b.keys()
changed=[n for n in a if a[n]['vertices']!=b[n]['vertices'] or [f['indices'] for f in a[n]['faces']]!=[f['indices'] for f in b[n]['faces']]]
assert all(n=='belt' or n.startswith(('belt_buckle_','belt_tongue','belt_front_pouch_','pouch_flap_','pouch_clasp_','belt_rear_pouch_','rear_pouch_clasp_','belt_back_loop_')) for n in changed),changed
belt=b['belt'];assert belt['role']=='worn_leather'
lo=[min(v[i] for v in belt['vertices']) for i in range(3)];hi=[max(v[i] for v in belt['vertices']) for i in range(3)]
bridges=[]
for p in new['parts']:
    if 'waist_bridge' not in p['name']:continue
    pivot=new['pivots'][p['bone']]
    for v in p['vertices']:
        for i in (0,2):assert lo[i]+.08<v[i]+pivot[i]<hi[i]-.08,p['name']
    assert p['vertices']==a[p['name']]['vertices'];bridges.append(p['name'])
assert len(bridges)==6
for n in ('belt_buckle_top','belt_buckle_bottom','belt_tongue'):assert min(v[2] for v in b[n]['vertices'])<lo[2]-.15,n
tile=Image.open(ROOT/'source/palette_E12_worn_leather.png').convert('RGB');pixels=list(tile.getdata());average=[sum(p[i] for p in pixels)/len(pixels) for i in range(3)];assert average[0]>average[1]>average[2]
report=dict(passed=True,revision='E12',authoritative_mesh_sha256=hashlib.sha256((ROOT/'source/armor_E12.json').read_bytes()).hexdigest(),changed_geometry_parts=changed,cloth_bridge_geometry_unchanged=True,bridges_inside_belt_xz_bounds=True,belt_bounds=[lo,hi],material='worn_leather',mean_material_rgb=average,scope='Static source occlusion and mount placement; native poses verified separately in run_08')
(ROOT/'reports/belt_E12_verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'passed':True,'changed_parts':len(changed),'cloth_unchanged':True}))
