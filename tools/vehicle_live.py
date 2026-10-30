from tools.deploy_updates import ssh
from pathlib import Path
import json
code="""from pathlib import Path
import subprocess,json,hashlib
r=Path('/opt/minecraft/erezcraft');c='erezcraft-minecraft-1'
def cmd(s):
 q=subprocess.run(['docker','exec',c,'rcon-cli',s],text=True,capture_output=True);return {'code':q.returncode,'output':q.stdout.strip(),'error':q.stderr.strip()}
x={'health':subprocess.check_output(['docker','inspect','--format','{{.State.Health.Status}}',c],text=True).strip(),'players':cmd('list'),'tps':cmd('neoforge tps'),'ships':cmd('execute in interstice:islands_v6 run sable info @e'),'mods':[p.name for p in (r/'data/mods').glob('*interstice*')],'owner':json.loads((r/'data/ops.json').read_text()),'containers':subprocess.check_output(['docker','ps','--format','{{.Names}}'],text=True).splitlines()}
print(json.dumps(x))
"""
x=json.loads(ssh(code));Path('.verification/vehicle-release-20261030/baseline.json').write_text(json.dumps(x,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps({k:v for k,v in x.items() if k!='owner'},ensure_ascii=False))
