"""Create an owned candidate from the accepted A04 runtime, preserving other work."""
from pathlib import Path
import hashlib,json,shutil,subprocess,re
ROOT=Path(__file__).resolve().parents[1]
GAME=Path('D:/repos/Minecraft/EREZCRAFT'); BASE=GAME/'.verification/ag03'; DEST=GAME/'.verification/kt01'
assert BASE.is_dir() and not DEST.exists()
for name in ['source','preview','assembly','exports','reports']:(ROOT/name).mkdir(exist_ok=True)
subprocess.run(['git','worktree','add','--detach',str(DEST),'HEAD'],cwd=GAME,check=True)
for name in ['src/main','src/smoke','tools']:
    shutil.copytree(BASE/name,DEST/name,dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
for name in ['build.gradle','gradle.properties']:shutil.copy2(BASE/name,DEST/name)
geometry=DEST/'src/main/resources/assets/interstice/armor/armor_design.json'
assert json.loads(geometry.read_text(encoding='utf-8'))['revision']=='ARMOR-A04'
p=DEST/'gradle.properties';p.write_text(re.sub(r'(?m)^mod_version=.*$','mod_version=0.7.0-preview.19-kotomyaf',p.read_text()),encoding='utf-8')
# The earlier gallery remains untouched in ag03, never active in this candidate.
p=DEST/'src/smoke/java/pro/erez/interstice/client/ArmorPlayerGallery.java'
s=p.read_text(encoding='utf-8').replace('System.getProperty("interstice.progressionSmoke","")','System.getProperty("interstice.disabledOldGallery","")');p.write_text(s,encoding='utf-8')
files={str(p.relative_to(DEST)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (DEST/'src/main').rglob('*') if p.is_file()}
(ROOT/'reports/baseline.json').write_text(json.dumps({'checkout':str(DEST),'base_checkout':str(BASE),'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=GAME,text=True).strip(),'files':files,'installed':False},indent=2),encoding='utf-8')
print(DEST)
