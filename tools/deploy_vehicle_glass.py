"""Roll forward the client-only glass correction after the measured fleet120."""
from pathlib import Path
import json,zipfile,hashlib,argparse,subprocess
from tools.deploy_vehicle_fleet import common,STAGE,NAME,REMOTE
from tools.deploy_updates import ssh
p=argparse.ArgumentParser();p.add_argument('phase',choices=['prepare','probe_start','probe_status','probe_stop','install','accept']);a=p.parse_args()
OLD='interstice-0.7.0-preview.120-vehicle-fleet.jar';NEW='interstice-0.7.0-preview.121-vehicle-fleet.jar';sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
info=json.loads((STAGE/'input.json').read_text());assert info['mod_version']=='0.7.0-preview.121-vehicle-fleet' and info['packaged'] and sha(info['jar'])==info['jar_sha256']
base=STAGE/OLD;assert sha(base)=='240e4ca7f969da933e6c7d3447eaf7afc4792616b4a7c180ca3dfe596369204d'
with zipfile.ZipFile(base) as before,zipfile.ZipFile(info['jar']) as after:
 added=set(after.namelist())-set(before.namelist());assert added=={'pro/erez/interstice/client/SkatGlassSprite.class'},added
 changed={n for n in before.namelist() if before.read(n)!=after.read(n)};assert changed=={'pro/erez/interstice/client/SkatModels.class','META-INF/neoforge.mods.toml'},changed
head=common()+'OLD='+repr(OLD)+';NEW='+repr(NEW)+';info='+repr(info)+'\n'
if a.phase=='prepare':
 subprocess.run(['scp','-q','-o','BatchMode=yes',info['jar'],'minecraft:/tmp/erez-vehicle-glass121.jar'],check=True,timeout=90)
 code=r'''
assert run(['docker','inspect','--format','{{.State.Status}}',NAME])=='exited'
assert len(list(mods.glob('*.jar')))==67 and sha(mods/OLD)=='240e4ca7f969da933e6c7d3447eaf7afc4792616b4a7c180ca3dfe596369204d'
source=Path('/tmp/erez-vehicle-glass121.jar');assert sha(source)==info['jar_sha256']
assert (probe/'mods'/OLD).is_file() and not (probe/'mods/erez-vehicle-verify.jar').exists()
shutil.copy2(source,stage/NEW);shutil.copy2(source,probe/'mods'/NEW);(probe/'mods'/OLD).unlink()
(stage/'glass-candidate.json').write_text(json.dumps(info));print(json.dumps({'prepared':True,'server_runtime_classes_unchanged':True}))
'''
elif a.phase=='probe_start':
 code=r'''
assert run(['docker','inspect','--format','{{.State.Status}}',NAME])=='exited'
assert int(next(l.split()[1] for l in Path('/proc/meminfo').read_text().splitlines() if l.startswith('MemAvailable:')))>5*1024**2
subprocess.run(['docker','start',NAME],check=True,capture_output=True);print(json.dumps({'started':True}))
'''
elif a.phase=='probe_status':
 code=r'''
s=json.loads(run(['docker','inspect','--format','{{json .State}}',NAME]));log=(probe/'logs/latest.log').read_text(errors='replace');r={'running':s['Running'],'healthy':s.get('Health',{}).get('Status')=='healthy','version':'0.7.0-preview.121-vehicle-fleet' in log,'dh':'Distant Horizons' in log,'done':'Done (' in log};r['passed']=all(r.values())
if r['passed']:(stage/'glass-probe.json').write_text(json.dumps(r))
else:r['tail']=log.splitlines()[-4:]
print(json.dumps(r))
'''
elif a.phase=='probe_stop':
 code=r'''
assert json.loads((stage/'glass-probe.json').read_text())['passed']
subprocess.run(['docker','stop','-t','45',NAME],check=True,capture_output=True);print(json.dumps({'stopped':True}))
'''
elif a.phase=='install':
 assert info['passed']
 code=r'''
assert info['passed'] and json.loads((stage/'glass-probe.json').read_text())['passed']
assert run(['docker','inspect','--format','{{.State.Status}}',NAME])=='exited'
assert re.search(r'There are 0 of a max of \d+ players online:',run(['docker','exec',container,'rcon-cli','list']))
assert len(list(mods.glob('*.jar')))==67 and sha(mods/OLD)=='240e4ca7f969da933e6c7d3447eaf7afc4792616b4a7c180ca3dfe596369204d'
assert sha(stage/NEW)==info['jar_sha256'] and not (mods/NEW).exists()
subprocess.run(['docker','stop','-t','45',container],check=True,capture_output=True);level_before=sha(root/'data/erezcraft/level.dat')
try:
 shutil.copy2(stage/NEW,mods/NEW);(mods/NEW).chmod(0o644);(mods/OLD).unlink()
 assert sha(root/'data/erezcraft/level.dat')==level_before
 subprocess.run(['docker','start',container],check=True,capture_output=True)
except BaseException:
 if (mods/NEW).exists():(mods/NEW).unlink()
 shutil.copy2(stage/OLD,mods/OLD);subprocess.run(['docker','start',container],check=True,capture_output=True);raise
print(json.dumps({'installed':True,'world_files_replaced':False,'backups_created':False,'client_only_delta':True}))
'''
else:
 assert info['passed']
 code=r'''
assert run(['docker','inspect','--format','{{.State.Health.Status}}',container])=='healthy'
log=(root/'data/logs/latest.log').read_text();assert '0.7.0-preview.121-vehicle-fleet' in log and 'Done (' in log and 'Distant Horizons' in log
for name,h in json.loads((stage/'preserve.json').read_text()).items():assert sha(root/name)==h
props=dict(l.split('=',1) for l in (root/'data/server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'));assert props==json.loads((stage/'properties.json').read_text())
before=json.loads((stage/'before.json').read_text());assert len(list(mods.glob('*.jar')))==67 and all(sha(mods/n)==h for n,h in before.items() if not n.startswith('interstice-'))
assert sha(mods/NEW)==info['jar_sha256'] and not (mods/'erez-vehicle-verify.jar').exists()
trial=json.loads((stage/'server-benchmark.json').read_text());assert trial['passed'] and trial['test_bodies_removed'] and trial['new_forced_chunks_removed']==177
r={'passed':True,'mod_version':'0.7.0-preview.121-vehicle-fleet','jar_sha256':info['jar_sha256'],'server_jars':67,'old_jars_unchanged':66,'healthy':True,'world_regenerated':False,'world_files_replaced':False,'backups_created':False,'settings_auth_ops_preserved':True,'server_runtime_classes_identical_to_measured_120':True,'players':run(['docker','exec',container,'rcon-cli','list'])}
if not (stage/'server-deployed-120.json').exists():(stage/'server-deployed.json').rename(stage/'server-deployed-120.json')
(stage/'server-deployed.json').write_text(json.dumps(r,indent=2));print(json.dumps(r))
'''
r=json.loads(ssh(head+code));(STAGE/('glass-'+a.phase+'.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
if a.phase=='accept':
 if not (STAGE/'server-deployed-120.json').exists():(STAGE/'server-deployed.json').rename(STAGE/'server-deployed-120.json')
 (STAGE/'server-deployed.json').write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps(r,ensure_ascii=False))
