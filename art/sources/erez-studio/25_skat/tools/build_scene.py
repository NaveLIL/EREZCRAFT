"""Rebuild SK01 from its geometry JSON in isolated Blender; render inspection views."""
from pathlib import Path
import bpy, math, json, argparse, sys
from mathutils import Vector, Euler
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--views',default='hero,aft,cutaway,hold,cabin,top,side');parser.add_argument('--resolution',type=int,default=1600)
args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
model=json.loads((ROOT/'source/skat_SK01.json').read_text())
floor_z=model['walkable_floor_z']
for ob in list(bpy.data.objects):bpy.data.objects.remove(ob,do_unlink=True)
scene=bpy.context.scene;scene.name='SKAT / SK01 / cargo expedition catamaran'
scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
scene.render.resolution_x=args.resolution;scene.render.resolution_y=round(args.resolution*.6875);scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='AgX'
scene.render.fps=24;scene.frame_start=1;scene.frame_end=49
scene.world.use_nodes=True
world=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND');world.inputs['Color'].default_value=(.16,.20,.23,1);world.inputs['Strength'].default_value=.4
collection=bpy.data.collections.new('SK01 / Vehicle');scene.collection.children.link(collection)
groups={};meshes=[]
for spec in model['groups']:
    ob=bpy.data.objects.new(spec['name'],None);collection.objects.link(ob);groups[spec['name']]=ob
    if spec['parent']:
        ob.parent=groups[spec['parent']];ob.location=Vector(spec['pivot'])-Vector(next(g['pivot'] for g in model['groups'] if g['name']==spec['parent']))
    else:ob.location=spec['pivot']
    ob['source_group']=spec['name'];ob['role']=spec['role'];ob.empty_display_size=.2
root=groups['SKAT_SK01'];root['units']='1 Blender unit = 1 Minecraft block (game art)';root['authoritative_source']='source/skat_SK01.json'
root['status']='Visual prototype. Not registered in Minecraft. No buoyancy/corrosion/filtration simulation.'
materials={}
for spec in json.loads((ROOT/'source/texture_manifest.json').read_text()):
    mat=bpy.data.materials.new('SK01 / '+spec['name']);mat.use_nodes=True
    shader=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    image=mat.node_tree.nodes.new('ShaderNodeTexImage');image.image=bpy.data.images.load(str(ROOT/spec['file']));image.image.pack();image.interpolation='Closest';image.extension='REPEAT'
    mat.node_tree.links.new(image.outputs['Color'],shader.inputs['Base Color']);mat.node_tree.links.new(image.outputs['Alpha'],shader.inputs['Alpha'])
    shader.inputs['Roughness'].default_value=spec['roughness'];shader.inputs['Metallic'].default_value=.15 if spec['name'] in ['copper','brass','steel'] else 0
    shader.inputs['Specular IOR Level'].default_value=.3
    mat.node_tree.links.new(image.outputs['Color'],shader.inputs['Emission Color']);shader.inputs['Emission Strength'].default_value=spec['emission']
    materials[spec['name']]=mat

# Each face tiles a ready-made 16x16 bitmap; no generated detail textures.
def make_mesh(name,verts,faces,mat,parent,location=(0,0,0),rotation=(0,0,0)):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],faces);mesh.update();mesh.materials.append(materials[mat])
    uv=mesh.uv_layers.new(name='16px per block / repeat')
    for polygon in mesh.polygons:
        corners=[Vector(verts[mesh.loops[li].vertex_index]) for li in polygon.loop_indices]
        # Local planar coordinates preserve texel density on long armor panels.
        origin=corners[0];u=(corners[1]-origin).normalized();normal=polygon.normal.normalized();v=normal.cross(u)
        for li,p in zip(polygon.loop_indices,corners):uv.data[li].uv=((p-origin).dot(u),(p-origin).dot(v))
    ob=bpy.data.objects.new(name,mesh);collection.objects.link(ob);ob.parent=parent;ob.location=location;ob.rotation_euler=rotation
    ob['source_part']=name;meshes.append(ob);return ob
def cube_data(size):
    x,y,z=[d/2 for d in size]
    vertices=[(-x,-y,-z),(x,-y,-z),(x,y,-z),(-x,y,-z),(-x,-y,z),(x,-y,z),(x,y,z),(-x,y,z)]
    return vertices,[(3,2,1,0),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7),(4,5,6,7)]
for part in model['parts']:
    parent=groups[part['group']];pivot=Vector(next(g['pivot'] for g in model['groups'] if g['name']==part['group']))
    shape=part['shape'];rotation=(0,0,0)
    if shape=='cube':
        verts,faces=cube_data(part['size']);center=Vector(part['center']);rotation=tuple(math.radians(v) for v in part['rotation'])
    elif shape=='beam':
        a=Vector(part['a']);b=Vector(part['b']);center=(a+b)/2;verts,faces=cube_data((part['width'],part['depth'],(b-a).length));rotation=(b-a).to_track_quat('Z','Y').to_euler()
    elif shape=='ring':
        n=part['segments'];r=part['radius'];t=part['thickness'];d=part['depth'];center=Vector(part['center']);verts=[];faces=[]
        for yy,rr in [(-d/2,r),(d/2,r),(-d/2,r-t),(d/2,r-t)]:
            for i in range(n):
                a=2*math.pi*(i+.5)/n;verts.append((math.cos(a)*rr,yy,math.sin(a)*rr))
        for i in range(n):
            j=(i+1)%n
            faces.extend([(i,j,n+j,n+i),(2*n+j,2*n+i,3*n+i,3*n+j),(i,2*n+i,2*n+j,j),(n+j,3*n+j,3*n+i,n+i)])
    elif shape=='cylinder':
        n=part['segments'];r=part['radius'];d=part['depth'];center=Vector(part['center']);verts=[]
        for yy in [-d/2,d/2]:
            for i in range(n):
                a=2*math.pi*i/n;verts.append((math.cos(a)*r,yy,math.sin(a)*r))
        faces=[tuple(reversed(range(n))),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
        if part['axis']=='Z':rotation=(math.pi/2,0,0)
    else:raise ValueError(shape)
    ob=make_mesh(part['name'],verts,faces,part['material'],parent,center-pivot,rotation)
    if part.get('tag'):ob['design_tag']=part['tag']

# Two synchronized rotor animations. This timeline demonstrates moving model parts only.
for index,name in enumerate(model['rotor_groups']):
    ob=groups[name];ob.rotation_mode='XYZ'
    for frame in [1,49]:
        ob.rotation_euler=(0,(-1 if index else 1)*2*math.pi*(frame-1)/48,0);ob.keyframe_insert(data_path='rotation_euler',frame=frame)
    for slot in ob.animation_data.action.slots:
        for layer in ob.animation_data.action.layers:
            for strip in layer.strips:
                try:
                    for fc in strip.channelbag(slot).fcurves:
                        for key in fc.keyframe_points:key.interpolation='LINEAR'
                except (AttributeError,TypeError):pass
scene.timeline_markers.new('Rotors / visual demonstration',frame=1)
scene.frame_set(1);bpy.context.view_layer.update()
points=[o.matrix_world@Vector(v) for o in meshes for v in o.bound_box]
minimum=[min(p[i] for p in points) for i in range(3)];maximum=[max(p[i] for p in points) for i in range(3)]

# Presentation-only water, neutral floor and scale figure are excluded from vehicle exports.
studio=bpy.data.collections.new('STUDIO / presentation only');scene.collection.children.link(studio)
def simple_mat(name,color,roughness=.8,metallic=0):
    mat=bpy.data.materials.new(name);mat.use_nodes=True;shader=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    shader.inputs['Base Color'].default_value=(*color,1);shader.inputs['Roughness'].default_value=roughness;shader.inputs['Metallic'].default_value=metallic;return mat
def studio_mesh(name,verts,faces,material):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],faces);mesh.update();mesh.materials.append(material)
    ob=bpy.data.objects.new(name,mesh);studio.objects.link(ob);return ob
watermat=simple_mat('STUDIO / toxic sea visualization',(.14,.012,.028),.28)
N=100;step=.8;verts=[];faces=[]
for j in range(N+1):
    for i in range(N+1):
        x=(i-N/2)*step;y=(j-N/2)*step
        z=.93+.025*math.sin(x*2.7+y*1.8)+.016*math.sin(x*5-y*3.4)
        verts.append((x,y,z))
for j in range(N):
    for i in range(N):
        k=j*(N+1)+i;faces.append((k,k+1,k+N+2,k+N+1))
water=studio_mesh('STUDIO / water; no physical waves',verts,faces,watermat)
floor=studio_mesh('STUDIO / neutral floor',[(-100,-100,-.1),(100,-100,-.1),(100,100,-.1),(-100,100,-.1)],[(0,1,2,3)],simple_mat('STUDIO / ground',(.14,.17,.18)))
figure=[];figmat=simple_mat('STUDIO / 1.8 block scale figure',(.3,.4,.39))
for name,c,size in [('Head',(0,0,1.56),(.43,.43,.48)),('Torso',(0,0,.99),(.5,.25,.66)),('LegL',(-.13,0,.33),(.23,.26,.66)),('LegR',(.13,0,.33),(.23,.26,.66)),('ArmL',(-.37,0,.99),(.23,.25,.66)),('ArmR',(.37,0,.99),(.23,.25,.66))]:
    verts,faces=cube_data(size);ob=studio_mesh('SCALE / '+name,verts,faces,figmat);ob.location=(c[0]-3.65,c[1]-3.1,c[2]+floor_z);figure.append(ob)
lights=[]
for name,location,power,size in [('Key',(-8,-10,17),4400,10),('Fill',(10,-5,11),2900,10),('Rim',(2,12,14),5000,8)]:
    data=bpy.data.lights.new(name,'AREA');data.energy=power;data.shape='DISK';data.size=size
    ob=bpy.data.objects.new('STUDIO / '+name,data);studio.objects.link(ob);ob.location=location;ob.rotation_euler=(Vector((0,0,2.5))-ob.location).to_track_quat('-Z','Y').to_euler();lights.append(ob)
data=bpy.data.lights.new('Interior practical','AREA');data.energy=90;data.shape='RECTANGLE';data.size=3;data.size_y=5
ob=bpy.data.objects.new('STUDIO / Interior practical',data);studio.objects.link(ob);ob.location=(0,1,4.6);lights.append(ob)
camera_data=bpy.data.cameras.new('SK01 Review Camera');camera=bpy.data.objects.new('SK01 Review Camera',camera_data);studio.objects.link(camera);scene.camera=camera;camera_data.type='ORTHO';camera_data.lens=38
def aim(location,target,scale):
    camera.location=location;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();camera_data.ortho_scale=scale
def set_cutaway(on):
    for g in model['cutaway_hide_groups']:
        for ob in groups[g].children_recursive:ob.hide_render=on;ob.hide_viewport=on
    # Hide starboard hold and cabin side panels for a readable oblique section.
    for ob in meshes:
        if ob.parent==groups['03_Cargo_hold_shell'] and ('_1_' in ob.name or ob.name.endswith('_1')):
            ob.hide_render=on;ob.hide_viewport=on
    water.hide_render=on
    for ob in figure:ob.hide_render=on
def reset():
    for ob in meshes:ob.hide_render=False;ob.hide_viewport=False
    water.hide_render=False
    for ob in figure:ob.hide_render=False
    scene.frame_set(1)
def render(view):
    reset()
    if view=='hero':aim((-19,-26,18),(0,0,2.6),23)
    elif view=='aft':aim((19,25,16),(0,0,2.8),23)
    elif view=='cutaway':
        set_cutaway(True);aim((17,-19,23),(0,-.1,2.5),24)
    elif view=='top':
        set_cutaway(True);aim((0,-.01,35),(0,0,2.5),26)
    elif view=='side':
        water.hide_render=True;aim((-27,-.01,6),(0,0,2.7),25)
    elif view=='hold':
        set_cutaway(True);aim((9,10,13),(0,1.0,3.0),12)
        for ob in groups['14_Cargo_crane'].children_recursive:ob.hide_render=True
        for ob in groups['12_Drive_machinery'].children_recursive:ob.hide_render=True
        for name in model['rotor_groups']:
            for ob in groups[name].children_recursive:ob.hide_render=True
        for ob in groups['03_Cargo_hold_shell'].children_recursive:
            if '_aft_' in ob.name:ob.hide_render=True
    elif view=='cabin':
        set_cutaway(True);aim((8,-12,12),(0,-4.4,3.2),8.6)
        for ob in meshes:
            if ob.name.startswith('Cabin_front_') or ob.name.startswith('Cabin_corner_'):ob.hide_render=True
    else:raise ValueError(view)
    scene.render.filepath=str(ROOT/f'preview/skat_SK01_{view}.png');bpy.ops.render.render(write_still=True)

# Self-contained scene and static GLB. A separate animation GLB is deliberately not needed
# for this review: source Blender contains the two rotor actions without split-track issues.
reset();aim((-19,-26,18),(0,0,2.6),23)
scene['SK01_status']='Art prototype, not an operational Minecraft vessel'
scene['cargo_capacity_target']='24 x 54 = 1296 slots; inventory not implemented'
scene['geometry_source']='source/skat_SK01.json';scene['physics_verified']=False
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':
            area.spaces.active.region_3d.view_distance=23;area.spaces.active.region_3d.view_location=(0,0,2.6)
            area.spaces.active.region_3d.view_rotation=camera.rotation_euler.to_quaternion();area.spaces.active.shading.type='MATERIAL'
bpy.ops.object.select_all(action='DESELECT')
for ob in collection.objects:ob.select_set(True)
bpy.context.view_layer.objects.active=root
bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/skat_SK01.glb'),export_format='GLB',use_selection=True,export_animations=False)
bpy.ops.object.select_all(action='DESELECT');bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/skat_SK01.blend'))
report={'revision':'SK01','blender_version':bpy.app.version_string,'meshes':len(meshes),'groups':len(groups),
    'bounds_min':minimum,'bounds_max':maximum,'dimensions_blocks_xyz':[maximum[i]-minimum[i] for i in range(3)],
    'all_source_images_packed':all(im.packed_file for im in bpy.data.images if im.source=='FILE'),
    'cargo_modules':24,'target_capacity_slots':1296,'renders_are_studio':True,'physics_verified':False,'runtime_registered':False}
(ROOT/'reports/BLENDER_BUILD.json').write_text(json.dumps(report,indent=2)+'\n')
for view in args.views.split(','):
    if view:render(view)
print('SKAT_SK01_COMPLETE',json.dumps(report))
