"""Owner-authorized Blade resource update. No world archives or PC backups."""
from pathlib import Path
import argparse,hashlib,json,subprocess

ROOT=Path('D:/repos/Minecraft/EREZCRAFT')
STAGE=ROOT/'.verification/blade-release-20261028'
REMOTE='/opt/minecraft/erezcraft/staging/blade-release-20261028'
OLD='interstice-0.7.0-preview.35-fauna-studio.jar'
NEW='interstice-0.7.0-preview.36-blade-tools.jar'

def ssh(code):
    result=subprocess.run(['ssh','-o','BatchMode=yes','minecraft','python3','-'],
        input=code,text=True,capture_output=True,encoding='utf8',timeout=180)
    if result.returncode:raise RuntimeError(result.stderr[-2400:])
    return result.stdout.strip()

def common():
    return """from pathlib import Path
import json,hashlib,subprocess,shutil,os,re
root=Path('/opt/minecraft/erezcraft');stage=Path(REMOTE);mods=root/'data/mods'
assert stage.parent==root/'staging' and not stage.is_symlink()
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
run=lambda a:subprocess.check_output(a,text=True).strip()
old=mods/OLD;new=mods/NEW;container='erezcraft-minecraft-1'
""".replace('REMOTE',repr(REMOTE)).replace('OLD',repr(OLD)).replace('NEW',repr(NEW))

def prepare():
    info=json.loads((ROOT/'.verification/blade-release-input.json').read_text())
    assert info['passed'] and info['bladeIncluded'] and info['modVersion']=='0.7.0-preview.36-blade-tools'
    assert hashlib.sha256(Path(info['jar']).read_bytes()).hexdigest()==info['jarSha256']
    for proof in ['components','native']:assert json.loads(Path(info[proof]).read_text())['passed'] is True
    subprocess.run(['scp','-q','-o','BatchMode=yes',info['jar'],'minecraft:/tmp/erez-blade-20261028.jar'],check=True)
    result=ssh(common()+"""info=INFO
assert not stage.exists(),'Never overwrite a deployment stage'
assert run(['docker','inspect','--format','{{.State.Health.Status}}',container])=='healthy'
before={p.name:sha(p) for p in mods.glob('*.jar')}
assert len(before)==67 and before[old.name]==info['baseJarSha256']
source=Path('/tmp/erez-blade-20261028.jar');assert not source.is_symlink() and sha(source)==info['jarSha256']
stage.mkdir(mode=0o750);shutil.copy2(source,stage/new.name)
(stage/'candidate.json').write_text(json.dumps(info,indent=2))
(stage/'before.json').write_text(json.dumps(before,indent=2))
(stage/'preserve.json').write_text(json.dumps({str(p.relative_to(root)):sha(p) for p in [root/'compose.json',root/'data/ops.json']}))
print('BLADE_SERVER_STAGE_READY_NO_BACKUPS')
""".replace('INFO',repr(info)))
    print(result,flush=True)

def install():
    result=ssh(common()+"""assert stage.is_dir()
assert run(['docker','inspect','--format','{{.State.Health.Status}}',container])=='healthy'
players=run(['docker','exec',container,'rcon-cli','list'])
assert re.search(r'There are 0 of a max of [0-9]+ players online:',players),'Players online; do not interrupt them'
before=json.loads((stage/'before.json').read_text());assert before=={p.name:sha(p) for p in mods.glob('*.jar')}
info=json.loads((stage/'candidate.json').read_text());assert sha(stage/new.name)==info['jarSha256']
saved=stage/('previous-'+old.name)
assert old.is_file() and not new.exists() and not saved.exists()
subprocess.run(['docker','stop','-t','45',container],check=True,stdout=subprocess.DEVNULL)
try:
 assert run(['docker','inspect','--format','{{.State.Status}}',container])=='exited'
 world=root/'data/erezcraft';assert (world/'level.dat').is_file()
 level_before=sha(world/'level.dat')
 pending=new.with_suffix('.pending');assert not pending.exists()
 shutil.copy2(stage/new.name,pending);assert sha(pending)==info['jarSha256'];pending.chmod(0o644)
 old.rename(saved);os.replace(pending,new)
 after={p.name:sha(p) for p in mods.glob('*.jar')}
 assert len(after)==67 and all(after[n]==h for n,h in before.items() if n!=old.name)
 assert sha(world/'level.dat')==level_before
 (stage/'mod-install.json').write_text(json.dumps({'passed':True,'old_jars_unchanged':66,'before':before,'after':after,'level_dat_unchanged_during_install':True,'backups_created':False},indent=2))
 subprocess.run(['docker','start',container],check=True,stdout=subprocess.DEVNULL)
except BaseException:
 if new.exists():new.rename(stage/('failed-'+new.name))
 if saved.exists():saved.rename(old)
 subprocess.run(['docker','start',container],check=True,stdout=subprocess.DEVNULL)
 raise
print('BLADE_SERVER_STARTING_67_JARS_NO_BACKUPS')
""")
    print(result,flush=True)

def accept():
    result=ssh(common()+"""state=json.loads(run(['docker','inspect','--format','{{json .State}}',container]))
assert state['Running'] and state['Health']['Status']=='healthy'
log=(root/'data/logs/latest.log').read_text();assert 'Done (' in log and 'Distant Horizons' in log and '0.7.0-preview.36-blade-tools' in log
before=json.loads((stage/'before.json').read_text());after={p.name:sha(p) for p in mods.glob('*.jar')};info=json.loads((stage/'candidate.json').read_text())
assert len(after)==67 and after[new.name]==info['jarSha256']
assert all(after[n]==h for n,h in before.items() if n!=old.name)
for rel,h in json.loads((stage/'preserve.json').read_text()).items():assert sha(root/rel)==h,rel
props=dict(l.split('=',1) for l in (root/'data/server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
assert props['online-mode']=='true' and props['white-list']=='false'
ops=json.loads((root/'data/ops.json').read_text());assert len(ops)==1 and ops[0]['level']==4 and ops[0]['uuid']=='836c22ec-6f31-4dad-abaf-37fd0795f518'
assert (root/'data/erezcraft/level.dat').is_file()
result={'passed':True,'server_jars':67,'old_jars_unchanged':66,'mod_version':'0.7.0-preview.36-blade-tools','healthy':True,'online_auth_required':True,'owner_only_op4':True,'world_regenerated':False,'player_worlds_replaced':False,'backups_created':False,'started_at':state['StartedAt']}
(stage/'server-deployed.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
""")
    proof=json.loads(result);(STAGE/'server-deployed.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
    print(result,flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('phase',choices=['prepare','install','accept'])
    args=parser.parse_args();globals()[args.phase]()
