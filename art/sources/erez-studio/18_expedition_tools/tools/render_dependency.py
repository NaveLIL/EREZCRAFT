import json,hashlib
from pathlib import Path
def digest(design):
    payload={'current':{n:{'elements':d['model']['elements'],'textures':d['model']['textures']} for n,d in design['models'].items()},
             'previous':design['previous'],'pixels':{n:hashlib.sha256(Path(p).read_bytes()).hexdigest() for n,p in design['textures'].items()}}
    return hashlib.sha256(json.dumps(payload,sort_keys=True).encode()).hexdigest()
if __name__=='__main__':
    root=Path(__file__).resolve().parents[1];d=json.loads((root/'source/tools_T01.json').read_text(encoding='utf-8'));p=root/'reports/blender.json';r=json.loads(p.read_text());r['rendered_dependency_sha256']=digest(d);p.write_text(json.dumps(r,indent=2)+'\n',encoding='utf-8');print(r['rendered_dependency_sha256'])
