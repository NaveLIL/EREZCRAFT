from pathlib import Path
import json,hashlib,zipfile,sys
ROOT=Path(__file__).resolve().parents[1]
check=json.loads((ROOT/'reports/MODEL_INSPECTION.json').read_text());export=json.loads((ROOT/'reports/RENDER_EXPORT.json').read_text());data=json.loads((ROOT/'source/ne01.json').read_text())
assert check['passed_geometry'] and check['passed_sampled_clearance']
assert check['geometry_sha256']==hashlib.sha256((ROOT/'source/ne01.json').read_bytes()).hexdigest()
assert check['assembly_sha256']==hashlib.sha256((ROOT/'assembly/ne01.blend').read_bytes()).hexdigest()
assert export['source_meshes']==len(data['parts']) and not export['boundary_snap_violations']
assert export['geometry_sha256']==check['geometry_sha256'] and export['assembly_sha256']==check['assembly_sha256']
values={'TOPOLOGY':('nonmanifold_meshes',len(check['nonmanifold_meshes']),'count'),'FACE-AREA':('zero_area_meshes',len(check['zero_area_meshes']),'count'),'WINDOWS':('window_ray_failures',len(check['window_ray_failures']),'count'),'ROUTES':('clearance_failures',len(check['clearance_failures']),'count'),'BERTHS':('berths',len(data['beds']),'count'),'TERMINALS':('terminal_positions',len(data['terminals']),'count'),'MATERIALS':('original_textures',len(data['materials']),'count'),'LAYER-SPLIT':('separate_render_layers',export['opaque_and_glass_separate'],'bool'),'EXPORT-SEAMS':('boundary_snap_violations',len(export['boundary_snap_violations']),'count')}
record=dict(method='inspection',result='pass',observations={id:dict(metric=metric,value=value,unit=unit) for id,(metric,value,unit) in values.items()},dependencies=['reports/MODEL_INSPECTION.json','reports/RENDER_EXPORT.json','assembly/ne01.blend','exports/ne01.glb','exports/minecraft/ne01_render_cells.json'],details=dict(setup='Blender 5.2.2 evaluated mesh, BVH window rays, static body samples and serialized float32 render export; no game test'),limitations=check['limitations'])
(ROOT/'verification/model-inspection.json').write_text(json.dumps(record,indent=2)+'\n')
readme=f"""# NE01 - Nordhavn expedition vessel, {data['revision']}

Authored virtual model based on Nordhavn 96. Four connected levels, interior,
10 modeled berths and 6 reserved terminal locations. Nineteen original texture
images are packed in the Blender file. No Minecraft or manufacturer photo pixels
are reused. These are original art assets, not manufacturer production drawings.

Open assembly/ne01.blend or exports/ne01.glb. Preview images are in preview/.
Frame 1/81: closed; frame 41: prescribed open articulation demonstration.
See docs/assembly.md for reproduction, coordinates and limitations.

The continuous hull, window overlaps, evaluated mesh topology, sampled route
clearance and float32 render export were inspected. Evidence and exact limits
are in reports/MODEL_INSPECTION.json and reports/RENDER_EXPORT.json.
Opaque and transparent surfaces are exported separately.

This package is a MODEL review with isolated native integration evidence.
Four real player stair routes, six actual AE2 terminal screens, finite generator
power and shared cell contents after cold reload are recorded in NATIVE_A06.json.
Physics was paused and body relocation was controlled: propulsion, >=50 km/h,
live navigation displays, sleeping and multiplayer Survival remain open.
No owner worlds or production server were changed.
"""
(ROOT/'README.md').write_text(readme,encoding='utf8')
if '--records-only' in sys.argv:sys.exit(0)
files=[]
for folder,patterns in [('', ['README.md','project.json','release.json']),('source',['*.json','*.md']),('tools',['*.py']),('textures',['*.png']),('assembly',['*.blend']),('exports',['*.glb']),('exports/minecraft',['*.json']),('docs',['*.md','*.csv']),('reports',['MODEL_INSPECTION.json','RENDER_EXPORT.json','BLENDER_BUILD.json','COLLISION_EXPORT.json','RUNTIME_EXPORT.json','NATIVE_A06.json','PREVIEW_RENDER.json','concept.json','concept.html','engineering.json','engineering.html']),('verification',['model-inspection.json','native-a06.json'])]:
    for pattern in patterns:files.extend((ROOT/folder).glob(pattern))
current=json.loads((ROOT/'reports/PREVIEW_RENDER.json').read_text());assert current['revision']==data['revision']
for view,expected in current['views'].items():
    path=ROOT/f'preview/ne01_{view}.png';assert hashlib.sha256(path.read_bytes()).hexdigest()==expected;files.append(path)
manifest_data=json.loads((ROOT/'project.json').read_text())
for evidence in manifest_data['evidence']:files.append(ROOT/evidence['record'])
files=list(dict.fromkeys(files))
manifest={p.relative_to(ROOT).as_posix():dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in files}
(ROOT/'reports/REVIEW_MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
files.append(ROOT/'reports/REVIEW_MANIFEST.json')
out=ROOT/('exports/'+data['revision'].replace('-','_')+'_model_review.zip')
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for p in files:z.write(p,p.relative_to(ROOT).as_posix())
print(json.dumps(dict(archive=str(out),files=len(files),bytes=out.stat().st_size,runtime_accepted=False)))
