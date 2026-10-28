"""Pin the official optional 3D-avatar dependency without touching any game profile."""
from pathlib import Path
import datetime, hashlib, json, urllib.request, zipfile

ROOT=Path(__file__).resolve().parents[1]
CAT=ROOT.parents[1]/'catalog/custom_player_models'
CAT.mkdir(parents=True,exist_ok=True)
def fetch(url):
    return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'EREZ-local-avatar-design/1.0'}),timeout=60).read()

version=json.loads(fetch('https://api.modrinth.com/v2/version/YXfPij2E'))
assert version['version_number']=='1.21v0.6.27a-neoforge'
assert '1.21.1' in version['game_versions'] and 'neoforge' in version['loaders']
file=next(f for f in version['files'] if f['primary'])
jar=CAT/file['filename']
data=jar.read_bytes() if jar.exists() else fetch(file['url'])
assert len(data)==file['size'] and hashlib.sha512(data).hexdigest()==file['hashes']['sha512']
jar.write_bytes(data)
with zipfile.ZipFile(jar) as z:
    metadata=z.read('META-INF/neoforge.mods.toml').decode()
    (CAT/'neoforge.mods.toml').write_text(metadata,encoding='utf-8')
    licenses=[n for n in z.namelist() if n.lower().rsplit('/',1)[-1] in ('license','license.txt','license.md')]
    for i,n in enumerate(licenses): (CAT/('LICENSE-'+str(i)+'.txt')).write_bytes(z.read(n))
record={'retrieved_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'version':version,
        'sha256':hashlib.sha256(data).hexdigest(),'catalog_jar':str(jar),'installed':False,
        'official_source':'https://github.com/tom5454/CustomPlayerModels',
        'export_documentation':'https://github.com/tom5454/CustomPlayerModels/wiki/Exporting',
        'tested_with_pack':False}
(CAT/'provenance.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
(ROOT/'source/cpm_dependency.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
p=ROOT/'source/requirements.json';r=json.loads(p.read_text(encoding='utf-8'))
r.update(revision='S02 3D avatar implementation pending',target='CPM 1.21v0.6.27a-neoforge, Minecraft Java 1.21.1 / NeoForge 21.1.252',
         concept_only=False,approval_required_before_final_skin=False,visual_direction_approved=True,
         authoritative_geometry='Not authored yet; will be declared when the CPM model is created')
r['user_direction'].append('Real 3D volume is important; investigate server support rather than flattening the concept')
r['constraints']=['Preserve normal player proportions and gameplay hitbox',
  'Real 3D respirator, collar, coat panels, belt pouches and instruments',
  'Mod must be present on viewing clients; server model-file mode requires server installation',
  'Armor and equipment must remain visible and functional',
  'Mask/collar must not intersect helmets; coat must handle leggings, sneaking and elytra',
  'No server deployment, live-profile changes or public model uploads in preparation phase']
r['verification_plan']=['Native CPM model loading and viewer synchronization in isolated profiles',
  'Bare player front/back/sides and Hebrew legibility',
  'Walk, crouch, swim, first-person arms and elytra',
  'Vanilla armor and silver, steel, Magen, Kotomyaf, mixed armor slots',
  'Backpacks and resource reload', 'Bound geometry/texture cost and compare render performance']
r['unknowns']=['CPM interoperability with the custom RealmArmor renderer not yet tested',
  'Automatic per-slot cosmetic visibility mechanism needs source/API verification',
  'Multiplayer model synchronization not yet tested with EREZ ID']
p.write_text(json.dumps(r,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps({'downloaded':str(jar),'sha256':record['sha256'],'installed':False,'tested':False}))
