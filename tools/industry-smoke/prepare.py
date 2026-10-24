"""Create a fresh industry acceptance profile. No worlds, settings, or credentials from players."""
from pathlib import Path
import argparse, hashlib, io, json, os, re, shutil, subprocess, zipfile

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('name')
parser.add_argument('--translation-pack', type=Path)
parser.add_argument('--normalized-names',action='store_true')
args=parser.parse_args()
assert args.name.startswith('industry-') and Path(args.name).name==args.name
profile=Path('D:/repos/erezcraft/.local/verification')/args.name
assert not profile.exists()
source=ROOT/'.verification/mod-candidates-20261024/client'
assert len(list((source/'mods').glob('*.jar')))==79
profile.mkdir()
for name in ('mods','config','defaultconfigs','resourcepacks','shaderpacks'):
    if (source/name).is_dir():shutil.copytree(source/name,profile/name)
if args.translation_pack:
    assert args.translation_pack.is_file()
    shutil.copyfile(args.translation_pack,profile/'resourcepacks/erezcraft-ru-mc1.21.1.zip')
if args.normalized_names:
    for p in (profile/'mods').glob('*.jar'):
        name=re.sub('-+','-',re.sub('[^A-Za-z0-9._-]+','-',p.name))
        if name!=p.name:
            target=p.with_name(name);assert not target.exists();p.rename(target)
(profile/'options.txt').write_text('lang:ru_ru\nonboardAccessibility:false\ntutorialStep:none\nresourcePacks:["mod_resources","file/erezcraft-ru-mc1.21.1.zip"]\nrenderDistance:2\nsimulationDistance:2\nguiScale:2\nmaxFps:60\nfullscreen:false\npauseOnLostFocus:false\n','utf-8')
lock=json.loads((ROOT/'.verification/mod-candidates-20261024/candidate-lock.json').read_text('utf-8'))
mods={'create':'6.0.10','modern_industrialization':'2.5.10','sable':'2.0.6','interstice':'0.7.0-preview.13'}
for row in lock['mods'].values():
    for m in row['metadata']['mods']:
        version=m['version']
        if version=='${file.jarVersion}':
            with zipfile.ZipFile(source/'mods'/row['filename']) as archive:
                manifest=archive.read('META-INF/MANIFEST.MF').decode('utf-8')
                version=next(line.split(': ',1)[1].strip() for line in manifest.splitlines() if line.startswith('Implementation-Version: '))
        mods[m['modId']]=version
expected={'mods':mods,'translations':{},'books':[]}
if args.translation_pack:
    with zipfile.ZipFile(args.translation_pack) as archive:
        for name in archive.namelist():
            if name.endswith('/lang/ru_ru.json'):
                for key,value in json.loads(archive.read(name).decode('utf-8-sig')).items():
                    if key in expected['translations']:
                        assert expected['translations'][key]==value, key
                    expected['translations'][key]=value
            elif name.endswith(('.json','.md')) and ('/patchouli_books/' in name or '/_ru_ru/' in name):
                expected['books'].append(name)
(profile/'industry-expected.json').write_text(json.dumps(expected,ensure_ascii=False,indent=2),'utf-8')
classes=profile/'check-classes';classes.mkdir()
libraries=profile/'check-libraries';libraries.mkdir();seen=set()
def nested(data,depth=0):
    assert depth<8
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                payload=archive.read(name);digest=hashlib.sha256(payload).hexdigest()
                if digest not in seen:
                    seen.add(digest);(libraries/(digest[:12]+'-'+Path(name).name)).write_bytes(payload);nested(payload,depth+1)
for jar in (profile/'mods').glob('*.jar'):nested(jar.read_bytes())
runtime=Path('D:/EREZCRAFT/data/stable/runtime')
patched=runtime/'libraries/net/neoforged/neoforge/21.1.252/neoforge-21.1.252-client.jar'
assert patched.is_file()
deps=[patched,*[f for f in (runtime/'libraries').rglob('*.jar') if f!=patched],*sorted((profile/'mods').glob('*.jar')),*libraries.glob('*.jar')]
javac=ROOT/'.verification/toolchain/jdk-21.0.12.1+1/bin/javac.exe'
arguments=profile/'javac.args';arguments.write_text('-proc:none\n-encoding UTF-8\n-cp "'+os.pathsep.join(f.as_posix() for f in deps)+'"\n-d "'+classes.as_posix()+'"\n"'+Path(__file__).with_name('IndustryCheck.java').as_posix()+'"\n','utf-8')
subprocess.run([str(javac),'@'+str(arguments)],check=True)
with zipfile.ZipFile(profile/'mods/erez-industry-check.jar','w') as archive:
    for file in classes.rglob('*.class'):archive.write(file,file.relative_to(classes).as_posix())
    archive.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="Apache-2.0"\n[[mods]]\nmodId="erez_industry_check"\nversion="1"\ndisplayName="Owned industry native check"\n')
proof={'owner_worlds_used':False,'user_credentials_read':False,'production_jars':79,'files':{p.relative_to(profile).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for folder in ('mods','config','defaultconfigs','resourcepacks','shaderpacks') for p in (profile/folder).rglob('*') if p.is_file()}}
(profile/'industry-fixture.json').write_text(json.dumps(proof,indent=2),'utf-8')
print('ISOLATED_INDUSTRY_PROFILE_READY',profile)
