"""Review scene from the same CPM source; no separate geometry authority."""
from pathlib import Path
import bpy,json,math
from mathutils import Vector,Euler
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'source/avatar_PK01.json').read_text(encoding='utf-8'))
for obj in list(bpy.data.objects):bpy.data.objects.remove(obj,do_unlink=True)
scene=bpy.context.scene;scene.name='Kizya Pink PK01'
scene['authoritative_geometry']='source/avatar_PK01.json'
scene['units']='Minecraft model units; 16 units per block'
scene['equipment_visibility']='CPM animations in source; preview scene shows bare avatar'
texture=bpy.data.images.load(str(ROOT/'source/avatar_atlas_PK01.png'));texture.pack()
def material(glow):
    mat=bpy.data.materials.new('CPM atlas'+(' / emissive' if glow else ''));mat.use_nodes=True
    bsdf=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED');tex=mat.node_tree.nodes.new('ShaderNodeTexImage');tex.image=texture;tex.interpolation='Closest'
    mat.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color']);bsdf.inputs['Roughness'].default_value=.8
    if glow:mat.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Emission Color']);bsdf.inputs['Emission Strength'].default_value=1
    return mat
mats={False:material(False),True:material(True)}
pivots={'head':(0,0,0),'body':(0,0,0),'right_arm':(-5,2,0),'left_arm':(5,2,0),'right_leg':(-1.9,12,0),'left_leg':(1.9,12,0)}
rig={}
for bone,(x,y,z) in pivots.items():
    obj=bpy.data.objects.new(bone,None);scene.collection.objects.link(obj);obj.location=(x,z,24-y);rig[bone]=obj
faces={'down':(5,4,0,1),'up':(2,3,7,6),'west':(0,4,7,3),'north':(1,0,3,2),'east':(5,1,2,6),'south':(4,5,6,7)}
for part in data['parts']:
    x,y,z=part['offset'];w,h,d=part['size']
    raw=[(x,y,z),(x+w,y,z),(x+w,y+h,z),(x,y+h,z),(x,y,z+d),(x+w,y,z+d),(x+w,y+h,z+d),(x,y+h,z+d)]
    rotation=Euler(tuple(math.radians(v) for v in part['rotation']),'XYZ').to_matrix()
    rotated=[rotation@Vector(p)+Vector(part.get('pivot',[0,0,0])) for p in raw]
    vertices=[(p.x,p.z,-p.y) for p in rotated];mesh=bpy.data.meshes.new(part['name']);mesh.from_pydata(vertices,[],list(faces.values()));mesh.update()
    obj=bpy.data.objects.new(part['name'],mesh);scene.collection.objects.link(obj);obj.parent=rig[part['bone']];mesh.materials.append(mats[part['glow']])
    obj['cpm_store_id']=part['id'];obj['source_part']=part['name'];obj['bone']=part['bone'];obj['hide_poses']=','.join(part['hide_poses'])
    uv=mesh.uv_layers.new(name='CPM per-face UV')
    for polygon,side in zip(mesh.polygons,faces):
        r=part['uv'][side];a,b,c,d=r['sx'],r['sy'],r['ex'],r['ey']
        for li,(u,v) in zip(polygon.loop_indices,[(c,b),(a,b),(a,d),(c,d)]):uv.data[li].uv=(u/128,1-v/128)
scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True;scene.render.resolution_x=1080;scene.render.resolution_y=1440;scene.render.resolution_percentage=100
scene.world.use_nodes=True;bg=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND');bg.inputs['Color'].default_value=(.13,.12,.14,1);bg.inputs['Strength'].default_value=.5
scene.view_settings.view_transform='AgX'
for name,loc,power in [('Key',(22,-35,45),11000),('Fill',(-24,-18,25),6500),('Rim',(10,30,36),11000)]:
    lamp=bpy.data.lights.new(name,'AREA');lamp.energy=power;lamp.shape='DISK';lamp.size=22
    obj=bpy.data.objects.new(name,lamp);scene.collection.objects.link(obj);obj.location=loc;obj.rotation_euler=(Vector((0,0,16))-obj.location).to_track_quat('-Z','Y').to_euler()
for name,location in [('Front',(0,-70,19)),('Quarter',(35,-70,28)),('Back',(0,70,19))]:
    cam=bpy.data.cameras.new(name);cam.type='ORTHO';cam.ortho_scale=27;obj=bpy.data.objects.new(name,cam);scene.collection.objects.link(obj);obj.location=location;obj.rotation_euler=(Vector((0,0,16))-obj.location).to_track_quat('-Z','Y').to_euler()
    if name=='Quarter':scene.camera=obj
for bone,obj in rig.items():
    for frame in (1,25):obj.rotation_euler=(0,0,0);obj.keyframe_insert('rotation_euler',frame=frame)
    obj.rotation_euler.x=.4 if bone in ('right_arm','left_leg') else -.4 if bone in ('left_arm','right_leg') else 0;obj.keyframe_insert('rotation_euler',frame=13)
scene.frame_start=1;scene.frame_end=25;scene.frame_set(1)
floor_mat=bpy.data.materials.new('Studio / charcoal');floor_mat.use_nodes=True;s=next(n for n in floor_mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED');s.inputs['Base Color'].default_value=(.045,.037,.05,1);s.inputs['Roughness'].default_value=1
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.06));floor=bpy.context.object;floor.name='STUDIO / floor';floor.data.materials.append(floor_mat)
for area in bpy.context.screen.areas:
    if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.shading.type='MATERIAL'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/Kizya_Pink_PK01.blend'))
for name in ['Front','Quarter','Back']:
    scene.camera=bpy.data.objects[name];scene.render.filepath=str(ROOT/f'preview/PK01_{name.lower()}.png');bpy.ops.render.render(write_still=True)
cam=bpy.data.cameras.new('Side');cam.type='ORTHO';cam.ortho_scale=27;camera=bpy.data.objects.new('Side',cam);scene.collection.objects.link(camera);camera.location=(70,0,18);camera.rotation_euler=(Vector((0,0,16))-camera.location).to_track_quat('-Z','Y').to_euler();scene.camera=camera
scene.render.filepath=str(ROOT/'preview/PK01_side.png');bpy.ops.render.render(write_still=True)
cam=bpy.data.cameras.new('Face');cam.type='ORTHO';cam.ortho_scale=11.5;camera=bpy.data.objects.new('Face',cam);scene.collection.objects.link(camera);camera.location=(0,-40,28);camera.rotation_euler=(Vector((0,0,28))-camera.location).to_track_quat('-Z','Y').to_euler();scene.camera=camera;scene.render.resolution_x=960;scene.render.resolution_y=960
scene.render.filepath=str(ROOT/'preview/PK01_face.png');bpy.ops.render.render(write_still=True)
report={'revision':'PK01','source':'source/avatar_PK01.json','parts':len(data['parts']),'packed_atlas':bool(texture.packed_file),'rendered_views':['front','quarter','back','side','face'],'native_game_verified':False}
(ROOT/'reports/BLENDER_BUILD.json').write_text(json.dumps(report,indent=2)+'\n');print('KIZYA_PK01_COMPLETE',json.dumps(report))
print('SAVED_AVATAR_SCENE')
