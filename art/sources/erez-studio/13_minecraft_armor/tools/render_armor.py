"""Blender review scenes from the armor JSON. No Minecraft files are changed."""
import bpy
import hashlib
import json
import math
from pathlib import Path
from mathutils import Vector, Euler

ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'source/armor_design.json';design=json.loads(source.read_text(encoding='utf-8'))
catalog=Path(design['textures_reference_catalog'])
for o in list(bpy.data.objects):bpy.data.objects.remove(o,do_unlink=True)
for folder in ('preview','assembly','exports','reports'):(ROOT/folder).mkdir(exist_ok=True)
images={};materials={}
def image(path):
    if path not in images:
        images[path]=bpy.data.images.load(str(catalog/path));images[path].pack()
    return images[path]
def linear(v):return v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4
def color(hexcolor):return tuple(linear(int(hexcolor[i:i+2],16)/255) for i in (1,3,5))
def material(label,path,tint='#ffffff',overlay=None):
    key=(path,tint,overlay)
    if key in materials:return materials[key]
    m=bpy.data.materials.new(label);m.use_nodes=True
    m.diffuse_color=(*color(tint),1)
    bsdf=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=image(path);tex.interpolation='Closest'
    multiply=m.node_tree.nodes.new('ShaderNodeMixRGB');multiply.blend_type='MULTIPLY'
    multiply.inputs[0].default_value=1;multiply.inputs[2].default_value=(*color(tint),1)
    m.node_tree.links.new(tex.outputs['Color'],multiply.inputs[1])
    m.node_tree.links.new(multiply.outputs[0],bsdf.inputs['Base Color'])
    m.node_tree.links.new(multiply.outputs[0],bsdf.inputs['Emission Color'])
    m.node_tree.links.new(tex.outputs['Alpha'],bsdf.inputs['Alpha'])
    if overlay:
        # Minecraft draws both cutout layers at the same depth. Composite them in
        # one shader so Cycles does not skip the base behind a coincident alpha face.
        over=m.node_tree.nodes.new('ShaderNodeTexImage');over.image=image(overlay);over.interpolation='Closest'
        mix=m.node_tree.nodes.new('ShaderNodeMixRGB');mix.blend_type='MIX'
        m.node_tree.links.new(over.outputs['Alpha'],mix.inputs[0])
        m.node_tree.links.new(multiply.outputs[0],mix.inputs[1])
        m.node_tree.links.new(over.outputs['Color'],mix.inputs[2])
        alpha=m.node_tree.nodes.new('ShaderNodeMath');alpha.operation='MAXIMUM'
        m.node_tree.links.new(tex.outputs['Alpha'],alpha.inputs[0]);m.node_tree.links.new(over.outputs['Alpha'],alpha.inputs[1])
        m.node_tree.links.new(mix.outputs[0],bsdf.inputs['Base Color'])
        m.node_tree.links.new(mix.outputs[0],bsdf.inputs['Emission Color'])
        m.node_tree.links.new(alpha.outputs[0],bsdf.inputs['Alpha'])
    bsdf.inputs['Emission Strength'].default_value=.12
    bsdf.inputs['Roughness'].default_value=.82
    bsdf.inputs['Specular IOR Level'].default_value=.18
    if hasattr(m,'surface_render_method'):m.surface_render_method='DITHERED'
    materials[key]=m;return m

faces={'down':(5,4,0,1),'up':(2,3,7,6),'west':(0,4,7,3),
       'north':(1,0,3,2),'east':(5,1,2,6),'south':(4,5,6,7)}
def cube(scene,name,bone,parent,xyz,size,mat,inflate=0,uv=(0,0),mirror=False,patch=None,rotation=None,pivot=None):
    x,y,z=xyz;dx,dy,dz=size
    xa,xb=x-inflate,x+dx+inflate;ya,yb=y-inflate,y+dy+inflate;za,zb=z-inflate,z+dz+inflate
    if mirror:xa,xb=xb,xa
    raw=[(xa,ya,za),(xb,ya,za),(xb,yb,za),(xa,yb,za),
         (xa,ya,zb),(xb,ya,zb),(xb,yb,zb),(xa,yb,zb)]
    vertices=[]
    for p in raw:
        v=Vector(p)
        if rotation:
            pivotv=Vector(pivot);v=Euler(tuple(math.radians(a) for a in rotation),'XYZ').to_matrix()@(v-pivotv)+pivotv
        vertices.append((v.x,v.z,-v.y))
    u,v=uv;f4=u;f5=u+dz;f6=f5+dx;f7=f6+dx;f8=f6+dz;f9=f8+dx
    f10=v;f11=v+dz;f12=f11+dy
    rects={'down':(f5,f10,f6,f11),'up':(f6,f11,f7,f10),'west':(f4,f11,f5,f12),
           'north':(f5,f11,f6,f12),'east':(f6,f11,f8,f12),'south':(f8,f11,f9,f12)}
    polygons=[];uvs=[]
    for side,indices in faces.items():
        a,b,c,d=patch or rects[side]
        coordinates=[(c,b),(a,b),(a,d),(c,d)]
        indices=list(indices)
        if mirror:indices.reverse();coordinates.reverse()
        polygons.append(indices);uvs.append(coordinates)
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vertices,[],polygons);mesh.update()
    layer=mesh.uv_layers.new(name='Minecraft UV')
    tex=next(n for n in mat.node_tree.nodes if n.type=='TEX_IMAGE').image
    for poly,coords in zip(mesh.polygons,uvs):
        for loop,point in zip(poly.loop_indices,coords):layer.data[loop].uv=(point[0]/tex.size[0],1-point[1]/tex.size[1])
    obj=bpy.data.objects.new(name,mesh);scene.collection.objects.link(obj);obj.parent=parent
    mesh.materials.append(mat);obj['bone']=bone;return obj

skin_boxes=[('head',[-4,-8,-4],[8,8,8],(0,0)),('body',[-4,0,-2],[8,12,4],(16,16)),
            ('right_arm',[-3,-2,-2],[4,12,4],(40,16)),('left_arm',[-1,-2,-2],[4,12,4],(32,48)),
            ('right_leg',[-2,0,-2],[4,12,4],(0,16)),('left_leg',[-2,0,-2],[4,12,4],(16,48))]
def actor(scene,tier,proposed):
    rig={}
    for bone,position in design['bones'].items():
        empty=bpy.data.objects.new(tier+' / '+bone,None);scene.collection.objects.link(empty)
        empty.location=(position[0],position[2],24-position[1]);rig[bone]=empty
    skin=material('Steve reference','entity/player/wide/steve.png')
    for bone,xyz,size,uv in skin_boxes:cube(scene,'mannequin / '+bone,bone,rig[bone],xyz,size,skin,uv=uv)
    armor=[]
    if proposed:
        palette=design['sets'][tier]['palette']
        for c in design['sets'][tier]['cubes']:
            role=c['role']
            path='models/armor/leather_layer_1.png' if role=='strap' else 'models/armor/iron_layer_1.png'
            mat=material(tier+' / '+role,path,palette[role])
            obj=cube(scene,c['name'],c['bone'],rig[c['bone']],c['from'],c['size'],mat,
                     patch=(8,0,16,8),rotation=c.get('rotation'),pivot=c.get('rotation_pivot'))
            obj['equipment_slot']=c['slot'];obj['role']=role;armor.append(obj)
    else:
        tint=design['baseline_tints'][tier]
        for slot,layer,bone_names,deformation in [('head',1,['head'],1),
            ('chest',1,['body','right_arm','left_arm'],1),('legs',2,['body','right_leg','left_leg'],.5),
            ('feet',1,['right_leg','left_leg'],1)]:
            path=f'models/armor/leather_layer_{layer}.png'
            mat=material('Original '+tier,path,tint,overlay=f'models/armor/leather_layer_{layer}_overlay.png')
            for bone in bone_names:
                _,xyz,size,uv=next(s for s in skin_boxes if s[0]==bone)
                if bone=='left_arm':uv=(40,16)
                if bone=='left_leg':uv=(0,16)
                obj=cube(scene,'original / '+slot+' / '+bone,bone,rig[bone],xyz,size,mat,
                         inflate=deformation-(.1 if bone.endswith('leg') else 0),uv=uv,
                         mirror=bone.startswith('left'))
                armor.append(obj)
    # Same gentle idle arm spread for both before/after, anchored to native pivots.
    for bone,obj in rig.items():
        for frame in (1,25):
            obj.rotation_euler=(0,.055 if bone=='right_arm' else -.055 if bone=='left_arm' else 0,0)
            obj.keyframe_insert('rotation_euler',frame=frame)
        obj.rotation_euler.x=(.48 if bone in ('right_arm','left_leg') else -.48 if bone in ('left_arm','right_leg') else 0)
        if bone=='head':obj.rotation_euler.z=-.18
        obj.keyframe_insert('rotation_euler',frame=13)
    return rig,armor

def setup_scene(name):
    scene=bpy.data.scenes.new(name);bpy.context.window.scene=scene
    scene.unit_settings.system='NONE';scene['units']='Minecraft model units, 16 per block'
    scene['status']='Cosmetic proposal only; not installed or verified in Minecraft'
    scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
    scene.render.resolution_x=900;scene.render.resolution_y=1100;scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
    scene.frame_start=1;scene.frame_end=25;scene.frame_set(1)
    world=bpy.data.worlds.new(name+' world');scene.world=world;world.use_nodes=True
    bg=next(n for n in world.node_tree.nodes if n.type=='BACKGROUND')
    bg.inputs['Color'].default_value=(.10,.135,.15,1);bg.inputs['Strength'].default_value=.7
    for label,loc,energy,diam in [('key',(23,-32,48),18000,25),('fill',(-28,-18,30),10000,24),('rim',(8,23,40),22000,20)]:
        data=bpy.data.lights.new(label,'AREA');data.energy=energy;data.shape='DISK';data.size=diam
        obj=bpy.data.objects.new(label,data);scene.collection.objects.link(obj);obj.location=loc
        obj.rotation_euler=(Vector((0,0,15))-obj.location).to_track_quat('-Z','Y').to_euler()
    def camera(label,location,scale):
        data=bpy.data.cameras.new(label);obj=bpy.data.objects.new(label,data);scene.collection.objects.link(obj)
        obj.location=location;obj.rotation_euler=(Vector((0,0,16))-obj.location).to_track_quat('-Z','Y').to_euler()
        data.type='ORTHO';data.ortho_scale=scale;return obj
    hero=camera('Three quarter',(40,-74,33),42)
    front=camera('Front',(0,-85,18),41)
    back=camera('Back',(-38,74,31),42)
    scene.camera=hero
    return scene,hero,front,back

results={};save_scene=None
for tier in ('riftsilver','riftsteel'):
    for proposed in (False,True):
        label=tier+('_proposed' if proposed else '_original')
        scene,hero,front,back=setup_scene('Mezhdumorye armor / '+label)
        rig,armor=actor(scene,tier,proposed);scene.frame_set(1)
        if proposed:
            bpy.ops.object.select_all(action='DESELECT')
            for obj in list(rig.values())+armor:obj.select_set(True)
            bpy.context.view_layer.objects.active=armor[0]
            kwargs={'filepath':str(ROOT/'exports'/f'{tier}_armor_A01.glb'),'export_format':'GLB',
                    'use_selection':True,'export_animations':True}
            if 'use_active_scene' in bpy.ops.export_scene.gltf.get_rna_type().properties:kwargs['use_active_scene']=True
            bpy.ops.export_scene.gltf(**kwargs)
        scene.render.filepath=str(ROOT/'preview'/f'{label}.png');bpy.ops.render.render(write_still=True)
        if proposed:
            scene.camera=front;scene.render.filepath=str(ROOT/'preview'/f'{tier}_front.png');bpy.ops.render.render(write_still=True)
            if tier=='riftsteel':
                scene.camera=back;scene.render.filepath=str(ROOT/'preview/steel_back.png');bpy.ops.render.render(write_still=True)
                scene.camera=hero;scene.frame_set(13);scene.render.filepath=str(ROOT/'preview/steel_walking.png');bpy.ops.render.render(write_still=True)
                scene.frame_set(1);save_scene=scene
            scene.camera=hero
        results[label]={'objects':len(armor),'armor_quads':sum(len(o.data.polygons) for o in armor),'preview':str(scene.render.filepath)}
bpy.context.window.scene=save_scene
for area in bpy.context.screen.areas:
    if area.type=='VIEW_3D':
        area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.shading.type='MATERIAL'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/mezhdumorye_armor_A01.blend'))
report={'revision':design['revision'],'geometry_source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
        'sets':results,'native_game_verified':False,'outputs':{}}
for p in [ROOT/'assembly/mezhdumorye_armor_A01.blend',*sorted((ROOT/'exports').glob('*.glb')),*sorted((ROOT/'preview').glob('*.png'))]:
    report['outputs'][str(p.relative_to(ROOT))]=hashlib.sha256(p.read_bytes()).hexdigest()
(ROOT/'reports/blender.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('ARMOR_PREVIEW_COMPLETE '+json.dumps(results))
