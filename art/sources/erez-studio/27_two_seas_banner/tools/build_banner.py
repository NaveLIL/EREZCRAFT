"""BN01: real Blender poster scene, including native editable lettering."""
from pathlib import Path
import bpy,math,json,random,sys,argparse,hashlib
from mathutils import Vector,Euler
from bpy_extras.object_utils import world_to_camera_view
ROOT=Path(__file__).resolve().parents[1]
spec=json.loads((ROOT/'source/banner_BN01.json').read_text(encoding='utf8'))
parser=argparse.ArgumentParser();parser.add_argument('--draft',action='store_true');args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
random.seed(27)
for ob in list(bpy.data.objects):bpy.data.objects.remove(ob,do_unlink=True)
scene=bpy.context.scene;scene.name='EREZCRAFT / DVA MORYA / BN01'
scene.render.engine='CYCLES';scene.cycles.samples=20 if args.draft else 32;scene.cycles.use_denoising=True
scene.cycles.max_bounces=6;scene.cycles.transmission_bounces=3;scene.cycles.volume_bounces=1
scene.render.resolution_x=1600 if args.draft else spec['resolution'][0];scene.render.resolution_y=700 if args.draft else spec['resolution'][1]
scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='AgX'
scene.world.use_nodes=True;bg=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND');bg.inputs['Color'].default_value=(.045,.055,.075,1);bg.inputs['Strength'].default_value=.3
scene.render.film_transparent=False
collections={}
def coll(name):
    c=bpy.data.collections.new(name);scene.collection.children.link(c);collections[name]=c;return c
environment=coll('01 / Two seas');islands=coll('02 / Floating islands');fauna=coll('03 / Future wildlife concepts');aircraft=coll('04 / Strizh concept');overlay=coll('05 / Editable banner typography');stage=coll('06 / Camera and lighting')
def mat(name,color,rough=.7,emission=0,metallic=0):
    m=bpy.data.materials.new(name);m.use_nodes=True;s=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    s.inputs['Base Color'].default_value=(*color,1);s.inputs['Roughness'].default_value=rough;s.inputs['Metallic'].default_value=metallic
    s.inputs['Emission Color'].default_value=(*color,1);s.inputs['Emission Strength'].default_value=emission;return m
def emit(name,color,strength=1):
    m=bpy.data.materials.new(name);m.use_nodes=True;nodes=m.node_tree.nodes;nodes.clear();e=nodes.new('ShaderNodeEmission');e.inputs['Color'].default_value=(*color,1);e.inputs['Strength'].default_value=strength
    o=nodes.new('ShaderNodeOutputMaterial');m.node_tree.links.new(e.outputs[0],o.inputs[0]);return m
graphite=mat('Graphite',(.035,.047,.055));steel=mat('Weathered steel',(.11,.15,.16),.65);copper=mat('Copper',(.25,.12,.055),.55,metallic=.15)
teal=mat('Muted teal',(.025,.1,.1));darkflesh=mat('Jelly / dark olive',(.065,.075,.03),.4);flesh=mat('Jelly / amber membrane',(.19,.19,.065),.45)
glow=mat('Fauna / dim amber',(.42,.33,.07),.5,.6);cyan=mat('Instruments / muted cyan',(.07,.28,.28),.4,.8);lamp=mat('Warm lamps',(.8,.52,.17),.4,3)
stone=mat('Island basalt',(.025,.037,.042));moss=mat('Sparse olive growth',(.06,.09,.045));raymat=mat('Plastun carapace',(.045,.027,.034),.35)
glass=mat('Strizh glass',(.04,.17,.2),.15);s=next(n for n in glass.node_tree.nodes if n.type=='BSDF_PRINCIPLED');s.inputs['Transmission Weight'].default_value=.35
def move_collection(ob,c):
    for old in list(ob.users_collection):old.objects.unlink(ob)
    c.objects.link(ob)
def cube(name,center,size,material,c=fauna,rotation=(0,0,0),parent=None):
    x,y,z=[v/2 for v in size];verts=[(-x,-y,-z),(x,-y,-z),(x,y,-z),(-x,y,-z),(-x,-y,z),(x,-y,z),(x,y,z),(-x,y,z)]
    data=bpy.data.meshes.new(name);data.from_pydata(verts,[],[(3,2,1,0),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7),(4,5,6,7)]);data.materials.append(material)
    ob=bpy.data.objects.new(name,data);c.objects.link(ob);ob.location=center;ob.rotation_euler=rotation
    if parent:ob.parent=parent
    return ob
def beam(name,a,b,width,material,c=fauna,depth=None,parent=None):
    a=Vector(a);b=Vector(b);ob=cube(name,(a+b)/2,(width,depth or width,(b-a).length),material,c,parent=parent);ob.rotation_euler=(b-a).to_track_quat('Z','Y').to_euler();return ob
def ellipsoid(name,center,size,material,c=fauna,segments=16,rings=8,parent=None):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments,ring_count=rings,radius=1,location=center);ob=bpy.context.object;ob.name=name;ob.scale=size;ob.data.materials.append(material);move_collection(ob,c)
    if parent:ob.parent=parent
    return ob
def mesh(name,verts,faces,material,c=fauna):
    data=bpy.data.meshes.new(name);data.from_pydata(verts,[],faces);data.update();data.materials.append(material);ob=bpy.data.objects.new(name,data);c.objects.link(ob);return ob

# Import exactly the authored vehicle collection; no studio or owner scene state.
with bpy.data.libraries.load(spec['sources']['skat'],link=False) as (src,dst):
    dst.collections=[name for name in src.collections if name=='SK02 / Vehicle']
for c in dst.collections:
    if c:scene.collection.children.link(c)
ship=bpy.data.objects['SKAT_SK02'];ship.location=(22,35,-.93*1.5);ship.rotation_euler.z=math.radians(-25);ship.scale=(1.5,1.5,1.5)
ship['banner_role']='Existing SK02 model, retained without mesh changes'

# Reuse the actual continuous ocean material authored for OC01.
with bpy.data.libraries.load(spec['sources']['ocean'],link=False) as (src,dst):
    dst.materials=[n for n in src.materials if n=='OC01 / shared world-space currents']
lower=dst.materials[0];lower.name='BN01 / lower toxic sea / OC01 material'
if lower.node_tree.animation_data:lower.node_tree.animation_data_clear()
upper=lower.copy();upper.name='BN01 / upper toxic sea / gold concept'
palette=upper.node_tree.nodes.get('Wine-black / burgundy base')
for e,color in zip(palette.color_ramp.elements,[(.015,.018,.005),(.05,.06,.015),(.16,.17,.045),(.3,.29,.08)]):e.color=(*color,1)
for n in upper.node_tree.nodes:
    if n.type=='BSDF_PRINCIPLED':
        upper.node_tree.links.new(n.inputs['Base Color'].links[0].from_socket,n.inputs['Emission Color']);n.inputs['Emission Strength'].default_value=.22
    if n.type=='VALTORGB' and n.label=='Copper / plum film':
        for e,color in zip(n.color_ramp.elements,[(.11,.13,.035),(.18,.16,.05)]):e.color=(*color,1)
def sea(name,z,material,ceiling=False):
    verts=[(-2000,-100,z),(2000,-100,z),(2000,4000,z),(-2000,4000,z)]
    ob=mesh(name,verts,[(3,2,1,0)] if ceiling else [(0,1,2,3)],material,environment);return ob
sea('Lower / uninterrupted burgundy sea',0,lower);sea('Upper / inverted golden sea',42,upper,True)

# Layered far islands, with no land mass filling the title area.
for i,(x,y,z,r) in enumerate([(-50,65,13,7),(-19,95,19,6),(5,125,22,8),(47,90,14,6),(65,130,24,9),(-65,150,25,8)]):
    for j in range(18):
        px=x+random.uniform(-r,r);py=y+random.uniform(-r*.5,r*.5);top=z+random.uniform(-1,1.2);height=random.uniform(1.4,5)
        cube(f'Island_{i}_basalt_{j}',(px,py,top-height/2),(random.uniform(1.3,3.1),random.uniform(1.5,3),height),stone,islands)
    for j in range(7):
        xx=x+random.uniform(-r*.6,r*.6);yy=y+random.uniform(-r*.2,r*.2)
        cube(f'Island_{i}_crown_{j}',(xx,yy,z+random.uniform(1,3)),(.5,.6,random.uniform(2,5)),stone,islands)
# One middle-distance island ties the air gap together and gives a sense of scale.
for j in range(22):
    xx=3+random.uniform(-9,9);yy=88+random.uniform(-3,3);height=random.uniform(2,6)
    cube(f'Middle island / mass {j}',(xx,yy,13-height/2),(random.uniform(1.5,3.5),random.uniform(2,4),height),stone,islands)
for xx in [-3,2,7]:
    beam(f'Middle island / trunk {xx}',(xx,88,13),(xx+.5,88,17.4),.5,copper,islands)
    for zz,width in [(16.3,3.8),(17.5,5.0),(18.1,3.0)]:cube(f'Middle island / canopy {xx}_{zz}',(xx,88,zz),(width,2.8,.7),moss,islands)

# Upper sea jelly: faceted bell with plate details, segmented hanging tentacles.
jelly_center=Vector((27,41,37.6))
ellipsoid('Svodovaya / wide bell',jelly_center,(9,6,3.3),darkflesh)
ellipsoid('Svodovaya / recessed membrane',(27,41,35.9),(7.8,5.2,1.0),flesh)
for i in range(12):
    a=i*2*math.pi/12
    p=jelly_center+Vector((math.cos(a)*7.5,math.sin(a)*4.6,.6))
    cube(f'Svodovaya / bell plate {i}',p,(2.1,1.6,.8),darkflesh,rotation=(0,0,a))
    ellipsoid(f'Svodovaya / rim light {i}',(p.x,p.y,35.5),(.3,.3,.2),glow,segments=8,rings=4)
for index in range(8):
    a=index*2*math.pi/8;length=12+3*math.sin(index*1.7);start=Vector((27+math.cos(a)*5.8,41+math.sin(a)*3.8,35.8))
    previous=start
    for step in range(13):
        t=(step+1)/13
        point=start+Vector((math.sin(t*4+index)*2.2,math.cos(t*3+index)*1.2,-length*t))
        thickness=.7*(1-t)+.12
        beam(f'Svodovaya / tentacle {index:02}_{step:02}',previous,point,thickness,darkflesh)
        if step in [3,7,10]:cube(f'Svodovaya / sucker {index}_{step}',point,(thickness*.7,thickness*.9,.15),flesh)
        previous=point

# A suspended two-person craft, created as concept geometry for this announcement.
craft_root=bpy.data.objects.new('STRIZH / concept assembly',None);aircraft.objects.link(craft_root);craft_root.location=(8,11,24);craft_root.rotation_euler.z=math.radians(-18);craft_root.scale=(1.35,1.35,1.35)
for side in [-1,1]:
    cube(f'Strizh / upper gondola {side}',(side*2.7,0,2.9),(1.4,6.4,1.3),graphite,aircraft,parent=craft_root)
    cube(f'Strizh / copper binding {side}',(side*2.7,-1.8,2.9),(1.55,.3,1.45),copper,aircraft,parent=craft_root)
    cube(f'Strizh / gondola cap {side}',(side*2.7,-3.3,2.9),(1.1,.4,.95),teal,aircraft,parent=craft_root)
    for yy in [-1.7,1.7]:beam(f'Strizh / hanger {side}_{yy}',(side*2.7,yy,2.3),(side*1.5,yy,-.5),.18,steel,aircraft,parent=craft_root)
    for zz in [-.9,.9]:cube(f'Strizh / cab side beam {side}_{zz}',(side*1.8,0,zz),(.2,4.7,.2),copper,aircraft,parent=craft_root)
    cube(f'Strizh / side glass {side}',(side*1.8,-.1,.1),(.1,4.2,1.4),glass,aircraft,parent=craft_root)
    cube(f'Strizh / lamp {side}',(side*1.4,-2.7,-.8),(.4,.3,.35),lamp,aircraft,parent=craft_root)
    # Compact duct rings built from twelve beams, with four propeller blades.
    for k in range(12):
        a=k*2*math.pi/12;b=(k+1)*2*math.pi/12
        beam(f'Strizh / duct {side}_{k}',(side*2.7+math.cos(a)*1.1,2.9,2.9+math.sin(a)*1.1),(side*2.7+math.cos(b)*1.1,2.9,2.9+math.sin(b)*1.1),.15,steel,aircraft,parent=craft_root)
    for k in range(4):
        a=k*math.pi/2
        beam(f'Strizh / prop {side}_{k}',(side*2.7,2.9,2.9),(side*2.7+math.cos(a),2.9,2.9+math.sin(a)),.18,copper,aircraft,parent=craft_root)
cube('Strizh / cabin floor',(0,0,-1.1),(3.8,5.0,.35),graphite,aircraft,parent=craft_root)
cube('Strizh / canopy frame',(0,0,1.1),(3.8,5.0,.25),graphite,aircraft,parent=craft_root)
cube('Strizh / forward glass',(0,-2.4,.05),(3.5,.12,1.8),glass,aircraft,parent=craft_root)
cube('Strizh / rear wall',(0,2.4,0),(3.5,.2,1.9),teal,aircraft,parent=craft_root)
for xx in [-1.75,0,1.75]:cube(f'Strizh / front mullion {xx}',(xx,-2.5,0),(.13,.2,2.0),steel,aircraft,parent=craft_root)
for xx in [-.8,.8]:cube(f'Strizh / cargo cassette {xx}',(xx,.4,-1.55),(1.3,2.3,.6),teal,aircraft,parent=craft_root)
beam('Strizh / sample arm',(2,-.7,-1),(4,-1.1,-3),.16,copper,aircraft,parent=craft_root)

# Lower sea giant's back breaks the surface; the rest is hidden by opaque toxin.
rayroot=bpy.data.objects.new('PLASTUN / concept silhouette',None);fauna.objects.link(rayroot);rayroot.location=(-4,29,-.4);rayroot.rotation_euler.z=math.radians(-24)
ellipsoid('Plastun / armored back',(0,0,-.12),(6.8,11.5,1.55),raymat,parent=rayroot)
for j in range(11):
    yy=-10+j*2;w=math.sqrt(max(.01,1-(yy/12)**2))*12
    cube(f'Plastun / carapace segment {j}',(0,yy,.6),(w*.86,1.8,.56),raymat,parent=rayroot)
    for side in [-1,1]:
        ob=cube(f'Plastun / lateral lobe {side}_{j}',(side*(w/2+.8),yy,-.1),(2,1.9,.3),raymat,parent=rayroot);ob.rotation_euler.y=side*.16
        if j%4==0:cube(f'Plastun / growth {side}_{j}',(side*w*.3,yy,1.1),(.3,.4,.5),moss,parent=rayroot)
beam('Plastun / root tail',(0,10,.15),(0,20,-.4),.4,raymat,parent=rayroot)

# Small schools visually connect the ceiling with its larger inhabitants.
for i in range(18):
    xx=4+i*1.5+random.uniform(-1,1);yy=48+random.uniform(-4,4);zz=36+math.sin(i*.6)*1.2
    ellipsoid(f'Upper swimmer {i}',(xx,yy,zz),(.7,.3,.17),flesh,segments=8,rings=4)

# Camera: below the upper sea, above the lower sea, so both surfaces are visible.
camdata=bpy.data.cameras.new('BN01 Banner Camera');camera=bpy.data.objects.new('BN01 Banner Camera',camdata);stage.objects.link(camera);scene.camera=camera
camera.location=(0,-70,21);target=Vector((0,20,20));camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler();camdata.type='PERSP';camdata.lens=30;camdata.sensor_width=36
def area(name,location,energy,color,size,target):
    data=bpy.data.lights.new(name,'AREA');data.energy=energy;data.color=color;data.shape='DISK';data.size=size
    ob=bpy.data.objects.new(name,data);stage.objects.link(ob);ob.location=location;ob.rotation_euler=(Vector(target)-ob.location).to_track_quat('-Z','Y').to_euler();return ob
area('Ship / cool key',(14,-10,20),13000,(.57,.76,1),15,(20,8,3))
area('Ship / warm rim',(34,28,18),18000,(1,.49,.2),15,(20,8,5))
area('Ceiling / warm wash',(18,28,27),18000,(1,.82,.36),24,(22,40,40))
area('Jelly / teal rim',(42,52,25),13000,(.22,.65,.6),16,(27,41,32))
area('Strizh / key',(-3,-3,32),5500,(.6,.8,1),10,(8,11,24))
area('Plastun / red edge',(-8,8,9),6500,(.8,.19,.14),12,(-4,29,0))
spot=bpy.data.lights.new('Strizh / survey searchlight','SPOT');spot.energy=2200;spot.color=(.4,.78,.85);spot.spot_size=.5;spot.spot_blend=.5;spot.shadow_soft_size=.3
ob=bpy.data.objects.new('Strizh / survey searchlight',spot);stage.objects.link(ob);ob.location=(9,11,27);ob.rotation_euler=(Vector((25,38,37))-ob.location).to_track_quat('-Z','Y').to_euler()
# Thin atmospheric veil: depth separation without hiding silhouette details.
fog=bpy.data.materials.new('Depth atmosphere');fog.use_nodes=True;fog.node_tree.nodes.clear();v=fog.node_tree.nodes.new('ShaderNodeVolumeScatter');v.inputs['Color'].default_value=(.35,.42,.45,1);v.inputs['Density'].default_value=.00075;v.inputs['Anisotropy'].default_value=.15
o=fog.node_tree.nodes.new('ShaderNodeOutputMaterial');fog.node_tree.links.new(v.outputs[0],o.inputs['Volume'])
cube('Atmosphere volume',(0,400,21),(3600,870,42),fog,environment)

# All lettering and the logo are native scene objects, parented to the camera.
W=4*camdata.sensor_width/camdata.lens;H=W*spec['resolution'][1]/spec['resolution'][0]
def screen_obj(ob,x,y,z=-4):
    ob.parent=camera;ob.location=(x,y,z);ob.rotation_euler=(0,0,0);move_collection(ob,overlay)
    for field in ['visible_shadow','visible_diffuse','visible_glossy','visible_transmission','visible_volume_scatter']:
        if hasattr(ob,field):setattr(ob,field,False)
    return ob
font=bpy.data.fonts.load('C:/Windows/Fonts/arialbd.ttf');display=bpy.data.fonts.load('C:/Windows/Fonts/impact.ttf')
white=emit('Type / ivory',(.82,.85,.75));secondary=emit('Type / muted ivory',(.48,.58,.55));goldtype=emit('Type / gold',(.55,.43,.21))
def text(name,body,x,y,size,material=white,font=font,align='LEFT'):
    curve=bpy.data.curves.new(name,'FONT');curve.body=body;curve.font=font;curve.size=size;curve.align_x=align;curve.space_character=1.08
    ob=bpy.data.objects.new(name,curve);overlay.objects.link(ob);curve.materials.append(material);screen_obj(ob,x,y,-3.98);return ob
# Screen-space translucent dark gradient supports the headline over the environment.
gradient=bpy.data.materials.new('Type / left atmosphere gradient');gradient.use_nodes=True;n=gradient.node_tree.nodes;n.clear();l=gradient.node_tree.links
tc=n.new('ShaderNodeTexCoord');sep=n.new('ShaderNodeSeparateXYZ');l.new(tc.outputs['Generated'],sep.inputs[0])
cr=n.new('ShaderNodeValToRGB');cr.color_ramp.elements[0].position=.05;cr.color_ramp.elements[0].color=(.96,.96,.96,1);cr.color_ramp.elements[1].position=.62;cr.color_ramp.elements[1].color=(0,0,0,1)
l.new(sep.outputs['X'],cr.inputs['Fac']);tr=n.new('ShaderNodeBsdfTransparent');em=n.new('ShaderNodeEmission');em.inputs['Color'].default_value=(.006,.01,.014,1);mix=n.new('ShaderNodeMixShader');l.new(cr.outputs[0],mix.inputs[0]);l.new(tr.outputs[0],mix.inputs[1]);l.new(em.outputs[0],mix.inputs[2]);out=n.new('ShaderNodeOutputMaterial');l.new(mix.outputs[0],out.inputs[0])
ob=mesh('Title / gradient',[(-W/2,-H/2,0),(W/2,-H/2,0),(W/2,H/2,0),(-W/2,H/2,0)],[(0,1,2,3)],gradient,overlay);screen_obj(ob,0,0,-4.02)
left=-W*.435
text('Brand',spec['brand'],left+.22,H*.365,.07)
text('Title / DVA',spec['title'][0],left,H*.08,.45,font=display)
text('Title / MORYA',spec['title'][1],left,-H*.12,.45,font=display)
text('Strapline',spec['strapline'],left,-H*.245,.07,secondary)
text('Features',spec['feature_line'],left,-H*.365,.045,secondary)
text('Development status',spec['status_line'],W*.44,-H*.43,.045,goldtype,align='RIGHT')
# Thin gold accent bar is native geometry.
ob=mesh('Title / gold rule',[(0,0,0),(.64,0,0),(.64,.009,0),(0,.009,0)],[(0,1,2,3)],goldtype,overlay);screen_obj(ob,left,-H*.29,-3.99)
# Use approved emblem PNG unchanged and pack the exact original into this scene.
logo=bpy.data.materials.new('Approved EREZCRAFT emblem');logo.use_nodes=True;ns=logo.node_tree.nodes;ns.clear();ls=logo.node_tree.links
im=ns.new('ShaderNodeTexImage');im.image=bpy.data.images.load(str(ROOT/'source/erezcraft-emblem.png'));im.image.pack()
uv=ns.new('ShaderNodeTexCoord');ls.new(uv.outputs['UV'],im.inputs['Vector']);e=ns.new('ShaderNodeEmission');ls.new(im.outputs['Color'],e.inputs['Color']);t=ns.new('ShaderNodeBsdfTransparent');m=ns.new('ShaderNodeMixShader');ls.new(im.outputs['Alpha'],m.inputs[0]);ls.new(t.outputs[0],m.inputs[1]);ls.new(e.outputs[0],m.inputs[2]);o=ns.new('ShaderNodeOutputMaterial');ls.new(m.outputs[0],o.inputs[0])
size=.18;ob=mesh('Approved emblem',[(-size/2,-size/2,0),(size/2,-size/2,0),(size/2,size/2,0),(-size/2,size/2,0)],[(0,1,2,3)],logo,overlay)
uvlayer=ob.data.uv_layers.new(name='Approved logo UV')
for li,uvv in zip(ob.data.polygons[0].loop_indices,[(0,0),(1,0),(1,1),(0,1)]):uvlayer.data[li].uv=uvv
screen_obj(ob,left+.09,H*.375,-3.97)
scene['status']='Native Blender concept teaser for a future update; not an in-game screenshot or release announcement'
scene['concept_entities']='Strizh, Svodovaya medusa, Plastun, upper swimmers: illustrative concept meshes only'
scene['sources']='SK02 authored ship, OC01 authored material, approved EREZCRAFT emblem'
scene.frame_set(1);bpy.context.view_layer.update()
ship_points=[world_to_camera_view(scene,camera,o.matrix_world@Vector(v)) for o in ship.children_recursive if o.type=='MESH' for v in o.bound_box if (o.matrix_world@Vector(v)).z>.05]
ship_frame={'minimum':[min(p[i] for p in ship_points) for i in range(2)],'maximum':[max(p[i] for p in ship_points) for i in range(2)]}
assert ship_frame['minimum'][0]>.04 and ship_frame['minimum'][1]>.03 and ship_frame['maximum'][0]<.96 and ship_frame['maximum'][1]<.95,ship_frame
for f in bpy.data.fonts:
    if f.filepath and f.filepath!='<builtin>':
        try:f.pack()
        except RuntimeError:pass
bpy.context.preferences.filepaths.save_version=0
for screen in bpy.data.screens:
    for a in screen.areas:
        if a.type=='VIEW_3D':a.spaces.active.region_3d.view_perspective='CAMERA'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/two_seas_BN01.blend'))
suffix='draft' if args.draft else 'banner'
scene.render.filepath=str(ROOT/f'preview/two_seas_BN01_{suffix}.png');bpy.ops.render.render(write_still=True)
report={'revision':'BN01','blender_version':bpy.app.version_string,'native_blender_render':True,'imagegen_used':False,
    'resolution':[scene.render.resolution_x,scene.render.resolution_y],'objects':len(bpy.data.objects),'editable_text_objects':len([o for o in overlay.objects if o.type=='FONT']),
    'all_external_images_packed':all(im.packed_file for im in bpy.data.images if im.source=='FILE'),
    'logo_sha256':hashlib.sha256((ROOT/'source/erezcraft-emblem.png').read_bytes()).hexdigest(),
    'ship_visible_geometry_frame_bounds':ship_frame,'future_entities_are_concept_geometry':True,'published':False,'game_verified':False}
(ROOT/f'reports/{suffix}.json').write_text(json.dumps(report,indent=2)+'\n');print('TWO_SEAS_BN01_COMPLETE',json.dumps(report))
