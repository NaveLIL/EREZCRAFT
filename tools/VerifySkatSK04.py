"""Deterministic game-art seam, glazing, identity and texture checks."""
from pathlib import Path
import json,math,hashlib,zipfile
ROOT=Path(__file__).resolve().parents[1];ART=Path('D:/repos/Blender/creations/25_skat');sha=lambda b:hashlib.sha256(b).hexdigest()
a=json.loads((ART/'source/skat_SK03.json').read_text(encoding='utf8'));b=json.loads((ART/'source/skat_SK04.json').read_text(encoding='utf8'));new={p['name']:p for p in b['parts']}
cubes=[p for p in a['parts'] if p['shape']=='cube' and p.get('rotation',[0,0,0])==[0,0,0] and p['group'] in {'01_Hull_shells','03_Hull_plating_and_ribs','07_Main_cargo_deck','11_Cabin_roof'}]
joined=0
for i,p in enumerate(cubes):
 for q in cubes[i+1:]:
  if p['group']!=q['group'] or p['material']!=q['material']:continue
  for k in range(3):
   if any(abs(p['center'][j]-q['center'][j])>1e-5 for j in range(3) if j!=k):continue
   gap=abs(p['center'][k]-q['center'][k])-(p['size'][k]+q['size'][k])/2
   if .0001<gap<=.035:
    u,v=new[p['name']],new[q['name']];actual=abs(u['center'][k]-v['center'][k])-(u['size'][k]+v['size'][k])/2
    assert abs(actual)<1e-8,(p['name'],q['name'],actual);joined+=1
assert joined==702
panes=[p for p in b['parts'] if p['group']=='10_Cabin_glass'];assert len(panes)==10
max_error=0
for p in panes:
 assert p['shape']=='solid' and len(p['vertices'])==8
 for face in p['faces']:
  v=[p['vertices'][i] for i in face];u=[v[1][i]-v[0][i] for i in range(3)];w=[v[2][i]-v[0][i] for i in range(3)];n=[u[1]*w[2]-u[2]*w[1],u[2]*w[0]-u[0]*w[2],u[0]*w[1]-u[1]*w[0]];length=math.sqrt(sum(t*t for t in n));assert length>1e-6
  error=abs(sum(n[i]*(v[3][i]-v[0][i]) for i in range(3)))/length;max_error=max(max_error,error);assert error<1e-8,p['name']
base=ROOT/'.verification/skat-thrust-release-20261030/interstice-0.7.0-preview.110-skat-thrust.jar';data=json.loads((ROOT/'src/main/resources/data/interstice/skat/sk03.json').read_text())
with zipfile.ZipFile(base) as jar:
 old=json.loads(jar.read('data/interstice/skat/sk03.json'));assert [(c['id'],c['pos']) for c in data['cells']]==[(c['id'],c['pos']) for c in old['cells']]
 assert data['cargo'][:24]==old['cargo']
 textures=[n for n in jar.namelist() if '/textures/' in n and '/skat/' in n and n.endswith('.png')]
 for n in textures:assert (ROOT/'src/main/resources'/n).read_bytes()==jar.read(n),n
provenance=json.loads((ROOT/'src/main/resources/assets/interstice/provenance/skat_sk04.json').read_text())
for p,h in provenance['files'].items():assert sha((ART/p).read_bytes())==h,p
out=dict(passed=True,joined_panel_pairs=joined,planar_glazing_panels=len(panes),maximum_planarity_error_blocks=max_error,stable_cell_ids=len(data['cells']),original_cargo_indices_preserved=24,cargo_containers=len(data['cargo']),original_texture_files_unchanged=len(textures),source_sk03_unchanged=sha((ART/'source/skat_SK03.json').read_bytes())=='fb3b855e297c45e94130d7ae75bcfb8b07b13514b0731a5991406745d1fc16f8',source_sk04_sha256=sha((ART/'source/skat_SK04.json').read_bytes()),units='Minecraft blocks',physical_certification=False)
(ROOT/'.verification/vehicle-release-20261030/skat-geometry.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps(out))
