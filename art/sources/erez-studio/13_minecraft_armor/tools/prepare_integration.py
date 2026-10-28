"""Create a clean armor candidate on top of the already isolated longsword delta."""
from pathlib import Path
import hashlib,json,subprocess,shutil
ROOT=Path(__file__).resolve().parents[1]
GAME=Path('D:/repos/Minecraft/EREZCRAFT')
CANDIDATE=GAME/'.verification/armor_a01'
BASE=ROOT/'reports/integration_baseline'
assert not CANDIDATE.exists(), 'Inspect existing candidate; never reset it'
BASE.mkdir(exist_ok=True)
paths=[p for top in ['src','tools','docs'] for p in (GAME/top).rglob('*') if p.is_file() and '__pycache__' not in p.parts]
paths += [GAME/'build.gradle',GAME/'gradle.properties']
hashes={p.relative_to(GAME).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
(BASE/'sha256.json').write_text(json.dumps(hashes,indent=2),encoding='utf-8')
for n in ['src/main/java/pro/erez/interstice/gear/RealmArmaments.java','src/main/resources/assets/interstice/lang/ru_ru.json','src/main/resources/assets/interstice/lang/en_us.json','tools/build_expedition_progression.py','docs/HANDOFF.md']:
    dest=BASE/n;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(GAME/n,dest)
subprocess.run(['git','worktree','add','--detach',str(CANDIDATE),'HEAD'],cwd=GAME,check=True)
patch=ROOT.parent/'12_minecraft_longsword/reports/longsword-only.patch'
subprocess.run(['git','apply','--check',str(patch)],cwd=CANDIDATE,check=True)
subprocess.run(['git','apply',str(patch)],cwd=CANDIDATE,check=True)
geometry=CANDIDATE/'src/main/resources/assets/interstice/armor/armor_design.json'
geometry.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2(ROOT/'source/armor_design.json',geometry)
print(json.dumps({'candidate':str(CANDIDATE),'baseline_files':len(hashes),'unfinished_fauna_included':False}))
