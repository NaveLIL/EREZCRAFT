"""Import the authored Eitan driving asset; never edits installed profiles."""
from pathlib import Path
import json,hashlib
ROOT=Path(__file__).resolve().parents[1]
MODEL=Path('D:/repos/Blender/creations/31_eitan_apc')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
src=MODEL/'exports/minecraft/eitan_vehicle.json';d=json.loads(src.read_text())
assert d['geometry_sha256']==sha(MODEL/'source/eitan_E01.json')
assert d['assembly_sha256']==sha(MODEL/'assembly/eitan_E01.blend')
RES=ROOT/'src/main/resources';AS=RES/'assets/interstice'
out=AS/'models/block/eitan_vehicle';out.mkdir(parents=True,exist_ok=True)
palette=json.loads((MODEL/'source/eitan_E01.json').read_text())['palette'];palette['rubber_dust']='#535447'
textures={n:'interstice:block/eitan/'+n for n in palette};textures['particle']=textures['olive']
for n in ['lamp','lens','screen']:textures['unlit_'+n]=textures[n]
lookup={tuple(c['pos']):c for c in d['cells']}
def model(name,quads,emits=True):
    if not emits:quads=[dict(q,material='unlit_'+q['material']) if q['material'] in ['lamp','lens','screen'] else q for q in quads]
    (out/(name+'.json')).write_text(json.dumps(dict(loader='interstice:eitan_vehicle',textures=textures,quads=quads,emissive=emits),separators=(',',':'))+'\n')
def states(name,variants):
    (AS/'blockstates'/(name+'.json')).write_text(json.dumps(dict(variants=variants),separators=(',',':'))+'\n')
def loc(name):return {'model':'interstice:block/eitan_vehicle/'+name}
variants={};cells=[]
for c in d['cells']:
    for lit in [False,True]:
        model(f'c{c["id"]}_{int(lit)}',c['base'],lit)
        for motion in [False,True]:variants[f'cell={c["id"]},lit={str(lit).lower()},motion={str(motion).lower()}']=loc(f'c{c["id"]}_{int(lit)}')
    light=12 if any(q['material'] in ['lamp','lens'] for q in c['base']) else 0
    cells.append({**{k:c[k] for k in ['id','pos','boxes','groups']},'light':light})
states('eitan_part',variants)
control=lookup[tuple(d['controller'])]
states('eitan_controller',{f'lit={str(lit).lower()},motion={str(motion).lower()}':loc(f'c{control["id"]}_{int(lit)}') for lit in [False,True] for motion in [False,True]})
wheel_variants={}
for mount in d['mounts']:
    c=lookup[tuple(mount['pos'])]
    for facing in ['north','south','east','west']:
        for motion in [False,True]:wheel_variants[f'facing={facing},slot={mount["slot"]},motion={str(motion).lower()}']=loc(f'c{c["id"]}_1')
states('eitan_wheel_mount',wheel_variants)
names=[]
for i,b in enumerate(d['bones']):
    name='bone'+str(i);model(name,b['quads']);names.append(dict(name=b['name'],model='interstice:block/eitan_vehicle/'+name))
dest=RES/'data/interstice/eitan';dest.mkdir(parents=True,exist_ok=True)
server={k:v for k,v in d.items() if k not in ['cells','bones']};server['cells']=cells;server['bones']=names
(dest/'vehicle.json').write_text(json.dumps(server,separators=(',',':'))+'\n')
# Game mass follows the occupied shell volume. These are balance values, not
# measurements of the real vehicle. No buoyancy is assigned to this land chassis.
props=RES/'data/interstice/physics_block_properties';props.mkdir(exist_ok=True)
def mass(c):return max(.005,sum((b[3]-b[0])*(b[4]-b[1])*(b[5]-b[2]) for b in c['boxes'])*1.2)
(props/'eitan_part.json').write_text(json.dumps(dict(selector='interstice:eitan_part',properties={'sable:mass':.005,'sable:volume':0},overrides={f'cell={c["id"]}':{'sable:mass':round(mass(c),7),'sable:volume':0} for c in cells}),separators=(',',':'))+'\n')
(props/'eitan_controller.json').write_text(json.dumps(dict(selector='interstice:eitan_controller',properties={'sable:mass':mass(control)+1.,'sable:volume':0}))+'\n')
(props/'eitan_wheel_mount.json').write_text(json.dumps(dict(selector='interstice:eitan_wheel_mount',properties={'sable:mass':.1,'sable:volume':0}))+'\n')
(AS/'models/item/eitan_blueprint.json').write_text(json.dumps(dict(parent='minecraft:item/generated',textures={'layer0':'minecraft:item/paper'}))+'\n')
(AS/'models/item/eitan_tire.json').write_text(json.dumps(dict(parent='minecraft:item/generated',textures={'layer0':'minecraft:item/black_dye'}))+'\n')
manifest=dict(geometry_sha256=d['geometry_sha256'],assembly_sha256=d['assembly_sha256'],runtime_sha256=sha(src),revision=d['revision'],runtime_revision=d['runtime_revision'],cells=len(cells),bones=len(names),monitors=len(d['monitors']),
              materials='Unchanged authored E01 palette; no manufacturer image pixels',native_drive_accepted=False)
(AS/'provenance/eitan_vehicle.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest))
