"""Blender meshes, UVs and scenes generated from the exact native Minecraft JSON export."""
from pathlib import Path
import bpy,json,math,hashlib,sys
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
def digest(design):return hashlib.sha256(json.dumps(design,sort_keys=True).encode()).hexdigest()
design=json.loads((ROOT/'source/worktools_WT05.json').read_text(encoding='utf-8'))
SAVE_ONLY='--save-only' in sys.argv
render_digest=digest(design)
if SAVE_ONLY:
    previous=json.loads((ROOT/'reports/blender_WT05.json').read_text());assert previous['rendered_dependency_sha256']==render_digest,'Changed render dependency; rerender required'
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
    scene['source']='source/worktools_WT05.json';scene['units']='16 model units per game block';scene['status']='Geometry review, game installation pending approval'
    scene.render.engine='CYCLES';scene.cycles.samples=12;scene.cycles.use_denoising=True
    scene.render.resolution_x=1920 if group else 1100;scene.render.resolution_y=1080 if group else 1100;scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
    world=bpy.data.worlds.new(name+' world');world.use_nodes=True;scene.world=world;world.node_tree.nodes.get('Background').inputs['Color'].default_value=(.10,.135,.16,1);world.node_tree.nodes.get('Background').inputs['Strength'].default_value=.8
    for label,loc,power,size in [('Key',(15,-30,40),11000,25),('Fill',(-30,-18,20),8500,23),('Rim',(8,25,28),13000,20)]:
        light=bpy.data.lights.new(label,'AREA');light.energy=power;light.size=size;obj=bpy.data.objects.new(label,light);scene.collection.objects.link(obj);obj.location=loc;obj.rotation_euler=(Vector((0,0,8))-obj.location).to_track_quat('-Z','Y').to_euler()
    cam=bpy.data.cameras.new('Geometry review');obj=bpy.data.objects.new('Geometry review',cam);scene.collection.objects.link(obj);obj.location=(12,-100,29) if group else (16,-65,23);target=Vector((0,0,13));obj.rotation_euler=(target-obj.location).to_track_quat('-Z','Y').to_euler();cam.type='ORTHO';cam.ortho_scale=116 if group else 32;scene.camera=obj
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
    scene=setup('Realm worktools / '+('WT05' if new else 'previous'),True)
    for i,n in enumerate(names):
        root=build(scene,n,(i%4-1.5)*17,new);root.location.z=17 if i<4 else -17
    scene.render.filepath=str(ROOT/'preview'/('worktools_WT05.png' if new else 'worktools_WT04_before_WT05.png'));render()
    if new:main=scene
for steel in (False,True):
    tier='riftsteel' if steel else 'riftsilver'
    scene=setup(tier+' / WT05',True);scene.camera.data.ortho_scale=82
    for i,n in enumerate(n for n in names if n.startswith(tier)):build(scene,n,(i-1.5)*17,True)
    scene.render.filepath=str(ROOT/'preview'/(tier+'_WT05.png'));render()
for name in names:
    scene=setup('WT05 / '+name,False);root=build(scene,name,0,True)
    bpy.ops.object.select_all(action='DESELECT');root.select_set(True)
    for o in scene.objects:
        if o.parent==root:o.select_set(True)
    bpy.context.view_layer.objects.active=root
    bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports'/(name+'_WT05.glb')),export_format='GLB',use_selection=True)
    results[name]={'elements':len(design['models'][name]['model']['elements']),'mesh_objects':len([o for o in scene.objects if o.parent==root])}
bpy.context.window.scene=main
for area in bpy.context.screen.areas:
    if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.shading.type='MATERIAL'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/realm_worktools_WT05.blend'))
report={'blender_version':bpy.app.version_string,'source_sha256':hashlib.sha256((ROOT/'source/worktools_WT05.json').read_bytes()).hexdigest(),'native_json_source':True,'geometry_modifiers':False,'game_verified':False,'sets':results,'outputs':{}}
for f in [ROOT/'assembly/realm_worktools_WT05.blend',*(ROOT/'preview').glob('*.png'),*(ROOT/'exports').glob('*.glb')]:report['outputs'][f.relative_to(ROOT).as_posix()]=hashlib.sha256(f.read_bytes()).hexdigest()
(ROOT/'reports/blender_WT05.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print('WORKTOOLS_BLENDER_COMPLETE')
