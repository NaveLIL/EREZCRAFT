from pathlib import Path
import bpy,bmesh,json,math,sys,argparse,hashlib
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--views',default='hero,rear,main_interior,cutaway');parser.add_argument('--resolution',type=int,default=1440)
args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
data=json.loads((ROOT/'source/ne01.json').read_text());P=data['parameters']
bpy.ops.wm.read_factory_settings(use_empty=True)
scene=bpy.context.scene;scene.unit_settings.system='METRIC';scene.unit_settings.scale_length=1
scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True;scene.cycles.max_bounces=10;scene.cycles.transmission_bounces=8
scene.render.threads_mode='FIXED';scene.render.threads=4
scene.render.resolution_x=args.resolution;scene.render.resolution_y=int(args.resolution*.70);scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='AgX'
scene.world=bpy.data.worlds.new('Studio sky');scene.world.use_nodes=True;scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.25,.28,.32,1);scene.world.node_tree.nodes['Background'].inputs[1].default_value=.40
vehicle=bpy.data.collections.new('NE01 / authored vessel');scene.collection.children.link(vehicle)
materials={}
for m in data['materials']:
    mat=bpy.data.materials.new(m['name']);mat.use_nodes=True
    bs=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED');bs.inputs['Roughness'].default_value=m['roughness'];bs.inputs['Metallic'].default_value=m['metallic']
    image=bpy.data.images.load(str(ROOT/m['file']),check_existing=True);image.pack()
    tex=mat.node_tree.nodes.new('ShaderNodeTexImage');tex.image=image;tex.interpolation='Closest';tex.extension='REPEAT';mat.node_tree.links.new(tex.outputs['Color'],bs.inputs['Base Color'])
    if m['name']=='glass':bs.inputs['Transmission Weight'].default_value=.94;bs.inputs['IOR'].default_value=1.45;bs.inputs['Alpha'].default_value=1
    if m['name'] in ['lamp','port_lamp','starboard_lamp']:
        mat.node_tree.links.new(tex.outputs['Color'],bs.inputs['Emission Color']);bs.inputs['Emission Strength'].default_value=1.1
    if m['name'] in ['display','me_display']:
        mat.node_tree.links.new(tex.outputs['Color'],bs.inputs['Emission Color']);bs.inputs['Emission Strength'].default_value=.35
    materials[m['name']]=mat
groups={};pivots={}
for g in data['groups']:
    ob=bpy.data.objects.new(g['id'],None);vehicle.objects.link(ob);pivots[g['id']]=Vector(g['pivot']);groups[g['id']]=ob
    if g['parent']:ob.parent=groups[g['parent']];ob.location=pivots[g['id']]-pivots[g['parent']]
    else:ob.location=pivots[g['id']]
def cube(s):
    v=[(x*s[0]/2,y*s[1]/2,z*s[2]/2) for x,y,z in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
    return v,[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)]
def cylinder(r,d,axis):
    v=[];n=20
    for zz in [-d/2,d/2]:
        for i in range(n):
            a=math.tau*i/n;x,y=r*math.cos(a),r*math.sin(a)
            v.append((zz,x,y) if axis=='X' else (x,zz,y) if axis=='Y' else (x,y,zz))
    return v,[list(range(n-1,-1,-1)),list(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
def make(n,v,f,g,mat,center=(0,0,0),rotation=(0,0,0)):
    me=bpy.data.meshes.new(n);me.from_pydata(v,[],f);me.update();bm=bmesh.new();bm.from_mesh(me);bmesh.ops.remove_doubles(bm,verts=bm.verts,dist=1e-8);bmesh.ops.dissolve_degenerate(bm,edges=bm.edges,dist=1e-9);bmesh.ops.recalc_face_normals(bm,faces=bm.faces);bm.to_mesh(me);bm.free()
    ob=bpy.data.objects.new(n,me);vehicle.objects.link(ob);ob.parent=groups[g];ob.location=Vector(center)-pivots[g];ob.rotation_euler=rotation
    me.materials.append(materials[mat]);return ob
objects=[]
for p in data['parts']:
    c=p.get('center',[0,0,0]);rot=[math.radians(a) for a in p.get('rotation',[0,0,0])]
    if p['shape']=='box':v,f=cube(p['size'])
    elif p['shape']=='mesh':v,f=p['vertices'],p['faces']
    elif p['shape']=='cylinder':v,f=cylinder(p['radius'],p['depth'],p['axis'])
    elif p['shape']=='beam':
        a,b=Vector(p['a']),Vector(p['b']);c=(a+b)/2;v,f=cube([p['width'],p['width'],(b-a).length]);rot=(b-a).to_track_quat('Z','Y').to_euler()
    else:raise ValueError(p['shape'])
    ob=make(p['name'],v,f,p['group'],p['material'],c,rot);ob['source_part']=p['name'];ob['source_part_id']=p['id'];ob['component']=p['group'];ob['material_id']=p['material'];objects.append(ob)
    bpy.context.view_layer.update()
    for cut in p.get('cutouts',[]):
        v,f=cube(cut['size']);cutter=make('CUTTER',v,f,'ROOT',p['material'],cut['center']);mod=ob.modifiers.new('True opening','BOOLEAN');mod.operation='DIFFERENCE';mod.solver='EXACT';mod.object=cutter
        bpy.context.view_layer.objects.active=ob;bpy.ops.object.modifier_apply(modifier=mod.name);bpy.data.objects.remove(cutter,do_unlink=True)
    if p.get('bevel'):
        mod=ob.modifiers.new('Edge detail away from mating skin','BEVEL');mod.width=p['bevel'];mod.segments=2
        bpy.context.view_layer.objects.active=ob;bpy.ops.object.modifier_apply(modifier=mod.name)
    if p.get('shear_yz'):
        factor,reference=p['shear_yz'];bpy.context.view_layer.update();inv=ob.matrix_world.inverted()
        for vertex in ob.data.vertices:
            point=ob.matrix_world@vertex.co;point.y+=factor*(point.z-reference);vertex.co=inv@point
    if p.get('shear_xy') or p.get('shear_xz'):
        fy,ry=p.get('shear_xy',[0,0]);fz,rz=p.get('shear_xz',[0,0]);bpy.context.view_layer.update();inv=ob.matrix_world.inverted()
        for vertex in ob.data.vertices:
            point=ob.matrix_world@vertex.co;point.x+=fy*(point.y-ry)+fz*(point.z-rz);vertex.co=inv@point
    # Continuous, piecewise-affine stem rake. Subdivide every intersecting solid
    # on the SAME branch planes before deformation, preserving matching seams.
    if not p['name'].startswith('FORE_LOWER'):
        bpy.context.view_layer.update();matrix=ob.matrix_world.copy();inv=matrix.inverted()
        if any((matrix@v.co).y>P['bow_rake_start_y_m'] and (matrix@v.co).z<P['bow_rake_top_z_m'] for v in ob.data.vertices):
            bm=bmesh.new();bm.from_mesh(ob.data)
            for v in bm.verts:v.co=matrix@v.co
            y0=P['bow_rake_start_y_m'];z0=P['bow_rake_top_z_m'];a=P['bow_rake_dy_per_down_m'];b=P['bow_rake_transition']
            for normal in [(0,1,0),(0,0,1),(0,b,a)]:
                bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),dist=1e-7,plane_co=(0,y0,z0),plane_no=normal,clear_inner=False,clear_outer=False)
            for v in bm.verts:
                v.co.y-=min(max(0,(z0-v.co.z)*a),max(0,(v.co.y-y0)*b));v.co=inv@v.co
            bm.to_mesh(ob.data);bm.free();ob.data.update()
    # Stairwell remains aligned to the rigid stair after the outer deck rake.
    for cut in p.get('post_cutouts',[]):
        v,f=cube(cut['size']);cutter=make('CUTTER',v,f,'ROOT',p['material'],cut['center']);mod=ob.modifiers.new('Post-rake true opening','BOOLEAN');mod.operation='DIFFERENCE';mod.solver='EXACT';mod.object=cutter
        bpy.context.view_layer.objects.active=ob;bpy.ops.object.modifier_apply(modifier=mod.name);bpy.data.objects.remove(cutter,do_unlink=True)
    bm=bmesh.new();bm.from_mesh(ob.data);bmesh.ops.recalc_face_normals(bm,faces=bm.faces);bm.to_mesh(ob.data);bm.free();ob.data.update()
    # All texture coordinates are projected from the SAME closed world frame.
    # Adjacent skin pieces therefore share grain phase, rather than restarting tiles.
    uv=ob.data.uv_layers.new(name='NE01 world material projection');bpy.context.view_layer.update()
    for face in ob.data.polygons:
        normal=ob.matrix_world.to_3x3()@face.normal;axis=max(range(3),key=lambda k:abs(normal[k]));axes=(1,2) if axis==0 else (0,2) if axis==1 else (0,1)
        for li in face.loop_indices:
            pos=ob.matrix_world@ob.data.vertices[ob.data.loops[li].vertex_index].co;uv.data[li].uv=(pos[axes[0]]/4,pos[axes[1]]/4)

# Prescribed articulation: actual native mesh groups, not painted concepts.
for frame,opened in [(1,False),(41,True),(81,False)]:
    for g in data['groups']:
        if 'axis' not in g:continue
        ob=groups[g['id']];axis='XYZ'.index(g['axis']);ob.rotation_euler[axis]=math.radians(g['open_deg']) if opened else 0;ob.keyframe_insert('rotation_euler',frame=frame)
    scene.timeline_markers.new('open' if opened else 'closed',frame=frame)
scene.frame_set(1)
stage=bpy.data.collections.new('STUDIO / excluded from game');scene.collection.children.link(stage)
def move_stage(ob):
    for coll in list(ob.users_collection):coll.objects.unlink(ob)
    stage.objects.link(ob)
bpy.ops.mesh.primitive_plane_add(size=300,location=(0,0,-.22));ground=bpy.context.object;ground.name='STUDIO floor';move_stage(ground)
mat=bpy.data.materials.new('STUDIO neutral');mat.diffuse_color=(.15,.17,.18,1);ground.data.materials.append(mat)
bpy.ops.mesh.primitive_plane_add(size=300,location=(0,0,2.49));water=bpy.context.object;water.name='STUDIO illustrative waterline / not simulation';move_stage(water);water.hide_render=True
wm=bpy.data.materials.new('STUDIO sea');wm.use_nodes=True;bs=next(n for n in wm.node_tree.nodes if n.type=='BSDF_PRINCIPLED');bs.inputs['Base Color'].default_value=(.018,.075,.085,1);bs.inputs['Metallic'].default_value=.18;bs.inputs['Roughness'].default_value=.22
noise=wm.node_tree.nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=7;noise.inputs['Detail'].default_value=2
bump=wm.node_tree.nodes.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.18;bump.inputs['Distance'].default_value=.17;wm.node_tree.links.new(noise.outputs['Fac'],bump.inputs['Height']);wm.node_tree.links.new(bump.outputs['Normal'],bs.inputs['Normal']);water.data.materials.append(wm)
def lamp(n,loc,energy,size,target):
    data=bpy.data.lights.new(n,'AREA');data.energy=energy;data.shape='DISK';data.size=size;ob=bpy.data.objects.new(n,data);stage.objects.link(ob);ob.location=loc;ob.rotation_euler=(Vector(target)-ob.location).to_track_quat('-Z','Y').to_euler()
lamp('Key',(12,-14,30),5500,16,(0,0,4));lamp('Fill',(-16,4,22),4000,16,(0,0,4));lamp('Rim',(8,24,22),6000,15,(0,0,5))
for g,z,ys in [('LOWER',3.82,[-11,-7,-3,1,5]),('MAIN',6.62,[-8,-4,0,4]),('UPPER',9.37,[-3,1,4])]:
    for y in ys:lamp(g+' inspection lamp '+str(y),(0,y,z),25,1,(0,y,z-3))
cam=bpy.data.cameras.new('NE01 camera');camera=bpy.data.objects.new('NE01 camera',cam);stage.objects.link(camera);scene.camera=camera
def aim(loc,target,scale,perspective=False):
    camera.location=loc;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();cam.type='PERSP' if perspective else 'ORTHO';cam.ortho_scale=scale;cam.lens=22
aim((27,42,23),(0,0,5.3),40)
scene['geometry_authority']='source/ne01.json';scene['revision']=P['revision'];scene['units']='metres = Minecraft blocks';scene['runtime_verified']=False
bpy.context.preferences.filepaths.save_version=0;bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/ne01.blend'))
bpy.ops.object.select_all(action='DESELECT')
for ob in vehicle.objects:ob.select_set(True)
bpy.context.view_layer.objects.active=groups['ROOT'];bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/ne01.glb'),export_format='GLB',use_selection=True,export_animations=True)
for view in args.views.split(','):
    if not view:continue
    for ob in objects:ob.hide_render=False
    scene.frame_set(1)
    water.hide_render=True
    if view=='hero':aim((27,42,23),(0,0,5.3),40)
    elif view=='afloat':water.hide_render=False;aim((24,36,18),(0,0,6.0),39)
    elif view=='rear':aim((-25,-42,20),(0,-.8,5.0),40)
    elif view=='side':aim((55,0,6.5),(0,0,6.5),35)
    elif view=='main_interior':aim((.4,-8.8,5.85),(-.5,-2.5,5.4),10,True)
    elif view=='bridge':aim((0,.3,8.7),(0,4.6,8.35),6,True)
    elif view=='lower_interior':aim((0,2.9,3.08),(0,7.4,2.5),7,True)
    elif view=='cutaway':
        for ob in objects:
            if ob['component'] in ['FLY','UPPER','GLASS','FITTINGS'] or ob.name.startswith(('Main side 1','Main window 1','Main roof','Main forward','Continuous watertight')):ob.hide_render=True
        aim((27,-28,31),(0,0,4.3),38)
    elif view=='open':scene.frame_set(41);aim((-22,-36,18),(0,-4,5),36)
    else:raise ValueError(view)
    scene.render.filepath=str(ROOT/f'preview/ne01_{view}.png');bpy.ops.render.render(write_still=True)
report=dict(revision=P['revision'],source_parts=len(data['parts']),mesh_objects=len(objects),windows=len(data['windows'])+3,doors=len(data['doors']),beds=len(data['beds']),terminals=len(data['terminals']),textures=len(data['materials']),geometry_sha256=hashlib.sha256((ROOT/'source/ne01.json').read_bytes()).hexdigest(),assembly_sha256=hashlib.sha256((ROOT/'assembly/ne01.blend').read_bytes()).hexdigest(),rendered_views=args.views.split(','),minecraft_tested=False)
(ROOT/'reports/BLENDER_BUILD.json').write_text(json.dumps(report,indent=2)+'\n');print('NE01_BUILD_COMPLETE',json.dumps(report))
