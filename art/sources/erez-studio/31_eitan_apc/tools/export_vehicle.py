"""E01 driving asset: baked shell, articulated bones and swept collision cells.

Uses the existing saved authoring scene; never changes it. Static chunk meshes
and animated cached models are separate. Stock preview snapshots stay intact.
"""
from pathlib import Path
from collections import defaultdict
import ast, bpy, json, math, hashlib
from mathutils import Vector
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'exports/minecraft';OUT.mkdir(exist_ok=True)
R=16;N=16;SUB=4;HALF=math.sqrt(3)/(2*R)
direction=Vector((1,.0271,.0137)).normalized()
data=json.loads((ROOT/'source/eitan_E01.json').read_text())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for report in ['MODEL_CHECK','ROOF_FIT']+(['HATCH_SEAL'] if data['parameters'].get('monitor_face_recess_m') else [])+(['FOOTWELL'] if any(p['name']=='sealed fore footwell floor' for p in data['parts']) else []):
    check=json.loads((ROOT/f'reports/{report}.json').read_text())
    assert check['passed'] and check['geometry_sha256']==sha(ROOT/'source/eitan_E01.json')
    assert check['assembly_sha256']==sha(ROOT/'assembly/eitan_E01.blend')
# Shared pure clipping and box-merging algorithms, without running the snapshot exporter.
tree=ast.parse((ROOT/'tools/export_minecraft.py').read_text())
exec(compile(ast.Module(body=[f for f in tree.body if isinstance(f,ast.FunctionDef) and f.name in ['clip','split','merge']],type_ignores=[]),'<eitan mesh helpers>','exec'))
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/eitan_E01.blend'))
scene=bpy.context.scene;scene.frame_set(1);bpy.context.view_layer.update()
groups={g['id']:g for g in data['groups']}
axis_groups=[g['id'] for g in data['groups'] if 'axis' in g]
wheel_groups=[g['id'] for g in data['groups'] if g['role']=='wheel_hub']
meshes=[o for o in bpy.data.objects if o.type=='MESH' and o.get('component')]
cells={};bones={};monitors=[];triangles=0;max_snap=0.
def cell(p):return cells.setdefault(tuple(p),dict(pos=list(p),base=[],boxes=[],groups={}))
def mc(v):return [float(v[0]),float(v[2]),float(v[1])]
def snap(v):
    global max_snap
    v=Vector(v)
    for i in range(3):
        if abs(v[i]-round(v[i]))<.00005:
            max_snap=max(max_snap,abs(v[i]-round(v[i])));v[i]=round(v[i])
    return v
def faces(poly,material,origin):
    global triangles
    out=[]
    for k in range(1,len(poly)-1):
        tri=[poly[0],poly[k],poly[k+1]];v=[[round(p[i]-origin[i],7) for i in range(3)] for p in tri]
        if (Vector(v[1])-Vector(v[0])).cross(Vector(v[2])-Vector(v[0])).length<1e-10:continue
        out.append(dict(material=material,vertices=v+[v[-1]]));triangles+=1
    return out
def render_group(ob):
    if ob.name in ['driver steering wheel','steering hub'] or ob.name.startswith('steering spoke '):return 'STEERING_WHEEL'
    if ob['component'] in axis_groups or ob['component'] in wheel_groups:return ob['component']
    if 'ACTUATOR' in ob.name:return ob.name
    for wheel in wheel_groups:
        if ob.name.startswith(wheel+' damper') or ob.name.startswith(wheel+' coil'):return wheel+'_spring'
    return None
dg=bpy.context.evaluated_depsgraph_get()
for ob in meshes:
    ev=ob.evaluated_get(dg);me=ev.to_mesh();me.calc_loop_triangles()
    is_monitor=ob.name in ['driver display 0','driver display 1','driver display 2','commander display','operator display','driver instruments']
    verts=[snap(ev.matrix_world@v.co) for v in me.vertices]
    if is_monitor:
        lo=[min(v[i] for v in verts) for i in range(3)];hi=[max(v[i] for v in verts) for i in range(3)]
        monitors.append(dict(name=ob.name,min=mc(lo),max=mc(hi)))
        ev.to_mesh_clear();continue
    group=render_group(ob)
    if group:
        target=bones.setdefault(group,dict(name=group,quads=[]))['quads']
    if 'ACTUATOR' in ob.name:verts=[v.co.copy() for v in me.vertices]
    for tri in me.loop_triangles:
        material=me.materials[me.polygons[tri.polygon_index].material_index].name.split(' / ')[-1]
        poly=[mc(verts[i]) for i in reversed(tri.vertices)]
        if group:target.extend(faces(poly,material,[0,0,0]))
        else:
            for p in split(poly):
                origin=tuple(math.floor(sum(v[i] for v in p)/len(p)+1e-8) for i in range(3))
                cell(origin)['base'].extend(faces(p,material,origin))
    ev.to_mesh_clear()

skip=('armor fastener ','hood seam ','hood service latch ','roof rack ','flexible antenna ',
      'interior ceiling lamp ','vent outlet ','channel plate ','steering spoke ','floor grip strip ',
      'troop hatch handle ','troop hatch hinge ','driver hinge ','commander hinge ',
      'rubber mudflap ','folded mirror ','mirror glass ')
def collidable(ob):
    return not (ob.name.endswith('hatch gasket') or ob['component'] in wheel_groups or ob['component']=='ANTENNAS' or
                'ACTUATOR' in ob.name or ob.name.startswith(skip) or
                any(w+' ' in ob.name for w in wheel_groups))
def voxelize(objects,margin=0.):
    result=set();graph=bpy.context.evaluated_depsgraph_get()
    for ob in objects:
        if not collidable(ob):continue
        ev=ob.evaluated_get(graph);me=ev.to_mesh();me.calc_loop_triangles()
        vs=[snap(ev.matrix_world@v.co) for v in me.vertices]
        bvh=BVHTree.FromPolygons(vs,[list(t.vertices) for t in me.loop_triangles],all_triangles=True)
        lo=[math.floor((min(v[i] for v in vs)-margin)*R+1e-7) for i in range(3)]
        hi=[math.ceil((max(v[i] for v in vs)+margin)*R-1e-7) for i in range(3)]
        for x in range(lo[0],hi[0]):
            for y in range(lo[1],hi[1]):
                for z in range(lo[2],hi[2]):
                    key=(x,z,y)
                    if key in result:continue
                    center=Vector(((x+.5)/R,(y+.5)/R,(z+.5)/R))
                    _,_,_,distance=bvh.find_nearest(center)
                    inside=False
                    if distance>HALF+margin:
                        hit,normal,_,_=bvh.ray_cast(center,direction,100)
                        inside=hit is not None and normal.dot(direction)>0
                    if distance<=HALF+margin+1e-7 or inside:result.add(key)
        ev.to_mesh_clear()
    return result
def encoded(values):
    cc=defaultdict(set)
    for v in values:cc[tuple(n//R for n in v)].add(tuple(n%R for n in v))
    return {p:merge(v) for p,v in cc.items()}
static=voxelize([o for o in meshes if o['component'] not in axis_groups])
for p,boxes in encoded(static).items():cell(p)['boxes']=boxes
collision_groups=[]
specs=[('REAR','RAMP',['RAMP','RAMP_DOOR'])]+[(g,g,[g]) for g in axis_groups if g not in ['RAMP','RAMP_DOOR']]
for name,driver,components in specs:
    objects=[o for o in meshes if o['component'] in components and collidable(o)]
    index=len(collision_groups);poses=[];sweeps=[];whole=set()
    motions=[driver,'RAMP_DOOR'] if name=='REAR' else [driver]
    for motion in motions:
        g=groups[motion];ob=bpy.data.objects[motion];axis={'X':0,'Y':1,'Z':2}[g['axis']]
        pivot=Vector(g['pivot']);radius=0.
        scene.frame_set(1);bpy.context.view_layer.update()
        for part in objects:
            ev=part.evaluated_get(bpy.context.evaluated_depsgraph_get());me=ev.to_mesh()
            radius=max(radius,max((ev.matrix_world@v.co-pivot).length for v in me.vertices));ev.to_mesh_clear()
        max_angle=math.radians(g['open_deg'])
        # For sampled swept voxels, a radius*half-angle bound covers the motion
        # between adjacent angular samples, in addition to the voxel diagonal.
        sweep_margin=radius*abs(max_angle)/(N*SUB*2)
        sweep=set()
        for sample in range(N*SUB+1):
            ob.rotation_euler[axis]=max_angle*sample/(N*SUB);bpy.context.view_layer.update()
            current=voxelize(objects)
            if sample%SUB==0 and (motion==motions[0] or sample>0):poses.append(encoded(current))
            swept=current if sweep_margin<1e-8 else voxelize(objects,sweep_margin)
            if sample%SUB==0:
                if sample:sweeps.append(encoded(sweep|swept))
                sweep=set(swept)
            else:sweep.update(swept)
            whole.update(swept)
        ob.rotation_euler[axis]=0;bpy.context.view_layer.update()
    assert len(poses)==(2*N+1 if name=='REAR' else N+1)
    states=poses+sweeps
    for state_index,state in enumerate(states):
        for p,boxes in state.items():
            entry=cell(p)['groups'].setdefault(index,[[] for _ in states]);entry[state_index]=boxes
    low=[min(v[i] for v in whole)/R for i in range(3)];high=[(max(v[i] for v in whole)+1)/R for i in range(3)]
    collision_groups.append(dict(name=name,index=index,pose_count=len(poses),sweep_count=len(sweeps),swept_min=low,swept_max=high))
    print('EITAN_COLLISION_GROUP',name,len(poses),len(sweeps),len(whole),flush=True)

# Mount cells and the controller exist even if an extremely thin source feature
# produced no static triangles at that block coordinate.
controller=[0,1,-2]
mounts=[]
for side in [-1,1]:
    for axle,y in enumerate(data['parameters']['axles_y_m']):
        pos=[-1 if side<0 else 0,0,math.floor(y)]
        mounts.append(dict(slot=len(mounts),bone=f'WHEEL_{"L" if side<0 else "R"}{axle+1}',pos=pos,hub=[side*1.26,.68,y],axle=axle,side=side))
        cell(pos)
cell(controller)
ordered=[]
for p,c in sorted(cells.items()):
    c['id']=len(ordered);c['groups']=[dict(index=i,states=states) for i,states in sorted(c['groups'].items())];ordered.append(c)
assert len(ordered)<500
result=dict(revision=data['revision'],runtime_revision='vehicle-v1',steps=N,geometry_sha256=sha(ROOT/'source/eitan_E01.json'),assembly_sha256=sha(ROOT/'assembly/eitan_E01.blend'),
            coordinate_map='Minecraft XYZ = Blender XZY; 1 metre = block',cells=ordered,bones=list(bones.values()),collision_groups=collision_groups,
            steering_pivot=mc(next(p['center'] for p in data['parts'] if p['name']=='driver steering wheel')),groups=data['groups'],actuators=data['actuators'],seats=data['seats'],monitors=monitors,mounts=mounts,controller=controller,
            max_integer_snap_error_m=max_snap,render_triangles_all_models=triangles,
            collision_method='Conservative 1/16 solids; swept samples include radius*half-angle bound; stock wheel contacts replace tyre solids.')
out=OUT/'eitan_vehicle.json';out.write_text(json.dumps(result,separators=(',',':'))+'\n')
report={k:result[k] for k in ['revision','runtime_revision','geometry_sha256','assembly_sha256','steps','max_integer_snap_error_m','render_triangles_all_models','collision_method']}
report.update(cells=len(ordered),bones=len(bones),collision_groups=len(collision_groups),monitors=len(monitors),wheel_mounts=len(mounts),runtime_sha256=sha(out),script_sha256=sha(Path(__file__)))
(ROOT/'reports/VEHICLE_EXPORT.json').write_text(json.dumps(report,indent=2)+'\n')
print('EITAN_VEHICLE_EXPORT',json.dumps(report))
