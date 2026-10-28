"""Use the established native-coordinate Blender importer; independent project scene."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
helper=ROOT.parent/'13_minecraft_armor/tools/render_armor.py'
base=helper.read_text(encoding='utf-8').split('results={};save_scene=None')[0]
base=base.replace("ROOT=Path(__file__).resolve().parents[1]",'')
base=base.replace("source=ROOT/'source/armor_design.json'","source=ROOT/'source/kotomyaf_design_A04.json'")
exec(compile(base,str(helper),'exec'),globals())
scene,hero,front,back=setup_scene('Kotomyaf / Siamese A04 no tail')
rig,armor=actor(scene,'kotomyaf',True);scene.frame_set(1)
scene['status']='KOTOMYAF-A04 no tail revision; open face remains approved'
scene.cycles.samples=24
scene.render.resolution_x=1000;scene.render.resolution_y=1200
for name,cam in [('hero',hero),('front',front),('back',back)]:
    scene.camera=cam;scene.render.filepath=str(ROOT/'preview'/f'kotomyaf_{name}_A04.png');bpy.ops.render.render(write_still=True)
scene.camera=hero
bpy.ops.object.select_all(action='DESELECT')
for obj in list(rig.values())+armor:obj.select_set(True)
bpy.context.view_layer.objects.active=armor[0]
bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/kotomyaf_A04.glb'),export_format='GLB',use_selection=True,export_animations=True)
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/kotomyaf_A04.blend'))
print('KOTOMYAF_RENDER_COMPLETE',len(armor))
