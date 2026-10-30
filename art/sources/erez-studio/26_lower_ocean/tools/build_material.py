"""World-coordinate ocean material study. No game files or image pixels are edited."""
from pathlib import Path
import bpy,math,json
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
spec=json.loads((ROOT/'source/material_OC01.json').read_text(encoding='utf8'))
for ob in list(bpy.data.objects):bpy.data.objects.remove(ob,do_unlink=True)
scene=bpy.context.scene;scene.name='OC01 / continuous toxic sea material study';scene.render.engine='CYCLES'
scene.cycles.samples=16;scene.cycles.use_denoising=True;scene.render.fps=24;scene.frame_start=1;scene.frame_end=385
scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='AgX'
scene.world.use_nodes=True;bg=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND');bg.inputs['Color'].default_value=(.18,.19,.23,1);bg.inputs['Strength'].default_value=.4
mat=bpy.data.materials.new('OC01 / shared world-space currents');mat.use_nodes=True;nodes=mat.node_tree.nodes;links=mat.node_tree.links;nodes.clear()
def value(name,v):
    n=nodes.new('ShaderNodeValue');n.name=name;n.label=name;n.outputs[0].default_value=v;return n.outputs[0]
def input_to(socket,source):
    if isinstance(source,(int,float)):socket.default_value=source
    else:links.new(source,socket)
def op(kind,a,b=0):
    n=nodes.new('ShaderNodeMath');n.operation=kind;input_to(n.inputs[0],a);input_to(n.inputs[1],b);return n.outputs[0]
def total(values):
    result=values[0]
    for v in values[1:]:result=op('ADD',result,v)
    return result
def ramp(source,stops,name):
    n=nodes.new('ShaderNodeValToRGB');n.name=name;n.label=name
    while len(n.color_ramp.elements)>2:n.color_ramp.elements.remove(n.color_ramp.elements[-1])
    n.color_ramp.interpolation='EASE'
    for i,(position,color) in enumerate(stops):
        e=n.color_ramp.elements[i] if i<2 else n.color_ramp.elements.new(position);e.position=position;e.color=(*color,1)
    links.new(source,n.inputs['Fac']);return n.outputs['Color']
geometry=nodes.new('ShaderNodeNewGeometry');geometry.label='WORLD position shared by all patches'
xyz=nodes.new('ShaderNodeSeparateXYZ');links.new(geometry.outputs['Position'],xyz.inputs[0])
x=xyz.outputs['X'];y=xyz.outputs['Y'];time=value('OC01 / time seconds',0)
time.node.outputs[0].default_value=0;time.node.outputs[0].keyframe_insert('default_value',frame=1)
time.node.outputs[0].default_value=16;time.node.outputs[0].keyframe_insert('default_value',frame=385)
action=mat.node_tree.animation_data.action
for slot in action.slots:
    for layer in action.layers:
        for strip in layer.strips:
            try:
                for fc in strip.channelbag(slot).fcurves:
                    for key in fc.keyframe_points:key.interpolation='LINEAR'
            except (AttributeError,TypeError):pass
def phase(w):
    return total([op('MULTIPLY',x,2*math.pi*w['kx']/64),op('MULTIPLY',y,2*math.pi*w['ky']/64),op('MULTIPLY',time,2*math.pi*w['kt']/16),w['phase']])
def wave(w):return op('MULTIPLY',op('SINE',phase(w)),w['weight'])
warp=total([wave(w) for w in spec['domain_warp']]);broad=total([wave(w) for w in spec['broad_currents']])
ribbon=op('SINE',op('ADD',phase(spec['ribbon']),warp));fine=total([wave(w) for w in spec['fine_ripples']])
field=total([.47,broad,op('MULTIPLY',ribbon,spec['ribbon']['weight']),fine])
noise_spec=spec['periodic_noise'];radius=noise_spec['torus_radius']
ax=op('ADD',phase(dict(kx=1,ky=0,kt=-1,phase=0)),op('MULTIPLY',warp,.22))
ay=op('ADD',phase(dict(kx=0,ky=1,kt=0,phase=0)),op('MULTIPLY',warp,.18))
torus=nodes.new('ShaderNodeCombineXYZ');torus.label='4D torus / seamless in space and time'
for socket,source in zip(torus.inputs,[op('MULTIPLY',op('COSINE',ax),radius),op('MULTIPLY',op('SINE',ax),radius),op('MULTIPLY',op('COSINE',ay),radius)]):links.new(source,socket)
torus_w=op('MULTIPLY',op('SINE',ay),radius)
noise=nodes.new('ShaderNodeTexNoise');noise.noise_dimensions='4D';noise.label='Irregular broad liquid currents'
links.new(torus.outputs[0],noise.inputs['Vector']);links.new(torus_w,noise.inputs['W'])
noise.inputs['Scale'].default_value=noise_spec['base_scale'];noise.inputs['Detail'].default_value=noise_spec['base_detail'];noise.inputs['Roughness'].default_value=noise_spec['base_roughness'];noise.inputs['Distortion'].default_value=noise_spec['base_distortion']
fine_noise=nodes.new('ShaderNodeTexNoise');fine_noise.noise_dimensions='4D';fine_noise.label='Fine continuous surface ripples'
links.new(torus.outputs[0],fine_noise.inputs['Vector']);links.new(torus_w,fine_noise.inputs['W']);fine_noise.inputs['Scale'].default_value=noise_spec['fine_scale'];fine_noise.inputs['Detail'].default_value=noise_spec['fine_detail']
flow=total([op('MULTIPLY',noise.outputs['Fac'],.87),op('MULTIPLY',field,.13)])
palette=spec['base_palette_linear_rgb'];base=ramp(flow,[(.22,palette[0]),(.44,palette[1]),(.56,palette[2]),(.77,palette[3])],'Wine-black / burgundy base')
film_field=op('SINE',op('ADD',op('MULTIPLY',noise.outputs['Fac'],23),op('MULTIPLY',broad,3)))
film_mask=ramp(op('MULTIPLY',op('ADD',film_field,1),.5),[(.77,(0,0,0)),(.98,(1,1,1))],'Fine irregular film ribbons')
film_hue=op('MULTIPLY',op('ADD',op('SINE',phase(dict(kx=1,ky=1,kt=0,phase=0))),1),.5)
film_color=ramp(film_hue,[(.15,spec['film_copper_linear_rgb']),(.85,spec['film_violet_linear_rgb'])],'Copper / plum film')
mix=nodes.new('ShaderNodeMixRGB');mix.blend_type='MIX';links.new(base,mix.inputs[1]);links.new(film_color,mix.inputs[2]);links.new(op('MULTIPLY',film_mask,spec['film_max_mix']),mix.inputs[0])
shader=nodes.new('ShaderNodeBsdfPrincipled');links.new(mix.outputs[0],shader.inputs['Base Color']);shader.inputs['Roughness'].default_value=.26;shader.inputs['Specular IOR Level'].default_value=.42
bump=nodes.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.32;bump.inputs['Distance'].default_value=.085
links.new(total([op('MULTIPLY',noise.outputs['Fac'],.14),op('MULTIPLY',fine_noise.outputs['Fac'],.09),op('MULTIPLY',fine,.12)]),bump.inputs['Height']);links.new(bump.outputs['Normal'],shader.inputs['Normal'])
emission=nodes.new('ShaderNodeEmission');links.new(mix.outputs[0],emission.inputs['Color']);emission.inputs['Strength'].default_value=1
output=nodes.new('ShaderNodeOutputMaterial');links.new(shader.outputs[0],output.inputs['Surface'])
for i,n in enumerate(nodes):n.location=((i%13)*210,-(i//13)*180)
patches=[]
def plane(name,cx,cy,size):
    h=size/2;mesh=bpy.data.meshes.new(name);mesh.from_pydata([(-h,-h,0),(h,-h,0),(h,h,0),(-h,h,0)],[],[(0,1,2,3)]);mesh.materials.append(mat)
    ob=bpy.data.objects.new(name,mesh);scene.collection.objects.link(ob);ob.location=(cx,cy,0);return ob
for iy in range(8):
    for ix in range(8):
        ob=plane(f'OC01_patch_{ix}_{iy}',-56+ix*16,-56+iy*16,16);ob['world_origin']=[-64+ix*16,-64+iy*16];patches.append(ob)
single=plane('OC01_reference_single_plane',0,0,128);single.hide_render=True;single.hide_viewport=True
camera_data=bpy.data.cameras.new('OC01 camera');camera=bpy.data.objects.new('OC01 camera',camera_data);scene.collection.objects.link(camera);scene.camera=camera;camera_data.type='ORTHO'
def aim(location,target,scale):
    camera.location=location;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();camera_data.ortho_scale=scale
for name,location,energy,size in [('Key',(-18,-23,25),21000,24),('Fill',(30,12,30),12000,30)]:
    light=bpy.data.lights.new(name,'AREA');light.energy=energy;light.size=size
    ob=bpy.data.objects.new(name,light);scene.collection.objects.link(ob);ob.location=location;ob.rotation_euler=(Vector((0,0,0))-ob.location).to_track_quat('-Z','Y').to_euler()
scene.render.resolution_x=1500;scene.render.resolution_y=900;aim((25,-35,28),(0,5,0),67);scene.frame_set(1)
scene['status']='Material continuity study. Not a Minecraft screenshot or DH test.'
scene['coordinates']='Shared world positions across 64 independent 16-block patches'
scene['source']='source/material_OC01.json';scene['loop_seconds']=16
bpy.context.preferences.filepaths.save_version=0
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':
            area.spaces.active.region_3d.view_location=(0,0,0);area.spaces.active.region_3d.view_distance=70;area.spaces.active.shading.type='MATERIAL'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/lower_ocean_OC01.blend'))
scene.render.filepath=str(ROOT/'preview/OC01_native_material.png');bpy.ops.render.render(write_still=True)

# Unlit orthographic control: the only variable is how the same plane is split.
links.new(emission.outputs[0],output.inputs['Surface']);scene.view_settings.view_transform='Standard'
scene.cycles.samples=1;scene.cycles.use_denoising=False;scene.render.resolution_x=1024;scene.render.resolution_y=1024
aim((0,0,50),(0,0,0),64);scene.frame_set(1)
scene.render.filepath=str(ROOT/'preview/OC01_patch_control.png');bpy.ops.render.render(write_still=True)
for ob in patches:ob.hide_render=True
single.hide_render=False
scene.render.filepath=str(ROOT/'preview/OC01_single_control.png');bpy.ops.render.render(write_still=True)
scene.frame_set(385);scene.render.filepath=str(ROOT/'preview/OC01_loop_end_control.png');bpy.ops.render.render(write_still=True);scene.frame_set(1)
single.hide_render=True
for ob in patches:ob.hide_render=False
scene.render.resolution_x=640;scene.render.resolution_y=640
for i in range(16):
    scene.frame_set(1+i*24);scene.render.filepath=str(ROOT/f'preview/loop_{i:02d}.png');bpy.ops.render.render(write_still=True)
report={'revision':'OC01','independent_patches':64,'patch_size_blocks':16,'spatial_period_blocks':64,'loop_seconds':16,
    'coordinates_source':'ShaderNodeNewGeometry.Position','shader_nodes':len(nodes),'mathematical_source':'source/material_OC01.json',
    'native_material_saved':True,'minecraft_or_DH_verified':False,'game_files_modified':False}
(ROOT/'reports/NATIVE_BUILD.json').write_text(json.dumps(report,indent=2)+'\n');print('OC01_MATERIAL_COMPLETE',json.dumps(report))
