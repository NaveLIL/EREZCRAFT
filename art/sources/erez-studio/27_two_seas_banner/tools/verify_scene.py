"""Reopen the saved announcement scene, inspect text/frame and preserved sources."""
from pathlib import Path
import bpy,json,hashlib
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
ROOT=Path(__file__).resolve().parents[1];spec=json.loads((ROOT/'source/banner_BN01.json').read_text(encoding='utf8'))
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/two_seas_BN01.blend'));scene=bpy.context.scene;bpy.context.view_layer.update()
assert (scene.render.resolution_x,scene.render.resolution_y)==tuple(spec['resolution'])
assert scene.render.engine=='CYCLES';assert scene.camera
texts={o.name:o for o in bpy.data.objects if o.type=='FONT'}
expected={'Brand':spec['brand'],'Title / DVA':spec['title'][0],'Title / MORYA':spec['title'][1],'Strapline':spec['strapline'],'Features':spec['feature_line'],'Development status':spec['status_line']}
assert {name:o.data.body for name,o in texts.items()}==expected
frames={}
for name,ob in texts.items():
    p=[world_to_camera_view(scene,scene.camera,ob.matrix_world@Vector(c)) for c in ob.bound_box]
    lower=[min(v[i] for v in p) for i in range(2)];upper=[max(v[i] for v in p) for i in range(2)]
    assert min(lower)>.025 and max(upper)<.975,(name,lower,upper)
    frames[name]={'minimum':lower,'maximum':upper}
used_fonts=[f for f in bpy.data.fonts if f.filepath and f.filepath!='<builtin>']
assert all(f.packed_file for f in used_fonts)
images=[im for im in bpy.data.images if im.source=='FILE'];assert all(im.packed_file for im in images)
emblem=next(im for im in images if im.name=='erezcraft-emblem.png')
expected_emblem='940adeeb393ca9cd32e869149d8dd3289c7c9a0ac56de8fc16c8470646892139'
assert sha(ROOT/'source/erezcraft-emblem.png')==expected_emblem
assert hashlib.sha256(bytes(emblem.packed_file.data)).hexdigest()==expected_emblem
ship=bpy.data.objects['SKAT_SK02'];assert sum(o.type=='MESH' for o in ship.children_recursive)==1615
refs=[]
for project,filename in [('25_skat','SK02_ARTIFACTS.json'),('26_lower_ocean','ARTIFACTS.json')]:
    parent=ROOT.parent/project;manifest=json.loads((parent/'reports'/filename).read_text())
    entry=next(a for a in manifest['artifacts'] if a['file'].endswith('.blend'))
    assert sha(parent/entry['file'])==entry['sha256'],project
    refs.append({'project':project,'source_blend_sha256':entry['sha256'],'unchanged':True})
report={'revision':'BN01','saved_scene_reopened':True,'native_blender_scene':True,'resolution':spec['resolution'],
    'text_objects_verified':6,'text_bounds':frames,'fonts_packed':[f.name for f in used_fonts],
    'packed_images':len(images),'approved_emblem_bytes_preserved':True,'existing_ship_meshes':1615,'source_scenes_unchanged':refs,
    'concept_entities_not_gameplay_verified':True,'imagegen_used':False,'published':False}
(ROOT/'reports/SCENE_CHECK.json').write_text(json.dumps(report,indent=2)+'\n');print('BN01_SCENE_VERIFIED',json.dumps(report))
