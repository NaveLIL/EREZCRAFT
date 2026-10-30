"""Coordinated owner-requested Skat HUD/ocean fix. No world/account archives."""
from pathlib import Path
import json,subprocess,argparse,hashlib
from tools.deploy_updates import ssh
ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'.verification/skat-hud-release-20261029'
REMOTE='/opt/minecraft/erezcraft/staging/skat-hud-release-20261029'
OLD='interstice-0.7.0-preview.98-skat-server.jar'
NEW='interstice-0.7.0-preview.100-skat-speed.jar'
NAME='erezcraft-updates-check-20261029'
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
def common():
 return "from pathlib import Path\nimport json,hashlib,subprocess,os,re,shutil\nroot=Path('/opt/minecraft/erezcraft');stage=Path("+repr(REMOTE)+");mods=root/'data/mods';probe=root/'staging/updates-release-20261029/probe/data'\nOLD="+repr(OLD)+";NEW="+repr(NEW)+";NAME="+repr(NAME)+"\ncontainer='erezcraft-minecraft-1'\nassert stage.parent==root/'staging' and not stage.is_symlink()\nsha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()\nrun=lambda a:subprocess.check_output(a,text=True).strip()\n"
def record(name,value):
 r=json.loads(value);STAGE.mkdir(exist_ok=True);(STAGE/name).write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps(r,ensure_ascii=False),flush=True)
def prepare():
 info=json.loads((STAGE/'input.json').read_text(encoding='utf8'))
 assert info['passed'] and info['mod_version']=='0.7.0-preview.100-skat-speed' and sha(info['jar'])==info['jar_sha256']
 subprocess.run(['scp','-q','-o','BatchMode=yes',info['jar'],'minecraft:/tmp/erez-skat-hud100.jar'],check=True,timeout=90)
 record('prepared.json',ssh(common()+'info='+repr(info)+'\n'+r'''
assert run(['docker','inspect','--format','{{.State.Health.Status}}',container])=='healthy'
assert run(['docker','inspect','--format','{{.State.Status}}',NAME])=='exited'
assert (probe/'.owned-updates-check').is_file() and len(list(mods.glob('*.jar')))==67
assert sha(mods/OLD)=='2bf65b335c51b52b7c2ece95a29878a4e6d68c584328738e549d68e38676d2dd'
source=Path('/tmp/erez-skat-hud100.jar');assert not source.is_symlink() and sha(source)==info['jar_sha256']
assert not stage.exists();stage.mkdir(mode=0o750);shutil.copy2(source,stage/NEW)
(stage/'candidate.json').write_text(json.dumps(info,indent=2));(stage/'before.json').write_text(json.dumps({p.name:sha(p) for p in mods.glob('*.jar')},indent=2))
(stage/'preserve.json').write_text(json.dumps({str(p.relative_to(root)):sha(p) for p in [root/'compose.json',root/'data/ops.json']}))
props=dict(l.split('=',1) for l in (root/'data/server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
(stage/'properties.json').write_text(json.dumps(props,sort_keys=True))
assert (probe/'mods'/OLD).is_file() and not (probe/'mods'/NEW).exists()
(probe/'mods'/OLD).rename(stage/('previous-probe-'+OLD));shutil.copy2(stage/NEW,probe/'mods'/NEW)
assert len(list((probe/'mods').glob('*.jar')))==67
print(json.dumps({'prepared':True,'production_unchanged':True,'backups_created':False}))
'''))
def probe_start():
 print(ssh(common()+r'''
assert int(next(l.split()[1] for l in Path('/proc/meminfo').read_text().splitlines() if l.startswith('MemAvailable:')))>5*1024**2,'Preserve host RAM reserve'
assert sha(probe/'mods'/NEW)==json.loads((stage/'candidate.json').read_text())['jar_sha256']
subprocess.run(['docker','start',NAME],check=True,capture_output=True)
print('SKAT_HUD_PRIVATE_DEDICATED_STARTED')
'''))
def probe_status():
 record('probe.json',ssh(common()+r'''
s=json.loads(run(['docker','inspect','--format','{{json .State}}',NAME]));log=(probe/'logs/latest.log').read_text(errors='replace')
r={'running':s['Running'],'health':s.get('Health',{}).get('Status'),'candidate':'0.7.0-preview.100-skat-speed' in log,'dh':'Distant Horizons' in log,'done':'Done (' in log}
r['passed']=r['running'] and r['health']=='healthy' and r['candidate'] and r['dh'] and r['done']
if r['passed']:
 r['ships']=run(['docker','exec',NAME,'rcon-cli','execute in interstice:islands_v6 run sable info @e']);r['tps']=run(['docker','exec',NAME,'rcon-cli','neoforge tps interstice:islands_v6'])
 (stage/'probe-accepted.json').write_text(json.dumps(r))
else:r['tail']=log.splitlines()[-8:]
print(json.dumps(r))
'''))
def probe_stop():
 print(ssh(common()+r'''
assert json.loads((stage/'probe-accepted.json').read_text())['passed']
subprocess.run(['docker','stop','-t','45',NAME],check=True,capture_output=True)
assert run(['docker','inspect','--format','{{.State.Status}}',NAME])=='exited'
print('SKAT_HUD_PRIVATE_DEDICATED_STOPPED')
'''))
def install():
 record('installed.json',ssh(common()+r'''
assert json.loads((stage/'probe-accepted.json').read_text())['passed']
assert run(['docker','inspect','--format','{{.State.Status}}',NAME])=='exited'
players=run(['docker','exec',container,'rcon-cli','list']);assert re.search(r'There are 0 of a max of \d+ players online:',players),'Players online; do not interrupt owner'
before=json.loads((stage/'before.json').read_text());assert before=={p.name:sha(p) for p in mods.glob('*.jar')}
assert not (mods/NEW).exists() and not (stage/('previous-server-'+OLD)).exists()
subprocess.run(['docker','stop','-t','45',container],check=True,capture_output=True)
world=root/'data/erezcraft';level_before=sha(world/'level.dat')
try:
 shutil.copy2(stage/NEW,mods/NEW);assert sha(mods/NEW)==json.loads((stage/'candidate.json').read_text())['jar_sha256'];(mods/NEW).chmod(0o644)
 (mods/OLD).rename(stage/('previous-server-'+OLD))
 assert len(list(mods.glob('*.jar')))==67 and all(sha(mods/n)==h for n,h in before.items() if n!=OLD)
 assert sha(world/'level.dat')==level_before
 subprocess.run(['docker','start',container],check=True,capture_output=True)
except BaseException:
 if (mods/NEW).exists():(mods/NEW).rename(stage/('failed-'+NEW))
 if (stage/('previous-server-'+OLD)).exists():(stage/('previous-server-'+OLD)).rename(mods/OLD)
 subprocess.run(['docker','start',container],check=True,capture_output=True);raise
print(json.dumps({'installed':True,'old_jars_unchanged':66,'world_regenerated':False,'world_files_replaced':False,'level_dat_unchanged_during_install':True,'backups_created':False}))
'''))
def accept():
 record('server-deployed.json',ssh(common()+r'''
assert run(['docker','inspect','--format','{{.State.Health.Status}}',container])=='healthy'
log=(root/'data/logs/latest.log').read_text();assert '0.7.0-preview.100-skat-speed' in log and 'Done (' in log and 'Distant Horizons' in log
for name,h in json.loads((stage/'preserve.json').read_text()).items():assert sha(root/name)==h
props=dict(l.split('=',1) for l in (root/'data/server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'));assert props==json.loads((stage/'properties.json').read_text())
before=json.loads((stage/'before.json').read_text());assert len(list(mods.glob('*.jar')))==67 and all(sha(mods/n)==h for n,h in before.items() if n!=OLD)
digest=sha(mods/NEW);assert digest==json.loads((stage/'candidate.json').read_text())['jar_sha256']
r={'passed':True,'mod_version':'0.7.0-preview.100-skat-speed','jar_sha256':digest,'server_jars':67,'old_jars_unchanged':66,'healthy':True,'world_regenerated':False,'world_files_replaced':False,'backups_created':False,'settings_auth_ops_preserved':True,'players':run(['docker','exec',container,'rcon-cli','list'])}
(stage/'server-deployed.json').write_text(json.dumps(r,indent=2));print(json.dumps(r))
'''))
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['prepare','probe_start','probe_status','probe_stop','install','accept']);args=parser.parse_args();globals()[args.phase]()
