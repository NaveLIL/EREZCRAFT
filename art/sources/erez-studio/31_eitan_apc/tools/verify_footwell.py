"""R5: downward rays through the front footwell and the central floor seam."""
from pathlib import Path
import bpy,json,hashlib
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/eitan_E01.blend'))
bpy.context.scene.frame_set(1);bpy.context.view_layer.update()
names=['central floor','sealed fore footwell floor']
def floor(x,y):
    hits=[]
    for name in names:
        obj=bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get());inv=obj.matrix_world.inverted()
        ok,p,n,_=obj.ray_cast(inv@Vector((x,y,.95)),inv.to_3x3()@Vector((0,0,-1)),distance=1)
        if ok:
            world=obj.matrix_world@p;normal=obj.matrix_world.to_3x3()@n
            if normal.z>.99:hits.append(world.z)
    return max(hits) if hits else None
samples=[]
for y in [-3.5,-3.2,-2.8,-2.4]:
    for x in [-1.1,-.8,-.4,0,.4,.8,1.1]:
        z=floor(x,y);assert z is not None and .87<z<.89,(x,y,z);samples.append([x,y,z])
for y in [-2.25,-2.22,-2.21,-2.17]:
    for x in [-.85,-.4,0,.4,.85]:
        z=floor(x,y);assert z is not None and .87<z<.89,(x,y,z);samples.append([x,y,z])
side_samples=[]
for side in [-1,1]:
    obj=bpy.data.objects['front wheelwell liner '+str(side)].evaluated_get(bpy.context.evaluated_depsgraph_get());inv=obj.matrix_world.inverted()
    for y in [-3.5,-3.2,-2.8,-2.4]:
        for z in [.95,1.1,1.25,1.4,1.55]:
            ok,p,_,_=obj.ray_cast(inv@Vector((0,y,z)),inv.to_3x3()@Vector((side,0,0)),distance=2)
            assert ok,(side,y,z)
            world=obj.matrix_world@p;assert .97<abs(world.x)<1.05,(side,y,z,list(world))
            side_samples.append([side,y,z,world.x])
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
report=dict(revision=json.loads((ROOT/'source/eitan_E01.json').read_text())['revision'],passed=True,geometry_sha256=sha(ROOT/'source/eitan_E01.json'),assembly_sha256=sha(ROOT/'assembly/eitan_E01.blend'),script_sha256=sha(Path(__file__)),downward_ray_count=len(samples),side_ray_count=len(side_samples),samples=samples,side_samples=side_samples,scope='Virtual closed footwell and rear seam; native pixels checked separately; no manufacturing claim.')
(ROOT/'reports/FOOTWELL.json').write_text(json.dumps(report,indent=2)+'\n')
print('EITAN_FOOTWELL_PASS',len(samples))
