"""Validate derived meshes and generate volume-aware Sable properties from collision cells.

Volumes use a bounded 16^3 union raster; mass=0.08*volume is an explicit gameplay
parameter in Sable units, not a measured real material density. Cargo contents
do not change it. Native gameplay tests must still determine flotation/stability.
"""
from pathlib import Path
import json, hashlib, math
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources'
data=json.loads((RES/'data/interstice/skat/sk03.json').read_text())
counts={};quads=0;volume=0; properties={}; positions=set()
for c in data['cells']:
    assert tuple(c['pos']) not in positions;positions.add(tuple(c['pos']))
    counts[c['role']]=counts.get(c['role'],0)+1
    model=json.loads((RES/f"assets/interstice/models/block/skat/c{c['id']}.json").read_text())
    for q in model['quads']:
        assert len(q['vertices'])==4 and len(q['uv'])==4
        assert all(math.isfinite(v) and -1e-5<=v<=1.00001 for p in q['vertices'] for v in p),c['id']
        assert all(0<=v<=1 for p in q['uv'] for v in p)
        a,b,d=q['vertices'][:3];u=[b[i]-a[i] for i in range(3)];v=[d[i]-a[i] for i in range(3)]
        cross=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
        assert sum(x*x for x in cross)>1e-18,('Degenerate serialized face',c['id'])
    quads+=len(model['quads'])
    occupied=set()
    for b in c['boxes']+c.get('closed_boxes',[]):
        assert all(0<=v<=1 for v in b),c['id']
        for y in range(max(0,math.floor(b[1]*16)),min(16,math.ceil(b[4]*16))):
            for z in range(max(0,math.floor(b[2]*16)),min(16,math.ceil(b[5]*16))):
                for x in range(max(0,math.floor(b[0]*16)),min(16,math.ceil(b[3]*16))):occupied.add((x,y,z))
    v=len(occupied)/4096;volume+=v
    role=c['role'];block='skat_'+role
    if role in ['propeller','engine']:continue
    properties.setdefault(block,{})['cell='+str(c['id'])]={'sable:mass':round(max(.001,.08*v),6),'sable:volume':round(v,6)}
assert counts['cargo']==24 and counts['engine']==2 and counts['propeller']==2 and counts['seat']==2 and counts['helm']==1
for block,overrides in properties.items():
    path=RES/f'data/interstice/physics_block_properties/{block}.json';path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps({'selector':'interstice:'+block,'properties':{'sable:mass':.1,'sable:volume':.1},'overrides':overrides},separators=(',',':'))+'\n')
for role in ['propeller','engine']:
    (RES/f'data/interstice/physics_block_properties/skat_{role}.json').write_text(json.dumps({'selector':'interstice:skat_'+role,'properties':{'sable:mass':1.0,'sable:volume':1.0}})+'\n')
out=ROOT/'.verification/skat-20261029/geometry.json';out.parent.mkdir(parents=True,exist_ok=True)
report={'passed':True,'source_meshes':data['source_meshes'],'cells':len(data['cells']),'roles':counts,'quads':quads,'collision_volume_16voxels':volume,'mass_parameter':.08,'runtime_verified':False}
out.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
