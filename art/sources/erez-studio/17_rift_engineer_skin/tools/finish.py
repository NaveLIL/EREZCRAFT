from pathlib import Path
import argparse,json,hashlib,zipfile,datetime,base64,shutil
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/sk01')
p=argparse.ArgumentParser();p.add_argument('--run-id',required=True);p.add_argument('--reviewed-scenes',action='store_true');a=p.parse_args();assert a.reviewed_scenes
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
r=json.loads((ROOT/'reports/native'/('run_'+a.run_id)/'run.json').read_text(encoding='utf-8'));assert r['passed'];proof=r['proof']
for flag in ('self_contained_model','server_to_client_sync_checked','cpm_loaded','distant_horizons_loaded','crouching_checked','walking_checked','realm_armor_clearance_checked','kotomyaf_vanilla_checked','per_slot_clearance_checked','elytra_backpacks_checked','swimming_checked','resource_reload_checked','first_person_capture','unarmed_hand_checked'):assert proof[flag],flag
assert proof['model_elements']==80 and proof['cpm_config_parallel_probe_calls']==8000 and len(proof['screenshots'])==14
build=json.loads((ROOT/'reports/model_build.json').read_text());assert build['geometry_sha256']==sha(ROOT/'source/avatar_S03.json')
author=json.loads((ROOT/'source/avatar_S03.json').read_text(encoding='utf-8'))
with zipfile.ZipFile(ROOT/'exports/EREZ_Rift_Engineer_S03.cpmproject') as z:
    assert json.loads(z.read('config.json'))==author['config']
    assert z.read('skin.png')==(ROOT/'source/avatar_atlas_S03.png').read_bytes()
    for name,anim in author['animations'].items():assert json.loads(z.read('animations/'+name))==anim
baseline=json.loads((ROOT/'reports/candidate.json').read_text())['main_source_sha256']
assert {f.relative_to(CAND).as_posix():sha(f) for f in (CAND/'src/main').rglob('*') if f.is_file()}==baseline
installer=json.loads((ROOT/'reports/launcher_installer.json').read_text());assert installer['passed'] and installer['focused_installer_fixture_files']==2
delivery=json.loads((ROOT/'reports/delivery_preparation.json').read_text());assert delivery['all_existing_entries_preserved'] and len(delivery['delta'])==2
model=ROOT/'exports/EREZ_Rift_Engineer_S03.cpmmodel';assert model.exists();guard=ROOT/'exports/erez-avatar-compat-0.1.0.jar'
assert sha(guard)==delivery['delta'][1]['sha256']
with zipfile.ZipFile(guard) as z:
    for f in (CAND/'build/classes/java/avatarCompat').rglob('*.class'):assert z.read(f.relative_to(CAND/'build/classes/java/avatarCompat').as_posix())==f.read_bytes()
for name,h in r['screenshot_sha256'].items():assert sha(ROOT/'preview/native'/name)==h
result={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'revision':'EREZ-ENGINEER-S03','geometry_source':'source/avatar_S03.json',
 'geometry_sha256':build['geometry_sha256'],'texture_sha256':sha(ROOT/'source/avatar_atlas_S03.png'),'project_sha256':sha(ROOT/'exports/EREZ_Rift_Engineer_S03.cpmproject'),'model_sha256':sha(model),'model_size_bytes':model.stat().st_size,'compatibility_jar_sha256':sha(guard),
 'native':r,'launcher_installer':installer,'delivery':delivery,'main_gameplay_sources_unchanged':True,'visual_review':'Native front/back, armor, mixed slots, flight and hand inspected',
 'source_authoring':'Original geometry and pixel texture; generated S01 art is concept reference only',
 'cpm_upstream_defect':{'observed_run':'03','failure':'Parallel model loading corrupted lazy CPM ConfigEntry TreeMap/HashMap caches',
  'guard':'Own separately packaged mixin wraps CPM config backing maps with synchronized views; original upstream JAR unchanged; safety policy unchanged',
  'verification':'Guard active on real CPM class; 8000 parallel calls and all native scenes pass'},
 'installed':False,'published':False,'limits':['One real client observing supported mock server players on an integrated server; not a real multi-client or dedicated-server acceptance',
  'First-person capture is a visual check; the skin does not add first-person full-body rendering',
  'All four Interstice sets plus iron/netherite and mixed slots covered; not every third-party armor renderer or shader',
  'Geometry upper bound 480 quads, one 256x256 atlas; no numeric FPS guarantee or new load benchmark',
  'S03 is a new detailed game model for visual review, not a claim of pixel-identical reproduction of the S01 illustration',
  'Launcher candidate unsigned and unpublished; fresh signed sequence and dedicated-server acceptance required before live release']}
(ROOT/'reports/accepted.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
req=json.loads((ROOT/'source/requirements.json').read_text(encoding='utf-8'));req.update(revision='S03 implemented and native checked; visual refinement awaiting user review',
 authoritative_geometry='source/avatar_S03.json',concept_only=False)
req['unknowns']=result['limits'];(ROOT/'source/requirements.json').write_text(json.dumps(req,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
with zipfile.ZipFile(ROOT/'exports/EREZ_Rift_Engineer_S03_bundle.zip','w',zipfile.ZIP_DEFLATED) as z:
    for f in [model,ROOT/'exports/EREZ_Rift_Engineer_S03.cpmproject',guard,ROOT/'README.md',ROOT/'reports/accepted.json',ROOT/'source/avatar_S03.json',ROOT/'source/avatar_atlas_S03.png',ROOT/'exports/delivery/client-manifest.UNSIGNED.json']:
        z.write(f,f.relative_to(ROOT).as_posix())
    for name in proof['screenshots']:z.write(ROOT/'preview/native'/name,'preview/native/'+name)
    for f in (ROOT/'source/compat').rglob('*'):
        if f.is_file():z.write(f,f.relative_to(ROOT).as_posix())
print(json.dumps({'model':str(model),'bytes':model.stat().st_size,'native_passed':True,'launcher_probe_passed':True,'published':False}))
