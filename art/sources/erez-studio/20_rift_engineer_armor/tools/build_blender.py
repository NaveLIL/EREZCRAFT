"""Bake real geometry/UVs, freeze the armor mesh JSON, and render actual Blender scenes."""
from pathlib import Path
import bpy,bmesh,json,math,hashlib,zipfile,sys,random
from mathutils import Vector,Euler,Matrix
sys.path.insert(0,str(Path(__file__).resolve().parent/'.deps'))
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parents[1]
data=json.loads((ROOT/'source/armor_E04_parameters.json').read_text(encoding='utf-8'))
sha=lambda b:hashlib.sha256(b).hexdigest()
for obj in list(bpy.data.objects):bpy.data.objects.remove(obj,do_unlink=True)
for scene in list(bpy.data.scenes):
    if scene!=bpy.context.scene:bpy.data.scenes.remove(scene)

# All new fabric/metal texels come from the pinned game's original textures, palette-only.
MC=Path('C:/Users/Dimon/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
CAT=REPO/'catalog/minecraft_rift_engineer_reference';CAT.mkdir(exist_ok=True)
refs={};provenance=[]
for name in ('black_wool','gray_wool','brown_wool','iron_block','dark_oak_log','white_wool'):
    with zipfile.ZipFile(MC) as z:raw=z.read('assets/minecraft/textures/block/'+name+'.png')
    f=CAT/(name+'.png')
    if f.exists():assert f.read_bytes()==raw
    else:f.write_bytes(raw)
    refs[name]=Image.open(f).convert('RGBA');provenance.append({'asset':'minecraft:block/'+name,'file':str(f),'sha256':sha(raw),'source':'Pinned Minecraft1.21.1 client JAR'})
# Reuse the preserved S03 cloth texels instead of high-contrast wool noise.
legacy=json.loads((ROOT/'source/reference/avatar_S03.json').read_text(encoding='utf-8'))
uv=next(p for p in legacy['parts'] if p['name']=='hood_base')['uv']['south']
cloth=Image.open(ROOT/'source/reference/avatar_atlas_S03.png').convert('RGBA').crop((uv['sx'],uv['sy'],uv['ex'],uv['ey']))
refs['S03_cloth']=cloth;cloth_path=ROOT/'source/reference/S03_cloth_tile.png';cloth.save(cloth_path)
provenance.append({'asset':'Preserved S03 hood cloth','file':str(cloth_path),'source_region':uv,'operation':'Unchanged subrectangle from original S03 atlas','sha256':sha(cloth_path.read_bytes())})
tiles={}
for role,hexcolor in data['palette'].items():
    name='S03_cloth' if role in ('coat','panel','dark','seam') else 'black_wool' if role=='black' else 'brown_wool' if role in ('lining','leather') else 'iron_block' if role in ('metal','steel','brass') else 'white_wool'
    src=refs[name];base=tuple(int(hexcolor[i:i+2],16) for i in (1,3,5));lum=[.2126*r+.7152*g+.0722*b for r,g,b,a in src.getdata()];mean=sum(lum)/len(lum)
    dst=src.copy();values=[]
    for (r,g,b,a),L in zip(src.getdata(),lum):
        strength=max(.50,min(1.55,L/max(mean,1)))
        if role in ('light','cyan'):strength=.9+.1*strength
        elif role in ('black','leather'):strength=.8+.2*strength
        values.append(tuple(min(255,round(c*strength)) for c in base)+(a,))
    dst.putdata(values);tiles[role]=dst
    original=cloth_path if name=='S03_cloth' else CAT/(name+'.png');derived=ROOT/'source'/('palette_'+role+'.png');dst.save(derived)
    provenance.append({'role':role,'original':str(original),'derived':derived.name,'sha256':sha(derived.read_bytes()),'operation':'Pointwise luminance palette only; original coordinates, dimensions and alpha preserved','tint':hexcolor})

ATLAS=1024;atlas=Image.new('RGBA',(ATLAS,ATLAS),(0,0,0,0));cursor=[1,1,0];cache={}
def tile(role,w,h,variant):
    w=max(1,w);h=max(1,h);key=(role,w,h,variant)
    if key in cache:return cache[key]
    if cursor[0]+w+2>=ATLAS:cursor[0]=1;cursor[1]+=cursor[2]+2;cursor[2]=0
    assert cursor[1]+h+2<ATLAS,'Atlas full'
    x,y=cursor[:2];cursor[0]+=w+2;cursor[2]=max(cursor[2],h)
    im=Image.new('RGBA',(w,h));source=tiles[role];tw,th=source.size
    im.putdata([source.getpixel(((xx+variant*3)%tw,(yy+variant*5)%th)) for yy in range(h) for xx in range(w)]);atlas.paste(im,(x,y))
    cache[key]=(x,y,x+w,y+h);return cache[key]

SIDES={'north':(1,0,3,2),'south':(4,5,6,7),'west':(0,4,7,3),'east':(5,1,2,6),'down':(0,1,5,4),'up':(7,6,2,3)}
def cube_mesh(p):
    x,y,z=p['offset'];w,h,d=p['size']
    raw=[(x,y,z),(x+w,y,z),(x+w,y+h,z),(x,y+h,z),(x,y,z+d),(x+w,y,z+d),(x+w,y+h,z+d),(x,y+h,z+d)]
    return raw,list(SIDES.values())
def bake(p):
    raw,faces=(p['vertices'],[f['indices'] for f in p['faces']]) if 'vertices' in p else cube_mesh(p)
    mesh=bpy.data.meshes.new(p['name']+' authoring');mesh.from_pydata(raw,[],faces);mesh.update()
    obj=bpy.data.objects.new(p['name']+' authoring',mesh);bpy.context.scene.collection.objects.link(obj);bpy.context.view_layer.objects.active=obj;obj.select_set(True)
    bm=bmesh.new();bm.from_mesh(mesh);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(mesh);bm.free()
    if p.get('bevel',0)>0:
        bevel=obj.modifiers.new('Baked physical edge geometry','BEVEL');bevel.width=p['bevel'];bevel.segments=1;bevel.affect='EDGES';bevel.use_clamp_overlap=True
        bpy.ops.object.modifier_apply(modifier=bevel.name)
    m=obj.data;rotation=Euler(tuple(math.radians(v) for v in p.get('rotation',[0,0,0])),'XYZ').to_matrix();pivot=Vector(p.get('pivot',[0,0,0]))
    vertices=[list(rotation@v.co+pivot) for v in m.vertices];polygons=[]
    # UVs use the actual unrotated face dimensions and preserve texel density on bevels.
    variant=sum(p['name'].encode())%7
    for face in m.polygons:
        inds=list(face.vertices);coords=[m.vertices[i].co for i in inds];normal=face.normal
        axis=max(range(3),key=lambda a:abs(normal[a]));axes={0:(2,1),1:(0,2),2:(0,1)}[axis]
        pts=[(v[axes[0]],v[axes[1]]) for v in coords];lo=[min(v[a] for v in pts) for a in (0,1)];hi=[max(v[a] for v in pts) for a in (0,1)]
        dims=[max(hi[i]-lo[i],.01) for i in (0,1)];w,h=[max(1,math.ceil(d*4)) for d in dims];x,y,X,Y=tile(p['role'],w,h,variant)
        uvs=[[x+(u-lo[0])/dims[0]*w,y+(v-lo[1])/dims[1]*h] for u,v in pts]
        polygons.append({'indices':inds,'uv':uvs})
    result={k:p[k] for k in ('name','bone','slot','role','glow')};result.update(vertices=vertices,faces=polygons)
    if 'derived_from' in p:result['derived_from']=p['derived_from']
    bpy.data.objects.remove(obj,do_unlink=True);return result

frozen=[bake(p) for p in data['parts']]

# Correctly ordered Hebrew is baked from real glyph outlines into ordinary mesh data.
for glyph in data['glyphs']:
    font=bpy.data.fonts.load('C:/Windows/Fonts/arialbd.ttf')
    curve=bpy.data.curves.new(glyph['name'],'FONT');curve.font=font;curve.body=glyph['visual_left_to_right'];curve.size=3;curve.extrude=.008;curve.resolution_u=2;curve.fill_mode='BOTH'
    obj=bpy.data.objects.new(glyph['name'],curve);bpy.context.scene.collection.objects.link(obj);bpy.context.view_layer.objects.active=obj;obj.select_set(True)
    bpy.ops.object.convert(target='MESH');m=obj.data
    a=[min(v.co[i] for v in m.vertices) for i in range(3)];b=[max(v.co[i] for v in m.vertices) for i in range(3)]
    scale=min(glyph['width']/(b[0]-a[0]),glyph['height']/(b[1]-a[1]));cx,cy,cz=glyph['center']
    verts=[[cx-(v.co.x-(a[0]+b[0])/2)*scale,cy+(b[1]-v.co.y)*scale,cz+v.co.z*scale] for v in m.vertices]
    x,y,X,Y=tile(glyph['role'],4,4,0)
    faces=[{'indices':list(f.vertices),'uv':[[x+2,y+2] for i in f.vertices]} for f in m.polygons]
    frozen.append({'name':glyph['name'],'bone':glyph['bone'],'slot':glyph['slot'],'role':glyph['role'],'glow':False,'vertices':verts,'faces':faces,'text':glyph['text'],'reading_order':'Actual glyph outlines; visual left-to-right zayin-resh-alef when seen from back'})
    bpy.data.objects.remove(obj,do_unlink=True)
atlas.save(ROOT/'source/armor_E04_atlas.png')
author={k:data[k] for k in ('revision','units','basis','pivots','review_pose','lineage','scope')};author.update(parts=frozen,atlas={'file':'armor_E04_atlas.png','width':ATLAS,'height':ATLAS,'sha256':sha((ROOT/'source/armor_E04_atlas.png').read_bytes())},palette=data['palette'],texture_provenance=provenance,
    authoritative_geometry='This JSON contains the final mesh vertices, face topology and UVs used in the Blender render. Parameters are authoring inputs, not an additional release geometry source.')
(ROOT/'source/armor_E04.json').write_text(json.dumps(author,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

# Re-import only the frozen mesh: saved Blender objects and GLB are exact render/export consumers.
A=Matrix(((1,0,0),(0,0,1),(0,-1,0)))
image=bpy.data.images.load(str(ROOT/'source/armor_E04_atlas.png'));image.pack()
materials={}
def material(glow):
    if glow in materials:return materials[glow]
    m=bpy.data.materials.new('EREZ packed atlas'+(' / fullbright cyan' if glow else ''));m.use_nodes=True
    b=m.node_tree.nodes.get('Principled BSDF');t=m.node_tree.nodes.new('ShaderNodeTexImage');t.image=image;t.interpolation='Closest';m.node_tree.links.new(t.outputs['Color'],b.inputs['Base Color'])
    b.inputs['Roughness'].default_value=.88;b.inputs['Specular IOR Level'].default_value=.1
    if glow:m.node_tree.links.new(t.outputs['Color'],b.inputs['Emission Color']);b.inputs['Emission Strength'].default_value=1.1
    materials[glow]=m;return m
def model(scene,name,x,yaw,pose=True):
    root=bpy.data.objects.new(name,None);scene.collection.objects.link(root);root.location=(x,0,0);root.rotation_euler.z=math.radians(yaw);rig={}
    for bone,pivot in author['pivots'].items():
        b=bpy.data.objects.new(name+'/'+bone,None);scene.collection.objects.link(b);b.parent=root;b.location=(pivot[0],pivot[2],24-pivot[1]);rig[bone]=b
        if pose and bone in author['review_pose']:
            e=Euler(tuple(math.radians(v) for v in author['review_pose'][bone]),'XYZ');b.rotation_euler=(A@e.to_matrix()@A.inverted()).to_euler()
    for p in frozen:
        mesh=bpy.data.meshes.new(p['name']);mesh.from_pydata([A@Vector(v) for v in p['vertices']],[],[f['indices'] for f in p['faces']]);mesh.update();mesh.materials.append(material(p['glow']))
        uv=mesh.uv_layers.new(name='Authoritative armor UV')
        for poly,face in zip(mesh.polygons,p['faces']):
            for li,(u,v) in zip(poly.loop_indices,face['uv']):uv.data[li].uv=(u/ATLAS,1-v/ATLAS)
        obj=bpy.data.objects.new(p['name'],mesh);scene.collection.objects.link(obj);obj.parent=rig[p['bone']];obj['source_part']=p['name'];obj['armor_slot']=p['slot'];obj['glow']=p['glow']
    root['source']='source/armor_E04.json';return root
def setup(name,group=True):
    scene=bpy.data.scenes.new(name);bpy.context.window.scene=scene
    scene.render.engine='CYCLES';scene.cycles.samples=32;scene.cycles.use_denoising=True;scene.render.threads_mode='FIXED';scene.render.threads=12
    scene.render.resolution_x=1920 if group else 1200;scene.render.resolution_y=1280 if group else 1500;scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
    world=bpy.data.worlds.new(name+' world');world.use_nodes=True;scene.world=world;world.node_tree.nodes.get('Background').inputs['Color'].default_value=(.20,.20,.20,1);world.node_tree.nodes.get('Background').inputs['Strength'].default_value=.5
    # Matte floor, real area lighting; no image overlays or concept-image projection.
    mesh=bpy.data.meshes.new('Floor');mesh.from_pydata([(-200,-200,-.4),(200,-200,-.4),(200,200,-.4),(-200,200,-.4)],[],[(0,1,2,3)]);obj=bpy.data.objects.new('Matte floor',mesh);scene.collection.objects.link(obj)
    floor=bpy.data.materials.get('Matte neutral gray') or bpy.data.materials.new('Matte neutral gray');floor.diffuse_color=(.22,.22,.22,1);mesh.materials.append(floor)
    for label,loc,power,size in [('Key',(-25,-35,50),16000,25),('Fill',(28,-20,28),6500,22),('Rim',(12,30,42),13500,25)]:
        lamp=bpy.data.lights.new(label,'AREA');lamp.energy=power;lamp.size=size;ob=bpy.data.objects.new(label,lamp);scene.collection.objects.link(ob);ob.location=loc;ob.rotation_euler=(Vector((0,0,17))-ob.location).to_track_quat('-Z','Y').to_euler()
    cam=bpy.data.cameras.new('Review camera');ob=bpy.data.objects.new('Review camera',cam);scene.collection.objects.link(ob);ob.location=(0,-100,25);ob.rotation_euler=(Vector((0,0,16))-ob.location).to_track_quat('-Z','Y').to_euler();cam.type='ORTHO';cam.ortho_scale=56 if group else 26;scene.camera=ob
    scene['authority']='source/armor_E04.json';scene['status']='Visual review prototype, not implemented in game';return scene

scene=setup('E04 / front and back');front=model(scene,'E04 Front',-12.3,-12);back=model(scene,'E04 Back',12.3,168)
scene.render.filepath=str(ROOT/'preview/engineer_E04_front_back.png');bpy.ops.render.render(write_still=True);main=scene
for name,yaw in [('front',0),('back',180),('quarter',-25)]:
    scene=setup('E04 / '+name,False);root=model(scene,'E04 '+name,0,yaw)
    scene.render.filepath=str(ROOT/'preview'/('engineer_E04_'+name+'.png'));bpy.ops.render.render(write_still=True)
    if name=='quarter':
        bpy.ops.object.select_all(action='DESELECT');root.select_set(True)
        def select_children(obj):
            for child in obj.children:child.select_set(True);select_children(child)
        select_children(root);bpy.context.view_layer.objects.active=root
        bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/EREZ_Rift_Engineer_Armor_E04.glb'),export_format='GLB',use_selection=True)
bpy.context.window.scene=main
for area in bpy.context.screen.areas:
    if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.shading.type='MATERIAL'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/EREZ_Rift_Engineer_Armor_E04.blend'))
report={'revision':author['revision'],'blender_version':bpy.app.version_string,'source_sha256':sha((ROOT/'source/armor_E04.json').read_bytes()),'parts':len(frozen),'faces':sum(len(p['faces']) for p in frozen),'vertices':sum(len(p['vertices']) for p in frozen),'atlas_size':[ATLAS,ATLAS],'atlas_used_height':cursor[1]+cursor[2], 'rendered_from_frozen_mesh':True,'game_implemented':False,'geometry_modifiers_remaining':False,'outputs':{}}
for f in [*(ROOT/'preview').glob('*.png'),ROOT/'assembly/EREZ_Rift_Engineer_Armor_E04.blend',ROOT/'exports/EREZ_Rift_Engineer_Armor_E04.glb']:report['outputs'][f.relative_to(ROOT).as_posix()]=sha(f.read_bytes())
(ROOT/'reports/blender.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print('ENGINEER_ARMOR_E04_BLENDER_COMPLETE',json.dumps({k:report[k] for k in ('parts','faces','vertices','atlas_used_height')}))
