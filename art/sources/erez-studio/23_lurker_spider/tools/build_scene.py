"""Run only in a separate background Blender process. Studio previews, not game evidence."""
import bpy, json, math, argparse, sys
from pathlib import Path
from mathutils import Matrix, Vector, Euler
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--revision',default='LS02')
parser.add_argument('--baseline',default='original')
parser.add_argument('--baseline-role',default='lurker')
parser.add_argument('--role',default='lurker')
parser.add_argument('--variant-scale',type=float,default=.85)
parser.add_argument('--views',default='quarter,front,top,baseline')
parser.add_argument('--resolution',type=int,default=1440)
args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
REV=args.revision
STEM=f'{args.role}_{REV}'
GAME=Path('D:/repos/Minecraft/EREZCRAFT')
SCALE=args.variant_scale/16
# Minecraft model-space (Y down) to Blender Z-up; front remains negative X.
CONVERT=Matrix(((1,0,0,0),(0,0,-1,0),(0,-1,0,0),(0,0,0,1)))
def number(value):
    try:return float(str(value).strip())
    except (ValueError,TypeError):return 0.0
for ob in list(bpy.data.objects):bpy.data.objects.remove(ob,do_unlink=True)
scene=bpy.context.scene;scene.name=f'{args.role} {REV} | model study'
scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
scene.render.resolution_x=args.resolution;scene.render.resolution_y=int(args.resolution*.75);scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.world.use_nodes=True
bg=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND')
bg.inputs['Color'].default_value=(.16,.18,.21,1);bg.inputs['Strength'].default_value=.4
material=bpy.data.materials.new('Existing Lurker atlas / unchanged 128x128');material.use_nodes=True
bsdf=next(n for n in material.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
tex=material.node_tree.nodes.new('ShaderNodeTexImage')
tex.image=bpy.data.images.load(str(ROOT/'source/cave_rift_spider_lurker.png'));tex.image.pack();tex.interpolation='Closest'
material.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
material.node_tree.links.new(tex.outputs['Alpha'],bsdf.inputs['Alpha'])
bsdf.inputs['Roughness'].default_value=.88
bsdf.inputs['Specular IOR Level'].default_value=.16
material.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Emission Color']);bsdf.inputs['Emission Strength'].default_value=.12
palette_materials={}
def model_materials(data,path):
    materials=[]
    for index,entry in enumerate(data.get('textures',[])):
        image_path=path.parent/entry['name']
        if not image_path.is_file() or entry['name']=='cave_rift_spider_lurker.png':
            materials.append(material);continue
        role=data.get('erezcraft_material_roles',{}).get(str(index),{})
        key=(str(image_path),json.dumps(role,sort_keys=True))
        if key not in palette_materials:
            m=bpy.data.materials.new(entry['name']);m.use_nodes=True
            shader=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
            texture=m.node_tree.nodes.new('ShaderNodeTexImage');texture.image=bpy.data.images.load(str(image_path),check_existing=True);texture.image.pack();texture.interpolation='Closest'
            m.node_tree.links.new(texture.outputs['Color'],shader.inputs['Base Color'])
            m.node_tree.links.new(texture.outputs['Alpha'],shader.inputs['Alpha'])
            shader.inputs['Roughness'].default_value=role.get('roughness',.9)
            shader.inputs['Specular IOR Level'].default_value=role.get('specular',.14)
            m.node_tree.links.new(texture.outputs['Color'],shader.inputs['Emission Color'])
            shader.inputs['Emission Strength'].default_value=0 if role.get('role')=='eyes' else .12
            palette_materials[key]=m
        materials.append(palette_materials[key])
    return materials

FACES={
 'north':[(1,1,0),(1,0,0),(0,0,0),(0,1,0)],
 'south':[(0,1,1),(0,0,1),(1,0,1),(1,1,1)],
 'west':[(0,1,0),(0,0,0),(0,0,1),(0,1,1)],
 'east':[(1,1,1),(1,0,1),(1,0,0),(1,1,0)],
 'up':[(0,1,0),(0,1,1),(1,1,1),(1,1,0)],
 'down':[(0,0,1),(0,0,0),(1,0,0),(1,0,1)]}

def import_model(path,label):
    data=json.loads(path.read_text(encoding='utf8'));groups={g['uuid']:g for g in data['groups']};elements={e['uuid']:e for e in data['elements']}
    materials=model_materials(data,path)
    collection=bpy.data.collections.new(label);scene.collection.children.link(collection)
    root=bpy.data.objects.new(label,None);collection.objects.link(root);root.matrix_world=CONVERT@Matrix.Diagonal((SCALE,SCALE,SCALE,1))
    base={}
    for anim in data['animations']:
        if anim['name']!='idle':continue
        for channel in anim['animators'].values():
            for k in channel.get('keyframes',[]):
                if k['channel']=='rotation' and float(k['time'])==0:
                    dp=k['data_points'][0];base[channel['name']]=[number(dp.get(c,0)) for c in 'xyz']
    bones={};meshes=[]
    def walk(node,parent,pivot=None):
        g=groups[node['uuid']];origin=g['origin'];p=pivot or [0,24,0]
        ob=bpy.data.objects.new(label+' / '+g['name'],None);collection.objects.link(ob);ob.parent=parent
        ob.empty_display_size=.8;ob.empty_display_type='PLAIN_AXES'
        ob.location=(origin[0]-p[0],-(origin[1]-p[1])-(5 if pivot is None else 0),origin[2]-p[2])
        rest=base.get(g['name'],[0,0,0]);r=[g['rotation'][i]+rest[i] for i in range(3)]
        ob.rotation_euler=Euler(tuple(math.radians(r[i])*(-1 if i!=1 else 1) for i in range(3)),'XYZ')
        bones[g['name']]=ob;ob['source_group_uuid']=g['uuid']
        for child in node['children']:
            if isinstance(child,dict):walk(child,ob,origin);continue
            e=elements[child];a=e['from'];b=e['to'];points=[];polys=[];uvs=[];face_materials=[]
            for side,cornerbits in FACES.items():
                face=e['faces'].get(side)
                if not face or face.get('texture') is None:continue
                face_materials.append(materials[int(face['texture'])])
                start=len(points)
                for bits in cornerbits:
                    raw=[(a,b)[bit][axis] for axis,bit in enumerate(bits)]
                    vertex=Vector((raw[0]-origin[0],-(raw[1]-origin[1]),raw[2]-origin[2]))
                    if e.get('rotation'):
                        pivot=e.get('origin',[0,0,0]);pivot=Vector((pivot[0]-origin[0],-(pivot[1]-origin[1]),pivot[2]-origin[2]))
                        er=e['rotation'];rotation=Euler(tuple(math.radians(er[i])*(-1 if i!=1 else 1) for i in range(3)),'XYZ').to_matrix()
                        vertex=pivot+rotation@(vertex-pivot)
                    points.append(vertex)
                # Reflection from Blockbench Y-up reverses winding.
                polys.append(tuple(start+i for i in [3,2,1,0]))
                u,v,uu,vv=face['uv'];uvs.append([(u,v),(uu,v),(uu,vv),(u,vv)])
            mesh=bpy.data.meshes.new(e['name']);mesh.from_pydata(points,[],polys);mesh.update()
            uv=mesh.uv_layers.new(name='Original pixel UV')
            for poly,coords,face_material in zip(mesh.polygons,uvs,face_materials):
                if face_material.name not in mesh.materials:mesh.materials.append(face_material)
                poly.material_index=mesh.materials.find(face_material.name)
                for li,(u,v) in zip(poly.loop_indices,coords):uv.data[li].uv=(u/128,1-v/128)
            item=bpy.data.objects.new(e['name'],mesh);collection.objects.link(item);item.parent=ob
            item['source_element_uuid']=e['uuid'];meshes.append(item)
    walk(data['outliner'][0],root)
    bpy.context.view_layer.update()
    low=min((o.matrix_world@Vector(v)).z for o in meshes for v in o.bound_box)
    root.location.z=-low
    # Bake the original MOVE clip on the same pivots for reviewing the new silhouette.
    move=next(a for a in data['animations'] if a['name']=='move')
    for channel in move['animators'].values():
        name=channel.get('name');ob=bones.get(name)
        if ob is None:continue
        group=next(g for g in groups.values() if g['name']==name)
        for k in channel.get('keyframes',[]):
            if k['channel']!='rotation':continue
            dp=k['data_points'][0]
            rotation=[group['rotation'][i]+number(dp.get(c,0)) for i,c in enumerate('xyz')]
            ob.rotation_euler=Euler(tuple(math.radians(rotation[i])*(-1 if i!=1 else 1) for i in range(3)),'XYZ')
            ob.keyframe_insert(data_path='rotation_euler',frame=1+float(k['time'])*24,group='Original MOVE')
    root['units']=f'1 Blender unit = 1 Minecraft block at variant scale {args.variant_scale}'
    root['purpose']='Model study; Blender render is not a Minecraft screenshot'
    # Store MOVE actions as references, but show the symmetric original IDLE rest pose.
    for name,ob in bones.items():
        if ob.animation_data and ob.animation_data.action:
            action=ob.animation_data.action;action.name=label+' / '+name+' / MOVE';action.use_fake_user=True
            ob['move_action']=action.name;ob.animation_data.action=None
        group=next(g for g in groups.values() if g['name']==name)
        rest=base.get(name,[0,0,0]);rotation=[group['rotation'][i]+rest[i] for i in range(3)]
        ob.rotation_euler=Euler(tuple(math.radians(rotation[i])*(-1 if i!=1 else 1) for i in range(3)),'XYZ')
    return collection,root,meshes

baseline_path=GAME/'art/sources/cc0/sea_spider/sea_spider.bbmodel' if args.baseline=='original' else ROOT/f'source/{args.baseline_role}_{args.baseline}.bbmodel'
original,old_root,old_meshes=import_model(baseline_path,f'01 {args.baseline} {args.baseline_role}')
candidate,new_root,new_meshes=import_model(ROOT/f'source/{STEM}.bbmodel',f'02 {REV} {args.role}')
scene.frame_start=1;scene.frame_end=25;scene.render.fps=24;scene.frame_set(1)
floor_mat=bpy.data.materials.new('Neutral matte studio');floor_mat.diffuse_color=(.115,.13,.15,1);floor_mat.use_nodes=True
f=next(n for n in floor_mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED');f.inputs['Base Color'].default_value=(.115,.13,.15,1);f.inputs['Roughness'].default_value=1
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.025));floor=bpy.context.object;floor.name='Studio floor';floor.data.materials.append(floor_mat)
for name,loc,energy,size in [('Key',(-4,-4,8),450,7),('Fill',(1,4,5),275,6),('Rim',(5,-1,6),400,5)]:
    light=bpy.data.lights.new(name,'AREA');light.energy=energy;light.shape='DISK';light.size=size
    ob=bpy.data.objects.new(name,light);scene.collection.objects.link(ob);ob.location=loc;ob.rotation_euler=(Vector((0,0,1))-ob.location).to_track_quat('-Z','Y').to_euler()
cam=bpy.data.cameras.new('Review camera');camera=bpy.data.objects.new('Review camera',cam);scene.collection.objects.link(camera);cam.type='ORTHO';scene.camera=camera
bpy.context.view_layer.update()
points=[o.matrix_world@Vector(v) for o in new_meshes+old_meshes for v in o.bound_box]
center=Vector(tuple((max(p[i] for p in points)+min(p[i] for p in points))/2 for i in range(3)))
span=max(max(p[i] for p in points)-min(p[i] for p in points) for i in [0,1]);cam.ortho_scale=span*1.65
def camera_at(direction):
    camera.location=center+Vector(direction).normalized()*15
    camera.rotation_euler=(center-camera.location).to_track_quat('-Z','Y').to_euler()
def render(name,direction):
    camera_at(direction);scene.render.filepath=str(ROOT/'preview'/name);bpy.ops.render.render(write_still=True)
original.hide_render=True;original.hide_viewport=True
views=args.views.split(',')
if 'quarter' in views:render(f'{STEM}_quarter.png',(-5,-7,5))
if 'front' in views:render(f'{STEM}_front.png',(-8,0,3))
if 'top' in views:render(f'{STEM}_top.png',(0,0,10))
candidate.hide_render=True;candidate.hide_viewport=True;original.hide_render=False;original.hide_viewport=False
if 'baseline' in views:render(f'{args.role}_original_{REV}_quarter.png',(-5,-7,5))
if 'baseline_front' in views:render(f'{args.role}_original_{REV}_front.png',(-8,0,3))
candidate.hide_render=False;candidate.hide_viewport=False;original.hide_render=True;original.hide_viewport=True
camera_at((-5,-7,5))
# Open the saved scene on the prototype at an accessible orbit view.
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':
            area.spaces.active.region_3d.view_distance=span*1.6
            area.spaces.active.region_3d.view_location=center
            area.spaces.active.region_3d.view_rotation=camera.rotation_euler.to_quaternion()
            area.spaces.active.shading.type='MATERIAL'
bpy.ops.object.select_all(action='DESELECT')
for ob in new_meshes:ob.select_set(True)
bpy.context.view_layer.objects.active=new_meshes[0]
bpy.ops.export_scene.gltf(filepath=str(ROOT/f'exports/{STEM}.glb'),export_format='GLB',use_selection=True,export_animations=False)
bpy.ops.object.select_all(action='DESELECT')
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/f'assembly/{STEM}.blend'))
report={'blender_version':bpy.app.version_string,'prototype':REV,'renders':'Studio renders, not in-game evidence',
        'comparison_baseline':args.baseline,
        'existing_move_clip_imported':True,'minecraft_scale':args.variant_scale,'span_blocks':span,'role':args.role,'views':views,
        'prototype_mesh_objects':len(new_meshes),'native_game_verified':False}
(ROOT/f'reports/blender_{REV}.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(f'LURKER_{REV}_SCENE_COMPLETE',json.dumps(report))
