"""Deliver the approved, tested candidate and integrate its narrow source delta safely."""
from pathlib import Path
import argparse,datetime,hashlib,json,shutil,zipfile,difflib,re
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');CAND=GAME/'.verification/kt01';BASE=GAME/'.verification/ag03'
p=argparse.ArgumentParser();p.add_argument('--component',required=True);p.add_argument('--assembly',required=True);p.add_argument('--native-run',required=True);args=p.parse_args()
sha=lambda b:hashlib.sha256(b).hexdigest()
component=CAND/args.component;summary=json.loads((component/'summary.json').read_text())
assert summary['passed'] and set(summary['completed_tasks'])=={'runProgressionGameTestServer','assemble'}
assert 'All 29 required tests passed' in (component/'runProgressionGameTestServer.log').read_text(encoding='utf-8')
checked=json.loads((component/'source-sha256.json').read_text())
checked_java={n.replace('\\','/') for n in checked if n.replace('\\','/').startswith('src/main/') and n.endswith('.java')}
assert checked_java=={p.relative_to(CAND).as_posix() for p in (CAND/'src/main').rglob('*.java')},'Unexpected new or removed runtime Java file'
cosmetic_changes=[]
allowed_cosmetics={'assets/interstice/armor/kotomyaf.json','assets/interstice/models/item/kotomyaf_leggings.json','assets/interstice/provenance/kotomyaf.json','assets/interstice/lang/en_us.json','assets/interstice/lang/ru_ru.json'}
for name,h in checked.items():
    normal=name.replace('\\','/')
    if normal.startswith('src/main/') and sha((CAND/name).read_bytes())!=h:
        assert normal.removeprefix('src/main/resources/') in allowed_cosmetics,name
        cosmetic_changes.append(normal)
assembly=CAND/args.assembly;assembly_result=json.loads((assembly/'summary.json').read_text());assert assembly_result['passed'] and assembly_result['completed_tasks']==['assemble']
for name,h in json.loads((assembly/'source-sha256.json').read_text()).items():
    if name.replace('\\','/').startswith('src/main/'):assert sha((CAND/name).read_bytes())==h,name
old_geo=json.loads((ROOT/'source/kotomyaf_design_A02.json').read_text(encoding='utf-8'))['sets']['kotomyaf']
new_geo=json.loads((ROOT/'source/kotomyaf_design_A04.json').read_text(encoding='utf-8'))['sets']['kotomyaf']
assert old_geo['palette']==new_geo['palette']
assert [c for c in old_geo['cubes'] if not c['name'].startswith('curled_tail_')]==new_geo['cubes']
assert len(new_geo['cubes'])==75 and all('tail' not in c['name'] for c in new_geo['cubes'])
native=ROOT/'reports/native'/('run_'+args.native_run);record=json.loads((native/'run.json').read_text());assert record['passed']
proof=record['proof']
for key in ['distant_horizons_loaded','crouching_checked','walking_checked','bow_pose_checked','backpack_visual_checked','swimming_checked',
    'native_purr_finished_after_shift_release','native_jump_meow_queued_without_cut','native_creeper_and_baby_zombie_retreated','tail_absent']:assert proof[key],key
assert proof['mod_version']=='0.7.0-preview.19-kotomyaf' and proof['shortest_purr_ms']>=2900
assert {r['skin_model'] for r in proof['players'] if r['tier']=='kotomyaf'}=={'default','slim'}
assert json.loads((ROOT/'source/requirements.json').read_text(encoding='utf-8'))['visual_approval']['approved']
jar=CAND/'build/libs/interstice-0.7.0-preview.19-kotomyaf.jar'
old=ROOT.parent/'13_minecraft_armor/exports/interstice-0.7.0-preview.18-armor.jar'
with zipfile.ZipFile(old) as oldzip,zipfile.ZipFile(jar) as newzip:
    a={n:oldzip.read(n) for n in oldzip.namelist() if not n.endswith('/')};b={n:newzip.read(n) for n in newzip.namelist() if not n.endswith('/')}
    assert a.keys()<=b.keys(),'No existing artifact entry may disappear'
    assert a['assets/interstice/armor/armor_design.json']==b['assets/interstice/armor/armor_design.json'],'Previous armor geometries must remain byte-identical'
    assert b['assets/interstice/armor/kotomyaf.json']==(ROOT/'source/kotomyaf_design_A04.json').read_bytes()
    changed_old=[n for n in a if a[n]!=b[n]]
    for name in changed_old:
        if not name.endswith('.class'):continue
        assert any(name.startswith('pro/erez/interstice/'+prefix) for prefix in ['client/RealmArmorClient','client/RealmArmorModel','gear/RealmArmaments']),name
    for item in (CAND/'build/classes/java/main').rglob('*.class'):
        name=item.relative_to(CAND/'build/classes/java/main').as_posix();assert b[name]==item.read_bytes(),name
baseline=json.loads((ROOT/'reports/baseline.json').read_text())['files']
current={str(p.relative_to(CAND)):sha(p.read_bytes()) for p in (CAND/'src/main').rglob('*') if p.is_file()}
assert baseline.keys()<=current.keys()
changed=[n for n in current if current[n]!=baseline.get(n)]
# Prepare every write before changing the primary checkout. JSON additions are merged,
# preserving independent fauna strings and any other unrelated local additions.
writes={};before={}
for name in changed:
    value=(CAND/name).read_bytes();target=GAME/name;previous=target.read_bytes() if target.exists() else None
    before[name]=previous
    if name not in baseline:
        assert previous is None or previous==value,'New file collision: '+name
        writes[name]=value;continue
    base=(BASE/name).read_bytes();assert sha(base)==baseline[name],name
    assert previous is not None,name
    if name.endswith('.json'):
        initial=json.loads(base);new=json.loads(value);actual=json.loads(previous)
        if '/tags/' in name.replace('\\','/'):
            assert all(initial.get(k)==new.get(k) for k in initial if k!='values')
            assert all(v in new['values'] for v in initial['values'])
            for v in new['values']:
                if v not in initial['values'] and v not in actual['values']:actual['values'].append(v)
        else:
            assert all(new.get(k)==v for k,v in initial.items()),'Existing JSON key changed: '+name
            for k,v in new.items():
                if k in initial:continue
                assert k not in actual or actual[k]==v,'JSON key collision: '+name+' '+k
                actual[k]=v
        writes[name]=(json.dumps(actual,indent=2,ensure_ascii=False)+'\n').encode('utf-8')
    else:
        assert previous.replace(b'\r\n',b'\n')==base.replace(b'\r\n',b'\n'),'Independent change in required source: '+name
        writes[name]=value
test='src/smoke/java/pro/erez/interstice/test/KotomyafGameTests.java';target=GAME/test
assert not target.exists() or target.read_bytes()==(CAND/test).read_bytes()
untouched={str(p.relative_to(GAME)):sha(p.read_bytes()) for p in (GAME/'src/main').rglob('*') if p.is_file() and str(p.relative_to(GAME)) not in writes}
patch=[]
for name,value in writes.items():
    if before[name]==value:continue
    label=name.replace('\\','/')
    patch.extend(difflib.unified_diff((before[name] or b'').decode('utf-8').splitlines(True),value.decode('utf-8').splitlines(True),fromfile='a/'+label if before[name] is not None else '/dev/null',tofile='b/'+label))
(ROOT/'reports/kotomyaf-only.patch').write_text(''.join(patch),encoding='utf-8')
for name,value in writes.items():
    target=GAME/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(value)
target=GAME/test;target.write_bytes((CAND/test).read_bytes())
for name,h in untouched.items():assert sha((GAME/name).read_bytes())==h,name
for dest in [ROOT/'exports'/jar.name,GAME/'build/libs'/jar.name]:shutil.copy2(jar,dest)
shutil.copy2(jar.with_name(jar.stem+'-sources.jar'),ROOT/'exports'/(jar.stem+'-sources.jar'))
result={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'version':proof['mod_version'],'geometry_revision':'KOTOMYAF-A04','visual_approved':True,
    'jar_sha256':sha(jar.read_bytes()),'jar_bytes':jar.stat().st_size,'component_evidence':str(component),'component_tests_passed':29,'cosmetic_changes_since_component_test':cosmetic_changes,'component_runtime_java_byte_identical':True,'assembly_evidence':str(assembly),'native_evidence':str(native),'native':record,
    'main_source_delta':changed,'integrated_files':list(writes)+[test],'unrelated_primary_runtime_files_preserved':len(untouched),
    'changed_previous_jar_entries':changed_old,'added_jar_entries':sorted(b.keys()-a.keys()),'previous_armor_geometry_unchanged':True,
    'owner_profile_used':False,'installed':False,'published':False,
    'limits':['Controlled native client test, not a long natural Survival playthrough','One real observer client with three server-connected mock players; not real multi-client capacity acceptance','Vanilla sound assets checked; arbitrary replacement resource-pack audio not exhaustively checked','Creepers manually ignited with flint and steel can still explode; trapped mobs need an escape path','Tail removed at explicit user request; previous tail proposals are historical only']}
for dest in [ROOT/'reports/accepted.json',GAME/'docs/verification/KOTOMYAF.json']:dest.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
doc=GAME/'docs/KOTOMYAF.md';documentation=(ROOT/'README.md').read_text(encoding='utf-8')
documentation=re.sub(r'\]\((source|preview|assembly|reports|exports)/([^)]*)\)',lambda m:']('+str(ROOT/m.group(1)/m.group(2)).replace('\\','/')+')',documentation)
doc.write_text(documentation+'\n\nПринято локально: 29 компонентных проверок и полный клиент с DH; [отчёт](verification/KOTOMYAF.json). Папка артефактов: `D:/repos/Blender/creations/14_kotomyaf_armor`.\n',encoding='utf-8')
handoff=GAME/'docs/HANDOFF.md';s=handoff.read_text(encoding='utf-8');head,rest=s.split('\n',1)
entry='\nЛокально 27.10.2026: добавлен **Котомяф A04**, утверждённые открытое лицо, ушки/усики/лапки; хвост удалён по последнему прямому поручению. JAR **0.7.0-preview.19-kotomyaf**. Полный комплект: нет обычного урона от падения, криперы и маленькие зомби избегают носителя в 8 блоках. Крафт — шерсть и рыба. Мяу/мурлыканье — штатные конечные звуки, очередь без обрыва при отпускании Shift. 29 GameTests прошли на тех же Java-исходниках и рецептах; косметические изменения A04 проверены новой сборкой и полноценным клиентом DH/106 модов, реальные бегство мобов и завершение мурлыканья проверены повторно, 13 исходных снимков. [Описание](KOTOMYAF.md), [проверка](verification/KOTOMYAF.json). Кандидат `.verification/kt01`; предыдущая броня и мечи сохранены, незавершённая фауна не включена. Изменения узко перенесены в исходники основной рабочей копии, её gradle.properties не менялся. На сервер и в игровые профили не устанавливалось.\n'
handoff.write_text(head+'\n'+entry+rest,encoding='utf-8')
with zipfile.ZipFile(ROOT/'exports/kotomyaf_A04_ingame.zip','w',zipfile.ZIP_DEFLATED) as z:
    for name,h in record['screenshot_sha256'].items():
        image=ROOT/'preview/native'/name;assert sha(image.read_bytes())==h;z.write(image,name)
    z.write(ROOT/'README.md','README.md');z.write(ROOT/'reports/accepted.json','verification.json')
    z.write(ROOT/'preview/native/README.md','gallery-index.md')
print(json.dumps({'version':result['version'],'jar_sha256':result['jar_sha256'],'integrated_files':len(result['integrated_files']),'preserved_unrelated':len(untouched),'screenshots':len(record['screenshot_sha256'])}))
