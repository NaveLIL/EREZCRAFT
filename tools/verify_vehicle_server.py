"""Temporary marked fixtures on the actual server; never player vehicles."""
from pathlib import Path
import argparse,json,subprocess,hashlib
from tools.deploy_updates import ssh
from tools.deploy_vehicle_fleet import common,STAGE,NAME,REMOTE
p=argparse.ArgumentParser();p.add_argument('phase',choices=['prepare','start','status','remove']);p.add_argument('--probe',action='store_true');a=p.parse_args()
addon=STAGE/'erez-vehicle-verify.jar';digest=hashlib.sha256(addon.read_bytes()).hexdigest()
previous=json.loads((STAGE/('probe-prepare.json' if a.probe else 'server-prepare.json')).read_text()) if (STAGE/('probe-prepare.json' if a.probe else 'server-prepare.json')).exists() else {}
head=common()+'previous='+repr(previous)+'\n'+"target="+repr(NAME if a.probe else 'erezcraft-minecraft-1')+"\ndata=probe if target==NAME else root/'data'\naddon=data/'mods/erez-vehicle-verify.jar';folder=data/'vehicle-verification';expected="+repr(digest)+"\n"
if a.phase=='prepare':
 subprocess.run(['scp','-q','-o','BatchMode=yes',str(addon),'minecraft:/tmp/erez-vehicle-verify.jar'],check=True,timeout=90)
 code=r'''
assert run(['docker','inspect','--format','{{.State.Status}}',target])=='exited','Install temporary addon only while stopped'
if addon.exists():
 assert target==NAME and sha(addon)==previous['temporary_addon_sha256'] and (folder/'.owned').is_file()
 result=json.loads((folder/'result.json').read_text());assert result.get('test_bodies_removed') or target==NAME and result.get('phase')==4 and 'eitan_uuid' in result and 'skat_uuid' in result
 (folder/'result.json').rename(folder/('result-'+str(len(list(folder.glob('result-*.json'))))+'.json') if result.get('test_bodies_removed') else folder/'result-pending-recovery.json')
else:assert not folder.exists()
source=Path('/tmp/erez-vehicle-verify.jar');assert sha(source)==expected
shutil.copy2(source,addon);addon.chmod(0o644);folder.mkdir(mode=0o750,exist_ok=True);(folder/'.owned').write_text('owner-authorized vehicle test 20261030')
print(json.dumps({'prepared':True,'target':target,'temporary_addon_sha256':expected}))
'''
elif a.phase=='start':
 code=r'''
assert sha(addon)==expected and (folder/'.owned').is_file()
assert run(['docker','inspect','--format','{{.State.Health.Status}}',target])=='healthy'
players=run(['docker','exec',target,'rcon-cli','list']);assert re.search(r'There are 0 of a max of \d+ players online:',players),players
print(json.dumps({'started':True,'target':target,'rcon':run(['docker','exec',target,'rcon-cli','fleetverify start'])}))
'''
elif a.phase=='status':
 code=r'''
path=folder/'result.json';r=json.loads(path.read_text()) if path.exists() else {'pending':True}
r['target']=target;r['health']=run(['docker','inspect','--format','{{.State.Health.Status}}',target])
r['players']=run(['docker','exec',target,'rcon-cli','list'])
r['docker_stats']=json.loads(run(['docker','stats','--no-stream','--format','{{json .}}',target]))
r['neoforge_tps']=run(['docker','exec',target,'rcon-cli','neoforge tps'])
if 'passed' in r:
 (stage/('probe-benchmark.json' if target==NAME else 'server-benchmark.json')).write_text(json.dumps(r,indent=2))
print(json.dumps(r))
'''
else:
 code=r'''
assert run(['docker','inspect','--format','{{.State.Status}}',target])=='exited'
assert sha(addon)==expected and (folder/'.owned').is_file()
r=json.loads((folder/'result.json').read_text());assert r['test_bodies_removed'] and r['passed'],r
addon.unlink();(folder/'.owned').unlink()
print(json.dumps({'temporary_addon_removed':True,'test_bodies_removed':True,'target':target}))
'''
value=ssh(head+code);r=json.loads(value);STAGE.mkdir(exist_ok=True)
(STAGE/(('probe-' if a.probe else 'server-')+a.phase+'.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
if a.phase=='status' and 'passed' in r:(STAGE/('probe-benchmark.json' if a.probe else 'server-benchmark.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(r,ensure_ascii=False))
