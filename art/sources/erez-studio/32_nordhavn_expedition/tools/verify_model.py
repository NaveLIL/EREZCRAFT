from pathlib import Path
import bpy,bmesh,json,math,hashlib
from mathutils import Vector
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'source/ne01.json').read_text());bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/ne01.blend'))
assert bpy.context.scene['revision']==data['revision']
scene=bpy.context.scene;scene.frame_set(1);bpy.context.view_layer.update()
objects=[o for o in bpy.data.objects if o.type=='MESH' and o.get('source_part_id')]
assert len(objects)==len(data['parts'])
def meshdata(ob):
    ev=ob.evaluated_get(bpy.context.evaluated_depsgraph_get());me=ev.to_mesh();v=[ev.matrix_world@p.co for p in me.vertices];f=[list(p.vertices) for p in me.polygons];ev.to_mesh_clear();return v,f
def bounds(v):return [min(p[i] for p in v) for i in range(3)],[max(p[i] for p in v) for i in range(3)]
bad=[];zero=[];nonpositive_volume=[];triangles=0
for ob in objects:
    v,f=meshdata(ob);bm=bmesh.new();bm.from_mesh(ob.data)
    if any(not e.is_manifold for e in bm.edges):bad.append(ob.name)
    if any(f.calc_area()<1e-11 for f in bm.faces):zero.append(ob.name)
    if bm.calc_volume(signed=True)<=0:nonpositive_volume.append(ob.name)
    triangles+=sum(len(p)-2 for p in f);bm.free()
hv=[v for ob in objects if ob.name=='Continuous watertight hull skin' or ob.name.startswith('Raised forecastle shell ') or ob.name=='Main roof and raised foredeck' for v in meshdata(ob)[0]];lo,hi=bounds(hv)
assert abs(hi[0]-lo[0]-7.32)<1e-5 and abs(hi[1]-lo[1]-29.44)<1e-5
closed_bounds=bounds([p for ob in objects for p in meshdata(ob)[0]])

def tree(obs):
    v=[];f=[];names=[]
    for ob in obs:
        a,b=meshdata(ob);off=len(v);v.extend(a)
        for poly in b:f.append([i+off for i in poly]);names.append(ob.name)
    return BVHTree.FromPolygons(v,f,all_triangles=False,epsilon=1e-7),names
closed,names=tree(objects);rays=[];ray_fail=[]
for w in data['windows']:
    x,a,b,c,d=w['opening'];side=1 if x>0 else -1
    for y in [a-.02,a+.02,(a+b)/2,b-.02,b+.02]:
        for z in [c-.02,c+.02,(c+d)/2,d-.02,d+.02]:
            hit,normal,idx,dist=closed.ray_cast(Vector((x+side*.8,y,z)),Vector((-side,0,0)),1.0)
            result=dict(window=w['id'],y=y,z=z,hit=names[idx] if idx is not None else None,distance=dist)
            rays.append(result)
            if hit is None:ray_fail.append(result)
for i in [-1,0,1]:
    for x in [i*1.55-.73,i*1.55,i*1.55+.73]:
        for z in [7.67,8.49,9.30]:
            y=data['parameters']['bridge_front_y_m']+.025-.18*(z-8.49);hit,normal,idx,dist=closed.ray_cast(Vector((x,y+.9,z)),Vector((0,-1,0)),1.2)
            if hit is None:ray_fail.append(dict(window='front_'+str(i),x=x,z=z))

# The rendered mesh is the authority for these STATIC samples. They are not
# Minecraft character movement, and do not prove dynamic collision correctness.
scene.frame_set(41);bpy.context.view_layer.update();open_tree,open_names=tree(objects)
box_faces=[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)]
def clearance(name,x,y,z,height=1.8,step_allowance=.0,width=.60):
    h=width/2
    v=[Vector((xx,yy,zz)) for xx,yy,zz in [(x-h,y-h,z+step_allowance+.012),(x+h,y-h,z+step_allowance+.012),(x+h,y+h,z+step_allowance+.012),(x-h,y+h,z+step_allowance+.012),(x-h,y-h,z+height),(x+h,y-h,z+height),(x+h,y+h,z+height),(x-h,y+h,z+height)]]
    t=BVHTree.FromPolygons(v,box_faces,all_triangles=False);hits=t.overlap(open_tree)
    return dict(name=name,position_m=[x,y,z],height_m=height,width_m=width,step_allowance_m=step_allowance,intersections=sorted(set(open_names[j] for i,j in hits)))
samples=[]
for st in data['stairs']:
    for i in range(st['steps']):samples.append(clearance(st['id']+f' step {i}',st['x'],st['y']+(i+.5)*st['tread'],st['z']+(i+1)*st['rise']/st['steps'],2.2,.60,.70))
    samples.append(clearance(st['id']+' landing',st['x'],st['y']+st.get('landing_probe_forward',4.15),st['z']+st['rise'],2.2))
for name,x,z,ys in [('Lower fore hall',0,1.5,[2.9,3.8,5.2,6.5,7.1]),('Main hall',.55,4.2,[-8.4,-7,-5,-3.8,-2.8,-1.5,0,1.2]),('Upper hall',-.50,6.95,[-3,-1.8,-.7]),('Helm approach',0,6.95,[-.2]),('Master side',1.8,4.2,[3.3,4.6,6.4])]:
    for y in ys:samples.append(clearance(name+str(y),x,y,z,2.2))
failed=[s for s in samples if s['intersections']]
allpoints=[p for ob in objects for p in meshdata(ob)[0]];alo,ahi=bounds(allpoints)
result=dict(revision=data['revision'],mesh_objects=len(objects),triangles=triangles,nonmanifold_meshes=bad,zero_area_meshes=zero,hull_dimensions_xyz_m=[hi[i]-lo[i] for i in range(3)],full_open_pose_bounds=[alo,ahi],window_rays=len(rays)+27,window_ray_failures=ray_fail,clearance_samples=samples,clearance_failures=failed,
    nonpositive_volume_meshes=nonpositive_volume,passed_geometry=not bad and not zero and not ray_fail and not nonpositive_volume,passed_sampled_clearance=not failed,
    geometry_sha256=hashlib.sha256((ROOT/'source/ne01.json').read_bytes()).hexdigest(),assembly_sha256=hashlib.sha256((ROOT/'assembly/ne01.blend').read_bytes()).hexdigest(),
    limitations=['Static evaluated mesh inspection only, not Minecraft walking or exported block collision acceptance.','Clearance samples use a 0.60m wide box and 2.20m headroom; stair samples exclude the bottom 0.60m to permit normal stepping.','No continuous swept-volume proof for doors or gangways.','Small AE2 raft compatibility is separate from full-vessel ME integration.'])
bpy.ops.wm.read_factory_settings(use_empty=True);bpy.ops.import_scene.gltf(filepath=str(ROOT/'exports/ne01.glb'));bpy.context.scene.frame_set(1);bpy.context.view_layer.update()
imported=[o for o in bpy.data.objects if o.type=='MESH'];assert len(imported)==len(objects)
a,b=bounds([p for ob in imported for p in meshdata(ob)[0]]);error=max(abs(a[i]-closed_bounds[0][i]) for i in range(3))+max(abs(b[i]-closed_bounds[1][i]) for i in range(3))
assert error<.0001,error
result.update(glb_round_trip_passed=True,glb_meshes=len(imported),glb_bounds_error_m=error,closed_bounds_m=closed_bounds)
(ROOT/'reports/MODEL_INSPECTION.json').write_text(json.dumps(result,indent=2)+'\n')
print('NE01_INSPECTION',json.dumps({k:result[k] for k in ['mesh_objects','triangles','nonmanifold_meshes','zero_area_meshes','window_ray_failures','clearance_failures','passed_geometry','passed_sampled_clearance']}))
