"""Owner-authorized alpha23 update; no world/account archives or owner-profile changes."""
from pathlib import Path
import argparse,hashlib,json,subprocess,shutil,zipfile
ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'.verification/updates-release-20261029'
REMOTE='/opt/minecraft/erezcraft/staging/updates-release-20261029'
OLD='interstice-0.7.0-preview.38-clock-grip.jar'
NEW='interstice-0.7.0-preview.98-skat-server.jar'
NAME='erezcraft-updates-check-20261029'
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
def ssh(code,timeout=120):
 p=subprocess.run(['ssh','-o','BatchMode=yes','-o','ConnectTimeout=10','minecraft','python3','-'],input=code,text=True,capture_output=True,encoding='utf8',timeout=timeout)
 if p.returncode:raise RuntimeError(p.stderr[-2200:])
 return p.stdout.strip()
def common():
 return "from pathlib import Path\nimport json,hashlib,subprocess,shutil,os,re,secrets\nroot=Path('/opt/minecraft/erezcraft');stage=Path("+repr(REMOTE)+");mods=root/'data/mods'\nOLD="+repr(OLD)+";NEW="+repr(NEW)+";NAME="+repr(NAME)+"\nassert stage.parent==root/'staging' and not stage.is_symlink()\nsha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()\nrun=lambda a:subprocess.check_output(a,text=True).strip()\ncontainer='erezcraft-minecraft-1'\n"
def record(name,result):
 value=json.loads(result);(STAGE/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps(value,ensure_ascii=False),flush=True)
def audit():
 info=json.loads((ROOT/'.verification/updates-release-input.json').read_text());proof=json.loads(Path(info['native']).read_text())
 assert info['passed'] and info['allUpdatesIncluded'] and info['modVersion']=='0.7.0-preview.98-skat-server'
 assert sha(info['jar'])==info['jarSha256']==proof['jar_sha256'] and proof['native']['passed'] and proof['cold']['passed']
 with zipfile.ZipFile(info['jar']) as new,zipfile.ZipFile(ROOT/'build/libs'/OLD) as old:
  preserved=[p for p in old.namelist() if p.endswith(('.png','.ogg')) or p.endswith('.class') and any(t in p for t in ['/worldgen/','/geometry/'])]
  assert all(old.read(p)==new.read(p) for p in preserved)
  must=['pro/erez/interstice/gear/EngineerBreathing.class','pro/erez/interstice/fauna/Lozhnokoren.class','pro/erez/interstice/ocean/LowerToxinSurface.class','pro/erez/interstice/ship/SkatPlayerLogin.class','data/interstice/skat/sk03.json']
  assert all(p in new.namelist() for p in must)
  result={'passed':True,'candidate':info['modVersion'],'jar_sha256':info['jarSha256'],'base':OLD,'old_png_ogg_worldgen_files_preserved':len(preserved),'new_files':len(set(new.namelist())-set(old.namelist())),'features':info['features'],'backups_created':False}
 record('input-audit.json',json.dumps(result))
def prepare():
 info=json.loads((ROOT/'.verification/updates-release-input.json').read_text());assert sha(info['jar'])==info['jarSha256']
 subprocess.run(['scp','-q','-o','BatchMode=yes',info['jar'],'minecraft:/tmp/erez-updates-preview97.jar'],check=True,timeout=90)
 output=ssh(common()+"info="+repr(info)+"\n"+r'''
assert not stage.exists(),'Do not overwrite a previous stage'
assert run(['docker','inspect','--format','{{.State.Health.Status}}',container])=='healthy'
before={p.name:sha(p) for p in mods.glob('*.jar')};assert len(before)==67 and before[OLD]==info['baseJarSha256']
source=Path('/tmp/erez-updates-preview97.jar');assert not source.is_symlink() and sha(source)==info['jarSha256']
stage.mkdir(mode=0o750);shutil.copy2(source,stage/NEW)
(stage/'before.json').write_text(json.dumps(before,indent=2));(stage/'candidate.json').write_text(json.dumps(info,indent=2))
(stage/'preserve.json').write_text(json.dumps({str(p.relative_to(root)):sha(p) for p in [root/'compose.json',root/'data/ops.json',root/'data/server.properties']}))
data=stage/'probe/data';data.mkdir(parents=True);(data/'.owned-updates-check').write_text('disposable; no production worlds or accounts\n')
(data/'mods').mkdir()
for p in mods.glob('*.jar'):
 if p.name!=OLD:shutil.copy2(p,data/'mods'/p.name)
shutil.copy2(stage/NEW,data/'mods'/NEW)
for name in ['.run-neoforge.env','.neoforge-manifest.json','user_jvm_args.txt','run.sh','run.bat']:
 if (root/'data'/name).is_file():shutil.copy2(root/'data'/name,data/name)
(data/'server.properties').write_text('online-mode=false\nenable-rcon=true\nwhite-list=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nlevel-name=owned-updates-check\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=1\nmotd=Owned updates check\ndifficulty=peaceful\nallow-flight=true\n')
env=stage/'probe/probe.env';env.write_text('EULA=TRUE\nTYPE=NEOFORGE\nVERSION=1.21.1\nNEOFORGE_VERSION=21.1.252\nMEMORY=3G\nONLINE_MODE=FALSE\nENABLE_WHITELIST=FALSE\nENABLE_RCON=TRUE\nRCON_PASSWORD='+secrets.token_urlsafe(32)+'\nLEVEL=owned-updates-check\nLEVEL_TYPE=minecraft:flat\nGENERATE_STRUCTURES=FALSE\nDIFFICULTY=peaceful\nMAX_PLAYERS=1\nSPAWN_PROTECTION=0\nVIEW_DISTANCE=2\nSIMULATION_DISTANCE=2\nALLOW_FLIGHT=TRUE\n');env.chmod(0o600)
image=json.loads((root/'compose.json').read_text())['services']['minecraft']['image'];assert '@sha256:' in image
(stage/'probe/image.txt').write_text(image)
assert before=={p.name:sha(p) for p in mods.glob('*.jar')}
print(json.dumps({'prepared':True,'server_jars':67,'production_unchanged':True,'owner_worlds_used':False,'backups_created':False}))
''')
 record('prepared.json',output)
def probe_start():
 print(ssh(common()+r'''
data=stage/'probe/data';assert (data/'.owned-updates-check').is_file()
assert len(list((data/'mods').glob('*.jar')))==67
assert sha(data/'mods'/NEW)==json.loads((stage/'candidate.json').read_text())['jarSha256']
subprocess.run(['docker','run','--detach','--pull','never','--name',NAME,'--memory','4g','--cpus','2','--publish','127.0.0.1:25567:25565','--env-file',str(stage/'probe/probe.env'),'--mount','type=bind,src='+str(data)+',dst=/data','--mount','type=bind,src='+str(root/'data/libraries')+',dst=/data/libraries,readonly',(stage/'probe/image.txt').read_text()],check=True,capture_output=True,text=True)
print('OWNED_LOOPBACK_UPDATES_PROBE_STARTED')
'''),flush=True)
def probe_status():
 output=ssh(common()+r'''
s=json.loads(run(['docker','inspect','--format','{{json .State}}',NAME]));log=stage/'probe/data/logs/latest.log'
result={'running':s['Running'],'exit':s['ExitCode'],'health':s.get('Health',{}).get('Status'),'owner_worlds_used':False,'production_changed':False}
if log.exists():
 text=log.read_text(errors='replace');result.update(done='Done (' in text,dh='Distant Horizons' in text,candidate='0.7.0-preview.98-skat-server' in text)
 result['tail']=text.splitlines()[-8:]
if result.get('done') and result['running'] and result['health']=='healthy':
 result['passed']=result['dh'] and result['candidate'];result['server_jars']=len(list((stage/'probe/data/mods').glob('*.jar')))
 (stage/'probe-verified.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result))
''')
 record('probe-status.json',output)
def probe_stop():
 output=ssh(common()+r'''
proof=json.loads((stage/'probe-verified.json').read_text());assert proof['passed']
subprocess.run(['docker','stop','-t','45',NAME],check=True,capture_output=True,text=True)
assert run(['docker','inspect','--format','{{.State.Status}}',NAME])=='exited'
print(json.dumps({**proof,'clean_stop':True,'production_unchanged':True,'scope':'dedicated full-pack startup, not multiplayer voyage'}))
''')
 record('dedicated-probe.json',output)
def install():
 output=ssh(common()+r'''
assert json.loads((stage/'probe-verified.json').read_text())['passed']
assert run(['docker','inspect','--format','{{.State.Health.Status}}',container])=='healthy'
players=run(['docker','exec',container,'rcon-cli','list']);assert re.search(r'There are 0 of a max of [0-9]+ players online:',players),'Players online'
before=json.loads((stage/'before.json').read_text());assert before=={p.name:sha(p) for p in mods.glob('*.jar')}
info=json.loads((stage/'candidate.json').read_text());assert info['passed'] and sha(stage/NEW)==info['jarSha256']
old=mods/OLD;new=mods/NEW;saved=stage/('previous-'+OLD);assert old.is_file() and not new.exists() and not saved.exists()
preserved=json.loads((stage/'preserve.json').read_text());assert sha(root/'data/server.properties')==preserved['data/server.properties']
properties=dict(line.split('=',1) for line in (root/'data/server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
(stage/'server-properties-before.json').write_text(json.dumps(properties,sort_keys=True))
subprocess.run(['docker','stop','-t','45',container],check=True,capture_output=True,text=True)
try:
 assert run(['docker','inspect','--format','{{.State.Status}}',container])=='exited'
 world=root/'data/erezcraft';assert (world/'level.dat').is_file();level_before=sha(world/'level.dat')
 pending=new.with_suffix('.pending');assert not pending.exists();shutil.copy2(stage/NEW,pending);assert sha(pending)==info['jarSha256'];pending.chmod(0o644)
 old.rename(saved);os.replace(pending,new)
 after={p.name:sha(p) for p in mods.glob('*.jar')};assert len(after)==67 and all(after[n]==h for n,h in before.items() if n!=OLD)
 assert sha(world/'level.dat')==level_before
 receipt={'passed':True,'old_jars_unchanged':66,'before':before,'after':after,'level_dat_unchanged_during_install':True,'backups_created':False}
 (stage/'mod-install.json').write_text(json.dumps(receipt,indent=2))
 subprocess.run(['docker','start',container],check=True,capture_output=True,text=True)
except BaseException:
 if new.exists():new.rename(stage/('failed-'+NEW))
 if saved.exists():saved.rename(old)
 subprocess.run(['docker','start',container],check=True,capture_output=True,text=True)
 raise
print(json.dumps({'installed':True,'server_jars':67,'backups_created':False,'world_regenerated':False,'starting':True}))
''')
 record('mod-install-status.json',output)
def accept():
 output=ssh(common()+r'''
s=json.loads(run(['docker','inspect','--format','{{json .State}}',container]));assert s['Running'] and s['Health']['Status']=='healthy'
text=(root/'data/logs/latest.log').read_text();assert 'Done (' in text and 'Distant Horizons' in text and '0.7.0-preview.98-skat-server' in text
before=json.loads((stage/'before.json').read_text());after={p.name:sha(p) for p in mods.glob('*.jar')};info=json.loads((stage/'candidate.json').read_text())
assert len(after)==67 and after[NEW]==info['jarSha256'];assert all(after[n]==h for n,h in before.items() if n!=OLD)
for rel,digest in json.loads((stage/'preserve.json').read_text()).items():
 if rel!='data/server.properties':assert sha(root/rel)==digest,rel
props=dict(l.split('=',1) for l in (root/'data/server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
assert props==json.loads((stage/'server-properties-before.json').read_text()),'Server setting values changed'
assert props['online-mode']=='true' and props['white-list']=='false'
ops=json.loads((root/'data/ops.json').read_text());assert len(ops)==1 and ops[0]['level']==4 and ops[0]['uuid']=='836c22ec-6f31-4dad-abaf-37fd0795f518'
assert (root/'data/erezcraft/level.dat').is_file();players=run(['docker','exec',container,'rcon-cli','list'])
result={'passed':True,'server_jars':67,'old_jars_unchanged':66,'mod_version':'0.7.0-preview.98-skat-server','jar_sha256':info['jarSha256'],'healthy':True,'online_auth_required':True,'owner_only_op4':True,'server_properties_unchanged':True,'world_regenerated':False,'player_worlds_replaced':False,'backups_created':False,'started_at':s['StartedAt'],'players':re.sub(r'\x1b\[[0-9;]*m','',players)}
(stage/'server-deployed.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
''')
 record('server-deployed.json',output)

def probe_diagnose():
 output=ssh(common()+r'''
log=stage/'probe/data/logs/latest.log';lines=log.read_text(errors='replace').splitlines()
errors=[]
for i,line in enumerate(lines):
 if any(t in line for t in ['ERROR','FATAL','Exception in server tick','Exception ticking']) and not any(t in line for t in ['LootDataType', 'No key layers']):errors.append(lines[max(0,i-2):i+24])
crashes=sorted((stage/'probe/data/crash-reports').glob('*.txt'),key=lambda p:p.stat().st_mtime)
print(json.dumps({'errors':errors[-4:],'crash_head':crashes[0].read_text(errors='replace').splitlines()[:100] if crashes else []}))
''')
 record('probe-diagnostic.json',output)


def probe_revise():
 info=json.loads((ROOT/'.verification/updates-release-input.json').read_text());assert info['modVersion']=='0.7.0-preview.98-skat-server' and sha(info['jar'])==info['jarSha256']
 subprocess.run(['scp','-q','-o','BatchMode=yes',info['jar'],'minecraft:/tmp/erez-updates-preview98.jar'],check=True,timeout=90)
 output=ssh(common()+"info="+repr(info)+"\n"+r'''
assert run(['docker','inspect','--format','{{.State.Status}}',NAME])=='exited'
old_info=json.loads((stage/'candidate.json').read_text());old_probe=stage/'probe/data/mods'/Path(old_info['jar']).name
assert sha(old_probe)==old_info['jarSha256']
source=Path('/tmp/erez-updates-preview98.jar');assert sha(source)==info['jarSha256']
shutil.copy2(source,stage/NEW)
pending=old_probe.with_suffix('.pending');assert not pending.exists();shutil.copy2(source,pending);assert sha(pending)==info['jarSha256'];pending.chmod(0o644)
os.replace(pending,old_probe);old_probe.rename(stage/'probe/data/mods'/NEW)
assert len(list((stage/'probe/data/mods').glob('*.jar')))==67
(stage/'candidate.json').write_text(json.dumps(info,indent=2))
assert json.loads((stage/'before.json').read_text())=={p.name:sha(p) for p in mods.glob('*.jar')}
subprocess.run(['docker','start',NAME],check=True,capture_output=True,text=True)
print(json.dumps({'revised':True,'candidate':info['modVersion'],'server_jars':67,'production_unchanged':True,'backups_created':False}))
''')
 record('probe-revised.json',output)


def ready():
 info=json.loads((ROOT/'.verification/updates-release-input.json').read_text());proof=json.loads(Path(info['native']).read_text());assert info['passed'] and proof['candidate']==info['modVersion'] and proof['jar_sha256']==info['jarSha256']
 ship=json.loads((STAGE/'dedicated-ship.json').read_text());assert ship['passed'] and ship['candidate']==info['modVersion']
 output=ssh(common()+"info="+repr(info)+"\n"+r'''
assert json.loads((stage/'probe-verified.json').read_text())['passed']
assert run(['docker','inspect','--format','{{.State.Health.Status}}',NAME])=='healthy'
assert json.loads((stage/'candidate.json').read_text())['jarSha256']==info['jarSha256']
assert sha(stage/NEW)==info['jarSha256']
(stage/'candidate.json').write_text(json.dumps(info,indent=2))
print(json.dumps({'ready':True,'candidate':info['modVersion'],'backups_created':False}))
''')
 record('ready.json',output)

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('phase',choices=['audit','prepare','probe_start','probe_revise','probe_status','probe_diagnose','probe_stop','ready','install','accept']);args=parser.parse_args();globals()[args.phase]()
