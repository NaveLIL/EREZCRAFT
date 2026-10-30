"""Build and photograph the actual MC01 mesh. No generated mockup images."""
from pathlib import Path
import bpy,bmesh,math,json,argparse,sys
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--views',default='hero,front,aft,side,deck,face');parser.add_argument('--resolution',type=int,default=1600)
args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
data=json.loads((ROOT/'source/calico_MC01.json').read_text(encoding='utf8'))
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
scene=bpy.context.scene;scene.name='MIKAN / MC01 / calico launch'
scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
scene.render.resolution_x=args.resolution;scene.render.resolution_y=round(args.resolution*.75);scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='AgX'
scene.render.fps=24;scene.frame_start=1;scene.frame_end=97
scene.world.use_nodes=True;bg=scene.world.node_tree.nodes.get('Background');bg.inputs['Color'].default_value=(.17,.2,.23,1);bg.inputs['Strength'].default_value=.45
vehicle=bpy.data.collections.new('MC01 / Calico boat');scene.collection.children.link(vehicle)
root=bpy.data.objects.new('MIKAN_MC01',None);vehicle.objects.link(root)
root['geometry_authority']='source/calico_MC01.json';root['units']=data['units'];root['runtime_registered']=False;root['physics_verified']=False
groups={}
for name in sorted({p['group'] for p in data['parts']}):
    ob=bpy.data.objects.new(name,None);vehicle.objects.link(ob);ob.parent=root;groups[name]=ob
groups['rotor'].location=(0,6.35,5.55)
materials={}
def linear(c):return c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4
for name,(hexcolor,metal,rough) in data['palette'].items():
    rgb=[linear(int(hexcolor[i:i+2],16)/255) for i in (1,3,5)]
    mat=bpy.data.materials.new('MC01 / '+name);mat.diffuse_color=(*rgb,1);mat.use_nodes=True
    bsdf=mat.node_tree.nodes.get('Principled BSDF');bsdf.inputs['Base Color'].default_value=(*rgb,1);bsdf.inputs['Metallic'].default_value=metal;bsdf.inputs['Roughness'].default_value=rough
    if name=='glass':bsdf.inputs['Transmission Weight'].default_value=.86;bsdf.inputs['IOR'].default_value=1.45;bsdf.inputs['Alpha'].default_value=.38
    if name in ['lamp','screen']:
        bsdf.inputs['Emission Color'].default_value=(*rgb,1);bsdf.inputs['Emission Strength'].default_value=.6 if name=='lamp' else .15
    materials[name]=mat

def cube(s):
    x,y,z=[v/2 for v in s]
    return [(-x,-y,-z),(x,-y,-z),(x,y,-z),(-x,y,-z),(-x,-y,z),(x,-y,z),(x,y,z),(-x,y,z)],[(0,3,2,1),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7),(4,5,6,7)]
objects=[]
for p in data['parts']:
    shape=p['shape'];center=Vector(p.get('center',(0,0,0)));rotation=tuple(math.radians(x) for x in p.get('rotation',(0,0,0)))
    if shape=='box':verts,faces=cube(p['size'])
    elif shape=='mesh':verts,faces=p['vertices'],p['faces']
    elif shape=='beam':
        a,b=Vector(p['a']),Vector(p['b']);center=(a+b)/2;verts,faces=cube((p['width'],p['width'],(b-a).length));rotation=(b-a).to_track_quat('Z','Y').to_euler()
    elif shape=='ring':
        n=p['segments'];r=p['radius'];t=p['thickness'];d=p['depth'];verts=[];faces=[]
        for y,rr in [(-d/2,r),(d/2,r),(-d/2,r-t),(d/2,r-t)]:
            for i in range(n):
                a=math.tau*i/n;verts.append((math.cos(a)*rr,y,math.sin(a)*rr))
        for i in range(n):
            j=(i+1)%n;faces.extend([(i,j,n+j,n+i),(2*n+j,2*n+i,3*n+i,3*n+j),(i,2*n+i,2*n+j,j),(n+j,3*n+j,3*n+i,n+i)])
    elif shape=='cylinder':
        n=p['segments'];r=p['radius'];d=p['depth'];verts=[]
        for y in [-d/2,d/2]:
            for i in range(n):a=math.tau*i/n;verts.append((r*math.cos(a),y,r*math.sin(a)))
        faces=[tuple(range(n-1,-1,-1)),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
    else:raise ValueError(shape)
    mesh=bpy.data.meshes.new(p['name']);mesh.from_pydata(verts,[],faces);mesh.update()
    bm=bmesh.new();bm.from_mesh(mesh);bmesh.ops.recalc_face_normals(bm,faces=bm.faces);bm.to_mesh(mesh);bm.free()
    ob=bpy.data.objects.new(p['name'],mesh);vehicle.objects.link(ob);ob.parent=groups[p['group']];ob.location=center-groups[p['group']].location;ob.rotation_euler=rotation;ob.data.materials.append(materials[p['material']])
    ob['source_part']=p['name'];ob['source_group']=p['group'];ob['material_key']=p['material']
    bevel=p.get('bevel',.018 if shape=='beam' else 0)
    if bevel:
        mod=ob.modifiers.new('Small chamfer / voxel edge','BEVEL');mod.width=bevel;mod.segments=1;mod.affect='EDGES'
        norm=ob.modifiers.new('Weighted face normals','WEIGHTED_NORMAL');norm.keep_sharp=True;norm.weight=50
    objects.append(ob)
# Animation is deliberately an art demonstration. It does not assert a Sable motor.
rotor=groups['rotor'];rotor.rotation_mode='XYZ'
for frame,angle in [(1,0),(97,math.tau*2)]:rotor.rotation_euler.y=angle;rotor.keyframe_insert('rotation_euler',index=1,frame=frame)
scene.frame_set(1)
stage=bpy.data.collections.new('STUDIO / not part of vessel');scene.collection.children.link(stage)
def stage_link(ob):
    for c in list(ob.users_collection):c.objects.unlink(ob)
    stage.objects.link(ob)
bpy.ops.mesh.primitive_plane_add(size=300,location=(0,0,.48));plane=bpy.context.object;plane.name='STUDIO / slate sea-level plane';stage_link(plane)
mat=bpy.data.materials.new('STUDIO / muted slate');mat.use_nodes=True;s=mat.node_tree.nodes.get('Principled BSDF');s.inputs['Base Color'].default_value=(.07,.1,.12,1);s.inputs['Roughness'].default_value=.4;s.inputs['Metallic'].default_value=.2;plane.data.materials.append(mat)
for name,pos,power,size in [('Key',(-10,-17,23),4000,13),('Fill',(15,-7,15),2700,11),('Rim',(-5,15,19),4500,10)]:
    light=bpy.data.lights.new(name,'AREA');light.energy=power;light.shape='DISK';light.size=size;ob=bpy.data.objects.new(name,light);stage.objects.link(ob);ob.location=pos;ob.rotation_euler=(Vector((0,0,3.5))-ob.location).to_track_quat('-Z','Y').to_euler()
cam=bpy.data.cameras.new('Review camera');cam.type='ORTHO';camera=bpy.data.objects.new('Review camera',cam);stage.objects.link(camera);scene.camera=camera
bpy.context.view_layer.update()
points=[ob.matrix_world@Vector(c) for ob in objects for c in ob.bound_box]
lo=[min(p[i] for p in points) for i in range(3)];hi=[max(p[i] for p in points) for i in range(3)]
def aim(location,target,scale,fit=True):
    camera.location=location;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();cam.ortho_scale=scale;bpy.context.view_layer.update()
    if fit:
        q=[world_to_camera_view(scene,camera,p) for p in points];span=max(max(v.x for v in q)-min(v.x for v in q),max(v.y for v in q)-min(v.y for v in q))
        cam.ortho_scale*=span*1.16
        q=[world_to_camera_view(scene,camera,p) for p in points];cx=(max(v.x for v in q)+min(v.x for v in q))/2-.5;cy=(max(v.y for v in q)+min(v.y for v in q))/2-.5
        camera.location+=camera.rotation_euler.to_matrix()@Vector((cx*cam.ortho_scale,cy*cam.ortho_scale*(scene.render.resolution_y/scene.render.resolution_x),0))
def view(name):
    if name=='hero':aim((20,-28,18),(0,0,4),22)
    elif name=='front':aim((0,-35,10),(0,-1,4),20)
    elif name=='aft':aim((-19,27,18),(0,0,4),22)
    elif name=='side':aim((32,0,11),(0,0,4),22)
    elif name=='deck':aim((12,15,30),(0,0,3.5),22)
    elif name=='face':aim((8,-30,10),(0,-5.6,4.9),11,False)
    else:raise ValueError(name)
    scene.render.filepath=str(ROOT/f'preview/calico_MC01_{name}.png');bpy.ops.render.render(write_still=True)
aim((20,-28,18),(0,0,4),22)
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.shading.type='MATERIAL'
scene['status']='Native Blender art prototype. Not installed in Minecraft. Physics not verified.'
scene['reference']='references/owner_calico_sketch.jpg';scene['geometry_authority']='source/calico_MC01.json'
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/calico_MC01.blend'))
bpy.ops.object.select_all(action='DESELECT')
for ob in vehicle.objects:ob.select_set(True)
bpy.context.view_layer.objects.active=root
bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/calico_MC01.glb'),export_format='GLB',use_selection=True,export_animations=False)
report={'revision':'MC01','parts':len(objects),'groups':len(groups),'bounds_min':lo,'bounds_max':hi,'dimensions_blocks_xyz':[hi[i]-lo[i] for i in range(3)],
 'muzzle_projection_blocks':.98,'solid_raised_muzzle':True,'seats':4,'storage_modules':6,'single_stern_fan':True,
 'blender_version':bpy.app.version_string,'views':args.views.split(','),'runtime_registered':False,'physics_verified':False,'render_type':'Native Blender mesh, studio lighting, no generated concept-image substitution'}
(ROOT/'reports/BLENDER_BUILD.json').write_text(json.dumps(report,indent=2)+'\n')
for name in args.views.split(','):view(name)
print('CALICO_MC01_COMPLETE',json.dumps(report))
