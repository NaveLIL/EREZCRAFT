"""Background Blender import of the exact Minecraft JSON faces (not a visual redesign)."""
import bpy
import hashlib
import json
import math
from pathlib import Path
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[1]
for initial in list(bpy.data.objects):
    bpy.data.objects.remove(initial, do_unlink=True)
cfg=json.loads((ROOT/'source/design.json').read_text(encoding='utf-8-sig'))
namespace,item_path=cfg['item_id'].split(':',1)
model_path=ROOT/f'exports/assets/{namespace}/models/item/{item_path}.json'
model=json.loads(model_path.read_text(encoding='utf-8'))
tex=Path(cfg['destination_project'])/'src/main/resources/assets/interstice/textures/item/armaments/tool_greatsword_steel.png'
assert hashlib.sha256(tex.read_bytes()).hexdigest()==cfg['texture_sha256']
image=bpy.data.images.load(str(tex));image.pack()
def srgb(v): return v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4
def sample(uv):
    x=int((uv[0]+uv[2])*.5/16*image.size[0]);y=int((uv[1]+uv[3])*.5/16*image.size[1])
    i=((image.size[1]-1-y)*image.size[0]+x)*4
    return tuple(srgb(v) for v in image.pixels[i:i+3])
scene=bpy.data.scenes.new('Mezhdumorye / Riftsteel '+cfg['revision'])
bpy.context.window.scene=scene
scene.unit_settings.system='NONE'
scene['geometry_units']='1 Blender unit = 1 Minecraft model unit = 1/16 game block'
scene['authoritative_geometry']=str(model_path)
scene['item_id']=cfg['item_id']
materials={};vertices=[];faces=[];indices=[]
face_indices={'down':(0,1,5,4),'up':(3,7,6,2),'north':(0,3,2,1),
              'south':(4,5,6,7),'west':(0,4,7,3),'east':(1,2,6,5)}
for e in model['elements']:
    a=e['from'];b=e['to'];ox,oy,oz=e['rotation']['origin'];rad=math.radians(e['rotation']['angle'])
    coords=[(a[0],a[1],a[2]),(b[0],a[1],a[2]),(b[0],b[1],a[2]),(a[0],b[1],a[2]),
            (a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2])]
    start=len(vertices)
    for x,y,z in coords:
        rx=ox+(x-ox)*math.cos(rad)-(y-oy)*math.sin(rad)
        ry=oy+(x-ox)*math.sin(rad)+(y-oy)*math.cos(rad)
        vertices.append((rx-8,-(z-8),ry-8))
    for side,info in e['faces'].items():
        rgb=sample(info['uv']);factor=1.0 if side in ('north','south') else .65
        key=tuple(round(v*factor,6) for v in rgb)
        if key not in materials:
            mat=bpy.data.materials.new('Source texel '+str(len(materials)));mat.use_nodes=True
            bsdf=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
            bsdf.inputs['Base Color'].default_value=(*key,1)
            bsdf.inputs['Roughness'].default_value=1
            bsdf.inputs['Emission Color'].default_value=(*key,1);bsdf.inputs['Emission Strength'].default_value=.8
            mat.diffuse_color=(*key,1);materials[key]=(mat,len(materials))
        faces.append(tuple(start+i for i in face_indices[side]));indices.append(materials[key][1])
mesh=bpy.data.meshes.new('Exact Minecraft faces');mesh.from_pydata(vertices,[],faces);mesh.update()
obj=bpy.data.objects.new('interstice_riftsteel_sword_'+cfg['revision'].replace('-',''),mesh);scene.collection.objects.link(obj)
for mat,index in materials.values():mesh.materials.append(mat)
for poly,index in zip(mesh.polygons,indices):poly.material_index=index
obj['source_model_sha256']=hashlib.sha256(model_path.read_bytes()).hexdigest()
obj['texture_credit']=cfg['texture_credit'];obj['minecraft_model_units_per_block']=16
low=Vector(tuple(min(v[i] for v in vertices) for i in range(3)))
high=Vector(tuple(max(v[i] for v in vertices) for i in range(3)));center=(low+high)*.5
def camera(name,offset,scale):
    data=bpy.data.cameras.new(name);cam=bpy.data.objects.new(name,data);scene.collection.objects.link(cam)
    cam.location=center+Vector(offset);cam.rotation_euler=(center-cam.location).to_track_quat('-Z','Y').to_euler()
    data.type='ORTHO';data.ortho_scale=scale;return cam
hero=camera('Three quarter',(22,-70,18),30)
front=camera('Front',(0,-80,0),30)
scene.camera=hero
world=bpy.data.worlds.new('Dark slate');scene.world=world;world.use_nodes=True
background=next(n for n in world.node_tree.nodes if n.type=='BACKGROUND')
background.inputs['Color'].default_value=(.025,.041,.052,1);background.inputs['Strength'].default_value=.65
scene.render.engine='CYCLES';scene.cycles.samples=16
scene.render.resolution_x=1000;scene.render.resolution_y=1000;scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.render.image_settings.file_format='PNG'
scene.render.film_transparent=False
for area in bpy.context.screen.areas:
    if area.type=='VIEW_3D':
        area.spaces.active.region_3d.view_perspective='CAMERA'
        area.spaces.active.shading.type='MATERIAL'
bpy.context.view_layer.objects.active=obj;obj.select_set(True)
for item in bpy.context.selected_objects:
    if item!=obj:item.select_set(False)
out=ROOT/'assembly';out.mkdir(exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=str(out/'mezhdumorye_riftsteel_longsword.blend'))
bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/riftsteel_longsword.glb'),export_format='GLB',use_selection=True)
scene.render.filepath=str(ROOT/'preview/sword_3d.png');bpy.ops.render.render(write_still=True)
scene.camera=front;scene.render.filepath=str(ROOT/'preview/sword_model_front.png');bpy.ops.render.render(write_still=True)
report={'scene':scene.name,'item_id':cfg['item_id'],'source_model_sha256':obj['source_model_sha256'],
        'vertices':len(mesh.vertices),'faces':len(mesh.polygons),'voxel_elements':len(model['elements']),
        'units':scene['geometry_units'],'outputs':{}}
for p in (out/'mezhdumorye_riftsteel_longsword.blend',ROOT/'exports/riftsteel_longsword.glb',ROOT/'preview/sword_3d.png',ROOT/'preview/sword_model_front.png'):
    report['outputs'][str(p.relative_to(ROOT))]=hashlib.sha256(p.read_bytes()).hexdigest()
(ROOT/'reports/blender.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('SWORD_BLENDER_COMPLETE '+json.dumps({'elements':len(model['elements']),'faces':len(mesh.polygons)}))
