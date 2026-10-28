"""Blender meshes, UVs and scenes generated from the exact native Minecraft JSON export."""
from pathlib import Path
import bpy,json,math,hashlib,sys
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from render_dependency import digest
design=json.loads((ROOT/'source/tools_T01.json').read_text(encoding='utf-8'))
SAVE_ONLY='--save-only' in sys.argv
render_digest=digest(design)
if SAVE_ONLY:
    previous=json.loads((ROOT/'reports/blender.json').read_text());assert previous['rendered_dependency_sha256']==render_digest,'Changed render dependency; rerender required'
def render():
    if not SAVE_ONLY:bpy.ops.render.render(write_still=True)
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
def resolve_old(name):
    d=design['previous'][name]
    if d.get('parent','').startswith('interstice:item/'):
        parent=resolve_old(d['parent'].split('/')[-1]);return parent|d|{'textures':parent.get('textures',{})|d.get('textures',{})}
    return d
def sprite(scene,root,ref):
    m=mat(ref);image=loaded[ref];w,h=image.size;pixels=list(image.pixels[:]);vertices=[];faces=[];uvs=[]
    def add(points,coords):
        n=len(vertices);vertices.extend(points);faces.append(tuple(range(n,n+4)));uvs.append(coords)
    add([(-8,-.5,0),(8,-.5,0),(8,-.5,16),(-8,-.5,16)],[(0,0),(1,0),(1,1),(0,1)])
    add([(8,.5,0),(-8,.5,0),(-8,.5,16),(8,.5,16)],[(1,0),(0,0),(0,1),(1,1)])
    opaque=lambda x,y:0<=x<w and 0<=y<h and pixels[(y*w+x)*4+3]>.5
    for y in range(h):
        for x in range(w):
            if not opaque(x,y):continue
            xa=-8+16*x/w;xb=-8+16*(x+1)/w;ya=16*y/h;yb=16*(y+1)/h;u=(x+.5)/w;v=(y+.5)/h;uv=[(u,v)]*4
            if not opaque(x-1,y):add([(xa,.5,ya),(xa,-.5,ya),(xa,-.5,yb),(xa,.5,yb)],uv)
            if not opaque(x+1,y):add([(xb,-.5,ya),(xb,.5,ya),(xb,.5,yb),(xb,-.5,yb)],uv)
            if not opaque(x,y-1):add([(xa,.5,ya),(xb,.5,ya),(xb,-.5,ya),(xa,-.5,ya)],uv)
            if not opaque(x,y+1):add([(xa,-.5,yb),(xb,-.5,yb),(xb,.5,yb),(xa,.5,yb)],uv)
    mesh=bpy.data.meshes.new('Generated item sprite');mesh.from_pydata(vertices,[],faces);mesh.update();mesh.materials.append(m);layer=mesh.uv_layers.new(name='Native sprite UV')
    for poly,coords in zip(mesh.polygons,uvs):
        for li,point in zip(poly.loop_indices,coords):layer.data[li].uv=point
    obj=bpy.data.objects.new('Previous generated sprite',mesh);scene.collection.objects.link(obj);obj.parent=root
def setup(name,group):
    scene=bpy.data.scenes.new(name);bpy.context.window.scene=scene
    scene['source']='source/tools_T01.json';scene['units']='16 model units per game block';scene['status']='Geometry review, game installation pending approval'
    scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
    scene.render.resolution_x=1900 if group else 1100;scene.render.resolution_y=950 if group else 1100;scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
    world=bpy.data.worlds.new(name+' world');world.use_nodes=True;scene.world=world;world.node_tree.nodes.get('Background').inputs['Color'].default_value=(.10,.135,.16,1);world.node_tree.nodes.get('Background').inputs['Strength'].default_value=.8
    for label,loc,power,size in [('Key',(15,-30,40),11000,25),('Fill',(-30,-18,20),8500,23),('Rim',(8,25,28),13000,20)]:
        light=bpy.data.lights.new(label,'AREA');light.energy=power;light.size=size;obj=bpy.data.objects.new(label,light);scene.collection.objects.link(obj);obj.location=loc;obj.rotation_euler=(Vector((0,0,8))-obj.location).to_track_quat('-Z','Y').to_euler()
    cam=bpy.data.cameras.new('Geometry review');obj=bpy.data.objects.new('Geometry review',cam);scene.collection.objects.link(obj);obj.location=(23,-90,34) if group else (25,-65,28);target=Vector((0,0,8));obj.rotation_euler=(target-obj.location).to_track_quat('-Z','Y').to_euler();cam.type='ORTHO';cam.ortho_scale=56 if group else 21;scene.camera=obj
    return scene
def build(scene,name,offset,new):
    root=bpy.data.objects.new(name,None);scene.collection.objects.link(root);root.location.x=offset;root['item_id']='interstice:'+name
    d=design['models'][name]['model'] if new else resolve_old(name)
    if 'elements' in d:
        for e in d['elements']:cube(scene,root,e,d['textures'])
    else:sprite(scene,root,d['textures']['layer0'])
    return root
names=list(design['models']);main=None
for new in (False,True):
    scene=setup('Expedition tools / '+('T01' if new else 'previous'),True)
    for n,x in zip(names,(-18,0,18)):build(scene,n,x,new)
    scene.render.filepath=str(ROOT/'preview'/('tools_T01.png' if new else 'tools_previous.png'));render()
    if new:main=scene
for name in names:
    scene=setup('T01 / '+name,False);root=build(scene,name,0,True)
    scene.render.filepath=str(ROOT/'preview'/(name+'_T01.png'));render()
    bpy.ops.object.select_all(action='DESELECT');root.select_set(True)
    for o in scene.objects:
        if o.parent==root:o.select_set(True)
    bpy.context.view_layer.objects.active=root
    bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports'/(name+'_T01.glb')),export_format='GLB',use_selection=True)
    results[name]={'elements':len(design['models'][name]['model']['elements']),'mesh_objects':len([o for o in scene.objects if o.parent==root])}
bpy.context.window.scene=main
saved_location=main.camera.location.copy();saved_rotation=main.camera.rotation_euler.copy()
main.camera.location=(0,-90,8);main.camera.rotation_euler=(Vector((0,0,8))-main.camera.location).to_track_quat('-Z','Y').to_euler()
main.render.filepath=str(ROOT/'preview/tools_T01_front.png');render()
main.camera.location=saved_location;main.camera.rotation_euler=saved_rotation
for area in bpy.context.screen.areas:
    if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.shading.type='MATERIAL'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/expedition_tools_T01.blend'))
report={'blender_version':bpy.app.version_string,'source_sha256':hashlib.sha256((ROOT/'source/tools_T01.json').read_bytes()).hexdigest(),'geometry_modifiers':False,'native_json_source':True,'game_verified':False,'sets':results,'outputs':{}}
report['rendered_dependency_sha256']=render_digest;report['save_only_display_metadata_update']=SAVE_ONLY
for f in [ROOT/'assembly/expedition_tools_T01.blend',*(ROOT/'preview').glob('*T01.png'),ROOT/'preview/tools_T01_front.png',ROOT/'preview/tools_previous.png',*(ROOT/'exports').glob('*T01.glb')]:report['outputs'][f.relative_to(ROOT).as_posix()]=hashlib.sha256(f.read_bytes()).hexdigest()
(ROOT/'reports/blender.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print('EXPEDITION_TOOLS_BLENDER_COMPLETE')
