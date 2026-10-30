from pathlib import Path
import bpy,json,hashlib
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/eitan_E01.blend'))
scene=bpy.context.scene;scene.cycles.samples=8;scene.render.resolution_x=960;scene.render.resolution_y=720;scene.render.resolution_percentage=100
camera=scene.camera;camera.data.type='ORTHO';camera.data.ortho_scale=10.8;camera.location=(-7,13,6);camera.rotation_euler=(Vector((0,.7,1.35))-camera.location).to_track_quat('-Z','Y').to_euler()
folder=ROOT/'preview/motion';folder.mkdir(exist_ok=True)
frames=[1,9,17,25,33,41,49,57,61,69,77,81,89,97,101]
for frame in frames:
    scene.frame_set(frame);bpy.context.view_layer.update();scene.render.filepath=str(folder/f'{frame:03}.png');bpy.ops.render.render(write_still=True)
report={'source_blend_sha256':hashlib.sha256((ROOT/'assembly/eitan_E01.blend').read_bytes()).hexdigest(),'frames':frames,'native_blender_renders':True,'scene_file_modified':False,'runtime_test':False}
(ROOT/'reports/MOTION_RENDER.json').write_text(json.dumps(report,indent=2)+'\n');print('EITAN_MOTION_COMPLETE')
