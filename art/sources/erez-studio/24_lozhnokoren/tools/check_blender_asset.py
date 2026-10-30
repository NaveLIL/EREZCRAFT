"""Read the saved .blend in background Blender; no rendering or edits."""
from pathlib import Path
import bpy,json,hashlib
ROOT=Path(__file__).resolve().parents[1]
source=json.loads((ROOT/'source/lozhnokoren_LK01.bbmodel').read_text(encoding='utf8'))
collection=bpy.data.collections['LK01 Creature'];scene=bpy.context.scene
meshes=[o for o in collection.objects if o.type=='MESH']
assert len(meshes)==len(source['elements'])
assert len({o['source_uuid'] for o in meshes})==len(meshes)
assert {o['source_uuid'] for o in meshes}=={e['uuid'] for e in source['elements']}
assert all(len(o.data.polygons)==6 for o in meshes)
assert all(not polygon.use_smooth for o in meshes for polygon in o.data.polygons)
assert all(len(o.data.uv_layers)==1 for o in meshes)
textures=[]
for spec in source['erezcraft_materials']:
    image=next(im for im in bpy.data.images if im.source=='FILE' and Path(im.filepath).name==Path(spec['file']).name)
    assert image.size[:]==(16,16) and image.packed_file
    digest=hashlib.sha256(bytes(image.packed_file.data)).hexdigest()
    assert digest==hashlib.sha256((ROOT/spec['file']).read_bytes()).hexdigest()
    textures.append({'file':spec['file'],'packed_bytes_match':True})
def position(name,frame):
    scene.frame_set(frame);return bpy.data.objects[name].matrix_world.translation.copy()
foot_names=[f'segment_{i:02d}_{side}_foot' for i in range(10) for side in ['left','right']]
foot_deltas={name:(position(name,67)-position(name,61)).length for name in foot_names}
moving_feet=sum(distance>.005 for distance in foot_deltas.values());assert moving_feet>=16,(moving_feet,foot_deltas)
head_delta=(position('head',143)-position('head',1)).length;assert head_delta>.05,head_delta
grasp_deltas={side:(position(f'{side}_grasp_wrist',143)-position(f'{side}_grasp_wrist',1)).length for side in ['left','right']}
assert min(grasp_deltas.values())>.3,grasp_deltas
report={'saved_blend_opened':True,'mesh_objects_match_source':True,'uv_layers_present':True,'flat_shading':True,
        'cuboids':len(meshes),'packed_textures':textures,'moving_crawl_feet_out_of_20':moving_feet,
        'foot_travel_blocks':foot_deltas,'head_threat_travel_blocks':head_delta,'grasp_threat_travel_blocks':grasp_deltas,
        'animation_checked_at_saved_scene_frames':True,'native_game_verified':False}
(ROOT/'reports/SAVED_BLEND_CHECK.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('LK01_SAVED_BLEND_CHECK_PASS',json.dumps({k:v for k,v in report.items() if k not in ['foot_travel_blocks','packed_textures']}))
