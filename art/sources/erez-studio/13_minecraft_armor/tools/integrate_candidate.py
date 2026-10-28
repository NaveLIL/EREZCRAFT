"""Apply only armor changes relative to the verified longsword candidate, preserving fauna work."""
from pathlib import Path
import argparse,datetime,difflib,hashlib,json,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
GAME=Path('D:/repos/Minecraft/EREZCRAFT');CANDIDATE=GAME/'.verification/armor_a01';OLD=GAME/'.verification/ls03'
sha=lambda b:hashlib.sha256(b).hexdigest()
parser=argparse.ArgumentParser();parser.add_argument('--evidence',required=True);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
evidence=CANDIDATE/args.evidence
summary=json.loads((evidence/'summary.json').read_text())
assert summary['passed'] and set(summary['completed_tasks'])=={'runProgressionGameTestServer','assemble','runProgressionNativeSmoke'}
baseline=json.loads((ROOT/'reports/integration_baseline/sha256.json').read_text())
freeze=json.loads((ROOT/'reports/final-code-freeze.json').read_text())['files']
for name,expected in freeze.items():assert sha((CANDIDATE/name).read_bytes())==expected,'Frozen candidate input changed: '+name
native=[]
task=json.loads((evidence/'runProgressionNativeSmoke.json').read_text())
run_root=Path(next(arg.split('=',1)[1] for arg in task['command'] if arg.startswith('-PintersticeRunRoot=')))
assert run_root.resolve().is_relative_to((CANDIDATE/'.verification').resolve())
for mode in ['create','reload']:
    report=run_root/'progressionNative'/f'progression-{mode}.json'
    data=json.loads(report.read_text())
    assert data==task['validations'][len(native)],'Runner and native evidence disagree'
    assert data['passed'] and data['three_armor_sets_native_rendered'] and data['magen_local_outline_and_night_vision']
    native.append({'path':str(report),'sha256':sha(report.read_bytes()),'data':data})
assert len(native)==2 and native[1]['data']['magen_cold_charge'] and native[0]['data']['pid']!=native[1]['data']['pid'],'Missing exact final cold proof'
changed=[];new=[];patch=[]
for top in ['src','tools','art/sources/cc-by/aegis']:
    for p in sorted((CANDIDATE/top).rglob('*')):
        if not p.is_file() or '__pycache__' in p.parts:continue
        name=p.relative_to(CANDIDATE).as_posix();old=OLD/name
        data=p.read_bytes()
        if old.exists():
            before=old.read_bytes()
            if before==data or before.replace(b'\r\n',b'\n')==data.replace(b'\r\n',b'\n'):continue
            assert name in baseline,'Unexpected modified candidate file: '+name
            assert sha((GAME/name).read_bytes())==baseline[name],'Primary file changed concurrently: '+name
            changed.append(name)
            patch.extend(difflib.unified_diff(before.decode('utf-8').replace('\r\n','\n').splitlines(keepends=True),
                data.decode('utf-8').replace('\r\n','\n').splitlines(keepends=True),fromfile='a/'+name,tofile='b/'+name))
        else:
            assert not (GAME/name).exists() or (GAME/name).read_bytes()==data,'New file already differs: '+name
            new.append(name)
            try:text=data.decode('utf-8').replace('\r\n','\n')
            except UnicodeDecodeError:continue
            if not text.endswith('\n'):text+='\n'
            patch.extend(difflib.unified_diff([],text.splitlines(keepends=True),fromfile='/dev/null',tofile='b/'+name))
doc='docs/ARMOR.md';assert not (GAME/doc).exists();new.append(doc)
patch.extend(difflib.unified_diff([],(CANDIDATE/doc).read_text(encoding='utf-8').splitlines(keepends=True),fromfile='/dev/null',tofile='b/'+doc))
patch_path=ROOT/'reports/armor-only.patch';patch_path.write_text(''.join(patch),encoding='utf-8',newline='\n')
subprocess.run(['git','apply','--check','--ignore-space-change',str(patch_path)],cwd=GAME,check=True)
jar=CANDIDATE/'build/libs/interstice-0.7.0-preview.17-armor.jar'
with zipfile.ZipFile(jar) as archive:
    for p in (CANDIDATE/'build/classes/java/main').rglob('*.class'):
        name=p.relative_to(CANDIDATE/'build/classes/java/main').as_posix();assert archive.read(name)==p.read_bytes(),'Packaged class mismatch: '+name
    assert archive.read('assets/interstice/armor/armor_design.json')==(CANDIDATE/'src/main/resources/assets/interstice/armor/armor_design.json').read_bytes()
    assert not any(n.startswith('assets/minecraft/textures/') for n in archive.namelist()),'Do not bundle Mojang reference textures'
report={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'candidate':str(CANDIDATE),'evidence':str(evidence),
    'version':'0.7.0-preview.17-armor','changed_existing':changed,'new_files':new,'patch_sha256':sha(patch_path.read_bytes()),
    'native':native,'component_game_tests':23,'runner_unit_tests':68,'jar_sha256':sha(jar.read_bytes()),
    'published':False,'owner_profile_modified':False,'unfinished_fauna_included':False,'applied':False}
if args.apply:
    subprocess.run(['git','apply','--ignore-space-change',str(patch_path)],cwd=GAME,check=True)
    for name in new:
        target=GAME/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(CANDIDATE/name,target)
    affected=set(changed)|set(new)
    for name,expected in baseline.items():
        if name in affected:continue
        assert (GAME/name).exists() and sha((GAME/name).read_bytes())==expected,'Unrelated input changed: '+name
    for dest in [ROOT/'exports'/jar.name,GAME/'build/libs'/jar.name]:dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(jar,dest)
    source_jar=jar.with_name(jar.stem+'-sources.jar');shutil.copy2(source_jar,ROOT/'exports'/source_jar.name)
    for name in changed+new:assert (GAME/name).read_bytes().replace(b'\r\n',b'\n')==(CANDIDATE/name).read_bytes().replace(b'\r\n',b'\n'),'Integrated payload differs: '+name
    report.update(applied=True,unrelated_baseline_inputs_unchanged=True,packaged_runtime_classes_match_tested_classes=True)
    verify=GAME/'docs/verification/ARMOR.json';verify.parent.mkdir(exist_ok=True);verify.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
(ROOT/'reports/integration.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k not in ['native','changed_existing','new_files']},ensure_ascii=False))
