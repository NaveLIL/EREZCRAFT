"""Coordinated expedition-only update; never reset worlds or change authentication."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,msvcrt,subprocess,time
ROOT=Path('D:/repos/Minecraft/EREZCRAFT')
STAGE=ROOT/'.verification/studio-fauna-release-20261027'
REMOTE='/opt/minecraft/erezcraft/staging/studio-fauna-release-20261027'
def sha_local(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def ssh(code):
    r=subprocess.run(['ssh','-o','BatchMode=yes','minecraft','python3','-'],input=code,text=True,capture_output=True,encoding='utf-8',timeout=900)
    if r.returncode:raise RuntimeError(r.stderr[-1800:])
    print(r.stdout,flush=True);return r.stdout

def common():
    return """from pathlib import Path
import json,hashlib,subprocess,shutil,tarfile,datetime,os,re
root=Path('/opt/minecraft/erezcraft');stage=Path(REMOTE);mods=root/'data/mods'
assert stage.parent==root/'staging' and not stage.is_symlink()
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
run=lambda args:subprocess.check_output(args,text=True).strip()
""".replace('REMOTE',repr(REMOTE))

def prepare():
    proof=json.loads((ROOT/'.verification/studio-fauna-release-input.json').read_text('utf-8'))
    assert proof['passed'] and proof['faunaIncluded'] and proof['modVersion']=='0.7.0-preview.35-fauna-studio'
    assert sha_local(Path(proof['jar']))==proof['jarSha256']
    subprocess.run(['scp','-q','-o','BatchMode=yes',proof['jar'],'minecraft:/tmp/erez-studio-fauna-20261027.jar'],check=True)
    code=common()+"""info=INFO
assert not stage.exists(),'Never overwrite a deployment stage'
before={p.name:sha(p) for p in mods.glob('*.jar')}
assert len(before)==67 and before['interstice-0.7.0-preview.15.jar']==info['baseJarSha256']
p=Path('/tmp/erez-studio-fauna-20261027.jar');assert not p.is_symlink() and sha(p)==info['jarSha256']
stage.mkdir(mode=0o750);shutil.copy2(p,stage/'interstice-0.7.0-preview.35-fauna-studio.jar')
(stage/'candidate.json').write_text(json.dumps(info,indent=2))
(stage/'before.json').write_text(json.dumps(before,indent=2))
(stage/'preserve.json').write_text(json.dumps({str(p.relative_to(root)):sha(p) for p in [root/'compose.json',root/'data/ops.json'] if p.is_file()}))
print('PROGRESSION_ONLY_SERVER_STAGE_READY')
""".replace('INFO',repr(proof))
    ssh(code)

def backup():
    proof=json.loads((ROOT/'.verification/studio-fauna-release-input.json').read_text('utf-8'))
    assert proof['passed'] and proof['faunaIncluded']
    assert sha_local(Path(proof['jar']))==proof['jarSha256']
    code=common()+'''assert stage.is_dir()
if (stage/'offline-backup.json').exists():
 assert run(['docker','inspect','--format','{{.State.Status}}','erezcraft-minecraft-1'])=='exited','A new stop needs a new backup stage'
 previous=json.loads((stage/'offline-backup.json').read_text());assert previous['passed'] and sha(Path(previous['archive']))==previous['sha256']
 print(json.dumps(previous));raise SystemExit(0)
assert json.loads(run(['docker','inspect','--format','{{json .State}}','erezcraft-minecraft-1']))['Health']['Status']=='healthy'
players=run(['docker','exec','erezcraft-minecraft-1','rcon-cli','list'])
assert re.search(r'There are 0 of a max of [0-9]+ players online:',players),'Players are online; defer update'
subprocess.run(['docker','stop','-t','45','erezcraft-minecraft-1'],check=True,stdout=subprocess.DEVNULL)
try:
 assert run(['docker','inspect','--format','{{.State.Status}}','erezcraft-minecraft-1'])=='exited'
 world=root/'data/erezcraft';assert (world/'level.dat').is_file()
 before={str(p.relative_to(world)):sha(p) for p in world.rglob('*') if p.is_file()}
 stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
 target=root/'backups'/('erezcraft-pre-studio-fauna-'+stamp+'.tar.gz');assert not target.exists()
 assert shutil.disk_usage(target.parent).free>sum(p.stat().st_size for p in world.rglob('*') if p.is_file())+2*1024**3
 with tarfile.open(target,'w:gz',compresslevel=1) as archive:archive.add(world,arcname='erezcraft')
 count=0
 with tarfile.open(target,'r:gz') as archive:
  for member in archive:
   if member.isfile():
    rel=str(Path(member.name).relative_to('erezcraft'))
    assert hashlib.file_digest(archive.extractfile(member),'sha256').hexdigest()==before[rel]
    count+=1
 assert count==len(before) and all(sha(world/p)==h for p,h in before.items())
 receipt={'passed':True,'archive':str(target),'sha256':sha(target),'world_files_verified':count,'source_stopped':True}
 (stage/'offline-backup.json').write_text(json.dumps(receipt,indent=2))
 print(json.dumps(receipt))
except BaseException:
 subprocess.run(['docker','start','erezcraft-minecraft-1'],check=True,stdout=subprocess.DEVNULL)
 raise
'''
    info=json.loads(ssh(code))
    spec=importlib.util.spec_from_file_location('backup_pc','D:/EREZCRAFT/backup-tools/backup_pc.py')
    b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
    folder=Path('D:/EREZCRAFT/backups')
    with (folder/'job.lock').open('a+b') as lock:
        lock.seek(0);lock.write(b'0');lock.flush();lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        # The restricted exporter ignores archives until their mtime settles.
        for attempt in range(12):
            latest=json.loads(subprocess.check_output(b.ssh('world')+['world-info'],timeout=60))
            if latest['source_sha256']==info['sha256'] and 'pre-studio-fauna-' in latest['source']:break
            time.sleep(5)
        else:raise RuntimeError('Fresh completed backup was not offered by exporter')
        world=b.download('world');assert b.valid_copy('world',world) and world['source_sha256']==info['sha256']
        server=b.download('server');assert b.valid_copy('server',server)
        receipt={'passed':True,'destination':'owner_pc','root':str(folder),'world':world,'server':server,'source':info}
        (STAGE/'backup.json').write_text(json.dumps(receipt,indent=2)+'\n','utf-8')
        state_path=folder/'state.json';state=json.loads(state_path.read_text())
        state.update(world=world,server=server,destination='owner_pc')
        pending=state_path.with_suffix('.pending');pending.write_text(json.dumps(state,indent=2)+'\n');pending.replace(state_path)
    ssh(common()+"(stage/'pc-backup.json').write_text("+repr(json.dumps(receipt))+ ")\nprint('PC_BACKUP_RECEIPT_INSTALLED')\n")

def install():
    ssh(common()+"""assert run(['docker','inspect','--format','{{.State.Status}}','erezcraft-minecraft-1'])=='exited'
waiver=json.loads((stage/'backup-transfer-waiver.json').read_text());assert waiver['owner_requested_skip_backup'] is True
assert json.loads((stage/'offline-backup.json').read_text())['passed'] is True
before=json.loads((stage/'before.json').read_text());assert before=={p.name:sha(p) for p in mods.glob('*.jar')}
info=json.loads((stage/'candidate.json').read_text())
old=mods/'interstice-0.7.0-preview.15.jar';new=mods/'interstice-0.7.0-preview.35-fauna-studio.jar';saved=stage/'previous-interstice-0.7.0-preview.15.jar'
assert old.is_file() and not new.exists() and not saved.exists()
assert sha(stage/new.name)==info['jarSha256']
try:
 pending=new.with_suffix('.pending');shutil.copy2(stage/new.name,pending);assert sha(pending)==info['jarSha256'];pending.chmod(0o644)
 old.rename(saved);os.replace(pending,new)
 after={p.name:sha(p) for p in mods.glob('*.jar')}
 assert len(after)==67 and all(after[n]==h for n,h in before.items() if n!=old.name)
 (stage/'mod-install.json').write_text(json.dumps({'passed':True,'old_jars_unchanged':66,'before':before,'after':after},indent=2))
 subprocess.run(['docker','start','erezcraft-minecraft-1'],check=True,stdout=subprocess.DEVNULL)
except BaseException:
 if new.exists():new.rename(stage/'failed-interstice-0.7.0-preview.35-fauna-studio.jar')
 if saved.exists():saved.rename(old)
 subprocess.run(['docker','start','erezcraft-minecraft-1'],check=True,stdout=subprocess.DEVNULL)
 raise
print('PROGRESSION_PRODUCTION_STARTING_67_JARS')
""")

def accept():
    out=ssh(common()+"""state=json.loads(run(['docker','inspect','--format','{{json .State}}','erezcraft-minecraft-1']))
assert state['Running'] and state['Health']['Status']=='healthy'
log=(root/'data/logs/latest.log').read_text();assert 'Done (' in log and 'Distant Horizons' in log and '0.7.0-preview.35-fauna-studio' in log
before=json.loads((stage/'before.json').read_text());after={p.name:sha(p) for p in mods.glob('*.jar')};info=json.loads((stage/'candidate.json').read_text())
assert len(after)==67 and after['interstice-0.7.0-preview.35-fauna-studio.jar']==info['jarSha256']
assert all(after[n]==h for n,h in before.items() if n!='interstice-0.7.0-preview.15.jar')
for rel,h in json.loads((stage/'preserve.json').read_text()).items():assert sha(root/rel)==h,rel
props=dict(l.split('=',1) for l in (root/'data/server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
assert props['online-mode']=='true' and props['white-list']=='false'
ops=json.loads((root/'data/ops.json').read_text());assert len(ops)==1 and ops[0]['level']==4 and ops[0]['uuid']=='836c22ec-6f31-4dad-abaf-37fd0795f518'
assert (root/'data/erezcraft/level.dat').is_file()
result={'passed':True,'server_jars':67,'old_jars_unchanged':66,'mod_version':'0.7.0-preview.35-fauna-studio','backup_destination':'server_local_only','pc_transfer_skipped_by_owner':True,'online_auth_required':True,'owner_only_op4':True,'healthy':True,'world_regenerated':False,'player_worlds_replaced':False,'fauna_included':True,'tcp_motion_acceptance':'open','started_at':state['StartedAt']}
(stage/'server-deployed.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
""")
    (STAGE/'server-deployed.json').write_text(json.dumps(json.loads(out),indent=2)+'\n','utf-8')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['prepare','backup','install','accept']);args=parser.parse_args()
    try:globals()[args.phase]()
    except BaseException:
        if args.phase=='backup':
            ssh(common()+"if run(['docker','inspect','--format','{{.State.Status}}','erezcraft-minecraft-1'])=='exited' and (mods/'interstice-0.7.0-preview.15.jar').exists():\n subprocess.run(['docker','start','erezcraft-minecraft-1'],check=True,stdout=subprocess.DEVNULL)\n print('OLD_SERVER_RESUMED_AFTER_BACKUP_FAILURE')\n")
        raise
