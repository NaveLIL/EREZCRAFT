"""Reuse the verified native JSON mesh/UV importer; author WT03 review scenes."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
base=(ROOT.parent/'18_expedition_tools/tools/build_blender.py').read_text(encoding='utf-8')
base=base[:base.index('names=list(design')]
base=base.replace("from render_dependency import digest","def digest(design):return hashlib.sha256(json.dumps(design,sort_keys=True).encode()).hexdigest()")
base=base.replace('source/tools_T01.json','source/worktools_WT03.json')
base=base.replace("scene.render.resolution_x=1900 if group else 1100;scene.render.resolution_y=950 if group else 1100", "scene.render.resolution_x=1920 if group else 1100;scene.render.resolution_y=1080 if group else 1100")
base=base.replace('scene.cycles.samples=24','scene.cycles.samples=20')
base=base.replace("(23,-90,34) if group else (25,-65,28)","(12,-100,29) if group else (16,-65,23)")
base=base.replace("cam.ortho_scale=56 if group else 21","cam.ortho_scale=72 if group else 20")
tail='''
names=list(design['models']);main=None
for new in (False,True):
    scene=setup('Realm worktools / '+('WT03' if new else 'previous'),True)
    for i,n in enumerate(names):
        root=build(scene,n,(i%4-1.5)*17,new);root.location.z=10 if i<4 else -10
    scene.render.filepath=str(ROOT/'preview'/('worktools_WT03.png' if new else 'worktools_previous.png'));render()
    if new:main=scene
for steel in (False,True):
    tier='riftsteel' if steel else 'riftsilver'
    scene=setup(tier+' / WT03',True);scene.camera.data.ortho_scale=68
    for i,n in enumerate(n for n in names if n.startswith(tier)):build(scene,n,(i-1.5)*17,True)
    scene.render.filepath=str(ROOT/'preview'/(tier+'_WT03.png'));render()
for name in names:
    scene=setup('WT03 / '+name,False);root=build(scene,name,0,True)
    bpy.ops.object.select_all(action='DESELECT');root.select_set(True)
    for o in scene.objects:
        if o.parent==root:o.select_set(True)
    bpy.context.view_layer.objects.active=root
    bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports'/(name+'_WT03.glb')),export_format='GLB',use_selection=True)
    results[name]={'elements':len(design['models'][name]['model']['elements']),'mesh_objects':len([o for o in scene.objects if o.parent==root])}
bpy.context.window.scene=main
for area in bpy.context.screen.areas:
    if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.shading.type='MATERIAL'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/realm_worktools_WT03.blend'))
report={'blender_version':bpy.app.version_string,'source_sha256':hashlib.sha256((ROOT/'source/worktools_WT03.json').read_bytes()).hexdigest(),'native_json_source':True,'geometry_modifiers':False,'game_verified':False,'sets':results,'outputs':{}}
for f in [ROOT/'assembly/realm_worktools_WT03.blend',*(ROOT/'preview').glob('*.png'),*(ROOT/'exports').glob('*.glb')]:report['outputs'][f.relative_to(ROOT).as_posix()]=hashlib.sha256(f.read_bytes()).hexdigest()
(ROOT/'reports/blender.json').write_text(json.dumps(report,indent=2)+'\\n',encoding='utf-8');print('WORKTOOLS_BLENDER_COMPLETE')
'''
(ROOT/'tools/build_blender.py').write_text(base+tail,encoding='utf-8')
print('WT03 Blender script prepared')
