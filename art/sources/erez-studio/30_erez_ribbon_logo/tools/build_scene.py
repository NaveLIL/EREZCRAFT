from pathlib import Path
import bpy,bmesh,json,math,argparse,sys
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--resolution',type=int,default=1500);parser.add_argument('--views',default='front,quarter,detail,transparent');args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
data=json.loads((ROOT/'source/ribbon_LG01.json').read_text())
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
scene=bpy.context.scene;scene.name='EREZ / LG01 / folded ribbon emblem'
scene.render.engine='CYCLES';scene.cycles.samples=32;scene.cycles.use_denoising=True
scene.render.resolution_x=args.resolution;scene.render.resolution_y=args.resolution;scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA'
scene.view_settings.view_transform='AgX';scene.world.use_nodes=True
bg=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND');bg.inputs['Color'].default_value=(.1,.1,.13,1);bg.inputs['Strength'].default_value=.36
vehicle=bpy.data.collections.new('EREZ / export geometry');scene.collection.children.link(vehicle)
root=bpy.data.objects.new('EREZ_RIBBON_LG01',None);vehicle.objects.link(root);root['authoritative_source']='source/ribbon_LG01.json';root['minecraft_registered']=False
def linear(c):return c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4
materials={}
for name,color in data['palette'].items():
    rgb=tuple(linear(int(color[i:i+2],16)/255) for i in (1,3,5))
    mat=bpy.data.materials.new('EREZ / '+name);mat.use_nodes=True;mat.diffuse_color=(*rgb,1)
    shader=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED');shader.inputs['Base Color'].default_value=(*rgb,1)
    shader.inputs['Metallic'].default_value=.16 if name.startswith('ribbon') else .12;shader.inputs['Roughness'].default_value=.4 if name.startswith('ribbon') else .66
    materials[name]=mat
def mesh_object(name,vertices,faces,keys,indices=None):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vertices,[],faces);mesh.update()
    bm=bmesh.new();bm.from_mesh(mesh);bmesh.ops.recalc_face_normals(bm,faces=bm.faces);bm.to_mesh(mesh);bm.free()
    ob=bpy.data.objects.new(name,mesh);vehicle.objects.link(ob);ob.parent=root
    for key in keys:mesh.materials.append(materials[key])
    if indices:
        for polygon,i in zip(mesh.polygons,indices):polygon.material_index=i
    return ob
knots=[]
for px,py,y,twist in data['knots_px_depth_twist']:
    knots.append((Vector(((px-640)/120,y,(640-py)/120)),math.radians(twist)))
def catmull(p0,p1,p2,p3,u):
    # Centripetal parameterization avoids kinks beside the long straight top arm.
    t0=0;t1=t0+max((p1-p0).length,.0001)**.5;t2=t1+max((p2-p1).length,.0001)**.5;t3=t2+max((p3-p2).length,.0001)**.5;t=t1+(t2-t1)*u
    a1=p0*((t1-t)/(t1-t0))+p1*((t-t0)/(t1-t0))
    a2=p1*((t2-t)/(t2-t1))+p2*((t-t1)/(t2-t1))
    a3=p2*((t3-t)/(t3-t2))+p3*((t-t2)/(t3-t2))
    b1=a1*((t2-t)/(t2-t0))+a2*((t-t0)/(t2-t0))
    b2=a2*((t3-t)/(t3-t1))+a3*((t-t1)/(t3-t1))
    return b1*((t2-t)/(t2-t1))+b2*((t-t1)/(t2-t1))
samples=[];angles=[]
for i in range(len(knots)-1):
    p1=knots[i][0];p2=knots[i+1][0];p0=knots[i-1][0] if i else p1*2-p2;p3=knots[i+2][0] if i+2<len(knots) else p2*2-p1
    for j in range(data['samples_per_span']):
        t=j/data['samples_per_span'];samples.append(catmull(p0,p1,p2,p3,t));angles.append(knots[i][1]*(1-t)+knots[i+1][1]*t)
samples.append(knots[-1][0]);angles.append(knots[-1][1])
N=len(samples);W=data['width_segments']+1;verts=[]
for side in [0,1]:
    for i,c in enumerate(samples):
        tangent=(samples[min(N-1,i+1)]-samples[max(0,i-1)]).normalized()
        across=Vector((-tangent.z,0,tangent.x)).normalized()
        across=across*math.cos(angles[i])+tangent.cross(across)*math.sin(angles[i])
        normal=tangent.cross(across).normalized()
        if normal.y>0:normal=-normal
        end_factor=min(1,.6+min(i,N-1-i)/5*.4)
        for j in range(W):
            u=2*j/(W-1)-1;offset=(1 if side==0 else -1)*data['ribbon_thickness']/2
            offset+=data['ribbon_crown']*(1-u*u)
            verts.append(tuple(c+across*(u*data['ribbon_width']*.5*end_factor)+normal*offset))
faces=[];indices=[]
for side in [0,1]:
    off=side*N*W
    for i in range(N-1):
        for j in range(W-1):
            faces.append((off+i*W+j,off+(i+1)*W+j,off+(i+1)*W+j+1,off+i*W+j+1));indices.append(side)
for i in range(N-1):
    for j in [0,W-1]:faces.append((i*W+j,(i+1)*W+j,N*W+(i+1)*W+j,N*W+i*W+j));indices.append(2)
for i in [0,N-1]:
    for j in range(W-1):faces.append((i*W+j,i*W+j+1,N*W+i*W+j+1,N*W+i*W+j));indices.append(2)
ribbon=mesh_object('Ribbon / real folded solid',verts,faces,['ribbon_front','ribbon_back','ribbon_edge'],indices)
for p in ribbon.data.polygons:p.use_smooth=True
# Keep sharp tape edges while smoothing only the lengthwise surface normals.
normals={}
for polygon in ribbon.data.polygons:
    for vi in polygon.vertices:
        key=(vi,polygon.material_index);normals[key]=normals.get(key,Vector())+polygon.normal
ribbon.data.normals_split_custom_set([normals[(loop.vertex_index,p.material_index)].normalized() for p in ribbon.data.polygons for loop in [ribbon.data.loops[i] for i in p.loop_indices]])
# Color is authored on vertices, not painted into a bitmap: rear runs are darker.
colors=ribbon.data.color_attributes.new(name='RibbonDepthColor',type='FLOAT_COLOR',domain='CORNER')
front_color=materials['ribbon_front'].diffuse_color[:3];back_color=materials['ribbon_back'].diffuse_color[:3];edge_color=materials['ribbon_edge'].diffuse_color[:3]
for polygon in ribbon.data.polygons:
    for li in polygon.loop_indices:
        vi=ribbon.data.loops[li].vertex_index;sample_i=(vi%(N*W))//W
        weight=max(0,min(1,(samples[sample_i].y+.42)/.54))
        rgb=tuple(front_color[k]*(1-weight)+back_color[k]*weight for k in range(3)) if polygon.material_index!=2 else edge_color
        colors.data[li].color=(*rgb,1)
for key in ['ribbon_front','ribbon_back','ribbon_edge']:
    mat=materials[key];shader=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED');vertex=mat.node_tree.nodes.new('ShaderNodeVertexColor');vertex.layer_name='RibbonDepthColor';mat.node_tree.links.new(vertex.outputs['Color'],shader.inputs['Base Color']);shader.inputs['Roughness'].default_value=.53;shader.inputs['Metallic'].default_value=.04
# Disk is built explicitly in the X/Z plane, facing -Y.
def disk(name,r,front,depth,material,segments=128):
    v=[]
    for y in [front,front+depth]:
        for i in range(segments):a=math.tau*i/segments;v.append((r*math.cos(a),y,r*math.sin(a)))
    f=[tuple(range(segments-1,-1,-1)),tuple(range(segments,segments*2))]
    f += [(i,(i+1)%segments,(i+1)%segments+segments,i+segments) for i in range(segments)]
    ob=mesh_object(name,v,f,[material]);mod=ob.modifiers.new('Small edge chamfer','BEVEL');mod.width=.045;mod.segments=2
    mod=ob.modifiers.new('Weighted face normals','WEIGHTED_NORMAL');mod.keep_sharp=True
    return ob
back=disk('Dark circular base',data['disc_radius'],data['disc_front_y'],data['disc_depth'],'disc')
rim=disk('Thin outer rim',data['disc_radius']+.028,data['disc_front_y']+.055,data['disc_depth']-.05,'rim')
# Export before adding studio support. The 2x2 GLB contains the same geometry.
bpy.ops.object.select_all(action='DESELECT')
for ob in vehicle.objects:ob.select_set(True)
bpy.context.view_layer.objects.active=root
bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/erez_ribbon_LG01.glb'),export_format='GLB',use_selection=True,export_animations=False)
scale=2/(2*(data['disc_radius']+.028));root.scale=(scale,)*3
bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/erez_ribbon_LG01_wall_2x2.glb'),export_format='GLB',use_selection=True,export_animations=False)
root.scale=(1,1,1)
stage=bpy.data.collections.new('STUDIO / excluded from exports');scene.collection.children.link(stage)
for name,pos,power,size in [('Key',(-7,-11,11),1900,8),('Fill',(8,-8,3),1100,7),('Edge',(1,-1,-9),650,5)]:
    light=bpy.data.lights.new(name,'AREA');light.energy=power;light.shape='DISK';light.size=size;ob=bpy.data.objects.new(name,light);stage.objects.link(ob);ob.location=pos;ob.rotation_euler=(Vector((0,0,0))-ob.location).to_track_quat('-Z','Y').to_euler()
cam=bpy.data.cameras.new('Emblem review camera');cam.type='ORTHO';camera=bpy.data.objects.new('Emblem review camera',cam);stage.objects.link(camera);scene.camera=camera
def aim(loc,target,scale):camera.location=loc;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();cam.ortho_scale=scale
aim((0,-24,0),(0,0,0),11.25)
scene['geometry_authority']='source/ribbon_LG01.json';scene['status']='Native mesh for Minecraft integration; not registered as a block or item.'
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.shading.type='MATERIAL'
bpy.context.preferences.filepaths.save_version=0;bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/erez_ribbon_LG01.blend'))
for view in args.views.split(','):
    scene.render.film_transparent=False;back.hide_render=False;rim.hide_render=False
    if view=='front':aim((0,-24,0),(0,0,0),11.25)
    elif view=='quarter':aim((10,-24,6),(0,0,0),12.0)
    elif view=='detail':aim((6,-20,3),(0,-.2,.7),8.7)
    elif view=='transparent':
        scene.render.film_transparent=True;back.hide_render=True;rim.hide_render=True;aim((0,-24,0),(0,0,0),9.0)
    else:raise ValueError(view)
    scene.render.filepath=str(ROOT/f'preview/erez_ribbon_LG01_{view}.png');bpy.ops.render.render(write_still=True)
report={'revision':'LG01','ribbon_samples':N,'ribbon_vertices':len(verts),'ribbon_faces':len(faces),'native_folded_solid':True,
 'reference':'references/owner_logo.jpg','raster_textures_used':False,'exported_wall_diameter_blocks':2,'game_registered':False,'views':args.views.split(',')}
(ROOT/'reports/BLENDER_BUILD.json').write_text(json.dumps(report,indent=2)+'\n');print('EREZ_LOGO_LG01_COMPLETE',json.dumps(report))
