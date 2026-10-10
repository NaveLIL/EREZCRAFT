"""Resolve the authorized technology/QoL batch without changing the accepted baseline."""
from pathlib import Path
import concurrent.futures
import datetime
import hashlib
import io
import json
import tomllib
import urllib.parse
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT/'.verification/tech-quality-research';CACHE.mkdir(parents=True,exist_ok=True)
QUERY=urllib.parse.urlencode({'game_versions':json.dumps(['1.21.1']),'loaders':json.dumps(['neoforge'])})
existing=json.loads((ROOT/'distribution/server-foundation.lock.json').read_text(encoding='utf-8'))['mods']+json.loads((ROOT/'distribution/techmagic-spine.lock.json').read_text(encoding='utf-8'))['required_spine']
present={m.get('project_id',m.get('modrinth_project_id')) for m in existing}
def api(path):
    req=urllib.request.Request('https://api.modrinth.com/v2/'+path,headers={'User-Agent':'EREZCRAFT-quality-batch/0.1'})
    with urllib.request.urlopen(req,timeout=40) as response:return json.load(response)
def metadata(data):
    result=[]
    def scan(z):
        for name in ['META-INF/neoforge.mods.toml','META-INF/mods.toml']:
            if name in z.namelist():result.append(tomllib.loads(z.read(name).decode('utf-8')))
        for name in z.namelist():
            if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                with zipfile.ZipFile(io.BytesIO(z.read(name))) as nested:scan(nested)
    with zipfile.ZipFile(io.BytesIO(data)) as z:scan(z)
    return result
def resolve(task):
    slug,role,forced=task;project=api('project/'+slug)
    if forced:version=api('version/'+forced)
    else:
        versions=api('project/'+project['id']+'/version?'+QUERY)
        versions=[v for v in versions if v['version_type']=='release']
        if slug=='mekanism-generators':versions=[v for v in versions if v['version_number']=='10.7.19.85']
        assert versions,'No stable matching version: '+slug
        version=versions[0]
    assert '1.21.1' in version['game_versions'] and 'neoforge' in version['loaders']
    file=next(f for f in version['files'] if f['primary']);path=ROOT/'research/dependencies'/file['filename']
    if not path.exists():
        with urllib.request.urlopen(file['url'],timeout=120) as response:data=response.read()
        assert hashlib.sha512(data).hexdigest()==file['hashes']['sha512'];path.write_bytes(data)
    data=path.read_bytes();assert hashlib.sha512(data).hexdigest()==file['hashes']['sha512']
    meta=metadata(data)
    (CACHE/(project['id']+'-metadata.json')).write_text(json.dumps(meta,indent=2)+'\n',encoding='utf-8')
    return {'project':project['slug'],'project_id':project['id'],'role':role,'version':version['version_number'],'version_id':version['id'],
        'filename':file['filename'],'bytes':len(data),'sha512':file['hashes']['sha512'],'sha256':hashlib.sha256(data).hexdigest(),
        'download_url':file['url'],'license':project['license'],'api_dependencies':version['dependencies'],
        'mod_ids':{m['modId']:str(m['version']) for record in meta for m in record.get('mods',[])},
        'actual_dependencies':[dict(dep,owner=owner) for record in meta for owner,deps in record.get('dependencies',{}).items() for dep in deps],
        'runtime_verified':False}
pending=[('jei','recipes',None),('jade','inspection',None),('mekanism-generators','power_generation',None),
    ('createaddition','create_electric_bridge',None),('storagedrawers','early_storage',None),('mouse-tweaks','client_inventory',None),('crafting-tweaks','crafting_qol',None)]
resolved={}
while pending:
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:rows=list(pool.map(resolve,pending))
    pending=[]
    for row in rows:
        resolved[row['project_id']]=row
        for dep in row['api_dependencies']:
            if dep['dependency_type']=='required' and dep.get('project_id') not in present|resolved.keys():
                assert dep.get('project_id'),'Unidentified required dependency'
                if not any(p[0]==dep['project_id'] for p in pending):pending.append((dep['project_id'],'library',dep.get('version_id')))
        print(json.dumps({'resolved':row['project'],'version':row['version'],'mod_ids':row['mod_ids']}),flush=True)
    assert len(resolved)<=15,'Unexpected dependency expansion'
lock={'schema':1,'minecraft':'1.21.1','neoforge':'21.1.252','checked_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'scope':'Authorized tech/QoL additions; artifacts and metadata verified, runtime acceptance pending','mods':list(resolved.values()),'runtime_verified':False}
(ROOT/'distribution/tech-quality.lock.json').write_text(json.dumps(lock,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'lock':'distribution/tech-quality.lock.json','components':len(resolved)}))
