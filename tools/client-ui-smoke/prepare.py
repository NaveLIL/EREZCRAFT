"""Stage a new full-pack Tab check. Copies only signed pack inputs, never worlds or credentials."""
import argparse, base64, hashlib, io, json, os, shutil, subprocess, urllib.request, zipfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('profile',type=Path)
p.add_argument('--launcher',type=Path,default=Path('D:/repos/erezcraft'))
p.add_argument('--source',type=Path,required=True)
p.add_argument('--candidate',type=Path,required=True)
p.add_argument('--runtime',type=Path,default=Path('D:/EREZCRAFT/data/stable/runtime'))
p.add_argument('--javac',type=Path,default=Path('D:/repos/Minecraft/EREZCRAFT/.verification/toolchain/jdk-21.0.12.1+1/bin/javac.exe'))
a=p.parse_args(); project=Path(__file__).resolve().parents[2]
profile=a.profile.absolute(); guard=(a.launcher/'.local/verification').resolve()
assert profile.parent.resolve()==guard and profile.name.startswith('tab-ui-') and not profile.exists()
source=a.source.resolve(strict=True);candidate=a.candidate.resolve(strict=True)
envelope=json.loads((a.launcher/'.local/files-release/public/catalog.json').read_text())
catalog=json.loads(base64.b64decode(envelope['payload']));server=next(s for s in catalog['servers'] if s['id']=='archipelago')
manifest_path=a.launcher/'.local/files-release/public/releases/archipelago'/server['releaseId']/'manifest.json'
manifest_bytes=manifest_path.read_bytes();assert hashlib.sha256(manifest_bytes).hexdigest()==server['manifestSha256']
manifest=json.loads(base64.b64decode(json.loads(manifest_bytes)['payload']));assert manifest['sequence']==15
profile.mkdir(parents=True)
for row in manifest['files']:
    assert row['path'].split('/')[0] in ('mods','config','defaultconfigs','kubejs','scripts','resourcepacks','shaderpacks')
    file=source/row['path']
    if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest()!=row['sha256']:
        file=a.launcher/'.local/files-release/public/blobs'/row['sha256']
    assert file.is_file() and hashlib.sha256(file.read_bytes()).hexdigest()==row['sha256'],row['path']
    dest=profile/row['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,dest)
shutil.copyfile(candidate,profile/'mods'/candidate.name)
assert len(list((profile/'mods').glob('*.jar')))==65
options='lang:ru_ru\nrenderDistance:2\nsimulationDistance:2\nguiScale:2\nmaxFps:60\nenableVsync:true\nfullscreen:false\n'
(profile/'options.txt').write_text(options,encoding='utf-8')
public_url='https://auth.erezcraft.com/authlib-injector/sessionserver/session/minecraft/profile/836c22ec6f314dadabaf37fd0795f518?unsigned=false'
with urllib.request.urlopen(public_url,timeout=20) as response: public=response.read()
data=json.loads(public);assert data['id']=='836c22ec6f314dadabaf37fd0795f518' and any(p['name']=='textures' and p.get('signature') for p in data['properties'])
(profile/'public-skin-profile.json').write_bytes(public) # Public signed profile only, no OAuth/session credentials.
classes=profile/'check-classes';classes.mkdir();libraries=profile/'check-libraries';libraries.mkdir();seen=set()
def nested(data,depth=0):
    assert depth<9
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in z.namelist():
            if name.endswith('.jar'):
                payload=z.read(name);digest=hashlib.sha256(payload).hexdigest()
                if digest not in seen:
                    seen.add(digest);(libraries/(digest[:12]+'-'+Path(name).name)).write_bytes(payload);nested(payload,depth+1)
for jar in (profile/'mods').glob('*.jar'):nested(jar.read_bytes())
patched=a.runtime/'libraries/net/neoforged/neoforge/21.1.252/neoforge-21.1.252-client.jar';assert patched.is_file()
deps=[patched,*[f for f in (a.runtime/'libraries').rglob('*.jar') if f!=patched],*sorted((profile/'mods').glob('*.jar')),*libraries.glob('*.jar')]
sources=[Path(__file__).with_name('TabUiCheck.java'),a.launcher/'tools/ocean-smoke/NativeChunkSettler.java']
args=profile/'javac.args'
args.write_text('-proc:none\n-encoding UTF-8\n-cp "'+os.pathsep.join(f.as_posix() for f in deps)+'"\n-d "'+classes.as_posix()+'"\n'+'\n'.join('"'+f.as_posix()+'"' for f in sources)+'\n',encoding='utf-8')
subprocess.run([str(a.javac),'@'+str(args)],check=True)
with zipfile.ZipFile(profile/'mods/erez-tab-ui-check.jar','w') as z:
    for file in classes.rglob('*.class'):z.write(file,file.relative_to(classes).as_posix())
    z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="Apache-2.0"\n[[mods]]\nmodId="erez_tab_ui_check"\nversion="1"\ndisplayName="Owned Tab UI native check"\n')
receipt={'candidate_sha256':hashlib.sha256(candidate.read_bytes()).hexdigest(),'source_sequence':15,'base_managed_files':len(manifest['files']),'base_jar_count':64,'production_jar_count':65,'owner_worlds_used':False,'user_credentials_read':False,'manifest_path':str(manifest_path)}
(profile/'tab-ui-fixture.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
print('OWN_TAB_PROFILE_PREPARED',profile)
