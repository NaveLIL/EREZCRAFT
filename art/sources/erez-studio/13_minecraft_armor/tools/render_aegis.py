"""Reuse the reviewed lighting/camera and render only the added endgame set."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
base=(ROOT/'tools/render_armor.py').read_text(encoding='utf-8')
base=base[:base.index('results={};save_scene=None')]
base=base.replace("source=ROOT/'source/armor_design.json'","source=ROOT/'source/armor_design_A03.json'")
exec(compile(base,str(ROOT/'tools/render_armor.py'),'exec'),globals())
scene,hero,front,back=setup_scene('Mezhdumorye armor / Aegis A03')
rig,armor=actor(scene,'aegis',True);scene.frame_set(1)
for label,camera in [('aegis_proposed',hero),('aegis_front',front),('aegis_back',back)]:
    scene.camera=camera;scene.render.filepath=str(ROOT/'preview'/f'{label}.png');bpy.ops.render.render(write_still=True)
scene.camera=hero
bpy.ops.object.select_all(action='DESELECT')
for obj in list(rig.values())+armor:obj.select_set(True)
bpy.context.view_layer.objects.active=armor[0]
bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/aegis_armor_A03.glb'),export_format='GLB',use_selection=True,export_animations=True)
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/mezhdumorye_aegis_A03.blend'))
print('AEGIS_PREVIEW_COMPLETE',len(armor))
