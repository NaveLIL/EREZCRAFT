from pathlib import Path
import bpy,json,hashlib,shutil
ROOT=Path(r'D:/repos/Blender/creations/19_realm_worktools');old=json.loads((ROOT/'source/worktools_WT08.json').read_text(encoding='utf8'));new=json.loads((ROOT/'source/worktools_WT09.json').read_text(encoding='utf8'))
for name in new['models']:
 for key in ['elements','textures']:assert old['models'][name]['model'][key]==new['models'][name]['model'][key]
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/realm_worktools_WT08.blend'))
for scene in bpy.data.scenes:
 scene.name=scene.name.replace('WT08','WT09');scene['source']='source/worktools_WT09.json';scene['status']='WT09 display transforms; geometry identical to WT08'
bpy.context.scene['revision']='WT09'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/realm_worktools_WT09.blend'))
for name in new['models']:shutil.copy2(ROOT/'exports'/(name+'_WT08.glb'),ROOT/'exports'/(name+'_WT09.glb'))
for name in ['worktools','riftsilver','riftsteel']:shutil.copy2(ROOT/'preview'/(name+'_WT08.png'),ROOT/'preview'/(name+'_WT09.png'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
r=json.loads((ROOT/'reports/blender_WT08.json').read_text());r.update(source_sha256=sha(ROOT/'source/worktools_WT09.json'),geometry_unchanged_from_WT08=True,render_reused_from_WT08=True,reason='Only Minecraft display transforms changed; mesh, UV, material, lighting and geometry review images are identical',game_verified=False,blender_sha256=sha(ROOT/'assembly/realm_worktools_WT09.blend'))
r['outputs']={p.relative_to(ROOT).as_posix():sha(p) for p in [ROOT/'assembly/realm_worktools_WT09.blend',*(ROOT/'exports').glob('*_WT09.glb'),*(ROOT/'preview').glob('*_WT09.png')]}
(ROOT/'reports/blender_WT09.json').write_text(json.dumps(r,indent=2)+'\n',encoding='utf8');print('WT09_DISPLAY_REVISION_SAVED')
