from pathlib import Path
import bpy,bmesh,math,json,hashlib,runpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'source/eitan_E01.json').read_text());P=data['parameters']
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/eitan_E01.blend'))
scene=bpy.context.scene;scene.frame_set(1);bpy.context.view_layer.update()
meshes=[o for o in bpy.data.objects if o.type=='MESH' and o.get('component')]
assert len(meshes)==len(data['parts'])+4
def points(obs):
    dg=bpy.context.evaluated_depsgraph_get();out=[]
    for ob in obs:
        ev=ob.evaluated_get(dg);mesh=ev.to_mesh();out.extend([ev.matrix_world@v.co for v in mesh.vertices]);ev.to_mesh_clear()
    return out
def bounds(ps):return [min(p[i] for p in ps) for i in range(3)],[max(p[i] for p in ps) for i in range(3)]
closed_points=points([o for o in meshes if o['component']!='ANTENNAS']);lo,hi=bounds(closed_points);dims=[hi[i]-lo[i] for i in range(3)]
assert abs(dims[0]-3)<.025 and abs(dims[1]-8)<.025 and abs(dims[2]-3)<.025,(lo,hi,dims)
nonmanifold=[];degenerate=[];triangles=0
for ob in meshes:
    ev=ob.evaluated_get(bpy.context.evaluated_depsgraph_get());me=ev.to_mesh();bm=bmesh.new();bm.from_mesh(me)
    if any(not edge.is_manifold for edge in bm.edges):nonmanifold.append(ob.name)
    if any(face.calc_area()<1e-12 for face in bm.faces):degenerate.append(ob.name)
    triangles+=sum(len(face.verts)-2 for face in bm.faces);bm.free();ev.to_mesh_clear()
assert not nonmanifold,nonmanifold
assert not degenerate,degenerate
assert len(data['seats'])==12 and sum(s['role']=='troop' for s in data['seats'])==9
wheel_ids=[g['id'] for g in data['groups'] if g['role']=='wheel_hub'];assert len(wheel_ids)==8
# Rear-specific requirements: measured projecting shoulders and recessed ramp.
shoulders=[o for o in meshes if o.name.startswith('projecting aft hull shoulder ')]
assert len(shoulders)==2
shoulder_points=points(shoulders);shoulder_aft=max(p.y for p in shoulder_points)
ramp_face=max(p.y for p in points([bpy.data.objects['continuous ramp frame']]))
rear_recess=shoulder_aft-ramp_face
assert rear_recess>=.55,rear_recess
shoulder_rise=max(p.z for p in shoulder_points)-P['roof_z_m']
assert shoulder_rise>=.14,shoulder_rise
last_seat_y=max(s['position_m'][1]+.24 for s in data['seats'] if s['role']=='troop')
last_seat_to_ramp=P['ramp_hinge_m'][1]-last_seat_y
assert last_seat_to_ramp>.20,last_seat_to_ramp
# Confirm holes exist in evaluated static roof geometry, independently of lids.
apertures=[]
for name,centre in [('roof centre fore',[-.65,-2.18]),('crew roof',[-.64,-.92]),('troop roof',[0,1.5])]:
    ob=bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get());inv=ob.matrix_world.inverted()
    origin=inv@Vector((centre[0],centre[1],3.2));direction=inv.to_3x3()@Vector((0,0,-1))
    hit=ob.ray_cast(origin,direction)[0];assert not hit,name;apertures.append(name)
# Analytic continuous clearance to flat ground for ALL vertices of the ramp assembly.
# This is a rotational-envelope check, not a collision/dynamics certification.
ramp=bpy.data.objects['RAMP'];inv=ramp.matrix_world.inverted()
ramp_mesh=[o for o in ramp.children_recursive if o.type=='MESH']
local=[inv@p for p in points(ramp_mesh)];amin=-math.radians(P['ramp_open_deg']);z0=P['ramp_hinge_m'][2]
ground_min=1e9
for p in local:
    angles=[amin,0];critical=math.atan2(p.y,p.z)
    angles.extend([critical+k*math.pi for k in range(-2,3) if amin<=critical+k*math.pi<=0])
    ground_min=min(ground_min,min(z0+p.y*math.sin(a)+p.z*math.cos(a) for a in angles))
assert ground_min>.005,ground_min
# Discrete triangle-surface checks of the changed rear bay. This is deliberately
# reported as sampled geometry, not a continuous swept-volume collision proof.
def tree(ob):
    ev=ob.evaluated_get(bpy.context.evaluated_depsgraph_get());me=ev.to_mesh()
    vs=[ev.matrix_world@v.co for v in me.vertices];fs=[list(f.vertices) for f in me.polygons]
    result=BVHTree.FromPolygons(vs,fs,all_triangles=False,epsilon=1e-7);ev.to_mesh_clear();return result
rear_static=[o for o in meshes if o.name.startswith(('projecting aft hull shoulder ','recess passage cheek ','lower aft corner ','fixed ramp jamb ','ramp side seal ')) or o.name in ['central floor','recessed aft roof lintel']]
static_trees=[(o.name,tree(o)) for o in rear_static];sample_overlaps=[]
sample_degrees=list(range(0,119,4))+[P['ramp_open_deg']]
for deg in sample_degrees:
    ramp.rotation_euler.x=-math.radians(deg);bpy.context.view_layer.update()
    for ob in ramp_mesh:
        t=tree(ob)
        for name,fixed in static_trees:
            if t.overlap(fixed):sample_overlaps.append({'angle_deg':deg,'moving':ob.name,'fixed':name})
ramp.rotation_euler.x=0;bpy.context.view_layer.update()
assert not sample_overlaps,sample_overlaps[:12]
seat_pans=[o for o in meshes if o.name.endswith('seat pan')];closed_pan_z={o.name:o.matrix_world.translation.z for o in seat_pans}
scene.frame_set(121);bpy.context.view_layer.update();rising=sum(o.matrix_world.translation.z-closed_pan_z[o.name]>.15 for o in seat_pans);assert rising==9,rising
actuator_errors=[]
for frame in [1,11,21,31,41,51,61,81,101,121,161]:
    scene.frame_set(frame);bpy.context.view_layer.update();dg=bpy.context.evaluated_depsgraph_get()
    for act in data['actuators']:
        rod=bpy.data.objects[act['id']+' rod'].evaluated_get(dg);end=bpy.data.objects[act['id']+' moving'].evaluated_get(dg)
        actuator_errors.append(((rod.matrix_world@Vector((0,0,1)))-end.matrix_world.translation).length)
assert max(actuator_errors)<.0001,max(actuator_errors)
scene.frame_set(81);assert abs(bpy.data.objects['RAMP'].rotation_euler.x)<1e-7 and bpy.data.objects['RAMP_DOOR'].rotation_euler.z>1.5
door=bpy.data.objects['RAMP_DOOR'];door_parts=[o for o in door.children_recursive if o.type=='MESH']
door_sample_degrees=[0,19,38,57,76,95];door_overlaps=[]
for deg in door_sample_degrees:
    door.rotation_euler.z=math.radians(deg);bpy.context.view_layer.update()
    for ob in door_parts:
        t=tree(ob)
        for name,fixed in static_trees:
            if t.overlap(fixed):door_overlaps.append({'angle_deg':deg,'moving':ob.name,'fixed':name})
assert not door_overlaps,door_overlaps
state=runpy.run_path(str(ROOT/'tools/mechanism_state.py'))['verify_contract']();assert state['passed']
# Reopen both actual exports and check both mesh count and closed dimensions.
exports={}
for filename in ['eitan_E01_closed.glb','eitan_E01_boarding.glb']:
    bpy.ops.wm.read_factory_settings(use_empty=True);bpy.ops.import_scene.gltf(filepath=str(ROOT/'exports'/filename))
    imported=[o for o in bpy.data.objects if o.type=='MESH'];assert len(imported)==len(meshes)
    pp=points([o for o in imported if not o.name.startswith('flexible antenna ') ]);a,b=bounds(pp)
    exports[filename]={'meshes':len(imported),'bounds_min':a,'bounds_max':b}
    if 'closed' in filename:
        err=max(abs(a[i]-lo[i]) for i in range(3))+max(abs(b[i]-hi[i]) for i in range(3));assert err<.002,err
report={'revision':data['revision'],'passed':True,'assembly_sha256':hashlib.sha256((ROOT/'assembly/eitan_E01.blend').read_bytes()).hexdigest(),'geometry_sha256':hashlib.sha256((ROOT/'source/eitan_E01.json').read_bytes()).hexdigest(),'mesh_objects':len(meshes),'evaluated_triangles':triangles,'nonmanifold_meshes':nonmanifold,'zero_area_meshes':degenerate,
 'bounds_min_m':lo,'bounds_max_m':hi,'dimensions_m_xyz':dims,'height_excludes_flexible_antennas':True,
 'seats':12,'troop_seats':9,'crew_seats':3,'wheels':8,'true_roof_apertures':apertures,
 'rear_recess_mm':rear_recess*1000,'rear_shoulder_rise_above_nominal_roof_mm':shoulder_rise*1000,'last_troop_pan_to_ramp_hinge_mm':last_seat_to_ramp*1000,
 'rear_ramp_surface_collision_samples_deg':sample_degrees,'rear_ramp_sampled_surface_intersections':sample_overlaps,
 'rear_door_surface_collision_samples_deg':door_sample_degrees,'rear_door_sampled_surface_intersections':door_overlaps,
 'ramp_continuous_min_z_mm':ground_min*1000,'ramp_clearance_method':'Analytic extrema of every evaluated ramp vertex under prescribed X-axis rotation over full interval; flat ground only.',
 'folded_pans_rising':rising,'actuator_endpoint_max_error_mm':max(actuator_errors)*1000,'actuator_sample_frames':[1,11,21,31,41,51,61,81,101,121,161],
 'state_contract':state,'exports':exports,'glb_round_trip_passed':True,'interior_nominal_clear_height_mm':1600,'standing_player_1800mm_fits':False,
 'runtime_registered':False,'offroad_driving_tested':False,'manufacturing_verified':False,
 'limitations':['Opening directions/angles and detailed dimensions are documented adaptations, not a factory drawing.', 'No complete continuous collision proof between all vehicle parts, occupants and terrain.', 'Actuator linkage sampled at listed poses; not force/friction/hydraulic simulation.', 'No Minecraft runtime integration or driving test.']}
(ROOT/'reports/MODEL_CHECK.json').write_text(json.dumps(report,indent=2)+'\n')
values={'DIM-L':('length_mm',dims[1]*1000,'mm'),'DIM-W':('width_mm',dims[0]*1000,'mm'),'DIM-H':('height_mm',dims[2]*1000,'mm'),'SEATS':('seats',12,'count'),'WHEELS':('wheels',8,'count'),'RAMP-GROUND':('ramp_continuous_min_z_mm',ground_min*1000,'mm'),'SEAT-FOLD':('folded_pans_rising',rising,'count'),'STATE':('state_contract_passed',True,'bool'),'EXPORT':('glb_round_trip_passed',True,'bool')}
values.update({'REAR-RECESS':('rear_recess_mm',rear_recess*1000,'mm'),'REAR-RISE':('rear_shoulder_rise_mm',shoulder_rise*1000,'mm'),'REAR-MOTION':('rear_sampled_surface_intersections',len(sample_overlaps)+len(door_overlaps),'count')})
record={'method':'inspection','result':'pass','observations':{id:{'metric':metric,'value':value,'unit':unit} for id,(metric,value,unit) in values.items()},
 'dependencies':['reports/MODEL_CHECK.json','assembly/eitan_E01.blend','exports/eitan_E01_closed.glb','exports/eitan_E01_boarding.glb'],
 'details':{'setup':'Blender '+bpy.app.version_string+' evaluated mesh + native driver + GLB round trip; virtual model inspection only'},'limitations':report['limitations']}
(ROOT/'verification/model-inspection.json').write_text(json.dumps(record,indent=2)+'\n')
print('EITAN_MODEL_CHECK_PASS',json.dumps({k:report[k] for k in ['dimensions_m_xyz','seats','wheels','ramp_continuous_min_z_mm','folded_pans_rising','actuator_endpoint_max_error_mm','passed']}))
