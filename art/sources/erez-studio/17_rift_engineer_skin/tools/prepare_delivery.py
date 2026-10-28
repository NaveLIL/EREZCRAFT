from pathlib import Path
import base64,datetime,hashlib,json,urllib.request
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
ROOT=Path(__file__).resolve().parents[1];LAUNCH=Path('D:/repos/erezcraft');OUT=ROOT/'exports/delivery';OUT.mkdir(exist_ok=True)
cfg=json.loads((LAUNCH/'config/launcher.downloads.json').read_text())
key=Ed25519PublicKey.from_public_bytes(base64.b64decode(cfg['publicKey']))
def fetch(url):return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'EREZ-avatar-candidate/1.0'}),timeout=45).read()
def decode(data):
    e=json.loads(data);payload=base64.b64decode(e['payload']);key.verify(base64.b64decode(e['signature']),payload);return json.loads(payload)
catalog_bytes=fetch(cfg['catalogUrl']);cat=decode(catalog_bytes);server=next(s for s in cat['servers'] if s['id']=='archipelago')
manifest_bytes=fetch(server['manifestUrl']);assert hashlib.sha256(manifest_bytes).hexdigest()==server['manifestSha256'];old=decode(manifest_bytes)
dep=json.loads((ROOT/'source/cpm_dependency.json').read_text());file=next(f for f in dep['version']['files'] if f['primary'])
row={'path':'mods/'+file['filename'],'size':file['size'],'sha256':dep['sha256'],'url':file['url'],'policy':'managed'}
assert not any('CustomPlayerModels' in r['path'] for r in old['files'])
guard=ROOT/'exports/erez-avatar-compat-0.1.0.jar';assert guard.exists()
digest=hashlib.sha256(guard.read_bytes()).hexdigest()
guard_row={'path':'mods/'+guard.name,'size':guard.stat().st_size,'sha256':digest,'url':'https://files.erezcraft.com/blobs/'+digest,'policy':'managed'}
(OUT/'blobs').mkdir(exist_ok=True);(OUT/'blobs'/digest).write_bytes(guard.read_bytes())
delta=[row,guard_row]
candidate=dict(old,releaseId=old['releaseId']+'-cpm-candidate',sequence=old['sequence']+1,files=old['files']+delta)
(OUT/'client-manifest.UNSIGNED.json').write_text(json.dumps(candidate,indent=2)+'\n',encoding='utf-8')
(OUT/'launcher-config.public.json').write_text(json.dumps(cfg,indent=2)+'\n',encoding='utf-8')
(OUT/'cpm-file.json').write_text(json.dumps(row,indent=2)+'\n',encoding='utf-8')
# The binary itself remains a third-party project in catalog, not copied into authored source.
record={'prepared_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'base_catalog_sequence':cat['sequence'],'base_manifest_sha256':server['manifestSha256'],
 'base_release':old['releaseId'],'base_files':len(old['files']),'candidate_files':len(candidate['files']),'delta':delta,
 'all_existing_entries_preserved':candidate['files'][:-2]==old['files'],'signature_verified':True,'published':False,'signed':False,
 'sequence_note':'Candidate only; re-read the current signed catalog and choose a fresh sequence immediately before an authorized release.',
 'client_model_delivery':'CPM server tracking protocol, or manual local import into player_models; this directory is outside launcher managed roots.',
 'server_overlay':delta,'personal_data_policy':'Do not add player_models to managed roots; do not ship a global CPM config that resets player choices.',
 'activation':'Publish a newly signed manifest containing CPM; install the same upstream mod on the server; assign/import the authored .cpmmodel. No live action performed.'}
(ROOT/'reports/delivery_preparation.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
(ROOT/'reports/launcher-base-catalog.signed.json').write_bytes(catalog_bytes);(ROOT/'reports/launcher-base-manifest.signed.json').write_bytes(manifest_bytes)
print(json.dumps({k:record[k] for k in ('base_catalog_sequence','base_files','candidate_files','all_existing_entries_preserved','published')}))
