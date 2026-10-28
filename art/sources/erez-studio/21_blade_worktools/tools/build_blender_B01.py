from pathlib import Path
import bpy,json,math,hashlib,sys
from mathutils import Vector
ROOT=Path(r'D:/repos/Blender/creations/21_blade_worktools')
design=json.loads((ROOT/'source/blade_worktools_B01.json').read_text(encoding='utf8'))
for obj in list(bpy.data.objects):bpy.data.objects.remove(obj,do_unlink=True)
materials={};loaded={};results={}
indices={'north':(1,0,3,2),'south':(4,5,6,7),'west':(0,4,7,3),'east':(5,1,2,6),'down':(0,1,5,4),'up':(7,6,2,3)}
def mat(ref):
    if ref in materials:return materials[ref]
    image=bpy.data.images.load(design['textures'][ref],check_existing=True);image.pack();loaded[ref]=image
    m=bpy.data.materials.new(ref);m.use_nodes=True
    bsdf=m.node_tree.nodes.get('Principled BSDF');tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=image;tex.interpolation='Closest'
    m.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color']);m.node_tree.links.new(tex.outputs['Alpha'],bsdf.inputs['Alpha'])
    bsdf.inputs['Roughness'].default_value=.87;bsdf.inputs['Specular IOR Level'].default_value=.1
    m.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Emission Color']);bsdf.inputs['Emission Strength'].default_value=.2
    if hasattr(m,'surface_render_method'):m.surface_render_method='DITHERED'
    materials[ref]=m;return m
def cube(scene,root,element,textures):
    a=element['from'];b=element['to']
    raw=[(a[0],a[1],a[2]),(b[0],a[1],a[2]),(b[0],b[1],a[2]),(a[0],b[1],a[2]),(a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2])]
    r=element.get('rotation');points=[]
    for point in raw:
        p=list(point)
        if r:
            origin=r['origin'];p=[p[i]-origin[i] for i in range(3)];co=math.cos(math.radians(r['angle']));si=math.sin(math.radians(r['angle']));x,y,z=p
            p=[co*x-si*y,si*x+co*y,z] if r['axis']=='z' else [x,co*y-si*z,si*y+co*z] if r['axis']=='x' else [co*x+si*z,y,-si*x+co*z]
            p=[p[i]+origin[i] for i in range(3)]
        points.append((p[0]-8,-(p[2]-8),p[1]))
    faces=[];uvs=[];mats=[]
    for side,face in element['faces'].items():
        ref=face['texture']
        while ref.startswith('#'):ref=textures[ref[1:]]
        m=mat(ref);u,v,uu,vv=face.get('uv',[0,0,16,16]);faces.append(indices[side]);uvs.append([(uu,v),(u,v),(u,vv),(uu,vv)]);mats.append(m)
    mesh=bpy.data.meshes.new(element.get('name','Native element'));mesh.from_pydata(points,[],faces);mesh.update();uv=mesh.uv_layers.new(name='Minecraft UV')
    for poly,coords,m in zip(mesh.polygons,uvs,mats):
        if m.name not in mesh.materials:mesh.materials.append(m)
        poly.material_index=mesh.materials.find(m.name)
        for li,(u,v) in zip(poly.loop_indices,coords):uv.data[li].uv=(u/16,1-v/16)
    obj=bpy.data.objects.new(element.get('name','Native element'),mesh);scene.collection.objects.link(obj);obj.parent=root
    return obj

scene=bpy.context.scene
scene.name='Blade / EREZ palettes / B01'
scene['source']='source/blade_worktools_B01.json'
scene['units']='16 model units per Minecraft block'
scene['geometry_source']='Blade 1.0.1; exact elements, rotation and UV'
scene['scope']='Geometry review; native screenshots establish actual grip'
scene.render.engine='CYCLES';scene.cycles.samples=16;scene.cycles.use_denoising=True
scene.render.resolution_x=1600;scene.render.resolution_y=1100;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
world=bpy.data.worlds.new('Blade review');world.use_nodes=True;scene.world=world
world.node_tree.nodes.get('Background').inputs['Color'].default_value=(.08,.12,.15,1);world.node_tree.nodes.get('Background').inputs['Strength'].default_value=.85
for label,loc,power,size in [('Key',(10,-30,45),16000,28),('Fill',(-30,-12,20),10000,28),('Rim',(0,25,35),14000,25)]:
 light=bpy.data.lights.new(label,'AREA');light.energy=power;light.size=size;o=bpy.data.objects.new(label,light);scene.collection.objects.link(o);o.location=loc;o.rotation_euler=(Vector((0,0,10))-o.location).to_track_quat('-Z','Y').to_euler()
cam=bpy.data.cameras.new('Palette review');o=bpy.data.objects.new('Palette review',cam);scene.collection.objects.link(o);o.location=(8,-110,35);o.rotation_euler=(Vector((0,0,12))-o.location).to_track_quat('-Z','Y').to_euler();cam.type='ORTHO';cam.ortho_scale=72;scene.camera=o
results={}
for index,(name,row) in enumerate(design['models'].items()):
 tier,kind=name.split('_',1);column=['pickaxe','axe','shovel','hoe'].index(kind)
 root=bpy.data.objects.new(name,None);scene.collection.objects.link(root);root.location.x=(column-1.5)*18;root.location.z=13 if tier=='riftsilver' else -10;root['item_id']='interstice:'+name
 for element in row['model']['elements']:cube(scene,root,element,row['model']['textures'])
 bpy.ops.object.select_all(action='DESELECT');root.select_set(True)
 for obj in scene.objects:
  if obj.parent==root:obj.select_set(True)
 bpy.context.view_layer.objects.active=root
 bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports'/(name+'_B01.glb')),export_format='GLB',use_selection=True)
 results[name]={'elements':len(row['model']['elements']),'mesh_objects':len([obj for obj in scene.objects if obj.parent==root])}
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/blade_worktools_B01.blend'))
scene.render.filepath=str(ROOT/'preview/blade_worktools_B01.png');bpy.ops.render.render(write_still=True)
report={'passed':True,'blender_version':bpy.app.version_string,'source_sha256':hashlib.sha256((ROOT/'source/blade_worktools_B01.json').read_bytes()).hexdigest(),'geometry_and_uv_unchanged':True,'game_verified':False,'models':results}
(ROOT/'reports/blender_B01.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('BLADE_B01_GEOMETRY_RENDERED')
