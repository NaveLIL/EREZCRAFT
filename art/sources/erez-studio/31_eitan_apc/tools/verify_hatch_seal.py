"""Finite visibility-ray coverage of the closed roof, not an airtight certification."""
from pathlib import Path
import bpy,json,math,hashlib
from mathutils import Vector
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'source/eitan_E01.json').read_text())
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/eitan_E01.blend'))
bpy.context.scene.frame_set(1);bpy.context.view_layer.update()
vv=[];ff=[];graph=bpy.context.evaluated_depsgraph_get()
for obj in [o for o in bpy.data.objects if o.type=='MESH' and o.get('component')]:
    ev=obj.evaluated_get(graph);mesh=ev.to_mesh();mesh.calc_loop_triangles();offset=len(vv)
    vv.extend([ev.matrix_world@p.co for p in mesh.vertices]);ff.extend([[offset+i for i in t.vertices] for t in mesh.loop_triangles]);ev.to_mesh_clear()
bvh=BVHTree.FromPolygons(vv,ff,all_triangles=True)
centres=[('driver',-.65,-2.08,2.36),('commander',-.64,-.92,2.50),('troop L',-.345,1.50,2.48),('troop R',.345,1.50,2.48)]
miss=[];count=0
for name,x,y,z in centres:
    for dx,dy in [(0,0),(-.15,-.15),(-.15,.15),(.15,-.15),(.15,.15)]:
        start=Vector((x+dx,y+dy,z))
        for degrees in range(0,86,5):
            theta=math.radians(degrees)
            for azimuth in range(0,360,10):
                az=math.radians(azimuth);direction=Vector((math.sin(theta)*math.cos(az),math.sin(theta)*math.sin(az),math.cos(theta)))
                hit,_,_,_=bvh.ray_cast(start,direction,10);count+=1
                if hit is None:miss.append(dict(hatch=name,start=list(start),inclination=degrees,azimuth=azimuth))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
report=dict(revision=data['revision'],passed=not miss,geometry_sha256=sha(ROOT/'source/eitan_E01.json'),assembly_sha256=sha(ROOT/'assembly/eitan_E01.blend'),script_sha256=sha(Path(__file__)),closed_roof_visibility_rays=count,misses=miss,scope='Five virtual viewpoints per lid, upward fan to 85 degrees, 5/10 degree grid. Separate native closed-pose inspection; no continuous sealing or manufacturing certification.')
(ROOT/'reports/HATCH_SEAL.json').write_text(json.dumps(report,indent=2)+'\n')
print('EITAN_HATCH_VISIBILITY',count,len(miss),flush=True)
assert not miss,miss[:12]
