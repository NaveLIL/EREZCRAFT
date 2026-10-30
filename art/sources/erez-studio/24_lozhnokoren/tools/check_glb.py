from pathlib import Path
import bpy,json
ROOT=Path(__file__).resolve().parents[1]
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(ROOT/'exports/lozhnokoren_LK01.glb'))
scene=bpy.context.scene;scene.render.fps=24
meshes=[o for o in scene.objects if o.type=='MESH'];assert len(meshes)==357,len(meshes)
def position(name,frame):
    scene.frame_set(frame);return bpy.data.objects[name].matrix_world.translation.copy()
grasp={side:(position(f'{side}_grasp_wrist',142)-position(f'{side}_grasp_wrist',0)).length for side in ['left','right']}
assert min(grasp.values())>.3,grasp
foot=(position('segment_03_left_foot',66)-position('segment_03_left_foot',60)).length;assert foot>.005,foot
report={'glb_reimported_in_blender':True,'mesh_objects':len(meshes),'grasp_animation_travel_blocks':grasp,
        'sample_crawl_foot_travel_blocks':foot,'animations_play_after_import':True,'native_game_verified':False}
(ROOT/'reports/GLB_IMPORT_CHECK.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('LK01_GLB_IMPORT_CHECK_PASS',json.dumps(report))
