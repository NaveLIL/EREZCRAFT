"""Join authored render layers and inspected collision for a Minecraft import.

Produces per-cell static meshes and independent mechanism endpoint poses.
One mechanism covers a door or a complete gangway with its folding rails.
"""
from pathlib import Path
from collections import defaultdict
import bpy
import hashlib
import json
import math
import runpy
ROOT=Path(__file__).resolve().parents[1]
module=runpy.run_path(str(ROOT/'tools/export_render_cells.py'))
data=module['data'];clip=module['clip'];split=module['split'];faces=module['faces']
render=json.loads((ROOT/'exports/minecraft/ne01_render_cells.json').read_text())
collision=json.loads((ROOT/'exports/minecraft/ne01_collision_cells.json').read_text())
check=json.loads((ROOT/'reports/COLLISION_EXPORT.json').read_text())
assert not check['clearance_failures'] and check['resolution']==16
assert render['geometry_sha256']==collision['geometry_sha256']
assert render['assembly_sha256']==collision['assembly_sha256']
moving={g['id'] for g in data['groups'] if 'axis' in g}
def family(name):return name.split('_RAIL_')[0]
mechanisms=sorted({family(g) for g in moving})
cells={}
def cell(p):
    return cells.setdefault(tuple(p),dict(pos=list(p),opaque=[],glass=[],boxes=[],mechanisms={}))
def mech(c,name):
    return c['mechanisms'].setdefault(name,dict(closed=dict(opaque=[],glass=[],boxes=[]),open=dict(opaque=[],glass=[],boxes=[])))
for c in render['cells']:
    cell(c['pos']).update(opaque=c['opaque'],glass=c['glass'])
for name,entries in collision['groups'].items():
    for c in entries:
        if name=='static':cell(c['pos'])['boxes'].extend(c['boxes'])
        else:
            group,pose=name.rsplit('/',1)
            mech(cell(c['pos']),family(group))[pose]['boxes'].extend(c['boxes'])

for frame,pose in [(1,'closed'),(41,'open')]:
    bpy.context.scene.frame_set(frame);bpy.context.view_layer.update()
    graph=bpy.context.evaluated_depsgraph_get()
    for ob in bpy.data.objects:
        if ob.type!='MESH' or not ob.get('source_part_id') or ob['component'] not in moving:continue
        ev=ob.evaluated_get(graph);me=ev.to_mesh();me.calc_loop_triangles();uv=me.uv_layers.active.data
        layer='glass' if ob['material_id']=='glass' else 'opaque'
        for tri in me.loop_triangles:
            poly=[]
            for li in reversed(list(tri.loops)):
                v=ev.matrix_world@me.vertices[me.loops[li].vertex_index].co;tex=uv[li].uv
                poly.append([float(v.x),float(v.z),float(v.y),float(tex.x),float(tex.y)])
            for p in split(poly,[0,1,2]):
                origin=tuple(math.floor(sum(v[i] for v in p)/len(p)+1e-8) for i in range(3))
                mech(cell(origin),family(ob['component']))[pose][layer].extend(faces(p,ob['material_id'],origin))
        ev.to_mesh_clear()

ordered=[]
for p,c in sorted(cells.items()):
    c['id']=len(ordered)
    c['mechanisms']=[dict(name=name,index=mechanisms.index(name),**poses) for name,poses in sorted(c['mechanisms'].items())]
    ordered.append(c)
assert max(len(c['mechanisms']) for c in ordered)<=3
result=dict(revision=data['revision'],geometry_sha256=render['geometry_sha256'],assembly_sha256=render['assembly_sha256'],
            cells=ordered,mechanisms=mechanisms,materials=data['materials'],native_accepted=False)
out=ROOT/'exports/minecraft/ne01_runtime.json';out.write_text(json.dumps(result,separators=(',',':'))+'\n')
report=dict(revision=data['revision'],cells=len(ordered),mechanisms=len(mechanisms),
            dynamic_cells=sum(bool(c['mechanisms']) for c in ordered),max_mechanisms_in_cell=max(len(c['mechanisms']) for c in ordered),
            collision_resolution=16,geometry_sha256=result['geometry_sha256'],assembly_sha256=result['assembly_sha256'],
            runtime_sha256=hashlib.sha256(out.read_bytes()).hexdigest(),native_accepted=False)
(ROOT/'reports/RUNTIME_EXPORT.json').write_text(json.dumps(report,indent=2)+'\n')
print('RUNTIME_EXPORT',json.dumps(report))
