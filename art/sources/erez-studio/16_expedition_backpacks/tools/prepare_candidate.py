from pathlib import Path
import json,hashlib,shutil,subprocess,re
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');BASE=GAME/'.verification/ai01';DEST=GAME/'.verification/bp01'
assert not DEST.exists()
subprocess.run(['git','worktree','add','--detach',str(DEST),'HEAD'],cwd=GAME,check=True,stdout=subprocess.DEVNULL)
for n in ['src/main','src/smoke','tools']:shutil.copytree(BASE/n,DEST/n,dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
for n in ['build.gradle','gradle.properties']:shutil.copy2(BASE/n,DEST/n)
p=DEST/'gradle.properties';p.write_text(re.sub(r'(?m)^mod_version=.*$','mod_version=0.7.0-preview.21-backpacks',p.read_text()),encoding='utf-8')
p=DEST/'src/smoke/java/pro/erez/interstice/client/ArmorIconsSmoke.java';s=p.read_text(encoding='utf-8').replace('System.getProperty("interstice.progressionSmoke","")','System.getProperty("interstice.disabledBackpackIcons","")');p.write_text(s,encoding='utf-8')
snapshot={p.relative_to(DEST).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in (DEST/'src/main').rglob('*') if p.is_file()}
(ROOT/'reports/baseline.json').write_text(json.dumps({'checkout':str(DEST),'base':str(BASE),'files':snapshot},indent=2),encoding='utf-8')
print(DEST)
