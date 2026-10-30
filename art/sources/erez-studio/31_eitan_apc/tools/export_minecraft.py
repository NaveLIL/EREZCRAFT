"""Exact render cells + conservative 1/16 block solids for an isolated game preview.

The four Blender inspection poses are not a vehicle controller. Wheel pivots,
individual groups and seats are retained for the later Offroad chassis adapter.
No saved Blender file, world or release archive is changed here.
"""
from pathlib import Path
from collections import defaultdict
import bpy, json, math, hashlib, struct, zlib
from mathutils import Vector
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'exports/minecraft';OUT.mkdir(parents=True,exist_ok=True)
data=json.loads((ROOT/'source/eitan_E01.json').read_text())
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/eitan_E01.blend'))
scene=bpy.context.scene
R=16;HALF=math.sqrt(3)/(2*R);direction=Vector((1,.0271,.0137)).normalized()
groups={g['id']:g for g in data['groups']}
moving={g['id'] for g in data['groups'] if 'axis' in g or g['role']=='wheel_hub'}
cells={};vox=defaultdict(set);stats=defaultdict(int);max_snap_error=0.
def cell(p):
    return cells.setdefault(tuple(p),dict(pos=list(p),base=[],poses=[[],[],[],[]],boxes=[],pose_boxes=[[],[],[],[]]))
def clip(poly,axis,bound,sign):
    result=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        da=(a[axis]-bound)*sign;db=(b[axis]-bound)*sign
        if da>=-1e-9:result.append(a)
        if da>1e-9 and db< -1e-9 or da< -1e-9 and db>1e-9:
            t=da/(da-db);p=[x+(y-x)*t for x,y in zip(a,b)];p[axis]=bound;result.append(p)
    return result
def split(poly):
    result=[poly]
    for axis in range(3):
        out=[]
        for p in result:
            lo=math.floor(min(v[axis] for v in p)+1e-8);hi=max(lo,math.floor(max(v[axis] for v in p)-1e-8))
            for n in range(lo,hi+1):
                q=clip(clip(p,axis,n,1),axis,n+1,-1)
                if len(q)>=3:out.append(q)
        result=out
    return result
def render_face(poly,material,key):
    for p in split(poly):
        origin=tuple(math.floor(sum(v[i] for v in p)/len(p)+1e-8) for i in range(3))
        target=cell(origin)['base'] if key=='static' else cell(origin)['poses'][key]
        for k in range(1,len(p)-1):
            tri=[p[0],p[k],p[k+1]]
            local=[[round(v[i]-origin[i],7) for i in range(3)] for v in tri]
            if (Vector(local[1])-Vector(local[0])).cross(Vector(local[2])-Vector(local[0])).length<1e-10:continue
            assert all(-1e-6<=v<=1.000001 for p in local for v in p)
            target.append(dict(material=material,vertices=local+[local[-1]]));stats['render_triangles']+=1
def merge(values):
    todo=set(values);out=[]
    while todo:
        x,y,z=min(todo);dx=1
        while (x+dx,y,z) in todo:dx+=1
        dz=1
        while all((i,y,z+dz) in todo for i in range(x,x+dx)):dz+=1
        dy=1
        while all((i,y+dy,k) in todo for i in range(x,x+dx) for k in range(z,z+dz)):dy+=1
        for i in range(x,x+dx):
            for j in range(y,y+dy):
                for k in range(z,z+dz):todo.remove((i,j,k))
        out.append([v/R for v in (x,y,z,x+dx,y+dy,z+dz)])
    return out
skip_collision=('armor fastener ','hood seam ','hood service latch ','roof rack ','flexible antenna ',
                'interior ceiling lamp ','vent outlet ','channel plate ','steering spoke ','floor grip strip ',
                'troop hatch handle ','troop hatch hinge ','driver hinge ','commander hinge ')
for pose,frame in enumerate([1,41,81,121]):
    scene.frame_set(frame);bpy.context.view_layer.update();dg=bpy.context.evaluated_depsgraph_get()
    for ob in bpy.data.objects:
        if ob.type!='MESH' or not ob.get('component'):continue
        dynamic=ob['component'] in moving or 'ACTUATOR' in ob.name
        if pose and not dynamic:continue
        key=pose if dynamic else 'static'
        ev=ob.evaluated_get(dg);me=ev.to_mesh();me.calc_loop_triangles()
        verts=[ev.matrix_world@v.co for v in me.vertices]
        # Tiny evaluated bevel/tread drift around integer planes must not create
        # an almost-zero fragment in the neighbouring ground block.
        for v in verts:
            for axis in range(3):
                if abs(v[axis]-round(v[axis]))<.00005:
                    max_snap_error=max(max_snap_error,abs(v[axis]-round(v[axis])));v[axis]=round(v[axis])
        for tri in me.loop_triangles:
            mat=me.materials[me.polygons[tri.polygon_index].material_index].name.split(' / ')[-1]
            # Swapping Y/Z reverses handedness; reverse triangle winding.
            render_face([[float(verts[i].x),float(verts[i].z),float(verts[i].y)] for i in reversed(tri.vertices)],mat,key)
        if ob['component']!='ANTENNAS' and not ob.name.startswith(skip_collision):
            tree=BVHTree.FromPolygons(verts,[list(t.vertices) for t in me.loop_triangles],all_triangles=True)
            lo=[math.floor(min(v[i] for v in verts)*R+1e-7) for i in range(3)]
            hi=[math.ceil(max(v[i] for v in verts)*R-1e-7) for i in range(3)]
            for x in range(lo[0],hi[0]):
                for y in range(lo[1],hi[1]):
                    for z in range(lo[2],hi[2]):
                        mc=(x,z,y)
                        if mc in vox[key]:continue
                        p=Vector(((x+.5)/R,(y+.5)/R,(z+.5)/R));nearest,_,_,dist=tree.find_nearest(p)
                        inside=False
                        if dist>HALF+1e-7:
                            hit,normal,_,_=tree.ray_cast(p,direction,100);inside=hit is not None and normal.dot(direction)>0
                        if dist<=HALF+1e-7 or inside:vox[key].add(mc)
        ev.to_mesh_clear()
    print('EITAN_EXPORT_POSE',pose,frame,flush=True)
for key,values in vox.items():
    bycell=defaultdict(set)
    for v in values:bycell[tuple(n//R for n in v)].add(tuple(n%R for n in v))
    for p,v in bycell.items():
        c=cell(p)
        if key=='static':c['boxes']=merge(v)
        else:c['pose_boxes'][key]=merge(v)
palette=dict(data['palette'],rubber_dust='#535447')
def png(color):
    rgb=bytes(int(color[i:i+2],16) for i in [1,3,5])+b'\xff'
    def chunk(n,p):return struct.pack('>I',len(p))+n+p+struct.pack('>I',zlib.crc32(n+p)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b'\0'+rgb))+chunk(b'IEND',b'')
textures=OUT/'textures';textures.mkdir(exist_ok=True)
for name,color in palette.items():(textures/(name+'.png')).write_bytes(png(color))
ordered=[]
for p,c in sorted(cells.items()):c['id']=len(ordered);ordered.append(c)
result=dict(revision=data['revision'],coordinate_map='Minecraft XYZ = Blender XZY; metre = block',
            geometry_sha256=hashlib.sha256((ROOT/'source/eitan_E01.json').read_bytes()).hexdigest(),
            assembly_sha256=hashlib.sha256((ROOT/'assembly/eitan_E01.blend').read_bytes()).hexdigest(),
            cells=ordered,palette=palette,groups=data['groups'],seats=data['seats'],
            poses=['closed','boarding','door','service'],collision_resolution=R,
            status='Native geometry preview only; no Offroad driving or armour/weapon simulation')
out=OUT/'eitan_runtime.json';out.write_text(json.dumps(result,separators=(',',':'))+'\n')
report=dict(revision=data['revision'],cells=len(ordered),triangles=dict(stats),voxels={str(k):len(v) for k,v in vox.items()},max_integer_plane_snap_error_m=max_snap_error,
            collision_resolution=R,maximum_surface_inflation_m=math.sqrt(3)/R,
            geometry_sha256=result['geometry_sha256'],assembly_sha256=result['assembly_sha256'],
            runtime_sha256=hashlib.sha256(out.read_bytes()).hexdigest(),
            limitations=['Conservative voxel solids; native crouch routes must be checked.',
                         'Four inspection poses are endpoint snapshots, not an animated gameplay controller.',
                         'Wheel collision is included in the stationary preview; Offroad chassis must replace it with tire contacts.'])
(ROOT/'reports/MINECRAFT_EXPORT.json').write_text(json.dumps(report,indent=2)+'\n')
print('EITAN_EXPORT_COMPLETE',json.dumps(report))
