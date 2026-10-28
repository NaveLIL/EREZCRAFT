"""Real Blender scenes for the exact old/new backpack meshes; no painted concept art."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
helper=ROOT.parent/'13_minecraft_armor/tools/render_armor.py'
source=(helper).read_text(encoding='utf-8').split('results={};save_scene=None')[0]
source=source.replace("ROOT=Path(__file__).resolve().parents[1]",'')
source=source.replace("source=ROOT/'source/armor_design.json'", "source=ROOT.parent/'13_minecraft_armor/source/armor_design_A04.json'")
exec(compile(source,str(helper),'exec'),globals())
pack_source=ROOT/'source/backpacks_B02.json';packs=json.loads(pack_source.read_text(encoding='utf-8'))
def mannequin(scene,offset):
    rig={};skin=material('Steve reference','entity/player/wide/steve.png')
    for bone,position in design['bones'].items():
        obj=bpy.data.objects.new('reference / '+bone,None);scene.collection.objects.link(obj);obj.location=(position[0]+offset,position[2],24-position[1]);rig[bone]=obj
    for bone,xyz,size,uv in skin_boxes:cube(scene,'player skin / '+bone,bone,rig[bone],xyz,size,skin,uv=uv)
    return rig
def mesh_part(scene,tier,part,palette,offset=0):
    path=packs['textures'][part['texture']];mat=material(tier+' / '+part['role'],path,palette[part['role']])
    # One deterministic coordinate conversion; source remains native torso coordinates.
    vertices=[(p[0]+offset,p[2],24-p[1]) for p in part['vertices']]
    mesh=bpy.data.meshes.new(part['name']);mesh.from_pydata(vertices,[],part['faces']);mesh.update()
    obj=bpy.data.objects.new(tier+' / '+part['name'],mesh);scene.collection.objects.link(obj);mesh.materials.append(mat)
    uv=mesh.uv_layers.new(name='Reference material UV')
    for poly in mesh.polygons:
        normal=poly.normal;axis=max(range(3),key=lambda a:abs(normal[a]));axes=[a for a in range(3) if a!=axis]
        points=[mesh.vertices[mesh.loops[li].vertex_index].co for li in poly.loop_indices]
        low=[min(v[a] for v in points) for a in axes];high=[max(v[a] for v in points) for a in axes]
        for li,v in zip(poly.loop_indices,points):
            u=(v[axes[0]]-low[0])/max(1e-6,high[0]-low[0]);q=(v[axes[1]]-low[1])/max(1e-6,high[1]-low[1])
            if part['texture'] in ['leather','metal']:u=(8+u*8)/64;q=q*8/32
            uv.data[li].uv=(u,1-q)
    obj['source_part']=part['name'];obj['attachment']=part['anchor'];obj['source_file']='source/backpacks_B02.json';return obj
def setup(label,group=True):
    scene,hero,front,back=setup_scene(label)
    scene.cycles.samples=20;scene.render.resolution_x=2100 if group else 1100;scene.render.resolution_y=1350 if group else 1100
    scene['status']='BACKPACKS-B02 geometry review; native checks recorded separately in reports/accepted.json'
    camera=back;camera.location=(-30,95,34) if group else (-21,42,25)
    target=Vector((0,1.5,15.8) if group else (0,3.5,18.1));camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
    camera.data.ortho_scale=57 if group else 18;scene.camera=camera
    return scene,camera
def render(scene,name):
    scene.render.filepath=str(ROOT/'preview'/name.replace('B01','B02'))
    if '--save-only' not in sys.argv:bpy.ops.render.render(write_still=True)
tiers=list(packs['proposed'])
scene,camera=setup('Backpacks B02 / proposed on players')
for tier,offset in zip(tiers,[-15,0,15]):
    mannequin(scene,offset);data=packs['proposed'][tier]
    for part in data['parts']:mesh_part(scene,tier,part,data['palette'],offset)
render(scene,'backpacks_proposed_B01.png');main=scene
scene,camera=setup('Backpacks / previous on players')
for tier,offset in zip(tiers,[-15,0,15]):
    mannequin(scene,offset);data=packs['previous'][tier]
    for part in data['parts']:mesh_part(scene,'previous '+tier,part,data['palette'],offset)
render(scene,'backpacks_previous.png')
for tier in tiers:
    scene,camera=setup('Backpacks B02 / '+tier,False);data=packs['proposed'][tier];objects=[]
    for part in data['parts']:objects.append(mesh_part(scene,tier,part,data['palette']))
    render(scene,tier+'_B01.png')
    bpy.ops.object.select_all(action='DESELECT')
    for obj in objects:obj.select_set(True)
    bpy.context.view_layer.objects.active=objects[0]
    bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports'/(tier+'_B02.glb')),export_format='GLB',use_selection=True)
# Front harness and side silhouettes are explicit review angles, not inferred from a hero image.
scene,camera=setup('Backpacks B02 / front harness')
for tier,offset in zip(tiers,[-15,0,15]):
    mannequin(scene,offset);data=packs['proposed'][tier]
    for part in data['parts']:mesh_part(scene,tier,part,data['palette'],offset)
camera.location=(24,-94,34);camera.rotation_euler=(Vector((0,0,15.8))-camera.location).to_track_quat('-Z','Y').to_euler()
render(scene,'backpacks_harness_B01.png')
bpy.context.window.scene=main
for area in bpy.context.screen.areas:
    if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.shading.type='MATERIAL'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/backpacks_B02.blend'))
(ROOT/'reports/blender.json').write_text(json.dumps({'source_sha256':hashlib.sha256(pack_source.read_bytes()).hexdigest(),'geometry_revision':'B02','save_only':'--save-only' in sys.argv,'verification_reference':'reports/accepted.json','scenes':len(bpy.data.scenes),'rendered_pngs':[] if '--save-only' in sys.argv else [p.name for p in (ROOT/'preview').glob('*B02.png')]},indent=2),encoding='utf-8')
print('BACKPACK_REVIEW_COMPLETE')
