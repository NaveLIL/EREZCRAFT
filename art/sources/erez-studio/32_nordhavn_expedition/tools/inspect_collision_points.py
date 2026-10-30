from pathlib import Path
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[1]
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/ne01.blend'))
bpy.context.scene.frame_set(41);bpy.context.view_layer.update()
points=[Vector((1.8125,-7.5625,4.1875)),Vector((1.5625,-4.1875,4.1875))]
for ob in bpy.data.objects:
    if ob.type!='MESH' or not ob.get('source_part_id'):continue
    ev=ob.evaluated_get(bpy.context.evaluated_depsgraph_get());me=ev.to_mesh();me.calc_loop_triangles()
    verts=[ev.matrix_world@v.co for v in me.vertices]
    tree=BVHTree.FromPolygons(verts,[list(t.vertices) for t in me.loop_triangles],all_triangles=True)
    for p in points:
        if any(p[i]<min(v[i] for v in verts)-.11 or p[i]>max(v[i] for v in verts)+.11 for i in range(3)):continue
        hit,n,idx,dist=tree.find_nearest(p)
        if dist<.11:print('NEAREST',ob.name,tuple(p),dist,tuple(hit),tuple(n),flush=True)
        elif ob.name=='Main structural deck':
            ray=p.copy();direction=Vector((1,.0271,.0137)).normalized();hits=[]
            for _ in range(8):
                h,n,i,d=tree.ray_cast(ray,direction,100)
                if h is None:break
                hits.append((tuple(h),tuple(n),d));ray=h+direction*.00001
            print('PARITY',ob.name,tuple(p),dist,hits,flush=True)
    ev.to_mesh_clear()
