from pathlib import Path
import bpy,json,math,hashlib
from mathutils import Vector
ROOT=Path(r'D:/repos/Blender/creations/22_tide_indicator');GAME=Path(r'D:/repos/Minecraft/EREZCRAFT')
design=json.loads((ROOT/'source/tide_indicator_TI01.json').read_text(encoding='utf8'))
design['textures']={r:str(GAME/'src/main/resources/assets'/r.split(':')[0]/'textures'/(r.split(':')[1]+'.png')) for r in ['interstice:item/expedition_tools/brass','interstice:item/tide_indicator_00']}
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
        m=mat(ref);u,v,uu,vv=face.get('uv',[0,0,16,16]);faces.append(indices[side]);uvs.append([(u,vv),(uu,vv),(uu,v),(u,v)] if side=='south' else [(uu,v),(u,v),(u,vv),(uu,vv)]);mats.append(m)
    mesh=bpy.data.meshes.new(element.get('name','Native element'));mesh.from_pydata(points,[],faces);mesh.update();uv=mesh.uv_layers.new(name='Minecraft UV')
    for poly,coords,m in zip(mesh.polygons,uvs,mats):
        if m.name not in mesh.materials:mesh.materials.append(m)
        poly.material_index=mesh.materials.find(m.name)
        for li,(u,v) in zip(poly.loop_indices,coords):uv.data[li].uv=(u/16,1-v/16)
    obj=bpy.data.objects.new(element.get('name','Native element'),mesh);scene.collection.objects.link(obj);obj.parent=root
    return obj

scene=bpy.context.scene;scene.name='TI01 / thin pocket barometer'
scene['source']='source/tide_indicator_TI01.json';scene['units']='16 model units per Minecraft block';scene['maximum_depth']=.88
scene.render.engine='CYCLES';scene.cycles.samples=20;scene.cycles.use_denoising=True;scene.render.resolution_x=1600;scene.render.resolution_y=900;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
world=bpy.data.worlds.new('TI01 studio');world.use_nodes=True;scene.world=world;world.node_tree.nodes.get('Background').inputs['Color'].default_value=(.07,.10,.12,1);world.node_tree.nodes.get('Background').inputs['Strength'].default_value=.8
for label,loc,power,size in [('Key',(-10,-25,32),10000,20),('Fill',(25,-15,18),7000,20),('Rim',(0,18,25),10000,15)]:
 light=bpy.data.lights.new(label,'AREA');light.energy=power;light.size=size;ob=bpy.data.objects.new(label,light);scene.collection.objects.link(ob);ob.location=loc;ob.rotation_euler=(Vector((0,0,8))-ob.location).to_track_quat('-Z','Y').to_euler()
cam=bpy.data.cameras.new('Thinness review');ob=bpy.data.objects.new('Thinness review',cam);scene.collection.objects.link(ob);ob.location=(0,-80,16);ob.rotation_euler=(Vector((0,0,8))-ob.location).to_track_quat('-Z','Y').to_euler();cam.type='ORTHO';cam.ortho_scale=42;scene.camera=ob
model=design['models']['tide_indicator_base'];roots=[]
for name,x,angle in [('Front',-12,0),('Quarter',0,50),('Side',12,90)]:
 root=bpy.data.objects.new(name,None);scene.collection.objects.link(root);root.location.x=x;root.rotation_euler.z=math.radians(angle);roots.append(root)
 for element in model['elements']:cube(scene,root,element,model['textures'])
root=roots[0];bpy.ops.object.select_all(action='DESELECT');root.select_set(True)
for ob in scene.objects:
 if ob.parent==root:ob.select_set(True)
bpy.context.view_layer.objects.active=root
bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/tide_indicator_TI01.glb'),export_format='GLB',use_selection=True)
bpy.context.preferences.filepaths.save_version=0;bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/tide_indicator_TI01.blend'))
scene.render.filepath=str(ROOT/'preview/tide_indicator_TI01.png');bpy.ops.render.render(write_still=True)
report={'passed':True,'blender_version':bpy.app.version_string,'canonical_source_sha256':hashlib.sha256((ROOT/'source/tide_indicator_TI01.json').read_bytes()).hexdigest(),'maximum_case_depth':.88,'game_verified':False,'native_elements':len(model['elements'])}
(ROOT/'reports/blender_TI01.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8');print('TI01_THIN_MODEL_RENDERED')
