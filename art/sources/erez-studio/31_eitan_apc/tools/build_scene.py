from pathlib import Path
import bpy,bmesh,math,json,argparse,sys
from mathutils import Vector,Matrix,Euler
from bpy_extras.object_utils import world_to_camera_view
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--views',default='hero,rear,boarding,interior,cutaway,door,hatches,side');parser.add_argument('--resolution',type=int,default=1600);args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
data=json.loads((ROOT/'source/eitan_E01.json').read_text());P=data['parameters']
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
scene=bpy.context.scene;scene.name='EITAN / E01 / 1:1 articulated game model'
scene.unit_settings.system='METRIC';scene.unit_settings.scale_length=1
scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
scene.render.resolution_x=args.resolution;scene.render.resolution_y=round(args.resolution*.75);scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG'
scene.render.fps=24;scene.frame_start=1;scene.frame_end=161;scene.view_settings.view_transform='AgX'
scene.world.use_nodes=True;bg=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND');bg.inputs['Color'].default_value=(.16,.17,.18,1);bg.inputs['Strength'].default_value=.5
coll=bpy.data.collections.new('EITAN / export');scene.collection.children.link(coll);groups={};pivots={}
for g in data['groups']:
    ob=bpy.data.objects.new(g['id'],None);coll.objects.link(ob);pivots[g['id']]=Vector(g['pivot'])
    if g['parent']:ob.parent=groups[g['parent']];ob.location=pivots[g['id']]-pivots[g['parent']]
    else:ob.location=g['pivot']
    ob['role']=g['role'];ob.empty_display_size=.15;groups[g['id']]=ob
root=groups['ROOT'];root['units']='1 coordinate unit = 1 metre = 1 Minecraft block';root['geometry_authority']='source/eitan_E01.json';root['runtime_registered']=False
materials={}
def linear(c):return c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4
palette=dict(data['palette'],rubber_dust='#535447')
for name,color in palette.items():
    rgb=tuple(linear(int(color[i:i+2],16)/255) for i in (1,3,5));mat=bpy.data.materials.new('E01 / '+name);mat.use_nodes=True;mat.diffuse_color=(*rgb,1)
    bs=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED');bs.inputs['Base Color'].default_value=(*rgb,1);bs.inputs['Roughness'].default_value=.78;bs.inputs['Metallic'].default_value=.12 if name in ['olive','olive_light','olive_dark','steel','floor','ivory'] else 0
    if name in ['glass','lens']:bs.inputs['Roughness'].default_value=.23;bs.inputs['Metallic'].default_value=.35
    if name in ['screen','lamp','lens']:bs.inputs['Emission Color'].default_value=(*rgb,1);bs.inputs['Emission Strength'].default_value=.3 if name=='lamp' else .1
    materials[name]=mat
def cube(s):
    x,y,z=[v/2 for v in s]
    return [(-x,-y,-z),(x,-y,-z),(x,y,-z),(-x,y,-z),(-x,-y,z),(x,-y,z),(x,y,z),(-x,y,z)],[(0,3,2,1),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7),(4,5,6,7)]
def radial(r,depth,axis,n=16,inner=None):
    verts=[];faces=[]
    def coord(a,b,t):return (t,a,b) if axis=='X' else (a,t,b) if axis=='Y' else (a,b,t)
    rings=[(-depth/2,r),(depth/2,r)] if inner is None else [(-depth/2,r),(depth/2,r),(-depth/2,inner),(depth/2,inner)]
    for t,rr in rings:
        for i in range(n):a=i*math.tau/n;verts.append(coord(rr*math.cos(a),rr*math.sin(a),t))
    for i in range(n):
        j=(i+1)%n;faces.append((i,j,n+j,n+i))
        if inner is not None:faces.extend([(2*n+j,2*n+i,3*n+i,3*n+j),(i,2*n+i,2*n+j,j),(n+j,3*n+j,3*n+i,n+i)])
    if inner is None:faces.extend([tuple(range(n-1,-1,-1)),tuple(range(n,n*2))])
    return verts,faces
def append_mesh(v,f,vs,fs,transform=None):
    off=len(v);v.extend([tuple(transform@Vector(p)) for p in vs] if transform else vs);f.extend([tuple(off+i for i in face) for face in fs])
def tyre(p):
    n=p['segments'];v=[];f=[];idx=[]
    profile=[(-.18,.43),(-.20,.53),(-.17,.63),(-.12,.65),(.12,.65),(.17,.63),(.20,.53),(.18,.43)]
    for x,r in profile:
        for i in range(n):a=i*math.tau/n;v.append((x,r*math.sin(a),r*math.cos(a)))
    for j in range(len(profile)):
        for i in range(n):k=(i+1)%n;l=(j+1)%len(profile);f.append((j*n+i,j*n+k,l*n+k,l*n+i));idx.append(0)
    for i in range(n):
        a=i*math.tau/n
        for band in [-1,0,1]:
            vs,fs=cube((.112,.12,.048));rot=Euler((-a,0,math.radians(band*14))).to_matrix().to_4x4();rot.translation=(band*.132,.655*math.sin(a),.655*math.cos(a));append_mesh(v,f,vs,fs,rot);idx.extend([1 if (i+band)%4 else 0]*len(fs))
    return v,f,idx
def perforated(p):
    v=[];f=[];n=16;w=p['width']/2;h=p['height']/3;d=p['depth']
    for col in range(2):
        for row in range(3):
            yc=(col-.5)*w;zc=(row-1)*h;vv=[]
            for xx,inner in [(-d/2,False),(d/2,False),(-d/2,True),(d/2,True)]:
                for k in range(n):
                    a=k*math.tau/n;co,si=math.cos(a),math.sin(a)
                    if inner:yy=.058*co;zz=.075*si
                    else:rr=min((w/2)/max(abs(co),1e-8),(h/2)/max(abs(si),1e-8));yy=rr*co;zz=rr*si
                    vv.append((xx,yc+yy,zc+zz))
            ff=[]
            for k in range(n):
                j=(k+1)%n;ff.extend([(k,j,n+j,n+k),(2*n+j,2*n+k,3*n+k,3*n+j),(k,2*n+k,2*n+j,j),(n+j,3*n+j,3*n+k,n+k)])
            append_mesh(v,f,vv,ff)
    return v,f
objects=[]
def make(name,v,f,mat,parent,center=(0,0,0),rotation=(0,0,0),bevel=0,source_id=None,extra_mats=None,indices=None):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(v,[],f);mesh.update();bm=bmesh.new();bm.from_mesh(mesh);bmesh.ops.recalc_face_normals(bm,faces=bm.faces);bm.to_mesh(mesh);bm.free()
    ob=bpy.data.objects.new(name,mesh);coll.objects.link(ob);ob.parent=groups[parent];ob.location=Vector(center)-pivots[parent];ob.rotation_euler=rotation
    for key in [mat]+(extra_mats or []):mesh.materials.append(materials[key])
    if indices:
        for poly,i in zip(mesh.polygons,indices):poly.material_index=i
    if source_id:ob['source_part_id']=source_id
    ob['component']=parent;objects.append(ob)
    if bevel:
        mod=ob.modifiers.new('Manufactured edge chamfer','BEVEL');mod.width=bevel;mod.segments=1
        norm=ob.modifiers.new('Weighted plate normals','WEIGHTED_NORMAL');norm.keep_sharp=True
    return ob
for p in data['parts']:
    shape=p['shape'];center=Vector(p.get('center',(0,0,0)));rot=tuple(math.radians(a) for a in p.get('rotation',(0,0,0)));indices=None;extras=None
    if shape=='box':v,f=cube(p['size'])
    elif shape=='mesh':v,f=p['vertices'],p['faces']
    elif shape=='beam':
        a,b=Vector(p['a']),Vector(p['b']);center=(a+b)/2;v,f=cube((p['width'],p['width'],(b-a).length));rot=(b-a).to_track_quat('Z','Y').to_euler()
    elif shape in ['cylinder','ring']:v,f=radial(p['radius'],p['depth'],p['axis'],p['segments'],p['radius']-p['thickness'] if shape=='ring' else None)
    elif shape=='tire':v,f,indices=tyre(p);extras=['rubber_dust']
    elif shape=='seat_back':v,f=perforated(p)
    else:raise ValueError(shape)
    ob=make(p['name'],v,f,p['material'],p['group'],center,rot,p.get('bevel',0),p['id'],extras,indices)
    for cut in p.get('cutouts',[]):
        vv,ff=cube(cut['size']);cutter=make('CUTTER',vv,ff,p['material'],'ROOT',cut['center']);mod=ob.modifiers.new('True aperture','BOOLEAN');mod.operation='DIFFERENCE';mod.object=cutter
        bpy.context.view_layer.objects.active=ob;bpy.ops.object.modifier_apply(modifier=mod.name);objects.remove(cutter);bpy.data.objects.remove(cutter,do_unlink=True)

# Native drivers keep telescoping cylinders attached for every ramp angle.
for act in data['actuators']:
    base=bpy.data.objects.new(act['id']+' base',None);coll.objects.link(base);base.parent=root;base.location=act['fixed_m']
    end=bpy.data.objects.new(act['id']+' moving',None);coll.objects.link(end);end.parent=groups['RAMP'];end.location=Vector(act['moving_closed_m'])-pivots['RAMP']
    con=base.constraints.new('DAMPED_TRACK');con.target=end;con.track_axis='TRACK_Z'
    v,f=radial(.037,act['tube_length_m'],'Z',12);tube=make(act['id']+' tube',v,f,'olive_dark','ROOT');tube.parent=base;tube.location=(0,0,act['tube_length_m']/2);tube.rotation_euler=(0,0,0)
    v,f=radial(.022,1,'Z',12);v=[(x,y,z+.5) for x,y,z in v];rod=make(act['id']+' rod',v,f,'steel','ROOT');rod.parent=base;rod.location=(0,0,act['rod_start_m']);rod.rotation_euler=(0,0,0)
    drv=rod.driver_add('scale',2).driver;var=drv.variables.new();var.name='distance';var.type='LOC_DIFF';var.targets[0].id=base;var.targets[1].id=end;drv.expression='max(0.01,distance-0.40)'

poses={1:'travel',41:'boarding',61:'travel',81:'door',101:'travel',121:'service',161:'travel'}
def pose(name):
    for g in data['groups']:
        ob=groups[g['id']];ob.rotation_euler=(0,0,0)
        if g['id'].startswith('WHEEL_'):ob.location=pivots[g['id']]
    if name in ['boarding','service']:
        groups['RAMP'].rotation_euler.x=math.radians(-P['ramp_open_deg'])
        for g in data['groups']:
            if 'HATCH' in g['id']:
                index={'X':0,'Y':1,'Z':2}[g['axis']];groups[g['id']].rotation_euler[index]=math.radians(g['open_deg'])
    if name=='door':groups['RAMP_DOOR'].rotation_euler.z=math.radians(95)
    if name=='service':
        for g in data['groups']:
            if g['role']=='folding_troop_pan':groups[g['id']].rotation_euler.y=math.radians(g['open_deg'])
            if g['id'].startswith('WHEEL_') and g['id'][-1] in '12':groups[g['id']].rotation_euler.z=math.radians(25 if g['id'][-1]=='1' else 17)
    bpy.context.view_layer.update()
for frame,name in poses.items():
    pose(name)
    for g in data['groups']:
        if g.get('axis') or g['id'].startswith('WHEEL_'):groups[g['id']].keyframe_insert('rotation_euler',frame=frame)
    scene.timeline_markers.new(name,frame=frame)
scene.frame_set(1);pose('travel')
def select_vehicle():
    bpy.ops.object.select_all(action='DESELECT')
    for ob in coll.objects:ob.select_set(True)
    bpy.context.view_layer.objects.active=root
select_vehicle();bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/eitan_E01_closed.glb'),export_format='GLB',use_selection=True,export_animations=False)
scene.frame_set(41);pose('boarding');select_vehicle();bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/eitan_E01_boarding.glb'),export_format='GLB',use_selection=True,export_animations=False)
scene.frame_set(1);pose('travel')
stage=bpy.data.collections.new('STUDIO / not exported');scene.collection.children.link(stage)
for j,y in enumerate([-.1,1.1,2.4]):
    lamp=bpy.data.lights.new('Interior inspection light '+str(j),'AREA');lamp.energy=12;lamp.shape='RECTANGLE';lamp.size=.28;lamp.size_y=.09
    lo=bpy.data.objects.new(lamp.name,lamp);stage.objects.link(lo);lo.location=(0,y,2.46)
def stage_link(ob):
    for c in list(ob.users_collection):c.objects.unlink(ob)
    stage.objects.link(ob)
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.015));ground=bpy.context.object;ground.name='STUDIO floor';stage_link(ground)
mat=bpy.data.materials.new('STUDIO / slate');mat.use_nodes=True;s=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED');s.inputs['Base Color'].default_value=(.075,.085,.088,1);s.inputs['Roughness'].default_value=.7;ground.data.materials.append(mat)
for name,loc,power,size in [('Key',(-7,-9,13),2400,9),('Fill',(8,-3,8),1500,8),('Rim',(-3,10,10),2600,7)]:
    light=bpy.data.lights.new(name,'AREA');light.energy=power;light.shape='DISK';light.size=size;ob=bpy.data.objects.new(name,light);stage.objects.link(ob);ob.location=loc;ob.rotation_euler=(Vector((0,0,1.3))-ob.location).to_track_quat('-Z','Y').to_euler()
cam=bpy.data.cameras.new('Inspection camera');cam.type='ORTHO';camera=bpy.data.objects.new('Inspection camera',cam);stage.objects.link(camera);scene.camera=camera
def aim(loc,target,scale,ortho=True):
    cam.type='ORTHO' if ortho else 'PERSP';cam.ortho_scale=scale;cam.lens=24;camera.location=loc;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler()
aim((10,-13,8),(0,0,1.6),10.6)
for screen in bpy.data.screens:
    for a in screen.areas:
        if a.type=='VIEW_3D':a.spaces.active.region_3d.view_perspective='CAMERA';a.spaces.active.shading.type='MATERIAL'
scene['scope']='Dimensional game model, articulated mechanism demonstrations. Not a manufactured or game-registered vehicle.'
scene['geometry_authority']='source/eitan_E01.json';scene['nominal_size']='8 x 3 x 3 m excluding flexible antenna';scene['standing_player_clearance']=False
bpy.context.preferences.filepaths.save_version=0;bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/eitan_E01.blend'))
for view in args.views.split(','):
    if not view:continue
    for ob in objects:ob.hide_render=False
    scene.frame_set(1);pose('travel');cam.type='ORTHO'
    if view=='hero':aim((10,-13,7),(0,0,1.55),10.6)
    elif view=='rear':aim((-10,13,7),(0,0,1.55),10.6)
    elif view=='rear_detail':aim((-5.5,11.5,4.8),(0,2.25,1.75),5.6)
    elif view=='boarding':scene.frame_set(41);pose('boarding');aim((-7,13,6),(0,.7,1.35),10.8)
    elif view=='door':scene.frame_set(81);pose('door');aim((6,12,5),(0,1.0,1.4),8.5)
    elif view=='hatches':scene.frame_set(41);pose('boarding');aim((6,-6,11),(0,-.1,1.5),9.1)
    elif view=='side':aim((16,0,3),(0,0,1.6),9.8)
    elif view=='driver':aim((-.68,-2.21,2.17),(-.65,-2.745,1.85),4,False)
    elif view=='interior':
        scene.frame_set(41);pose('boarding');aim((0,5.1,1.85),(0,-.55,1.68),4,False)
    elif view=='cutaway':
        for ob in groups['ROOF'].children_recursive:
            if ob.type=='MESH':ob.hide_render=True
        for ob in objects:
            if ob.name.startswith(('sloped modular side 1','interior liner 1','wheel arch scallops 1','overhead service channel 1')):ob.hide_render=True
        for ob in groups['ANTENNAS'].children_recursive:ob.hide_render=True
        aim((9,9,10),(0,.0,1.3),10.5)
    else:raise ValueError(view)
    scene.render.filepath=str(ROOT/f'preview/eitan_E01_{view}.png');bpy.ops.render.render(write_still=True)
report={'revision':data['revision'],'source_parts':len(data['parts']),'mesh_objects':len(objects),'groups':len(groups),'metres_per_blender_unit':1,
 'seats':len(data['seats']),'troop_seats':9,'crew_seats':3,'roof_openings':3,'roof_lids':4,'rear_openings':2,'rear_ramp_open_deg':P['ramp_open_deg'],
 'independent_door_in_ramp':True,'driven_telescoping_actuators':2,'folding_seat_pans':9,'pose_frames':poses,'rendered_views':args.views.split(','),'runtime_registered':False,'offroad_driving_tested':False}
(ROOT/'reports/BLENDER_BUILD.json').write_text(json.dumps(report,indent=2)+'\n');print('EITAN_E01_COMPLETE',json.dumps(report))
