import bpy,json
from mathutils import Vector
from mathutils.bvhtree import BVHTree
bpy.ops.wm.open_mainfile(filepath='D:/repos/Blender/creations/32_nordhavn_expedition/assembly/ne01.blend')
bpy.context.scene.frame_set(41);bpy.context.view_layer.update()
p=Vector((-.28125,7.34375,1.53125));near=[]
for ob in bpy.data.objects:
    if ob.type!='MESH' or not ob.get('source_part_id'):continue
    ev=ob.evaluated_get(bpy.context.evaluated_depsgraph_get());me=ev.to_mesh();me.calc_loop_triangles()
    tree=BVHTree.FromPolygons([ev.matrix_world@v.co for v in me.vertices],[list(t.vertices) for t in me.loop_triangles],all_triangles=True)
    q,n,i,d=tree.find_nearest(p)
    if d<.35:near.append((ob.name,d,list(q)))
    if ob.name=='Lower transverse 7.35 timber jamb':
        ray=p.copy();hits=[];direction=Vector((1,.0271,.0137)).normalized()
        for _ in range(8):
            hit,n,f,dist=tree.ray_cast(ray,direction,100)
            if hit is None:break
            hits.append([list(hit),dist,list(n)]);ray=hit+direction*.001
        print('JAMB_RAY',json.dumps(hits))
    ev.to_mesh_clear()
print('NEAR_PARTS',json.dumps(sorted(near,key=lambda a:a[1])))
