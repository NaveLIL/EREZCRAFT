"""Read native evaluated meshes and prepare layer-separated Minecraft render cells.
No game registration or collision approximation is implied by this render export.
"""
from pathlib import Path
import bpy,json,math,hashlib,struct
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'source/ne01.json').read_text());bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/ne01.blend'))
assert bpy.context.scene['revision']==data['revision'];bpy.context.scene.frame_set(1);bpy.context.view_layer.update()
OUT=ROOT/'exports/minecraft';OUT.mkdir(parents=True,exist_ok=True)
cells={};dynamic={};discarded=0;max_error=0.;seam_violations=[];source_meshes=0
moving={g['id'] for g in data['groups'] if 'axis' in g}
def clip(poly,axis,bound,sign):
    result=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        da=(a[axis]-bound)*sign;db=(b[axis]-bound)*sign
        if da>=-1e-9:result.append(a)
        if da>1e-9 and db< -1e-9 or da< -1e-9 and db>1e-9:
            t=da/(da-db);v=[x+(y-x)*t for x,y in zip(a,b)];v[axis]=bound;result.append(v)
    return result
def split(poly,axes):
    result=[poly]
    for axis in axes:
        out=[]
        for p in result:
            lo=math.floor(min(a[axis] for a in p)+1e-8);hi=max(lo,math.floor(max(a[axis] for a in p)-1e-8))
            for n in range(lo,hi+1):
                q=clip(clip(p,axis,n,1),axis,n+1,-1)
                if len(q)>=3:out.append(q)
        result=out
    return result
def f32(n):return struct.unpack('<f',struct.pack('<f',n))[0]
def faces(poly,material,origin):
    global discarded,max_error
    out=[]
    for p in split(poly,[3,4]):
        uvbase=[math.floor(sum(v[i] for v in p)/len(p)) for i in [3,4]]
        for k in range(1,len(p)-1):
            tri=[p[0],p[k],p[k+1]];v=[]
            for point in tri:
                local=[]
                for axis in range(3):
                    x=point[axis]-origin[axis]
                    if abs(x)<1e-7:x=0.
                    elif abs(x-1)<1e-7:x=1.
                    else:x=round(x,6)
                    x=f32(x);local.append(x);max_error=max(max_error,abs(origin[axis]+x-point[axis]))
                    if point[axis]==origin[axis] and x!=0 or point[axis]==origin[axis]+1 and x!=1:seam_violations.append([origin,axis,point[axis],x])
                v.append(local)
            if (Vector(v[1])-Vector(v[0])).cross(Vector(v[2])-Vector(v[0])).length<1e-10:discarded+=1;continue
            uv=[[round(max(0,min(1,point[i]-uvbase[i-3])),7) for i in [3,4]] for point in tri]
            out.append(dict(material=material,vertices=v+[v[-1]],uv=uv+[uv[-1]]))
    return out
for ob in bpy.data.objects:
    if ob.type!='MESH' or not ob.get('source_part_id'):continue
    source_meshes+=1;ev=ob.evaluated_get(bpy.context.evaluated_depsgraph_get());me=ev.to_mesh();me.calc_loop_triangles();uv=me.uv_layers.active.data
    layer='glass' if ob['material_id']=='glass' else 'opaque'
    for tri in me.loop_triangles:
        poly=[]
        for li in reversed(list(tri.loops)):
            v=ev.matrix_world@me.vertices[me.loops[li].vertex_index].co;tex=uv[li].uv
            poly.append([float(v.x),float(v.z),float(v.y),float(tex.x),float(tex.y)])
        if ob['component'] in moving:
            dynamic.setdefault(ob['component'],dict(opaque=[],glass=[]))[layer].extend(faces(poly,ob['material_id'],[0,0,0]));continue
        for p in split(poly,[0,1,2]):
            origin=tuple(math.floor(sum(v[i] for v in p)/len(p)+1e-8) for i in range(3))
            c=cells.setdefault(origin,dict(pos=list(origin),opaque=[],glass=[]));c[layer].extend(faces(p,ob['material_id'],origin))
    ev.to_mesh_clear()
assert source_meshes==len(data['parts']) and not seam_violations
result=dict(revision=data['revision'],coordinate_map='Minecraft X,Y,Z = Blender X,Z,Y; no storage plot offset baked in',
    geometry_sha256=hashlib.sha256((ROOT/'source/ne01.json').read_bytes()).hexdigest(),assembly_sha256=hashlib.sha256((ROOT/'assembly/ne01.blend').read_bytes()).hexdigest(),
    cells=list(cells.values()),moving_groups=dynamic,materials=data['materials'],collision_status='Not exported: requires deliberate source collision cells and native walking acceptance',runtime_registered=False)
(OUT/'ne01_render_cells.json').write_text(json.dumps(result,separators=(',',':'))+'\n')
report=dict(revision=data['revision'],geometry_sha256=result['geometry_sha256'],assembly_sha256=result['assembly_sha256'],source_meshes=source_meshes,cells=len(cells),opaque_quads=sum(len(c['opaque']) for c in cells.values()),glass_quads=sum(len(c['glass']) for c in cells.values()),mixed_cells=sum(bool(c['opaque']) and bool(c['glass']) for c in cells.values()),moving_groups=len(dynamic),collapsed_slivers_discarded=discarded,max_float32_reconstruction_error_m=max_error,boundary_snap_violations=seam_violations,opaque_and_glass_separate=True,runtime_registered=False,collision_accepted=False)
(ROOT/'reports/RENDER_EXPORT.json').write_text(json.dumps(report,indent=2)+'\n');print('NE01_RENDER_EXPORT',json.dumps(report))
