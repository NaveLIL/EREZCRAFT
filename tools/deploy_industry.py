"""Owner-requested industry update; guarded phases and verified backup to owner PC."""
from pathlib import Path
import argparse, hashlib, importlib.util, json, msvcrt, subprocess, time

ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'.verification/industry-release-20261024'
WORK=ROOT/'.verification/mod-candidates-20261024'
REMOTE='/opt/minecraft/erezcraft/staging/industry-release-20261024'

def ssh(code):
    result=subprocess.run(['ssh','-o','BatchMode=yes','minecraft','python3','-'],input=code,
                          text=True,capture_output=True,encoding='utf-8',timeout=900)
    if result.returncode:raise RuntimeError(result.stderr[-1600:])
    print(result.stdout,flush=True)
    return result.stdout

def common():
    return '''from pathlib import Path
import json,hashlib,subprocess,shutil,tarfile,datetime,os,re
root=Path('/opt/minecraft/erezcraft');stage=Path(REMOTE);mods=root/'data/mods'
assert stage.parent==root/'staging' and not stage.is_symlink()
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
run=lambda args:subprocess.check_output(args,text=True).strip()
'''.replace('REMOTE',repr(REMOTE))

def prepare():
    lock=json.loads((ROOT/'distribution/industry-expansion.lock.json').read_text('utf-8'))
    rows=[{'original':r['filename'],'installed':r['installed_filename'],'sha256':r['sha256']} for r in lock['mods']]
    kotlin=next((WORK/'client/mods').glob('kotlinforforge-*.jar'))
    rows.append({'original':kotlin.name,'installed':kotlin.name,'sha256':hashlib.sha256(kotlin.read_bytes()).hexdigest()})
    assert len(rows)==13
    code=common()+'''rows=ROWS
assert not stage.exists(),'Never overwrite a deployment stage'
before={p.name:sha(p) for p in mods.glob('*.jar')}
assert len(before)==54 and before['interstice-0.7.0-preview.13.jar']=='565b8a18f2ba17a6caadd2301d9c265d535cc46895e8d2602430207530635979'
assert all(row['installed'] not in before for row in rows)
stage.mkdir(mode=0o750)
for row in rows:
 source=Path('/tmp/erez-industry-20261024')/row['original']
 assert source.name==row['original'] and sha(source)==row['sha256']
 shutil.copy2(source,stage/row['installed'])
(stage/'mods.json').write_text(json.dumps(rows,indent=2))
(stage/'before.json').write_text(json.dumps(before,indent=2))
(stage/'preserve.json').write_text(json.dumps({str(p.relative_to(root)):sha(p) for p in [root/'compose.json',root/'data/ops.json',root/'data/erezcraft/level.dat_old'] if p.is_file()}))
print('INDUSTRY_SERVER_STAGE_READY_13_ADDITIONS')
'''.replace('ROWS',repr(rows))
    ssh(code)

def backup():
    proof=json.loads((WORK/'industry-native-normalized.json').read_text('utf-8'))
    assert proof['passed'] and proof['no_world_opened'] and proof['distant_horizons_loaded']
    assert json.loads((WORK/'translation-build.json').read_text())['ready_for_publication']
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
 target=root/'backups'/('erezcraft-pre-industry-'+stamp+'.tar.gz');assert not target.exists()
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
            if latest['source_sha256']==info['sha256'] and 'pre-industry-' in latest['source']:break
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
    ssh(common()+'''assert run(['docker','inspect','--format','{{.State.Status}}','erezcraft-minecraft-1'])=='exited'
backup=json.loads((stage/'pc-backup.json').read_text());assert backup['passed'] and backup['destination']=='owner_pc'
before=json.loads((stage/'before.json').read_text());assert before=={p.name:sha(p) for p in mods.glob('*.jar')}
rows=json.loads((stage/'mods.json').read_text())
for row in rows:
 source=stage/row['installed'];target=mods/row['installed']
 assert source.name==row['installed'] and not target.exists() and sha(source)==row['sha256']
for row in rows:
 target=mods/row['installed'];pending=target.with_suffix('.pending')
 shutil.copy2(stage/row['installed'],pending);assert sha(pending)==row['sha256'];pending.chmod(0o644);os.replace(pending,target)
after={p.name:sha(p) for p in mods.glob('*.jar')}
assert len(after)==67 and all(after[n]==h for n,h in before.items())
(stage/'mod-install.json').write_text(json.dumps({'passed':True,'server_jars':67,'old_jars_unchanged':54,'before':before,'after':after},indent=2))
subprocess.run(['docker','start','erezcraft-minecraft-1'],check=True,stdout=subprocess.DEVNULL)
print('INDUSTRY_PRODUCTION_STARTING_67_JARS')
''')

def accept():
    out=ssh(common()+'''state=json.loads(run(['docker','inspect','--format','{{json .State}}','erezcraft-minecraft-1']))
assert state['Running'] and state['Health']['Status']=='healthy'
log=(root/'data/logs/latest.log').read_text()
assert 'Done (' in log and 'Distant Horizons' in log and '0.7.0-preview.13' in log
required=['create_connected','sliceanddice','create_enchantment_industry','packagedauto','packageddraconic','pneumaticcraft','extended_industrialization','sfm','railways','kotlinforforge']
assert all('('+m+')' in log for m in required),[(m,'('+m+')' in log) for m in required]
before=json.loads((stage/'before.json').read_text());after={p.name:sha(p) for p in mods.glob('*.jar')}
assert len(after)==67 and all(after[n]==h for n,h in before.items())
for rel,h in json.loads((stage/'preserve.json').read_text()).items():
 if rel.endswith('level.dat_old'):continue
 assert sha(root/rel)==h,rel
props=dict(l.split('=',1) for l in (root/'data/server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
assert props['online-mode']=='true' and props['white-list']=='false'
ops=json.loads((root/'data/ops.json').read_text());assert len(ops)==1 and ops[0]['level']==4 and ops[0]['uuid']=='836c22ec-6f31-4dad-abaf-37fd0795f518'
assert (root/'data/erezcraft/level.dat').is_file()
result={'passed':True,'server_jars':67,'old_jars_unchanged':54,'mod_version':'0.7.0-preview.13','backup_destination':'owner_pc','online_auth_required':True,'owner_only_op4':True,'healthy':True,'world_regenerated':False,'player_worlds_replaced':False,'started_at':state['StartedAt']}
(stage/'server-deployed.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
''')
    receipt=json.loads(out);(STAGE/'server-deployed.json').write_text(json.dumps(receipt,indent=2)+'\n','utf-8')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['prepare','backup','install','accept'])
    args=parser.parse_args();globals()[args.phase]()
