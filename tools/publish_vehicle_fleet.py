"""Promote the owner-authorized coordinated expedition progression delta after server acceptance. Existing releases remain immutable."""
from pathlib import Path
import base64,hashlib,json,os,subprocess,time,urllib.request,uuid,shlex
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

root=Path('D:/repos/erezcraft')
public=root/'.local/files-release/public'
stage=Path('D:/repos/Minecraft/EREZCRAFT/.verification/vehicle-release-20261030')
proof=json.loads((stage/'publication.json').read_text())
key=Ed25519PublicKey.from_public_bytes(base64.b64decode(json.loads((root/'config/launcher.downloads.json').read_text())['publicKey']))
def digest(b):return hashlib.sha256(b).hexdigest()
def decode(raw):
    envelope=json.loads(raw);payload=base64.b64decode(envelope['payload']);key.verify(base64.b64decode(envelope['signature']),payload);return json.loads(payload)
def get(url):return urllib.request.urlopen(url,timeout=30).read()
old=get('https://files.erezcraft.com/catalog.json');assert decode(old)['sequence']==25 and digest(old)==proof['oldCatalogSha256']
catalog=(public/'catalog.json').read_bytes();assert digest(catalog)==proof['newCatalogSha256'] and decode(catalog)['sequence']==26
relative='releases/archipelago/techmagic-0-1-0-alpha-26-vehicle-fleet/manifest.json'
manifest=(public/relative).read_bytes();m=decode(manifest);assert m['sequence']==26 and len(m['files'])==89
server=next(s for s in decode(catalog)['servers'] if s['id']=='archipelago');assert server['manifestSha256']==digest(manifest)
deployed=json.loads((stage/'server-deployed.json').read_text())
assert deployed['passed'] and deployed['mod_version']=='0.7.0-preview.121-vehicle-fleet' and deployed['backups_created'] is False
files={relative:manifest,'catalog.json':catalog}
for row in proof['changed']:
    if not row['url'].startswith('https://files.erezcraft.com/blobs/'):continue
    blob=(public/'blobs'/row['sha256']).read_bytes();assert digest(blob)==row['sha256'];files['blobs/'+row['sha256']]=blob
assert len(files)==4 and sum(map(len,files.values()))<32*1024**2
transport=['-i','C:/Users/Dimon/.ssh/erez_vps_193_148_59_26_ed25519','-o','IdentitiesOnly=yes','-o','UserKnownHostsFile=C:/Users/Dimon/.ssh/known_hosts_erez_vps','-o','HostKeyAlias=193.148.59.26','-o','StrictHostKeyChecking=yes','-o','BatchMode=yes','-o','ConnectTimeout=8']
host='root@193.148.59.26';incoming='/var/www/erezcraft/incoming/updates-'+str(uuid.uuid4())
def ssh(code):
    result=transfer(['ssh','-T',*transport,host,'python3','-'],input=code.encode(),timeout=240)
    print(result.stdout.decode().strip())
def transfer(command,**options):
    for attempt in range(3):
        try:
            result=subprocess.run(command,capture_output=True,**options)
            if result.returncode==0:return result
            message=result.stderr.decode(errors='replace')[-1000:]
        except subprocess.TimeoutExpired:message='Owned file transfer timed out'
        if attempt==2:raise RuntimeError(message)
        print('Retrying owned transfer after transient connection failure:',message.strip(),flush=True);time.sleep(1)
hashes={name:{'incoming':str(i),'sha256':digest(content),'size':len(content),'data':base64.b64encode(content).decode('ascii')} for i,(name,content) in enumerate(files.items())}
code='''from pathlib import Path
import hashlib,os,shutil,sys,base64
incoming=Path(INCOMING);root=Path('/var/www/erezcraft/public')
assert incoming.parent==Path('/var/www/erezcraft/incoming') and not incoming.is_symlink()
assert shutil.disk_usage(root).free>2*1024**3,'Preserve VPS disk reserve'
rows=ROWS
current=hashlib.sha256((root/'catalog.json').read_bytes()).hexdigest()
if current==rows['catalog.json']['sha256']:
 for name,row in rows.items():assert hashlib.file_digest((root/name).open('rb'),'sha256').hexdigest()==row['sha256']
 print('Exact release already promoted and verified');sys.exit(0)
assert current==OLDHASH,'Channel moved'
incoming.mkdir(mode=0o700,exist_ok=True)
for row in rows.values():
 content=base64.b64decode(row['data']);assert len(content)==row['size'] and hashlib.sha256(content).hexdigest()==row['sha256']
 p=incoming/row['incoming'];assert not p.is_symlink();p.write_bytes(content)
for name,row in rows.items():
 p=incoming/row['incoming'];assert p.stat().st_size==row['size'] and hashlib.file_digest(p.open('rb'),'sha256').hexdigest()==row['sha256']
for name,row in rows.items():
 if name=='catalog.json':continue
 p=incoming/row['incoming'];target=root/name;assert target.is_relative_to(root) and not target.is_symlink()
 target.parent.mkdir(parents=True,exist_ok=True)
 if target.exists():assert hashlib.file_digest(target.open('rb'),'sha256').hexdigest()==row['sha256'],'Immutable file differs'
 else:os.chmod(p,0o644);os.replace(p,target)
assert hashlib.sha256((root/'catalog.json').read_bytes()).hexdigest()==OLDHASH
p=incoming/rows['catalog.json']['incoming'];os.chmod(p,0o644);os.replace(p,root/'catalog.json')
for row in rows.values():
 p=incoming/row['incoming']
 if p.exists():p.unlink()
incoming.rmdir()
print('Verified blobs and immutable manifest promoted; signed catalog switched last')
'''
code=code.replace('INCOMING',repr(incoming)).replace('OLDHASH',repr(digest(old))).replace('ROWS',repr(hashes))
ssh(code)
assert get('https://files.erezcraft.com/catalog.json')==catalog
assert get('https://files.erezcraft.com/'+relative)==manifest
for row in proof['changed']:assert digest(get(row['url']))==row['sha256']
receipt={**proof,'public_https_verified':True,'signatures_verified':True,'uploaded_files':4,'uploaded_bytes':sum(map(len,files.values())),'game_server_restarted':True,'world_regenerated':False,'player_worlds_replaced':False}
(stage/'published.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
print('PUBLIC_UPDATES_SEQUENCE26_VERIFIED')
