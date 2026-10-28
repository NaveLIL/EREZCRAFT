"""Front orthographic close-up of the actual candidate model, without perspective."""
from pathlib import Path
import bpy
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
scene=bpy.context.scene
candidate=next(o for o in scene.objects if o.type=='MESH' and o.name.startswith('interstice_riftsteel_sword_'))
vertices=[candidate.matrix_world@v.co for v in candidate.data.vertices]
point=max(vertices,key=lambda p:p.x+p.z)
center=point-Vector((1.5,0,1.5))
cam_data=bpy.data.cameras.new('Tip orthographic');cam=bpy.data.objects.new('Tip orthographic',cam_data)
scene.collection.objects.link(cam);cam.location=center+Vector((0,-60,0))
cam.rotation_euler=(center-cam.location).to_track_quat('-Z','Y').to_euler()
cam_data.type='ORTHO';cam_data.ortho_scale=6.5;scene.camera=cam
scene.render.resolution_x=800;scene.render.resolution_y=800
scene.render.filepath=str(ROOT/'preview/tip_LS03.png')
bpy.ops.render.render(write_still=True)
print('TIP_RENDER_COMPLETE')
