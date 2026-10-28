"""Render baseline generated sprite and candidate at identical scale/light/camera.
No resource in the Minecraft repository is written by this script.
"""
import bpy
import hashlib
import json
from pathlib import Path
import runpy
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[1]
runpy.run_path(str(ROOT/'tools/build_blender.py'))
scene=bpy.context.scene
config=json.loads((ROOT/'source/design.json').read_text(encoding='utf-8-sig'))
candidate=bpy.data.objects['interstice_riftsteel_sword_'+config['revision'].replace('-','')]
tex=Path(config['destination_project'])/'src/main/resources/assets/interstice/textures/item/armaments/tool_greatsword_steel.png'
image=bpy.data.images.load(str(tex),check_existing=True)
pixels=list(image.pixels[:]);width,height=image.size[:]
cells={}
for y in range(height):
    for x in range(width):
        i=((height-1-y)*width+x)*4
        if pixels[i+3]>.5: cells[x,y]=tuple(pixels[i:i+3])
def linear(v):return v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4
old_handle=[(x,y) for (x,y),(r,g,b) in cells.items() if r>g*1.2 and b<.05]
old_grip=Vector((sum(x+.5 for x,y in old_handle)/len(old_handle)*.5-8,0,
                 sum(height-y-.5 for x,y in old_handle)/len(old_handle)*.5-8))
new_handle=[]
namespace,item_path=config['item_id'].split(':',1)
model_path=ROOT/f'exports/assets/{namespace}/models/item/{item_path}.json'
for e in json.loads(model_path.read_text())['elements']:
    if e['name'].endswith(('_B','_C','_T')):
        from math import radians,cos,sin
        x=(e['from'][0]+e['to'][0])*.5-8;y=(e['from'][1]+e['to'][1])*.5-8;a=radians(e['rotation']['angle'])
        new_handle.append(Vector((x*cos(a)-y*sin(a),0,x*sin(a)+y*cos(a))))
new_grip=sum(new_handle,Vector())/len(new_handle);offset=new_grip-old_grip
verts=[];faces=[];indices=[];materials={}
face_indices={'down':(0,1,5,4),'up':(3,7,6,2),'north':(0,3,2,1),
              'south':(4,5,6,7),'west':(0,4,7,3),'east':(1,2,6,5)}
for (x,y),color in cells.items():
    a=[x*.5-8,-.5,(height-y-1)*.5-8];b=[a[0]+.5,.5,a[2]+.5]
    start=len(verts)
    coords=[(a[0],a[2],a[1]),(b[0],a[2],a[1]),(b[0],b[2],a[1]),(a[0],b[2],a[1]),
            (a[0],a[2],b[1]),(b[0],a[2],b[1]),(b[0],b[2],b[1]),(a[0],b[2],b[1])]
    # Same game->Blender axes as the candidate (front Y negative).
    for px,py,pz in coords:verts.append(tuple(Vector((px,-pz,py))+offset))
    sides={'north':True,'south':True,'west':(x-1,y) not in cells,
           'east':(x+1,y) not in cells,'up':(x,y-1) not in cells,'down':(x,y+1) not in cells}
    for side,on in sides.items():
        if not on:continue
        factor=1 if side in ('north','south') else .65
        key=tuple(round(linear(v)*factor,6) for v in color)
        if key not in materials:
            mat=bpy.data.materials.new('Baseline source '+str(len(materials)));mat.use_nodes=True
            bsdf=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
            bsdf.inputs['Base Color'].default_value=(*key,1);bsdf.inputs['Roughness'].default_value=1
            bsdf.inputs['Emission Color'].default_value=(*key,1);bsdf.inputs['Emission Strength'].default_value=.8
            materials[key]=(mat,len(materials))
        faces.append(tuple(start+i for i in face_indices[side]));indices.append(materials[key][1])
mesh=bpy.data.meshes.new('Baseline generated sprite at 16 units');mesh.from_pydata(verts,[],faces);mesh.update()
baseline=bpy.data.objects.new('BEFORE existing riftsteel generated model',mesh)
for mat,index in materials.values():mesh.materials.append(mat)
for poly,index in zip(mesh.polygons,indices):poly.material_index=index
before=scene.copy();before.name='Mezhdumorye / BEFORE existing sword'
before.collection.objects.unlink(candidate);before.collection.objects.link(baseline)
scene.name='Mezhdumorye / AFTER proposed '+config['revision']
points=[candidate.matrix_world@v.co for v in candidate.data.vertices]+[v.co for v in baseline.data.vertices]
low=Vector(tuple(min(v[i] for v in points) for i in range(3)))
high=Vector(tuple(max(v[i] for v in points) for i in range(3)));center=(low+high)*.5
camera=scene.camera;camera.location=center+Vector((22,-70,18))
camera.rotation_euler=(center-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.ortho_scale=31
for s,name in ((before,'before_same_scale.png'),(scene,'after_same_scale.png')):
    s.render.resolution_x=900;s.render.resolution_y=900
    s.render.filepath=str(ROOT/'preview'/name)
    bpy.ops.render.render(write_still=True,scene=s.name)
bpy.context.window.scene=scene
scene['comparison']='Same camera, orthographic scale, lighting; grip centres aligned. Not an in-game screenshot.'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/mezhdumorye_sword_before_after.blend'))
report={'baseline':'Existing 32x32 sprite generated into a 16-model-unit square with 1-unit depth',
        'candidate':'Exact candidate JSON mesh','same_camera':True,'same_scale':True,'same_material_shader':True,
        'alignment':'Centres of brown grip texels aligned; this is not a hand-transform verification',
        'minecraft_resources_modified':False,'native_game_screenshot':False,
        'baseline_texture_sha256':hashlib.sha256(tex.read_bytes()).hexdigest(),
        'candidate_model_sha256':hashlib.sha256(model_path.read_bytes()).hexdigest()}
(ROOT/'reports/comparison.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('COMPARISON_RENDER_COMPLETE')
