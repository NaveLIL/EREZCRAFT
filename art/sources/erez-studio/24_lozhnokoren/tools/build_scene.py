"""Build LK01 in a separate background Blender. No live scene or game profile is used."""
from pathlib import Path
import bpy,json,math
from mathutils import Matrix,Vector,Euler
ROOT=Path(__file__).resolve().parents[1]
model=json.loads((ROOT/'source/lozhnokoren_LK01.bbmodel').read_text(encoding='utf8'))
for ob in list(bpy.data.objects):bpy.data.objects.remove(ob,do_unlink=True)
scene=bpy.context.scene;scene.name='Lozhnokoren LK01 / articulated creature study'
scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
scene.render.resolution_x=1600;scene.render.resolution_y=1000;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.render.fps=24;scene.frame_start=1;scene.frame_end=155
scene.world.use_nodes=True
background=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND')
background.inputs['Color'].default_value=(.16,.17,.15,1);background.inputs['Strength'].default_value=.55
model_collection=bpy.data.collections.new('LK01 Creature');scene.collection.children.link(model_collection)
wrapper=bpy.data.objects.new('LOZHNOKOREN / LK01',None);model_collection.objects.link(wrapper)
C=Matrix(((1,0,0,0),(0,0,-1,0),(0,1,0,0),(0,0,0,1)))
wrapper.matrix_world=C@Matrix.Diagonal((1/16,1/16,1/16,1));wrapper['units']='16 model units per Minecraft block'
wrapper['source']='source/lozhnokoren_LK01.bbmodel';wrapper['status']='Design asset; not a Minecraft runtime entity'
materials=[]
for spec in model['erezcraft_materials']:
    m=bpy.data.materials.new('LK01 '+spec['name']);m.use_nodes=True
    shader=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    texture=m.node_tree.nodes.new('ShaderNodeTexImage');texture.image=bpy.data.images.load(str(ROOT/spec['file']));texture.image.pack();texture.interpolation='Closest'
    texture.extension='REPEAT';m.node_tree.links.new(texture.outputs['Color'],shader.inputs['Base Color'])
    m.node_tree.links.new(texture.outputs['Alpha'],shader.inputs['Alpha'])
    shader.inputs['Roughness'].default_value=spec['roughness'];shader.inputs['Specular IOR Level'].default_value=spec['specular']
    m.node_tree.links.new(texture.outputs['Color'],shader.inputs['Emission Color']);shader.inputs['Emission Strength'].default_value=spec['emission']
    materials.append(m)
FACES={
 'north':[(1,1,0),(1,0,0),(0,0,0),(0,1,0)],'south':[(0,1,1),(0,0,1),(1,0,1),(1,1,1)],
 'west':[(0,1,0),(0,0,0),(0,0,1),(0,1,1)],'east':[(1,1,1),(1,0,1),(1,0,0),(1,1,0)],
 'up':[(0,1,0),(0,1,1),(1,1,1),(1,1,0)],'down':[(0,0,1),(0,0,0),(1,0,0),(1,0,1)]}
groups={g['uuid']:g for g in model['groups']};elements={e['uuid']:e for e in model['elements']};bones={};rest_positions={};meshes=[]
def walk(node,parent,parent_origin=None):
    g=groups[node['uuid']];origin=Vector(g['origin']);previous=Vector(parent_origin or (0,0,0))
    ob=bpy.data.objects.new(g['name'],None);model_collection.objects.link(ob);ob.parent=parent;ob.location=origin-previous
    ob.rotation_mode='XYZ';ob.empty_display_size=1;ob.empty_display_type='PLAIN_AXES';bones[g['name']]=ob;rest_positions[g['name']]=ob.location.copy()
    for child in node['children']:
        if isinstance(child,dict):walk(child,ob,g['origin']);continue
        e=elements[child];a=e['from'];b=e['to'];points=[];faces=[];uvs=[];face_materials=[]
        rotation=Euler(tuple(math.radians(v) for v in e.get('rotation',[0,0,0])),'XYZ').to_matrix();pivot=Vector(e['origin'])
        for side,cornerbits in FACES.items():
            face=e['faces'][side];start=len(points)
            for bits in cornerbits:
                raw=Vector([(a,b)[bit][axis] for axis,bit in enumerate(bits)])
                raw=pivot+rotation@(raw-pivot);points.append(raw-origin)
            faces.append(tuple(start+i for i in range(4)))
            u,v,uu,vv=face['uv'];uvs.append([(u,v),(u,vv),(uu,vv),(uu,v)]);face_materials.append(materials[int(face['texture'])])
        mesh=bpy.data.meshes.new(e['name']);mesh.from_pydata(points,[],faces);mesh.update();uv=mesh.uv_layers.new(name='16px native UV')
        for polygon,coords,mat in zip(mesh.polygons,uvs,face_materials):
            if mat.name not in mesh.materials:mesh.materials.append(mat)
            polygon.material_index=mesh.materials.find(mat.name)
            for li,(u,v) in zip(polygon.loop_indices,coords):uv.data[li].uv=(u/16,1-v/16)
        item=bpy.data.objects.new(e['name'],mesh);model_collection.objects.link(item);item.parent=ob;item['source_uuid']=e['uuid'];meshes.append(item)
walk(model['outliner'][0],wrapper)
bpy.context.view_layer.update()
points=[o.matrix_world@Vector(v) for o in meshes for v in o.bound_box]
minimum=Vector([min(p[i] for p in points) for i in range(3)]);maximum=Vector([max(p[i] for p in points) for i in range(3)])
wrapper.location.z=-minimum.z
dimensions=maximum-minimum;center=(maximum+minimum)/2;center.z+=wrapper.location.z

# Sample the three source clips into one labelled Blender review timeline.
offsets={'Idle':1,'Crawl':61,'Threat':121}
def sample(keys,time):
    keys=sorted(keys,key=lambda k:k['time'])
    if time<=keys[0]['time']:return [float(keys[0]['data_points'][0].get(c,0)) for c in 'xyz']
    if time>=keys[-1]['time']:return [float(keys[-1]['data_points'][0].get(c,0)) for c in 'xyz']
    for a,b in zip(keys,keys[1:]):
        if a['time']<=time<=b['time']:
            f=(time-a['time'])/(b['time']-a['time']);return [float(a['data_points'][0].get(c,0))*(1-f)+float(b['data_points'][0].get(c,0))*f for c in 'xyz']
for name,ob in bones.items():
    for frame in [1,49,60,61,109,120,121,155]:
        ob.location=rest_positions[name];ob.rotation_euler=(0,0,0)
        ob.keyframe_insert(data_path='location',frame=frame,group='LK01 review')
        ob.keyframe_insert(data_path='rotation_euler',frame=frame,group='LK01 review')
for animation in model['animations']:
    start=offsets[animation['name']];scene.timeline_markers.new(animation['name'],frame=start)
    for channel in animation['animators'].values():
        ob=bones[channel['name']]
        for kind in ['rotation','position']:
            keys=[k for k in channel['keyframes'] if k['channel']==kind]
            if not keys:continue
            for frame_offset in range(round(animation['length']*24)+1):
                values=sample(keys,min(frame_offset/24,animation['length']))
                if kind=='rotation':
                    ob.rotation_euler=tuple(math.radians(v) for v in values);data_path='rotation_euler'
                else:ob.location=rest_positions[channel['name']]+Vector(values);data_path='location'
                ob.keyframe_insert(data_path=data_path,frame=start+frame_offset,group='LK01 review')
scene.frame_set(1)

# Studio and a neutral 1.8-block scale figure, excluded from exports.
def simple_material(name,color,roughness=1):
    m=bpy.data.materials.new(name);m.use_nodes=True
    s=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');s.inputs['Base Color'].default_value=(*color,1);s.inputs['Roughness'].default_value=roughness
    return m
floor_mat=simple_material('Neutral earth studio',(.16,.17,.145))
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.035));floor=bpy.context.object;floor.name='STUDIO / floor';floor.data.materials.append(floor_mat)
figure=bpy.data.collections.new('STUDIO / scale reference 1.8 blocks');scene.collection.children.link(figure)
silhouette=simple_material('Scale reference charcoal',(.09,.115,.105))
for label,c,size in [('Head',(0,0,1.56),(.43,.43,.48)),('Body',(0,0,.99),(.5,.25,.66)),('LegL',(-.13,0,.33),(.23,.26,.66)),('LegR',(.13,0,.33),(.23,.26,.66)),('ArmL',(-.37,0,.99),(.23,.25,.66)),('ArmR',(.37,0,.99),(.23,.25,.66))]:
    bpy.ops.mesh.primitive_cube_add(size=1,location=(c[0]+3.3,c[1]+2.2,c[2]));ob=bpy.context.object;ob.name='SCALE / '+label;ob.dimensions=size;ob.data.materials.append(silhouette)
    for collection in list(ob.users_collection):collection.objects.unlink(ob)
    figure.objects.link(ob)
for name,location,power,size in [('Key',(4,5,10),1600,8),('Fill',(-5,3,7),900,7),('Rim',(0,-6,9),1800,6)]:
    data=bpy.data.lights.new(name,'AREA');data.energy=power;data.size=size;ob=bpy.data.objects.new(name,data);scene.collection.objects.link(ob);ob.location=location
    ob.rotation_euler=(Vector((0,0,1))-ob.location).to_track_quat('-Z','Y').to_euler()
cam=bpy.data.cameras.new('LK01 review camera');camera=bpy.data.objects.new('LK01 review camera',cam);scene.collection.objects.link(camera);scene.camera=camera;cam.type='ORTHO';cam.ortho_scale=14.8
target=Vector((0,0,1.15))
def aim(direction):
    camera.location=target+Vector(direction).normalized()*20
    camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
def render(filename,frame,direction,scale=14.8):
    scene.frame_set(frame);aim(direction);cam.ortho_scale=scale;scene.render.filepath=str(ROOT/'preview'/filename);bpy.ops.render.render(write_still=True)
aim((-11,14,9));scene.frame_set(1)
scene['LK01_timeline']='Idle 1-49; Crawl 61-109; Threat 121-155. Review sequence, not runtime AI.'
scene['geometry_source']='source/lozhnokoren_LK01.bbmodel'
scene['native_game_verified']=False
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':
            area.spaces.active.region_3d.view_distance=15
            area.spaces.active.region_3d.view_location=target
            area.spaces.active.region_3d.view_rotation=camera.rotation_euler.to_quaternion()
            area.spaces.active.shading.type='MATERIAL'
bpy.ops.object.select_all(action='DESELECT')
for ob in model_collection.objects:ob.select_set(True)
bpy.context.view_layer.objects.active=wrapper
export={'filepath':str(ROOT/'exports/lozhnokoren_LK01.glb'),'export_format':'GLB','use_selection':True,'export_animations':True}
rna=bpy.ops.export_scene.gltf.get_rna_type().properties
mode=None
if 'export_animation_mode' in rna:
    options=[e.identifier for e in rna['export_animation_mode'].enum_items]
    if 'SCENE' in options:export['export_animation_mode']='SCENE';mode='SCENE'
if 'export_frame_range' in rna:export['export_frame_range']=True
if 'export_force_sampling' in rna:export['export_force_sampling']=True
bpy.ops.export_scene.gltf(**export)
bpy.ops.object.select_all(action='DESELECT')
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/lozhnokoren_LK01.blend'))
render('lozhnokoren_LK01_hero.png',1,(-11,14,9))
render('lozhnokoren_LK01_threat.png',143,(-8,15,6.5))
render('lozhnokoren_LK01_side.png',1,(18,1,4),14.8)
figure.hide_render=True
render('lozhnokoren_LK01_top.png',1,(0,.01,20),19.5)
# Short in-place crawl preview; game pathfinding and forward locomotion are separate work.
figure.hide_render=True;scene.render.resolution_x=800;scene.render.resolution_y=500;scene.cycles.samples=12
for i,frame in enumerate([61,67,73,79,85,91,97,103]):render(f'crawl_{i:02d}.png',frame,(-11,14,9))
report={'revision':'LK01','blender_version':bpy.app.version_string,'mesh_objects':len(meshes),'articulated_groups':len(bones),
        'dimensions_blocks_xyz':list(dimensions),'review_animation_export_mode':mode,'source_clips':['Idle','Crawl','Threat'],
        'blender_review_timeline':{'Idle':[1,49],'Crawl':[61,109],'Threat':[121,155]},
        'scale_figure_height_blocks':1.8,'all_images_packed':all(im.packed_file for im in bpy.data.images if im.source=='FILE'),
        'rendered_studio_not_minecraft':True,'game_verified':False}
(ROOT/'reports/blender_LK01.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('LOZHNOKOREN_LK01_COMPLETE',json.dumps(report))
