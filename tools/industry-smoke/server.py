"""Prepare a disposable loopback-only dedicated candidate on the home server.

Never copies a production world, account database, operations list or environment.
Original installed mod binaries are read-only inputs. Stop only the named probe.
"""
from pathlib import Path
import hashlib, json, subprocess, time

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'.verification/mod-candidates-20261024'
NAME='erezcraft-industry-check'

def ssh(code):
    p=subprocess.run(['ssh','-o','BatchMode=yes','minecraft','python3','-'],
                     input=code,text=True,capture_output=True,timeout=60,encoding='utf-8')
    if p.returncode:raise RuntimeError(p.stderr[-1200:])
    return p.stdout

lock=json.loads((WORK/'candidate-lock.json').read_text('utf-8'))
files={r['filename']:r['sha256'] for r in lock['mods'].values()}
kotlin=WORK/'client/mods/kotlinforforge-5.12.0-all.jar'
if not kotlin.exists():
    kotlin=next((WORK/'client/mods').glob('kotlinforforge-*.jar'))
files[kotlin.name]=hashlib.sha256(kotlin.read_bytes()).hexdigest()
setup=r'''
from pathlib import Path
import subprocess,shutil,secrets,json
root=Path('/opt/minecraft/erezcraft');stage=root/'staging/industry-check-20261024';data=stage/'data'
assert not stage.exists(), 'Never replace an earlier test'
data.mkdir(parents=True)
shutil.copytree(root/'data/mods',data/'mods')
for name in ['.run-neoforge.env','.neoforge-manifest.json','user_jvm_args.txt','run.sh','run.bat']:
 if (root/'data'/name).is_file():shutil.copy2(root/'data'/name,data/name)
(data/'.owned-industry-check').write_text('disposable-loopback-only\n')
(data/'server.properties').write_text('online-mode=false\nenable-rcon=true\nwhite-list=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nlevel-name=owned-industry-check\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=1\nmotd=Owned isolated industry check\ndifficulty=peaceful\n')
env=stage/'probe.env'
env.write_text('EULA=TRUE\nTYPE=NEOFORGE\nVERSION=1.21.1\nNEOFORGE_VERSION=21.1.252\nMEMORY=3G\nONLINE_MODE=FALSE\nENABLE_WHITELIST=FALSE\nENABLE_RCON=TRUE\nRCON_PASSWORD='+secrets.token_urlsafe(32)+'\nLEVEL=owned-industry-check\nLEVEL_TYPE=minecraft:flat\nGENERATE_STRUCTURES=FALSE\nDIFFICULTY=peaceful\nMAX_PLAYERS=1\nSPAWN_PROTECTION=0\nVIEW_DISTANCE=2\nSIMULATION_DISTANCE=2\n')
env.chmod(0o600)
image=json.loads((root/'compose.json').read_text())['services']['minecraft']['image']
assert '@sha256:' in image
(stage/'image.txt').write_text(image)
upload=Path('/tmp/erez-industry-20261024');assert not upload.exists();upload.mkdir();shutil.chown(upload,user='dimon',group='dimon')
print('OWNED_SERVER_STAGE_PREPARED')
'''
print(ssh(setup),flush=True)
for filename,digest in files.items():
    local=WORK/filename
    if not local.exists():local=WORK/'client/mods'/filename
    assert hashlib.sha256(local.read_bytes()).hexdigest()==digest
    subprocess.run(['scp','-q',str(local),'minecraft:/tmp/erez-industry-20261024/'+filename],check=True,timeout=90)
copy_and_start=r'''
from pathlib import Path
import subprocess,shutil,hashlib,json
root=Path('/opt/minecraft/erezcraft');stage=root/'staging/industry-check-20261024';data=stage/'data'
files=json.loads(FILES)
for filename,digest in files.items():
 p=Path('/tmp/erez-industry-20261024')/filename;assert p.name==filename
 assert hashlib.sha256(p.read_bytes()).hexdigest()==digest
 shutil.copyfile(p,data/'mods'/filename)
assert len(list((data/'mods').glob('*.jar')))==67
p=subprocess.run(['docker','run','--detach','--pull','never','--name','erezcraft-industry-check','--memory','4g','--cpus','2','--publish','127.0.0.1:25567:25565','--env-file',str(stage/'probe.env'),'--mount','type=bind,src='+str(data)+',dst=/data','--mount','type=bind,src='+str(root/'data/libraries')+',dst=/data/libraries,readonly',(stage/'image.txt').read_text()],check=True,capture_output=True,text=True)
print('OWNED_LOOPBACK_INDUSTRY_SERVER_STARTED')
'''.replace('FILES',repr(json.dumps(files)))
print(ssh(copy_and_start),flush=True)
for i in range(90):
    status=ssh("import subprocess,json\np=subprocess.run(['docker','inspect','erezcraft-industry-check','--format','{{json .State}}'],capture_output=True,text=True,check=True);s=json.loads(p.stdout);print(json.dumps({'running':s['Running'],'exit':s['ExitCode'],'health':s.get('Health',{}).get('Status')}))\n")
    row=json.loads(status)
    if not row['running']:raise RuntimeError('Disposable server exited: '+status)
    if row['health']=='healthy':
        print('OWNED_DEDICATED_HEALTHY',flush=True)
        receipt={'status':'dedicated startup only','healthy':True,'server_jars':67,'owner_worlds_used':False,'production_changed':False,'loopback_port':25567,'container':NAME}
        (WORK/'dedicated-startup.json').write_text(json.dumps(receipt,indent=2)+'\n','utf-8')
        break
    if i%4==0:print('OWNED_DEDICATED_STARTING',row,flush=True)
    time.sleep(5)
else:raise TimeoutError('Owned dedicated did not become healthy; inspect exact container before another run')
