from pathlib import Path
import hashlib,json,shutil,subprocess,re
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');BASE=GAME/'.verification/kt01';DEST=GAME/'.verification/ai01'
assert not DEST.exists(),'Keep an existing candidate intact'
for name in ['source','exports','preview','reports']:(ROOT/name).mkdir(exist_ok=True)
accepted=json.loads((ROOT.parent/'14_kotomyaf_armor/reports/accepted.json').read_text(encoding='utf-8'))
subprocess.run(['git','worktree','add','--detach',str(DEST),'HEAD'],cwd=GAME,check=True,stdout=subprocess.DEVNULL)
for name in ['src/main','src/smoke','tools']:shutil.copytree(BASE/name,DEST/name,dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
for name in ['build.gradle','gradle.properties']:shutil.copy2(BASE/name,DEST/name)
for name in ['KotomyafGallery.java']:
    p=DEST/'src/smoke/java/pro/erez/interstice/client'/name;s=p.read_text(encoding='utf-8').replace('System.getProperty("interstice.progressionSmoke","")','System.getProperty("interstice.disabledIconGallery","")');p.write_text(s,encoding='utf-8')
p=DEST/'gradle.properties';p.write_text(re.sub(r'(?m)^mod_version=.*$','mod_version=0.7.0-preview.20-icons',p.read_text()),encoding='utf-8')
snapshot={p.relative_to(DEST).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in (DEST/'src/main').rglob('*') if p.is_file()}
(ROOT/'reports/baseline.json').write_text(json.dumps({'checkout':str(DEST),'previous_jar_sha256':accepted['jar_sha256'],'files':snapshot},indent=2),encoding='utf-8')
print(DEST)
