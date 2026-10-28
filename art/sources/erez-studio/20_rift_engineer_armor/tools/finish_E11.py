"""Package only a revision-bound Blender/native acceptance candidate; never deploy."""
from pathlib import Path
import json,hashlib,zipfile,shutil,datetime,difflib,subprocess
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');CAND=GAME/'.verification/ea01';BASE=GAME/'.verification/wt01'
read=lambda p:json.loads(p.read_text(encoding='utf-8'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
mesh=ROOT/'source/armor_E11.json';atlas=ROOT/'source/armor_E11_atlas.png';d=read(mesh);params=read(ROOT/'source/armor_E11_parameters.json')
blender=read(ROOT/'reports/blender_E11.json');review=read(ROOT/'reports/review_verification.json');native=read(ROOT/'reports/native/run_07/run.json')
assert review['passed'] and blender['source_sha256']==review['authoritative_mesh_sha256']==sha(mesh)
assert native['passed'] and native['proof']['loaded_mesh_sha256']==sha(mesh)
assert native['proof']['source_parts']==len(d['parts']) and native['proof']['source_faces']==sum(len(p['faces']) for p in d['parts'])
assert native['proof']['distant_horizons_loaded'] and native['proof']['reload_checked'] and native['proof']['swimming_checked']

# Fail the exporter before a native run if float32 normals would collapse again.
for p in d['parts']:
    for f in p['faces']:
        v=np.array([p['vertices'][i] for i in f['indices']],dtype=np.float32);n=np.cross(v[1]-v[0],v[2]-v[0]);assert np.dot(n,n)>1e-24,(p['name'],f['indices'])
for role,color in params['flat_mask_materials'].items():
    image=Image.open(ROOT/'source'/('palette_E11_'+role+'.png')).convert('RGBA');assert len(image.getcolors())==1
    assert image.getpixel((0,0))==tuple(int(color[i:i+2],16) for i in (1,3,5))+(255,)

testroot=CAND/'.verification/20261027T123204.122899Z-engineer24';tests=read(testroot/'runProgressionGameTestServer.json');assert tests['accepted'] and tests['required_tests_passed']==43
tested=read(testroot/'source-sha256.json');checked_java=0
for rel,h in tested.items():
    rel=rel.replace('\\','/')
    if rel.startswith('src/main/java/') and rel.endswith('.java'):assert sha(CAND/rel)==h,rel;checked_java+=1

def cloth_signature(source):
    obj=read(source);rows=[{k:p[k] for k in ('name','bone','vertices')}|{'faces':[f['indices'] for f in p['faces']]} for p in obj['parts'] if p['bone'].endswith('_leg') and any(s in p['name'] for s in ('skirt','_hem','inner_lining','gusset','_front_fold','_back_fold','waist_bridge'))]
    return hashlib.sha256(json.dumps(rows,sort_keys=True).encode()).hexdigest()
cloth=read(ROOT/'reports/cloth/run_05/result.json');assert cloth['passed'];assert cloth_signature(mesh)==cloth_signature(ROOT/'source/armor_E07.json')
clothrun=read(ROOT/'reports/cloth/run_05/run.json');clothjava=CAND/'src/main/java/pro/erez/interstice/client/EngineerCloth.java';assert sha(clothjava)==clothrun['source_sha256']

jar=CAND/'build/libs/interstice-0.7.0-preview.24-engineer.jar';prior=ROOT.parent/'19_realm_worktools/exports/interstice-0.7.0-preview.23-worktools.jar'
with zipfile.ZipFile(prior) as a,zipfile.ZipFile(jar) as b:
    assert not set(a.namelist())-set(b.namelist())
    changed=[n for n in a.namelist() if a.read(n)!=b.read(n)];added=sorted(set(b.namelist())-set(a.namelist()))
    changed_classes=[n for n in changed if n.endswith('.class')]
    assert all(n.startswith('pro/erez/interstice/gear/RealmArmaments') for n in changed_classes),changed_classes
    assert all(n.startswith(('pro/erez/interstice/client/Engineer','pro/erez/interstice/gear/Engineer')) for n in added if n.endswith('.class'))
    unchanged_classes=sum(n.endswith('.class') and n not in changed for n in a.namelist())
    for source,entry in ((mesh,'assets/interstice/armor/engineer.json'),(atlas,'assets/interstice/textures/models/armor/engineer.png')):
        assert b.read(entry)==source.read_bytes()==(CAND/'build/resources/main'/entry).read_bytes()
    for file in (CAND/'build/classes/java/main').rglob('*.class'):assert b.read(file.relative_to(CAND/'build/classes/java/main').as_posix())==file.read_bytes()
for suffix in ('.jar','-sources.jar'):
    src=CAND/'build/libs'/('interstice-0.7.0-preview.24-engineer'+suffix);shutil.copy2(src,ROOT/'exports'/src.name)

baseline=read(ROOT/'reports/game_baseline.json')['files'];snapshot={};patch=[]
for file in (CAND/'src/main').rglob('*'):
    if not file.is_file():continue
    rel=file.relative_to(CAND).as_posix()
    if baseline.get(rel)==sha(file):continue
    dest=ROOT/'source/code'/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,dest);snapshot[rel]=sha(file)
    if file.suffix in ('.java','.json'):
        previous=(BASE/rel).read_text(encoding='utf-8').splitlines(True) if (BASE/rel).exists() else []
        patch.extend(difflib.unified_diff(previous,file.read_text(encoding='utf-8').splitlines(True),fromfile='a/'+rel,tofile='b/'+rel))
rel='src/smoke/java/pro/erez/interstice/client/EngineerNative.java';dest=ROOT/'source/code'/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(CAND/rel,dest);snapshot[rel]=sha(CAND/rel)
(ROOT/'source/game_changes.patch').write_text(''.join(patch),encoding='utf-8')
for name in ('runProgressionGameTestServer.json','runProgressionGameTestServer.log','source-sha256.json','summary.json'):
    dest=ROOT/'reports/gametests';dest.mkdir(exist_ok=True);shutil.copy2(testroot/name,dest/name)

frames=sorted((ROOT/'preview/native/E11').glob('engineer-motion-walk-*.png'));assert len(frames)==24 and len({sha(p) for p in frames})>1
video=ROOT/'preview/native/E11/engineer_motion_E11.mp4';ffmpeg=next((ROOT/'tools/.video_deps/imageio_ffmpeg/binaries').glob('ffmpeg-*.exe'))
command=[str(ffmpeg),'-y','-loglevel','error','-framerate','10','-i',str(frames[0].parent/'engineer-motion-walk-%03d.png'),'-vf','scale=1280:720:flags=lanczos','-c:v','libx264','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(video)]
subprocess.run(command,check=True)
(ROOT/'reports/motion_video.json').write_text(json.dumps(dict(command=command,video_sha256=sha(video),input_sha256={p.name:sha(p) for p in frames},presentation_fps=10,frame_count=24,source='Unmodified native screenshot sequence; display timing is 10 fps, not a measured realtime performance benchmark'),indent=2)+'\n',encoding='utf-8')

req=read(ROOT/'source/requirements.json');req.update(revision='E11 native visual candidate',current_geometry='source/armor_E11.json',current_scope='Owner-directed appearance and native movement acceptance before gameplay design',owner_visual_approval='Pending final E11 review',latest_changes=params['owner_corrections'])
req['constraints']=[s.replace('No armor stats, recipes or abilities yet','Iron armor baseline only for visual testing; no authored balance, recipes or abilities') for s in req['constraints']]
req['acceptance']=[s.replace('Unverified in-game movement and performance explicitly pending','Native motion checked in prepared scene; mass performance and first-person sleeve rendering remain unverified') for s in req['acceptance']]
req['must_preserve']=[s.replace('Chest and back Star of David','Rear Star of David only; front emblem and backing removed by owner') for s in req['must_preserve']]
(ROOT/'source/requirements.json').write_text(json.dumps(req,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
shutil.copy2(ROOT/'tools/review_README.md',ROOT/'README.md');shutil.copy2(ROOT/'tools/review_template.html',ROOT/'reports/review.html')
for p in (ROOT/'README.md',ROOT/'reports/review.html'):p.write_text(p.read_text(encoding='utf-8').replace('native/run_04','native/run_07'),encoding='utf-8')
record=dict(recorded_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),revision='E11',version='0.7.0-preview.24-engineer',candidate=str(CAND),mesh_sha256=sha(mesh),atlas_sha256=sha(atlas),jar_sha256=sha(jar),jar_bytes=jar.stat().st_size,parts=len(d['parts']),faces=sum(len(p['faces']) for p in d['parts']),exact_mesh_uv_atlas_in_jar_and_processed_resources=True,blender='reports/blender_E11.json',native=dict(passed=True,evidence='reports/native/run_07/run.json',mod_count=native['proof']['mod_count'],distant_horizons=True,motion_frames=24),gametests=dict(passed=43,runtime_java_files_unchanged_since_tests=checked_java,evidence='reports/gametests/runProgressionGameTestServer.json'),cloth=dict(evidence='reports/cloth/run_05/result.json',source_sha256=sha(clothjava),identical_cloth_surface_geometry_to_tested_E07=True,signature=cloth_signature(mesh),sampled_points=cloth['sampled_surface_points']),runtime_delta=dict(unchanged_prior_classes=unchanged_classes,changed_prior_classes=changed_classes,added_classes=[n for n in added if n.endswith('.class')]),source_snapshot=snapshot,flat_mask_materials=params['flat_mask_materials'],old_S03_preserved=True,owner_final_visual_approval=False,deployed=False,limits=['Prepared gallery, not Survival gameplay acceptance','No mass-player FPS benchmark or first-person sleeve acceptance','Iron baseline statistics only; recipes and abilities not designed','Geometry/UV/albedo parity does not make studio and Minecraft lighting pixel-identical'])
record['gameplay_proposal']={'implemented':False,'path':'source/engineer_gameplay_proposal.json','sha256':sha(ROOT/'source/engineer_gameplay_proposal.json'),'design_review':'reports/gameplay_design_review.json','note':'Future stats and abilities; current native candidate still has the iron visual-test baseline.'}
(ROOT/'reports/game_review.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

archive=ROOT/'exports/EREZ_Rift_Engineer_Armor_E11_review.zip'
files=[ROOT/'README.md',ROOT/'source/requirements.json',ROOT/'source/engineer_gameplay_proposal.json',ROOT/'reports/GAMEPLAY_PROPOSAL.md',mesh,atlas,ROOT/'source/armor_E11_parameters.json',ROOT/'source/armor_E07.json',ROOT/'source/game_changes.patch',ROOT/'assembly/EREZ_Rift_Engineer_Armor_E11.blend',ROOT/'exports/EREZ_Rift_Engineer_Armor_E11.glb',ROOT/'exports'/jar.name,ROOT/'exports'/jar.name.replace('.jar','-sources.jar')]
files+=list((ROOT/'source/code').rglob('*'))+list((ROOT/'source/materials').glob('*'))+list((ROOT/'source/reference').glob('*'))+list((ROOT/'tools').glob('*.py'))+list((ROOT/'tools').glob('*.java'))+list((ROOT/'tools').glob('*.txt'))+list((ROOT/'tools').glob('*.html'))+list((ROOT/'tools').glob('*.md'))
files+=list((ROOT/'source').glob('armor_E*_parameters.json'))+list((ROOT/'source').glob('palette_E11_*.png'))
files+=[ROOT/'reports/gameplay_design_review.json']
files+=list((ROOT/'preview').glob('*E11*.png'))+list((ROOT/'preview/native/E11').glob('*'))+list((ROOT/'reports/native/run_07').glob('*'))+list((ROOT/'reports/gametests').glob('*'))+list((ROOT/'reports/cloth/run_05').glob('*.json'))
files+=[ROOT/'reports'/name for name in ('review.html','game_review.json','review_verification.json','blender_E11.json','motion_video.json','native_expected.json','game_resources.json')]
files+=[ROOT/'preview'/('engineer_'+r+'_front_back.png') for r in ('E04','E07','E08','E09','E10')]
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for file in dict.fromkeys(files):
        if file.is_file():z.write(file,file.relative_to(ROOT))
(ROOT/'reports/package.json').write_text(json.dumps(dict(path=str(archive),sha256=sha(archive),bytes=archive.stat().st_size,stage='E11 local visual candidate; owner review pending; no deployment'),indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(revision='E11',native_passed=True,jar_sha256=record['jar_sha256'],prior_classes_unchanged=unchanged_classes,archive_bytes=archive.stat().st_size)))
