from pathlib import Path
import argparse,datetime,hashlib,json,shutil,zipfile
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');CAND=GAME/'.verification/bp01';BASE=GAME/'.verification/ai01'
p=argparse.ArgumentParser();p.add_argument('--assembly',required=True);p.add_argument('--native-run',required=True);p.add_argument('--reviewed-scenes',action='store_true');args=p.parse_args();assert args.reviewed_scenes
sha=lambda b:hashlib.sha256(b).hexdigest()
assembly=CAND/args.assembly;assert json.loads((assembly/'summary.json').read_text())['passed']
for n,h in json.loads((assembly/'source-sha256.json').read_text()).items():
    if n.replace('\\','/').startswith('src/main/'):assert sha((CAND/n).read_bytes())==h,n
native=ROOT/'reports/native'/('run_'+args.native_run);record=json.loads((native/'run.json').read_text(encoding='utf-8'));assert record['passed'];proof=record['proof']
for key in ['distant_horizons_loaded','previous_16_armor_icons_checked','crouching_checked','walking_checked','kotomyaf_checked','elytra_checked','swimming_checked','benchmark_camera_inside_studio']:assert proof[key],key
assert proof['mod_version']=='0.7.0-preview.21-backpacks' and proof['benchmark_players']==10 and len(proof['screenshots'])==16
assert len(proof['frame_benchmark'])==3 and all(r['samples']==1200 for r in proof['frame_benchmark'])
assert all(r.get('minimum_players_drawn')==10 for r in proof['frame_benchmark']),"Ten actors must actually render throughout each measurement"
if (native/'benchmark_review.json').exists():assert json.loads((native/'benchmark_review.json').read_text())['accepted_for_performance']
bench=proof['frame_benchmark'];old_ms=(bench[0]['median_frame_ms']+bench[2]['median_frame_ms'])/2;new_ms=bench[1]['median_frame_ms']
performance={'gpu':proof['gpu'],'resolution':'1920x1080','visible_test_players':10,'armor':'Magen','backpack':'Rift','method':'Old/new/old native client frame intervals, 480 warm-up frames and 1200 samples each; vsync off; native framerate option 260 means uncapped',
 'old_median_frame_ms':old_ms,'new_median_frame_ms':new_ms,'delta_ms':new_ms-old_ms,'delta_percent':(new_ms/old_ms-1)*100,'old_equivalent_fps':1000/old_ms,'new_equivalent_fps':1000/new_ms,
 'framerate_option':260,'effective_frame_cap_fps':None,'limit_source':'Pinned Minecraft 1.21.1 Minecraft.runTick calls RenderSystem.limitDisplayFPS only when getFramerateLimit() < 260.',
 'limitation':'Short local rendering comparison on this GPU; includes other frame work, not universal FPS or dedicated-server capacity evidence.'}
jar=CAND/'build/libs/interstice-0.7.0-preview.21-backpacks.jar';old=ROOT.parent/'15_armor_inventory_icons/exports/interstice-0.7.0-preview.20-icons.jar'
source=ROOT/'source/backpacks_B02.json';geometry_sha=sha(source.read_bytes())
with zipfile.ZipFile(old) as a,zipfile.ZipFile(jar) as b:
    before={n:a.read(n) for n in a.namelist() if not n.endswith('/')};after={n:b.read(n) for n in b.namelist() if not n.endswith('/')}
    assert before.keys()<=after.keys();changed=[n for n in before if before[n]!=after[n]]
    allowed_models={'assets/interstice/models/item/'+n+'.json' for n in ['field_backpack','expedition_backpack','rift_backpack']}
    for n in changed:
        assert n in allowed_models or n=='META-INF/neoforge.mods.toml' or n.startswith(('pro/erez/interstice/client/BackpackLayer','pro/erez/interstice/client/ArmorIconModels')),n
    assert json.loads(after['assets/interstice/equipment/backpacks.json'])['source_sha256']==geometry_sha
    for f in (CAND/'build/classes/java/main').rglob('*.class'):assert after[f.relative_to(CAND/'build/classes/java/main').as_posix()]==f.read_bytes()
    common_unchanged=sum(n.endswith('.class') and before[n]==after[n] for n in before)
baseline=json.loads((ROOT/'reports/baseline.json').read_text())['files'];current={f.relative_to(CAND).as_posix():sha(f.read_bytes()) for f in (CAND/'src/main').rglob('*') if f.is_file()}
delta=[n for n,h in current.items() if baseline.get(n)!=h]
expected={'src/main/java/pro/erez/interstice/client/'+n+'.java' for n in ['BackpackModels','BackpackLayer','ArmorIconModels']}|{'src/main/resources/assets/interstice/equipment/backpacks.json'}|{'src/main/resources/'+n for n in allowed_models}
assert set(delta)==expected,delta
writes={}
for n in delta:
    target=GAME/n;value=(CAND/n).read_bytes()
    if n in baseline:
        assert target.exists();actual=target.read_bytes();base=(BASE/n).read_bytes()
        if n.endswith('.json'):assert json.loads(actual)==json.loads(base),'Independent model edit: '+n
        else:assert actual.replace(b'\r\n',b'\n')==base.replace(b'\r\n',b'\n'),'Independent client edit: '+n
    elif target.exists():assert target.read_bytes()==value,'New source collision: '+n
    writes[n]=value
untouched={f.relative_to(GAME).as_posix():sha(f.read_bytes()) for f in (GAME/'src/main').rglob('*') if f.is_file() and f.relative_to(GAME).as_posix() not in writes}
for n,value in writes.items():target=GAME/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(value)
for n,h in untouched.items():assert sha((GAME/n).read_bytes())==h,n
for target in [ROOT/'exports'/jar.name,GAME/'build/libs'/jar.name]:shutil.copy2(jar,target)
shutil.copy2(jar.with_name(jar.stem+'-sources.jar'),ROOT/'exports'/(jar.stem+'-sources.jar'))
result={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'version':proof['mod_version'],'geometry_revision':'BACKPACKS-B02','source_sha256':geometry_sha,'jar_sha256':sha(jar.read_bytes()),
 'native':record,'assembly_evidence':str(assembly),'performance':performance,'main_source_delta':delta,'unchanged_old_runtime_classes':common_unchanged,'unchanged_primary_runtime_files':len(untouched),
 'visual_direction_accepted':'User broadly accepted the B01 direction and explicitly authorized continuation',
 'new_texture_files':False,'maximum_worn_material_batches':3,'worn_quads':{'field':318,'expedition':368,'rift':372},'installed':False,'published':False,'owner_profile_used':False,
 'limits':['One real client with supported mock server player connections; no real multiplayer capacity acceptance','Brief local frame comparison, not a guarantee across all GPUs, shaders or modpacks','External gear and the core remain cosmetic; no new item functionality','Native scenes manually inspected; not every modded chest item or custom player model was covered']}
for target in [ROOT/'reports/accepted.json',GAME/'docs/verification/BACKPACKS_REDESIGN.json']:target.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
requirements=json.loads((ROOT/'source/requirements.json').read_text(encoding='utf-8'));requirements['revision']='BACKPACKS-B02';requirements['authoritative_geometry']='source/backpacks_B02.json';requirements['approval']='Visual direction accepted; native B02 scenes inspected';(ROOT/'source/requirements.json').write_text(json.dumps(requirements,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
backlog=json.loads((ROOT/'source/item_model_backlog.json').read_text(encoding='utf-8'));backlog['queue'][0]['status']='B02 implemented and native checked; local version 21, not installed';(ROOT/'source/item_model_backlog.json').write_text(json.dumps(backlog,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
handoff=GAME/'docs/HANDOFF.md';s=handoff.read_text(encoding='utf-8');header,rest=s.split('\n',1)
entry='\nBackpacks 2026-10-27: all three B02 models implemented; local JAR 0.7.0-preview.21-backpacks. Capacities 54/72/84, storage, recipes and gameplay classes preserved. Native client with DH and 106 mods: wide/slim players, walking, crouching, swimming, Kotomyaf, armor, folded/flying elytra, all three item miniatures and the previous 16 armor icons checked; 16 screenshots. Old/new/old frame comparison with 10 actually rendered players: see [evidence](verification/BACKPACKS_REDESIGN.json); local rendering evidence only. Candidate .verification/bp01. Sources, Blender scenes and item-art backlog: Blender creations/16_expedition_backpacks. Only seven main-source paths integrated; unrelated files preserved. Server, owner profiles and primary gradle.properties unchanged.\n'
handoff.write_text(header+'\n'+entry+rest,encoding='utf-8')
with zipfile.ZipFile(ROOT/'exports/backpacks_B02_ingame.zip','w',zipfile.ZIP_DEFLATED) as z:
    for n,h in record['screenshot_sha256'].items():
        f=ROOT/'preview/native'/n;assert sha(f.read_bytes())==h;z.write(f,n)
    z.write(ROOT/'reports/accepted.json','verification.json')
print(json.dumps({'version':result['version'],'jar_sha256':result['jar_sha256'],'performance':performance,'integrated_files':len(delta)},ensure_ascii=False))
