"""Import authored E01 geometry for opt-in native inspection, never a live profile."""
from pathlib import Path
import json,hashlib,shutil
ROOT=Path(__file__).resolve().parents[1]
MODEL=Path('D:/repos/Blender/creations/31_eitan_apc')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
runtime=MODEL/'exports/minecraft/eitan_runtime.json';d=json.loads(runtime.read_text())
assert d['geometry_sha256']==sha(MODEL/'source/eitan_E01.json')
assert d['assembly_sha256']==sha(MODEL/'assembly/eitan_E01.blend')
for name in ['MODEL_CHECK.json','ROOF_FIT.json']:
    check=json.loads((MODEL/'reports'/name).read_text())
    assert check['passed'] and check['geometry_sha256']==d['geometry_sha256'] and check['assembly_sha256']==d['assembly_sha256'],name
RES=ROOT/'src/main/resources';AS=RES/'assets/interstice'
models=AS/'models/block/eitan';models.mkdir(parents=True,exist_ok=True)
tex=AS/'textures/block/eitan';tex.mkdir(parents=True,exist_ok=True)
textures={name:'interstice:block/eitan/'+name for name in d['palette']};textures['particle']=textures['olive']
for name in d['palette']:shutil.copy2(MODEL/'exports/minecraft/textures'/(name+'.png'),tex/(name+'.png'))
variants={};geometry=[]
for c in d['cells']:
    (models/f'c{c["id"]}.json').write_text(json.dumps(dict(loader='interstice:eitan_preview',textures=textures,base=c['base'],poses=c['poses']),separators=(',',':'))+'\n')
    for pose in range(4):variants[f'cell={c["id"]},pose={pose}']={'model':f'interstice:block/eitan/c{c["id"]}'}
    light=10 if any(q['material'] in ['lamp','lens'] for q in c['base']) else 0
    geometry.append(dict(id=c['id'],pos=c['pos'],boxes=c['boxes'],pose_boxes=c['pose_boxes'],light=light))
# Remove only this generator's stale numbered models from its dedicated folder.
for old in models.glob('c[0-9]*.json'):
    number=old.stem[1:]
    if number.isdecimal() and int(number)>=len(geometry):
        assert old.resolve().parent==models.resolve()
        old.unlink()
(AS/'blockstates/eitan_preview.json').write_text(json.dumps(dict(variants=variants),separators=(',',':'))+'\n')
dest=RES/'data/interstice/eitan';dest.mkdir(parents=True,exist_ok=True)
(dest/'eitan.json').write_text(json.dumps(dict(revision=d['revision'],geometry_sha256=d['geometry_sha256'],cells=geometry,groups=d['groups'],seats=d['seats'],poses=d['poses']),separators=(',',':'))+'\n')
manifest=dict(revision=d['revision'],source_project=str(MODEL),geometry_sha256=d['geometry_sha256'],assembly_sha256=d['assembly_sha256'],runtime_sha256=sha(runtime),
              cells=len(geometry),materials=len(d['palette']),texture_sha256={n:sha(tex/(n+'.png')) for n in d['palette']},
              pixels='Flat authored source palette; no manufacturer/reference photograph pixels',
              status='Opt-in native geometry preview. Offroad drive, independent smooth mechanisms and seat passengers pending.')
(AS/'provenance/eitan.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest))
