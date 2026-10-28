from pathlib import Path
import bpy,json,hashlib,shutil
ROOT=Path(r'D:/repos/Blender/creations/19_realm_worktools');old=json.loads((ROOT/'source/worktools_WT10.json').read_text(encoding='utf8'));new=json.loads((ROOT/'source/worktools_WT11.json').read_text(encoding='utf8'))
for name in new['models']:
 for key in ['elements','textures']:assert old['models'][name]['model'][key]==new['models'][name]['model'][key]
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/realm_worktools_WT10.blend'))
for scene in bpy.data.scenes:
 scene.name=scene.name.replace('WT10','WT11');scene['source']='source/worktools_WT11.json';scene['status']='WT11 display transforms; geometry identical to WT10'
bpy.context.scene['revision']='WT11'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'assembly/realm_worktools_WT11.blend'))
for name in new['models']:shutil.copy2(ROOT/'exports'/(name+'_WT10.glb'),ROOT/'exports'/(name+'_WT11.glb'))
for name in ['worktools','riftsilver','riftsteel']:shutil.copy2(ROOT/'preview'/(name+'_WT10.png'),ROOT/'preview'/(name+'_WT11.png'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
r=json.loads((ROOT/'reports/blender_WT10.json').read_text());r.update(source_sha256=sha(ROOT/'source/worktools_WT11.json'),geometry_unchanged_from_WT10=True,render_reused_from_WT10=True,reason='Only Minecraft display transforms changed; mesh, UV, material, lighting and geometry review images are identical',game_verified=False,blender_sha256=sha(ROOT/'assembly/realm_worktools_WT11.blend'))
r['outputs']={p.relative_to(ROOT).as_posix():sha(p) for p in [ROOT/'assembly/realm_worktools_WT11.blend',*(ROOT/'exports').glob('*_WT11.glb'),*(ROOT/'preview').glob('*_WT11.png')]}
(ROOT/'reports/blender_WT11.json').write_text(json.dumps(r,indent=2)+'\n',encoding='utf8');print('WT11_DISPLAY_REVISION_SAVED')
