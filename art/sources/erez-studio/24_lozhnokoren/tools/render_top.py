from pathlib import Path
import bpy,json
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
ROOT=Path(__file__).resolve().parents[1]
scene=bpy.context.scene;scene.frame_set(1)
bpy.data.collections['STUDIO / scale reference 1.8 blocks'].hide_render=True
target=Vector((0,0,1.15));camera=scene.camera
camera.location=target+Vector((0,.01,20)).normalized()*20
camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.ortho_scale=19.5
bpy.context.view_layer.update()
points=[world_to_camera_view(scene,camera,o.matrix_world@Vector(p)) for o in bpy.data.collections['LK01 Creature'].objects if o.type=='MESH' for p in o.bound_box]
bounds={'minimum':[min(p[i] for p in points) for i in range(2)],'maximum':[max(p[i] for p in points) for i in range(2)]}
assert min(bounds['minimum'])>.02 and max(bounds['maximum'])<.98,bounds
scene.render.filepath=str(ROOT/'preview/lozhnokoren_LK01_top.png');bpy.ops.render.render(write_still=True)
(ROOT/'reports/TOP_FRAME_CHECK.json').write_text(json.dumps({'full_creature_inside_frame':True,'normalized_bounds':bounds},indent=2)+'\n',encoding='utf8')
print('LK01_TOP_FRAME_COMPLETE',bounds)
