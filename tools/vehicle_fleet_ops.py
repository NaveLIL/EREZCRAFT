"""Bounded fleet maintenance. No player messages, world copies or generic deletion."""
import argparse,json
from tools.deploy_vehicle_fleet import common,STAGE,NAME
from tools.deploy_updates import ssh
p=argparse.ArgumentParser();p.add_argument('phase',choices=['status','recover','adopt','stop','start']);p.add_argument('--probe',action='store_true');a=p.parse_args()
head=common()+"target="+repr(NAME if a.probe else 'erezcraft-minecraft-1')+"\ndata=probe if target==NAME else root/'data'\n"
if a.phase=='status':
 code=r'''
s=json.loads(run(['docker','inspect','--format','{{json .State}}',target]));r={'running':s['Running'],'health':s.get('Health',{}).get('Status'),'exit':s['ExitCode']}
if r['running'] and r['health']=='healthy':r['players']=run(['docker','exec',target,'rcon-cli','list'])
else:r['tail']=(data/'logs/latest.log').read_text(errors='replace').splitlines()[-5:]
print(json.dumps(r))
'''
elif a.phase=='recover':
 code=r'''
assert target==NAME and (data/'vehicle-verification/.owned').is_file()
assert run(['docker','inspect','--format','{{.State.Health.Status}}',target])=='healthy'
assert 'There are 0 ' in run(['docker','exec',target,'rcon-cli','list'])
print(json.dumps({'command':run(['docker','exec',target,'rcon-cli','fleetverify recover']),'record_recovered':(data/'vehicle-verification/result-crash-recovered.json').is_file()}))
'''
elif a.phase=='adopt':
 code=r'''
assert target!=NAME
ops=json.loads((data/'ops.json').read_text());assert len(ops)==1 and ops[0]['level']==4
out=run(['docker','exec',target,'rcon-cli','erezvehicle admin adopt '+ops[0]['uuid']]);assert 'Legacy vehicle ownership assigned' in out,out
print(json.dumps({'legacy_ownership_assigned':True,'existing_owners_preserved':True,'loaded':run(['docker','exec',target,'rcon-cli','erezvehicle admin list'])}))
'''
elif a.phase=='stop':
 code=r'''
assert 'There are 0 ' in run(['docker','exec',target,'rcon-cli','list'])
subprocess.run(['docker','stop','-t','45',target],check=True,capture_output=True)
print(json.dumps({'stopped':True,'target':target}))
'''
else:
 code=r'''
assert run(['docker','inspect','--format','{{.State.Status}}',target])=='exited'
subprocess.run(['docker','start',target],check=True,capture_output=True)
print(json.dumps({'started':True,'target':target}))
'''
r=json.loads(ssh(head+code));(STAGE/(('probe-' if a.probe else 'server-')+'ops-'+a.phase+'.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps(r,ensure_ascii=False))
