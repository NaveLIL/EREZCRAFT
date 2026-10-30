"""Import only authored NE01 files; never edits a game profile or Skat assets."""
from pathlib import Path
import hashlib
import math
import json
import shutil
ROOT=Path(__file__).resolve().parents[1]
MODEL=Path('D:/repos/Blender/creations/32_nordhavn_expedition')
runtime=MODEL/'exports/minecraft/ne01_runtime.json'
d=json.loads(runtime.read_text())
source=json.loads((MODEL/'source/ne01.json').read_text())
check=json.loads((MODEL/'reports/COLLISION_EXPORT.json').read_text())
assert check['resolution']==16 and not check['clearance_failures']
assert d['geometry_sha256']==hashlib.sha256((MODEL/'source/ne01.json').read_bytes()).hexdigest()
assert d['assembly_sha256']==hashlib.sha256((MODEL/'assembly/ne01.blend').read_bytes()).hexdigest()
RES=ROOT/'src/main/resources'
assets=RES/'assets/interstice'
models=assets/'models/block/ne01';models.mkdir(parents=True,exist_ok=True)
tex=assets/'textures/block/ne01';tex.mkdir(parents=True,exist_ok=True)
textures={m['name']:'interstice:block/ne01/'+m['name'] for m in d['materials']}
textures['particle']=textures['hull_ivory']
for m in d['materials']:shutil.copy2(MODEL/m['file'],tex/(m['name']+'.png'))
geometry=[];variants={}
for c in d['cells']:
    model=dict(loader='interstice:ne01',textures=textures,opaque=c['opaque'],glass=c['glass'],
               mechanisms=[{k:v for k,v in g.items() if k in ('name','index','closed','open')} for g in c['mechanisms']])
    # Collision data is kept server-side; quads are only client model resources.
    for g in model['mechanisms']:
        for pose in ['closed','open']:g[pose]={k:v for k,v in g[pose].items() if k!='boxes'}
    (models/f'c{c["id"]}.json').write_text(json.dumps(model,separators=(',',':'))+'\n')
    light=15 if any(q['material']=='lamp' for q in c['opaque']) else 0
    geometry.append(dict(id=c['id'],pos=c['pos'],boxes=c['boxes'],light=light,
                         mechanisms=[dict(name=g['name'],index=g['index'],closed=g['closed']['boxes'],open=g['open']['boxes']) for g in c['mechanisms']]))
    variants[f'cell={c["id"]}']={'model':f'interstice:block/ne01/c{c["id"]}'}
states=assets/'blockstates';states.mkdir(parents=True,exist_ok=True)
(states/'ne01_part.json').write_text(json.dumps(dict(variants=variants),separators=(',',':'))+'\n')
for role in ['terminal','drive','generator']:
    (states/f'ne01_{role}.json').write_text(json.dumps(dict(variants={k+',pose='+str(b):v for k,v in variants.items() for b in range(4)}),separators=(',',':'))+'\n')
(states/'ne01_opening.json').write_text(json.dumps(dict(variants={k+',pose='+str(b):v for k,v in variants.items() for b in range(4)}),separators=(',',':'))+'\n')
dest=RES/'data/interstice/ne01';dest.mkdir(parents=True,exist_ok=True)
def mc(point):return [point[0],point[2],point[1]]
terminals=[dict(id=t['id'],screen=mc([t['center_m'][0]+.06,t['center_m'][1],t['center_m'][2]]),anchor=[math.floor(v) for v in mc(t['center_m'])]) for t in source['terminals']]
assert len(terminals)==6 and all(tuple(t['anchor']) in {tuple(c['pos']) for c in geometry} for t in terminals)
controls=[dict(id=c['id'],floor=c['floor_m'],wheel=mc(c['wheel_center_m']),seats=[mc(p) for p in c['seat_centers_m']],screens=[mc(p) for p in c['display_centers_m']]) for c in source['controls']]
(dest/'ne01.json').write_text(json.dumps(dict(revision=d['revision'],geometry_sha256=d['geometry_sha256'],cells=geometry,mechanisms=d['mechanisms'],terminals=terminals,controls=controls,stairs=source['stairs']),separators=(',',':'))+'\n')
manifest=dict(revision=d['revision'],source_project=str(MODEL),runtime_sha256=hashlib.sha256(runtime.read_bytes()).hexdigest(),
              geometry_sha256=d['geometry_sha256'],assembly_sha256=d['assembly_sha256'],
              texture_sha256={m['name']:hashlib.sha256((tex/(m['name']+'.png')).read_bytes()).hexdigest() for m in d['materials']},
              pixels='Original authored NE01 textures; no Minecraft or manufacturer image pixels',
              status='preview import, native whole-vessel acceptance pending')
(assets/'provenance/ne01.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(dict(cells=len(geometry),textures=len(d['materials']),mechanisms=len(d['mechanisms']),native_accepted=False)))
